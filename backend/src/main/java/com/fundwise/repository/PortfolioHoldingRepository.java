package com.fundwise.repository;

import com.fundwise.entity.PortfolioHolding;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * 组合持仓数据访问接口
 */
@Repository
public interface PortfolioHoldingRepository extends JpaRepository<PortfolioHolding, Long> {
    
    /**
     * 查询组合的所有持仓
     */
    List<PortfolioHolding> findByPortfolioIdOrderByCreatedAtAsc(Long portfolioId);
    
    /**
     * 查询组合的特定持仓
     */
    Optional<PortfolioHolding> findByPortfolioIdAndFundCode(Long portfolioId, String fundCode);
    
    /**
     * 检查组合中是否已存在某基金
     */
    boolean existsByPortfolioIdAndFundCode(Long portfolioId, String fundCode);
    
    /**
     * 删除组合的所有持仓
     */
    @Transactional
    void deleteByPortfolioId(Long portfolioId);
    
    /**
     * 删除组合的特定持仓
     */
    @Transactional
    void deleteByPortfolioIdAndFundCode(Long portfolioId, String fundCode);
}
