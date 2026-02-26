// FundWise 前端辅助函数

/**
 * 获取投资建议
 */
function getInvestmentTips(funds, backtest) {
    const tips = [];
    
    // 投资规模建议
    const avgSize = funds.reduce((sum, f) => sum + (f.totalAssets || 0), 0) / funds.length;
    if (avgSize > 200) {
        tips.push('组合包含大型基金，流动性好，适合大额投资');
    } else if (avgSize > 50) {
        tips.push('基金规模适中，平衡了流动性和管理灵活性');
    }
    
    // 投资期限建议
    const avgAge = funds.reduce((sum, f) => sum + (f.establishedYears || 0), 0) / funds.length;
    if (avgAge > 10) {
        tips.push('老牌基金组合，历史业绩参考价值高');
    }
    
    // 风险分散建议
    const fundTypes = new Set(funds.map(f => f.type));
    if (fundTypes.size >= 3) {
        tips.push('多类型基金组合，分散投资风险');
    } else if (fundTypes.size === 1) {
        tips.push('单类型组合，建议考虑增加其他类型基金');
    }
    
    // 回测结果建议
    if (backtest) {
        if (backtest.annualizedReturn > 0.08) {
            tips.push('历史模拟收益较高，但请注意历史不代表未来');
        }
        if (backtest.maxDrawdown > 0.15) {
            tips.push('历史最大回撤较大，请确保风险承受能力');
        }
        if (backtest.sharpeRatio > 1.2) {
            tips.push('夏普比率优秀，收益风险性价比较高');
        }
    }
    
    // 指数基金建议
    const indexFundCount = funds.filter(f => f.isIndexFund).length;
    if (indexFundCount > 0) {
        tips.push('指数基金费率较低，长期投资优势明显');
    }
    
    // 默认建议
    if (tips.length === 0) {
        tips.push('建议定期关注基金表现，适时调整仓位');
        tips.push('长期持有比短期频繁交易更可能获得稳定回报');
    }
    
    return tips.map(tip => `<li>${tip}</li>`).join('');
}

/**
 * 添加分析样式
 */
function addAnalysisStyles() {
    if (!document.querySelector('#analysis-styles')) {
        const style = document.createElement('style');
        style.id = 'analysis-styles';
        style.textContent = `
            /* 洞察分析样式 */
            .insight-analysis {
                margin-top: 1.5rem;
                padding: 1rem;
                background: #f0f9ff;
                border-radius: var(--radius);
                border-left: 4px solid var(--info-color);
            }
            
            .insight-analysis h5 {
                color: var(--info-color);
                margin-bottom: 0.75rem;
                font-size: 1rem;
            }
            
            .analysis-list {
                list-style: none;
                padding: 0;
                margin: 0;
            }
            
            .analysis-list li {
                margin-bottom: 0.5rem;
                padding: 0.5rem 0;
                display: flex;
                align-items: center;
                gap: 0.5rem;
            }
            
            .analysis-list li i {
                color: var(--info-color);
                min-width: 20px;
            }
            
            .risk-badge {
                display: inline-block;
                padding: 0.2rem 0.5rem;
                border-radius: 12px;
                font-size: 0.8rem;
                font-weight: 600;
                margin: 0 0.2rem;
            }
            
            .risk-badge.success {
                background: var(--success-color);
                color: white;
            }
            
            .risk-badge.primary {
                background: var(--primary-color);
                color: white;
            }
            
            .risk-badge.warning {
                background: var(--warning-color);
                color: white;
            }
            
            .risk-badge.secondary {
                background: var(--secondary-color);
                color: white;
            }
            
            /* 投资建议样式 */
            .investment-tips {
                margin-top: 1.5rem;
                padding: 1rem;
                background: #f8f9fa;
                border-radius: var(--radius);
                border: 1px dashed var(--primary-color);
            }
            
            .investment-tips h5 {
                color: var(--primary-color);
                margin-bottom: 0.75rem;
                font-size: 1rem;
            }
            
            .tips-list {
                list-style: none;
                padding: 0;
                margin: 0;
            }
            
            .tips-list li {
                margin-bottom: 0.5rem;
                padding-left: 1.5rem;
                position: relative;
            }
            
            .tips-list li:before {
                content: '💡';
                position: absolute;
                left: 0;
                top: 0;
            }
            
            /* 指标卡片增强 */
            .metric-card {
                transition: all 0.3s ease;
                position: relative;
                overflow: hidden;
            }
            
            .metric-card:hover {
                transform: translateY(-5px);
                box-shadow: var(--shadow-lg);
            }
            
            .metric-value {
                position: relative;
                z-index: 2;
            }
            
            .metric-trend {
                position: absolute;
                top: 0;
                right: 0;
                padding: 0.5rem;
                font-size: 0.8rem;
                border-radius: 0 var(--radius) 0 var(--radius);
            }
            
            .metric-trend.positive {
                background: rgba(76, 201, 240, 0.1);
                color: var(--success-color);
            }
            
            .metric-trend.negative {
                background: rgba(247, 37, 133, 0.1);
                color: var(--danger-color);
            }
        `;
        document.head.appendChild(style);
    }
}

/**
 * 获取基金类型统计
 */
function getFundTypeStats(funds) {
    const stats = {};
    funds.forEach(fund => {
        const type = fund.type || '未知';
        stats[type] = (stats[type] || 0) + 1;
    });
    return stats;
}

/**
 * 格式化基金规模
 */
function formatFundSize(size) {
    if (size === null || size === undefined) return '--';
    if (size >= 1000) return (size / 1000).toFixed(1) + '千亿';
    if (size >= 100) return size.toFixed(1) + '亿';
    if (size >= 10) return size.toFixed(1) + '亿';
    return size.toFixed(1) + '亿';
}

/**
 * 获取基金规模等级
 */
function getSizeLevel(size) {
    if (size === null || size === undefined) return 'unknown';
    if (size >= 500) return 'giant';       // 巨无霸
    if (size >= 200) return 'large';      // 大型
    if (size >= 50) return 'medium';      // 中型
    if (size >= 10) return 'small';       // 小型
    return 'micro';                       // 微型
}

/**
 * 获取成立时间等级
 */
function getAgeLevel(years) {
    if (years === null || years === undefined) return 'unknown';
    if (years >= 15) return 'veteran';    // 老牌
    if (years >= 10) return 'established'; // 成熟
    if (years >= 5) return 'experienced'; // 有经验
    if (years >= 3) return 'young';       // 年轻
    return 'new';                         // 新基金
}