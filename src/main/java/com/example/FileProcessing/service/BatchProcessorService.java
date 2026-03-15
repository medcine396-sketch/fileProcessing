package com.example.FileProcessing.service;

import com.example.FileProcessing.model.CustomerRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 批处理服务
 * 当检测到1-10个CSV文件时自动启动批处理，45分钟内完成
 */
@Service
public class BatchProcessorService {

    @Autowired
    private CsvParserService csvParserService;

    @Autowired
    private StatementRecordService statementRecordService;

    // 跟踪已处理的文件
    private final Set<String> processedFiles = ConcurrentHashMap.newKeySet();
    
    // 跟踪正在处理的批次
    private final Map<String, BatchInfo> activeBatches = new ConcurrentHashMap<>();

    /**
     * 处理一批CSV文件（1-10个）
     */
    public void processBatch(List<File> csvFiles) {
        if (csvFiles == null || csvFiles.isEmpty() || csvFiles.size() > 10) {
            return;
        }

        String batchId = "BATCH_" + LocalDateTime.now().format(
            java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        
        BatchInfo batchInfo = new BatchInfo(batchId, csvFiles.size(), LocalDateTime.now());
        activeBatches.put(batchId, batchInfo);

        System.out.println("========================================");
        System.out.println("开始批处理: " + batchId);
        System.out.println("文件数量: " + csvFiles.size());
        System.out.println("开始时间: " + batchInfo.getStartTime());
        System.out.println("========================================");

        // 异步处理，确保在45分钟内完成
        CompletableFuture.runAsync(() -> {
            try {
                List<CustomerRecord> allRecords = new ArrayList<>();

                // 解析所有CSV文件（从 CSV 中读取账号、姓名、额度、币种、邮箱、账单日期等信息）
                for (File csvFile : csvFiles) {
                    try {
                        List<CustomerRecord> records = csvParserService.parseCsvFile(csvFile);
                        allRecords.addAll(records);
                        System.out.println("已解析文件: " + csvFile.getName() + ", 记录数: " + records.size());
                        
                        // 标记文件为已处理
                        processedFiles.add(csvFile.getAbsolutePath());
                    } catch (IOException e) {
                        System.err.println("解析文件失败: " + csvFile.getName() + ", 错误: " + e.getMessage());
                    }
                }

                // 仅将解析结果写入 H2，由后续结算任务统一生成 PDF + 邮件
                if (!allRecords.isEmpty()) {
                    statementRecordService.saveRecords(allRecords);
                    System.out.println("批处理完成，本批共写入中间库记录数: " + allRecords.size());
                } else {
                    System.out.println("批处理完成，但没有找到有效的客户记录");
                }

                batchInfo.setEndTime(LocalDateTime.now());
                long duration = ChronoUnit.MINUTES.between(batchInfo.getStartTime(), batchInfo.getEndTime());
                System.out.println("批处理耗时: " + duration + " 分钟");
                
                if (duration > 45) {
                    System.err.println("警告: 批处理超过45分钟限制！");
                }

            } catch (Exception e) {
                System.err.println("批处理失败: " + e.getMessage());
                e.printStackTrace();
            } finally {
                activeBatches.remove(batchId);
            }
        });
    }

    /**
     * 检查文件是否已被处理
     */
    public boolean isFileProcessed(File file) {
        return processedFiles.contains(file.getAbsolutePath());
    }

    /**
     * 获取待处理的CSV文件列表（1-10个）
     */
    public List<File> getPendingCsvFiles(File[] allFiles) {
        List<File> pendingFiles = new ArrayList<>();
        
        for (File file : allFiles) {
            if (file.isFile() && 
                file.getName().startsWith("STATEMENT") && 
                file.getName().endsWith(".csv") &&
                !isFileProcessed(file)) {
                pendingFiles.add(file);
                
                // 最多收集10个文件
                if (pendingFiles.size() >= 10) {
                    break;
                }
            }
        }
        
        return pendingFiles;
    }

    /**
     * 批次信息
     */
    private static class BatchInfo {
        private String batchId;
        private int fileCount;
        private LocalDateTime startTime;
        private LocalDateTime endTime;

        public BatchInfo(String batchId, int fileCount, LocalDateTime startTime) {
            this.batchId = batchId;
            this.fileCount = fileCount;
            this.startTime = startTime;
        }

        public String getBatchId() { return batchId; }
        public int getFileCount() { return fileCount; }
        public LocalDateTime getStartTime() { return startTime; }
        public LocalDateTime getEndTime() { return endTime; }
        public void setEndTime(LocalDateTime endTime) { this.endTime = endTime; }
    }
}

