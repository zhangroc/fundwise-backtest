package com.fundwise.controller;

import com.fundwise.entity.Fund;
import com.fundwise.repository.FundRepository;
import com.fundwise.service.FundMetricsService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.Period;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 基金详情控制器 - 提供基金详细分析
 * 参考 Python 版本的 get_fund_detail 实现
 */
@RestController
@RequestMapping("/api/v1/funds")
@CrossOrigin(origins = "*")
public class FundDetailController {
    
    private final FundRepository fundRepository;
    private final FundMetricsService metricsService;
    private final JdbcTemplate jdbcTemplate;
    
    // 交易日常量
    private static final int TRADING_DAYS_PER_YEAR = 252;
    private static final double RISK_FREE_RATE = 0.02;
    
    // 时间段映射
    private static final Map<String, Integer> PERIOD_DAYS = Map.of(
        "1m", 21, "3m", 63, "6m", 126, "1y", 252, "2y", 504, "3y", 756, "all", 9999
    );
    
    public FundDetailController(FundRepository fundRepository,
                                  FundMetricsService metricsService,
                                  JdbcTemplate jdbcTemplate) {
        this.fundRepository = fundRepository;
        this.metricsService = metricsService;
        this.jdbcTemplate = jdbcTemplate;
    }
    
    /**
     * 获取基金详细分析
     * GET /api/v1/funds/{code}/detail?period=1y
     */
    @GetMapping("/{code}/detail")
    public Map<String, Object> getFundDetail(@PathVariable String code,
                                              @RequestParam(defaultValue = "1y") String period) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            Fund fund = fundRepository.findByFundCode(code)
                .orElseThrow(() -> new RuntimeException("基金不存在：" + code));
            
            // 基本信息
            Map<String, Object> info = convertFundToMap(fund);
            
            // 获取净值数据
            String navSql = """
                SELECT nav_date, nav, accumulated_nav, daily_return
                FROM fund_nav
                WHERE fund_code = ?
                ORDER BY nav_date ASC
            """;
            
            List<Map<String, Object>> navData = jdbcTemplate.query(navSql, 
                ps -> ps.setString(1, code),
                (rs, rowNum) -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("date", rs.getDate("nav_date").toLocalDate().toString());
                    map.put("nav", rs.getDouble("nav"));
                    map.put("accumulatedNav", rs.getDouble("accumulated_nav"));
                    map.put("dailyReturn", rs.getDouble("daily_return"));
                    return map;
                });
            
            if (navData.isEmpty()) {
                response.put("success", false);
                response.put("message", "无净值数据");
                return response;
            }
            
            // 计算各期收益率
            Map<String, Object> returns = calculatePeriodReturns(navData);
            
            // 计算当前周期的风险指标
            int tradingDays = PERIOD_DAYS.getOrDefault(period, 252);
            Map<String, Double> riskMetrics = metricsService.calculateMetrics(code, tradingDays);
            
            // 获取净值历史（最近500条）
            List<Map<String, Object>> navHistory = navData.stream()
                .skip(Math.max(0, navData.size() - 500))
                .collect(Collectors.toList());
            
            // 计算月度收益
            List<Map<String, Object>> monthlyReturns = calculateMonthlyReturns(navData);
            
            // 计算滚动指标
            Map<String, Object> rollingMetrics = calculateRollingMetrics(navData, 252);
            
            // 计算回撤分析
            Map<String, Object> drawdownAnalysis = calculateDrawdownAnalysis(navData);
            
            // 同类排名（简化实现）
            Map<String, Object> peerRanking = getPeerRanking(code, fund.getFundType(), riskMetrics);
            
            // 数据范围
            Map<String, Object> dataRange = new HashMap<>();
            dataRange.put("start", navData.get(0).get("date"));
            dataRange.put("end", navData.get(navData.size() - 1).get("date"));
            dataRange.put("totalDays", navData.size());
            
            // 组装响应
            response.put("success", true);
            response.put("info", info);
            response.put("returns", returns);
            response.put("riskMetrics", formatRiskMetrics(riskMetrics));
            response.put("navHistory", navHistory);
            response.put("monthlyReturns", monthlyReturns);
            response.put("rollingMetrics", rollingMetrics);
            response.put("drawdownAnalysis", drawdownAnalysis);
            response.put("peerRanking", peerRanking);
            response.put("dataRange", dataRange);
            response.put("period", period);
            
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", e.getMessage());
            e.printStackTrace();
        }
        
        return response;
    }
    
    /**
     * 计算各期收益率
     */
    private Map<String, Object> calculatePeriodReturns(List<Map<String, Object>> navData) {
        Map<String, Object> returns = new HashMap<>();
        
        int[] periods = {21, 63, 126, 252, 504, 756}; // 1m, 3m, 6m, 1y, 2y, 3y
        String[] labels = {"return1m", "return3m", "return6m", "return1y", "return2y", "return3y"};
        
        for (int i = 0; i < periods.length; i++) {
            int days = periods[i];
            if (navData.size() >= days) {
                double startNav = (Double) navData.get(navData.size() - days).get("nav");
                double endNav = (Double) navData.get(navData.size() - 1).get("nav");
                double ret = (endNav / startNav - 1) * 100;
                returns.put(labels[i], Math.round(ret * 100.0) / 100.0);
            } else {
                returns.put(labels[i], null);
            }
        }
        
        // 成立以来总收益
        double startNav = (Double) navData.get(0).get("nav");
        double endNav = (Double) navData.get(navData.size() - 1).get("nav");
        double totalReturn = (endNav / startNav - 1) * 100;
        returns.put("returnTotal", Math.round(totalReturn * 100.0) / 100.0);
        
        return returns;
    }
    
    /**
     * 计算月度收益
     */
    private List<Map<String, Object>> calculateMonthlyReturns(List<Map<String, Object>> navData) {
        // 按月份分组，取每月最后一个交易日净值
        Map<String, Double> monthlyNav = new LinkedHashMap<>();
        
        for (Map<String, Object> nav : navData) {
            String date = (String) nav.get("date");
            String month = date.substring(0, 7); // YYYY-MM
            monthlyNav.put(month, (Double) nav.get("nav"));
        }
        
        // 计算月度收益率
        List<Map<String, Object>> result = new ArrayList<>();
        List<String> months = new ArrayList<>(monthlyNav.keySet());
        
        for (int i = 1; i < months.size(); i++) {
            String month = months.get(i);
            String prevMonth = months.get(i - 1);
            
            double currentNav = monthlyNav.get(month);
            double prevNav = monthlyNav.get(prevMonth);
            double monthlyReturn = (currentNav / prevNav - 1) * 100;
            
            Map<String, Object> item = new HashMap<>();
            item.put("month", month);
            item.put("return", Math.round(monthlyReturn * 100.0) / 100.0);
            result.add(item);
        }
        
        // 返回最近24个月
        if (result.size() > 24) {
            return result.subList(result.size() - 24, result.size());
        }
        return result;
    }
    
    /**
     * 计算滚动指标
     */
    private Map<String, Object> calculateRollingMetrics(List<Map<String, Object>> navData, int windowSize) {
        Map<String, Object> result = new HashMap<>();
        
        if (navData.size() < windowSize) {
            result.put("dates", Collections.emptyList());
            result.put("returns", Collections.emptyList());
            result.put("volatility", Collections.emptyList());
            return result;
        }
        
        List<String> dates = new ArrayList<>();
        List<Double> returns = new ArrayList<>();
        List<Double> volatility = new ArrayList<>();
        
        // 计算滚动收益率和波动率
        for (int i = windowSize; i < navData.size(); i++) {
            String date = (String) navData.get(i).get("date");
            dates.add(date);
            
            // 计算窗口期收益率
            double startNav = (Double) navData.get(i - windowSize).get("nav");
            double endNav = (Double) navData.get(i).get("nav");
            double rollingReturn = (endNav / startNav - 1) * 100;
            returns.add(Math.round(rollingReturn * 100.0) / 100.0);
            
            // 计算窗口期波动率
            List<Double> dailyReturns = new ArrayList<>();
            for (int j = i - windowSize + 1; j <= i; j++) {
                double dailyRet = (Double) navData.get(j).get("dailyReturn");
                if (dailyRet != 0) {
                    dailyReturns.add(dailyRet);
                }
            }
            
            if (!dailyReturns.isEmpty()) {
                double mean = dailyReturns.stream().mapToDouble(Double::doubleValue).average().orElse(0);
                double variance = dailyReturns.stream()
                    .mapToDouble(r -> Math.pow(r - mean, 2))
                    .average().orElse(0);
                double dailyVol = Math.sqrt(variance);
                double annualVol = dailyVol * Math.sqrt(252) * 100;
                volatility.add(Math.round(annualVol * 100.0) / 100.0);
            } else {
                volatility.add(0.0);
            }
        }
        
        // 返回最近250个数据点
        int limit = Math.min(250, dates.size());
        if (dates.size() > limit) {
            result.put("dates", dates.subList(dates.size() - limit, dates.size()));
            result.put("returns", returns.subList(returns.size() - limit, returns.size()));
            result.put("volatility", volatility.subList(volatility.size() - limit, volatility.size()));
        } else {
            result.put("dates", dates);
            result.put("returns", returns);
            result.put("volatility", volatility);
        }
        
        return result;
    }
    
    /**
     * 计算回撤分析
     */
    private Map<String, Object> calculateDrawdownAnalysis(List<Map<String, Object>> navData) {
        Map<String, Object> result = new HashMap<>();
        
        // 计算最大回撤及相关信息
        double maxNav = 0;
        double maxDrawdown = 0;
        String maxDrawdownDate = null;
        String peakDate = null;
        
        for (Map<String, Object> nav : navData) {
            double currentNav = (Double) nav.get("nav");
            String date = (String) nav.get("date");
            
            if (currentNav > maxNav) {
                maxNav = currentNav;
                peakDate = date;
            }
            
            double drawdown = (maxNav - currentNav) / maxNav;
            if (drawdown > maxDrawdown) {
                maxDrawdown = drawdown;
                maxDrawdownDate = date;
            }
        }
        
        result.put("maxDrawdown", Math.round(maxDrawdown * 10000.0) / 100.0); // 百分比
        result.put("maxDrawdownDate", maxDrawdownDate);
        result.put("peakDate", peakDate);
        
        // 计算当前回撤
        double currentNav = (Double) navData.get(navData.size() - 1).get("nav");
        double currentMax = 0;
        for (Map<String, Object> nav : navData) {
            double n = (Double) nav.get("nav");
            if (n > currentMax) currentMax = n;
        }
        double currentDrawdown = (currentMax - currentNav) / currentMax * 100;
        result.put("currentDrawdown", Math.round(currentDrawdown * 100.0) / 100.0);
        
        return result;
    }
    
    /**
     * 获取同类排名（简化实现）
     */
    private Map<String, Object> getPeerRanking(String fundCode, String fundType, Map<String, Double> metrics) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            // 获取同类型基金数量
            String countSql = "SELECT COUNT(*) FROM fund WHERE fund_type LIKE ? AND nav_record_count > 0";
            Integer totalPeers = jdbcTemplate.queryForObject(countSql, 
                Integer.class, "%" + (fundType != null ? fundType.split("-")[0] : "") + "%");
            
            result.put("peerCount", totalPeers != null ? totalPeers : 0);
            
            // 简化排名计算（基于收益率分位）
            double returnRate = metrics.getOrDefault("totalReturn", 0.0);
            String rankLevel = returnRate > 0.1 ? "优秀" : returnRate > 0 ? "良好" : "一般";
            result.put("rankLevel", rankLevel);
            
        } catch (Exception e) {
            result.put("peerCount", 0);
            result.put("rankLevel", "--");
        }
        
        return result;
    }
    
    /**
     * 格式化风险指标
     */
    private Map<String, Object> formatRiskMetrics(Map<String, Double> metrics) {
        Map<String, Object> formatted = new HashMap<>();
        
        // 收益率指标（转为百分比）
        formatted.put("returnPeriod", round(metrics.getOrDefault("totalReturn", 0.0) * 100, 2));
        formatted.put("annualReturn", round(metrics.getOrDefault("annualizedReturn", 0.0) * 100, 2));
        
        // 风险指标
        formatted.put("maxDrawdown", round(metrics.getOrDefault("maxDrawdown", 0.0) * 100, 2));
        formatted.put("volatility", round(metrics.getOrDefault("volatility", 0.0) * 100, 2));
        
        // 风险调整收益
        formatted.put("sharpeRatio", round(metrics.getOrDefault("sharpeRatio", 0.0), 2));
        formatted.put("calmarRatio", round(metrics.getOrDefault("calmarRatio", 0.0), 2));
        formatted.put("winRate", round(metrics.getOrDefault("winRate", 0.0) * 100, 2));
        
        return formatted;
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
        map.put("establishmentDate", fund.getEstablishmentDate() != null 
            ? fund.getEstablishmentDate().toString() : null);
        map.put("managementFeeRate", fund.getManagementFeeRate());
        map.put("custodianFeeRate", fund.getCustodianFeeRate());
        
        if (fund.getEstablishmentDate() != null) {
            int years = Period.between(fund.getEstablishmentDate(), LocalDate.now()).getYears();
            map.put("establishedYears", years);
        }
        
        return map;
    }
    
    /**
     * 四舍五入
     */
    private double round(double value, int places) {
        if (Double.isNaN(value) || Double.isInfinite(value)) return 0;
        long factor = (long) Math.pow(10, places);
        return Math.round(value * factor) / (double) factor;
    }
}