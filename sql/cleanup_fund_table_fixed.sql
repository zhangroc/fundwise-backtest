-- 清理fund表结构，使其匹配Python代码中的字段

-- 1. 检查并删除外键约束
SET @fund_nav_fk = (
    SELECT CONSTRAINT_NAME 
    FROM information_schema.KEY_COLUMN_USAGE 
    WHERE TABLE_SCHEMA = DATABASE() 
    AND TABLE_NAME = 'fund_nav' 
    AND COLUMN_NAME = 'fund_code' 
    AND REFERENCED_TABLE_NAME = 'fund'
);

SET @portfolio_holding_fk = (
    SELECT CONSTRAINT_NAME 
    FROM information_schema.KEY_COLUMN_USAGE 
    WHERE TABLE_SCHEMA = DATABASE() 
    AND TABLE_NAME = 'portfolio_holding' 
    AND COLUMN_NAME = 'fund_code' 
    AND REFERENCED_TABLE_NAME = 'fund'
);

-- 删除外键约束（如果存在）
SET @drop_fk_sql = '';

IF @fund_nav_fk IS NOT NULL THEN
    SET @drop_fk_sql = CONCAT('ALTER TABLE fund_nav DROP FOREIGN KEY ', @fund_nav_fk, ';');
    PREPARE stmt FROM @drop_fk_sql;
    EXECUTE stmt;
    DEALLOCATE PREPARE stmt;
END IF;

IF @portfolio_holding_fk IS NOT NULL THEN
    SET @drop_fk_sql = CONCAT('ALTER TABLE portfolio_holding DROP FOREIGN KEY ', @portfolio_holding_fk, ';');
    PREPARE stmt FROM @drop_fk_sql;
    EXECUTE stmt;
    DEALLOCATE PREPARE stmt;
END IF;

-- 2. 删除fund表并重新创建
DROP TABLE IF EXISTS fund;

-- 3. 重新创建fund表
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

-- 4. 重新建立外键关系
ALTER TABLE fund_nav ADD CONSTRAINT fund_nav_ibfk_1 FOREIGN KEY (fund_code) REFERENCES fund(fund_code) ON DELETE CASCADE;
ALTER TABLE portfolio_holding ADD CONSTRAINT portfolio_holding_ibfk_2 FOREIGN KEY (fund_code) REFERENCES fund(fund_code) ON DELETE CASCADE;

-- 5. 验证表结构
DESCRIBE fund;