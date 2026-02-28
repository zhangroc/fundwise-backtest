#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
基金数据获取脚本
使用 AKShare 获取中国公募基金真实数据并存储到 MySQL 数据库
"""

import akshare as ak
import pandas as pd
import pymysql
from sqlalchemy import create_engine
import time
from datetime import datetime, timedelta
import sys
import os

# 数据库配置
DB_CONFIG = {
    'host': 'localhost',
    'port': 3306,
    'user': 'root',
    'unix_socket': '/var/run/mysqld/mysqld.sock',
    'database': 'fundwise_backtest'
}

# 创建数据库连接
def create_db_connection():
    """创建数据库连接"""
    return pymysql.connect(
        host=DB_CONFIG['host'],
        port=DB_CONFIG['port'],
        user=DB_CONFIG['user'],
        unix_socket=DB_CONFIG.get('unix_socket'),
        database=DB_CONFIG['database'],
        charset='utf8mb4'
    )

# 获取所有基金列表
def fetch_fund_list():
    """获取所有公募基金列表"""
    print("正在获取基金列表...")
    try:
        # 使用 AKShare 获取基金列表 - 修复 API 调用
        fund_df = ak.fund_open_fund_info_em(indicator="单位净值")
        print(f"获取到 {len(fund_df)} 只基金")
        return fund_df
    except Exception as e:
        print(f"获取基金列表失败：{e}")
        return None

# 获取基金详细信息
def fetch_fund_detail(fund_code):
    """获取单只基金的详细信息"""
    try:
        # 获取基金基本信息
        fund_info = ak.fund_info_em(fund=fund_code)
        return fund_info
    except Exception as e:
        print(f"获取基金 {fund_code} 详情失败：{e}")
        return None

# 获取基金历史净值
def fetch_fund_nav_history(fund_code, start_date='20200101', end_date=None):
    """获取基金历史净值数据"""
    if end_date is None:
        end_date = datetime.now().strftime('%Y%m%d')
    
    try:
        # 获取历史净值
        nav_df = ak.fund_open_fund_info_em(fund=fund_code, indicator="累计净值")
        
        if nav_df is None or len(nav_df) == 0:
            return None
        
        # 数据清洗
        nav_df = nav_df[['净值日期', '累计净值', '单位净值', '日增长率']]
        nav_df.columns = ['nav_date', 'accumulated_nav', 'unit_nav', 'daily_return']
        
        # 日期格式转换
        nav_df['nav_date'] = pd.to_datetime(nav_df['nav_date']).dt.date
        
        # 数值类型转换
        nav_df['accumulated_nav'] = pd.to_numeric(nav_df['accumulated_nav'], errors='coerce')
        nav_df['unit_nav'] = pd.to_numeric(nav_df['unit_nav'], errors='coerce')
        nav_df['daily_return'] = pd.to_numeric(nav_df['daily_return'], errors='coerce') / 100.0
        
        return nav_df
    except Exception as e:
        print(f"获取基金 {fund_code} 历史净值失败：{e}")
        return None

# 保存基金基本信息到数据库
def save_fund_to_db(conn, fund_code, fund_name, fund_type, company=None, 
                    establishment_date=None, total_assets=None, is_index_fund=False,
                    risk_level=None):
    """保存基金基本信息到数据库"""
    cursor = conn.cursor()
    
    try:
        sql = """
        INSERT INTO fund (code, name, type, company, establishment_date, 
                         latest_size, is_index_fund, risk_level, created_at, updated_at)
        VALUES (%s, %s, %s, %s, %s, %s, %s, %s, NOW(), NOW())
        ON DUPLICATE KEY UPDATE
            name = VALUES(name),
            type = VALUES(type),
            company = VALUES(company),
            establishment_date = VALUES(establishment_date),
            latest_size = VALUES(latest_size),
            is_index_fund = VALUES(is_index_fund),
            risk_level = VALUES(risk_level),
            updated_at = NOW()
        """
        
        cursor.execute(sql, (
            fund_code,
            fund_name,
            fund_type,
            company,
            establishment_date,
            total_assets,
            is_index_fund,
            risk_level
        ))
        
        conn.commit()
        return True
    except Exception as e:
        conn.rollback()
        print(f"保存基金 {fund_code} 失败：{e}")
        return False
    finally:
        cursor.close()

# 保存基金净值到数据库
def save_nav_to_db(conn, fund_id, nav_data):
    """保存基金净值数据到数据库"""
    cursor = conn.cursor()
    
    try:
        sql = """
        INSERT INTO fund_nav (fund_id, nav_date, accumulated_nav, unit_nav, 
                             daily_return, data_source, snapshot_date, created_at)
        VALUES (%s, %s, %s, %s, %s, %s, %s, NOW())
        ON DUPLICATE KEY UPDATE
            accumulated_nav = VALUES(accumulated_nav),
            unit_nav = VALUES(unit_nav),
            daily_return = VALUES(daily_return),
            snapshot_date = VALUES(snapshot_date)
        """
        
        snapshot_date = datetime.now().date()
        data = []
        
        for _, row in nav_data.iterrows():
            data.append((
                fund_id,
                row['nav_date'],
                row['accumulated_nav'],
                row['unit_nav'],
                row['daily_return'],
                'akshare',
                snapshot_date
            ))
        
        # 批量插入
        cursor.executemany(sql, data)
        conn.commit()
        return len(data)
    except Exception as e:
        conn.rollback()
        print(f"保存净值数据失败：{e}")
        return 0
    finally:
        cursor.close()

# 获取基金 ID
def get_fund_id(conn, fund_code):
    """根据基金代码获取基金 ID"""
    cursor = conn.cursor()
    try:
        cursor.execute("SELECT id FROM fund WHERE code = %s", (fund_code,))
        result = cursor.fetchone()
        return result[0] if result else None
    finally:
        cursor.close()

# 解析基金类型
def parse_fund_type(fund_name, fund_category=None):
    """根据基金名称和类别判断基金类型"""
    if fund_category:
        if '股票' in fund_category:
            return '股票型'
        elif '混合' in fund_category:
            return '混合型'
        elif '债券' in fund_category:
            return '债券型'
        elif '指数' in fund_category or 'ETF' in fund_category:
            return '指数型'
        elif '货币' in fund_category:
            return '货币型'
        elif 'QDII' in fund_category:
            return 'QDII'
    
    # 根据名称判断
    if '指数' in fund_name or 'ETF' in fund_name:
        return '指数型'
    elif '股票' in fund_name:
        return '股票型'
    elif '混合' in fund_name:
        return '混合型'
    elif '债券' in fund_name:
        return '债券型'
    elif '货币' in fund_name:
        return '货币型'
    elif 'QDII' in fund_name:
        return 'QDII'
    
    return '混合型'  # 默认

# 判断是否为指数基金
def is_index_fund(fund_name, fund_type):
    """判断是否为指数基金"""
    if fund_type == '指数型':
        return True
    if 'ETF' in fund_name or '指数' in fund_name:
        return True
    return False

# 批量导入基金数据
def batch_import_funds(limit=100, offset=0):
    """批量导入基金数据"""
    print("=" * 60)
    print("开始批量导入基金数据")
    print("=" * 60)
    
    conn = create_db_connection()
    
    try:
        # 获取基金列表
        fund_list = fetch_fund_list()
        
        if fund_list is None:
            print("获取基金列表失败，退出")
            return
        
        # 限制数量
        if limit > 0:
            fund_list = fund_list.iloc[offset:offset+limit]
        
        print(f"准备导入 {len(fund_list)} 只基金")
        
        success_count = 0
        nav_count = 0
        
        for idx, row in fund_list.iterrows():
            fund_code = row.get('基金代码', '')
            fund_name = row.get('基金简称', '')
            fund_type = parse_fund_type(fund_name, row.get('基金类型'))
            
            if not fund_code:
                continue
            
            print(f"\n[{idx+1}/{len(fund_list)}] 处理基金：{fund_code} - {fund_name}")
            
            # 保存基金基本信息
            if save_fund_to_db(
                conn, 
                fund_code, 
                fund_name, 
                fund_type,
                is_index_fund=is_index_fund(fund_name, fund_type)
            ):
                success_count += 1
                
                # 获取并保存净值数据
                fund_id = get_fund_id(conn, fund_code)
                if fund_id:
                    print(f"  获取历史净值...")
                    nav_data = fetch_fund_nav_history(fund_code)
                    if nav_data is not None and len(nav_data) > 0:
                        inserted = save_nav_to_db(conn, fund_id, nav_data)
                        nav_count += inserted
                        print(f"  保存了 {inserted} 条净值记录")
                    else:
                        print(f"  无净值数据")
            
            # 避免请求过快
            time.sleep(0.5)
        
        print("\n" + "=" * 60)
        print(f"导入完成!")
        print(f"成功导入基金：{success_count}/{len(fund_list)}")
        print(f"保存净值记录：{nav_count} 条")
        print("=" * 60)
        
    except Exception as e:
        print(f"批量导入失败：{e}")
        import traceback
        traceback.print_exc()
    finally:
        conn.close()

# 测试单个基金
def test_single_fund(fund_code='000001'):
    """测试单个基金数据获取"""
    print(f"测试基金：{fund_code}")
    
    conn = create_db_connection()
    
    try:
        # 获取净值
        nav_data = fetch_fund_nav_history(fund_code)
        if nav_data is not None:
            print(f"获取到 {len(nav_data)} 条净值记录")
            print(nav_data.head())
            
            # 保存到数据库
            fund_id = get_fund_id(conn, fund_code)
            if fund_id:
                count = save_nav_to_db(conn, fund_id, nav_data)
                print(f"保存了 {count} 条记录")
        else:
            print("获取净值失败")
        
    finally:
        conn.close()

if __name__ == '__main__':
    import argparse
    
    parser = argparse.ArgumentParser(description='基金数据获取脚本')
    parser.add_argument('--mode', choices=['test', 'batch', 'fetch'], default='batch',
                       help='运行模式：test-测试单个基金，batch-批量导入，fetch-仅获取列表')
    parser.add_argument('--fund-code', type=str, default='000001',
                       help='测试模式的基金代码')
    parser.add_argument('--limit', type=int, default=50,
                       help='批量导入的基金数量')
    parser.add_argument('--offset', type=int, default=0,
                       help='批量导入的起始偏移')
    
    args = parser.parse_args()
    
    if args.mode == 'test':
        test_single_fund(args.fund_code)
    elif args.mode == 'batch':
        batch_import_funds(limit=args.limit, offset=args.offset)
    elif args.mode == 'fetch':
        fund_list = fetch_fund_list()
        if fund_list is not None:
            print(f"\n获取到 {len(fund_list)} 只基金")
            print(fund_list.head(10))
