package com.fundwise.service;

import com.fundwise.entity.Fund;
import com.fundwise.entity.Portfolio;
import com.fundwise.entity.PortfolioHolding;
import com.fundwise.repository.FundRepository;
import com.fundwise.repository.PortfolioHoldingRepository;
import com.fundwise.repository.PortfolioRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 回测服务 - 基于真实历史净值计算投资组合表现
 */
@Service
@Transactional(readOnly = true)
public class BacktestService {
    
    private final PortfolioRepository portfolioRepository;
    private final PortfolioHoldingRepository holdingRepository;
    private final FundRepository fundRepository;
    private final JdbcTemplate jdbcTemplate;
    
    public BacktestService(PortfolioRepository portfolioRepository,
                          PortfolioHoldingRepository holdingRepository,
                          FundRepository fundRepository,
                          JdbcTemplate jdbcTemplate) {
        this.portfolioRepository = portfolioRepository;
        this.holdingRepository = holdingRepository;
        this.fundRepository = fundRepository;
        this.jdbcTemplate = jdbcTemplate;
    }
    
    /**
     * 运行回测
     * @param portfolioId 组合 ID
     * @param startDate 开始日期
     * @param endDate 结束日期
     * @param initialCapital 初始资金
     * @return 回测结果
     */
    public Map<String, Object> runBacktest(Long portfolioId, String startDate, String endDate, 
                                          Double initialCapital) {
        // 获取组合信息
        Portfolio portfolio = portfolioRepository.findById(portfolioId)
            .orElseThrow(() -> new RuntimeException("组合不存在：" + portfolioId));
        
        // 获取持仓
        List<PortfolioHolding> holdings = holdingRepository.findByPortfolioIdOrderByCreatedAtAsc(portfolioId);
        if (holdings.isEmpty()) {
            throw new RuntimeException("组合中没有基金");
        }
        
        // 获取所有持仓基金代码和权重
        Map<String, Double> fundWeights = new HashMap<>();
        for (PortfolioHolding holding : holdings) {
            String fundCode = holding.getFundCode();
            Double weight = holding.getTargetWeight().doubleValue();
            fundWeights.put(fundCode, weight);
        }
        
        // 获取回测期间的净值数据
        Map<String, List<NavData>> fundNavMap = getNavDataForPeriod(
            fundWeights.keySet(), 
            LocalDate.parse(startDate), 
            LocalDate.parse(endDate)
        );
        
        // 计算组合每日净值
        List<DailyBacktestData> dailyData = calculatePortfolioNav(fundWeights, fundNavMap, initialCapital);
        
        // 计算回测指标
        Map<String, Object> metrics = calculateMetrics(dailyData, initialCapital);
        
        // 构建结果
        Map<String, Object> result = new HashMap<>();
        result.put("portfolioId", portfolioId);
        result.put("portfolioName", portfolio.getName());
        result.put("startDate", startDate);
        result.put("endDate", endDate);
        result.put("initialCapital", initialCapital);
        result.put("finalValue", metrics.get("finalValue"));
        result.put("totalReturn", metrics.get("totalReturn"));
        result.put("annualizedReturn", metrics.get("annualizedReturn"));
        result.put("maxDrawdown", metrics.get("maxDrawdown"));
        result.put("sharpeRatio", metrics.get("sharpeRatio"));
        result.put("volatility", metrics.get("volatility"));
        result.put("winRate", metrics.get("winRate"));
        result.put("totalDays", dailyData.size());
        result.put("dailyData", dailyData);
        
        return result;
    }
    
    /**
     * 获取指定期间的净值数据
     */
    private Map<String, List<NavData>> getNavDataForPeriod(Set<String> fundCodes, 
                                                           LocalDate startDate, 
                                                           LocalDate endDate) {
        Map<String, List<NavData>> result = new HashMap<>();
        
        String sql = """
            SELECT fund_code, nav_date, nav, accumulated_nav, daily_return
            FROM fund_nav
            WHERE fund_code = ?
            AND nav_date BETWEEN ? AND ?
            ORDER BY nav_date ASC
        """;
        
        DateTimeFormatter formatter = DateTimeFormatter.ISO_LOCAL_DATE;
        
        for (String fundCode : fundCodes) {
            try {
                List<NavData> navList = jdbcTemplate.query(sql, (rs, rowNum) -> {
                    NavData nav = new NavData();
                    nav.fundCode = rs.getString("fund_code");
                    nav.navDate = rs.getDate("nav_date").toLocalDate();
                    nav.nav = rs.getDouble("nav");
                    nav.accumulatedNav = rs.getDouble("accumulated_nav");
                    nav.dailyReturn = rs.getDouble("daily_return");
                    return nav;
                }, fundCode, startDate.format(formatter), endDate.format(formatter));
                
                result.put(fundCode, navList);
            } catch (Exception e) {
                System.err.println("获取基金 " + fundCode + " 净值失败：" + e.getMessage());
                result.put(fundCode, new ArrayList<>());
            }
        }
        
        return result;
    }
    
    /**
     * 计算组合每日净值
     */
    private List<DailyBacktestData> calculatePortfolioNav(Map<String, Double> fundWeights,
                                                          Map<String, List<NavData>> fundNavMap,
                                                          Double initialCapital) {
        List<DailyBacktestData> result = new ArrayList<>();
        
        // 获取所有日期
        Set<LocalDate> allDates = new TreeSet<>();
        for (List<NavData> navList : fundNavMap.values()) {
            for (NavData nav : navList) {
                allDates.add(nav.navDate);
            }
        }
        
        if (allDates.isEmpty()) {
            return result;
        }
        
        // 计算每个日期的组合净值
        double totalWeight = fundWeights.values().stream().mapToDouble(Double::doubleValue).sum();
        
        for (LocalDate date : allDates) {
            double portfolioReturn = 0.0;
            boolean hasData = false;
            
            for (Map.Entry<String, Double> entry : fundWeights.entrySet()) {
                String fundCode = entry.getKey();
                Double weight = entry.getValue() / totalWeight; // 归一化权重
                
                List<NavData> navList = fundNavMap.get(fundCode);
                if (navList == null) continue;
                
                // 找到该日期的净值
                for (NavData nav : navList) {
                    if (nav.navDate.equals(date)) {
                        portfolioReturn += nav.dailyReturn * weight;
                        hasData = true;
                        break;
                    }
                }
            }
            
            if (hasData) {
                DailyBacktestData data = new DailyBacktestData();
                data.date = date;
                data.dailyReturn = portfolioReturn;
                result.add(data);
            }
        }
        
        // 计算累计净值和市值
        double cumulativeNav = 1.0;
        for (DailyBacktestData data : result) {
            cumulativeNav *= (1 + data.dailyReturn);
            data.cumulativeNav = cumulativeNav;
            data.portfolioValue = initialCapital * cumulativeNav;
        }
        
        return result;
    }
    
    /**
     * 计算回测指标
     */
    private Map<String, Object> calculateMetrics(List<DailyBacktestData> dailyData, Double initialCapital) {
        Map<String, Object> metrics = new HashMap<>();
        
        if (dailyData.isEmpty()) {
            metrics.put("finalValue", initialCapital);
            metrics.put("totalReturn", 0.0);
            metrics.put("annualizedReturn", 0.0);
            metrics.put("maxDrawdown", 0.0);
            metrics.put("sharpeRatio", 0.0);
            metrics.put("volatility", 0.0);
            metrics.put("winRate", 0.0);
            return metrics;
        }
        
        // 最终市值
        double finalValue = dailyData.get(dailyData.size() - 1).portfolioValue;
        metrics.put("finalValue", finalValue);
        
        // 总收益率
        double totalReturn = (finalValue - initialCapital) / initialCapital;
        metrics.put("totalReturn", totalReturn);
        
        // 年化收益率
        int days = dailyData.size();
        double years = days / 252.0; // 252 个交易日/年
        double annualizedReturn = years > 0 ? Math.pow(finalValue / initialCapital, 1.0 / years) - 1 : 0;
        metrics.put("annualizedReturn", annualizedReturn);
        
        // 最大回撤
        double maxDrawdown = 0;
        double peak = initialCapital;
        for (DailyBacktestData data : dailyData) {
            if (data.portfolioValue > peak) {
                peak = data.portfolioValue;
            }
            double drawdown = (peak - data.portfolioValue) / peak;
            if (drawdown > maxDrawdown) {
                maxDrawdown = drawdown;
            }
        }
        metrics.put("maxDrawdown", maxDrawdown);
        
        // 波动率（年化）
        double avgReturn = dailyData.stream()
            .mapToDouble(d -> d.dailyReturn)
            .average()
            .orElse(0.0);
        
        double variance = dailyData.stream()
            .mapToDouble(d -> Math.pow(d.dailyReturn - avgReturn, 2))
            .average()
            .orElse(0.0);
        
        double dailyVolatility = Math.sqrt(variance);
        double annualVolatility = dailyVolatility * Math.sqrt(252);
        metrics.put("volatility", annualVolatility);
        
        // 夏普比率（假设无风险利率 3%）
        double riskFreeRate = 0.03;
        double sharpeRatio = annualVolatility > 0 ? (annualizedReturn - riskFreeRate) / annualVolatility : 0;
        metrics.put("sharpeRatio", sharpeRatio);
        
        // 胜率
        long winDays = dailyData.stream()
            .filter(d -> d.dailyReturn > 0)
            .count();
        double winRate = days > 0 ? (double) winDays / days : 0;
        metrics.put("winRate", winRate);
        
        return metrics;
    }
    
    /**
     * 净值数据内部类
     */
    private static class NavData {
        String fundCode;
        LocalDate navDate;
        double nav;
        double accumulatedNav;
        double dailyReturn;
    }
    
    /**
     * 回测每日数据内部类
     */
    public static class DailyBacktestData {
        LocalDate date;
        double dailyReturn;
        double cumulativeNav;
        double portfolioValue;
        
        public Map<String, Object> toMap() {
            Map<String, Object> map = new HashMap<>();
            map.put("date", date.toString());
            map.put("dailyReturn", dailyReturn);
            map.put("cumulativeNav", cumulativeNav);
            map.put("portfolioValue", portfolioValue);
            return map;
        }
    }
}
