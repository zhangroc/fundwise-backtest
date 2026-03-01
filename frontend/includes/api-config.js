// API 配置
// 生产环境使用相对路径，通过反向代理访问
// 开发环境可设置 API_BASE_URL 为实际后端地址
(function() {
    // 使用相对路径，通过 Nginx 反向代理或同源访问
    window.API_BASE_URL = '';
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
            funds: '/api/v1/recommend/funds',
            portfolio: '/api/v1/recommend/portfolio'
        },
        // 首页统计
        stats: {
            overview: '/api/v1/stats/overview'
        }
    };
})();