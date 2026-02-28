package com.fundwise.controller;

import com.fundwise.entity.Fund;
import com.fundwise.repository.FundRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.Period;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * 基金详情控制器 - 提供基金详细信息、净值历史、业绩指标
 */
@RestController
@RequestMapping("/api/v1/funds")
@CrossOrigin(origins = "*")
public class FundDetailController {
    
    private final FundRepository fundRepository;
    private final JdbcTemplate jdbcTemplate;
    
    public FundDetailController(FundRepository fundRepository, JdbcTemplate jdbcTemplate) {
        this.fundRepository = fundRepository;
        this.jdbcTemplate = jdbcTemplate;
    }
    
    /**
     * 获取基金完整详情（含净值历史和业绩指标）
     * GET /api/v1/funds/{code}/detail
     */
    @GetMapping("/{code}/detail")
    public ResponseEntity<Map<String, Object>> getFundFullDetail(@PathVariable String code) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            // 1. 获取基金基本信息
            Fund fund = fundRepository.findByFundCode(code)
                .orElseThrow(() -> new RuntimeException("基金不存在：" + code));
            
            Map<String, Object> fundInfo = convertFundToMap(fund);
            
            // 2. 获取净值历史（最近一年，最多252个交易日）
            List<Map<String, Object>> navHistory = getNavHistory(code, 252);
            fundInfo.put("navHistory", navHistory);
            
            // 3. 计算业绩指标
            Map<String, Object> performance = calculatePerformanceMetrics(code, navHistory);
            fundInfo.put("performance", performance);
            
            // 4. 获取净值统计
            Map<String, Object> navStats = getNavStats(code);
            fundInfo.put("navStats", navStats);
            
            response.put("success", true);
            response.put("data", fundInfo);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.ok(response);
        }
    }
    
    /**
     * 获取基金净值历史
     * GET /api/v1/funds/{code}/nav?limit=100
     */
    @GetMapping("/{code}/nav")
    public ResponseEntity<Map<String, Object>> getFundNavHistory(
            @PathVariable String code,
            @RequestParam(defaultValue = "100") int limit,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            List<Map<String, Object>> navHistory;
            
            if (startDate != null && endDate != null) {
                navHistory = getNavHistoryByDateRange(code, startDate, endDate);
            } else {
                navHistory = getNavHistory(code, limit);
            }
            
            response.put("success", true);
            response.put("data", navHistory);
            response.put("count", navHistory.size());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.ok(response);
        }
    }
    
    /**
     * 获取基金业绩指标
     * GET /api/v1/funds/{code}/performance?period=1y
     */
    @GetMapping("/{code}/performance")
    public ResponseEntity<Map<String, Object>> getFundPerformance(
            @PathVariable String code,
            @RequestParam(defaultValue = "1y") String period) {
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            // 根据period确定查询的记录数
            int limit = getLimitByPeriod(period);
            List<Map<String, Object>> navHistory = getNavHistory(code, limit);
            
            Map<String, Object> performance = calculatePerformanceMetrics(code, navHistory);
            
            response.put("success", true);
            response.put("data", performance);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.ok(response);
        }
    }
    
    /**
     * 获取同类基金对比
     * GET /api/v1/funds/{code}/compare
     */
    @GetMapping("/{code}/compare")
    public ResponseEntity<Map<String, Object>> getFundComparison(@PathVariable String code) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            Fund fund = fundRepository.findByFundCode(code)
                .orElseThrow(() -> new RuntimeException("基金不存在：" + code));
            
            String fundType = fund.getFundType();
            if (fundType == null) {
                fundType = "混合型";
            }
            
            // 获取同类型基金（按规模排序，取前10）
            String sql = """
                SELECT fund_code, fund_name, fund_type, risk_level, total_assets,
                       establishment_date
                FROM fund
                WHERE fund_type LIKE ? AND fund_code != ?
                ORDER BY total_assets DESC
                LIMIT 10
            """;
            
            List<Map<String, Object>> peers = jdbcTemplate.query(sql, (rs, rowNum) -> {
                Map<String, Object> peer = new HashMap<>();
                peer.put("code", rs.getString("fund_code"));
                peer.put("name", rs.getString("fund_name"));
                peer.put("type", rs.getString("fund_type"));
                peer.put("riskLevel", rs.getString("risk_level"));
                peer.put("totalAssets", rs.getDouble("total_assets"));
                
                java.sql.Date estDate = rs.getDate("establishment_date");
                if (estDate != null) {
                    int years = Period.between(estDate.toLocalDate(), LocalDate.now()).getYears();
                    peer.put("establishedYears", years);
                }
                
                return peer;
            }, "%" + fundType.split("-")[0] + "%", code);
            
            response.put("success", true);
            response.put("data", Map.of(
                "fund", convertFundToMap(fund),
                "peers", peers,
                "fundType", fundType,
                "peersCount", peers.size()
            ));
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.ok(response);
        }
    }
    
    // ============ 私有方法 ============
    
    /**
     * 获取净值历史
     */
    private List<Map<String, Object>> getNavHistory(String fundCode, int limit) {
        String sql = """
            SELECT nav_date, nav, accumulated_nav, daily_return
            FROM fund_nav
            WHERE fund_code = ?
            ORDER BY nav_date DESC
            LIMIT ?
        """;
        
        return jdbcTemplate.query(sql, (rs, rowNum) -> {
            Map<String, Object> nav = new HashMap<>();
            nav.put("date", rs.getDate("nav_date").toString());
            nav.put("nav", rs.getDouble("nav"));
            nav.put("accumulatedNav", rs.getDouble("accumulated_nav"));
            nav.put("dailyReturn", rs.getDouble("daily_return"));
            return nav;
        }, fundCode, limit);
    }
    
    /**
     * 按日期范围获取净值历史
     */
    private List<Map<String, Object>> getNavHistoryByDateRange(String fundCode, String startDate, String endDate) {
        String sql = """
            SELECT nav_date, nav, accumulated_nav, daily_return
            FROM fund_nav
            WHERE fund_code = ? AND nav_date BETWEEN ? AND ?
            ORDER BY nav_date ASC
        """;
        
        return jdbcTemplate.query(sql, (rs, rowNum) -> {
            Map<String, Object> nav = new HashMap<>();
            nav.put("date", rs.getDate("nav_date").toString());
            nav.put("nav", rs.getDouble("nav"));
            nav.put("accumulatedNav", rs.getDouble("accumulated_nav"));
            nav.put("dailyReturn", rs.getDouble("daily_return"));
            return nav;
        }, fundCode, startDate, endDate);
    }
    
    /**
     * 获取净值统计
     */
    private Map<String, Object> getNavStats(String fundCode) {
        String sql = """
            SELECT 
                COUNT(*) as total_records,
                MIN(nav_date) as first_date,
                MAX(nav_date) as last_date,
                MIN(nav) as min_nav,
                MAX(nav) as max_nav,
                AVG(nav) as avg_nav,
                MAX(accumulated_nav) as max_accumulated_nav
            FROM fund_nav
            WHERE fund_code = ?
        """;
        
        return jdbcTemplate.queryForObject(sql, (rs, rowNum) -> {
            Map<String, Object> stats = new HashMap<>();
            stats.put("totalRecords", rs.getInt("total_records"));
            stats.put("firstDate", rs.getDate("first_date") != null ? rs.getDate("first_date").toString() : null);
            stats.put("lastDate", rs.getDate("last_date") != null ? rs.getDate("last_date").toString() : null);
            stats.put("minNav", rs.getDouble("min_nav"));
            stats.put("maxNav", rs.getDouble("max_nav"));
            stats.put("avgNav", rs.getDouble("avg_nav"));
            stats.put("maxAccumulatedNav", rs.getDouble("max_accumulated_nav"));
            return stats;
        }, fundCode);
    }
    
    /**
     * 计算业绩指标
     */
    private Map<String, Object> calculatePerformanceMetrics(String fundCode, List<Map<String, Object>> navHistory) {
        Map<String, Object> metrics = new HashMap<>();
        
        if (navHistory == null || navHistory.size() < 2) {
            metrics.put("error", "净值数据不足，无法计算业绩指标");
            return metrics;
        }
        
        // 反转列表（从旧到新）
        Collections.reverse(navHistory);
        
        // 计算收益率
        double firstNav = (Double) navHistory.get(0).get("nav");
        double lastNav = (Double) navHistory.get(navHistory.size() - 1).get("nav");
        double totalReturn = (lastNav - firstNav) / firstNav;
        
        // 计算年化收益率
        String firstDate = (String) navHistory.get(0).get("date");
        String lastDate = (String) navHistory.get(navHistory.size() - 1).get("date");
        long days = ChronoUnit.DAYS.between(LocalDate.parse(firstDate), LocalDate.parse(lastDate));
        double years = days / 365.0;
        double annualizedReturn = years > 0 ? Math.pow(1 + totalReturn, 1.0 / years) - 1 : 0;
        
        // 计算最大回撤
        double maxDrawdown = 0;
        double peak = firstNav;
        for (Map<String, Object> nav : navHistory) {
            double currentNav = (Double) nav.get("nav");
            if (currentNav > peak) {
                peak = currentNav;
            }
            double drawdown = (peak - currentNav) / peak;
            if (drawdown > maxDrawdown) {
                maxDrawdown = drawdown;
            }
        }
        
        // 计算夏普比率
        List<Double> dailyReturns = new ArrayList<>();
        for (int i = 1; i < navHistory.size(); i++) {
            Double prevNav = (Double) navHistory.get(i - 1).get("nav");
            Double currNav = (Double) navHistory.get(i).get("nav");
            if (prevNav != null && currNav != null && prevNav > 0) {
                dailyReturns.add((currNav - prevNav) / prevNav);
            }
        }
        
        double sharpeRatio = 0;
        if (!dailyReturns.isEmpty()) {
            double avgDailyReturn = dailyReturns.stream().mapToDouble(Double::doubleValue).average().orElse(0);
            double variance = dailyReturns.stream()
                .mapToDouble(r -> Math.pow(r - avgDailyReturn, 2))
                .average().orElse(0);
            double dailyVolatility = Math.sqrt(variance);
            double annualVolatility = dailyVolatility * Math.sqrt(252);
            double riskFreeRate = 0.03; // 3% 无风险利率
            sharpeRatio = annualVolatility > 0 ? (annualizedReturn - riskFreeRate) / annualVolatility : 0;
        }
        
        // 计算波动率
        double volatility = 0;
        if (!dailyReturns.isEmpty()) {
            double avgReturn = dailyReturns.stream().mapToDouble(Double::doubleValue).average().orElse(0);
            double variance = dailyReturns.stream()
                .mapToDouble(r -> Math.pow(r - avgReturn, 2))
                .average().orElse(0);
            volatility = Math.sqrt(variance) * Math.sqrt(252);
        }
        
        // 胜率
        long winDays = dailyReturns.stream().filter(r -> r > 0).count();
        double winRate = dailyReturns.size() > 0 ? (double) winDays / dailyReturns.size() : 0;
        
        // 构建结果
        metrics.put("totalReturn", round(totalReturn, 4));
        metrics.put("annualizedReturn", round(annualizedReturn, 4));
        metrics.put("maxDrawdown", round(maxDrawdown, 4));
        metrics.put("sharpeRatio", round(sharpeRatio, 2));
        metrics.put("volatility", round(volatility, 4));
        metrics.put("winRate", round(winRate, 4));
        metrics.put("totalDays", navHistory.size());
        metrics.put("tradingDays", dailyReturns.size());
        metrics.put("startDate", firstDate);
        metrics.put("endDate", lastDate);
        
        return metrics;
    }
    
    /**
     * 根据period参数获取记录数限制
     */
    private int getLimitByPeriod(String period) {
        return switch (period) {
            case "1m" -> 22;
            case "3m" -> 66;
            case "6m" -> 132;
            case "1y" -> 252;
            case "3y" -> 756;
            case "5y" -> 1260;
            case "all" -> 10000;
            default -> 252;
        };
    }
    
    /**
     * 转换Fund实体为Map
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
        map.put("managementFeeRate", fund.getManagementFeeRate());
        map.put("custodianFeeRate", fund.getCustodianFeeRate());
        
        if (fund.getEstablishmentDate() != null) {
            map.put("establishmentDate", fund.getEstablishmentDate().toString());
            int years = Period.between(fund.getEstablishmentDate(), LocalDate.now()).getYears();
            map.put("establishedYears", years);
        }
        
        return map;
    }
    
    /**
     * 四舍五入
     */
    private double round(double value, int places) {
        return BigDecimal.valueOf(value).setScale(places, RoundingMode.HALF_UP).doubleValue();
    }
}