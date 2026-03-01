/**
 * API Request Utility
 * Unified API calls
 */

// Backend URL config (set empty for reverse proxy, or set explicit URL like 'http://106.12.165.68:3389')
const BACKEND_URL = '';

class ApiClient {
    constructor() {
        this.baseURL = this._getBaseURL();
    }
    
    _getBaseURL() {
        if (BACKEND_URL) {
            return BACKEND_URL;
        }
        
        const hostname = window.location.hostname;
        
        if (hostname === 'localhost' || hostname === '127.0.0.1') {
            return 'http://localhost:3389';
        }
        
        if (hostname.startsWith('192.168.') || hostname.startsWith('10.') || hostname.startsWith('172.')) {
            return 'http://' + hostname + ':3389';
        }
        
        return '';
    }
    
    async request(endpoint, options = {}) {
        const {
            method = 'GET',
            body = null,
            headers = {},
            showLoading = true,
            showError = true,
            retry = 0
        } = options;
        
        if (showLoading && typeof options.onLoading === 'function') {
            options.onLoading(true);
        }
        
        const url = this.baseURL + endpoint;
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
            
            if (!response.ok) {
                throw new Error('HTTP ' + response.status + ': ' + response.statusText);
            }
            
            const data = await response.json();
            
            if (showLoading && typeof options.onLoading === 'function') {
                options.onLoading(false);
            }
            
            if (data.success === false) {
                throw new Error(data.message || 'Request failed');
            }
            
            return data;
            
        } catch (error) {
            if (showLoading && typeof options.onLoading === 'function') {
                options.onLoading(false);
            }
            
            if (retry > 0) {
                console.log('API request failed, retry in ' + retry + 's...');
                await new Promise(resolve => setTimeout(resolve, 1000));
                return this.request(endpoint, { ...options, retry: retry - 1 });
            }
            
            if (showError) {
                console.error('API Error:', error);
                alert(error.message || 'Network error, please try again');
            }
            
            throw error;
        }
    }
    
    async get(endpoint, options = {}) {
        return this.request(endpoint, { ...options, method: 'GET' });
    }
    
    async post(endpoint, body, options = {}) {
        return this.request(endpoint, { ...options, method: 'POST', body });
    }
    
    async put(endpoint, body, options = {}) {
        return this.request(endpoint, { ...options, method: 'PUT', body });
    }
    
    async delete(endpoint, options = {}) {
        return this.request(endpoint, { ...options, method: 'DELETE' });
    }
    
    // ==================== Business APIs ====================
    
    funds = {
        list: (params = {}) => {
            const query = new URLSearchParams(params).toString();
            return api.get('/api/v1/funds/list' + (query ? '?' + query : ''));
        },
        
        detail: (code) => {
            return api.get('/api/v1/funds/' + code);
        },
        
        screen: (params = {}) => {
            const query = new URLSearchParams(params).toString();
            return api.get('/api/v1/funds/screen' + (query ? '?' + query : ''));
        },
        
        search: (keyword) => {
            return api.get('/api/v1/funds/search?keyword=' + encodeURIComponent(keyword));
        }
    };
    
    portfolio = {
        list: () => {
            return api.get('/api/v1/portfolio/list');
        },
        
        detail: (id) => {
            return api.get('/api/v1/portfolio/' + id);
        },
        
        create: (data) => {
            return api.post('/api/v1/portfolio/create', data);
        },
        
        update: (id, data) => {
            return api.put('/api/v1/portfolio/' + id, data);
        },
        
        delete: (id) => {
            return api.delete('/api/v1/portfolio/delete', { id: id });
        }
    };
    
    backtest = {
        run: (data) => {
            return api.post('/api/backtest/run', data);
        },
        
        result: (id) => {
            return api.get('/api/backtest/result/' + id);
        },
        
        history: () => {
            return api.get('/api/backtest/history');
        }
    };
    
    recommend = {
        portfolio: (data) => {
            return api.post('/api/recommend', data);
        }
    };
    
    compare = {
        portfolios: () => {
            return api.get('/api/v1/portfolio/list');
        },
        
        compare: (ids) => {
            return api.post('/api/compare', { ids: ids });
        }
    };
    
    stats = {
        overview: () => {
            return api.get('/api/v1/stats/overview');
        }
    };
}

// Create global API instance
window.api = new ApiClient();

// Compatible with old code
window.API_BASE_URL = window.api.baseURL;
window.API = {
    funds: window.api.funds,
    portfolio: window.api.portfolio,
    backtest: window.api.backtest,
    recommend: window.api.recommend,
    compare: window.api.compare,
    stats: window.api.stats
};