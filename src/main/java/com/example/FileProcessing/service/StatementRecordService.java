package com.example.FileProcessing.service;

import com.example.FileProcessing.entity.StatementRecordEntity;
import com.example.FileProcessing.model.CustomerRecord;
import com.example.FileProcessing.repository.StatementRecordRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 负责：
 * - 将 CSV 解析得到的 CustomerRecord 写入 H2 中间表；
 * - 在时间窗内，检测“静默期”（无新 CSV 达到）后，按账号做合并聚合；
 * - 调用 PDF + 邮件服务生成最终账单，并标记记录已结算。
 */
@Service
public class StatementRecordService {

    private final StatementRecordRepository repository;
    private final PdfGeneratorService pdfGeneratorService;

    /**
     * 最近一次有 CSV 数据写入 H2 的时间
     */
    private volatile LocalDateTime lastIngestTime;

    @Value("${batch.window.start:20:00}")
    private String windowStartStr;

    @Value("${batch.window.end:22:00}")
    private String windowEndStr;

    @Value("${batch.settlement.quiet-minutes:10}")
    private long quietMinutes;

    public StatementRecordService(StatementRecordRepository repository,
                                  PdfGeneratorService pdfGeneratorService) {
        this.repository = repository;
        this.pdfGeneratorService = pdfGeneratorService;
    }

    @PostConstruct
    public void logConfig() {
        System.out.println("[结算] 配置已加载: 时间窗 " + windowStartStr + "–" + windowEndStr + "，静默 " + quietMinutes + " 分钟");
    }

    /**
     * 将一批 CSV 解析后的记录写入 H2 中间表，仅存储，不生成 PDF。
     */
    public void saveRecords(List<CustomerRecord> records) {
        if (records == null || records.isEmpty()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        List<StatementRecordEntity> entities = new ArrayList<>(records.size());

        for (CustomerRecord r : records) {
            if (r.getAccountNumber() == null || r.getAccountNumber().isEmpty()) {
                continue;
            }
            StatementRecordEntity e = new StatementRecordEntity();
            e.setAccountNumber(r.getAccountNumber());
            e.setTransactionAmount(r.getTransactionAmount());
            e.setBalance(r.getBalance());
            e.setCurrency(r.getCurrency());
            e.setEmail(r.getEmail());
            e.setName(r.getName());
            if (r.getStatementDate() != null && !r.getStatementDate().isEmpty()) {
                try {
                    e.setStatementDate(LocalDate.parse(r.getStatementDate(), DateTimeFormatter.ISO_LOCAL_DATE));
                } catch (Exception ignored) {
                }
            }
            e.setCreatedAt(now);
            e.setSettled(false);
            entities.add(e);
        }

        if (!entities.isEmpty()) {
            repository.saveAll(entities);
            lastIngestTime = now;
            System.out.println("[结算] 已写入 H2 记录数: " + entities.size() + "，lastIngestTime=" + now);
        }
    }

    /**
     * 定时任务：每 30 秒检查一次是否满足“时间窗 + 静默期 + 有未结算数据”，满足则统一结算。
     */
    @Scheduled(fixedRate = 30000)
    public void settleIfReady() {
        LocalDateTime now = LocalDateTime.now();

        if (!withinWindow(now.toLocalTime())) {
            return;
        }
        if (lastIngestTime == null) {
            return; // 本次启动后从未写入过 CSV，跳过
        }

        long elapsedMinutes = Duration.between(lastIngestTime, now).toMinutes();
        if (elapsedMinutes < quietMinutes) {
            return; // 静默期未到，跳过
        }

        List<StatementRecordEntity> pending = repository.findBySettledFalse();
        if (pending.isEmpty()) {
            return; // 无未结算记录，跳过
        }

        System.out.println("[结算] 条件满足，开始生成 PDF。未结算行数: " + pending.size() + "，距上次入库: " + elapsedMinutes + " 分钟");

        try {
            List<CustomerRecord> merged = mergeForSettlement(pending);
            String batchId = "SETTLE_" + now.format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            pdfGeneratorService.generatePdfBillsAndSendEmail(merged, batchId);

            // 标记已结算
            for (StatementRecordEntity e : pending) {
                e.setSettled(true);
            }
            repository.saveAll(pending);

            System.out.println("结算完成，本次生成账单账户数: " + merged.size()
                    + "，原始行数: " + pending.size());
        } catch (Exception e) {
            System.err.println("结算任务失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private boolean withinWindow(LocalTime now) {
        try {
            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("HH:mm");
            LocalTime start = LocalTime.parse(windowStartStr, fmt);
            LocalTime end = LocalTime.parse(windowEndStr, fmt);
            return !now.isBefore(start) && !now.isAfter(end);
        } catch (Exception e) {
            // 配置异常时，默认总是允许
            return true;
        }
    }

    /**
     * 使用聚合逻辑，将同一账号的多行记录合并成一条账单记录。
     */
    private List<CustomerRecord> mergeForSettlement(List<StatementRecordEntity> rows) {
        Map<String, CustomerRecord> merged = new LinkedHashMap<>();

        for (StatementRecordEntity e : rows) {
            String account = e.getAccountNumber();
            if (account == null || account.isEmpty()) {
                continue;
            }
            CustomerRecord agg = merged.get(account);
            if (agg == null) {
                agg = new CustomerRecord();
                agg.setAccountNumber(account);
                agg.setTransactionAmount(e.getTransactionAmount());
                agg.setBalance(e.getBalance());
                agg.setCurrency(e.getCurrency());
                agg.setEmail(e.getEmail());
                agg.setName(e.getName());
                agg.setStatementDate(e.getStatementDate() != null
                        ? e.getStatementDate().format(DateTimeFormatter.ISO_LOCAL_DATE)
                        : null);
                merged.put(account, agg);
            } else {
                // 收支情况累加
                BigDecimal existingTxn = toBigDecimal(agg.getTransactionAmount());
                BigDecimal newTxn = toBigDecimal(e.getTransactionAmount());
                BigDecimal sum = existingTxn.add(newTxn);
                if (sum.compareTo(BigDecimal.ZERO) != 0) {
                    agg.setTransactionAmount(sum.toPlainString());
                }
                // 余额取最新
                if (e.getBalance() != null && !e.getBalance().isEmpty()) {
                    agg.setBalance(e.getBalance());
                }
                // 其他字段为空则补齐
                if ((agg.getName() == null || agg.getName().isEmpty()) && e.getName() != null) {
                    agg.setName(e.getName());
                }
                if ((agg.getEmail() == null || agg.getEmail().isEmpty()) && e.getEmail() != null) {
                    agg.setEmail(e.getEmail());
                }
                if ((agg.getCurrency() == null || agg.getCurrency().isEmpty()) && e.getCurrency() != null) {
                    agg.setCurrency(e.getCurrency());
                }
                if ((agg.getStatementDate() == null || agg.getStatementDate().isEmpty())
                        && e.getStatementDate() != null) {
                    agg.setStatementDate(e.getStatementDate().format(DateTimeFormatter.ISO_LOCAL_DATE));
                }
            }
        }

        return new ArrayList<>(merged.values());
    }

    private BigDecimal toBigDecimal(String raw) {
        if (raw == null || raw.isBlank()) {
            return BigDecimal.ZERO;
        }
        String cleaned = raw.replace(",", "").trim();
        try {
            return new BigDecimal(cleaned);
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }
}

