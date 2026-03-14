package com.example.FileProcessing.model;

/**
 * 客户记录模型
 */
public class CustomerRecord {
    private String accountNumber;  // 账号
    private String balance;        // 额度
    private String currency;       // 币种（可选，但建议提供）
    private String email;          // 邮箱
    private String name;           // 姓名（可选）
    private String statementDate;  // 账单日期（可选）

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

