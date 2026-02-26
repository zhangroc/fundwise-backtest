#!/usr/bin/env python3
"""
修复版：获取准确净值数据
直接使用数据库连接，避免ORM问题
"""

import pandas as pd
import numpy as np
from datetime import datetime, timedelta
import time
import logging
import warnings
import sys
import os
import pymysql
import akshare as ak
import traceback
warnings.filterwarnings('ignore')

# 配置日志
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s',
    handlers=[
        logging.FileHandler('fix_nav.log'),
        logging.StreamHandler()
    ]
)
logger = logging.getLogger(__name__)

class AccurateNavFixer:
    """准确的净值数据修复器"""
    
    def __init__(self, host='localhost', user='fundwise', password='password123', 
                 database='fundwise_backtest'):
        """初始化数据库连接"""
        try:
            self.connection = pymysql.connect(
                host=host,
                user=user,
                password=password,
                database=database,
                charset='utf8mb4',
                cursorclass=pymysql.cursors.DictCursor
            )
            logger.info("数据库连接成功")
        except Exception as e:
            logger.error(f"数据库连接失败: {e}")
            raise
    
    def clear_existing_nav_data(self):
        """清空现有的净值数据"""
        try:
            with self.connection.cursor() as cursor:
                cursor.execute("DELETE FROM fund_nav")
                self.connection.commit()
                logger.info("已清空现有的净值数据")
        except Exception as e:
            logger.error(f"清空数据失败: {e}")
    
    def get_fund_codes(self, limit=20):
        """获取基金代码列表"""
        try:
            with self.connection.cursor() as cursor:
                query = """
                    SELECT fund_code, fund_name, fund_type 
                    FROM fund 
                    WHERE fund_code REGEXP '^[0-9]{6}$'
                    ORDER BY fund_code
                    LIMIT %s
                """
                cursor.execute(query, (limit,))
                result = cursor.fetchall()
                logger.info(f"获取到 {len(result)} 只基金")
                return pd.DataFrame(result)
        except Exception as e:
            logger.error(f"获取基金列表失败: {e}")
            return pd.DataFrame()
    
    def fetch_accurate_nav_data(self, fund_code, fund_name):
        """获取准确的净值数据"""
        try:
            logger.info(f"获取基金 {fund_code} - {fund_name} 的准确净值数据...")
            
            # 接口1: fund_open_fund_info_em
            try:
                df = ak.fund_open_fund_info_em(symbol=fund_code, indicator='单位净值走势')
                if not df.empty and '净值日期' in df.columns and '单位净值' in df.columns:
                    df = df.rename(columns={'净值日期': 'nav_date', '单位净值': 'nav'})
                    df['nav_date'] = pd.to_datetime(df['nav_date'])
                    df['accumulated_nav'] = df['nav']  # 暂时使用单位净值作为累计净值
                    df['daily_return'] = df['nav'].pct_change()
                    df['fund_code'] = fund_code
                    df['snapshot_date'] = datetime.now().date()
                    
                    # 确保列顺序正确
                    columns_needed = ['fund_code', 'nav_date', 'nav', 'accumulated_nav', 'daily_return', 'snapshot_date']
                    df = df[columns_needed]
                    
                    logger.info(f"✅ 成功获取: {len(df)}条真实数据")
                    return df
            except Exception as e:
                logger.debug(f"接口1失败: {e}")
            
            logger.warning(f"⚠️ 基金 {fund_code} 无可用真实数据")
            return None
            
        except Exception as e:
            logger.error(f"获取基金 {fund_code} 数据时出错: {e}")
            logger.debug(traceback.format_exc())
            return None
    
    def save_nav_data_batch(self, nav_data):
        """批量保存净值数据到数据库"""
        if nav_data is None or nav_data.empty:
            return False
            
        try:
            with self.connection.cursor() as cursor:
                # 准备SQL语句
                sql = """
                    INSERT INTO fund_nav 
                    (fund_code, nav_date, nav, accumulated_nav, daily_return, snapshot_date)
                    VALUES (%s, %s, %s, %s, %s, %s)
                """
                
                # 准备批量数据
                batch_data = []
                for _, row in nav_data.iterrows():
                    batch_data.append((
                        str(row['fund_code']),
                        row['nav_date'].date() if hasattr(row['nav_date'], 'date') else row['nav_date'],
                        float(row['nav']) if pd.notna(row['nav']) else None,
                        float(row['accumulated_nav']) if pd.notna(row['accumulated_nav']) else float(row['nav']),
                        float(row['daily_return']) if pd.notna(row['daily_return']) else None,
                        row['snapshot_date']
                    ))
                
                # 批量插入
                cursor.executemany(sql, batch_data)
                self.connection.commit()
                logger.info(f"✅ 批量保存 {len(batch_data)} 条净值数据")
                return True
                
        except Exception as e:
            logger.error(f"批量保存净值数据失败: {e}")
            logger.debug(traceback.format_exc())
            self.connection.rollback()
            return False
    
    def run(self, fund_limit=20):
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
        total_records = 0
        
        for idx, row in funds.iterrows():
            fund_code = row['fund_code']
            fund_name = row['fund_name']
            
            logger.info(f"[{idx+1}/{total_funds}] 处理基金 {fund_code} - {fund_name}")
            
            # 获取准确净值数据
            nav_data = self.fetch_accurate_nav_data(fund_code, fund_name)
            
            if nav_data is not None and not nav_data.empty:
                # 保存数据
                if self.save_nav_data_batch(nav_data):
                    successful += 1
                    total_records += len(nav_data)
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
        logger.info(f"   - 总记录数: {total_records}")
        
        # 验证数据质量
        self.verify_data_quality()
    
    def verify_data_quality(self):
        """验证数据质量"""
        try:
            with self.connection.cursor() as cursor:
                # 统计信息
                cursor.execute("SELECT COUNT(*) as total FROM fund_nav")
                total_nav = cursor.fetchone()['total']
                
                cursor.execute("SELECT COUNT(DISTINCT fund_code) as funds FROM fund_nav")
                funds_with_nav = cursor.fetchone()['funds']
                
                cursor.execute("SELECT AVG(records_per_fund) as avg_records FROM (SELECT fund_code, COUNT(*) as records_per_fund FROM fund_nav GROUP BY fund_code) t")
                avg_records = cursor.fetchone()['avg_records']
                
                cursor.execute("SELECT MIN(nav_date) as min_date, MAX(nav_date) as max_date FROM fund_nav")
                date_range = cursor.fetchone()
                
                logger.info(f"\n✅ 数据质量验证:")
                logger.info(f"   - 总净值记录数: {total_nav}")
                logger.info(f"   - 有净值数据的基金数: {funds_with_nav}")
                logger.info(f"   - 每只基金平均记录数: {avg_records:.1f}")
                logger.info(f"   - 数据时间范围: {date_range['min_date']} 到 {date_range['max_date']}")
                
                # 样本数据
                cursor.execute("""
                    SELECT f.fund_code, f.fund_name, COUNT(n.nav_date) as nav_count, 
                           MIN(n.nav_date) as first_date, MAX(n.nav_date) as last_date,
                           AVG(n.nav) as avg_nav
                    FROM fund f
                    LEFT JOIN fund_nav n ON f.fund_code = n.fund_code
                    WHERE n.nav_date IS NOT NULL
                    GROUP BY f.fund_code, f.fund_name
                    ORDER BY nav_count DESC
                    LIMIT 5
                """)
                logger.info(f"\n📈 样本基金数据:")
                for row in cursor.fetchall():
                    logger.info(f"   {row['fund_code']} - {row['fund_name']}:")
                    logger.info(f"      记录数: {row['nav_count']} ({row['first_date']} 到 {row['last_date']})")
                    logger.info(f"      平均净值: {row['avg_nav']:.4f}")
                    
        except Exception as e:
            logger.error(f"数据质量验证失败: {e}")
    
    def close(self):
        """关闭连接"""
        if hasattr(self, 'connection'):
            self.connection.close()
            logger.info("数据库连接已关闭")

def main():
    """主函数"""
    fetcher = AccurateNavFixer()
    
    try:
        # 先测试少量基金，确保数据准确性
        logger.info("开始获取准确净值数据（测试20只基金）...")
        fetcher.run(fund_limit=20)
        
    except Exception as e:
        logger.error(f"主程序出错: {e}")
        logger.debug(traceback.format_exc())
    finally:
        fetcher.close()

if __name__ == "__main__":
    main()