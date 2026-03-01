// API 配置
// 生产环境使用相对路径，通过反向代理访问
// 开发环境可设置 API_BASE_URL 为实际后端地址
(function() {
    // 使用相对路径或动态判断
    // 如果存在 /api 路径的反向代理，则用空字符串
    // 否则需要指向实际后端地址
    const currentOrigin = window.location.origin;
    const isLocalDev = currentOrigin.includes('localhost') || currentOrigin.includes('127.0.0.1');
    
    // 本地开发用localhost:3389，生产环境应该配置反向代理
    window.API_BASE_URL = isLocalDev ? 'http://localhost:3389' : '';
    
    window.API = {
        // 基金相关
        funds: {
            list: '/api/v1/funds/list',
            detail: '/api/v1/funds',
            screen: '/api/v1/funds/screen',
            search: '/api/v1/funds/search'
        },
        // 组合相关
        portfolio: {
            list: '/api/v1/portfolio/list',
            detail: '/api/v1/portfolio',
            create: '/api/v1/portfolio/create',
            update: '/api/v1/portfolio/update',
            delete: '/api/v1/portfolio/delete'
        },
        // 回测相关
        backtest: {
            run: '/api/backtest/run',
            result: '/api/backtest/result',
            history: '/api/backtest/history'
        },
        // 组合对比
        compare: {
            portfolios: '/api/v1/portfolio/list',
            compare: '/api/compare'
        },
        // 智能推荐
        recommend: {
            funds: '/api/recommend',
            portfolio: '/api/recommend'
        },
        // 首页统计
        stats: {
            overview: '/api/v1/stats/overview'
        }
    };
})();