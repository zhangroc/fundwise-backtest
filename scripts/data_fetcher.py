#!/usr/bin/env python3
"""
基金数据抓取与入库脚本
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
            logger.info("akshare 模块可用")
            return True
        except ImportError as e:
            logger.error(f"akshare 模块未安装: {e}")
            logger.info("安装命令: pip install akshare -i https://pypi.tuna.tsinghua.edu.cn/simple")
            return False
        
    def fetch_fund_list(self):
        """获取基金列表"""
        try:
            import akshare as ak
            
            # 获取开放式基金列表
            logger.info("正在获取开放式基金列表...")
            fund_list = ak.fund_open_fund_daily()
            
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
                'fund_company': '基金管理人',
                'establishment_date': '成立日期',
                'total_assets': '基金规模(亿元)'
            }
            
            # 只保留我们需要的列
            available_columns = []
            for db_col, ak_col in column_mapping.items():
                if ak_col in fund_list_clean.columns:
                    fund_list_clean[db_col] = fund_list_clean[ak_col]
                    available_columns.append(db_col)
            
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
            
            # 转换日期格式
            if 'establishment_date' in fund_list_clean.columns:
                fund_list_clean['establishment_date'] = pd.to_datetime(
                    fund_list_clean['establishment_date'], errors='coerce'
                ).dt.date
            
            # 转换基金规模
            if 'total_assets' in fund_list_clean.columns:
                fund_list_clean['total_assets'] = pd.to_numeric(
                    fund_list_clean['total_assets'], errors='coerce'
                )
            
            return fund_list_clean[available_columns + ['is_index_fund', 'risk_level', 'status', 'management_fee_rate', 'custodian_fee_rate']]
            
        except Exception as e:
            logger.error(f"获取基金列表失败: {e}")
            return pd.DataFrame()
    
    def _map_risk_level(self, fund_type):
        """根据基金类型映射风险等级"""
        if not isinstance(fund_type, str):
            return '稳健'
        
        fund_type = fund_type.lower()
        
        if any(word in fund_type for word in ['债券', '货币', '理财']):
            return '保守'
        elif any(word in fund_type for word in ['股票', '积极', '成长', '进取']):
            return '积极'
        else:
            return '稳健'
    
    def fetch_fund_nav(self, fund_code, days=365*3):
        """获取基金历史净值"""
        try:
            import akshare as ak
            
            # 计算起始日期（3年前）
            end_date = datetime.now().strftime('%Y%m%d')
            start_date = (datetime.now() - timedelta(days=days)).strftime('%Y%m%d')
            
            logger.info(f"获取基金 {fund_code} 净值数据 ({start_date} 至 {end_date})...")
            
            # 获取基金净值
            nav_data = ak.fund_open_fund_info(fund=fund_code, indicator="单位净值走势")
            
            if nav_data.empty:
                logger.warning(f"基金 {fund_code} 无净值数据")
                return pd.DataFrame()
            
            # 数据清洗
            nav_data_clean = nav_data.copy()
            nav_data_clean['fund_code'] = fund_code
            nav_data_clean['nav_date'] = pd.to_datetime(nav_data_clean['净值日期']).dt.date
            nav_data_clean['nav'] = pd.to_numeric(nav_data_clean['单位净值'], errors='coerce')
            nav_data_clean['accumulated_nav'] = pd.to_numeric(nav_data_clean['累计净值'], errors='coerce')
            nav_data_clean['daily_return'] = nav_data_clean['日增长率'].str.rstrip('%').astype(float) / 100
            
            # 添加快照日期
            nav_data_clean['snapshot_date'] = datetime.now().date()
            
            # 过滤日期范围
            nav_data_clean = nav_data_clean[
                (nav_data_clean['nav_date'] >= pd.to_datetime(start_date).date()) &
                (nav_data_clean['nav_date'] <= pd.to_datetime(end_date).date())
            ]
            
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
    
    def update_fund_data(self, sample_size=50):
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
        
        funds_to_fetch = fund_list.head(sample_size)['fund_code'].tolist()
        total_nav_data = []
        
        for i, fund_code in enumerate(funds_to_fetch, 1):
            logger.info(f"[{i}/{len(funds_to_fetch)}] 处理基金 {fund_code}")
            
            nav_data = self.fetch_fund_nav(fund_code)
            if not nav_data.empty:
                total_nav_data.append(nav_data)
            
            # 避免请求过快
            time.sleep(0.5)
        
        # 3. 保存净值数据
        if total_nav_data:
            all_nav_data = pd.concat(total_nav_data, ignore_index=True)
            self.save_to_database(all_nav_data, 'fund_nav')
            logger.info(f"成功保存 {len(all_nav_data)} 条净值记录")
        
        logger.info("基金数据更新完成")
        return True
    
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
                
        except Exception as e:
            logger.error(f"创建示例组合失败: {e}")
    
    def validate_data_quality(self):
        """验证数据质量"""
        try:
            with self.engine.connect() as conn:
                checks = [
                    ("基金表记录数", "SELECT COUNT(*) FROM fund"),
                    ("净值表记录数", "SELECT COUNT(*) FROM fund_nav"),
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
    success = fetcher.update_fund_data(sample_size=30)  # 先获取30只基金作为测试
    
    if success:
        # 创建示例组合
        fetcher.create_sample_portfolio()
        
        # 验证数据质量
        fetcher.validate_data_quality()
        
        logger.info("数据抓取任务完成！")
        
        # 打印汇总信息
        with fetcher.engine.connect() as conn:
            fund_count = conn.execute(text("SELECT COUNT(*) FROM fund")).scalar()
            nav_count = conn.execute(text("SELECT COUNT(*) FROM fund_nav")).scalar()
            logger.info(f"\n数据汇总:")
            logger.info(f"  - 基金数量: {fund_count}")
            logger.info(f"  - 净值记录: {nav_count}")
            
            # 显示前5只基金
            funds = conn.execute(text("SELECT fund_code, fund_name, fund_type, risk_level FROM fund LIMIT 5")).fetchall()
            logger.info(f"  - 示例基金:")
            for fund in funds:
                logger.info(f"    {fund[0]} - {fund[1]} ({fund[2]}, {fund[3]})")
    else:
        logger.error("数据抓取失败")

if __name__ == "__main__":
    main()