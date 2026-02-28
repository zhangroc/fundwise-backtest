package com.fundwise.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 组合持仓实体类
 * 对应数据库表：portfolio_holding
 */
@Entity
@Table(name = "portfolio_holding", 
       uniqueConstraints = @UniqueConstraint(columnNames = {"portfolio_id", "fund_id"}))
public class PortfolioHolding {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "portfolio_id", nullable = false)
    private Portfolio portfolio;
    
    @Column(name = "fund_code", nullable = false, length = 10)
    private String fundCode;
    
    @Column(name = "fund_name", length = 100)
    private String fundName;
    
    @Column(name = "fund_type", length = 50)
    private String fundType;
    
    @Column(name = "risk_level", length = 20)
    private String riskLevel;
    
    @Column(name = "target_weight", nullable = false, precision = 5, scale = 4)
    private BigDecimal targetWeight; // 目标权重 (0~1)
    
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
    
    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    
    public Portfolio getPortfolio() { return portfolio; }
    public void setPortfolio(Portfolio portfolio) { this.portfolio = portfolio; }
    
    public String getFundCode() { return fundCode; }
    public void setFundCode(String fundCode) { this.fundCode = fundCode; }
    
    public String getFundName() { return fundName; }
    public void setFundName(String fundName) { this.fundName = fundName; }
    
    public String getFundType() { return fundType; }
    public void setFundType(String fundType) { this.fundType = fundType; }
    
    public String getRiskLevel() { return riskLevel; }
    public void setRiskLevel(String riskLevel) { this.riskLevel = riskLevel; }
    
    public BigDecimal getTargetWeight() { return targetWeight; }
    public void setTargetWeight(BigDecimal targetWeight) { this.targetWeight = targetWeight; }
    
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    
    @Override
    public String toString() {
        return "PortfolioHolding{" +
                "id=" + id +
                ", portfolioId=" + (portfolio != null ? portfolio.getId() : null) +
                ", fundCode=" + fundCode +
                ", targetWeight=" + targetWeight +
                '}';
    }
}
