# FundWise 基智回测 - 开发计划

## 项目概述

**产品名称**：基智回测（FundWise Backtest）
**版本**：v1.0
**开发周期**：预计 2 周

---

## 一、产品功能清单

### 已完成功能 ✅

| 功能模块 | 状态 | 说明 |
|---------|------|------|
| 基金筛选 | ✅ | 多维度筛选，分页查询 |
| 基金详情 | ✅ | 基本信息、业绩指标、净值图表 |
| 智能推荐 | ✅ | 基于偏好推荐组合 |
| 首页展示 | ✅ | 统计卡片、精选基金池 |

### 待开发功能 📋

| 功能模块 | 优先级 | 预计工时 | 说明 |
|---------|--------|---------|------|
| 同类对比 | P0 | 4h | 同类型基金对比分析 |
| 组合管理完善 | P0 | 4h | 组合CRUD、权重调整 |
| 回测引擎 | P0 | 6h | 真实历史数据回测 |
| 组合详情页 | P1 | 3h | 组合分析与展示 |
| 基金对比页 | P1 | 3h | 多基金对比功能 |
| 数据导出 | P2 | 2h | Excel导出筛选结果 |
| 定投计算 | P2 | 3h | 定投收益模拟 |

---

## 二、开发计划

### 第一阶段：核心功能完善（Day 1-3）

#### Day 1：同类对比 + 组合管理
- [ ] 开发同类对比API（`/api/v1/funds/{code}/compare`）
- [ ] 创建同类对比页面 `compare.html`
- [ ] 完善组合管理API（增删改查）
- [ ] 测试组合管理功能

#### Day 2：回测引擎
- [ ] 开发回测核心算法
- [ ] 实现净值数据查询优化
- [ ] 计算回测指标（收益、回撤、夏普等）
- [ ] 回测API测试

#### Day 3：组合详情 + 基金对比
- [ ] 组合详情页面 `portfolio-detail.html`
- [ ] 基金对比页面（支持多选对比）
- [ ] 前后端联调测试

### 第二阶段：功能增强（Day 4-5）

#### Day 4：数据导出 + 定投计算
- [ ] Excel导出功能
- [ ] 定投收益计算器
- [ ] 数据可视化优化

#### Day 5：端到端测试
- [ ] 功能测试用例编写
- [ ] 端到端测试执行
- [ ] Bug修复
- [ ] 性能优化

### 第三阶段：文档与部署（Day 6-7）

#### Day 6：文档更新
- [ ] 产品使用手册
- [ ] API接口文档
- [ ] 部署文档
- [ ] 用户指南

#### Day 7：最终部署
- [ ] 生产环境部署
- [ ] 监控配置
- [ ] 备份策略
- [ ] 上线验收

---

## 三、测试计划

### 3.1 单元测试

| 模块 | 测试项 | 覆盖率目标 |
|------|--------|-----------|
| FundController | 筛选、详情、搜索 | 80% |
| FundDetailController | 详情、净值、业绩 | 80% |
| PortfolioController | CRUD操作 | 80% |
| BacktestService | 回测计算 | 90% |

### 3.2 集成测试

| 场景 | 测试步骤 |
|------|---------|
| 基金筛选流程 | 筛选 → 查看详情 → 添加组合 |
| 组合回测流程 | 创建组合 → 添加基金 → 执行回测 |
| 同类对比流程 | 查看详情 → 发起对比 → 查看结果 |

### 3.3 端到端测试

```
测试用例 1：完整投资流程
1. 访问首页 → 查看统计数据
2. 进入筛选页 → 设置条件筛选
3. 查看基金详情 → 分析业绩
4. 添加到组合 → 创建新组合
5. 执行回测 → 查看结果
6. 导出报告

测试用例 2：组合管理流程
1. 创建新组合
2. 搜索基金添加
3. 调整权重
4. 保存组合
5. 执行回测
6. 对比不同组合

测试用例 3：对比分析流程
1. 筛选多只基金
2. 勾选对比
3. 查看对比结果
4. 分析差异
5. 选择最优
```

### 3.4 性能测试

| 指标 | 目标值 |
|------|--------|
| 筛选API响应 | < 500ms |
| 详情API响应 | < 300ms |
| 回测API响应 | < 3s |
| 页面加载 | < 2s |

---

## 四、API 接口设计

### 4.1 基金相关

```
GET  /api/v1/funds/screen       # 基金筛选
GET  /api/v1/funds/{code}       # 基金基本信息
GET  /api/v1/funds/{code}/detail   # 完整详情
GET  /api/v1/funds/{code}/nav      # 净值历史
GET  /api/v1/funds/{code}/performance # 业绩指标
GET  /api/v1/funds/{code}/compare   # 同类对比
GET  /api/v1/funds/search        # 基金搜索
```

### 4.2 组合相关

```
GET    /api/portfolios           # 组合列表
POST   /api/portfolios           # 创建组合
GET    /api/portfolios/{id}      # 组合详情
PUT    /api/portfolios/{id}      # 更新组合
DELETE /api/portfolios/{id}      # 删除组合
POST   /api/portfolios/{id}/holdings  # 添加持仓
DELETE /api/portfolios/{id}/holdings/{fundCode} # 删除持仓
```

### 4.3 回测相关

```
POST /api/backtest/run          # 执行回测
GET  /api/backtest/results      # 回测历史
```

### 4.4 推荐相关

```
POST /api/recommend             # 智能推荐
```

---

## 五、数据库设计

### 现有表结构

```sql
-- 基金表
fund (fund_code, fund_name, fund_type, risk_level, total_assets, ...)

-- 净值表
fund_nav (id, fund_code, nav_date, nav, accumulated_nav, daily_return, ...)

-- 组合表
portfolio (id, name, description, risk_level, is_template, ...)

-- 持仓表
portfolio_holding (id, portfolio_id, fund_code, fund_name, target_weight, ...)
```

### 待新增表

```sql
-- 回测结果表
CREATE TABLE backtest_result (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    portfolio_id BIGINT,
    start_date DATE,
    end_date DATE,
    initial_capital DECIMAL(15,2),
    final_value DECIMAL(15,2),
    total_return DECIMAL(10,4),
    annualized_return DECIMAL(10,4),
    max_drawdown DECIMAL(10,4),
    sharpe_ratio DECIMAL(10,4),
    created_at TIMESTAMP
);

-- 用户偏好表（可选）
CREATE TABLE user_preference (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id VARCHAR(50),
    risk_level VARCHAR(20),
    investment_period INT,
    prefer_index_fund BOOLEAN,
    created_at TIMESTAMP
);
```

---

## 六、部署架构

```
┌─────────────────────────────────────────────────┐
│                   Nginx (可选)                   │
│              反向代理 + 静态资源                  │
├─────────────────────────────────────────────────┤
│                                                 │
│  ┌─────────────┐     ┌─────────────────────┐   │
│  │   前端服务   │     │      后端服务        │   │
│  │   端口 80    │────▶│     端口 3389        │   │
│  │   Python    │     │   Java Spring Boot  │   │
│  └─────────────┘     └─────────────────────┘   │
│                              │                  │
│                              ▼                  │
│                    ┌─────────────────────┐     │
│                    │      MySQL 8.0       │     │
│                    │      端口 3306        │     │
│                    └─────────────────────┘     │
└─────────────────────────────────────────────────┘
```

---

## 七、监控与运维

### 7.1 健康检查

```bash
# 后端健康检查
curl http://localhost:3389/api/health

# 前端健康检查
curl http://localhost/

# 数据库健康检查
mysql -u fundwise -p -e "SELECT 1"
```

### 7.2 日志监控

- 后端日志：`logs/backend.log`
- 前端日志：`logs/frontend.log`
- 监控脚本：`scripts/monitor.sh`

### 7.3 性能监控

```bash
# 运行监控
./scripts/monitor.sh check

# 生成报告
./scripts/monitor.sh report
```

---

## 八、里程碑

| 里程碑 | 时间 | 交付物 |
|--------|------|--------|
| M1: 核心功能完成 | Day 3 | 同类对比、回测引擎、组合管理 |
| M2: 功能增强完成 | Day 5 | 导出、定投、测试用例 |
| M3: 文档与部署 | Day 7 | 完整文档、生产部署 |

---

## 九、风险与应对

| 风险 | 影响 | 应对措施 |
|------|------|---------|
| 数据质量问题 | 中 | 数据清洗脚本、异常处理 |
| 性能瓶颈 | 中 | 索引优化、缓存策略 |
| 并发问题 | 低 | 连接池配置、限流 |

---

## 十、下一步行动

**当前进度**：基金筛选 ✅、基金详情 ✅、智能推荐 ✅

**下一步**：开发同类对比功能

1. 完善对比API返回数据
2. 创建对比页面 `compare.html`
3. 实现多基金对比图表
4. 端到端测试

---

*文档版本：v1.0*
*更新时间：2026-02-28*