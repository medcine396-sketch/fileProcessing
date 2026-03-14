package com.example.FileProcessing.service;

import com.example.FileProcessing.model.CustomerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * 邮件发送服务（负责将账单 PDF 以附件形式发送给客户）
 *
 * 设计要点：
 * - 与 PDF 生成解耦，只依赖已有的 PDF 文件和 CustomerRecord 中的邮箱等字段；
 * - 行级错误：单个客户发送失败只记录错误，不中断整个批次；
 * - 简单重试：对临时性错误进行有限次数的重试（指数退避）；
 * - 预留审计与指标的扩展点（发送成功/失败事件）。
 */
@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;

    /**
     * 发件人邮箱地址（从配置中注入）
     * 例如：mail.from=no-reply@bank.com
     */
    @Value("${mail.from:no-reply@example.com}")
    private String fromAddress;

    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    /**
     * 发送单个客户的账单邮件（带 PDF 附件），包含有限次数重试。
     *
     * @param record 客户记录（需包含邮箱、姓名、账号等）
     * @param pdfFile 已生成的 PDF 账单文件
     * @return true 表示最终发送成功，false 表示最终失败（调用方应记录到 error_report）
     */
    public boolean sendStatementEmail(CustomerRecord record, File pdfFile) {
        if (record == null || pdfFile == null) {
            log.warn("发送账单邮件失败：record 或 pdfFile 为空");
            return false;
        }
        String to = record.getEmail();
        if (to == null || to.isBlank()) {
            log.warn("发送账单邮件失败：客户账号 {} 邮箱为空", record.getAccountNumber());
            return false;
        }
        if (!pdfFile.exists() || !pdfFile.isFile()) {
            log.warn("发送账单邮件失败：账单文件不存在 {}", pdfFile.getAbsolutePath());
            return false;
        }

        int maxAttempts = 3;
        long baseBackoffMillis = 1000L; // 1 秒起步的指数退避

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                sendOnce(record, pdfFile);
                // TODO: 在这里记录“发送成功”审计事件 / 指标
                return true;
            } catch (MessagingException | MailException ex) {
                log.warn("发送账单邮件失败（第 {} 次尝试），账号 {}，原因：{}",
                        attempt, record.getAccountNumber(), ex.getMessage());
                if (attempt == maxAttempts) {
                    // TODO: 在这里记录“发送失败”审计事件 / 指标，并交由错误报告模块汇总
                    return false;
                }
                // 简单指数退避
                long sleepMillis = (long) (baseBackoffMillis * Math.pow(2, attempt - 1));
                try {
                    Thread.sleep(sleepMillis);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
        }
        return false;
    }

    private void sendOnce(CustomerRecord record, File pdfFile) throws MessagingException {
        MimeMessage mimeMessage = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(
                mimeMessage,
                true,
                StandardCharsets.UTF_8.name()
        );

        String to = record.getEmail();
        String customerName = record.getName() != null ? record.getName() : record.getAccountNumber();
        String subject = "您的银行账单 / Bank Statement";

        // 简单模板：后续可替换为 Freemarker / Thymeleaf 模板
        String body = buildEmailBody(record);

        helper.setFrom(fromAddress);
        helper.setTo(to);
        helper.setSubject(subject);
        helper.setText(body, false);
        helper.addAttachment(pdfFile.getName(), pdfFile);

        mailSender.send(mimeMessage);
        log.info("已发送账单邮件给 {} ({})，附件：{}", customerName, to, pdfFile.getName());
    }

    private String buildEmailBody(CustomerRecord record) {
        String customerName = record.getName() != null ? record.getName() : "";
        String account = record.getAccountNumber() != null ? record.getAccountNumber() : "";
        String amount = record.getBalance() != null ? record.getBalance() : "0.00";
        String currency = record.getCurrency() != null ? record.getCurrency() : "";
        String statementDate = record.getStatementDate() != null ? record.getStatementDate() : "";

        StringBuilder sb = new StringBuilder();
        sb.append("尊敬的客户 ").append(customerName).append("，您好！\n\n")
                .append("附件为您账号 ").append(account).append(" 的对账单，请查收。\n")
                .append("账单日期：").append(statementDate).append("\n")
                .append("账单金额：").append(amount).append(" ").append(currency).append("\n\n")
                .append("本邮件由系统自动发送，请勿直接回复。如有疑问，请联系银行客服。\n\n")
                .append("Dear Customer,\n\n")
                .append("Please find attached your latest bank statement for account ")
                .append(account).append(".\n")
                .append("Statement date: ").append(statementDate).append("\n")
                .append("Amount: ").append(amount).append(" ").append(currency).append("\n\n")
                .append("This email is generated automatically. Please contact customer service if you have any questions.\n");
        return sb.toString();
    }
}

