package com.example.FileProcessing.entity;

import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * H2 中间表：保存从 CSV 解析出的原始行（按账号/日期后续聚合）。
 */
@Entity
@Table(name = "statement_record")
public class StatementRecordEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 32)
    private String accountNumber;

    /**
     * 单行收支金额（收入为正，支出为负）
     */
    @Column
    private String transactionAmount;

    /**
     * 当前余额
     */
    @Column
    private String balance;

    @Column(length = 8)
    private String currency;

    @Column(length = 256)
    private String email;

    @Column(length = 128)
    private String name;

    /**
     * 账单日期（如果 CSV 未提供可为空，结算时可按业务规则处理）
     */
    @Column
    private LocalDate statementDate;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    /**
     * 是否已参与结算（生成 PDF + 发送邮件）
     */
    @Column(nullable = false)
    private boolean settled = false;

    public Long getId() {
        return id;
        }

    public void setId(Long id) {
        this.id = id;
    }

    public String getAccountNumber() {
        return accountNumber;
    }

    public void setAccountNumber(String accountNumber) {
        this.accountNumber = accountNumber;
    }

    public String getTransactionAmount() {
        return transactionAmount;
    }

    public void setTransactionAmount(String transactionAmount) {
        this.transactionAmount = transactionAmount;
    }

    public String getBalance() {
        return balance;
    }

    public void setBalance(String balance) {
        this.balance = balance;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public LocalDate getStatementDate() {
        return statementDate;
    }

    public void setStatementDate(LocalDate statementDate) {
        this.statementDate = statementDate;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public boolean isSettled() {
        return settled;
    }

    public void setSettled(boolean settled) {
        this.settled = settled;
    }
}

