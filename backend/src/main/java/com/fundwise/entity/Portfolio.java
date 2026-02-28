package com.fundwise.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 投资组合实体类
 * 对应数据库表：portfolio
 */
@Entity
@Table(name = "portfolio")
public class Portfolio {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "name", nullable = false, length = 255)
    private String name;
    
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;
    
    @Column(name = "risk_level", length = 20)
    private String riskLevel; // 保守、稳健、积极、自定义
    
    @Column(name = "is_template")
    private Boolean isTemplate = false;
    
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    @OneToMany(mappedBy = "portfolio", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PortfolioHolding> holdings = new ArrayList<>();
    
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
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    
    public String getRiskLevel() { return riskLevel; }
    public void setRiskLevel(String riskLevel) { this.riskLevel = riskLevel; }
    
    public Boolean getIsTemplate() { return isTemplate; }
    public void setIsTemplate(Boolean isTemplate) { this.isTemplate = isTemplate; }
    
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    
    public List<PortfolioHolding> getHoldings() { return holdings; }
    public void setHoldings(List<PortfolioHolding> holdings) { this.holdings = holdings; }
    
    public void addHolding(PortfolioHolding holding) {
        holdings.add(holding);
        holding.setPortfolio(this);
    }
    
    public void removeHolding(PortfolioHolding holding) {
        holdings.remove(holding);
        holding.setPortfolio(null);
    }
    
    @Override
    public String toString() {
        return "Portfolio{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", riskLevel='" + riskLevel + '\'' +
                ", holdingsCount=" + holdings.size() +
                '}';
    }
}
