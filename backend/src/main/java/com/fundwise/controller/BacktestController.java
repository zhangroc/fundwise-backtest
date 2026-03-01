package com.fundwise.controller;

import com.fundwise.service.BacktestService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 回测控制器
 */
@RestController
@RequestMapping("/api/backtest")
@CrossOrigin(origins = "*")
public class BacktestController {
    
    private final BacktestService backtestService;
    
    public BacktestController(BacktestService backtestService) {
        this.backtestService = backtestService;
    }
    
    /**
     * 运行回测
     * POST /api/backtest/run
     */
    @PostMapping("/run")
    public ResponseEntity<Map<String, Object>> runBacktest(@RequestBody BacktestRequest request) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            // 参数验证
            if (request.portfolioId == null) {
                throw new RuntimeException("组合 ID 不能为空");
            }
            if (request.startDate == null || request.endDate == null) {
                throw new RuntimeException("开始和结束日期不能为空");
            }
            if (request.initialCapital == null || request.initialCapital <= 0) {
                request.initialCapital = 10000.0; // 默认 1 万元
            }
            
            // 运行回测
            Map<String, Object> result = backtestService.runBacktest(
                request.portfolioId,
                request.startDate,
                request.endDate,
                request.initialCapital
            );
            
            // 格式化返回数据
            Map<String, Object> formattedResult = new HashMap<>();
            formattedResult.put("portfolioId", result.get("portfolioId"));
            formattedResult.put("portfolioName", result.get("portfolioName"));
            formattedResult.put("startDate", result.get("startDate"));
            formattedResult.put("endDate", result.get("endDate"));
            formattedResult.put("initialCapital", formatMoney((Double) result.get("initialCapital")));
            formattedResult.put("finalValue", formatMoney((Double) result.get("finalValue")));
            formattedResult.put("totalReturn", formatPercent((Double) result.get("totalReturn")));
            formattedResult.put("totalReturnRaw", result.get("totalReturn"));
            formattedResult.put("annualizedReturn", formatPercent((Double) result.get("annualizedReturn")));
            formattedResult.put("annualizedReturnRaw", result.get("annualizedReturn"));
            formattedResult.put("maxDrawdown", formatPercent((Double) result.get("maxDrawdown")));
            formattedResult.put("maxDrawdownRaw", result.get("maxDrawdown"));
            formattedResult.put("sharpeRatio", formatNumber((Double) result.get("sharpeRatio"), 2));
            formattedResult.put("volatility", formatPercent((Double) result.get("volatility")));
            formattedResult.put("winRate", formatPercent((Double) result.get("winRate")));
            formattedResult.put("totalDays", result.get("totalDays"));
            
            // 转换每日数据
            @SuppressWarnings("unchecked")
            List<BacktestService.DailyBacktestData> dailyData = 
                (List<BacktestService.DailyBacktestData>) result.get("dailyData");
            
            List<Map<String, Object>> formattedDailyData = dailyData.stream()
                .map(BacktestService.DailyBacktestData::toMap)
                .collect(Collectors.toList());
            
            formattedResult.put("dailyData", formattedDailyData);
            
            // 添加图表数据（简化版，只返回部分数据点）
            List<Map<String, Object>> chartData = downsampleData(formattedDailyData, 100);
            formattedResult.put("chartData", chartData);
            
            response.put("success", true);
            response.put("data", formattedResult);
            response.put("message", "回测完成");
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.ok(response);
        }
    }
    
    /**
     * 快速回测（使用默认参数）
     * POST /api/backtest/quick/{portfolioId}
     */
    @PostMapping("/quick/{portfolioId}")
    public ResponseEntity<Map<String, Object>> quickBacktest(@PathVariable Long portfolioId) {
        BacktestRequest request = new BacktestRequest();
        request.portfolioId = portfolioId;
        request.startDate = "2023-01-01";
        request.endDate = "2024-12-31";
        request.initialCapital = 10000.0;
        
        return runBacktest(request);
    }
    
    /**
     * 格式化金额
     */
    private String formatMoney(Double value) {
        if (value == null) return "--";
        return String.format("¥%,.2f", value);
    }
    
    /**
     * 格式化百分比
     */
    private String formatPercent(Double value) {
        if (value == null) return "--";
        return String.format("%.2f%%", value * 100);
    }
    
    /**
     * 格式化数字
     */
    private String formatNumber(Double value, int decimals) {
        if (value == null) return "--";
        return String.format("%." + decimals + "f", value);
    }
    
    /**
     * 数据降采样（用于图表展示）
     */
    private List<Map<String, Object>> downsampleData(List<Map<String, Object>> data, int maxPoints) {
        if (data.size() <= maxPoints) {
            return data;
        }
        
        int step = (int) Math.ceil((double) data.size() / maxPoints);
        List<Map<String, Object>> result = new java.util.ArrayList<>();
        
        for (int i = 0; i < data.size(); i += step) {
            result.add(data.get(i));
        }
        
        // 确保包含最后一个点
        if (result.size() < data.size()) {
            result.add(data.get(data.size() - 1));
        }
        
        return result;
    }
    
    /**
     * 回测请求参数
     */
    public static class BacktestRequest {
        private Long portfolioId;
        private String startDate;
        private String endDate;
        private Double initialCapital;
        
        // Getters and Setters
        public Long getPortfolioId() { return portfolioId; }
        public void setPortfolioId(Long portfolioId) { this.portfolioId = portfolioId; }
        
        public String getStartDate() { return startDate; }
        public void setStartDate(String startDate) { this.startDate = startDate; }
        
        public String getEndDate() { return endDate; }
        public void setEndDate(String endDate) { this.endDate = endDate; }
        
        public Double getInitialCapital() { return initialCapital; }
        public void setInitialCapital(Double initialCapital) { this.initialCapital = initialCapital; }
    }
}
