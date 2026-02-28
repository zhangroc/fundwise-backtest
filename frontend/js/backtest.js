// 基智回测 - 回测分析页面逻辑

// API 基础 URL - 自动检测当前主机
const API_BASE_URL = window.location.protocol + '//' + window.location.hostname + ':3389/api';

// 全局状态
const BacktestState = {
    portfolios: [],
    selectedPortfolioId: null,
    chartInstance: null
};

// DOM 元素
const elements = {
    portfolioSelect: document.getElementById('portfolioSelect'),
    startDate: document.getElementById('startDate'),
    endDate: document.getElementById('endDate'),
    initialCapital: document.getElementById('initialCapital'),
    backtestForm: document.getElementById('backtestForm'),
    runBacktestBtn: document.getElementById('runBacktestBtn'),
    
    emptyState: document.getElementById('emptyState'),
    loadingState: document.getElementById('loadingState'),
    resultsContent: document.getElementById('resultsContent'),
    
    resultPortfolioName: document.getElementById('resultPortfolioName'),
    resultPeriod: document.getElementById('resultPeriod'),
    metricsGrid: document.getElementById('metricsGrid'),
    navChart: document.getElementById('navChart'),
    totalDays: document.getElementById('totalDays'),
    winRate: document.getElementById('winRate'),
    volatility: document.getElementById('volatility')
};

// 工具函数
const utils = {
    formatMoney: (value) => {
        if (value === null || value === undefined) return '--';
        return '¥' + Number(value).toLocaleString('zh-CN', {
            minimumFractionDigits: 2,
            maximumFractionDigits: 2
        });
    },
    
    formatPercent: (value) => {
        if (value === null || value === undefined) return '--';
        const numValue = typeof value === 'string' ? parseFloat(value) : value;
        return (numValue * 100).toFixed(2) + '%';
    },
    
    formatDate: (dateStr) => {
        if (!dateStr) return '--';
        return new Date(dateStr).toLocaleDateString('zh-CN');
    },
    
    showLoading: (show) => {
        elements.emptyState.style.display = show ? 'none' : 'block';
        elements.loadingState.style.display = show ? 'block' : 'none';
        elements.resultsContent.style.display = show ? 'none' : 'block';
        elements.runBacktestBtn.disabled = show;
    }
};

// API 函数
const api = {
    // 获取所有投资组合
    getPortfolios: async () => {
        try {
            const response = await fetch(`${API_BASE_URL}/portfolios`);
            const result = await response.json();
            
            if (result.success) {
                BacktestState.portfolios = result.data || [];
                return BacktestState.portfolios;
            }
            return [];
        } catch (error) {
            console.error('获取组合列表失败:', error);
            return [];
        }
    },
    
    // 运行回测
    runBacktest: async (portfolioId, startDate, endDate, initialCapital) => {
        try {
            const response = await fetch(`${API_BASE_URL}/backtest/run`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    portfolioId: portfolioId,
                    startDate: startDate,
                    endDate: endDate,
                    initialCapital: initialCapital
                })
            });
            
            const result = await response.json();
            
            if (!result.success) {
                throw new Error(result.message || '回测失败');
            }
            
            return result.data;
        } catch (error) {
            console.error('回测失败:', error);
            throw error;
        }
    }
};

// UI 更新函数
const ui = {
    // 填充组合下拉框
    renderPortfolioSelect: () => {
        const portfolios = BacktestState.portfolios;
        
        if (portfolios.length === 0) {
            elements.portfolioSelect.innerHTML = '<option value="">暂无投资组合</option>';
            return;
        }
        
        elements.portfolioSelect.innerHTML = 
            '<option value="">请选择投资组合</option>' +
            portfolios.map(p => 
                `<option value="${p.id}">${p.name} (${p.holdingsCount || 0}只基金)</option>`
            ).join('');
    },
    
    // 显示回测结果
    showResults: (data) => {
        // 基本信息
        elements.resultPortfolioName.textContent = data.portfolioName || '--';
        elements.resultPeriod.textContent = `${data.startDate} 至 ${data.endDate}`;
        
        // 关键指标
        const metrics = [
            { label: '初始资金', value: data.initialCapital, type: 'money' },
            { label: '最终市值', value: data.finalValue, type: 'money' },
            { label: '总收益率', value: data.totalReturnRaw, type: 'percent', rawKey: 'totalReturn' },
            { label: '年化收益', value: data.annualizedReturn, type: 'percent', rawKey: 'annualizedReturnRaw' },
            { label: '最大回撤', value: data.maxDrawdown, type: 'percent', rawKey: 'maxDrawdownRaw', negative: true },
            { label: '夏普比率', value: data.sharpeRatio, type: 'number' }
        ];
        
        elements.metricsGrid.innerHTML = metrics.map(m => {
            let displayValue;
            if (m.type === 'money') {
                displayValue = utils.formatMoney(m.value);
            } else if (m.type === 'percent') {
                displayValue = m.value;
            } else {
                displayValue = m.value || '--';
            }
            
            const valueClass = m.negative ? 'negative' : 
                              (m.rawKey && data[m.rawKey] < 0 ? 'negative' : 
                               (m.rawKey && data[m.rawKey] > 0 ? 'positive' : ''));
            
            return `
                <div class="metric-card">
                    <div class="metric-label">${m.label}</div>
                    <div class="metric-value ${valueClass}">${displayValue}</div>
                </div>
            `;
        }).join('');
        
        // 其他统计
        elements.totalDays.textContent = data.totalDays || '--';
        elements.winRate.textContent = data.winRate || '--';
        elements.volatility.textContent = data.volatility || '--';
        
        // 绘制图表
        renderChart(data.chartData || []);
    }
};

// 绘制净值走势图表
function renderChart(chartData) {
    if (elements.chartInstance) {
        elements.chartInstance.destroy();
    }
    
    const ctx = elements.navChart.getContext('2d');
    
    const labels = chartData.map(d => d.date);
    const values = chartData.map(d => d.portfolioValue);
    
    elements.chartInstance = new Chart(ctx, {
        type: 'line',
        data: {
            labels: labels,
            datasets: [{
                label: '组合市值',
                data: values,
                borderColor: '#4361ee',
                backgroundColor: 'rgba(67, 97, 238, 0.1)',
                borderWidth: 2,
                fill: true,
                tension: 0.1,
                pointRadius: 0
            }]
        },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            plugins: {
                legend: {
                    display: false
                },
                tooltip: {
                    mode: 'index',
                    intersect: false,
                    callbacks: {
                        label: (context) => {
                            const value = context.parsed.y;
                            return '市值：¥' + value.toLocaleString('zh-CN', {
                                minimumFractionDigits: 2,
                                maximumFractionDigits: 2
                            });
                        }
                    }
                }
            },
            scales: {
                x: {
                    title: {
                        display: true,
                        text: '日期'
                    },
                    ticks: {
                        maxTicksLimit: 10
                    }
                },
                y: {
                    title: {
                        display: true,
                        text: '市值 (元)'
                    },
                    ticks: {
                        callback: (value) => '¥' + value.toLocaleString()
                    }
                }
            }
        }
    });
}

// 事件处理
const eventHandlers = {
    // 加载组合列表
    loadPortfolios: async () => {
        await api.getPortfolios();
        ui.renderPortfolioSelect();
    },
    
    // 提交回测
    onSubmit: async (e) => {
        e.preventDefault();
        
        const portfolioId = elements.portfolioSelect.value;
        const startDate = elements.startDate.value;
        const endDate = elements.endDate.value;
        const initialCapital = parseFloat(elements.initialCapital.value);
        
        if (!portfolioId) {
            alert('请选择投资组合');
            return;
        }
        
        if (!startDate || !endDate) {
            alert('请设置回测期间');
            return;
        }
        
        if (startDate > endDate) {
            alert('开始日期不能晚于结束日期');
            return;
        }
        
        try {
            utils.showLoading(true);
            
            const data = await api.runBacktest(portfolioId, startDate, endDate, initialCapital);
            
            ui.showResults(data);
            
        } catch (error) {
            alert('回测失败：' + error.message);
        } finally {
            utils.showLoading(false);
        }
    }
};

// 初始化
const init = () => {
    console.log('回测页面初始化');
    
    // 绑定事件
    elements.backtestForm.addEventListener('submit', eventHandlers.onSubmit);
    
    // 加载组合列表
    eventHandlers.loadPortfolios();
};

// 页面加载完成后初始化
if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
} else {
    init();
}
