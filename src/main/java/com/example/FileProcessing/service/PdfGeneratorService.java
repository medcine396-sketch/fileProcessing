package com.example.FileProcessing.service;

import com.example.FileProcessing.model.CustomerRecord;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * PDF生成服务
 */
@Service
public class PdfGeneratorService {

    private static final String OUTPUT_DIR = "C:/Users/Selina/Desktop/outbound";

    private final EmailService emailService;

    public PdfGeneratorService(EmailService emailService) {
        this.emailService = emailService;
    }

    /**
     * 为每个客户记录生成PDF账单
     */
    public void generatePdfBills(List<CustomerRecord> records, String batchId) throws IOException {
        // 确保输出目录存在
        File outputDir = new File(OUTPUT_DIR);
        if (!outputDir.exists()) {
            outputDir.mkdirs();
        }

        for (CustomerRecord record : records) {
            String dateForFile = formatDateForFileName(record.getStatementDate());
            String pdfFileName = String.format("BILL_%s_%s.pdf", 
                record.getAccountNumber(), 
                dateForFile);
            File pdfFile = new File(outputDir, pdfFileName);
            
            generateSinglePdf(record, pdfFile);
            System.out.println("已生成PDF账单: " + pdfFile.getAbsolutePath());
        }
    }

    /**
     * 为每个客户生成 PDF 后，立即发送账单邮件
     * （可供批处理编排层调用，保证“生成后自动邮件发送”能力）
     */
    public void generatePdfBillsAndSendEmail(List<CustomerRecord> records, String batchId) throws IOException {
        // 复用原有 PDF 生成逻辑
        File outputDir = new File(OUTPUT_DIR);
        if (!outputDir.exists()) {
            outputDir.mkdirs();
        }

        for (CustomerRecord record : records) {
            String dateForFile = formatDateForFileName(record.getStatementDate());
            String pdfFileName = String.format("BILL_%s_%s.pdf",
                    record.getAccountNumber(),
                    dateForFile);
            File pdfFile = new File(outputDir, pdfFileName);

            // 1. 生成 PDF
            generateSinglePdf(record, pdfFile);
            System.out.println("已生成PDF账单: " + pdfFile.getAbsolutePath());

            // 2. 发送邮件（行级容错：失败只影响单个客户，可由上层汇总到 error_report）
            boolean success = emailService.sendStatementEmail(record, pdfFile);
            if (!success) {
                // 这里不抛出异常，中断整个批次，而是由上层编排将失败信息收集到错误报告
                System.err.println("发送账单邮件失败，账号: " + record.getAccountNumber()
                        + "，邮箱: " + record.getEmail());
            }
        }
    }

    /**
     * 生成单个客户的PDF账单
     */
    private void generateSinglePdf(CustomerRecord record, File pdfFile) throws IOException {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);

            try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
                // 设置字体（优先使用系统中文字体嵌入PDF，避免Helvetica不支持中文导致报错）
                Fonts fonts = resolveFonts(document);
                PDFont fontBold = fonts.bold;
                PDFont fontRegular = fonts.regular;
                
                float margin = 50;
                float yPosition = 750;
                float lineHeight = 20;
                float titleFontSize = 18;
                float normalFontSize = 12;

                // 标题
                contentStream.beginText();
                contentStream.setFont(fontBold, titleFontSize);
                contentStream.newLineAtOffset(margin, yPosition);
                contentStream.showText(fonts.unicode ? "客户账单" : "Customer Statement");
                contentStream.endText();
                yPosition -= 40;

                // 账单日期
                String statementDate = record.getStatementDate() != null ? 
                    record.getStatementDate() : LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
                contentStream.beginText();
                contentStream.setFont(fontRegular, normalFontSize);
                contentStream.newLineAtOffset(margin, yPosition);
                contentStream.showText((fonts.unicode ? "账单日期: " : "Statement Date: ") + safeText(statementDate, fonts.unicode));
                contentStream.endText();
                yPosition -= 30;

                // 分隔线
                contentStream.moveTo(margin, yPosition);
                contentStream.lineTo(550, yPosition);
                contentStream.stroke();
                yPosition -= 30;

                // 客户信息
                if (record.getName() != null && !record.getName().isEmpty()) {
                    contentStream.beginText();
                    contentStream.setFont(fontBold, normalFontSize);
                    contentStream.newLineAtOffset(margin, yPosition);
                    contentStream.showText(fonts.unicode ? "客户姓名: " : "Name: ");
                    contentStream.endText();
                    
                    contentStream.beginText();
                    contentStream.setFont(fontRegular, normalFontSize);
                    contentStream.newLineAtOffset(margin + 80, yPosition);
                    contentStream.showText(safeText(record.getName(), fonts.unicode));
                    contentStream.endText();
                    yPosition -= lineHeight;
                }

                contentStream.beginText();
                contentStream.setFont(fontBold, normalFontSize);
                contentStream.newLineAtOffset(margin, yPosition);
                contentStream.showText(fonts.unicode ? "账号: " : "Account: ");
                contentStream.endText();
                
                contentStream.beginText();
                contentStream.setFont(fontRegular, normalFontSize);
                contentStream.newLineAtOffset(margin + 80, yPosition);
                contentStream.showText(safeText(record.getAccountNumber(), fonts.unicode));
                contentStream.endText();
                yPosition -= lineHeight;

                contentStream.beginText();
                contentStream.setFont(fontBold, normalFontSize);
                contentStream.newLineAtOffset(margin, yPosition);
                contentStream.showText(fonts.unicode ? "邮箱: " : "Email: ");
                contentStream.endText();
                
                contentStream.beginText();
                contentStream.setFont(fontRegular, normalFontSize);
                contentStream.newLineAtOffset(margin + 80, yPosition);
                contentStream.showText(safeText(record.getEmail(), fonts.unicode));
                contentStream.endText();
                yPosition -= lineHeight;

                yPosition -= 10;

                // 分隔线
                contentStream.moveTo(margin, yPosition);
                contentStream.lineTo(550, yPosition);
                contentStream.stroke();
                yPosition -= 30;

                // 额度信息
                contentStream.beginText();
                contentStream.setFont(fontBold, normalFontSize + 2);
                contentStream.newLineAtOffset(margin, yPosition);
                contentStream.showText(fonts.unicode ? "账单额度: " : "Amount Due: ");
                contentStream.endText();
                
                String amountText = record.getBalance() != null ? record.getBalance() : "0.00";
                if (record.getCurrency() != null && !record.getCurrency().isEmpty()) {
                    amountText = amountText + " " + record.getCurrency();
                }
                contentStream.beginText();
                contentStream.setFont(fontBold, normalFontSize + 2);
                contentStream.newLineAtOffset(margin + 100, yPosition);
                contentStream.showText(safeText(amountText, fonts.unicode));
                contentStream.endText();
                yPosition -= 40;

                // 页脚
                contentStream.beginText();
                contentStream.setFont(fontRegular, 10);
                contentStream.newLineAtOffset(margin, 50);
                contentStream.showText(fonts.unicode
                    ? "此账单由系统自动生成，如有疑问请联系客服。"
                    : "This statement is generated automatically. Please contact support if you have questions.");
                contentStream.endText();
            }

            document.save(pdfFile);
        }
    }

    private String formatDateForFileName(String statementDate) {
        if (statementDate == null || statementDate.isEmpty()) {
            return LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        }
        try {
            LocalDate d = LocalDate.parse(statementDate, DateTimeFormatter.ISO_LOCAL_DATE);
            return d.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        } catch (DateTimeParseException ignored) {
            return LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        }
    }

    private static class Fonts {
        final PDFont regular;
        final PDFont bold;
        final boolean unicode;

        private Fonts(PDFont regular, PDFont bold, boolean unicode) {
            this.regular = regular;
            this.bold = bold;
            this.unicode = unicode;
        }
    }

    private Fonts resolveFonts(PDDocument document) {
        // Windows 常见中文字体路径（优先微软雅黑，其次黑体/宋体等）
        List<String> regularCandidates = new ArrayList<>();
        List<String> boldCandidates = new ArrayList<>();

        String winFonts = "C:\\Windows\\Fonts\\";
        // 优先使用 TTF（PDFBox 3.0.3 下更稳定；TTC 需要额外处理）
        regularCandidates.add(winFonts + "msyh.ttf");    // Microsoft YaHei (部分系统有)
        regularCandidates.add(winFonts + "simhei.ttf");  // 黑体
        regularCandidates.add(winFonts + "simsun.ttf");

        boldCandidates.add(winFonts + "msyhbd.ttf");
        boldCandidates.add(winFonts + "simhei.ttf");
        boldCandidates.add(winFonts + "msyh.ttf");

        PDFont regular = tryLoadUnicodeFont(document, regularCandidates);
        PDFont bold = tryLoadUnicodeFont(document, boldCandidates);

        if (regular != null && bold != null) {
            return new Fonts(regular, bold, true);
        }
        if (regular != null) {
            return new Fonts(regular, regular, true);
        }

        // 兜底：仍用Helvetica，但必须避免输出中文/非ASCII字符
        PDFont fallbackRegular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
        PDFont fallbackBold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
        System.err.println("未找到可用中文字体，将回退到Helvetica（PDF内容将使用英文/ASCII避免乱码）");
        return new Fonts(fallbackRegular, fallbackBold, false);
    }

    private PDFont tryLoadUnicodeFont(PDDocument document, List<String> candidates) {
        for (String path : candidates) {
            File f = new File(path);
            if (!f.exists() || !f.isFile()) continue;
            try {
                String lower = f.getName().toLowerCase();
                if (lower.endsWith(".ttc")) {
                    // 当前实现不处理 TTC；避免因 API/兼容性导致编译或运行失败
                    continue;
                }
                if (lower.endsWith(".ttf") || lower.endsWith(".otf")) {
                    try (InputStream in = new FileInputStream(f)) {
                        return PDType0Font.load(document, in, true);
                    }
                }
            } catch (Exception e) {
                System.err.println("加载字体失败: " + path + "，原因: " + e.getMessage());
            }
        }
        return null;
    }

    private String safeText(String s, boolean unicodeSupported) {
        if (s == null) return "";
        if (unicodeSupported) return s;
        // Helvetica(WinAnsi) 不支持中文等字符，回退时强制转为 ASCII
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); ) {
            int cp = s.codePointAt(i);
            i += Character.charCount(cp);
            if (cp >= 32 && cp <= 126) {
                sb.append((char) cp);
            } else {
                sb.append('?');
            }
        }
        return sb.toString();
    }
}

