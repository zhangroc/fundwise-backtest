package com.fundwise.repository;

import com.fundwise.entity.Fund;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 基金数据访问层
 */
@Repository
public interface FundRepository extends JpaRepository<Fund, String>, org.springframework.data.jpa.repository.JpaSpecificationExecutor<Fund> {
    
    /**
     * 根据基金类型查询
     */
    List<Fund> findByFundType(String fundType);
    
    /**
     * 根据风险等级查询
     */
    List<Fund> findByRiskLevel(String riskLevel);
    
    /**
     * 查询指数基金
     */
    List<Fund> findByIsIndexFund(Boolean isIndexFund);
    
    /**
     * 根据基金公司查询
     */
    List<Fund> findByFundCompany(String fundCompany);
    
    /**
     * 查询成立时间超过指定年限的基金
     */
    @Query("SELECT f FROM Fund f WHERE YEAR(CURRENT_DATE) - YEAR(f.establishmentDate) >= :minYears")
    List<Fund> findFundsOlderThanYears(Integer minYears);
    
    /**
     * 查询基金规模大于指定值的基金
     */
    @Query("SELECT f FROM Fund f WHERE f.totalAssets >= :minAssets")
    List<Fund> findFundsWithMinAssets(Double minAssets);
    
    /**
     * 根据基金名称模糊查询
     */
    List<Fund> findByFundNameContaining(String keyword);
    
    /**
     * 根据状态查询基金
     */
    List<Fund> findByStatus(String status);
    
    /**
     * 复合查询：指数基金且风险等级匹配
     */
    List<Fund> findByIsIndexFundAndRiskLevel(Boolean isIndexFund, String riskLevel);
    
    /**
     * 根据基金代码查询
     */
    java.util.Optional<Fund> findByFundCode(String fundCode);
    
    /**
     * 查询有净值数据的基金（navRecordCount > 0）
     */
    @Query("SELECT f FROM Fund f WHERE f.navRecordCount > 0")
    List<Fund> findFundsWithNavData();
    
    /**
     * 查询有净值数据的基金（分页）
     */
    @Query("SELECT f FROM Fund f WHERE f.navRecordCount > 0")
    Page<Fund> findFundsWithNavData(Pageable pageable);
}