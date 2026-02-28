package com.fundwise.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 基金信息实体类
 * 对应数据库表：fund
 */
@Entity
@Table(name = "fund")
public class Fund {
    @Id
    @Column(name = "fund_code", length = 10)
    private String fundCode;
    
    @Column(name = "fund_name", nullable = false, length = 100)
    private String fundName;
    
    @Column(name = "fund_type", length = 20)
    private String fundType; // 如：股票型、混合型、债券型、指数型等
    
    @Column(name = "fund_company", length = 50)
    private String fundCompany;
    
    @Column(name = "establishment_date")
    private LocalDate establishmentDate;
    
    @Column(name = "status", length = 10)
    private String status; // 如：active, closed, merged
    
    @Column(name = "is_index_fund")
    private Boolean isIndexFund = false; // 是否为指数基金
    
    @Column(name = "risk_level", length = 10)
    private String riskLevel; // 风险等级：保守、稳健、积极
    
    @Column(name = "management_fee_rate")
    private Double managementFeeRate; // 管理费率
    
    @Column(name = "custodian_fee_rate")
    private Double custodianFeeRate; // 托管费率
    
    @Column(name = "total_assets")
    private Double totalAssets; // 基金规模（亿元）
    
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
    
    // Getters and Setters
    /**
     * JPA requires an getId() method when using @GeneratedValue or for consistency
     * Returns the fundCode as the identifier
     */
    public String getId() { return fundCode; }
    
    public String getFundCode() { return fundCode; }
    public void setFundCode(String fundCode) { this.fundCode = fundCode; }
    
    public String getFundName() { return fundName; }
    public void setFundName(String fundName) { this.fundName = fundName; }
    
    public String getFundType() { return fundType; }
    public void setFundType(String fundType) { this.fundType = fundType; }
    
    public String getFundCompany() { return fundCompany; }
    public void setFundCompany(String fundCompany) { this.fundCompany = fundCompany; }
    
    public LocalDate getEstablishmentDate() { return establishmentDate; }
    public void setEstablishmentDate(LocalDate establishmentDate) { this.establishmentDate = establishmentDate; }
    
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    
    public Boolean getIsIndexFund() { return isIndexFund; }
    public void setIsIndexFund(Boolean isIndexFund) { this.isIndexFund = isIndexFund; }
    
    public String getRiskLevel() { return riskLevel; }
    public void setRiskLevel(String riskLevel) { this.riskLevel = riskLevel; }
    
    public Double getManagementFeeRate() { return managementFeeRate; }
    public void setManagementFeeRate(Double managementFeeRate) { this.managementFeeRate = managementFeeRate; }
    
    public Double getCustodianFeeRate() { return custodianFeeRate; }
    public void setCustodianFeeRate(Double custodianFeeRate) { this.custodianFeeRate = custodianFeeRate; }
    
    public Double getTotalAssets() { return totalAssets; }
    public void setTotalAssets(Double totalAssets) { this.totalAssets = totalAssets; }
    
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    
    @Override
    public String toString() {
        return "Fund{" +
                "fundCode='" + fundCode + '\'' +
                ", fundName='" + fundName + '\'' +
                ", fundType='" + fundType + '\'' +
                ", isIndexFund=" + isIndexFund +
                '}';
    }
}