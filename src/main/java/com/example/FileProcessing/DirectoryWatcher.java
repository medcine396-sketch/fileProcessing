package com.example.FileProcessing;

import com.example.FileProcessing.service.BatchProcessorService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.File;
import java.time.LocalTime;
import java.util.List;

@Component
public class DirectoryWatcher {

    private final String folderPath = "C:\\Users\\Selina\\Desktop\\testPy\\inbound";

    @Autowired
    private BatchProcessorService batchProcessorService;

    @Scheduled(fixedRate = 10000)  // 每隔10秒检查一次
    public void checkFolder() {
        System.out.println("Watcher tick " + LocalTime.now());
        // 只在晚上 6 点～10 点运行
        LocalTime now = LocalTime.now();
        if (now.isBefore(LocalTime.of(9, 0)) || now.isAfter(LocalTime.of(21, 0))) {
            return; // 不在时间段就不工作
        }

        File folder = new File(folderPath);
        if (!folder.exists() || !folder.isDirectory()) {
            System.out.println("文件夹不存在: " + folderPath);
            return;
        }

        File[] files = folder.listFiles();
        if (files == null || files.length == 0) {
            return;
        }

        // 获取待处理的CSV文件（1-10个）
        List<File> pendingFiles = batchProcessorService.getPendingCsvFiles(files);

        if (pendingFiles.isEmpty()) {
            return;
        }

        System.out.println("检测到 " + pendingFiles.size() + " 个待处理的CSV文件");
        for (File file : pendingFiles) {
            System.out.println("  - " + file.getName());
        }

        // 当有1-10个文件时，自动启动批处理
        if (pendingFiles.size() >= 1 && pendingFiles.size() <= 10) {
            System.out.println("满足批处理条件，启动批处理...");
            batchProcessorService.processBatch(pendingFiles);
        } else if (pendingFiles.size() > 10) {
            System.out.println("警告: 待处理文件超过10个，将分批处理");
            // 分批处理，每次最多10个
            for (int i = 0; i < pendingFiles.size(); i += 10) {
                int end = Math.min(i + 10, pendingFiles.size());
                List<File> batch = pendingFiles.subList(i, end);
                batchProcessorService.processBatch(batch);
            }
        }
    }
}

