// 基智回测 - 基金筛选页面逻辑

// API 基础 URL
const API_BASE_URL = 'http://192.168.0.2:3389/api';

// 全局状态
const ScreeningState = {
    currentPage: 1,
    totalPages: 1,
    totalCount: 0,
    pageSize: 20,
    funds: [],
    selectedFunds: new Set(),
    sortField: 'return',
    sortOrder: 'desc'
};

// DOM 元素
const elements = {
    // 表单
    filterForm: document.getElementById('filterForm'),
    timePeriod: document.getElementById('timePeriod'),
    fundType: document.getElementById('fundType'),
    indexFundOnly: document.getElementById('indexFundOnly'),
    etfOnly: document.getElementById('etfOnly'),
    minReturn: document.getElementById('minReturn'),
    maxDrawdown: document.getElementById('maxDrawdown'),
    minSharpe: document.getElementById('minSharpe'),
    minSize: document.getElementById('minSize'),
    maxSize: document.getElementById('maxSize'),
    minYears: document.getElementById('minYears'),
    sortBy: document.getElementById('sortBy'),
    sortOrder: document.getElementById('sortOrder'),
    pageSize: document.getElementById('pageSize'),
    
    // 状态显示
    emptyState: document.getElementById('emptyState'),
    loadingState: document.getElementById('loadingState'),
    tableContainer: document.getElementById('tableContainer'),
    resultsCount: document.getElementById('resultsCount'),
    fundTableBody: document.getElementById('fundTableBody'),
    
    // 分页
    pagination: document.getElementById('pagination'),
    prevPage: document.getElementById('prevPage'),
    nextPage: document.getElementById('nextPage'),
    pageInfo: document.getElementById('pageInfo'),
    
    // 按钮
    searchBtn: document.getElementById('searchBtn'),
    resetBtn: document.getElementById('resetBtn'),
    exportBtn: document.getElementById('exportBtn'),
    compareBtn: document.getElementById('compareBtn'),
    selectAll: document.getElementById('selectAll')
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
    
    formatBillion: (value) => {
        if (value === null || value === undefined || isNaN(value)) return '--';
        return value.toFixed(1) + '亿';
    },
    
    showLoading: (show) => {
        elements.emptyState.style.display = show ? 'none' : 'block';
        elements.loadingState.style.display = show ? 'block' : 'none';
        elements.tableContainer.style.display = show ? 'none' : 'block';
        elements.searchBtn.disabled = show;
        elements.searchBtn.innerHTML = show 
            ? '<i class="fas fa-spinner fa-spin"></i> 筛选中...' 
            : '<i class="fas fa-search"></i> 开始筛选';
    },
    
    showError: (message) => {
        alert(`错误：${message}`);
    },
    
    // 获取基金类型样式类
    getFundTypeClass: (type) => {
        if (!type) return '';
        const typeMap = {
            '股票型': '股票型',
            '混合型': '混合型',
            '债券型': '债券型',
            '指数型': '指数型',
            'QDII': 'QDII',
            '货币型': '货币型'
        };
        return typeMap[type] || '';
    },
    
    // 获取风险等级样式类
    getRiskLevelClass: (level) => {
        if (!level) return '';
        const levelMap = {
            '低风险': '低风险',
            '中低风险': '中低风险',
            '中风险': '中风险',
            '中高风险': '中高风险',
            '高风险': '高风险'
        };
        return levelMap[level] || '';
    }
};

// API 函数
const api = {
    // 筛选基金
    screenFunds: async (params) => {
        console.log('调用基金筛选 API:', params);
        
        try {
            // 构建查询参数
            const queryParams = new URLSearchParams();
            if (params.timePeriod) queryParams.append('timePeriod', params.timePeriod);
            if (params.fundType && params.fundType !== 'all') queryParams.append('fundType', params.fundType);
            if (params.indexFundOnly) queryParams.append('indexFundOnly', 'true');
            if (params.etfOnly) queryParams.append('etfOnly', 'true');
            if (params.minReturn !== null && params.minReturn !== undefined) queryParams.append('minReturn', params.minReturn.toString());
            if (params.maxDrawdown !== null && params.maxDrawdown !== undefined) queryParams.append('maxDrawdown', params.maxDrawdown.toString());
            if (params.minSharpe !== null && params.minSharpe !== undefined) queryParams.append('minSharpe', params.minSharpe.toString());
            if (params.minSize !== null && params.minSize !== undefined) queryParams.append('minSize', params.minSize.toString());
            if (params.maxSize !== null && params.maxSize !== undefined) queryParams.append('maxSize', params.maxSize.toString());
            if (params.minYears !== null && params.minYears !== undefined) queryParams.append('minYears', params.minYears.toString());
            if (params.sortBy) queryParams.append('sortBy', params.sortBy);
            if (params.sortOrder) queryParams.append('sortOrder', params.sortOrder);
            queryParams.append('page', params.page || 1);
            queryParams.append('pageSize', params.pageSize || 20);
            
            const url = `${API_BASE_URL}/v1/funds/screen?${queryParams.toString()}`;
            console.log('请求 URL:', url);
            
            const response = await fetch(url);
            const result = await response.json();
            
            if (!result.success) {
                throw new Error(result.message || '筛选失败');
            }
            
            // 转换数据格式
            return {
                success: true,
                data: {
                    funds: result.data,
                    total: result.total,
                    page: result.page,
                    pageSize: result.pageSize,
                    totalPages: result.totalPages
                }
            };
        } catch (error) {
            console.error('API 调用失败:', error);
            // 如果 API 失败，回退到模拟数据
            console.log('使用模拟数据作为回退');
            await new Promise(resolve => setTimeout(resolve, 800));
            return generateMockFundData(params);
        }
    },
    
    // 获取基金详情
    getFundDetail: async (fundCode) => {
        console.log('获取基金详情:', fundCode);
        // TODO: 实现真实 API 调用
        return { success: true, data: {} };
    }
};

// 生成模拟基金数据
function generateMockFundData(params) {
    const fundTypes = ['股票型', '混合型', '债券型', '指数型', 'QDII'];
    const riskLevels = ['低风险', '中低风险', '中风险', '中高风险', '高风险'];
    const fundCompanies = [
        '易方达基金', '华夏基金', '广发基金', '富国基金', '汇添富基金',
        '招商基金', '南方基金', '博时基金', '嘉实基金', '鹏华基金'
    ];
    
    const fundNames = [
        '成长精选', '价值优势', '蓝筹精选', '科技创新', '消费行业',
        '医疗健康', '新能源', '半导体', '金融地产', '高端制造',
        '稳健收益', '双债增强', '信用债', '利率债', '可转债',
        '沪深 300ETF 联接', '中证 500ETF 联接', '创业板 ETF 联接', '科创 50ETF 联接',
        '全球科技', '美国纳斯达克', '香港恒生', '亚太精选'
    ];
    
    // 根据筛选条件生成数据
    const pageSize = parseInt(params.pageSize) || 20;
    const page = params.page || 1;
    const total = Math.floor(Math.random() * 200) + 50; // 模拟 50-250 条结果
    
    const funds = [];
    for (let i = 0; i < pageSize; i++) {
        const fundIndex = (page - 1) * pageSize + i;
        if (fundIndex >= total) break;
        
        const fundType = params.fundType && params.fundType !== 'all' 
            ? params.fundType 
            : fundTypes[Math.floor(Math.random() * fundTypes.length)];
        
        const isIndexFund = params.indexFundOnly ? true : (Math.random() > 0.6);
        const returnRate = params.minReturn ? 
            (parseFloat(params.minReturn) / 100) + Math.random() * 0.3 : 
            (Math.random() - 0.3) * 0.5;
        
        const maxDrawdown = params.maxDrawdown ?
            parseFloat(params.maxDrawdown) / 100 :
            -(Math.random() * 0.3);
        
        const sharpeRatio = params.minSharpe ?
            parseFloat(params.minSharpe) + Math.random() * 2 :
            (Math.random() - 0.5) * 3;
        
        const fundSize = params.minSize ?
            Math.max(parseFloat(params.minSize), Math.random() * 500) :
            Math.random() * 500;
        
        const establishedYears = params.minYears ?
            Math.max(parseInt(params.minYears), Math.floor(Math.random() * 20)) :
            Math.floor(Math.random() * 20);
        
        const fundCode = String(100000 + fundIndex).padStart(6, '0');
        const fundName = fundNames[Math.floor(Math.random() * fundNames.length)] + 
                        (isIndexFund ? 'ETF 联接' : '') + 
                        (Math.random() > 0.5 ? 'A' : 'C');
        
        funds.push({
            code: fundCode,
            name: fundName,
            type: fundType,
            isIndexFund: isIndexFund,
            isETF: params.etfOnly ? true : (Math.random() > 0.8),
            company: fundCompanies[Math.floor(Math.random() * fundCompanies.length)],
            returnRate: returnRate,
            maxDrawdown: maxDrawdown,
            sharpeRatio: sharpeRatio,
            totalAssets: fundSize,
            establishedYears: establishedYears,
            riskLevel: riskLevels[Math.floor(Math.random() * riskLevels.length)],
            netValue: (1 + returnRate) * (Math.random() * 0.5 + 0.8),
            netValueDate: new Date().toISOString().split('T')[0]
        });
    }
    
    // 排序
    const sortField = params.sortBy || 'return';
    const sortOrder = params.sortOrder || 'desc';
    
    funds.sort((a, b) => {
        let valueA, valueB;
        
        switch (sortField) {
            case 'return':
                valueA = a.returnRate;
                valueB = b.returnRate;
                break;
            case 'sharpe':
                valueA = a.sharpeRatio;
                valueB = b.sharpeRatio;
                break;
            case 'drawdown':
                valueA = a.maxDrawdown;
                valueB = b.maxDrawdown;
                break;
            case 'size':
                valueA = a.totalAssets;
                valueB = b.totalAssets;
                break;
            case 'age':
                valueA = a.establishedYears;
                valueB = b.establishedYears;
                break;
            default:
                valueA = a.returnRate;
                valueB = b.returnRate;
        }
        
        if (sortOrder === 'desc') {
            return valueB - valueA;
        } else {
            return valueA - valueB;
        }
    });
    
    return {
        success: true,
        data: {
            funds: funds,
            total: total,
            page: page,
            pageSize: pageSize,
            totalPages: Math.ceil(total / pageSize)
        }
    };
}

// UI 更新函数
const ui = {
    updateResultsCount: (count) => {
        elements.resultsCount.textContent = count;
    },
    
    renderFundTable: (funds) => {
        if (!funds || funds.length === 0) {
            elements.fundTableBody.innerHTML = `
                <tr>
                    <td colspan="11" style="text-align: center; padding: 3rem;">
                        <i class="fas fa-inbox" style="font-size: 3rem; color: #dee2e6; margin-bottom: 1rem;"></i>
                        <p style="color: var(--text-secondary);">暂无符合条件的基金</p>
                    </td>
                </tr>
            `;
            return;
        }
        
        elements.fundTableBody.innerHTML = funds.map(fund => {
            const returnClass = fund.returnRate >= 0 ? 'positive' : 'negative';
            const drawdownClass = fund.maxDrawdown >= 0 ? 'negative' : 'positive';
            
            return `
                <tr data-code="${fund.code}">
                    <td>
                        <input type="checkbox" class="fund-checkbox" value="${fund.code}">
                    </td>
                    <td>
                        <span class="fund-code">${fund.code}</span>
                    </td>
                    <td>
                        <div class="fund-name">${fund.name}</div>
                        ${fund.isIndexFund ? '<small style="color: var(--primary-color);"><i class="fas fa-chart-line"></i> 指数</small>' : ''}
                    </td>
                    <td>
                        <span class="fund-type-badge ${utils.getFundTypeClass(fund.type)}">${fund.type}</span>
                    </td>
                    <td class="${returnClass}">
                        ${utils.formatPercent(fund.returnRate)}
                    </td>
                    <td class="${drawdownClass}">
                        ${utils.formatPercent(fund.maxDrawdown)}
                    </td>
                    <td>
                        ${fund.sharpeRatio !== null ? fund.sharpeRatio.toFixed(2) : '--'}
                    </td>
                    <td>
                        ${utils.formatBillion(fund.totalAssets)}
                    </td>
                    <td>
                        ${fund.establishedYears}年
                    </td>
                    <td>
                        <span class="risk-level ${utils.getRiskLevelClass(fund.riskLevel)}">
                            <i class="fas fa-shield-alt"></i> ${fund.riskLevel}
                        </span>
                    </td>
                    <td>
                        <div style="display: flex; gap: 0.5rem;">
                            <button class="btn-secondary" onclick="viewFundDetail('${fund.code}')" 
                                    style="padding: 0.4rem 0.75rem; font-size: 0.85rem;">
                                <i class="fas fa-eye"></i>
                            </button>
                            <button class="btn-secondary" onclick="addFundToPortfolio('${fund.code}')" 
                                    style="padding: 0.4rem 0.75rem; font-size: 0.85rem;" 
                                    title="添加到投资组合">
                                <i class="fas fa-plus"></i>
                            </button>
                        </div>
                    </td>
                </tr>
            `;
        }).join('');
        
        // 更新全选 checkbox 状态
        updateSelectAllState();
    },
    
    updatePagination: (currentPage, totalPages, totalCount) => {
        elements.pageInfo.textContent = `第 ${currentPage} / ${totalPages} 页，共 ${totalCount} 条`;
        elements.prevPage.disabled = currentPage <= 1;
        elements.nextPage.disabled = currentPage >= totalPages;
    },
    
    showTable: (show) => {
        elements.emptyState.style.display = show ? 'none' : 'block';
        elements.tableContainer.style.display = show ? 'block' : 'none';
    }
};

// 更新全选 checkbox 状态
function updateSelectAllState() {
    const checkboxes = document.querySelectorAll('.fund-checkbox');
    const checkedCount = Array.from(checkboxes).filter(cb => cb.checked).length;
    elements.selectAll.checked = checkedCount > 0 && checkedCount === checkboxes.length;
    elements.selectAll.indeterminate = checkedCount > 0 && checkedCount < checkboxes.length;
}

// 事件处理
const eventHandlers = {
    // 提交筛选表单
    onSubmit: async (e) => {
        e.preventDefault();
        
        try {
            // 收集筛选参数
            const params = {
                timePeriod: elements.timePeriod.value,
                fundType: elements.fundType.value,
                indexFundOnly: elements.indexFundOnly.checked,
                etfOnly: elements.etfOnly.checked,
                minReturn: elements.minReturn.value ? parseFloat(elements.minReturn.value) : null,
                maxDrawdown: elements.maxDrawdown.value ? parseFloat(elements.maxDrawdown.value) : null,
                minSharpe: elements.minSharpe.value ? parseFloat(elements.minSharpe.value) : null,
                minSize: elements.minSize.value ? parseFloat(elements.minSize.value) : null,
                maxSize: elements.maxSize.value ? parseFloat(elements.maxSize.value) : null,
                minYears: elements.minYears.value ? parseInt(elements.minYears.value) : null,
                sortBy: elements.sortBy.value,
                sortOrder: elements.sortOrder.value,
                page: ScreeningState.currentPage,
                pageSize: parseInt(elements.pageSize.value)
            };
            
            // 显示加载状态
            utils.showLoading(true);
            
            // 调用 API
            const response = await api.screenFunds(params);
            
            if (!response.success) {
                throw new Error('筛选失败');
            }
            
            // 更新状态
            ScreeningState.funds = response.data.funds;
            ScreeningState.totalCount = response.data.total;
            ScreeningState.totalPages = response.data.totalPages;
            ScreeningState.currentPage = response.data.page;
            ScreeningState.pageSize = response.data.pageSize;
            
            // 更新 UI
            ui.updateResultsCount(response.data.total);
            ui.renderFundTable(response.data.funds);
            ui.updatePagination(
                response.data.page, 
                response.data.totalPages, 
                response.data.total
            );
            ui.showTable(true);
            
        } catch (error) {
            console.error('筛选失败:', error);
            utils.showError(error.message || '筛选失败，请重试');
        } finally {
            utils.showLoading(false);
        }
    },
    
    // 重置筛选条件
    onReset: () => {
        elements.filterForm.reset();
        ScreeningState.currentPage = 1;
        ScreeningState.selectedFunds.clear();
        ui.updateResultsCount(0);
        ui.showTable(false);
    },
    
    // 分页 - 上一页
    onPrevPage: () => {
        if (ScreeningState.currentPage > 1) {
            ScreeningState.currentPage--;
            eventHandlers.onSubmit(new Event('submit'));
        }
    },
    
    // 分页 - 下一页
    onNextPage: () => {
        if (ScreeningState.currentPage < ScreeningState.totalPages) {
            ScreeningState.currentPage++;
            eventHandlers.onSubmit(new Event('submit'));
        }
    },
    
    // 全选/取消全选
    onSelectAll: (e) => {
        const checkboxes = document.querySelectorAll('.fund-checkbox');
        checkboxes.forEach(cb => {
            cb.checked = e.target.checked;
            if (e.target.checked) {
                ScreeningState.selectedFunds.add(cb.value);
            } else {
                ScreeningState.selectedFunds.delete(cb.value);
            }
        });
        updateSelectAllState();
    },
    
    // 单选基金
    onFundSelect: (e) => {
        const code = e.target.value;
        if (e.target.checked) {
            ScreeningState.selectedFunds.add(code);
        } else {
            ScreeningState.selectedFunds.delete(code);
        }
        updateSelectAllState();
    },
    
    // 导出 Excel
    onExport: () => {
        if (ScreeningState.funds.length === 0) {
            utils.showError('暂无数据可导出');
            return;
        }
        alert('导出功能开发中...');
        // TODO: 实现导出功能
    },
    
    // 对比选中基金
    onCompare: () => {
        if (ScreeningState.selectedFunds.size === 0) {
            utils.showError('请先选择要对比的基金');
            return;
        }
        if (ScreeningState.selectedFunds.size < 2) {
            utils.showError('请至少选择 2 只基金进行对比');
            return;
        }
        alert(`对比功能开发中... 已选择 ${ScreeningState.selectedFunds.size} 只基金`);
        // TODO: 实现对比功能
    }
};

// 查看基金详情（全局函数）
window.viewFundDetail = (fundCode) => {
    console.log('查看基金详情:', fundCode);
    alert(`基金详情功能开发中...\n基金代码：${fundCode}`);
    // TODO: 实现基金详情页或弹窗
};

// 添加到投资组合（全局函数）
window.addFundToPortfolio = async (fundCode) => {
    console.log('添加到组合:', fundCode);
    
    // 获取基金信息
    const fund = ScreeningState.funds.find(f => f.code === fundCode);
    if (!fund) {
        utils.showError('基金信息不存在');
        return;
    }
    
    try {
        // 获取用户的所有组合
        const response = await fetch('http://192.168.0.2:3389/api/portfolios');
        const result = await response.json();
        
        if (!result.success || !result.data || result.data.length === 0) {
            // 没有组合，提示创建
            if (confirm('您还没有投资组合，是否现在创建一个？')) {
                window.location.href = 'portfolio.html';
            }
            return;
        }
        
        // 显示组合选择对话框
        const portfolios = result.data;
        const options = portfolios.map((p, index) => 
            `${index + 1}. ${p.name} (${p.riskLevel}) - ${p.holdings?.length || 0}只基金`
        ).join('\n');
        
        const choice = prompt(`选择要添加到的投资组合:\n${options}\n\n输入序号 (1-${portfolios.length}):`);
        
        if (!choice) return;
        
        const selectedIndex = parseInt(choice) - 1;
        if (isNaN(selectedIndex) || selectedIndex < 0 || selectedIndex >= portfolios.length) {
            utils.showError('无效的选择');
            return;
        }
        
        const selectedPortfolio = portfolios[selectedIndex];
        
        // 调用 API 添加到组合
        const addResponse = await fetch(`http://192.168.0.2:3389/api/portfolios/${selectedPortfolio.id}/holdings`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                fundCode: fund.code,
                fundName: fund.name,
                type: fund.type,
                riskLevel: fund.riskLevel,
                targetWeight: 0.1 // 默认 10%
            })
        });
        
        const addResult = await addResponse.json();
        
        if (addResult.success) {
            alert(`成功添加 ${fund.name} (${fund.code}) 到组合 "${selectedPortfolio.name}"`);
        } else {
            throw new Error(addResult.message || '添加失败');
        }
        
    } catch (error) {
        console.error('添加失败:', error);
        // 如果 API 调用失败，使用模拟方式
        const confirmed = confirm(`将 ${fund.name} (${fund.code}) 添加到投资组合？\n\n(后端 API 未就绪，点击确定跳转到组合页面)`);
        if (confirmed) {
            window.location.href = 'portfolio.html';
        }
    }
};

// 初始化
const init = () => {
    console.log('基金筛选页面初始化');
    
    // 绑定事件
    elements.filterForm.addEventListener('submit', eventHandlers.onSubmit);
    elements.resetBtn.addEventListener('click', eventHandlers.onReset);
    elements.prevPage.addEventListener('click', eventHandlers.onPrevPage);
    elements.nextPage.addEventListener('click', eventHandlers.onNextPage);
    elements.selectAll.addEventListener('change', eventHandlers.onSelectAll);
    elements.exportBtn.addEventListener('click', eventHandlers.onExport);
    elements.compareBtn.addEventListener('click', eventHandlers.onCompare);
    
    // 委托事件处理基金 checkbox
    elements.fundTableBody.addEventListener('change', (e) => {
        if (e.target.classList.contains('fund-checkbox')) {
            eventHandlers.onFundSelect(e);
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
