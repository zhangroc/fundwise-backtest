package com.fundwise.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 基金筛选服务 - 多维度筛选、排名
 * 参考 Python 版本的 FundScreener 实现
 */
@Service
public class FundScreenerService {
    
    private final JdbcTemplate jdbcTemplate;
    private final FundMetricsService metricsService;
    
    // 交易日常量
    private static final int TRADING_DAYS_PER_YEAR = 252;
    
    // 无风险利率（年化）
    private static final double RISK_FREE_RATE = 0.02;
    
    // 时间段映射
    private static final Map<String, Integer> PERIOD_DAYS = Map.of(
        "1m", 21,
        "3m", 63,
        "6m", 126,
        "1y", 252,
        "2y", 504,
        "3y", 756,
        "5y", 1260
    );
    
    public FundScreenerService(JdbcTemplate jdbcTemplate, FundMetricsService metricsService) {
        this.jdbcTemplate = jdbcTemplate;
        this.metricsService = metricsService;
    }
    
    /**
     * 多维度筛选基金
     * 
     * @param filters 筛选条件
     * @return 筛选结果列表
     */
    public Map<String, Object> screenFunds(ScreeningFilters filters) {
        Map<String, Object> result = new HashMap<>();
        
        int tradingDays = PERIOD_DAYS.getOrDefault(filters.period, 252);
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays((long) (tradingDays * 1.5)); // 多取一些数据
        
        // 第一步：数据库层面筛选（基础条件）
        List<Map<String, Object>> candidates = queryCandidatesFromDB(filters);
        
        if (candidates.isEmpty()) {
            result.put("funds", Collections.emptyList());
            result.put("total", 0);
            return result;
        }
        
        // 第二步：计算每只基金的指标
        List<String> fundCodes = candidates.stream()
            .map(f -> (String) f.get("fund_code"))
            .collect(Collectors.toList());
        
        Map<String, Map<String, Double>> metricsMap = metricsService.batchCalculateMetrics(fundCodes, tradingDays);
        
        // 第三步：合并数据并应用指标筛选
        List<Map<String, Object>> fundsWithMetrics = new ArrayList<>();
        for (Map<String, Object> fund : candidates) {
            String code = (String) fund.get("fund_code");
            Map<String, Object> fundData = new HashMap<>(fund);
            
            Map<String, Double> metrics = metricsMap.getOrDefault(code, getDefaultMetrics());
            fundData.putAll(flattenMetrics(metrics));
            
            // 应用指标筛选
            if (passesMetricFilters(fundData, filters)) {
                fundsWithMetrics.add(fundData);
            }
        }
        
        // 第四步：排序
        sortFunds(fundsWithMetrics, filters.sortBy, filters.sortOrder);
        
        // 第五步：分页（支持 page=0 和 page=1 两种习惯）
        int total = fundsWithMetrics.size();
        int pageNum = filters.page <= 0 ? 0 : filters.page - 1; // 兼容 page=0
        int fromIndex = pageNum * filters.pageSize;
        int toIndex = Math.min(fromIndex + filters.pageSize, total);
        
        List<Map<String, Object>> pagedResults = (fromIndex >= 0 && fromIndex < total)
            ? fundsWithMetrics.subList(fromIndex, toIndex) 
            : Collections.emptyList();
        
        result.put("funds", pagedResults);
        result.put("total", total);
        result.put("page", filters.page);
        result.put("pageSize", filters.pageSize);
        result.put("period", filters.period);
        
        return result;
    }
    
    /**
     * 从数据库查询候选基金（应用基础筛选条件）
     */
    private List<Map<String, Object>> queryCandidatesFromDB(ScreeningFilters filters) {
        StringBuilder sql = new StringBuilder("""
            SELECT fund_code, fund_name, fund_type, fund_company, 
                   establishment_date, is_index_fund, risk_level, 
                   total_assets, nav_record_count, nav_start_date, nav_end_date
            FROM fund 
            WHERE nav_record_count > 0
        """);
        
        List<Object> params = new ArrayList<>();
        
        // 基金类型筛选
        if (filters.fundType != null && !"all".equals(filters.fundType)) {
            sql.append(" AND fund_type LIKE ?");
            params.add("%" + filters.fundType + "%");
        }
        
        // 指数基金筛选
        if (Boolean.TRUE.equals(filters.indexFundOnly)) {
            sql.append(" AND is_index_fund = true");
        }
        
        // 基金规模筛选
        if (filters.minSize != null) {
            sql.append(" AND total_assets >= ?");
            params.add(filters.minSize);
        }
        if (filters.maxSize != null) {
            sql.append(" AND total_assets <= ?");
            params.add(filters.maxSize);
        }
        
        // 成立年限筛选
        if (filters.minYears != null && filters.minYears > 0) {
            LocalDate cutoffDate = LocalDate.now().minusYears(filters.minYears);
            sql.append(" AND establishment_date <= ?");
            params.add(cutoffDate);
        }
        
        // 风险等级筛选
        if (filters.riskLevel != null && !"all".equals(filters.riskLevel)) {
            sql.append(" AND risk_level = ?");
            params.add(filters.riskLevel);
        }
        
        // 基金公司筛选
        if (filters.company != null && !filters.company.isEmpty()) {
            sql.append(" AND fund_company LIKE ?");
            params.add("%" + filters.company + "%");
        }
        
        // 关键词搜索
        if (filters.keyword != null && !filters.keyword.isEmpty()) {
            sql.append(" AND (fund_name LIKE ? OR fund_code LIKE ?)");
            params.add("%" + filters.keyword + "%");
            params.add("%" + filters.keyword + "%");
        }
        
        // 限制初步查询数量，避免内存溢出
        sql.append(" ORDER BY nav_record_count DESC LIMIT 2000");
        
        try {
            return jdbcTemplate.query(sql.toString(), params.toArray(), (rs, rowNum) -> {
                Map<String, Object> map = new HashMap<>();
                map.put("fund_code", rs.getString("fund_code"));
                map.put("fund_name", rs.getString("fund_name"));
                map.put("fund_type", rs.getString("fund_type"));
                map.put("fund_company", rs.getString("fund_company"));
                map.put("establishment_date", rs.getDate("establishment_date"));
                map.put("is_index_fund", rs.getBoolean("is_index_fund"));
                map.put("risk_level", rs.getString("risk_level"));
                map.put("total_assets", rs.getDouble("total_assets"));
                map.put("nav_record_count", rs.getInt("nav_record_count"));
                map.put("nav_start_date", rs.getDate("nav_start_date"));
                map.put("nav_end_date", rs.getDate("nav_end_date"));
                
                // 计算成立年限
                java.sql.Date estDate = rs.getDate("establishment_date");
                if (estDate != null) {
                    int years = (int) ChronoUnit.YEARS.between(estDate.toLocalDate(), LocalDate.now());
                    map.put("established_years", years);
                }
                
                return map;
            });
        } catch (Exception e) {
            System.err.println("查询候选基金失败: " + e.getMessage());
            return Collections.emptyList();
        }
    }
    
    /**
     * 检查基金是否通过指标筛选条件
     */
    private boolean passesMetricFilters(Map<String, Object> fund, ScreeningFilters filters) {
        // 最低收益率筛选
        if (filters.minReturn != null) {
            Double returnRate = (Double) fund.get("return_period");
            if (returnRate == null || returnRate < filters.minReturn) {
                return false;
            }
        }
        
        // 最大回撤筛选（回撤为负值，比较绝对值）
        if (filters.maxDrawdown != null) {
            Double maxDd = (Double) fund.get("max_drawdown");
            if (maxDd != null && Math.abs(maxDd) > Math.abs(filters.maxDrawdown)) {
                return false;
            }
        }
        
        // 最低夏普比率筛选
        if (filters.minSharpe != null) {
            Double sharpe = (Double) fund.get("sharpe_ratio");
            if (sharpe == null || sharpe < filters.minSharpe) {
                return false;
            }
        }
        
        // 最大波动率筛选
        if (filters.maxVolatility != null) {
            Double volatility = (Double) fund.get("volatility");
            if (volatility != null && volatility > filters.maxVolatility) {
                return false;
            }
        }
        
        // 最低胜率筛选
        if (filters.minWinRate != null) {
            Double winRate = (Double) fund.get("win_rate");
            if (winRate == null || winRate < filters.minWinRate) {
                return false;
            }
        }
        
        // 最低Calmar比率筛选
        if (filters.minCalmar != null) {
            Double calmar = (Double) fund.get("calmar_ratio");
            if (calmar == null || calmar < filters.minCalmar) {
                return false;
            }
        }
        
        // 最低成立年限筛选
        if (filters.minYears != null && filters.minYears > 0) {
            Integer years = (Integer) fund.get("established_years");
            if (years == null || years < filters.minYears) {
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * 排序基金列表
     */
    private void sortFunds(List<Map<String, Object>> funds, String sortBy, String sortOrder) {
        final String sortField = sortBy != null ? sortBy : "return_period";
        final boolean desc = "desc".equalsIgnoreCase(sortOrder) || sortOrder == null;
        
        funds.sort((a, b) -> {
            Object valA = getSortValue(a, sortField);
            Object valB = getSortValue(b, sortField);
            
            int cmp;
            if (valA instanceof Double && valB instanceof Double) {
                cmp = Double.compare((Double) valA, (Double) valB);
            } else if (valA instanceof Integer && valB instanceof Integer) {
                cmp = Integer.compare((Integer) valA, (Integer) valB);
            } else if (valA == null && valB == null) {
                cmp = 0;
            } else if (valA == null) {
                cmp = -1;
            } else if (valB == null) {
                cmp = 1;
            } else {
                cmp = valA.toString().compareTo(valB.toString());
            }
            
            return desc ? -cmp : cmp;
        });
    }
    
    /**
     * 获取排序字段值
     */
    private Object getSortValue(Map<String, Object> fund, String field) {
        return switch (field) {
            case "return", "returnRate", "return_period" -> fund.get("return_period");
            case "sharpe", "sharpeRatio", "sharpe_ratio" -> fund.get("sharpe_ratio");
            case "drawdown", "maxDrawdown", "max_drawdown" -> fund.get("max_drawdown");
            case "size", "totalAssets", "total_assets" -> fund.get("total_assets");
            case "age", "establishedYears", "established_years" -> fund.get("established_years");
            case "volatility", "annualVolatility" -> fund.get("volatility");
            case "calmar", "calmarRatio", "calmar_ratio" -> fund.get("calmar_ratio");
            case "winRate", "win_rate" -> fund.get("win_rate");
            default -> fund.get(field);
        };
    }
    
    /**
     * 将指标Map扁平化
     */
    private Map<String, Object> flattenMetrics(Map<String, Double> metrics) {
        Map<String, Object> flat = new HashMap<>();
        flat.put("return_period", metrics.getOrDefault("totalReturn", 0.0) * 100); // 转为百分比
        flat.put("annual_return", metrics.getOrDefault("annualizedReturn", 0.0) * 100);
        flat.put("max_drawdown", metrics.getOrDefault("maxDrawdown", 0.0) * 100); // 负数表示回撤
        flat.put("volatility", metrics.getOrDefault("volatility", 0.0) * 100);
        flat.put("sharpe_ratio", metrics.getOrDefault("sharpeRatio", 0.0));
        flat.put("win_rate", metrics.getOrDefault("winRate", 0.0) * 100);
        flat.put("calmar_ratio", metrics.getOrDefault("calmarRatio", 0.0));
        return flat;
    }
    
    /**
     * 获取仪表盘数据
     */
    public Map<String, Object> getDashboardData() {
        Map<String, Object> dashboard = new HashMap<>();
        
        try {
            // 统计数据
            Map<String, Object> stats = new HashMap<>();
            stats.put("fund_count", jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM fund WHERE nav_record_count > 0", Long.class));
            stats.put("nav_records", jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM fund_nav", Long.class));
            dashboard.put("stats", stats);
            
            // 基金类型分布
            List<Map<String, Object>> typeDistribution = jdbcTemplate.query(
                "SELECT fund_type as type, COUNT(*) as count FROM fund " +
                "WHERE fund_type IS NOT NULL AND fund_type != '' AND nav_record_count > 0 " +
                "GROUP BY fund_type ORDER BY count DESC LIMIT 10",
                (rs, rowNum) -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("type", rs.getString("type"));
                    map.put("count", rs.getLong("count"));
                    return map;
                });
            dashboard.put("type_distribution", typeDistribution);
            
            // 热门基金排行（近1年收益率最高）
            List<Map<String, Object>> topFunds = getTopFundsByReturn("1y", 10);
            dashboard.put("top_funds", topFunds);
            
        } catch (Exception e) {
            System.err.println("获取仪表盘数据失败: " + e.getMessage());
        }
        
        return dashboard;
    }
    
    /**
     * 获取收益率最高的基金
     */
    public List<Map<String, Object>> getTopFundsByReturn(String period, int limit) {
        int tradingDays = PERIOD_DAYS.getOrDefault(period, 252);
        
        // 先获取有足够净值数据的基金
        String sql = """
            SELECT f.fund_code, f.fund_name, f.fund_type, f.total_assets,
                   f.nav_record_count, f.establishment_date
            FROM fund f
            WHERE f.nav_record_count >= ?
            ORDER BY f.nav_record_count DESC
            LIMIT 500
        """;
        
        try {
            List<Map<String, Object>> funds = jdbcTemplate.query(sql, 
                ps -> ps.setInt(1, tradingDays),
                (rs, rowNum) -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("fund_code", rs.getString("fund_code"));
                    map.put("fund_name", rs.getString("fund_name"));
                    map.put("fund_type", rs.getString("fund_type"));
                    map.put("total_assets", rs.getDouble("total_assets"));
                    return map;
                });
            
            if (funds.isEmpty()) {
                return Collections.emptyList();
            }
            
            // 计算指标
            List<String> codes = funds.stream()
                .map(f -> (String) f.get("fund_code"))
                .collect(Collectors.toList());
            
            Map<String, Map<String, Double>> metricsMap = metricsService.batchCalculateMetrics(codes, tradingDays);
            
            // 合并指标并排序
            for (Map<String, Object> fund : funds) {
                String code = (String) fund.get("fund_code");
                Map<String, Double> metrics = metricsMap.getOrDefault(code, getDefaultMetrics());
                fund.put("return_rate", metrics.getOrDefault("totalReturn", 0.0) * 100);
                fund.put("sharpe_ratio", metrics.getOrDefault("sharpeRatio", 0.0));
                fund.put("max_drawdown", metrics.getOrDefault("maxDrawdown", 0.0) * 100);
            }
            
            // 按收益率排序
            funds.sort((a, b) -> {
                Double ra = (Double) a.get("return_rate");
                Double rb = (Double) b.get("return_rate");
                return Double.compare(rb != null ? rb : 0, ra != null ? ra : 0);
            });
            
            return funds.stream().limit(limit).collect(Collectors.toList());
            
        } catch (Exception e) {
            System.err.println("获取热门基金失败: " + e.getMessage());
            return Collections.emptyList();
        }
    }
    
    /**
     * 默认指标
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
     * 筛选条件类
     */
    public static class ScreeningFilters {
        public String period = "1y";
        public String fundType;
        public Boolean indexFundOnly;
        public Double minReturn;
        public Double maxDrawdown;
        public Double minSharpe;
        public Double maxVolatility;
        public Double minWinRate;
        public Double minCalmar;
        public Double minSize;
        public Double maxSize;
        public Integer minYears;
        public String riskLevel;
        public String company;
        public String keyword;
        public String sortBy = "return_period";
        public String sortOrder = "desc";
        public int page = 1;
        public int pageSize = 20;
    }
}