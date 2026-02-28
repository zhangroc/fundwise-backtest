package com.fundwise.service;

import com.fundwise.entity.Fund;
import com.fundwise.entity.Portfolio;
import com.fundwise.entity.PortfolioHolding;
import com.fundwise.repository.FundRepository;
import com.fundwise.repository.PortfolioHoldingRepository;
import com.fundwise.repository.PortfolioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 投资组合服务类
 */
@Service
@Transactional
public class PortfolioService {
    
    private final PortfolioRepository portfolioRepository;
    private final PortfolioHoldingRepository holdingRepository;
    private final FundRepository fundRepository;
    
    public PortfolioService(PortfolioRepository portfolioRepository, 
                          PortfolioHoldingRepository holdingRepository,
                          FundRepository fundRepository) {
        this.portfolioRepository = portfolioRepository;
        this.holdingRepository = holdingRepository;
        this.fundRepository = fundRepository;
    }
    
    /**
     * 获取所有投资组合
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getAllPortfolios() {
        List<Portfolio> portfolios = portfolioRepository.findByIsTemplateFalseOrderByCreatedAtDesc();
        
        return portfolios.stream().map(this::convertPortfolioToMap).collect(Collectors.toList());
    }
    
    /**
     * 根据 ID 获取投资组合详情
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getPortfolioById(Long id) {
        Portfolio portfolio = portfolioRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("组合不存在，ID: " + id));
        
        return convertPortfolioToMap(portfolio);
    }
    
    /**
     * 创建投资组合
     */
    public Map<String, Object> createPortfolio(String name, String description, String riskLevel, 
                                               List<Map<String, Object>> holdings) {
        // 检查名称是否已存在
        List<Portfolio> existing = portfolioRepository.findByNameContainingIgnoreCaseOrderByCreatedAtDesc(name);
        if (!existing.isEmpty()) {
            throw new RuntimeException("组合名称已存在：" + name);
        }
        
        Portfolio portfolio = new Portfolio();
        portfolio.setName(name);
        portfolio.setDescription(description);
        portfolio.setRiskLevel(riskLevel);
        portfolio.setIsTemplate(false);
        
        portfolio = portfolioRepository.save(portfolio);
        
        // 添加持仓
        if (holdings != null && !holdings.isEmpty()) {
            addHoldingsToPortfolio(portfolio.getId(), holdings);
        }
        
        return getPortfolioById(portfolio.getId());
    }
    
    /**
     * 更新投资组合
     */
    public Map<String, Object> updatePortfolio(Long id, String name, String description, String riskLevel,
                                               List<Map<String, Object>> holdings) {
        Portfolio portfolio = portfolioRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("组合不存在，ID: " + id));
        
        portfolio.setName(name);
        portfolio.setDescription(description);
        portfolio.setRiskLevel(riskLevel);
        
        portfolioRepository.save(portfolio);
        
        // 更新持仓
        if (holdings != null) {
            // 删除现有持仓
            holdingRepository.deleteByPortfolioId(id);
            // 添加新持仓
            addHoldingsToPortfolio(id, holdings);
        }
        
        return getPortfolioById(id);
    }
    
    /**
     * 删除投资组合
     */
    public void deletePortfolio(Long id) {
        if (!portfolioRepository.existsById(id)) {
            throw new RuntimeException("组合不存在，ID: " + id);
        }
        portfolioRepository.deleteById(id);
    }
    
    /**
     * 复制投资组合
     */
    public Map<String, Object> duplicatePortfolio(Long id) {
        Portfolio source = portfolioRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("组合不存在，ID: " + id));
        
        Portfolio newPortfolio = new Portfolio();
        newPortfolio.setName(source.getName() + " (副本)");
        newPortfolio.setDescription(source.getDescription());
        newPortfolio.setRiskLevel(source.getRiskLevel());
        newPortfolio.setIsTemplate(false);
        
        newPortfolio = portfolioRepository.save(newPortfolio);
        
        // 复制持仓
        List<PortfolioHolding> sourceHoldings = holdingRepository.findByPortfolioIdOrderByCreatedAtAsc(id);
        for (PortfolioHolding holding : sourceHoldings) {
            PortfolioHolding newHolding = new PortfolioHolding();
            newHolding.setPortfolio(newPortfolio);
            newHolding.setFundCode(holding.getFundCode());
            newHolding.setFundName(holding.getFundName());
            newHolding.setFundType(holding.getFundType());
            newHolding.setRiskLevel(holding.getRiskLevel());
            newHolding.setTargetWeight(holding.getTargetWeight());
            holdingRepository.save(newHolding);
        }
        
        return getPortfolioById(newPortfolio.getId());
    }
    
    /**
     * 添加基金到组合
     */
    public Map<String, Object> addHoldingToPortfolio(Long portfolioId, String fundCode, 
                                                     String fundName, String type, 
                                                     String riskLevel, Double targetWeight) {
        Portfolio portfolio = portfolioRepository.findById(portfolioId)
            .orElseThrow(() -> new RuntimeException("组合不存在，ID: " + portfolioId));
        
        // 检查是否已存在
        if (holdingRepository.existsByPortfolioIdAndFundCode(portfolioId, fundCode)) {
            throw new RuntimeException("该基金已在组合中");
        }
        
        PortfolioHolding holding = new PortfolioHolding();
        holding.setPortfolio(portfolio);
        holding.setFundCode(fundCode);
        holding.setFundName(fundName);
        holding.setFundType(type);
        holding.setRiskLevel(riskLevel);
        holding.setTargetWeight(BigDecimal.valueOf(targetWeight));
        
        holdingRepository.save(holding);
        
        return getPortfolioById(portfolioId);
    }
    
    /**
     * 批量添加持仓到组合
     */
    private void addHoldingsToPortfolio(Long portfolioId, List<Map<String, Object>> holdings) {
        Portfolio portfolio = portfolioRepository.findById(portfolioId)
            .orElseThrow(() -> new RuntimeException("组合不存在，ID: " + portfolioId));
        
        for (Map<String, Object> holdingData : holdings) {
            String fundCode = (String) holdingData.get("fundCode");
            String fundName = (String) holdingData.get("fundName");
            String type = (String) holdingData.get("type");
            String riskLevel = (String) holdingData.get("riskLevel");
            Double targetWeight = convertToDouble(holdingData.get("targetWeight"));
            
            if (fundCode == null || targetWeight == null) {
                continue;
            }
            
            try {
                addHoldingToPortfolio(portfolioId, fundCode, fundName, type, riskLevel, targetWeight);
            } catch (Exception e) {
                // 跳过已存在的基金
                if (!e.getMessage().contains("已在组合中")) {
                    throw e;
                }
            }
        }
    }
    
    /**
     * 更新持仓权重
     */
    public void updateHoldingWeight(Long portfolioId, String fundCode, Double targetWeight) {
        PortfolioHolding holding = holdingRepository.findByPortfolioIdAndFundCode(portfolioId, fundCode)
            .orElseThrow(() -> new RuntimeException("持仓不存在"));
        
        holding.setTargetWeight(BigDecimal.valueOf(targetWeight));
        holdingRepository.save(holding);
    }
    
    /**
     * 删除持仓
     */
    public void removeHolding(Long portfolioId, String fundCode) {
        if (!holdingRepository.existsByPortfolioIdAndFundCode(portfolioId, fundCode)) {
            throw new RuntimeException("持仓不存在");
        }
        holdingRepository.deleteByPortfolioIdAndFundCode(portfolioId, fundCode);
    }
    
    /**
     * 运行回测
     */
    public Map<String, Object> runBacktest(Long portfolioId, String startDate, String endDate, 
                                          Double initialCapital) {
        Portfolio portfolio = portfolioRepository.findById(portfolioId)
            .orElseThrow(() -> new RuntimeException("组合不存在，ID: " + portfolioId));
        
        if (portfolio.getHoldings() == null || portfolio.getHoldings().isEmpty()) {
            throw new RuntimeException("组合中没有基金，无法回测");
        }
        
        // TODO: 实现真实回测逻辑
        // 这里返回模拟结果
        Map<String, Object> result = new HashMap<>();
        result.put("portfolioId", portfolioId);
        result.put("startDate", startDate);
        result.put("endDate", endDate);
        result.put("initialCapital", initialCapital);
        result.put("finalValue", initialCapital * 1.35);
        result.put("totalReturn", 0.35);
        result.put("annualizedReturn", 0.085);
        result.put("maxDrawdown", 0.092);
        result.put("sharpeRatio", 1.2);
        
        return result;
    }
    
    /**
     * 将 Portfolio 转换为 Map
     */
    private Map<String, Object> convertPortfolioToMap(Portfolio portfolio) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", portfolio.getId());
        map.put("name", portfolio.getName());
        map.put("description", portfolio.getDescription());
        map.put("riskLevel", portfolio.getRiskLevel());
        map.put("isTemplate", portfolio.getIsTemplate());
        map.put("createdAt", portfolio.getCreatedAt());
        map.put("updatedAt", portfolio.getUpdatedAt());
        
        // 转换持仓
        List<Map<String, Object>> holdingsList = new ArrayList<>();
        if (portfolio.getHoldings() != null) {
            for (PortfolioHolding holding : portfolio.getHoldings()) {
                Map<String, Object> holdingMap = new HashMap<>();
                holdingMap.put("id", holding.getId());
                holdingMap.put("fundCode", holding.getFundCode());
                holdingMap.put("fundName", holding.getFundName());
                holdingMap.put("type", holding.getFundType());
                holdingMap.put("riskLevel", holding.getRiskLevel());
                holdingMap.put("targetWeight", holding.getTargetWeight().doubleValue());
                holdingMap.put("createdAt", holding.getCreatedAt());
                holdingsList.add(holdingMap);
            }
        }
        map.put("holdings", holdingsList);
        map.put("holdingsCount", holdingsList.size());
        
        return map;
    }
    
    /**
     * 转换为 Double
     */
    private Double convertToDouble(Object obj) {
        if (obj == null) return null;
        if (obj instanceof Double) return (Double) obj;
        if (obj instanceof Integer) return ((Integer) obj).doubleValue();
        if (obj instanceof Long) return ((Long) obj).doubleValue();
        if (obj instanceof BigDecimal) return ((BigDecimal) obj).doubleValue();
        try {
            return Double.parseDouble(obj.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
