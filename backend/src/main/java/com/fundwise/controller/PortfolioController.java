package com.fundwise.controller;

import com.fundwise.service.PortfolioService;
import com.fundwise.service.BacktestService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 投资组合控制器
 */
@RestController
@RequestMapping("/api/portfolios")
@CrossOrigin(origins = "*")
public class PortfolioController {
    
    private final PortfolioService portfolioService;
    private final BacktestService backtestService;
    
    public PortfolioController(PortfolioService portfolioService,
                              BacktestService backtestService) {
        this.portfolioService = portfolioService;
        this.backtestService = backtestService;
    }
    
    /**
     * 获取所有投资组合
     * GET /api/portfolios
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> getAllPortfolios() {
        Map<String, Object> response = new HashMap<>();
        try {
            List<Map<String, Object>> portfolios = portfolioService.getAllPortfolios();
            response.put("success", true);
            response.put("data", portfolios);
            response.put("count", portfolios.size());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.ok(response);
        }
    }
    
    /**
     * 获取投资组合详情
     * GET /api/portfolios/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getPortfolio(@PathVariable Long id) {
        Map<String, Object> response = new HashMap<>();
        try {
            Map<String, Object> portfolio = portfolioService.getPortfolioById(id);
            response.put("success", true);
            response.put("data", portfolio);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.ok(response);
        }
    }
    
    /**
     * 创建投资组合
     * POST /api/portfolios
     */
    @PostMapping
    public ResponseEntity<Map<String, Object>> createPortfolio(@RequestBody PortfolioRequest request) {
        Map<String, Object> response = new HashMap<>();
        try {
            Map<String, Object> portfolio = portfolioService.createPortfolio(
                request.getName(),
                request.getDescription(),
                request.getRiskLevel(),
                request.getHoldings()
            );
            response.put("success", true);
            response.put("data", portfolio);
            response.put("message", "组合创建成功");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.ok(response);
        }
    }
    
    /**
     * 更新投资组合
     * PUT /api/portfolios/{id}
     */
    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> updatePortfolio(@PathVariable Long id, 
                                                               @RequestBody PortfolioRequest request) {
        Map<String, Object> response = new HashMap<>();
        try {
            Map<String, Object> portfolio = portfolioService.updatePortfolio(
                id,
                request.getName(),
                request.getDescription(),
                request.getRiskLevel(),
                request.getHoldings()
            );
            response.put("success", true);
            response.put("data", portfolio);
            response.put("message", "组合更新成功");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.ok(response);
        }
    }
    
    /**
     * 删除投资组合
     * DELETE /api/portfolios/{id}
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> deletePortfolio(@PathVariable Long id) {
        Map<String, Object> response = new HashMap<>();
        try {
            portfolioService.deletePortfolio(id);
            response.put("success", true);
            response.put("message", "组合删除成功");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.ok(response);
        }
    }
    
    /**
     * 复制投资组合
     * POST /api/portfolios/{id}/duplicate
     */
    @PostMapping("/{id}/duplicate")
    public ResponseEntity<Map<String, Object>> duplicatePortfolio(@PathVariable Long id) {
        Map<String, Object> response = new HashMap<>();
        try {
            Map<String, Object> portfolio = portfolioService.duplicatePortfolio(id);
            response.put("success", true);
            response.put("data", portfolio);
            response.put("message", "组合复制成功");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.ok(response);
        }
    }
    
    /**
     * 添加基金到组合
     * POST /api/portfolios/{id}/holdings
     */
    @PostMapping("/{id}/holdings")
    public ResponseEntity<Map<String, Object>> addHolding(@PathVariable Long id,
                                                          @RequestBody HoldingRequest request) {
        Map<String, Object> response = new HashMap<>();
        try {
            Map<String, Object> portfolio = portfolioService.addHoldingToPortfolio(
                id,
                request.getFundCode(),
                request.getFundName(),
                request.getType(),
                request.getRiskLevel(),
                request.getTargetWeight() != null ? request.getTargetWeight() : 0.1
            );
            response.put("success", true);
            response.put("data", portfolio);
            response.put("message", "基金添加成功");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.ok(response);
        }
    }
    
    /**
     * 更新持仓权重
     * PUT /api/portfolios/{portfolioId}/holdings/{fundCode}
     */
    @PutMapping("/{portfolioId}/holdings/{fundCode}")
    public ResponseEntity<Map<String, Object>> updateHoldingWeight(@PathVariable Long portfolioId,
                                                                   @PathVariable String fundCode,
                                                                   @RequestParam Double targetWeight) {
        Map<String, Object> response = new HashMap<>();
        try {
            portfolioService.updateHoldingWeight(portfolioId, fundCode, targetWeight);
            response.put("success", true);
            response.put("message", "权重更新成功");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.ok(response);
        }
    }
    
    /**
     * 删除持仓
     * DELETE /api/portfolios/{portfolioId}/holdings/{fundCode}
     */
    @DeleteMapping("/{portfolioId}/holdings/{fundCode}")
    public ResponseEntity<Map<String, Object>> removeHolding(@PathVariable Long portfolioId,
                                                             @PathVariable String fundCode) {
        Map<String, Object> response = new HashMap<>();
        try {
            portfolioService.removeHolding(portfolioId, fundCode);
            response.put("success", true);
            response.put("message", "持仓删除成功");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.ok(response);
        }
    }
    
    /**
     * 运行回测
     * POST /api/portfolios/{id}/backtest
     */
    @PostMapping("/{id}/backtest")
    public ResponseEntity<Map<String, Object>> runBacktest(@PathVariable Long id,
                                                           @RequestBody BacktestRequest request) {
        Map<String, Object> response = new HashMap<>();
        try {
            Map<String, Object> result = backtestService.runBacktest(
                id,
                request.getStartDate() != null ? request.getStartDate() : "2023-01-01",
                request.getEndDate() != null ? request.getEndDate() : "2024-12-31",
                request.getInitialCapital() != null ? request.getInitialCapital() : 10000.0
            );
            response.put("success", true);
            response.put("data", result);
            response.put("message", "回测完成");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.ok(response);
        }
    }
    
    /**
     * 投资组合请求对象
     */
    public static class PortfolioRequest {
        private String name;
        private String description;
        private String riskLevel;
        private List<Map<String, Object>> holdings;
        
        // Getters and Setters
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        
        public String getRiskLevel() { return riskLevel; }
        public void setRiskLevel(String riskLevel) { this.riskLevel = riskLevel; }
        
        public List<Map<String, Object>> getHoldings() { return holdings; }
        public void setHoldings(List<Map<String, Object>> holdings) { this.holdings = holdings; }
    }
    
    /**
     * 持仓请求对象
     */
    public static class HoldingRequest {
        private String fundCode;
        private String fundName;
        private String type;
        private String riskLevel;
        private Double targetWeight;
        
        // Getters and Setters
        public String getFundCode() { return fundCode; }
        public void setFundCode(String fundCode) { this.fundCode = fundCode; }
        
        public String getFundName() { return fundName; }
        public void setFundName(String fundName) { this.fundName = fundName; }
        
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        
        public String getRiskLevel() { return riskLevel; }
        public void setRiskLevel(String riskLevel) { this.riskLevel = riskLevel; }
        
        public Double getTargetWeight() { return targetWeight; }
        public void setTargetWeight(Double targetWeight) { this.targetWeight = targetWeight; }
    }
    
    /**
     * 回测请求对象
     */
    public static class BacktestRequest {
        private String startDate;
        private String endDate;
        private Double initialCapital;
        
        // Getters and Setters
        public String getStartDate() { return startDate; }
        public void setStartDate(String startDate) { this.startDate = startDate; }
        
        public String getEndDate() { return endDate; }
        public void setEndDate(String endDate) { this.endDate = endDate; }
        
        public Double getInitialCapital() { return initialCapital; }
        public void setInitialCapital(Double initialCapital) { this.initialCapital = initialCapital; }
    }
}
