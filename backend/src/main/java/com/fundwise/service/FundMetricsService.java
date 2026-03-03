package com.fundwise.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 基金指标计算服务 - 基于真实净值数据计算收益率、最大回撤、夏普比率等
 */
@Service
public class FundMetricsService {
    
    private final JdbcTemplate jdbcTemplate;
    
    // 无风险利率（年化）
    private static final double RISK_FREE_RATE = 0.03;
    
    public FundMetricsService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }
    
    /**
     * 批量计算基金指标（优化版，一次查询多只基金）
     * @param fundCodes 基金代码列表
     * @param periodDays 计算周期（天数），如 365 表示近一年
     * @return Map<基金代码, 指标数据>
     */
    public Map<String, Map<String, Double>> batchCalculateMetrics(List<String> fundCodes, int periodDays) {
        Map<String, Map<String, Double>> result = new HashMap<>();
        
        if (fundCodes == null || fundCodes.isEmpty()) {
            return result;
        }
        
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(periodDays);
        
        // 构建IN子句
        String inClause = fundCodes.stream().map(c -> "'" + c + "'").collect(java.util.stream.Collectors.joining(","));
        
        String sql = String.format("""
            SELECT fund_code, nav_date, nav, accumulated_nav, daily_return
            FROM fund_nav
            WHERE fund_code IN (%s)
            AND nav_date >= '%s'
            ORDER BY fund_code, nav_date ASC
        """, inClause, startDate);
        
        try {
            // 按基金分组数据
            Map<String, List<NavPoint>> navByFund = new HashMap<>();
            
            jdbcTemplate.query(sql, rs -> {
                String fundCode = rs.getString("fund_code");
                NavPoint nav = new NavPoint();
                nav.navDate = rs.getDate("nav_date").toLocalDate();
                nav.nav = rs.getDouble("nav");
                nav.dailyReturn = rs.getDouble("daily_return");
                
                navByFund.computeIfAbsent(fundCode, k -> new java.util.ArrayList<>()).add(nav);
            });
            
            // 计算每只基金的指标
            for (Map.Entry<String, List<NavPoint>> entry : navByFund.entrySet()) {
                String fundCode = entry.getKey();
                List<NavPoint> navList = entry.getValue();
                
                if (navList.size() >= 10) { // 至少10个数据点
                    result.put(fundCode, calculateMetricsFromNav(navList));
                } else {
                    result.put(fundCode, getDefaultMetrics());
                }
            }
            
            // 没有数据的基金也返回默认值
            for (String code : fundCodes) {
                if (!result.containsKey(code)) {
                    result.put(code, getDefaultMetrics());
                }
            }
        } catch (Exception e) {
            System.err.println("批量计算指标失败: " + e.getMessage());
            e.printStackTrace();
        }
        
        return result;
    }
    
    /**
     * 计算单只基金的指标
     */
    public Map<String, Double> calculateMetrics(String fundCode, int periodDays) {
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(periodDays);
        
        String sql = """
            SELECT nav_date, nav, accumulated_nav, daily_return
            FROM fund_nav
            WHERE fund_code = ?
            AND nav_date >= ?
            ORDER BY nav_date ASC
        """;
        
        try {
            List<NavPoint> navList = jdbcTemplate.query(sql, (rs, rowNum) -> {
                NavPoint nav = new NavPoint();
                nav.navDate = rs.getDate("nav_date").toLocalDate();
                nav.nav = rs.getDouble("nav");
                nav.dailyReturn = rs.getDouble("daily_return");
                return nav;
            }, fundCode, startDate);
            
            if (navList.size() >= 10) {
                return calculateMetricsFromNav(navList);
            }
        } catch (Exception e) {
            System.err.println("计算基金 " + fundCode + " 指标失败: " + e.getMessage());
        }
        
        return getDefaultMetrics();
    }
    
    /**
     * 从净值数据计算指标
     */
    private Map<String, Double> calculateMetricsFromNav(List<NavPoint> navList) {
        Map<String, Double> metrics = new HashMap<>();
        
        // 计算总收益率
        double startNav = navList.get(0).nav;
        double endNav = navList.get(navList.size() - 1).nav;
        double totalReturn = (endNav - startNav) / startNav;
        metrics.put("totalReturn", totalReturn);
        
        // 计算年化收益率
        long days = ChronoUnit.DAYS.between(navList.get(0).navDate, navList.get(navList.size() - 1).navDate);
        double years = days / 365.0;
        double annualizedReturn = years > 0 ? Math.pow(endNav / startNav, 1.0 / years) - 1 : 0;
        metrics.put("annualizedReturn", annualizedReturn);
        
        // 计算日收益率列表
        List<Double> dailyReturns = navList.stream()
            .filter(n -> n.dailyReturn != 0)
            .map(n -> n.dailyReturn)
            .toList();
        
        if (dailyReturns.isEmpty()) {
            // 如果 daily_return 字段为空，手动计算
            dailyReturns = new java.util.ArrayList<>();
            for (int i = 1; i < navList.size(); i++) {
                double ret = (navList.get(i).nav - navList.get(i-1).nav) / navList.get(i-1).nav;
                ((java.util.ArrayList<Double>) dailyReturns).add(ret);
            }
        }
        
        // 计算最大回撤
        double maxNav = Double.MIN_VALUE;
        double maxDrawdown = 0;
        for (NavPoint nav : navList) {
            if (nav.nav > maxNav) {
                maxNav = nav.nav;
            }
            double drawdown = (maxNav - nav.nav) / maxNav;
            if (drawdown > maxDrawdown) {
                maxDrawdown = drawdown;
            }
        }
        metrics.put("maxDrawdown", maxDrawdown);
        
        // 计算波动率（年化）
        if (!dailyReturns.isEmpty()) {
            double avgReturn = dailyReturns.stream().mapToDouble(Double::doubleValue).average().orElse(0);
            double variance = dailyReturns.stream()
                .mapToDouble(r -> Math.pow(r - avgReturn, 2))
                .average().orElse(0);
            double dailyVolatility = Math.sqrt(variance);
            double annualVolatility = dailyVolatility * Math.sqrt(252);
            metrics.put("volatility", annualVolatility);
            
            // 计算夏普比率
            double sharpeRatio = annualVolatility > 0 ? (annualizedReturn - RISK_FREE_RATE) / annualVolatility : 0;
            metrics.put("sharpeRatio", sharpeRatio);
            
            // 计算胜率
            long winDays = dailyReturns.stream().filter(r -> r > 0).count();
            double winRate = (double) winDays / dailyReturns.size();
            metrics.put("winRate", winRate);
            
            // 计算 Calmar 比率（年化收益率 / 最大回撤绝对值）
            double calmarRatio = maxDrawdown > 0 ? annualizedReturn / maxDrawdown : 0;
            metrics.put("calmarRatio", calmarRatio);
        } else {
            metrics.put("volatility", 0.0);
            metrics.put("sharpeRatio", 0.0);
            metrics.put("winRate", 0.0);
            metrics.put("calmarRatio", 0.0);
        }
        
        return metrics;
    }
    
    /**
     * 默认指标（无数据时返回）
     */
    private Map<String, Double> getDefaultMetrics() {
        Map<String, Double> metrics = new HashMap<>();
        metrics.put("totalReturn", 0.0);
        metrics.put("annualizedReturn", 0.0);
        metrics.put("maxDrawdown", 0.0);
        metrics.put("volatility", 0.0);
        metrics.put("sharpeRatio", 0.0);
        metrics.put("winRate", 0.0);
        metrics.put("calmarRatio", 0.0);
        return metrics;
    }
    
    /**
     * 净值数据点
     */
    private static class NavPoint {
        LocalDate navDate;
        double nav;
        double dailyReturn;
    }
}