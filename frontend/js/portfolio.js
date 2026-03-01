// 基智回测 - 投资组合管理页面逻辑

// API 基础 URL
// API 基础 URL - 自动检测当前主机
const API_BASE_URL = '';

// 全局状态
const PortfolioState = {
    portfolios: [],
    selectedPortfolioId: null,
    currentFunds: [], // 当前组合中的基金
    tempSelectedFund: null // 临时选择的基金
};

// DOM 元素
const elements = {
    // 列表和状态
    emptyState: document.getElementById('emptyState'),
    loadingState: document.getElementById('loadingState'),
    portfolioGrid: document.getElementById('portfolioGrid'),
    actionPanel: document.getElementById('actionPanel'),
    
    // 按钮
    createPortfolioBtn: document.getElementById('createPortfolioBtn'),
    backtestBtn: document.getElementById('backtestBtn'),
    editFundsBtn: document.getElementById('editFundsBtn'),
    duplicateBtn: document.getElementById('duplicateBtn'),
    deleteBtn: document.getElementById('deleteBtn'),
    
    // 模态框
    portfolioModal: document.getElementById('portfolioModal'),
    portfolioForm: document.getElementById('portfolioForm'),
    modalTitle: document.getElementById('modalTitle'),
    portfolioId: document.getElementById('portfolioId'),
    portfolioName: document.getElementById('portfolioName'),
    portfolioDescription: document.getElementById('portfolioDescription'),
    portfolioRiskLevel: document.getElementById('portfolioRiskLevel'),
    portfolioFundList: document.getElementById('portfolioFundList'),
    addFundBtn: document.getElementById('addFundBtn'),
    totalWeightDisplay: document.getElementById('totalWeightDisplay'),
    totalWeightValue: document.getElementById('totalWeightValue'),
    
    // 选择基金模态框
    selectFundModal: document.getElementById('selectFundModal'),
    searchFundInput: document.getElementById('searchFundInput'),
    searchFundList: document.getElementById('searchFundList'),
    confirmAddFundBtn: document.getElementById('confirmAddFundBtn'),
    
    // 选中信息
    selectedPortfolioName: document.getElementById('selectedPortfolioName')
};

// 工具函数
const utils = {
    formatPercent: (value) => {
        if (value === null || value === undefined || isNaN(value)) return '--';
        const numValue = typeof value === 'string' ? parseFloat(value) : value;
        return (numValue * 100).toFixed(2) + '%';
    },
    
    formatNumber: (value, decimals = 2) => {
        if (value === null || value === undefined || isNaN(value)) return '--';
        return Number(value).toLocaleString('zh-CN', {
            minimumFractionDigits: decimals,
            maximumFractionDigits: decimals
        });
    },
    
    formatDate: (dateStr) => {
        if (!dateStr) return '--';
        return new Date(dateStr).toLocaleDateString('zh-CN');
    },
    
    showModal: (modal) => {
        modal.classList.add('show');
        document.body.style.overflow = 'hidden';
    },
    
    hideModal: (modal) => {
        modal.classList.remove('show');
        document.body.style.overflow = '';
    },
    
    showLoading: (show) => {
        elements.emptyState.style.display = show ? 'none' : 'block';
        elements.loadingState.style.display = show ? 'block' : 'none';
        elements.portfolioGrid.style.display = show ? 'none' : 'grid';
    },
    
    showError: (message) => {
        alert(`错误：${message}`);
    },
    
    showSuccess: (message) => {
        // 可以添加 toast 通知
        console.log('成功:', message);
    }
};

// API 函数
const api = {
    // 获取所有投资组合
    getPortfolios: async () => {
        console.log('获取投资组合列表');
        
        // TODO: 替换为真实 API
        // const response = await fetch(`${API_BASE_URL}/portfolios`);
        // return await response.json();
        
        await new Promise(resolve => setTimeout(resolve, 500));
        
        // 模拟数据
        return {
            success: true,
            data: PortfolioState.portfolios.length > 0 ? PortfolioState.portfolios : []
        };
    },
    
    // 创建投资组合
    createPortfolio: async (params) => {
        console.log('创建投资组合:', params);
        
        // TODO: 替换为真实 API
        await new Promise(resolve => setTimeout(resolve, 500));
        
        const newPortfolio = {
            id: Date.now(),
            name: params.name,
            description: params.description,
            riskLevel: params.riskLevel,
            createdAt: new Date().toISOString(),
            holdings: params.holdings || [],
            latestBacktest: null
        };
        
        PortfolioState.portfolios.push(newPortfolio);
        
        return {
            success: true,
            data: newPortfolio
        };
    },
    
    // 更新投资组合
    updatePortfolio: async (id, params) => {
        console.log('更新投资组合:', id, params);
        
        await new Promise(resolve => setTimeout(resolve, 500));
        
        const index = PortfolioState.portfolios.findIndex(p => p.id === id);
        if (index === -1) {
            return { success: false, message: '组合不存在' };
        }
        
        PortfolioState.portfolios[index] = {
            ...PortfolioState.portfolios[index],
            ...params,
            updatedAt: new Date().toISOString()
        };
        
        return {
            success: true,
            data: PortfolioState.portfolios[index]
        };
    },
    
    // 删除投资组合
    deletePortfolio: async (id) => {
        console.log('删除投资组合:', id);
        
        await new Promise(resolve => setTimeout(resolve, 500));
        
        const index = PortfolioState.portfolios.findIndex(p => p.id === id);
        if (index === -1) {
            return { success: false, message: '组合不存在' };
        }
        
        PortfolioState.portfolios.splice(index, 1);
        
        return { success: true };
    },
    
    // 获取组合详情
    getPortfolioDetail: async (id) => {
        console.log('获取组合详情:', id);
        
        await new Promise(resolve => setTimeout(resolve, 300));
        
        const portfolio = PortfolioState.portfolios.find(p => p.id === id);
        if (!portfolio) {
            return { success: false, message: '组合不存在' };
        }
        
        return {
            success: true,
            data: portfolio
        };
    },
    
    // 搜索基金
    searchFunds: async (keyword) => {
        console.log('搜索基金:', keyword);
        
        await new Promise(resolve => setTimeout(resolve, 500));
        
        // 模拟基金数据
        const mockFunds = [
            { code: '000001', name: '华夏成长混合', type: '混合型', riskLevel: '中风险' },
            { code: '000002', name: '易方达沪深 300ETF 联接', type: '指数型', riskLevel: '中风险', isIndexFund: true },
            { code: '000003', name: '富国天惠债券', type: '债券型', riskLevel: '低风险' },
            { code: '000004', name: '嘉实沪深 300ETF', type: '指数型', riskLevel: '中风险', isIndexFund: true },
            { code: '000005', name: '南方中证 500ETF 联接', type: '指数型', riskLevel: '中风险', isIndexFund: true },
            { code: '000006', name: '广发纳斯达克 100ETF 联接', type: 'QDII', riskLevel: '高风险' },
            { code: '000007', name: '招商中证白酒指数', type: '指数型', riskLevel: '中高风险', isIndexFund: true },
            { code: '000008', name: '汇添富消费行业混合', type: '混合型', riskLevel: '中风险' },
            { code: '000009', name: '鹏华创业板 ETF 联接', type: '指数型', riskLevel: '中高风险', isIndexFund: true },
            { code: '000010', name: '博时信用债纯债', type: '债券型', riskLevel: '低风险' }
        ];
        
        if (!keyword) {
            return { success: true, data: mockFunds.slice(0, 5) };
        }
        
        const filtered = mockFunds.filter(fund => 
            fund.code.includes(keyword) || 
            fund.name.toLowerCase().includes(keyword.toLowerCase())
        );
        
        return {
            success: true,
            data: filtered
        };
    },
    
    // 运行回测
    runBacktest: async (portfolioId, params) => {
        console.log('运行回测:', portfolioId, params);
        
        await new Promise(resolve => setTimeout(resolve, 2000));
        
        // 模拟回测结果
        return {
            success: true,
            data: {
                backtestId: Date.now(),
                portfolioId: portfolioId,
                startDate: params.startDate,
                endDate: params.endDate,
                initialCapital: params.initialCapital,
                finalValue: params.initialCapital * 1.35,
                totalReturn: 0.35,
                annualizedReturn: 0.085,
                maxDrawdown: 0.092,
                sharpeRatio: 1.2,
                benchmarkReturn: 0.045
            }
        };
    }
};

// UI 更新函数
const ui = {
    renderPortfolioList: () => {
        const portfolios = PortfolioState.portfolios;
        
        if (portfolios.length === 0) {
            elements.emptyState.style.display = 'block';
            elements.portfolioGrid.style.display = 'none';
            elements.actionPanel.style.display = 'none';
            return;
        }
        
        elements.emptyState.style.display = 'none';
        elements.portfolioGrid.style.display = 'grid';
        
        elements.portfolioGrid.innerHTML = portfolios.map(portfolio => {
            const badgeClass = {
                '保守': 'badge-conservative',
                '稳健': 'badge-steady',
                '积极': 'badge-aggressive',
                '自定义': 'badge-custom'
            }[portfolio.riskLevel] || 'badge-custom';
            
            const fundCount = portfolio.holdings ? portfolio.holdings.length : 0;
            const fundCodes = (portfolio.holdings || []).slice(0, 3).map(h => h.fundCode || h.code);
            
            return `
                <div class="portfolio-card ${PortfolioState.selectedPortfolioId === portfolio.id ? 'selected' : ''}" 
                     data-id="${portfolio.id}">
                    <div class="portfolio-card-header">
                        <h3>${portfolio.name}</h3>
                        <span class="portfolio-badge ${badgeClass}">${portfolio.riskLevel}</span>
                    </div>
                    
                    <div class="portfolio-stats">
                        <div class="stat-item">
                            <div class="stat-label">基金数量</div>
                            <div class="stat-value">${fundCount}只</div>
                        </div>
                        <div class="stat-item">
                            <div class="stat-label">创建时间</div>
                            <div class="stat-value">${utils.formatDate(portfolio.createdAt)}</div>
                        </div>
                    </div>
                    
                    ${fundCount > 0 ? `
                        <div class="portfolio-funds">
                            <div class="portfolio-funds-title">持仓基金 (${fundCount}只)</div>
                            <div class="fund-tags">
                                ${fundCodes.map(code => `<span class="fund-tag">${code}</span>`).join('')}
                                ${fundCount > 3 ? `<span class="fund-tag">+${fundCount - 3}</span>` : ''}
                            </div>
                        </div>
                    ` : '<div style="color: var(--text-secondary); font-size: 0.9rem; margin-bottom: 1rem;"><i class="fas fa-info-circle"></i> 暂无持仓基金</div>'}
                    
                    <div class="portfolio-card-actions">
                        <button class="btn-secondary" onclick="selectPortfolio(${portfolio.id})">
                            <i class="fas fa-eye"></i> 查看
                        </button>
                        <button class="btn-secondary" onclick="quickBacktest(${portfolio.id})">
                            <i class="fas fa-rocket"></i> 回测
                        </button>
                    </div>
                </div>
            `;
        }).join('');
        
        // 绑定点击事件
        document.querySelectorAll('.portfolio-card').forEach(card => {
            card.addEventListener('click', (e) => {
                if (!e.target.closest('button')) {
                    selectPortfolio(parseInt(card.dataset.id));
                }
            });
        });
    },
    
    updateSelectedPortfolio: (portfolio) => {
        if (portfolio) {
            elements.selectedPortfolioName.textContent = portfolio.name;
            elements.actionPanel.style.display = 'block';
        } else {
            elements.actionPanel.style.display = 'none';
        }
    },
    
    renderPortfolioFunds: (holdings) => {
        if (!holdings || holdings.length === 0) {
            elements.portfolioFundList.innerHTML = `
                <div style="padding: 1.5rem; text-align: center; color: var(--text-secondary);">
                    暂无持仓基金
                </div>
            `;
            updateTotalWeight([]);
            return;
        }
        
        elements.portfolioFundList.innerHTML = holdings.map((holding, index) => `
            <div class="fund-list-item" data-index="${index}">
                <div class="fund-info">
                    <div class="fund-code-name">
                        <span class="fund-code">${holding.fundCode || holding.code}</span>
                        ${holding.fundName || holding.name}
                    </div>
                    <div class="fund-meta">
                        ${holding.type || '--'} | ${holding.riskLevel || '--'}
                    </div>
                </div>
                <div style="display: flex; align-items: center; gap: 0.5rem;">
                    <input type="number" class="fund-weight-input" 
                           value="${(holding.targetWeight * 100).toFixed(0)}" 
                           data-index="${index}"
                           placeholder="权重%"
                           min="0" max="100">
                    <span style="color: var(--text-secondary);">%</span>
                    <button type="button" class="remove-fund-btn" data-index="${index}">
                        <i class="fas fa-trash"></i>
                    </button>
                </div>
            </div>
        `).join('');
        
        updateTotalWeight(holdings);
        
        // 绑定删除事件
        elements.portfolioFundList.querySelectorAll('.remove-fund-btn').forEach(btn => {
            btn.addEventListener('click', (e) => {
                const index = parseInt(e.target.closest('button').dataset.index);
                removeFundFromPortfolio(index);
            });
        });
        
        // 绑定权重变化事件
        elements.portfolioFundList.querySelectorAll('.fund-weight-input').forEach(input => {
            input.addEventListener('change', (e) => {
                const index = parseInt(e.target.dataset.index);
                const newWeight = parseFloat(e.target.value) / 100;
                updateFundWeight(index, newWeight);
            });
        });
    },
    
    renderSearchFundList: (funds) => {
        if (!funds || funds.length === 0) {
            elements.searchFundList.innerHTML = `
                <div style="padding: 1.5rem; text-align: center; color: var(--text-secondary);">
                    暂无搜索结果
                </div>
            `;
            return;
        }
        
        elements.searchFundList.innerHTML = funds.map(fund => `
            <div class="fund-list-item" onclick="selectFundTo('${fund.code}', '${fund.name}', '${fund.type}', '${fund.riskLevel}')">
                <div class="fund-info">
                    <div class="fund-code-name">
                        <span class="fund-code">${fund.code}</span>
                        ${fund.name}
                    </div>
                    <div class="fund-meta">
                        ${fund.type} | ${fund.riskLevel}
                        ${fund.isIndexFund ? '<span style="color: var(--success-color);"><i class="fas fa-chart-line"></i> 指数</span>' : ''}
                    </div>
                </div>
                <i class="fas fa-plus-circle" style="color: var(--primary-color);"></i>
            </div>
        `).join('');
    }
};

// 事件处理
const eventHandlers = {
    // 创建组合
    onCreatePortfolio: () => {
        PortfolioState.selectedPortfolioId = null;
        PortfolioState.currentFunds = [];
        elements.portfolioForm.reset();
        elements.portfolioId.value = '';
        elements.modalTitle.innerHTML = '<i class="fas fa-plus"></i> 新建投资组合';
        ui.renderPortfolioFunds([]);
        utils.showModal(elements.portfolioModal);
    },
    
    // 保存组合
    onSavePortfolio: async (e) => {
        e.preventDefault();
        
        const id = elements.portfolioId.value ? parseInt(elements.portfolioId.value) : null;
        const params = {
            name: elements.portfolioName.value,
            description: elements.portfolioDescription.value,
            riskLevel: elements.portfolioRiskLevel.value,
            holdings: PortfolioState.currentFunds
        };
        
        try {
            let response;
            if (id) {
                response = await api.updatePortfolio(id, params);
            } else {
                response = await api.createPortfolio(params);
            }
            
            if (!response.success) {
                throw new Error(response.message || '保存失败');
            }
            
            utils.hideModal(elements.portfolioModal);
            await loadPortfolios();
            utils.showSuccess('保存成功');
            
        } catch (error) {
            console.error('保存失败:', error);
            utils.showError(error.message || '保存失败，请重试');
        }
    },
    
    // 添加基金
    onAddFund: async () => {
        // 先搜索基金
        await searchFunds('');
        utils.showModal(elements.selectFundModal);
    },
    
    // 搜索基金
    onSearchFund: async () => {
        const keyword = elements.searchFundInput.value.trim();
        await searchFunds(keyword);
    },
    
    // 确认添加基金
    onConfirmAddFund: () => {
        if (!PortfolioState.tempSelectedFund) {
            utils.showError('请先选择基金');
            return;
        }
        
        // 检查是否已存在
        const exists = PortfolioState.currentFunds.some(
            f => f.fundCode === PortfolioState.tempSelectedFund.code
        );
        
        if (exists) {
            utils.showError('该基金已在组合中');
            return;
        }
        
        // 添加到临时列表
        PortfolioState.currentFunds.push({
            fundCode: PortfolioState.tempSelectedFund.code,
            fundName: PortfolioState.tempSelectedFund.name,
            type: PortfolioState.tempSelectedFund.type,
            riskLevel: PortfolioState.tempSelectedFund.riskLevel,
            targetWeight: 0.1 // 默认 10%
        });
        
        ui.renderPortfolioFunds(PortfolioState.currentFunds);
        utils.hideModal(elements.selectFundModal);
        PortfolioState.tempSelectedFund = null;
    },
    
    // 编辑持仓
    onEditFunds: async () => {
        const portfolio = PortfolioState.portfolios.find(p => p.id === PortfolioState.selectedPortfolioId);
        if (!portfolio) return;
        
        PortfolioState.currentFunds = [...(portfolio.holdings || [])];
        elements.portfolioId.value = portfolio.id;
        elements.portfolioName.value = portfolio.name;
        elements.portfolioDescription.value = portfolio.description || '';
        elements.portfolioRiskLevel.value = portfolio.riskLevel || '稳健';
        elements.modalTitle.innerHTML = '<i class="fas fa-edit"></i> 编辑投资组合';
        
        ui.renderPortfolioFunds(PortfolioState.currentFunds);
        utils.showModal(elements.portfolioModal);
    },
    
    // 回测
    onBacktest: async () => {
        const portfolio = PortfolioState.portfolios.find(p => p.id === PortfolioState.selectedPortfolioId);
        if (!portfolio) {
            utils.showError('请先选择组合');
            return;
        }
        
        if (!portfolio.holdings || portfolio.holdings.length === 0) {
            utils.showError('组合中没有基金，请先添加持仓');
            return;
        }
        
        // 跳转到回测页面或打开回测配置模态框
        alert(`开始回测组合：${portfolio.name}\n回测功能开发中...`);
        // TODO: 实现回测配置和运行
    },
    
    // 复制组合
    onDuplicate: async () => {
        const portfolio = PortfolioState.portfolios.find(p => p.id === PortfolioState.selectedPortfolioId);
        if (!portfolio) return;
        
        const newPortfolio = {
            name: portfolio.name + ' (副本)',
            description: portfolio.description,
            riskLevel: portfolio.riskLevel,
            holdings: [...(portfolio.holdings || [])]
        };
        
        try {
            const response = await api.createPortfolio(newPortfolio);
            if (response.success) {
                await loadPortfolios();
                utils.showSuccess('复制成功');
            }
        } catch (error) {
            utils.showError(error.message || '复制失败');
        }
    },
    
    // 删除组合
    onDelete: async () => {
        const portfolio = PortfolioState.portfolios.find(p => p.id === PortfolioState.selectedPortfolioId);
        if (!portfolio) return;
        
        if (!confirm(`确定要删除组合"${portfolio.name}"吗？此操作不可恢复。`)) {
            return;
        }
        
        try {
            const response = await api.deletePortfolio(portfolio.id);
            if (response.success) {
                PortfolioState.selectedPortfolioId = null;
                await loadPortfolios();
                utils.showSuccess('删除成功');
            }
        } catch (error) {
            utils.showError(error.message || '删除失败');
        }
    },
    
    // 关闭模态框
    onCloseModal: (modal) => {
        utils.hideModal(modal);
    }
};

// 辅助函数
async function loadPortfolios() {
    try {
        utils.showLoading(true);
        const response = await api.getPortfolios();
        if (response.success) {
            PortfolioState.portfolios = response.data;
            ui.renderPortfolioList();
        }
    } catch (error) {
        console.error('加载失败:', error);
        utils.showError(error.message || '加载失败');
    } finally {
        utils.showLoading(false);
    }
}

async function searchFunds(keyword) {
    try {
        const response = await api.searchFunds(keyword);
        if (response.success) {
            ui.renderSearchFundList(response.data);
        }
    } catch (error) {
        console.error('搜索失败:', error);
    }
}

function selectPortfolio(id) {
    PortfolioState.selectedPortfolioId = id;
    ui.renderPortfolioList();
    
    const portfolio = PortfolioState.portfolios.find(p => p.id === id);
    ui.updateSelectedPortfolio(portfolio);
}

window.selectPortfolio = selectPortfolio;

function quickBacktest(id) {
    selectPortfolio(id);
    setTimeout(() => {
        eventHandlers.onBacktest();
    }, 100);
}

window.quickBacktest = quickBacktest;

function selectFundTo(code, name, type, riskLevel) {
    PortfolioState.tempSelectedFund = { code, name, type, riskLevel };
    
    // 高亮选中
    elements.searchFundList.querySelectorAll('.fund-list-item').forEach(item => {
        item.style.background = '';
    });
    
    const selectedItem = Array.from(elements.searchFundList.querySelectorAll('.fund-list-item'))
        .find(item => item.querySelector('.fund-code')?.textContent === code);
    
    if (selectedItem) {
        selectedItem.style.background = '#e3f2fd';
    }
}

window.selectFundTo = selectFundTo;

function removeFundFromPortfolio(index) {
    PortfolioState.currentFunds.splice(index, 1);
    ui.renderPortfolioFunds(PortfolioState.currentFunds);
}

function updateFundWeight(index, newWeight) {
    if (PortfolioState.currentFunds[index]) {
        PortfolioState.currentFunds[index].targetWeight = newWeight;
        ui.renderPortfolioFunds(PortfolioState.currentFunds);
    }
}

function updateTotalWeight(holdings) {
    const total = holdings.reduce((sum, h) => sum + (h.targetWeight || 0), 0);
    const totalPercent = (total * 100).toFixed(1);
    
    elements.totalWeightValue.textContent = `${totalPercent}%`;
    
    elements.totalWeightDisplay.className = 'total-weight';
    if (Math.abs(total - 1) < 0.01) {
        elements.totalWeightDisplay.classList.add('success');
        elements.totalWeightValue.textContent += ' ✓';
    } else if (total > 1.05) {
        elements.totalWeightDisplay.classList.add('error');
        elements.totalWeightValue.textContent += ' ⚠️ 超过 100%';
    } else if (total < 0.95 && holdings.length > 0) {
        elements.totalWeightDisplay.classList.add('warning');
        elements.totalWeightValue.textContent += ' ⚠️ 不足 100%';
    }
}

// 初始化
const init = () => {
    console.log('投资组合页面初始化');
    
    // 绑定事件
    elements.createPortfolioBtn.addEventListener('click', eventHandlers.onCreatePortfolio);
    elements.portfolioForm.addEventListener('submit', eventHandlers.onSavePortfolio);
    elements.addFundBtn.addEventListener('click', eventHandlers.onAddFund);
    elements.searchFundInput.addEventListener('input', eventHandlers.onSearchFund);
    elements.searchFundInput.addEventListener('keypress', (e) => {
        if (e.key === 'Enter') eventHandlers.onSearchFund();
    });
    elements.confirmAddFundBtn.addEventListener('click', eventHandlers.onConfirmAddFund);
    elements.backtestBtn.addEventListener('click', eventHandlers.onBacktest);
    elements.editFundsBtn.addEventListener('click', eventHandlers.onEditFunds);
    elements.duplicateBtn.addEventListener('click', eventHandlers.onDuplicate);
    elements.deleteBtn.addEventListener('click', eventHandlers.onDelete);
    
    // 模态框关闭
    document.querySelectorAll('.close-modal').forEach(btn => {
        btn.addEventListener('click', () => {
            eventHandlers.onCloseModal(btn.closest('.modal'));
        });
    });
    
    // 点击模态框外部关闭
    elements.portfolioModal.addEventListener('click', (e) => {
        if (e.target === elements.portfolioModal) {
            eventHandlers.onCloseModal(elements.portfolioModal);
        }
    });
    
    elements.selectFundModal.addEventListener('click', (e) => {
        if (e.target === elements.selectFundModal) {
            eventHandlers.onCloseModal(elements.selectFundModal);
        }
    });
    
    // 加载数据
    loadPortfolios();
};

// 页面加载完成后初始化
if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
} else {
    init();
}
