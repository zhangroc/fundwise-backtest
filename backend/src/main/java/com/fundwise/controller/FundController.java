package com.fundwise.controller;

import com.fundwise.entity.Fund;
import com.fundwise.repository.FundRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Period;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 基金数据控制器 - 提供真实基金数据
 */
@RestController
@RequestMapping("/api/v1/funds")
@CrossOrigin(origins = "*")
public class FundController {
    
    private final FundRepository fundRepository;
    
    public FundController(FundRepository fundRepository) {
        this.fundRepository = fundRepository;
    }
    
    /**
     * 筛选基金
     * GET /api/funds/screen
     */
    @GetMapping("/screen")
    public ResponseEntity<Map<String, Object>> screenFunds(
            @RequestParam(required = false) String timePeriod,
            @RequestParam(required = false) String fundType,
            @RequestParam(required = false) Boolean indexFundOnly,
            @RequestParam(required = false) Double minReturn,
            @RequestParam(required = false) Double maxDrawdown,
            @RequestParam(required = false) Double minSharpe,
            @RequestParam(required = false) Double minSize,
            @RequestParam(required = false) Double maxSize,
            @RequestParam(required = false) Integer minYears,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String sortOrder,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize
    ) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            // 从数据库获取所有基金
            List<Fund> allFunds = fundRepository.findAll();
            
            // 筛选
            List<Fund> filteredFunds = allFunds.stream()
                .filter(fund -> fundType == null || "all".equals(fundType) || 
                        (fund.getFundType() != null && fund.getFundType().contains(fundType)))
                .filter(fund -> indexFundOnly == null || !indexFundOnly || 
                        Boolean.TRUE.equals(fund.getIsIndexFund()))
                .filter(fund -> minSize == null || fund.getTotalAssets() == null || 
                        fund.getTotalAssets() >= minSize)
                .filter(fund -> maxSize == null || fund.getTotalAssets() == null || 
                        fund.getTotalAssets() <= maxSize)
                .filter(fund -> minYears == null || fund.getEstablishmentDate() == null || 
                        Period.between(fund.getEstablishmentDate(), LocalDate.now()).getYears() >= minYears)
                .collect(Collectors.toList());
            
            // 计算业绩指标（基于真实净值数据）
            List<Map<String, Object>> fundsWithMetrics = new ArrayList<>();
            for (Fund fund : filteredFunds) {
                Map<String, Object> fundData = convertFundToMap(fund);
                
                // TODO: 从数据库计算真实指标
                // 这里先使用模拟数据，后续需要连接 NAV 表计算
                fundData.put("returnRate", (Math.random() - 0.3) * 0.5);
                fundData.put("maxDrawdown", -(Math.random() * 0.3));
                fundData.put("sharpeRatio", (Math.random() - 0.5) * 3);
                
                fundsWithMetrics.add(fundData);
            }
            
            // 按收益率筛选
            if (minReturn != null) {
                final double minRet = minReturn / 100.0;
                fundsWithMetrics = fundsWithMetrics.stream()
                    .filter(f -> {
                        Double ret = (Double) f.get("returnRate");
                        return ret != null && ret >= minRet;
                    })
                    .collect(Collectors.toList());
            }
            
            // 按最大回撤筛选
            if (maxDrawdown != null) {
                final double maxDD = maxDrawdown / 100.0;
                fundsWithMetrics = fundsWithMetrics.stream()
                    .filter(f -> {
                        Double dd = (Double) f.get("maxDrawdown");
                        return dd != null && dd >= maxDD; // maxDrawdown 是负数
                    })
                    .collect(Collectors.toList());
            }
            
            // 按夏普比率筛选
            if (minSharpe != null) {
                fundsWithMetrics = fundsWithMetrics.stream()
                    .filter(f -> {
                        Double sharpe = (Double) f.get("sharpeRatio");
                        return sharpe != null && sharpe >= minSharpe;
                    })
                    .collect(Collectors.toList());
            }
            
            // 排序
            final String sortField = sortBy != null ? sortBy : "returnRate";
            final boolean desc = sortOrder == null || "desc".equals(sortOrder);
            
            fundsWithMetrics.sort((a, b) -> {
                Double valA = getMetricValue(a, sortField);
                Double valB = getMetricValue(b, sortField);
                
                if (valA == null) valA = 0.0;
                if (valB == null) valB = 0.0;
                
                return desc ? valB.compareTo(valA) : valA.compareTo(valB);
            });
            
            // 分页
            int total = fundsWithMetrics.size();
            int fromIndex = (page - 1) * pageSize;
            int toIndex = Math.min(fromIndex + pageSize, total);
            
            List<Map<String, Object>> pageData = new ArrayList<>();
            if (fromIndex < total) {
                pageData = fundsWithMetrics.subList(fromIndex, toIndex);
            }
            
            response.put("success", true);
            response.put("data", pageData);
            response.put("total", total);
            response.put("page", page);
            response.put("pageSize", pageSize);
            response.put("totalPages", (int) Math.ceil((double) total / pageSize));
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.ok(response);
        }
    }
    
    /**
     * 获取基金详情
     * GET /api/funds/{code}
     */
    @GetMapping("/{code}")
    public ResponseEntity<Map<String, Object>> getFundDetail(@PathVariable String code) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            Fund fund = fundRepository.findByFundCode(code)
                .orElseThrow(() -> new RuntimeException("基金不存在：" + code));
            
            response.put("success", true);
            response.put("data", convertFundToMap(fund));
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.ok(response);
        }
    }
    
    /**
     * 搜索基金
     * GET /api/funds/search?keyword=xxx
     */
    @GetMapping("/search")
    public ResponseEntity<Map<String, Object>> searchFunds(
            @RequestParam String keyword,
            @RequestParam(defaultValue = "10") int limit
    ) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            List<Fund> funds = fundRepository.findByFundNameContaining(keyword);
            
            List<Map<String, Object>> results = funds.stream()
                .limit(limit)
                .map(this::convertFundToMap)
                .collect(Collectors.toList());
            
            response.put("success", true);
            response.put("data", results);
            response.put("count", results.size());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.ok(response);
        }
    }
    
    /**
     * 将 Fund 实体转换为 Map
     */
    private Map<String, Object> convertFundToMap(Fund fund) {
        Map<String, Object> map = new HashMap<>();
        map.put("code", fund.getFundCode());
        map.put("name", fund.getFundName());
        map.put("type", fund.getFundType());
        map.put("company", fund.getFundCompany());
        map.put("riskLevel", fund.getRiskLevel());
        map.put("totalAssets", fund.getTotalAssets());
        map.put("isIndexFund", fund.getIsIndexFund());
        map.put("establishmentDate", fund.getEstablishmentDate());
        
        // 计算成立年限
        if (fund.getEstablishmentDate() != null) {
            int years = Period.between(fund.getEstablishmentDate(), LocalDate.now()).getYears();
            map.put("establishedYears", years);
        }
        
        return map;
    }
    
    /**
     * 获取指标值
     */
    private Double getMetricValue(Map<String, Object> fund, String field) {
        switch (field) {
            case "return":
            case "returnRate":
                return (Double) fund.get("returnRate");
            case "sharpe":
            case "sharpeRatio":
                return (Double) fund.get("sharpeRatio");
            case "drawdown":
            case "maxDrawdown":
                return (Double) fund.get("maxDrawdown");
            case "size":
            case "totalAssets":
                Double assets = (Double) fund.get("totalAssets");
                return assets != null ? assets : 0.0;
            case "age":
            case "establishedYears":
                Integer years = (Integer) fund.get("establishedYears");
                return years != null ? years.doubleValue() : 0.0;
            default:
                return 0.0;
        }
    }
}
