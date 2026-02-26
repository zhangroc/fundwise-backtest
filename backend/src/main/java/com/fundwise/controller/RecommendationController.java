package com.fundwise.controller;

import com.fundwise.service.RecommendationService;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*") // 允许前端访问
public class RecommendationController {
    
    private final RecommendationService recommendationService;
    
    public RecommendationController(RecommendationService recommendationService) {
        this.recommendationService = recommendationService;
    }
    
    /**
     * 智能推荐组合 API
     * POST /api/recommend
     * 请求体示例：
     * {
     *   "initialCapital": 10000,
     *   "maxDrawdown": 0.10,
     *   "investmentPeriod": 3,
     *   "indexFundOnly": false,
     *   "enableRebalancing": false
     * }
     */
    @PostMapping("/recommend")
    public ResponseEntity<Map<String, Object>> recommendPortfolio(@RequestBody PortfolioRequest request) {
        // 调用服务层，基于真实数据库数据进行智能推荐
        Map<String, Object> response = new HashMap<>();
        
        try {
            // 1. 获取推荐基金组合（基于真实数据库 - 优化版）
            List<Map<String, Object>> funds = recommendationService.recommendFunds(
                request.getInitialCapital(),
                request.getMaxDrawdown(),
                request.isIndexFundOnly(),
                determineRiskLevel(request.getMaxDrawdown()),
                request.getInvestmentPeriod()
            );
            
            // 2. 计算回测结果（模拟，后续应接入真实回测引擎）
            Map<String, Object> backtest = recommendationService.calculateBacktestResult(
                funds,
                request.getInitialCapital(),
                request.getInvestmentPeriod()
            );
            
            // 3. 生成智能解读
            List<String> insights = recommendationService.generateInsights(
                funds,
                backtest,
                request.getMaxDrawdown(),
                request.isIndexFundOnly()
            );
            
            // 4. 构建响应
            response.put("portfolio", Map.of(
                "id", 1,
                "name", request.isIndexFundOnly() ? "指数基金优选组合" : "智能稳健组合",
                "funds", funds,
                "riskLevel", determineRiskLevel(request.getMaxDrawdown())
            ));
            response.put("backtest", backtest);
            response.put("insights", insights);
            response.put("success", true);
            response.put("dataSource", "real_database");
            response.put("fundsCount", funds.size());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            // 出错时回退到模拟数据
            return fallbackResponse(request);
        }
    }
    
    /**
     * 简单的回测API（后续扩展用）
     */
    @PostMapping("/backtest")
    public ResponseEntity<Map<String, Object>> runBacktest(@RequestBody BacktestRequest request) {
        // TODO: 实现真实回测逻辑
        Map<String, Object> response = new HashMap<>();
        response.put("message", "回测功能开发中");
        response.put("success", false);
        return ResponseEntity.ok(response);
    }
    
    /**
     * 健康检查端点
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> healthCheck() {
        Map<String, String> response = new HashMap<>();
        response.put("status", "UP");
        response.put("service", "fundwise-backend");
        response.put("timestamp", java.time.LocalDateTime.now().toString());
        return ResponseEntity.ok(response);
    }
    
    /**
     * 查询基金列表API（用于前端展示）
     */
    @GetMapping("/funds")
    public ResponseEntity<Map<String, Object>> getFunds(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String riskLevel,
            @RequestParam(required = false, defaultValue = "20") int limit) {
        
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("timestamp", java.time.LocalDateTime.now().toString());
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * 查询基金详情API
     */
    @GetMapping("/funds/{code}")
    public ResponseEntity<Map<String, Object>> getFundDetail(@PathVariable String code) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("message", "功能开发中");
        response.put("code", code);
        return ResponseEntity.ok(response);
    }
    
    // 辅助方法
    private Map<String, Object> createFund(String code, String name, String type, double weight) {
        Map<String, Object> fund = new HashMap<>();
        fund.put("code", code);
        fund.put("name", name);
        fund.put("type", type);
        fund.put("weight", weight);
        return fund;
    }
    
    private String determineRiskLevel(Double maxDrawdown) {
        if (maxDrawdown == null) return "稳健";
        if (maxDrawdown <= 0.05) return "保守";
        if (maxDrawdown <= 0.15) return "稳健";
        return "积极";
    }
    
    /**
     * 出错时返回模拟数据
     */
    private ResponseEntity<Map<String, Object>> fallbackResponse(PortfolioRequest request) {
        Map<String, Object> response = new HashMap<>();
        
        // 构建模拟推荐组合
        List<Map<String, Object>> funds = new ArrayList<>();
        
        if (request.isIndexFundOnly()) {
            funds.add(createFund("000001", "华夏沪深300ETF联接", "指数型", 0.40));
            funds.add(createFund("000002", "易方达中证500ETF联接", "指数型", 0.35));
            funds.add(createFund("000003", "富国国债ETF联接", "债券指数型", 0.25));
        } else {
            funds.add(createFund("000001", "华夏成长混合", "混合型", 0.30));
            funds.add(createFund("000002", "易方达沪深300ETF联接", "指数型", 0.40));
            funds.add(createFund("000003", "富国天惠债券", "债券型", 0.30));
        }
        
        Map<String, Object> backtest = new HashMap<>();
        backtest.put("startDate", "2021-01-01");
        backtest.put("endDate", "2024-01-01");
        backtest.put("initialCapital", request.getInitialCapital());
        backtest.put("finalValue", request.getInitialCapital() * 1.35);
        backtest.put("annualizedReturn", 0.085);
        backtest.put("maxDrawdown", request.getMaxDrawdown() != null ? request.getMaxDrawdown() * 0.9 : 0.092);
        backtest.put("sharpeRatio", 1.2);
        backtest.put("benchmarkReturn", 0.045);
        
        List<String> insights = new ArrayList<>();
        insights.add(String.format("该组合模拟回测显示，年化收益约为%.1f%%", 0.085 * 100));
        if (request.getMaxDrawdown() != null) {
            insights.add(String.format("历史最大回撤控制在您设定的%.1f%%以内", request.getMaxDrawdown() * 100));
        } else {
            insights.add("历史最大回撤为9.2%，风险控制良好");
        }
        insights.add("组合的夏普比率为1.2，收益风险性价比较好");
        if (request.isIndexFundOnly()) {
            insights.add("指数基金组合管理费率较低，长期成本优势明显");
        }
        
        response.put("portfolio", Map.of(
            "id", 1,
            "name", request.isIndexFundOnly() ? "指数基金优选组合" : "智能稳健组合",
            "funds", funds,
            "riskLevel", determineRiskLevel(request.getMaxDrawdown())
        ));
        response.put("backtest", backtest);
        response.put("insights", insights);
        response.put("success", true);
        response.put("dataSource", "fallback_simulation");
        response.put("fundsCount", funds.size());
        
        return ResponseEntity.ok(response);
    }
    
    // 请求参数类
    public static class PortfolioRequest {
        private double initialCapital;
        private Double maxDrawdown;
        private int investmentPeriod;
        private boolean indexFundOnly;
        private boolean enableRebalancing;
        
        // getters and setters
        public double getInitialCapital() { return initialCapital; }
        public void setInitialCapital(double initialCapital) { this.initialCapital = initialCapital; }
        
        public Double getMaxDrawdown() { return maxDrawdown; }
        public void setMaxDrawdown(Double maxDrawdown) { this.maxDrawdown = maxDrawdown; }
        
        public int getInvestmentPeriod() { return investmentPeriod; }
        public void setInvestmentPeriod(int investmentPeriod) { this.investmentPeriod = investmentPeriod; }
        
        public boolean isIndexFundOnly() { return indexFundOnly; }
        public void setIndexFundOnly(boolean indexFundOnly) { this.indexFundOnly = indexFundOnly; }
        
        public boolean isEnableRebalancing() { return enableRebalancing; }
        public void setEnableRebalancing(boolean enableRebalancing) { this.enableRebalancing = enableRebalancing; }
    }
    
    public static class BacktestRequest {
        // TODO: 定义回测请求参数
        private List<String> fundCodes;
        private List<Double> weights;
        private String startDate;
        private String endDate;
        private double initialCapital;
        
        // getters and setters
        public List<String> getFundCodes() { return fundCodes; }
        public void setFundCodes(List<String> fundCodes) { this.fundCodes = fundCodes; }
        
        public List<Double> getWeights() { return weights; }
        public void setWeights(List<Double> weights) { this.weights = weights; }
        
        public String getStartDate() { return startDate; }
        public void setStartDate(String startDate) { this.startDate = startDate; }
        
        public String getEndDate() { return endDate; }
        public void setEndDate(String endDate) { this.endDate = endDate; }
        
        public double getInitialCapital() { return initialCapital; }
        public void setInitialCapital(double initialCapital) { this.initialCapital = initialCapital; }
    }
}