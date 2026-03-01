#!/usr/bin/env python3
"""
准确的净值数据抓取脚本
使用akshare的真实接口获取准确的基金净值数据
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
import akshare as ak
import traceback
warnings.filterwarnings('ignore')

# 配置日志
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s',
    handlers=[
        logging.FileHandler('accurate_nav.log'),
        logging.StreamHandler()
    ]
)
logger = logging.getLogger(__name__)

class AccurateNavFetcher:
    """准确的净值数据抓取器"""
    
    def __init__(self, db_url='mysql+pymysql://fundwise:password123@localhost/fundwise_backtest'):
        """初始化数据库连接"""
        try:
            self.engine = create_engine(db_url, pool_recycle=3600)
            logger.info("数据库连接成功")
        except Exception as e:
            logger.error(f"数据库连接失败: {e}")
            raise
    
    def clear_existing_nav_data(self):
        """清空现有的净值数据"""
        try:
            with self.engine.connect() as conn:
                conn.execute(text("DELETE FROM fund_nav"))
                conn.commit()
                logger.info("已清空现有的净值数据")
        except Exception as e:
            logger.error(f"清空数据失败: {e}")
    
    def get_fund_codes(self, limit=100):
        """获取基金代码列表"""
        try:
            query = """
                SELECT fund_code, fund_name, fund_type 
                FROM fund 
                WHERE fund_code REGEXP '^[0-9]{6}$'
                ORDER BY fund_code
                LIMIT %s
            """
            df = pd.read_sql_query(query, self.engine, params=(limit,))
            logger.info(f"获取到 {len(df)} 只基金")
            return df
        except Exception as e:
            logger.error(f"获取基金列表失败: {e}")
            return pd.DataFrame()
    
    def fetch_accurate_nav_data(self, fund_code, fund_name):
        """获取准确的净值数据"""
        try:
            logger.info(f"获取基金 {fund_code} - {fund_name} 的准确净值数据...")
            
            # 尝试多个接口
            nav_data = None
            
            # 接口1: fund_open_fund_info_em
            try:
                df = ak.fund_open_fund_info_em(symbol=fund_code, indicator='单位净值走势')
                if not df.empty and '净值日期' in df.columns and '单位净值' in df.columns:
                    df = df.rename(columns={'净值日期': 'nav_date', '单位净值': 'unit_nav'})
                    df['nav_date'] = pd.to_datetime(df['nav_date'])
                    df['accumulated_nav'] = df['unit_nav']  # 暂时使用单位净值作为累计净值
                    df['daily_return'] = df['unit_nav'].pct_change()
                    nav_data = df[['nav_date', 'unit_nav', 'accumulated_nav', 'daily_return']]
                    logger.info(f"✅ 接口1成功: {len(nav_data)}条数据")
            except Exception as e:
                logger.debug(f"接口1失败: {e}")
            
            # 接口2: fund_etf_hist_sina (针对ETF)
            if nav_data is None and fund_code.startswith('51') or fund_code.startswith('15'):
                try:
                    market = 'sh' if fund_code.startswith('51') else 'sz'
                    df = ak.fund_etf_hist_sina(symbol=f"{market}{fund_code}")
                    if not df.empty and 'date' in df.columns and 'close' in df.columns:
                        df = df.rename(columns={'date': 'nav_date', 'close': 'unit_nav'})
                        df['nav_date'] = pd.to_datetime(df['nav_date'])
                        df['accumulated_nav'] = df['unit_nav']
                        df['daily_return'] = df['unit_nav'].pct_change()
                        nav_data = df[['nav_date', 'unit_nav', 'accumulated_nav', 'daily_return']]
                        logger.info(f"✅ 接口2成功: {len(nav_data)}条数据")
                except Exception as e:
                    logger.debug(f"接口2失败: {e}")
            
            # 接口3: fund_fh_hist_em (分红数据)
            if nav_data is None:
                try:
                    df = ak.fund_fh_hist_em(symbol=fund_code)
                    if not df.empty and '净值日期' in df.columns and '单位净值' in df.columns:
                        df = df.rename(columns={'净值日期': 'nav_date', '单位净值': 'unit_nav'})
                        df['nav_date'] = pd.to_datetime(df['nav_date'])
                        df['accumulated_nav'] = df['unit_nav']
                        df['daily_return'] = df['unit_nav'].pct_change()
                        nav_data = df[['nav_date', 'unit_nav', 'accumulated_nav', 'daily_return']]
                        logger.info(f"✅ 接口3成功: {len(nav_data)}条数据")
                except Exception as e:
                    logger.debug(f"接口3失败: {e}")
            
            if nav_data is not None and not nav_data.empty:
                # 获取fund_id
                with self.engine.connect() as conn:
                    result = conn.execute(
                        text("SELECT fund_code FROM fund WHERE fund_code = :code"), 
                        {'code': fund_code}
                    )
                    fund_record = result.fetchone()
                    if fund_record:
                        nav_data['fund_code'] = fund_code
                        nav_data['snapshot_date'] = datetime.now().date()
                        nav_data['data_source'] = 'akshare_accurate'
                        return nav_data
                    else:
                        logger.warning(f"基金 {fund_code} 在数据库中不存在")
            
            logger.warning(f"⚠️ 基金 {fund_code} 无可用准确数据")
            return None
            
        except Exception as e:
            logger.error(f"获取基金 {fund_code} 数据时出错: {e}")
            logger.debug(traceback.format_exc())
            return None
    
    def save_nav_data(self, fund_code, nav_data):
        """保存净值数据到数据库"""
        if nav_data is None or nav_data.empty:
            return False
            
        try:
            # 准备数据
            records = []
            for _, row in nav_data.iterrows():
                record = {
                    'fund_code': fund_code,
                    'nav_date': row['nav_date'].date() if hasattr(row['nav_date'], 'date') else row['nav_date'],
                    'unit_nav': float(row['unit_nav']) if pd.notna(row['unit_nav']) else None,
                    'accumulated_nav': float(row['accumulated_nav']) if pd.notna(row['accumulated_nav']) else row['unit_nav'],
                    'daily_return': float(row['daily_return']) if pd.notna(row['daily_return']) else None,
                    'snapshot_date': row['snapshot_date'],
                    'data_source': row['data_source']
                }
                records.append(record)
            
            # 批量插入
            df_to_save = pd.DataFrame(records)
            df_to_save.to_sql('fund_nav', self.engine, if_exists='append', index=False)
            logger.info(f"✅ 保存 {len(records)} 条净值数据到基金 {fund_code}")
            return True
            
        except Exception as e:
            logger.error(f"保存净值数据失败: {e}")
            logger.debug(traceback.format_exc())
            return False
    
    def run(self, fund_limit=50):
        """运行主程序"""
        logger.info("开始获取准确的净值数据...")
        
        # 清空现有数据
        self.clear_existing_nav_data()
        
        # 获取基金列表
        funds = self.get_fund_codes(limit=fund_limit)
        if funds.empty:
            logger.error("没有获取到基金数据")
            return
        
        total_funds = len(funds)
        successful = 0
        failed = 0
        
        for idx, row in funds.iterrows():
            fund_code = row['fund_code']
            fund_name = row['fund_name']
            
            logger.info(f"[{idx+1}/{total_funds}] 处理基金 {fund_code} - {fund_name}")
            
            # 获取准确净值数据
            nav_data = self.fetch_accurate_nav_data(fund_code, fund_name)
            
            if nav_data is not None and not nav_data.empty:
                # 保存数据
                if self.save_nav_data(fund_code, nav_data):
                    successful += 1
                else:
                    failed += 1
            else:
                failed += 1
                logger.warning(f"基金 {fund_code} 无准确数据可用")
            
            # 避免请求过快
            time.sleep(1)
        
        # 统计数据
        logger.info(f"\n📊 数据获取完成:")
        logger.info(f"   - 尝试基金: {total_funds}")
        logger.info(f"   - 成功获取: {successful}")
        logger.info(f"   - 失败/无数据: {failed}")
        
        # 验证数据质量
        self.verify_data_quality()
    
    def verify_data_quality(self):
        """验证数据质量"""
        try:
            with self.engine.connect() as conn:
                # 统计信息
                result = conn.execute(text("SELECT COUNT(*) as total FROM fund_nav"))
                total_nav = result.fetchone()[0]
                
                result = conn.execute(text("SELECT COUNT(DISTINCT fund_code) as funds FROM fund_nav"))
                funds_with_nav = result.fetchone()[0]
                
                result = conn.execute(text("SELECT AVG(records_per_fund) FROM (SELECT fund_code, COUNT(*) as records_per_fund FROM fund_nav GROUP BY fund_code) t"))
                avg_records = result.fetchone()[0]
                
                result = conn.execute(text("SELECT MIN(nav_date), MAX(nav_date) FROM fund_nav"))
                date_range = result.fetchone()
                
                logger.info(f"\n✅ 数据质量验证:")
                logger.info(f"   - 总净值记录数: {total_nav}")
                logger.info(f"   - 有净值数据的基金数: {funds_with_nav}")
                logger.info(f"   - 每只基金平均记录数: {avg_records:.1f}")
                logger.info(f"   - 数据时间范围: {date_range[0]} 到 {date_range[1]}")
                
                # 样本数据
                result = conn.execute(text("""
                    SELECT f.fund_code, f.fund_name, COUNT(n.nav_date) as nav_count, 
                           MIN(n.nav_date) as first_date, MAX(n.nav_date) as last_date
                    FROM fund f
                    LEFT JOIN fund_nav n ON f.fund_code = n.fund_code
                    WHERE n.nav_date IS NOT NULL
                    GROUP BY f.fund_code, f.fund_name
                    ORDER BY nav_count DESC
                    LIMIT 5
                """))
                logger.info(f"\n📈 样本基金数据:")
                for row in result:
                    logger.info(f"   {row[0]} - {row[1]}: {row[2]}条记录 ({row[3]} 到 {row[4]})")
                    
        except Exception as e:
            logger.error(f"数据质量验证失败: {e}")

def main():
    """主函数"""
    fetcher = AccurateNavFetcher()
    
    try:
        # 先测试少量基金
        logger.info("开始获取准确净值数据（测试50只基金）...")
        fetcher.run(fund_limit=50)
        
    except Exception as e:
        logger.error(f"主程序出错: {e}")
        logger.debug(traceback.format_exc())

if __name__ == "__main__":
    main()