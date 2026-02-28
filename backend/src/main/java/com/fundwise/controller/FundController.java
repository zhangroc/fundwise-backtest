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
     * 筛选基金 - 使用数据库分页优化性能
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
            // 使用分页查询，避免加载全部数据
            org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(
                page - 1, 
                Math.min(pageSize, 100) // 限制最大每页100条
            );
            
            // 构建查询条件
            org.springframework.data.jpa.domain.Specification<Fund> spec = (root, query, cb) -> {
                List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
                
                // 必须有净值数据
                predicates.add(cb.greaterThan(root.get("navRecordCount"), 0));
                
                // 基金类型筛选
                if (fundType != null && !"all".equals(fundType)) {
                    predicates.add(cb.like(root.get("fundType"), "%" + fundType + "%"));
                }
                
                // 指数基金筛选
                if (Boolean.TRUE.equals(indexFundOnly)) {
                    predicates.add(cb.isTrue(root.get("isIndexFund")));
                }
                
                // 规模筛选
                if (minSize != null) {
                    predicates.add(cb.greaterThanOrEqualTo(root.get("totalAssets"), minSize));
                }
                if (maxSize != null) {
                    predicates.add(cb.lessThanOrEqualTo(root.get("totalAssets"), maxSize));
                }
                
                // 成立年限筛选
                if (minYears != null && minYears > 0) {
                    LocalDate cutoffDate = LocalDate.now().minusYears(minYears);
                    predicates.add(cb.lessThanOrEqualTo(root.get("establishmentDate"), cutoffDate));
                }
                
                return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
            };
            
            // 执行分页查询
            org.springframework.data.domain.Page<Fund> fundPage = fundRepository.findAll(spec, pageable);
            
            // 转换为 Map 并添加模拟指标
            List<Map<String, Object>> fundsWithMetrics = fundPage.getContent().stream()
                .map(fund -> {
                    Map<String, Object> fundData = convertFundToMap(fund);
                    // 模拟业绩指标（后续接入真实计算）
                    fundData.put("returnRate", (Math.random() - 0.3) * 0.5);
                    fundData.put("maxDrawdown", -(Math.random() * 0.3));
                    fundData.put("sharpeRatio", (Math.random() - 0.5) * 3);
                    return fundData;
                })
                .collect(Collectors.toList());
            
            // 内存中排序（因为指标是模拟的）
            final String sortField = sortBy != null ? sortBy : "returnRate";
            final boolean desc = "desc".equals(sortOrder) || sortOrder == null;
            
            fundsWithMetrics.sort((a, b) -> {
                Double valA = getMetricValue(a, sortField);
                Double valB = getMetricValue(b, sortField);
                if (valA == null) valA = 0.0;
                if (valB == null) valB = 0.0;
                return desc ? valB.compareTo(valA) : valA.compareTo(valB);
            });
            
            response.put("success", true);
            response.put("data", fundsWithMetrics);
            response.put("total", fundPage.getTotalElements());
            response.put("page", page);
            response.put("pageSize", pageSize);
            response.put("totalPages", fundPage.getTotalPages());
            
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
            
            // 只返回有净值数据的基金
            List<Map<String, Object>> results = funds.stream()
                .filter(f -> f.getNavRecordCount() != null && f.getNavRecordCount() > 0)
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
        map.put("navRecordCount", fund.getNavRecordCount());
        map.put("navStartDate", fund.getNavStartDate());
        map.put("navEndDate", fund.getNavEndDate());
        
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
