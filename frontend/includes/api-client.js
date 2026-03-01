/**
 * 统一的 API 请求工具类
 * 封装所有后端API调用，统一处理错误、loading、认证等
 * 
 * 使用说明：
 * - 本地开发：直接使用，会自动识别 localhost
 * - 远程测试：需要配置 BACKEND_URL 或使用nginx反向代理
 */

// 后端地址配置（远程测试时可修改此值）
const BACKEND_URL = '';  // 如: 'http://106.12.165.68:3389' 或 ''（使用反向代理）

class ApiClient {
    constructor() {
        // 根据环境自动判断API基础地址
        this.baseURL = this._getBaseURL();
    }
    
    _getBaseURL() {
        // 如果手动配置了后端地址，优先使用
        if (BACKEND_URL) {
            return BACKEND_URL;
        }
        
        const hostname = window.location.hostname;
        
        // 本地开发环境
        if (hostname === 'localhost' || hostname === '127.0.0.1') {
            return 'http://localhost:3389';
        }
        
        // 局域网访问 - 使用当前主机名+3389端口
        if (hostname.startsWith('192.168.') || hostname.startsWith('10.') || hostname.startsWith('172.')) {
            return `http://${hostname}:3389`;
        }
        
        // 远程服务器 - 使用相对路径（需要nginx反向代理）
        return '';
    }
    
    /**
     * 发送API请求
     * @param {string} endpoint - API端点路径
     * @param {object} options - 请求选项
     * @returns {Promise} - 返回数据或抛出错误
     */
    async request(endpoint, options = {}) {
        const {
            method = 'GET',
            body = null,
            headers = {},
            showLoading = true,
            showError = true,
            retry = 0
        } = options;
        
        // 显示loading (如果有回调)
        if (showLoading && typeof options.onLoading === 'function') {
            options.onLoading(true);
        }
        
        const url = `${this.baseURL}${endpoint}`;
        const config = {
            method,
            headers: {
                'Content-Type': 'application/json',
                ...headers
            }
        };
        
        if (body && (method === 'POST' || method === 'PUT' || method === 'PATCH')) {
            config.body = JSON.stringify(body);
        }
        
        try {
            const response = await fetch(url, config);
            
            // 处理HTTP错误
            if (!response.ok) {
                throw new Error(`HTTP ${response.status}: ${response.statusText}`);
            }
            
            const data = await response.json();
            
            // 隐藏loading
            if (showLoading && typeof options.onLoading === 'function') {
                options.onLoading(false);
            }
            
            // 检查业务错误
            if (data.success === false) {
                throw new Error(data.message || '请求失败');
            }
            
            return data;
            
        } catch (error) {
            // 隐藏loading
            if (showLoading && typeof options.onLoading === 'function') {
                options.onLoading(false);
            }
            
            // 重试机制
            if (retry > 0) {
                console.log(`API请求失败，${retry}秒后重试...`);
                await new Promise(resolve => setTimeout(resolve, 1000));
                return this.request(endpoint, { ...options, retry: retry - 1 });
            }
            
            // 显示错误提示
            if (showError) {
                console.error('API请求错误:', error);
                this._showError(error.message || '网络错误，请稍后重试');
            }
            
            throw error;
        }
    }
    
    /**
     * GET 请求
     */
    async get(endpoint, options = {}) {
        return this.request(endpoint, { ...options, method: 'GET' });
    }
    
    /**
     * POST 请求
     */
    async post(endpoint, body, options = {}) {
        return this.request(endpoint, { ...options, method: 'POST', body });
    }
    
    /**
     * PUT 请求
     */
    async put(endpoint, body, options = {}) {
        return this.request(endpoint, { ...options, method: 'PUT', body });
    }
    
    /**
     * DELETE 请求
     */
    async delete(endpoint, options = {}) {
        return this.request(endpoint, { ...options, method: 'DELETE' });
    }
    
    /**
     * 显示错误提示
     */
    _showError(message) {
        // 可以替换为自定义Toast组件
        alert(message);
    }
    
    // ==================== 业务API封装 ====================
    
    // 基金相关
    funds = {
        /**
         * 获取基金列表
         */
        list: (params = {}) => {
            const query = new URLSearchParams(params).toString();
            return api.get(`/api/v1/funds/list${query ? '?' + query : ''}`);
        },
        
        /**
         * 获取基金详情
         */
        detail: (code) => {
            return api.get(`/api/v1/funds/${code}`);
        },
        
        /**
         * 基金筛选
         */
        screen: (params = {}) => {
            const query = new URLSearchParams(params).toString();
            return api.get(`/api/v1/funds/screen${query ? '?' + query : ''}`);
        },
        
        /**
         * 基金搜索
         */
        search: (keyword) => {
            return api.get(`/api/v1/funds/search?keyword=${encodeURIComponent(keyword)}`);
        }
    };
    
    // 组合相关
    portfolio = {
        /**
         * 获取组合列表
         */
        list: () => {
            return api.get('/api/v1/portfolio/list');
        },
        
        /**
         * 获取组合详情
         */
        detail: (id) => {
            return api.get(`/api/v1/portfolio/${id}`);
        },
        
        /**
         * 创建组合
         */
        create: (data) => {
            return api.post('/api/v1/portfolio/create', data);
        },
        
        /**
         * 更新组合
         */
        update: (id, data) => {
            return api.put(`/api/v1/portfolio/${id}`, data);
        },
        
        /**
         * 删除组合
         */
        delete: (id) => {
            return api.delete(`/api/v1/portfolio/delete', { id });
        }
    };
    
    // 回测相关
    backtest = {
        /**
         * 运行回测
         */
        run: (data) => {
            return api.post('/api/backtest/run', data);
        },
        
        /**
         * 获取回测结果
         */
        result: (id) => {
            return api.get(`/api/backtest/result/${id}`);
        },
        
        /**
         * 获取回测历史
         */
        history: () => {
            return api.get('/api/backtest/history');
        }
    };
    
    // 智能推荐
    recommend = {
        /**
         * 获取推荐组合
         */
        portfolio: (data) => {
            return api.post('/api/recommend', data);
        }
    };
    
    // 组合对比
    compare = {
        /**
         * 获取对比数据
         */
        portfolios: () => {
            return api.get('/api/v1/portfolio/list');
        },
        
        /**
         * 执行对比
         */
        compare: (ids) => {
            return api.post('/api/compare', { ids });
        }
    };
    
    // 统计
    stats = {
        /**
         * 获取概览数据
         */
        overview: () => {
            return api.get('/api/v1/stats/overview');
        }
    };
}

// 创建全局API实例
window.api = new ApiClient();

// 兼容旧代码 - 导出API_BASE_URL和API对象供旧代码使用
window.API_BASE_URL = window.api.baseURL;
window.API = {
    funds: window.api.funds,
    portfolio: window.api.portfolio,
    backtest: window.api.backtest,
    recommend: window.api.recommend,
    compare: window.api.compare,
    stats: window.api.stats
};