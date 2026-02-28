package com.fundwise.repository;

import com.fundwise.entity.Portfolio;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 投资组合数据访问接口
 */
@Repository
public interface PortfolioRepository extends JpaRepository<Portfolio, Long> {
    
    /**
     * 查询所有非模板组合
     */
    List<Portfolio> findByIsTemplateFalseOrderByCreatedAtDesc();
    
    /**
     * 查询所有模板组合
     */
    List<Portfolio> findByIsTemplateTrueOrderByNameAsc();
    
    /**
     * 根据风险等级查询组合
     */
    List<Portfolio> findByRiskLevelOrderByCreatedAtDesc(String riskLevel);
    
    /**
     * 搜索组合（按名称）
     */
    List<Portfolio> findByNameContainingIgnoreCaseOrderByCreatedAtDesc(String keyword);
    
    /**
     * 查询组合数量
     */
    long countByIsTemplateFalse();
}
