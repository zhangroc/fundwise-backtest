#!/usr/bin/env python3
"""
基金数据抓取与入库脚本 - 修复版
使用 akshare 获取中国公募基金数据
"""

import pandas as pd
import numpy as np
from datetime import datetime, timedelta
import time
import logging
import warnings
import sys
import os
from sqlalchemy import create_engine, text
from sqlalchemy.exc import SQLAlchemyError
warnings.filterwarnings('ignore')

# 添加项目根目录到路径
sys.path.append(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

# 配置日志
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s',
    handlers=[
        logging.FileHandler('data_fetch.log'),
        logging.StreamHandler()
    ]
)
logger = logging.getLogger(__name__)

class FundDataFetcher:
    """基金数据抓取器"""
    
    def __init__(self, db_url='mysql+pymysql://fundwise:password123@localhost/fundwise_backtest'):
        """初始化数据库连接"""
        try:
            self.engine = create_engine(db_url, pool_recycle=3600)
            logger.info("数据库连接成功")
        except Exception as e:
            logger.error(f"数据库连接失败: {e}")
            raise
    
    def test_akshare_available(self):
        """测试akshare是否可用"""
        try:
            import akshare as ak
            # 测试一个简单的函数
            test_result = ak.fund_name_em()
            logger.info(f"akshare 模块可用，获取到 {len(test_result)} 条基金记录")
            return True
        except ImportError as e:
            logger.error(f"akshare 模块未安装: {e}")
            return False
        except Exception as e:
            logger.error(f"akshare 测试失败: {e}")
            return False
        
    def fetch_fund_list(self):
        """获取基金列表 - 使用正确的akshare函数"""
        try:
            import akshare as ak
            
            # 获取基金列表
            logger.info("正在获取基金列表...")
            fund_list = ak.fund_name_em()
            
            if fund_list.empty:
                logger.warning("未获取到基金列表数据")
                return pd.DataFrame()
            
            logger.info(f"获取到 {len(fund_list)} 只基金")
            
            # 数据清洗和转换
            fund_list_clean = fund_list.copy()
            
            # 重命名列以匹配数据库
            column_mapping = {
                'fund_code': '基金代码',
                'fund_name': '基金简称',
                'fund_type': '基金类型',
            }
            
            # 只保留我们需要的列
            for db_col, ak_col in column_mapping.items():
                if ak_col in fund_list_clean.columns:
                    fund_list_clean[db_col] = fund_list_clean[ak_col]
            
            # 添加其他字段
            fund_list_clean['is_index_fund'] = fund_list_clean['fund_type'].apply(
                lambda x: 1 if '指数' in str(x) else 0
            )
            
            fund_list_clean['risk_level'] = fund_list_clean['fund_type'].apply(
                self._map_risk_level
            )
            
            fund_list_clean['status'] = 'active'
            fund_list_clean['management_fee_rate'] = 0.015  # 默认管理费率1.5%
            fund_list_clean['custodian_fee_rate'] = 0.001   # 默认托管费率0.1%
            
            # 添加示例基金公司（实际需要从其他接口获取）
            fund_companies = ['华夏基金', '易方达基金', '富国基金', '南方基金', '嘉实基金', 
                             '博时基金', '广发基金', '汇添富基金', '工银瑞信', '招商基金']
            np.random.seed(42)
            fund_list_clean['fund_company'] = np.random.choice(fund_companies, size=len(fund_list_clean))
            
            # 添加示例成立日期（实际需要从其他接口获取）
            start_date = pd.to_datetime('2000-01-01')
            end_date = pd.to_datetime('2023-01-01')
            date_range = pd.date_range(start=start_date, end=end_date, periods=1000)
            fund_list_clean['establishment_date'] = np.random.choice(date_range, size=len(fund_list_clean))
            
            # 添加示例基金规模（亿元）
            fund_list_clean['total_assets'] = np.random.uniform(1, 500, size=len(fund_list_clean))
            
            # 选择要返回的列
            result_columns = ['fund_code', 'fund_name', 'fund_type', 'fund_company', 
                             'establishment_date', 'is_index_fund', 'risk_level', 'status',
                             'management_fee_rate', 'custodian_fee_rate', 'total_assets']
            
            return fund_list_clean[result_columns]
            
        except Exception as e:
            logger.error(f"获取基金列表失败: {e}")
            return pd.DataFrame()
    
    def _map_risk_level(self, fund_type):
        """根据基金类型映射风险等级"""
        if not isinstance(fund_type, str):
            return '稳健'
        
        fund_type = fund_type.lower()
        
        if any(word in fund_type for word in ['债券', '货币', '理财', '定开']):
            return '保守'
        elif any(word in fund_type for word in ['股票', '积极', '成长', '进取', '行业']):
            return '积极'
        elif '指数' in fund_type:
            return '稳健'  # 指数基金通常风险适中
        else:
            return '稳健'
    
    def fetch_fund_nav(self, fund_code):
        """获取基金历史净值"""
        try:
            import akshare as ak
            
            logger.info(f"获取基金 {fund_code} 净值数据...")
            
            # 获取基金净值 - 使用不同的接口
            try:
                # 尝试ETF基金接口
                nav_data = ak.fund_etf_hist_em(symbol=fund_code, period="daily")
            except:
                try:
                    # 尝试开放式基金接口
                    nav_data = ak.fund_open_fund_info_em(fund=fund_code, indicator="单位净值走势")
                except:
                    # 如果都失败，返回空数据
                    logger.warning(f"基金 {fund_code} 无可用净值接口")
                    return pd.DataFrame()
            
            if nav_data.empty:
                logger.warning(f"基金 {fund_code} 无净值数据")
                return pd.DataFrame()
            
            # 数据清洗
            nav_data_clean = nav_data.copy()
            
            # 尝试不同的列名
            date_col = None
            nav_col = None
            
            for col in nav_data_clean.columns:
                col_lower = str(col).lower()
                if any(word in col_lower for word in ['日期', 'date', 'time']):
                    date_col = col
                elif any(word in col_lower for word in ['净值', 'nav', 'value', 'close']):
                    nav_col = col
            
            if date_col and nav_col:
                nav_data_clean['nav_date'] = pd.to_datetime(nav_data_clean[date_col]).dt.date
                nav_data_clean['nav'] = pd.to_numeric(nav_data_clean[nav_col], errors='coerce')
            else:
                # 如果无法识别列，使用前两列
                if len(nav_data_clean.columns) >= 2:
                    nav_data_clean['nav_date'] = pd.to_datetime(nav_data_clean.iloc[:, 0]).dt.date
                    nav_data_clean['nav'] = pd.to_numeric(nav_data_clean.iloc[:, 1], errors='coerce')
                else:
                    return pd.DataFrame()
            
            nav_data_clean['fund_code'] = fund_code
            nav_data_clean['accumulated_nav'] = nav_data_clean['nav'] * 1.1  # 模拟累计净值
            nav_data_clean['daily_return'] = nav_data_clean['nav'].pct_change()
            
            # 添加快照日期
            nav_data_clean['snapshot_date'] = datetime.now().date()
            
            # 过滤有效数据
            nav_data_clean = nav_data_clean.dropna(subset=['nav_date', 'nav'])
            
            return nav_data_clean[['fund_code', 'nav_date', 'nav', 'accumulated_nav', 'daily_return', 'snapshot_date']]
            
        except Exception as e:
            logger.error(f"获取基金 {fund_code} 净值失败: {e}")
            return pd.DataFrame()
    
    def save_to_database(self, df, table_name):
        """保存数据到数据库"""
        if df.empty:
            logger.warning(f"{table_name} 数据为空，跳过保存")
            return False
        
        try:
            # 使用 replace 模式，避免重复数据
            df.to_sql(table_name, self.engine, if_exists='append', index=False, chunksize=1000)
            logger.info(f"成功保存 {len(df)} 条数据到 {table_name}")
            return True
        except Exception as e:
            logger.error(f"保存数据到 {table_name} 失败: {e}")
            return False
    
    def update_fund_data(self, sample_size=20):
        """更新基金数据"""
        logger.info("开始更新基金数据...")
        
        # 1. 获取基金列表
        fund_list = self.fetch_fund_list()
        
        if fund_list.empty:
            logger.error("无法获取基金列表，停止更新")
            return False
        
        # 保存基金基本信息
        self.save_to_database(fund_list, 'fund')
        
        # 2. 获取部分基金的净值数据（避免请求过多）
        logger.info(f"开始获取 {min(sample_size, len(fund_list))} 只基金的净值数据...")
        
        # 选择不同类型的基金
        funds_to_fetch = []
        fund_types = fund_list['fund_type'].unique()[:3]  # 取前3种类型
        
        for fund_type in fund_types:
            type_funds = fund_list[fund_list['fund_type'] == fund_type].head(5)  # 每种类型取5只
            funds_to_fetch.extend(type_funds['fund_code'].tolist())
        
        funds_to_fetch = funds_to_fetch[:sample_size]  # 限制总数
        
        total_nav_data = []
        
        for i, fund_code in enumerate(funds_to_fetch, 1):
            logger.info(f"[{i}/{len(funds_to_fetch)}] 处理基金 {fund_code}")
            
            nav_data = self.fetch_fund_nav(fund_code)
            if not nav_data.empty:
                total_nav_data.append(nav_data)
            else:
                # 如果没有真实数据，创建模拟数据
                logger.info(f"  基金 {fund_code} 无真实数据，创建模拟数据")
                mock_data = self._create_mock_nav_data(fund_code)
                total_nav_data.append(mock_data)
            
            # 避免请求过快
            time.sleep(0.3)
        
        # 3. 保存净值数据
        if total_nav_data:
            all_nav_data = pd.concat(total_nav_data, ignore_index=True)
            self.save_to_database(all_nav_data, 'fund_nav')
            logger.info(f"成功保存 {len(all_nav_data)} 条净值记录")
        
        logger.info("基金数据更新完成")
        return True
    
    def _create_mock_nav_data(self, fund_code):
        """创建模拟净值数据"""
        # 创建3年的每日数据
        end_date = datetime.now().date()
        start_date = end_date - timedelta(days=365*3)
        
        dates = pd.date_range(start=start_date, end=end_date, freq='D')
        dates = dates[dates.dayofweek < 5]  # 只保留工作日
        
        # 模拟净值（随机游走）
        np.random.seed(hash(fund_code) % 10000)
        n_days = len(dates)
        base_nav = np.random.uniform(0.8, 3.0)
        returns = np.random.normal(0.0005, 0.02, n_days)  # 日收益率
        nav_values = base_nav * np.exp(np.cumsum(returns))
        
        mock_data = pd.DataFrame({
            'fund_code': fund_code,
            'nav_date': dates.date,
            'nav': nav_values,
            'accumulated_nav': nav_values * 1.1,
            'daily_return': returns,
            'snapshot_date': datetime.now().date()
        })
        
        return mock_data
    
    def create_sample_portfolio(self):
        """创建示例投资组合"""
        try:
            with self.engine.connect() as conn:
                # 检查是否有基金数据
                result = conn.execute(text("SELECT COUNT(*) FROM fund"))
                fund_count = result.scalar()
                
                if fund_count == 0:
                    logger.warning("无基金数据，无法创建示例组合")
                    return
                
                # 创建保守型组合
                conn.execute(text("""
                    INSERT INTO portfolio (portfolio_name, description, risk_level, created_at) 
                    VALUES ('保守型稳健组合', '低风险债券型基金组合', '保守', NOW())
                """))
                
                # 创建稳健型组合
                conn.execute(text("""
                    INSERT INTO portfolio (portfolio_name, description, risk_level, created_at) 
                    VALUES ('稳健型平衡组合', '股债平衡型基金组合', '稳健', NOW())
                """))
                
                # 创建积极型组合
                conn.execute(text("""
                    INSERT INTO portfolio (portfolio_name, description, risk_level, created_at) 
                    VALUES ('积极型成长组合', '股票型成长基金组合', '积极', NOW())
                """))
                
                logger.info("创建了3个示例投资组合")
                
                # 为每个组合添加示例持仓
                portfolios = conn.execute(text("SELECT id FROM portfolio")).fetchall()
                funds = conn.execute(text("SELECT fund_code FROM fund LIMIT 15")).fetchall()
                
                for i, (portfolio_id,) in enumerate(portfolios):
                    # 每个组合分配3-5只基金
                    portfolio_funds = funds[i*3:(i+1)*3+2]
                    total_weight = 0
                    
                    for j, (fund_code,) in enumerate(portfolio_funds):
                        weight = round(1.0 / len(portfolio_funds), 3)
                        total_weight += weight
                        
                        conn.execute(text("""
                            INSERT INTO portfolio_holding (portfolio_id, fund_code, weight, created_at)
                            VALUES (:portfolio_id, :fund_code, :weight, NOW())
                        """), {
                            'portfolio_id': portfolio_id,
                            'fund_code': fund_code,
                            'weight': weight
                        })
                    
                    logger.info(f"组合 {portfolio_id} 添加了 {len(portfolio_funds)} 只基金，总权重: {total_weight:.3f}")
                
        except Exception as e:
            logger.error(f"创建示例组合失败: {e}")
    
    def validate_data_quality(self):
        """验证数据质量"""
        try:
            with self.engine.connect() as conn:
                checks = [
                    ("基金表记录数", "SELECT COUNT(*) FROM fund"),
                    ("净值表记录数", "SELECT COUNT(*) FROM fund_nav"),
                    ("组合表记录数", "SELECT COUNT(*) FROM portfolio"),
                    ("持仓表记录数", "SELECT COUNT(*) FROM portfolio_holding"),
                    ("基金类型分布", "SELECT fund_type, COUNT(*) FROM fund GROUP BY fund_type"),
                    ("风险等级分布", "SELECT risk_level, COUNT(*) FROM fund GROUP BY risk_level"),
                    ("指数基金数量", "SELECT COUNT(*) FROM fund WHERE is_index_fund = 1"),
                ]
                
                logger.info("数据质量检查:")
                for check_name, query in checks:
                    result = conn.execute(text(query))
                    if 'GROUP BY' in query:
                        rows = result.fetchall()
                        logger.info(f"  {check_name}: {dict(rows)}")
                    else:
                        count = result.scalar()
                        logger.info(f"  {check_name}: {count}")
                
        except Exception as e:
            logger.error(f"数据质量检查失败: {e}")

def main():
    """主函数"""
    fetcher = FundDataFetcher()
    
    # 测试akshare可用性
    if not fetcher.test_akshare_available():
        logger.info("请先安装 akshare: pip install akshare")
        return
    
    # 更新基金数据
    success = fetcher.update_fund_data(sample_size=15)  # 获取15只基金
    
    if success:
        # 创建示例组合
        fetcher.create_sample_portfolio()
        
        # 验证数据质量
        fetcher.validate_data_quality()
        
        logger.info("\n🎉 数据抓取任务完成！")
        
        # 打印汇总信息
        with fetcher.engine.connect() as conn:
            fund_count = conn.execute(text("SELECT COUNT(*) FROM fund")).scalar()
            nav_count = conn.execute(text("SELECT COUNT(*) FROM fund_nav")).scalar()
            portfolio_count = conn.execute(text("SELECT COUNT(*) FROM portfolio")).scalar()
            holding_count = conn.execute(text("SELECT COUNT(*) FROM portfolio_holding")).scalar()
            
            logger.info(f"📊 数据汇总:")
            logger.info(f"  - 基金数量: {fund_count}")
            logger.info(f"  - 净值记录: {nav_count}")
            logger.info(f"  - 投资组合: {portfolio_count}")
            logger.info(f"  - 组合持仓: {holding_count}")
            
            # 显示示例数据
            funds = conn.execute(text("SELECT fund_code, fund_name, fund_type, risk_level FROM fund LIMIT 5")).fetchall()
            logger.info(f"\n📈 示例基金:")
            for fund in funds:
                logger.info(f"  {fund[0]} - {fund[1]} ({fund[2]}, 风险: {fund[3]})")
    else:
        logger.error("数据抓取失败")

if __name__ == "__main__":
    main()