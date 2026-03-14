package com.example.FileProcessing.service;

import com.example.FileProcessing.model.CustomerRecord;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * CSV解析服务
 */
@Service
public class CsvParserService {

    /**
     * 解析CSV文件，提取客户记录
     * 支持多种CSV格式，自动检测列名
     */
    public List<CustomerRecord> parseCsvFile(File csvFile) throws IOException {
        List<CustomerRecord> records = new ArrayList<>();

        try (FileReader reader = new FileReader(csvFile, StandardCharsets.UTF_8);
             CSVParser parser = CSVFormat.DEFAULT.withFirstRecordAsHeader().parse(reader)) {

            // 获取表头
            var headers = parser.getHeaderMap();
            
            // 尝试识别列名（支持中英文）
            String accountCol = findColumn(headers, "账号", "account", "accountNumber", "account_number");
            String balanceCol = findColumn(headers, "额度", "balance", "amount", "金额");
            String currencyCol = findColumn(headers, "币种", "currency", "ccy", "curr");
            String emailCol = findColumn(headers, "邮箱", "email", "e-mail");
            String nameCol = findColumn(headers, "姓名", "name", "customerName", "客户姓名");
            String dateCol = findColumn(headers, "日期", "date", "statementDate", "账单日期");

            int rowNum = 1; // 仅用于日志（不包含表头）
            for (CSVRecord record : parser) {
                CustomerRecord customer = new CustomerRecord();
                
                if (accountCol != null && record.isSet(accountCol)) {
                    customer.setAccountNumber(safeTrim(record.get(accountCol)));
                }
                if (balanceCol != null && record.isSet(balanceCol)) {
                    customer.setBalance(normalizeAmount(record.get(balanceCol)));
                }
                if (currencyCol != null && record.isSet(currencyCol)) {
                    customer.setCurrency(normalizeCurrency(record.get(currencyCol)));
                }
                if (emailCol != null && record.isSet(emailCol)) {
                    customer.setEmail(safeTrim(record.get(emailCol)));
                }
                if (nameCol != null && record.isSet(nameCol)) {
                    customer.setName(safeTrim(record.get(nameCol)));
                }
                if (dateCol != null && record.isSet(dateCol)) {
                    customer.setStatementDate(normalizeDate(record.get(dateCol)));
                }

                // 记录校验（基于你提供的样例：账号/金额/币种/邮箱/日期）
                String invalidReason = validate(customer);
                if (invalidReason == null) {
                    records.add(customer);
                } else {
                    System.err.println("跳过无效CSV记录(行 " + rowNum + "): " + invalidReason);
                }
                rowNum++;
            }
        }

        return records;
    }

    /**
     * 查找匹配的列名（不区分大小写）
     */
    private String findColumn(java.util.Map<String, Integer> headers, String... possibleNames) {
        for (String name : possibleNames) {
            for (String header : headers.keySet()) {
                if (header.trim().equalsIgnoreCase(name)) {
                    return header;
                }
            }
        }
        return null;
    }

    private String safeTrim(String s) {
        return s == null ? null : s.trim();
    }

    private String normalizeCurrency(String raw) {
        String v = safeTrim(raw);
        if (v == null || v.isEmpty()) return null;
        return v.toUpperCase(Locale.ROOT);
    }

    private String normalizeAmount(String raw) {
        String v = safeTrim(raw);
        if (v == null || v.isEmpty()) return null;
        String cleaned = v.replace(",", "");
        try {
            // 仅用于校验合法数字；存储仍保持原始精度字符串
            new BigDecimal(cleaned);
            return cleaned;
        } catch (NumberFormatException e) {
            return v; // 让 validate() 给出明确原因
        }
    }

    private String normalizeDate(String raw) {
        String v = safeTrim(raw);
        if (v == null || v.isEmpty()) return null;

        // 支持样例格式：2026/3/4 以及常见 yyyy-MM-dd / yyyy/MM/dd
        DateTimeFormatter[] formatters = new DateTimeFormatter[] {
            DateTimeFormatter.ofPattern("yyyy/M/d"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd"),
            DateTimeFormatter.ISO_LOCAL_DATE
        };

        for (DateTimeFormatter f : formatters) {
            try {
                LocalDate d = LocalDate.parse(v, f);
                return d.format(DateTimeFormatter.ISO_LOCAL_DATE);
            } catch (DateTimeParseException ignored) {
            }
        }
        return v; // 让 validate() 给出明确原因
    }

    private String validate(CustomerRecord r) {
        if (r.getAccountNumber() == null || r.getAccountNumber().isEmpty()) {
            return "缺少账号(account)";
        }
        if (r.getEmail() == null || r.getEmail().isEmpty() || !r.getEmail().contains("@")) {
            return "邮箱无效(email)";
        }

        if (r.getBalance() == null || r.getBalance().isEmpty()) {
            return "缺少金额(amount)";
        }
        try {
            new BigDecimal(r.getBalance().replace(",", ""));
        } catch (NumberFormatException e) {
            return "金额格式无效(amount): " + r.getBalance();
        }

        if (r.getCurrency() == null || r.getCurrency().isEmpty()) {
            return "缺少币种(currency)";
        }
        if (!r.getCurrency().matches("^[A-Z]{3}$")) {
            return "币种格式无效(currency): " + r.getCurrency();
        }

        if (r.getStatementDate() != null && !r.getStatementDate().isEmpty()) {
            try {
                LocalDate.parse(r.getStatementDate(), DateTimeFormatter.ISO_LOCAL_DATE);
            } catch (DateTimeParseException e) {
                return "日期格式无效(date): " + r.getStatementDate();
            }
        }

        return null;
    }
}

