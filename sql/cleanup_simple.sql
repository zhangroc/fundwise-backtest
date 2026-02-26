-- 简单方式：删除整个数据库并重新创建

-- 1. 删除所有表（级联删除）
SET FOREIGN_KEY_CHECKS = 0;

DROP TABLE IF EXISTS backtest_daily_detail;
DROP TABLE IF EXISTS backtest_result;
DROP TABLE IF EXISTS portfolio_holding;
DROP TABLE IF EXISTS portfolio;
DROP TABLE IF EXISTS fund_nav;
DROP TABLE IF EXISTS fund;

SET FOREIGN_KEY_CHECKS = 1;

-- 2. 重新创建fund表
CREATE TABLE fund (
    fund_code VARCHAR(10) NOT NULL PRIMARY KEY,
    fund_name VARCHAR(100) NOT NULL,
    fund_type VARCHAR(20),
    fund_company VARCHAR(50),
    establishment_date DATE,
    is_index_fund TINYINT(1) DEFAULT 0,
    risk_level VARCHAR(10),
    status VARCHAR(10) DEFAULT 'active',
    management_fee_rate DOUBLE,
    custodian_fee_rate DOUBLE,
    total_assets DOUBLE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_fund_type (fund_type),
    INDEX idx_risk_level (risk_level),
    INDEX idx_is_index_fund (is_index_fund)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 3. 创建fund_nav表
CREATE TABLE fund_nav (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    fund_code VARCHAR(10) NOT NULL,
    nav_date DATE NOT NULL,
    nav DOUBLE NOT NULL,
    accumulated_nav DOUBLE,
    daily_return DOUBLE,
    snapshot_date DATE NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE INDEX uniq_fund_nav_date (fund_code, nav_date, snapshot_date),
    FOREIGN KEY (fund_code) REFERENCES fund(fund_code) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 4. 创建portfolio表
CREATE TABLE portfolio (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    portfolio_name VARCHAR(100) NOT NULL,
    description TEXT,
    risk_level VARCHAR(10),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 5. 创建portfolio_holding表
CREATE TABLE portfolio_holding (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    portfolio_id BIGINT NOT NULL,
    fund_code VARCHAR(10) NOT NULL,
    weight DOUBLE NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE INDEX uniq_portfolio_fund (portfolio_id, fund_code),
    FOREIGN KEY (portfolio_id) REFERENCES portfolio(id) ON DELETE CASCADE,
    FOREIGN KEY (fund_code) REFERENCES fund(fund_code) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 6. 验证表结构
DESCRIBE fund;
DESCRIBE fund_nav;