package com.fundwise.service;

import com.fundwise.entity.Fund;
import com.fundwise.repository.FundRepository;
import org.springframework.stereotype.Service;
import java.util.*;
import java.util.stream.Collectors;
import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
public class RecommendationService {
    
    private final FundRepository fundRepository;
    private final org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;
    
    public RecommendationService(FundRepository fundRepository, 
                                org.springframework.jdbc.core.JdbcTemplate jdbcTemplate) {
        this.fundRepository = fundRepository;
        this.jdbcTemplate = jdbcTemplate;
    }
    
    /**
     * 根据用户偏好智能推荐基金组合（优化版）
     * @param initialCapital 初始资金
     * @param maxDrawdown 最大回撤限制
     * @param indexFundOnly 是否仅使用指数基金
     * @param userRiskLevel 用户指定的风险等级（可选）
     * @param investmentPeriod 投资期限（年）
     * @return 推荐基金列表及权重
     */
    public List<Map<String, Object>> recommendFunds(double initialCapital, Double maxDrawdown, 
                                                   boolean indexFundOnly, String userRiskLevel, int investmentPeriod) {
        // 1. 确定风险等级和投资风格
        String actualRiskLevel = determineRiskLevel(maxDrawdown, userRiskLevel);
        String investmentStyle = determineInvestmentStyle(actualRiskLevel, investmentPeriod, indexFundOnly);
        
        // 2. 筛选基金（基于多重条件）
        List<Fund> candidateFunds = filterFundsEnhanced(actualRiskLevel, indexFundOnly, investmentPeriod);
        
        if (candidateFunds.isEmpty()) {
            // 如果没找到匹配的基金，放宽条件
            candidateFunds = fundRepository.findAll().stream()
                    .filter(f -> f.getStatus() == null || "active".equals(f.getStatus()))
                    .filter(f -> f.getTotalAssets() != null && f.getTotalAssets() >= 5.0) // 至少5亿规模
                    .sorted((f1, f2) -> Double.compare(f2.getTotalAssets(), f1.getTotalAssets())) // 按规模降序
                    .limit(30)
                    .collect(Collectors.toList());
        }
        
        // 3. 挑选代表性基金（基于投资风格）
        List<Fund> selectedFunds = selectFundsByInvestmentStyle(candidateFunds, investmentStyle, indexFundOnly);
        
        // 4. 分配权重（基于风险收益优化）
        return assignWeightsOptimized(selectedFunds, actualRiskLevel, investmentPeriod);
    }
    
    /**
     * 确定风险等级
     */
    private String determineRiskLevel(Double maxDrawdown, String userRiskLevel) {
        if (userRiskLevel != null && !userRiskLevel.trim().isEmpty()) {
            return userRiskLevel;
        }
        
        if (maxDrawdown == null) {
            return "稳健"; // 默认
        }
        
        if (maxDrawdown <= 0.05) return "保守";
        if (maxDrawdown <= 0.15) return "稳健";
        return "积极";
    }
    
    /**
     * 确定投资风格
     */
    private String determineInvestmentStyle(String riskLevel, int investmentPeriod, boolean indexFundOnly) {
        if (indexFundOnly) {
            // 指数基金：根据风险等级确定风格
            switch (riskLevel) {
                case "保守": return "指数稳健";
                case "稳健": return "指数平衡"; 
                case "积极": return "指数成长";
                default: return "指数平衡";
            }
        }
        
        // 混合型基金的投资风格
        if (investmentPeriod <= 1) {
            // 短期投资：偏防御
            switch (riskLevel) {
                case "保守": return "防御保守";
                case "稳健": return "防御平衡";
                default: return "平衡";
            }
        } else if (investmentPeriod <= 3) {
            // 中期投资：平衡配置
            switch (riskLevel) {
                case "保守": return "平衡保守";
                case "稳健": return "平衡";
                default: return "平衡成长";
            }
        } else {
            // 长期投资：偏成长
            switch (riskLevel) {
                case "保守": return "成长保守";
                case "稳健": return "成长平衡";
                default: return "成长进取";
            }
        }
    }
    
    /**
     * 筛选符合条件的基金（增强版）
     */
    private List<Fund> filterFundsEnhanced(String riskLevel, boolean indexFundOnly, int investmentPeriod) {
        List<Fund> funds;
        
        if (indexFundOnly) {
            // 只选指数基金
            funds = fundRepository.findByIsIndexFundAndRiskLevel(true, riskLevel);
        } else {
            // 所有基金中按风险等级筛选
            funds = fundRepository.findByRiskLevel(riskLevel);
        }
        
        // 增强筛选条件
        return funds.stream()
                .filter(f -> f.getStatus() == null || "active".equals(f.getStatus()))
                .filter(f -> f.getFundType() != null && !f.getFundType().isEmpty())
                .filter(f -> f.getTotalAssets() != null && f.getTotalAssets() >= 2.0) // 至少2亿规模
                .filter(f -> isFundSuitableForPeriod(f, investmentPeriod)) // 投资期限适配性
                .filter(f -> hasNavData(f.getFundCode())) // 确保有净值数据
                .sorted(Comparator.comparing(Fund::getTotalAssets).reversed()) // 按规模降序
                .limit(100) // 取前100只
                .collect(Collectors.toList());
    }
    
    /**
     * 判断基金是否适合投资期限
     */
    /**
     * 检查基金是否有净值数据
     */
    private boolean hasNavData(String fundCode) {
        try {
            String sql = "SELECT COUNT(*) FROM fund_nav WHERE fund_code = ? LIMIT 1";
            Integer count = jdbcTemplate.queryForObject(sql, Integer.class, fundCode);
            return count != null && count > 0;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isFundSuitableForPeriod(Fund fund, int investmentPeriod) {
        if (fund.getEstablishmentDate() == null) {
            return true; // 没有成立日期信息，默认适合
        }
        
        long yearsEstablished = java.time.temporal.ChronoUnit.YEARS.between(
            fund.getEstablishmentDate(), java.time.LocalDate.now());
        
        // 成立时间需要足够长，适合投资期限
        if (investmentPeriod <= 1) {
            return yearsEstablished >= 2; // 短期投资需要至少成立2年
        } else if (investmentPeriod <= 3) {
            return yearsEstablished >= 3; // 中期投资需要至少成立3年
        } else {
            return yearsEstablished >= 5; // 长期投资需要至少成立5年
        }
    }
    
    /**
     * 根据投资风格挑选基金（智能版）
     */
    private List<Fund> selectFundsByInvestmentStyle(List<Fund> funds, String investmentStyle, boolean indexFundOnly) {
        if (funds.isEmpty()) {
            return funds;
        }
        
        List<Fund> selected = new ArrayList<>();
        Map<String, List<Fund>> fundsByType = funds.stream()
                .collect(Collectors.groupingBy(Fund::getFundType));
        
        if (indexFundOnly) {
            // 指数基金选择策略
            selectIndexFundsByStyle(fundsByType, investmentStyle, selected);
        } else {
            // 混合基金选择策略
            selectMixedFundsByStyle(fundsByType, investmentStyle, selected);
        }
        
        // 补充原则：确保组合包含不同类型的基金
        ensureDiversification(selected, funds, fundsByType);
        
        return selected.stream()
                .distinct() // 去重
                .limit(6) // 最多6只基金
                .collect(Collectors.toList());
    }
    
    /**
     * 根据投资风格选择指数基金
     */
    private void selectIndexFundsByStyle(Map<String, List<Fund>> fundsByType, String style, List<Fund> selected) {
        Map<String, Double[]> typeWeights = getIndexFundTypeWeightsByStyle(style);
        
        for (Map.Entry<String, Double[]> entry : typeWeights.entrySet()) {
            String targetType = entry.getKey();
            Double[] config = entry.getValue(); // [minWeight, maxWeight, minFunds]
            
            if (fundsByType.containsKey(targetType)) {
                List<Fund> candidates = fundsByType.get(targetType);
                int numToSelect = (int) Math.min(candidates.size(), Math.max(1, config[2]));
                
                // 根据基金规模和质量排序
                candidates.sort((f1, f2) -> {
                    int sizeCompare = Double.compare(f2.getTotalAssets(), f1.getTotalAssets());
                    if (sizeCompare != 0) return sizeCompare;
                    return f1.getFundName().compareTo(f2.getFundName());
                });
                
                selected.addAll(candidates.subList(0, Math.min(numToSelect, candidates.size())));
            }
        }
    }
    
    /**
     * 根据投资风格选择混合基金
     */
    private void selectMixedFundsByStyle(Map<String, List<Fund>> fundsByType, String style, List<Fund> selected) {
        Map<String, Double[]> typeWeights = getMixedFundTypeWeightsByStyle(style);
        
        // 第一阶段：按类型配置选取主要基金
        for (Map.Entry<String, Double[]> entry : typeWeights.entrySet()) {
            String targetType = entry.getKey();
            Double[] config = entry.getValue(); // [minWeight, maxWeight, minFunds]
            
            if (fundsByType.containsKey(targetType)) {
                List<Fund> candidates = fundsByType.get(targetType);
                int numToSelect = (int) Math.min(candidates.size(), Math.max(1, config[2]));
                
                // 基于基金规模、历史表现等综合评分
                candidates.sort(this::compareFundScore);
                
                selected.addAll(candidates.subList(0, Math.min(numToSelect, candidates.size())));
            }
        }
        
        // 第二阶段：补充优质基金
        if (selected.size() < 3) {
            List<Fund> allFunds = fundsByType.values().stream()
                    .flatMap(List::stream)
                    .filter(f -> !selected.contains(f))
                    .sorted(this::compareFundScore)
                    .limit(3 - selected.size())
                    .collect(Collectors.toList());
            selected.addAll(allFunds);
        }
    }
    
    /**
     * 基金综合评分比较
     */
    private int compareFundScore(Fund f1, Fund f2) {
        // 评分规则：规模（40%）+ 成立时间（30%）+ 基金公司声誉（30%）
        double score1 = calculateFundScore(f1);
        double score2 = calculateFundScore(f2);
        return Double.compare(score2, score1); // 降序
    }
    
    /**
     * 计算基金综合评分
     */
    private double calculateFundScore(Fund fund) {
        double score = 0.0;
        
        // 1. 基金规模评分（0-40分）
        if (fund.getTotalAssets() != null) {
            double sizeScore = Math.min(fund.getTotalAssets() / 50.0, 1.0) * 40;
            score += sizeScore;
        }
        
        // 2. 成立时间评分（0-30分）
        if (fund.getEstablishmentDate() != null) {
            long years = java.time.temporal.ChronoUnit.YEARS.between(
                fund.getEstablishmentDate(), java.time.LocalDate.now());
            double yearsScore = Math.min(years / 10.0, 1.0) * 30;
            score += yearsScore;
        }
        
        // 3. 基金公司评分（基础30分）
        score += 30;
        
        return score;
    }
    
    /**
     * 确保组合多样化
     */
    private void ensureDiversification(List<Fund> selected, List<Fund> allFunds, Map<String, List<Fund>> fundsByType) {
        Set<String> selectedTypes = selected.stream()
                .map(Fund::getFundType)
                .collect(Collectors.toSet());
        
        if (selectedTypes.size() < 2) {
            // 类型太少，补充不同类型的基金
            String[] priorityTypes = {"混合型-偏股", "债券型", "指数型-股票", "混合型-灵活"};
            for (String type : priorityTypes) {
                if (!selectedTypes.contains(type) && fundsByType.containsKey(type) && selected.size() < 6) {
                    fundsByType.get(type).stream()
                            .filter(f -> !selected.contains(f))
                            .sorted(this::compareFundScore)
                            .findFirst()
                            .ifPresent(selected::add);
                    selectedTypes.add(type);
                }
            }
        }
    }
    
    /**
     * 获取指数基金类型权重配置
     */
    private Map<String, Double[]> getIndexFundTypeWeightsByStyle(String style) {
        Map<String, Double[]> configs = new HashMap<>();
        
        switch (style) {
            case "指数稳健":
                configs.put("指数型-固收", new Double[]{0.4, 0.6, 2.0});
                configs.put("指数型-股票", new Double[]{0.2, 0.4, 1.0});
                break;
            case "指数平衡":
                configs.put("指数型-股票", new Double[]{0.4, 0.6, 2.0});
                configs.put("指数型-固收", new Double[]{0.3, 0.4, 1.0});
                configs.put("指数型-海外股票", new Double[]{0.1, 0.2, 1.0});
                break;
            case "指数成长":
                configs.put("指数型-股票", new Double[]{0.6, 0.8, 3.0});
                configs.put("指数型-海外股票", new Double[]{0.2, 0.3, 1.0});
                break;
            default: // 默认平衡配置
                configs.put("指数型-股票", new Double[]{0.4, 0.6, 2.0});
                configs.put("指数型-固收", new Double[]{0.3, 0.4, 1.0});
        }
        
        return configs;
    }
    
    /**
     * 获取混合基金类型权重配置
     */
    private Map<String, Double[]> getMixedFundTypeWeightsByStyle(String style) {
        Map<String, Double[]> configs = new HashMap<>();
        
        switch (style) {
            case "防御保守":
                configs.put("债券型-混合二级", new Double[]{0.5, 0.7, 3.0});
                configs.put("混合型-偏债", new Double[]{0.3, 0.4, 2.0});
                break;
            case "防御平衡":
                configs.put("混合型-偏债", new Double[]{0.4, 0.5, 2.0});
                configs.put("债券型-混合二级", new Double[]{0.3, 0.4, 2.0});
                configs.put("混合型-灵活", new Double[]{0.1, 0.2, 1.0});
                break;
            case "平衡":
                configs.put("混合型-偏股", new Double[]{0.4, 0.5, 2.0});
                configs.put("债券型-混合二级", new Double[]{0.3, 0.4, 2.0});
                configs.put("混合型-灵活", new Double[]{0.1, 0.2, 1.0});
                break;
            case "平衡成长":
                configs.put("混合型-偏股", new Double[]{0.5, 0.6, 3.0});
                configs.put("混合型-灵活", new Double[]{0.2, 0.3, 1.0});
                configs.put("债券型-混合二级", new Double[]{0.1, 0.2, 1.0});
                break;
            case "成长进取":
                configs.put("混合型-偏股", new Double[]{0.6, 0.8, 4.0});
                configs.put("指数型-股票", new Double[]{0.2, 0.3, 1.0});
                break;
            default: // 默认平衡配置
                configs.put("混合型-偏股", new Double[]{0.4, 0.5, 2.0});
                configs.put("债券型-混合二级", new Double[]{0.3, 0.4, 2.0});
        }
        
        return configs;
    }
    
    /**
     * 分配权重（优化版）
     */
    private List<Map<String, Object>> assignWeightsOptimized(List<Fund> funds, String riskLevel, int investmentPeriod) {
        List<Map<String, Object>> result = new ArrayList<>();
        
        if (funds.isEmpty()) {
            return result;
        }
        
        // 根据基金类型和风险等级计算基础权重
        Map<String, Double> baseWeights = calculateBaseWeights(funds, riskLevel);
        
        // 应用投资期限调整
        Map<String, Double> adjustedWeights = applyPeriodAdjustment(baseWeights, funds, investmentPeriod);
        
        // 应用基金质量调整
        Map<String, Double> finalWeights = applyQualityAdjustment(adjustedWeights, funds);
        
        // 构建结果
        double totalWeight = 0;
        for (int i = 0; i < funds.size(); i++) {
            Fund fund = funds.get(i);
            String fundCode = fund.getFundCode();
            
            double weight = finalWeights.getOrDefault(fundCode, 
                i == funds.size() - 1 && totalWeight < 1.0 ? 1.0 - totalWeight : 1.0 / funds.size());
            
            // 边界控制
            weight = Math.min(weight, 0.35); // 单只基金不超过35%
            weight = Math.max(weight, 0.08); // 单只基金不低于8%
            
            // 构建基金信息
            Map<String, Object> fundMap = new HashMap<>();
            fundMap.put("code", fundCode);
            fundMap.put("name", fund.getFundName());
            fundMap.put("type", fund.getFundType());
            fundMap.put("weight", roundWeight(weight));
            fundMap.put("company", fund.getFundCompany());
            fundMap.put("isIndexFund", fund.getIsIndexFund());
            fundMap.put("riskLevel", fund.getRiskLevel());
            fundMap.put("totalAssets", fund.getTotalAssets());
            if (fund.getEstablishmentDate() != null) {
                fundMap.put("establishedYears", java.time.temporal.ChronoUnit.YEARS.between(
                    fund.getEstablishmentDate(), java.time.LocalDate.now()));
            }
            
            result.add(fundMap);
            totalWeight += weight;
        }
        
        // 归一化权重
        normalizeWeights(result);
        
        // 对权重进行最终平滑处理
        smoothWeights(result);
        
        return result;
    }
    
    /**
     * 计算基础权重
     */
    private Map<String, Double> calculateBaseWeights(List<Fund> funds, String riskLevel) {
        Map<String, Double> weights = new HashMap<>();
        Map<String, Integer> typeCount = new HashMap<>();
        
        // 统计每种类型的基金数量
        for (Fund fund : funds) {
            String type = fund.getFundType();
            typeCount.put(type, typeCount.getOrDefault(type, 0) + 1);
        }
        
        // 根据风险等级分配基础权重
        double totalBaseWeight = 0;
        for (Fund fund : funds) {
            String type = fund.getFundType();
            String code = fund.getFundCode();
            
            // 根据基金类型确定基础权重
            double baseWeight = getBaseWeightByTypeAndRisk(type, riskLevel);
            
            // 根据同类型基金数量调整
            int count = typeCount.get(type);
            double adjustedWeight = baseWeight / Math.sqrt(count); // 类型内基金越多，单只权重越低
            
            weights.put(code, adjustedWeight);
            totalBaseWeight += adjustedWeight;
        }
        
        return weights;
    }
    
    /**
     * 根据基金类型和风险等级获取基础权重
     */
    private double getBaseWeightByTypeAndRisk(String type, String riskLevel) {
        // 基本权重配置
        Map<String, Map<String, Double>> riskWeightConfig = new HashMap<>();
        
        // 保守型权重配置
        Map<String, Double> conservativeWeights = new HashMap<>();
        conservativeWeights.put("债券型", 0.35);
        conservativeWeights.put("混合型-偏债", 0.30);
        conservativeWeights.put("混合型-灵活", 0.20);
        conservativeWeights.put("混合型-偏股", 0.15);
        conservativeWeights.put("指数型-股票", 0.10);
        
        // 稳健型权重配置
        Map<String, Double> moderateWeights = new HashMap<>();
        moderateWeights.put("混合型-偏股", 0.35);
        moderateWeights.put("债券型", 0.25);
        moderateWeights.put("指数型-股票", 0.20);
        moderateWeights.put("混合型-灵活", 0.20);
        
        // 积极型权重配置
        Map<String, Double> aggressiveWeights = new HashMap<>();
        aggressiveWeights.put("混合型-偏股", 0.40);
        aggressiveWeights.put("指数型-股票", 0.30);
        aggressiveWeights.put("股票型", 0.20);
        aggressiveWeights.put("混合型-灵活", 0.10);
        
        riskWeightConfig.put("保守", conservativeWeights);
        riskWeightConfig.put("稳健", moderateWeights);
        riskWeightConfig.put("积极", aggressiveWeights);
        
        Map<String, Double> weights = riskWeightConfig.getOrDefault(riskLevel, moderateWeights);
        
        // 查找最匹配的类型
        for (String key : weights.keySet()) {
            if (type != null && type.contains(key)) {
                return weights.get(key);
            }
        }
        
        // 默认权重
        return 0.20;
    }
    
    /**
     * 应用投资期限调整
     */
    private Map<String, Double> applyPeriodAdjustment(Map<String, Double> weights, List<Fund> funds, int period) {
        Map<String, Double> adjusted = new HashMap<>(weights);
        
        // 投资期限越长，对成立时间长的基金给予更高权重
        for (Fund fund : funds) {
            String code = fund.getFundCode();
            double baseWeight = weights.getOrDefault(code, 0.0);
            
            if (fund.getEstablishmentDate() != null) {
                long years = java.time.temporal.ChronoUnit.YEARS.between(
                    fund.getEstablishmentDate(), java.time.LocalDate.now());
                
                // 成立时间因子：成立越久，调整越大
                double yearFactor = Math.min(years / 10.0, 2.0);
                
                // 投资期限因子：期限越长，对成立时间的要求越高
                double periodFactor = period >= 3 ? 1.2 : (period >= 1 ? 1.0 : 0.8);
                
                adjusted.put(code, baseWeight * yearFactor * periodFactor);
            }
        }
        
        return adjusted;
    }
    
    /**
     * 应用基金质量调整
     */
    private Map<String, Double> applyQualityAdjustment(Map<String, Double> weights, List<Fund> funds) {
        Map<String, Double> adjusted = new HashMap<>();
        
        // 计算质量分数并归一化
        Map<String, Double> qualityScores = new HashMap<>();
        double maxScore = 0.0;
        
        for (Fund fund : funds) {
            String code = fund.getFundCode();
            double score = calculateFundScore(fund);
            qualityScores.put(code, score);
            maxScore = Math.max(maxScore, score);
        }
        
        // 应用质量调整
        for (Fund fund : funds) {
            String code = fund.getFundCode();
            double baseWeight = weights.getOrDefault(code, 0.0);
            double qualityScore = qualityScores.get(code);
            double qualityRatio = maxScore > 0 ? qualityScore / maxScore : 1.0;
            
            // 质量越高的基金获得更高权重
            adjusted.put(code, baseWeight * (0.8 + 0.4 * qualityRatio));
        }
        
        return adjusted;
    }
    
    /**
     * 对权重进行平滑处理
     */
    private void smoothWeights(List<Map<String, Object>> funds) {
        // 确保权重分布平滑，避免极端值
        List<Double> weights = funds.stream()
                .map(f -> (Double) f.get("weight"))
                .collect(Collectors.toList());
        
        // 计算均值和标准差
        double mean = weights.stream().mapToDouble(Double::doubleValue).average().orElse(0.2);
        double std = Math.sqrt(
            weights.stream()
                .mapToDouble(w -> Math.pow(w - mean, 2))
                .average()
                .orElse(0.01)
        );
        
        // 对偏离均值过多的权重进行调整
        for (Map<String, Object> fund : funds) {
            double weight = (Double) fund.get("weight");
            if (Math.abs(weight - mean) > 1.5 * std) {
                double smoothed = mean + (weight - mean) * 0.7; // 向均值收缩
                fund.put("weight", roundWeight(smoothed));
            }
        }
        
        // 再次归一化
        normalizeWeights(funds);
    }
    
    /**
     * 四舍五入保留4位小数
     */
    private double roundWeight(double weight) {
        return BigDecimal.valueOf(weight)
                .setScale(4, RoundingMode.HALF_UP)
                .doubleValue();
    }
    
    /**
     * 归一化权重，确保总和为1
     */
    private void normalizeWeights(List<Map<String, Object>> funds) {
        double totalWeight = funds.stream()
                .mapToDouble(f -> (Double) f.get("weight"))
                .sum();
        
        if (totalWeight == 0) {
            // 平均分配
            double avgWeight = 1.0 / funds.size();
            funds.forEach(f -> f.put("weight", avgWeight));
        } else if (Math.abs(totalWeight - 1.0) > 0.001) {
            // 归一化
            final double factor = 1.0 / totalWeight;
            funds.forEach(f -> f.put("weight", (Double) f.get("weight") * factor));
        }
    }
    
    /**
     * 计算回测数据（模拟，后续应接入真实回测引擎）
     */
    public Map<String, Object> calculateBacktestResult(List<Map<String, Object>> portfolio, 
                                                     double initialCapital, 
                                                     int investmentPeriod) {
        Map<String, Object> backtest = new HashMap<>();
        
        // 这里使用模拟数据，后续应接入真实回测引擎
        double simulatedReturn = 0.35; // 模拟35%收益
        double annualizedReturn = 0.085;
        
        backtest.put("startDate", "2021-01-01");
        backtest.put("endDate", "2024-01-01");
        backtest.put("initialCapital", initialCapital);
        backtest.put("finalValue", initialCapital * (1 + simulatedReturn));
        backtest.put("annualizedReturn", annualizedReturn);
        backtest.put("maxDrawdown", 0.135);
        backtest.put("sharpeRatio", 1.2);
        backtest.put("benchmarkReturn", 0.045);
        backtest.put("portfolioCount", portfolio.size());
        
        return backtest;
    }
    
    /**
     * 生成智能解读
     */
    public List<String> generateInsights(List<Map<String, Object>> portfolio, 
                                        Map<String, Object> backtestResult,
                                        Double userMaxDrawdown,
                                        boolean indexFundOnly) {
        List<String> insights = new ArrayList<>();
        
        double annualReturn = (Double) backtestResult.get("annualizedReturn");
        double maxDrawdown = (Double) backtestResult.get("maxDrawdown");
        int portfolioSize = portfolio.size();
        
        // 收益解读
        insights.add(String.format("该组合模拟回测显示，年化收益约为%.1f%%", annualReturn * 100));
        
        // 风险控制解读
        if (userMaxDrawdown != null) {
            if (maxDrawdown <= userMaxDrawdown) {
                insights.add(String.format("历史最大回撤控制在您设定的%.1f%%以内", userMaxDrawdown * 100));
            } else {
                insights.add(String.format("历史最大回撤为%.1f%%，略高于您的设定", maxDrawdown * 100));
            }
        } else {
            insights.add(String.format("历史最大回撤为%.1f%%，风险控制良好", maxDrawdown * 100));
        }
        
        // 组合解读
        insights.add(String.format("组合包含%d只基金，分散了单一基金风险", portfolioSize));
        
        if (indexFundOnly) {
            insights.add("指数基金组合管理费率较低，长期成本优势明显");
        }
        
        // 夏普比率解读
        double sharpeRatio = (Double) backtestResult.get("sharpeRatio");
        if (sharpeRatio > 1.5) {
            insights.add("夏普比率优秀，收益风险性价比很高");
        } else if (sharpeRatio > 0.8) {
            insights.add("夏普比率良好，收益风险性价比较好");
        } else {
            insights.add("夏普比率一般，建议进一步优化组合");
        }
        
        return insights;
    }
}