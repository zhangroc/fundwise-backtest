// 基智回测 - 前端主应用逻辑

// API 基础URL - 自动检测当前主机
const API_BASE_URL = '';

// 全局状态
const AppState = {
    isLoading: false,
    currentBacktestResult: null,
    chartInstance: null
};

// DOM 元素
const elements = {
    // 配置面板
    configPanel: document.getElementById('configPanel'),
    initialCapital: document.getElementById('initialCapital'),
    maxDrawdown: document.getElementById('maxDrawdown'),
    investmentPeriod: document.getElementById('investmentPeriod'),
    indexFundOnly: document.getElementById('indexFundOnly'),
    enableRebalancing: document.getElementById('enableRebalancing'),
    toggleAdvanced: document.getElementById('toggleAdvanced'),
    advancedOptions: document.getElementById('advancedOptions'),
    runBacktestBtn: document.getElementById('runBacktestBtn'),
    
    // 结果区域
    resultSection: document.getElementById('resultSection'),
    resultTitle: document.getElementById('resultTitle'),
    resultSubtitle: document.getElementById('resultSubtitle'),
    welcomeGuide: document.getElementById('welcomeGuide'),
    
    // 图表
    navChart: document.getElementById('navChart'),
    
    // 指标
    metricAnnualReturn: document.getElementById('metricAnnualReturn'),
    metricAnnualReturnBar: document.getElementById('metricAnnualReturnBar'),
    metricMaxDrawdown: document.getElementById('metricMaxDrawdown'),
    metricMaxDrawdownBar: document.getElementById('metricMaxDrawdownBar'),
    metricSharpeRatio: document.getElementById('metricSharpeRatio'),
    metricSharpeRatioBar: document.getElementById('metricSharpeRatioBar'),
    
    // 洞察面板
    insightPanel: document.getElementById('insightPanel'),
    portfolioComposition: document.getElementById('portfolioComposition'),
    insightBubble: document.getElementById('insightBubble'),
    
    // 操作按钮
    savePortfolioBtn: document.getElementById('savePortfolioBtn'),
    compareBtn: document.getElementById('compareBtn'),
    resetBtn: document.getElementById('resetBtn'),
    
    // 模态框
    aboutModal: document.getElementById('aboutModal'),
    aboutLink: document.getElementById('aboutLink'),
    disclaimerLink: document.getElementById('disclaimerLink'),
    apiLink: document.getElementById('apiLink')
};

// 工具函数
const utils = {
    formatPercent: (value) => {
        if (value === null || value === undefined) return '--';
        return (value * 100).toFixed(2) + '%';
    },
    
    formatNumber: (value) => {
        if (value === null || value === undefined) return '--';
        return value.toLocaleString('zh-CN', {
            minimumFractionDigits: 2,
            maximumFractionDigits: 2
        });
    },
    
    showLoading: (show) => {
        AppState.isLoading = show;
        elements.runBacktestBtn.disabled = show;
        elements.runBacktestBtn.innerHTML = show 
            ? '<i class="fas fa-spinner fa-spin"></i> 分析中...' 
            : '<i class="fas fa-rocket"></i> 开始智能回测';
    },
    
    showError: (message) => {
        alert(`错误: ${message}`);
    }
};

// 图表函数
const chartUtils = {
    createNavChart: (labels, portfolioData, benchmarkData) => {
        if (AppState.chartInstance) {
            AppState.chartInstance.destroy();
        }
        
        const ctx = elements.navChart.getContext('2d');
        AppState.chartInstance = new Chart(ctx, {
            type: 'line',
            data: {
                labels: labels,
                datasets: [
                    {
                        label: '推荐组合',
                        data: portfolioData,
                        borderColor: '#4361ee',
                        backgroundColor: 'rgba(67, 97, 238, 0.1)',
                        borderWidth: 2,
                        fill: true,
                        tension: 0.1
                    },
                    {
                        label: '沪深300指数',
                        data: benchmarkData,
                        borderColor: '#7209b7',
                        borderWidth: 1,
                        borderDash: [5, 5],
                        fill: false,
                        tension: 0.1
                    }
                ]
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                plugins: {
                    legend: {
                        position: 'top',
                    },
                    tooltip: {
                        mode: 'index',
                        intersect: false,
                        callbacks: {
                            label: (context) => {
                                let label = context.dataset.label || '';
                                if (label) {
                                    label += ': ';
                                }
                                label += utils.formatNumber(context.parsed.y);
                                return label;
                            }
                        }
                    }
                },
                scales: {
                    x: {
                        title: {
                            display: true,
                            text: '日期'
                        }
                    },
                    y: {
                        title: {
                            display: true,
                            text: '净值'
                        },
                        ticks: {
                            callback: (value) => utils.formatNumber(value)
                        }
                    }
                }
            }
        });
    },
    
    updateMetrics: (metrics) => {
        // 年化收益
        elements.metricAnnualReturn.textContent = utils.formatPercent(metrics.annualizedReturn);
        elements.metricAnnualReturnBar.style.width = `${Math.min(100, (metrics.annualizedReturn || 0) * 500)}%`;
        
        // 最大回撤
        elements.metricMaxDrawdown.textContent = utils.formatPercent(metrics.maxDrawdown);
        elements.metricMaxDrawdownBar.style.width = `${Math.min(100, (metrics.maxDrawdown || 0) * 500)}%`;
        
        // 夏普比率
        elements.metricSharpeRatio.textContent = metrics.sharpeRatio?.toFixed(2) || '--';
        elements.metricSharpeRatioBar.style.width = `${Math.min(100, (metrics.sharpeRatio || 0) * 50)}%`;
    }
};

// API 函数
const api = {
    recommendPortfolio: async (params) => {
        // TODO: 替换为真实的API调用
        console.log('调用推荐API:', params);
        
        // 模拟API响应延迟
        await new Promise(resolve => setTimeout(resolve, 1500));
        
        // 模拟响应数据
        return {
            success: true,
            data: {
                portfolio: {
                    id: 1,
                    name: '智能稳健组合',
                    funds: [
                        { id: 1, code: '000001', name: '华夏成长混合', type: '混合型', weight: 0.30 },
                        { id: 2, code: '000002', name: '易方达沪深300ETF联接', type: '指数型', weight: 0.40 },
                        { id: 3, code: '000003', name: '富国天惠债券', type: '债券型', weight: 0.30 }
                    ],
                    riskLevel: '稳健'
                },
                backtest: {
                    startDate: '2021-01-01',
                    endDate: '2024-01-01',
                    initialCapital: parseInt(params.initialCapital),
                    finalValue: parseInt(params.initialCapital) * 1.35,
                    annualizedReturn: 0.085,
                    maxDrawdown: 0.092,
                    sharpeRatio: 1.2,
                    benchmarkReturn: 0.045,
                    dailyData: [
                        { date: '2021-01-04', portfolioValue: 10000, benchmarkValue: 10000 },
                        // ... 更多模拟数据
                    ]
                },
                insights: [
                    "该组合在过去3年模拟中，有90%的时间将亏损控制在您设定的10%以内。",
                    "在2022年的市场下跌中，该组合表现出了较强的抗跌性，最大回撤为9.2%。",
                    "组合的夏普比率为1.2，意味着其收益风险性价比较好。"
                ]
            }
        };
    },
    
    // TODO: 添加更多API函数
};

// UI 更新函数
const ui = {
    showResults: () => {
        elements.welcomeGuide.style.display = 'none';
        elements.resultSection.style.display = 'block';
        elements.insightPanel.style.display = 'block';
        
        // 添加淡入效果
        elements.resultSection.classList.add('fade-in');
        elements.insightPanel.classList.add('fade-in');
    },
    
    updatePortfolioComposition: (funds) => {
        if (!funds || funds.length === 0) {
            elements.portfolioComposition.innerHTML = '<p class="loading">暂无组合数据</p>';
            return;
        }
        
        // 计算组合总体数据
        const totalWeight = funds.reduce((sum, fund) => sum + fund.weight, 0);
        const avgSize = funds.reduce((sum, fund) => sum + (fund.totalAssets || 0), 0) / funds.length;
        const avgYears = funds.reduce((sum, fund) => sum + (fund.establishedYears || 0), 0) / funds.length;
        
        // 添加组合概览
        const overviewHtml = `
            <div class="portfolio-overview">
                <h4><i class="fas fa-chart-pie"></i> 组合概览</h4>
                <div class="overview-stats">
                    <div class="overview-stat">
                        <span class="stat-label">基金数量</span>
                        <span class="stat-value">${funds.length}只</span>
                    </div>
                    <div class="overview-stat">
                        <span class="stat-label">平均规模</span>
                        <span class="stat-value">${avgSize.toFixed(1)}亿</span>
                    </div>
                    <div class="overview-stat">
                        <span class="stat-label">平均成立</span>
                        <span class="stat-value">${avgYears.toFixed(1)}年</span>
                    </div>
                </div>
            </div>
        `;
        
        const fundsHtml = funds.map(fund => {
            // 计算基金质量评分
            const qualityScore = calculateFundQualityScore(fund);
            
            // 根据风险等级选择颜色
            const riskColor = getRiskColor(fund.riskLevel);
            
            // 根据基金类型选择图标
            const typeIcon = getFundTypeIcon(fund.type, fund.isIndexFund);
            
            return `
            <div class="fund-card" data-risk="${fund.riskLevel}">
                <div class="fund-header">
                    <div class="fund-basic">
                        <div class="fund-code-name">
                            <span class="fund-code">${fund.code}</span>
                            <span class="fund-name">${fund.name}</span>
                            ${fund.isIndexFund ? '<span class="fund-index-badge"><i class="fas fa-chart-line"></i> 指数</span>' : ''}
                        </div>
                        <div class="fund-weight">${(fund.weight * 100).toFixed(1)}%</div>
                    </div>
                </div>
                
                <div class="fund-details">
                    <div class="fund-meta">
                        <span class="fund-size" title="基金规模">
                            <i class="fas fa-chart-bar"></i> ${fund.totalAssets ? fund.totalAssets.toFixed(1) + '亿' : '--'}
                        </span>
                        <span class="fund-age" title="成立年限">
                            <i class="fas fa-calendar-alt"></i> ${fund.establishedYears || '--'}年
                        </span>
                        <span class="fund-company" title="基金公司">
                            <i class="fas fa-building"></i> ${fund.company || '--'}
                        </span>
                    </div>
                    
                    <div class="fund-categories">
                        <span class="fund-type ${riskColor}" style="border-color: var(--${riskColor}-color);">
                            ${typeIcon} ${fund.type || '--'}
                        </span>
                        <span class="fund-risk" style="color: var(--${riskColor}-color);">
                            <i class="fas fa-shield-alt"></i> ${fund.riskLevel || '未知'}
                        </span>
                    </div>
                    
                    <div class="fund-quality">
                        <div class="quality-bar">
                            <div class="quality-label">基金质量:</div>
                            <div class="quality-stars">
                                ${'★'.repeat(Math.floor(qualityScore / 20))}${'☆'.repeat(5 - Math.floor(qualityScore / 20))}
                            </div>
                            <div class="quality-score">${qualityScore.toFixed(0)}分</div>
                        </div>
                    </div>
                </div>
            </div>
        `}).join('');
        
        elements.portfolioComposition.innerHTML = overviewHtml + fundsHtml;
        
        // 添加样式
        addEnhancedStyles();
    },
    
    updateInsights: (insights, portfolio, backtest) => {
        if (!insights || insights.length === 0) {
            elements.insightBubble.innerHTML = '<p>暂无解读信息</p>';
            return;
        }
        
        // 结构化解读
        let structuredInsights = insights.map(insight => `<p>${insight}</p>`).join('');
        
        // 如果有投资组合数据，添加更多分析
        if (portfolio && portfolio.funds && portfolio.funds.length > 0) {
            const funds = portfolio.funds;
            
            // 计算组合特征
            const fundTypes = new Set(funds.map(f => f.type));
            const avgSize = funds.reduce((sum, f) => sum + (f.totalAssets || 0), 0) / funds.length;
            const riskLevels = new Set(funds.map(f => f.riskLevel || '未知'));
            const indexFundCount = funds.filter(f => f.isIndexFund).length;
            
            structuredInsights += `
                <div class="insight-analysis">
                    <h5><i class="fas fa-search"></i> 组合深度分析</h5>
                    <ul class="analysis-list">
                        <li><i class="fas fa-layer-group"></i> 组合包含 <strong>${funds.length}</strong> 只基金，<strong>${fundTypes.size}</strong> 种类型</li>
                        ${avgSize > 0 ? `<li><i class="fas fa-weight"></i> 平均基金规模 <strong>${avgSize.toFixed(1)}亿</strong></li>` : ''}
                        ${riskLevels.size > 0 ? `<li><i class="fas fa-shield-alt"></i> 风险等级: ${Array.from(riskLevels).map(r => `<span class="risk-badge ${getRiskColor(r)}">${r}</span>`).join('')}</li>` : ''}
                        ${indexFundCount > 0 ? `<li><i class="fas fa-chart-line"></i> 包含 <strong>${indexFundCount}</strong> 只指数基金</li>` : ''}
                    </ul>
                </div>
                
                <div class="investment-tips">
                    <h5><i class="fas fa-lightbulb"></i> 投资建议</h5>
                    <ul class="tips-list">
                        ${getInvestmentTips(funds, backtest)}
                    </ul>
                </div>
            `;
        }
        
        elements.insightBubble.innerHTML = structuredInsights;
        
        // 添加分析样式
        addAnalysisStyles();
    },
    
    updateResultTitle: (riskLevel, indexFundOnly) => {
        let title = '您的智能投资组合分析';
        let subtitle = '基于您的风险偏好和投资目标';
        
        if (riskLevel) {
            subtitle = `基于您的${riskLevel}风险偏好`;
        }
        
        if (indexFundOnly) {
            title = '您的指数基金组合分析';
            subtitle = '基于指数基金的透明、低成本配置';
        }
        
        elements.resultTitle.textContent = title;
        elements.resultSubtitle.textContent = subtitle;
    }
};

// 事件处理
const eventHandlers = {
    onRunBacktest: async () => {
        try {
            // 收集参数
            const params = {
                initialCapital: parseFloat(elements.initialCapital.value),
                maxDrawdown: elements.maxDrawdown.value === 'unknown' ? null : parseFloat(elements.maxDrawdown.value),
                investmentPeriod: parseInt(elements.investmentPeriod.value),
                indexFundOnly: elements.indexFundOnly.checked,
                enableRebalancing: elements.enableRebalancing.checked
            };
            
            // 验证输入
            if (!params.initialCapital || params.initialCapital < 1000) {
                utils.showError('请输入有效的投资金额（至少1000元）');
                return;
            }
            
            // 显示加载状态
            utils.showLoading(true);
            
            // 调用API
            const response = await api.recommendPortfolio(params);
            
            if (!response.success) {
                throw new Error('推荐失败');
            }
            
            // 更新全局状态
            AppState.currentBacktestResult = response.data;
            
            // 更新UI
            ui.showResults();
            ui.updateResultTitle(response.data.portfolio.riskLevel, params.indexFundOnly);
            ui.updatePortfolioComposition(response.data.portfolio.funds);
            ui.updateInsights(response.data.insights, response.data.portfolio, response.data.backtest);
            
            // 更新图表（使用模拟数据）
            const labels = ['2021-01', '2021-07', '2022-01', '2022-07', '2023-01', '2023-07', '2024-01'];
            const portfolioData = [10000, 10500, 11000, 9800, 11500, 12500, 13500];
            const benchmarkData = [10000, 10200, 10800, 9200, 10500, 11200, 11800];
            chartUtils.createNavChart(labels, portfolioData, benchmarkData);
            
            // 更新指标
            chartUtils.updateMetrics(response.data.backtest);
            
        } catch (error) {
            console.error('回测失败:', error);
            utils.showError(error.message || '回测分析失败，请重试');
        } finally {
            utils.showLoading(false);
        }
    },
    
    onToggleAdvanced: () => {
        const isVisible = elements.advancedOptions.style.display !== 'none';
        elements.advancedOptions.style.display = isVisible ? 'none' : 'block';
        elements.toggleAdvanced.innerHTML = isVisible 
            ? '<i class="fas fa-chevron-down"></i> 显示高级选项' 
            : '<i class="fas fa-chevron-up"></i> 隐藏高级选项';
    },
    
    onReset: () => {
        elements.resultSection.style.display = 'none';
        elements.insightPanel.style.display = 'none';
        elements.welcomeGuide.style.display = 'block';
        
        // 重置表单（可选）
        // elements.initialCapital.value = 10000;
        // elements.maxDrawdown.value = '0.10';
        // elements.investmentPeriod.value = '3';
        // elements.indexFundOnly.checked = false;
        // elements.enableRebalancing.checked = false;
    },
    
    onSavePortfolio: () => {
        alert('保存功能开发中...');
    },
    
    onCompare: () => {
        alert('对比功能开发中...');
    },
    
    onShowAbout: (e) => {
        e.preventDefault();
        elements.aboutModal.style.display = 'flex';
    },
    
    onCloseModal: () => {
        elements.aboutModal.style.display = 'none';
    }
};

// 初始化
const init = () => {
    console.log('基智回测前端应用初始化');
    
    // 绑定事件
    elements.runBacktestBtn.addEventListener('click', eventHandlers.onRunBacktest);
    elements.toggleAdvanced.addEventListener('click', eventHandlers.onToggleAdvanced);
    elements.resetBtn.addEventListener('click', eventHandlers.onReset);
    elements.savePortfolioBtn.addEventListener('click', eventHandlers.onSavePortfolio);
    elements.compareBtn.addEventListener('click', eventHandlers.onCompare);
    
    // 模态框事件
    elements.aboutLink.addEventListener('click', eventHandlers.onShowAbout);
    elements.disclaimerLink.addEventListener('click', eventHandlers.onShowAbout);
    elements.apiLink.addEventListener('click', eventHandlers.onShowAbout);
    
    // 点击模态框外部关闭
    elements.aboutModal.addEventListener('click', (e) => {
        if (e.target === elements.aboutModal) {
            eventHandlers.onCloseModal();
        }
    });
    
    // 关闭按钮
    const closeModalBtn = elements.aboutModal.querySelector('.close-modal');
    if (closeModalBtn) {
        closeModalBtn.addEventListener('click', eventHandlers.onCloseModal);
    }
    
    // 监听回车键快速开始
    document.addEventListener('keydown', (e) => {
        if (e.key === 'Enter' && !AppState.isLoading) {
            eventHandlers.onRunBacktest();
        }
    });
    
    // 初始状态
    utils.showLoading(false);
};

// 辅助函数
function calculateFundQualityScore(fund) {
    let score = 0;
    const maxScore = 100;
    
    // 1. 基金规模评分 (0-40分)
    if (fund.totalAssets) {
        const sizeScore = Math.min(fund.totalAssets / 500, 1) * 40;
        score += sizeScore;
    }
    
    // 2. 成立时间评分 (0-30分)
    if (fund.establishedYears) {
        const ageScore = Math.min(fund.establishedYears / 20, 1) * 30;
        score += ageScore;
    }
    
    // 3. 风险等级加分 (0-15分)
    const riskBonus = {
        '稳健': 15,
        '保守': 10,
        '积极': 8
    };
    score += riskBonus[fund.riskLevel] || 5;
    
    // 4. 指数基金加分 (0-15分)
    if (fund.isIndexFund) {
        score += 15;
    }
    
    return Math.min(score, maxScore);
}

function getRiskColor(riskLevel) {
    switch (riskLevel) {
        case '保守': return 'success';
        case '稳健': return 'primary';
        case '积极': return 'warning';
        default: return 'secondary';
    }
}

function getFundTypeIcon(type, isIndexFund) {
    if (isIndexFund) return '<i class="fas fa-chart-line"></i>';
    
    if (type && type.includes('股票')) return '<i class="fas fa-chart-bar"></i>';
    if (type && type.includes('债券')) return '<i class="fas fa-hand-holding-usd"></i>';
    if (type && type.includes('混合')) return '<i class="fas fa-balance-scale"></i>';
    if (type && type.includes('指数')) return '<i class="fas fa-chart-line"></i>';
    
    return '<i class="fas fa-money-bill-wave"></i>';
}

function addEnhancedStyles() {
    // 如果样式还没添加，动态添加
    if (!document.querySelector('#enhanced-styles')) {
        const style = document.createElement('style');
        style.id = 'enhanced-styles';
        style.textContent = `
            /* 增强的基金卡片样式 */
            .portfolio-overview {
                background: linear-gradient(135deg, var(--light-bg) 0%, #e3f2fd 100%);
                padding: 1rem;
                border-radius: var(--radius);
                margin-bottom: 1.5rem;
                border-left: 4px solid var(--primary-color);
            }
            
            .portfolio-overview h4 {
                color: var(--primary-color);
                margin-bottom: 0.75rem;
                font-size: 1.1rem;
            }
            
            .overview-stats {
                display: flex;
                gap: 1rem;
                flex-wrap: wrap;
            }
            
            .overview-stat {
                display: flex;
                flex-direction: column;
                align-items: center;
                padding: 0.5rem 1rem;
                background: white;
                border-radius: var(--radius);
                min-width: 80px;
            }
            
            .stat-label {
                font-size: 0.85rem;
                color: var(--text-secondary);
            }
            
            .stat-value {
                font-weight: 600;
                font-size: 1.2rem;
                color: var(--primary-color);
            }
            
            /* 基金卡片增强 */
            .fund-card[data-risk="保守"] {
                border-left-color: var(--success-color);
            }
            
            .fund-card[data-risk="稳健"] {
                border-left-color: var(--primary-color);
            }
            
            .fund-card[data-risk="积极"] {
                border-left-color: var(--warning-color);
            }
            
            .fund-basic {
                display: flex;
                justify-content: space-between;
                align-items: flex-start;
                margin-bottom: 0.5rem;
            }
            
            .fund-code-name {
                flex: 1;
            }
            
            .fund-code {
                display: inline-block;
                background: var(--light-bg);
                color: var(--primary-color);
                padding: 0.2rem 0.5rem;
                border-radius: 4px;
                font-family: monospace;
                font-size: 0.9rem;
                margin-right: 0.5rem;
            }
            
            .fund-index-badge {
                display: inline-block;
                background: linear-gradient(45deg, var(--success-color), var(--info-color));
                color: white;
                padding: 0.15rem 0.5rem;
                border-radius: 12px;
                font-size: 0.75rem;
                margin-left: 0.5rem;
            }
            
            .fund-meta {
                display: flex;
                gap: 1rem;
                flex-wrap: wrap;
                margin-bottom: 0.5rem;
                font-size: 0.9rem;
            }
            
            .fund-meta span {
                display: flex;
                align-items: center;
                gap: 0.3rem;
                color: var(--text-secondary);
            }
            
            .fund-categories {
                display: flex;
                gap: 0.75rem;
                margin-bottom: 0.75rem;
                flex-wrap: wrap;
            }
            
            .fund-type {
                padding: 0.25rem 0.75rem;
                border-radius: 16px;
                font-size: 0.85rem;
                background: var(--light-bg);
                display: inline-flex;
                align-items: center;
                gap: 0.3rem;
            }
            
            .fund-risk {
                font-size: 0.85rem;
                font-weight: 600;
                display: flex;
                align-items: center;
                gap: 0.3rem;
            }
            
            .fund-quality {
                margin-top: 0.75rem;
                padding-top: 0.75rem;
                border-top: 1px solid var(--border-color);
            }
            
            .quality-bar {
                display: flex;
                align-items: center;
                gap: 0.75rem;
                flex-wrap: wrap;
            }
            
            .quality-label {
                font-size: 0.9rem;
                color: var(--text-secondary);
            }
            
            .quality-stars {
                color: var(--warning-color);
                font-size: 1.1rem;
                letter-spacing: 2px;
            }
            
            .quality-score {
                background: var(--primary-color);
                color: white;
                padding: 0.15rem 0.5rem;
                border-radius: 12px;
                font-size: 0.8rem;
                font-weight: 600;
            }
            
            /* 风险等级颜色 */
            :root {
                --success-color: #4cc9f0;
                --primary-color: #4361ee;
                --warning-color: #f8961e;
                --secondary-color: #6c757d;
            }
            
            .fund-type.success {
                border-left: 3px solid var(--success-color);
            }
            
            .fund-type.primary {
                border-left: 3px solid var(--primary-color);
            }
            
            .fund-type.warning {
                border-left: 3px solid var(--warning-color);
            }
            
            .fund-type.secondary {
                border-left: 3px solid var(--secondary-color);
            }
        `;
        document.head.appendChild(style);
    }
}

// 引入辅助函数
function getInvestmentTips(funds, backtest) {
    // 简化版本，如果需要完整版本请加载 helpers.js
    const tips = [];
    
    const avgSize = funds.reduce((sum, f) => sum + (f.totalAssets || 0), 0) / funds.length;
    if (avgSize > 200) tips.push('组合包含大型基金，流动性好，适合大额投资');
    else if (avgSize > 50) tips.push('基金规模适中，平衡了流动性和管理灵活性');
    
    const avgAge = funds.reduce((sum, f) => sum + (f.establishedYears || 0), 0) / funds.length;
    if (avgAge > 10) tips.push('老牌基金组合，历史业绩参考价值高');
    
    const fundTypes = new Set(funds.map(f => f.type));
    if (fundTypes.size >= 3) tips.push('多类型基金组合，分散投资风险');
    
    const indexFundCount = funds.filter(f => f.isIndexFund).length;
    if (indexFundCount > 0) tips.push('指数基金费率较低，长期投资优势明显');
    
    return tips.map(tip => `<li>${tip}</li>`).join('');
}

function addAnalysisStyles() {
    if (!document.querySelector('#analysis-styles')) {
        const style = document.createElement('style');
        style.id = 'analysis-styles';
        style.textContent = `
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
        `;
        document.head.appendChild(style);
    }
}

// 页面加载完成后初始化
if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
} else {
    init();
}