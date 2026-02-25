// 基智回测 - 前端主应用逻辑

// API 基础URL (开发环境)
const API_BASE_URL = 'http://localhost:8080/api';

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
        
        const html = funds.map(fund => `
            <div class="fund-card">
                <div class="fund-header">
                    <span class="fund-name">${fund.name}</span>
                    <span class="fund-weight">${(fund.weight * 100).toFixed(0)}%</span>
                </div>
                <div class="fund-details">
                    <div>代码: ${fund.code}</div>
                    <div>类型: ${fund.type}</div>
                </div>
            </div>
        `).join('');
        
        elements.portfolioComposition.innerHTML = html;
    },
    
    updateInsights: (insights) => {
        if (!insights || insights.length === 0) {
            elements.insightBubble.innerHTML = '<p>暂无解读信息</p>';
            return;
        }
        
        const html = insights.map(insight => `<p>${insight}</p>`).join('');
        elements.insightBubble.innerHTML = html;
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
            ui.updateInsights(response.data.insights);
            
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

// 页面加载完成后初始化
if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
} else {
    init();
}