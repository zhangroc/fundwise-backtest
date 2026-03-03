package com.fundwise.controller;

import com.fundwise.entity.Fund;
import com.fundwise.repository.FundRepository;
import com.fundwise.service.FundMetricsService;
import com.fundwise.service.FundScreenerService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.Period;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 基金数据控制器 - 提供多维度筛选、排名、详情分析
 * 参考 Python 版本的 FundScreener 实现
 */
@RestController
@RequestMapping("/api/v1/funds")
@CrossOrigin(origins = "*")
public class FundController {
    
    private final FundRepository fundRepository;
    private final FundMetricsService metricsService;
    private final FundScreenerService screenerService;
    
    public FundController(FundRepository fundRepository, 
                          FundMetricsService metricsService,
                          FundScreenerService screenerService) {
        this.fundRepository = fundRepository;
        this.metricsService = metricsService;
        this.screenerService = screenerService;
    }
    
    /**
     * 多维度筛选基金 - 增强版
     * GET /api/v1/funds/screen
     * 
     * 参数说明：
     * - period: 统计时间段 (1m, 3m, 6m, 1y, 2y, 3y, 5y)
     * - fundType: 基金类型（股票型、混合型、债券型、指数型等）
     * - indexFundOnly: 是否只筛选指数基金
     * - minReturn: 最低收益率（百分比，如 10 表示 10%）
     * - maxDrawdown: 最大可接受回撤（百分比，如 20 表示最大回撤不超过 20%）
     * - minSharpe: 最低夏普比率
     * - maxVolatility: 最大波动率（百分比）
     * - minWinRate: 最低胜率（百分比）
     * - minCalmar: 最低 Calmar 比率
     * - minSize: 最小基金规模（亿元）
     * - maxSize: 最大基金规模（亿元）
     * - minYears: 最低成立年限
     * - riskLevel: 风险等级（保守、稳健、积极）
     * - company: 基金公司
     * - keyword: 关键词搜索（基金名称或代码）
     * - sortBy: 排序字段 (return_period, sharpe_ratio, max_drawdown, volatility, calmar_ratio, win_rate)
     * - sortOrder: 排序方式 (asc/desc)
     * - page: 页码
     * - pageSize: 每页数量
     */
    @GetMapping("/screen")
    public ResponseEntity<Map<String, Object>> screenFunds(
            @RequestParam(required = false) String period,
            @RequestParam(required = false) String fundType,
            @RequestParam(required = false) Boolean indexFundOnly,
            @RequestParam(required = false) Double minReturn,
            @RequestParam(required = false) Double maxDrawdown,
            @RequestParam(required = false) Double minSharpe,
            @RequestParam(required = false) Double maxVolatility,
            @RequestParam(required = false) Double minWinRate,
            @RequestParam(required = false) Double minCalmar,
            @RequestParam(required = false) Double minSize,
            @RequestParam(required = false) Double maxSize,
            @RequestParam(required = false) Integer minYears,
            @RequestParam(required = false) String riskLevel,
            @RequestParam(required = false) String company,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String sortOrder,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize
    ) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            // 构建筛选条件
            FundScreenerService.ScreeningFilters filters = new FundScreenerService.ScreeningFilters();
            filters.period = period != null ? period : "1y";
            filters.fundType = fundType;
            filters.indexFundOnly = indexFundOnly;
            filters.minReturn = minReturn;
            filters.maxDrawdown = maxDrawdown;
            filters.minSharpe = minSharpe;
            filters.maxVolatility = maxVolatility;
            filters.minWinRate = minWinRate;
            filters.minCalmar = minCalmar;
            filters.minSize = minSize;
            filters.maxSize = maxSize;
            filters.minYears = minYears;
            filters.riskLevel = riskLevel;
            filters.company = company;
            filters.keyword = keyword;
            filters.sortBy = sortBy != null ? sortBy : "return_period";
            filters.sortOrder = sortOrder != null ? sortOrder : "desc";
            filters.page = page;
            filters.pageSize = Math.min(pageSize, 100);
            
            // 调用筛选服务
            Map<String, Object> result = screenerService.screenFunds(filters);
            
            // 格式化输出
            List<Map<String, Object>> funds = (List<Map<String, Object>>) result.get("funds");
            List<Map<String, Object>> formattedFunds = funds.stream()
                .map(this::formatFundData)
                .collect(Collectors.toList());
            
            response.put("success", true);
            response.put("data", formattedFunds);
            response.put("total", result.get("total"));
            response.put("page", result.get("page"));
            response.put("pageSize", result.get("pageSize"));
            response.put("period", result.get("period"));
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "筛选失败: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.ok(response);
        }
    }
    
    /**
     * 获取基金详情（包含多周期指标）
     * GET /api/v1/funds/{code}
     */
    @GetMapping("/{code}")
    public ResponseEntity<Map<String, Object>> getFundDetail(
            @PathVariable String code,
            @RequestParam(defaultValue = "1y") String period
    ) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            Fund fund = fundRepository.findByFundCode(code)
                .orElseThrow(() -> new RuntimeException("基金不存在：" + code));
            
            Map<String, Object> fundData = convertFundToMap(fund);
            
            // 计算多个时间周期的指标
            Map<String, Double> metrics1m = metricsService.calculateMetrics(code, 21);
            Map<String, Double> metrics3m = metricsService.calculateMetrics(code, 63);
            Map<String, Double> metrics6m = metricsService.calculateMetrics(code, 126);
            Map<String, Double> metrics1y = metricsService.calculateMetrics(code, 252);
            Map<String, Double> metrics3y = metricsService.calculateMetrics(code, 756);
            
            // 各周期收益率
            Map<String, Object> returns = new HashMap<>();
            returns.put("return_1m", metrics1m.getOrDefault("totalReturn", 0.0) * 100);
            returns.put("return_3m", metrics3m.getOrDefault("totalReturn", 0.0) * 100);
            returns.put("return_6m", metrics6m.getOrDefault("totalReturn", 0.0) * 100);
            returns.put("return_1y", metrics1y.getOrDefault("totalReturn", 0.0) * 100);
            returns.put("return_3y", metrics3y.getOrDefault("totalReturn", 0.0) * 100);
            fundData.put("returns", returns);
            
            // 当前周期的详细指标
            Map<String, Double> currentMetrics = switch (period) {
                case "1m" -> metrics1m;
                case "3m" -> metrics3m;
                case "6m" -> metrics6m;
                case "3y" -> metrics3y;
                default -> metrics1y;
            };
            
            fundData.put("return_rate", currentMetrics.getOrDefault("totalReturn", 0.0) * 100);
            fundData.put("annual_return", currentMetrics.getOrDefault("annualizedReturn", 0.0) * 100);
            fundData.put("max_drawdown", currentMetrics.getOrDefault("maxDrawdown", 0.0) * 100);
            fundData.put("volatility", currentMetrics.getOrDefault("volatility", 0.0) * 100);
            fundData.put("sharpe_ratio", currentMetrics.getOrDefault("sharpeRatio", 0.0));
            fundData.put("win_rate", currentMetrics.getOrDefault("winRate", 0.0) * 100);
            fundData.put("calmar_ratio", currentMetrics.getOrDefault("calmarRatio", 0.0));
            
            // 1年期和3年期对比
            fundData.put("sharpe_1y", metrics1y.getOrDefault("sharpeRatio", 0.0));
            fundData.put("sharpe_3y", metrics3y.getOrDefault("sharpeRatio", 0.0));
            fundData.put("max_drawdown_1y", metrics1y.getOrDefault("maxDrawdown", 0.0) * 100);
            fundData.put("max_drawdown_3y", metrics3y.getOrDefault("maxDrawdown", 0.0) * 100);
            
            response.put("success", true);
            response.put("data", fundData);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.ok(response);
        }
    }
    
    /**
     * 搜索基金
     * GET /api/v1/funds/search?keyword=xxx
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
     * 获取仪表盘数据
     * GET /api/v1/funds/dashboard
     */
    @GetMapping("/dashboard")
    public ResponseEntity<Map<String, Object>> getDashboard() {
        Map<String, Object> response = new HashMap<>();
        
        try {
            Map<String, Object> dashboard = screenerService.getDashboardData();
            
            response.put("success", true);
            response.put("data", dashboard);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.ok(response);
        }
    }
    
    /**
     * 获取热门基金排行
     * GET /api/v1/funds/top
     */
    @GetMapping("/top")
    public ResponseEntity<Map<String, Object>> getTopFunds(
            @RequestParam(defaultValue = "1y") String period,
            @RequestParam(defaultValue = "10") int limit
    ) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            List<Map<String, Object>> topFunds = screenerService.getTopFundsByReturn(period, limit);
            
            response.put("success", true);
            response.put("data", topFunds);
            response.put("period", period);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.ok(response);
        }
    }
    
    /**
     * 获取筛选选项（用于前端下拉框）
     * GET /api/v1/funds/filter-options
     */
    @GetMapping("/filter-options")
    public ResponseEntity<Map<String, Object>> getFilterOptions() {
        Map<String, Object> response = new HashMap<>();
        
        try {
            Map<String, Object> options = new HashMap<>();
            
            // 基金类型列表
            options.put("fundTypes", Arrays.asList(
                "股票型", "混合型", "债券型", "指数型", "货币型", 
                "QDII", "FOF", "商品型", "其他"
            ));
            
            // 时间周期选项
            options.put("periods", Arrays.asList(
                Map.of("value", "1m", "label", "近1月"),
                Map.of("value", "3m", "label", "近3月"),
                Map.of("value", "6m", "label", "近6月"),
                Map.of("value", "1y", "label", "近1年"),
                Map.of("value", "2y", "label", "近2年"),
                Map.of("value", "3y", "label", "近3年"),
                Map.of("value", "5y", "label", "近5年")
            ));
            
            // 排序字段选项
            options.put("sortFields", Arrays.asList(
                Map.of("value", "return_period", "label", "收益率"),
                Map.of("value", "sharpe_ratio", "label", "夏普比率"),
                Map.of("value", "max_drawdown", "label", "最大回撤"),
                Map.of("value", "volatility", "label", "波动率"),
                Map.of("value", "calmar_ratio", "label", "Calmar比率"),
                Map.of("value", "win_rate", "label", "胜率"),
                Map.of("value", "total_assets", "label", "基金规模"),
                Map.of("value", "established_years", "label", "成立年限")
            ));
            
            // 风险等级
            options.put("riskLevels", Arrays.asList(
                Map.of("value", "保守", "label", "保守型"),
                Map.of("value", "稳健", "label", "稳健型"),
                Map.of("value", "积极", "label", "积极型")
            ));
            
            response.put("success", true);
            response.put("data", options);
            
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
        
        if (fund.getEstablishmentDate() != null) {
            int years = Period.between(fund.getEstablishmentDate(), LocalDate.now()).getYears();
            map.put("establishedYears", years);
        }
        
        return map;
    }
    
    /**
     * 格式化基金数据（用于筛选结果输出）
     */
    private Map<String, Object> formatFundData(Map<String, Object> fund) {
        Map<String, Object> formatted = new HashMap<>();
        
        formatted.put("code", fund.get("fund_code"));
        formatted.put("name", fund.get("fund_name"));
        formatted.put("type", fund.get("fund_type"));
        formatted.put("company", fund.get("fund_company"));
        formatted.put("riskLevel", fund.get("risk_level"));
        formatted.put("isIndexFund", fund.get("is_index_fund"));
        formatted.put("totalAssets", fund.get("total_assets"));
        formatted.put("establishedYears", fund.get("established_years"));
        formatted.put("navRecordCount", fund.get("nav_record_count"));
        
        // 指标数据（保留两位小数）
        formatted.put("returnRate", round((Double) fund.getOrDefault("return_period", 0.0), 2));
        formatted.put("annualReturn", round((Double) fund.getOrDefault("annual_return", 0.0), 2));
        formatted.put("maxDrawdown", round((Double) fund.getOrDefault("max_drawdown", 0.0), 2));
        formatted.put("volatility", round((Double) fund.getOrDefault("volatility", 0.0), 2));
        formatted.put("sharpeRatio", round((Double) fund.getOrDefault("sharpe_ratio", 0.0), 2));
        formatted.put("winRate", round((Double) fund.getOrDefault("win_rate", 0.0), 2));
        formatted.put("calmarRatio", round((Double) fund.getOrDefault("calmar_ratio", 0.0), 2));
        
        return formatted;
    }
    
    /**
     * 四舍五入
     */
    private Double round(Double value, int places) {
        if (value == null) return 0.0;
        long factor = (long) Math.pow(10, places);
        return Math.round(value * factor) / (double) factor;
    }
}