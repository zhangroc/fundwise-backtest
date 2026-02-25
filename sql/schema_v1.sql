-- fundwise_backtest_schema_v1.sql
-- 数据库建表脚本 (MySQL 8.0+)
-- 版本: 1.0
-- 说明: 包含基金信息、组合、回测结果等核心表

-- 创建数据库 (请根据实际情况修改)
CREATE DATABASE IF NOT EXISTS fundwise_backtest DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE fundwise_backtest;

-- 1. 基金基础信息表
CREATE TABLE fund (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '自增主键',
    code VARCHAR(10) NOT NULL UNIQUE COMMENT '基金代码，唯一标识，如 000001',
    name VARCHAR(255) NOT NULL COMMENT '基金全称',
    type VARCHAR(50) COMMENT '基金类型: 股票型, 混合型, 债券型, 货币型, QDII等',
    company VARCHAR(100) COMMENT '基金管理公司',
    establishment_date DATE COMMENT '成立日期',
    latest_size DECIMAL(15, 2) COMMENT '最新规模 (单位: 亿元)',
    is_index_fund BOOLEAN DEFAULT FALSE COMMENT '是否为指数基金',
    underlying_index VARCHAR(100) COMMENT '跟踪的指数名称，如"沪深300"',
    tracking_error DECIMAL(8,4) COMMENT '年化跟踪误差',
    management_fee_rate DECIMAL(6,4) COMMENT '管理费率',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '记录创建时间',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '记录更新时间',
    INDEX idx_fund_code (code),
    INDEX idx_fund_type (type),
    INDEX idx_is_index (is_index_fund)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='基金基础信息维度表';

-- 2. 基金历史净值表 (核心回测数据)
CREATE TABLE fund_nav (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '自增主键',
    fund_id BIGINT NOT NULL COMMENT '关联基金ID',
    nav_date DATE NOT NULL COMMENT '净值日期',
    accumulated_nav DECIMAL(12, 6) NOT NULL COMMENT '累计净值 (用于复权收益计算)',
    unit_nav DECIMAL(10, 4) COMMENT '单位净值',
    daily_return DECIMAL(8, 4) COMMENT '日收益率 (可冗余存储，方便计算)',
    data_source VARCHAR(20) DEFAULT 'akshare' COMMENT '数据来源',
    snapshot_date DATE COMMENT '数据获取的快照日期（我们哪天抓的）',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '记录创建时间',
    FOREIGN KEY (fund_id) REFERENCES fund(id) ON DELETE CASCADE,
    UNIQUE KEY uk_fund_nav_date_source (fund_id, nav_date, snapshot_date) COMMENT '同一基金、同一日期、同一快照日期的数据唯一',
    INDEX idx_nav_date (nav_date) COMMENT '按日期范围查询索引',
    INDEX idx_snapshot_date (snapshot_date) COMMENT '按快照日期查询'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='基金历史净值事实表（支持多版本）';

-- 3. 投资组合定义表
CREATE TABLE portfolio (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '自增主键',
    name VARCHAR(255) NOT NULL COMMENT '组合名称',
    description TEXT COMMENT '组合描述',
    risk_level ENUM('保守', '稳健', '积极', '自定义') DEFAULT '自定义' COMMENT '风险等级',
    is_template BOOLEAN DEFAULT FALSE COMMENT '是否为系统预设模板',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户投资组合定义';

-- 4. 组合持仓表 (多对多关系)
CREATE TABLE portfolio_holding (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '自增主键',
    portfolio_id BIGINT NOT NULL COMMENT '关联组合ID',
    fund_id BIGINT NOT NULL COMMENT '关联基金ID',
    target_weight DECIMAL(5, 4) NOT NULL COMMENT '目标权重 (0~1, 如0.4)',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '添加时间',
    FOREIGN KEY (portfolio_id) REFERENCES portfolio(id) ON DELETE CASCADE,
    FOREIGN KEY (fund_id) REFERENCES fund(id),
    UNIQUE KEY uk_portfolio_fund (portfolio_id, fund_id) COMMENT '同一组合中同一基金唯一',
    INDEX idx_portfolio_id (portfolio_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='组合持仓明细';

-- 5. 回测结果摘要表
CREATE TABLE backtest_result (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '自增主键',
    portfolio_id BIGINT NOT NULL COMMENT '关联的组合ID',
    backtest_name VARCHAR(255) COMMENT '回测名称',
    start_date DATE NOT NULL COMMENT '回测开始日期',
    end_date DATE NOT NULL COMMENT '回测结束日期',
    initial_capital DECIMAL(15, 2) NOT NULL COMMENT '初始本金',
    final_value DECIMAL(15, 2) NOT NULL COMMENT '最终市值',
    total_return DECIMAL(10, 4) NOT NULL COMMENT '总收益率 (小数表示)',
    annualized_return DECIMAL(10, 4) COMMENT '年化收益率',
    max_drawdown DECIMAL(10, 4) COMMENT '最大回撤 (正数表示，如0.15表示15%)',
    sharpe_ratio DECIMAL(10, 4) COMMENT '夏普比率',
    benchmark_return DECIMAL(10, 4) COMMENT '基准指数同期收益率',
    parameters JSON COMMENT '回测参数JSON快照，如: {"rebalance": "quarterly", "fee_rate": 0.001, "index_fund_only": false}',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '回测运行时间',
    FOREIGN KEY (portfolio_id) REFERENCES portfolio(id) ON DELETE CASCADE,
    INDEX idx_portfolio_created (portfolio_id, created_at) COMMENT '查询组合的历史回测'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='回测结果摘要';

-- 6. 回测每日明细表 (可选，按需创建)
CREATE TABLE backtest_daily_detail (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '自增主键',
    backtest_result_id BIGINT NOT NULL COMMENT '关联的回测结果ID',
    detail_date DATE NOT NULL COMMENT '日期',
    portfolio_value DECIMAL(15, 2) NOT NULL COMMENT '组合当日总市值',
    daily_return DECIMAL(10, 6) COMMENT '组合日收益率',
    drawdown DECIMAL(10, 6) COMMENT '当日回撤 (距前高点的跌幅)',
    FOREIGN KEY (backtest_result_id) REFERENCES backtest_result(id) ON DELETE CASCADE,
    INDEX idx_backtest_date (backtest_result_id, detail_date) COMMENT '快速查询某次回测的每日数据'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='回测每日明细 (用于绘制净值曲线)';

-- 创建完成后可以插入一些系统预设的组合模板 (示例)
-- INSERT INTO portfolio (name, description, risk_level, is_template) VALUES
-- ('保守型-纯债组合', '以债券型基金为主的低风险组合', '保守', TRUE),
-- ('稳健型-股债平衡', '经典的50%股票+50%债券平衡组合', '稳健', TRUE),
-- ('积极型-成长组合', '以股票型基金为主，追求较高增长', '积极', TRUE);

-- 提示：运行此脚本前请确保MySQL用户有创建数据库和表的权限。
-- 可以使用命令: mysql -u用户名 -p < sql/schema_v1.sql