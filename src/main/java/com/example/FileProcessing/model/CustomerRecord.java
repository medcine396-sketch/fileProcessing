package com.example.FileProcessing.model;

/**
 * 客户记录模型
 */
public class CustomerRecord {
    private String accountNumber;      // 账号（8位数字）
    /**
     * 单行收支金额（收入为正，支出为负），来自 CSV 中的“收支情况”列
     */
    private String transactionAmount;  // 收支情况（正数/负数）
    /**
     * 当前余额，来自 CSV 中的“余额”列
     */
    private String balance;            // 余额
    private String currency;           // 币种（ISO 4217）
    private String email;              // 邮箱
    private String name;               // 姓名（可选，中英文均可）
    private String statementDate;      // 账单日期（可选）

    public CustomerRecord() {
    }

    public CustomerRecord(String accountNumber, String balance, String email) {
        this.accountNumber = accountNumber;
        this.balance = balance;
        this.email = email;
    }

    // Getters and Setters
    public String getAccountNumber() {
        return accountNumber;
    }

    public void setAccountNumber(String accountNumber) {
        this.accountNumber = accountNumber;
    }

    public String getBalance() {
        return balance;
    }

    public void setBalance(String balance) {
        this.balance = balance;
    }

    public String getTransactionAmount() {
        return transactionAmount;
    }

    public void setTransactionAmount(String transactionAmount) {
        this.transactionAmount = transactionAmount;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getStatementDate() {
        return statementDate;
    }

    public void setStatementDate(String statementDate) {
        this.statementDate = statementDate;
    }
}

