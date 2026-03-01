#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
使用 AKShare 获取真实基金净值数据
"""

import pymysql
import akshare as ak
import pandas as pd
import time
from datetime import datetime

# 数据库配置
DB_CONFIG = {
    'host': 'localhost',
    'port': 3306,
    'user': 'root',
    'unix_socket': '/var/run/mysqld/mysqld.sock',
    'database': 'fundwise_backtest'
}

def create_db_connection():
    return pymysql.connect(**DB_CONFIG, charset='utf8mb4')

def fetch_real_nav(fund_code):
    """获取单只基金的真实历史净值"""
    try:
        # 使用 AKShare 获取开放式基金净值
        fund_df = ak.fund_open_fund_info_em(fund=fund_code, indicator="单位净值")
        
        if fund_df is None or len(fund_df) == 0:
            return []
        
        # 数据清洗
        fund_df['净值日期'] = pd.to_datetime(fund_df['净值日期']).dt.date
        
        data = []
        for _, row in fund_df.iterrows():
            try:
                nav_date = row['净值日期']
                nav = float(row['单位净值']) if pd.notna(row['单位净值']) else 0
                accumulated_nav = float(row['累计净值']) if pd.notna(row.get('累计净值', nav)) else nav
                daily_return = float(row['日增长率']) / 100.0 if pd.notna(row.get('日增长率', 0)) else 0
                
                data.append((fund_code, nav_date, nav, accumulated_nav, daily_return))
            except Exception as e:
                continue
        
        return data
        
    except Exception as e:
        print(f"  获取 {fund_code} 失败：{e}")
        return []

def save_nav_to_db(conn, nav_data):
    """保存净值数据到数据库"""
    cursor = conn.cursor()
    
    sql = """
    INSERT INTO fund_nav (fund_code, nav_date, nav, accumulated_nav, daily_return, 
                         snapshot_date, created_at)
    VALUES (%s, %s, %s, %s, %s, CURDATE(), NOW())
    ON DUPLICATE KEY UPDATE
        nav = VALUES(nav),
        accumulated_nav = VALUES(accumulated_nav),
        daily_return = VALUES(daily_return),
        snapshot_date = VALUES(snapshot_date)
    """
    
    if not nav_data:
        return 0
    
    try:
        cursor.executemany(sql, nav_data)
        conn.commit()
        return len(nav_data)
    except Exception as e:
        conn.rollback()
        print(f"  保存失败：{e}")
        return 0
    finally:
        cursor.close()

def batch_import_real_nav(limit=100):
    """批量导入真实净值数据"""
    print("=" * 60)
    print("开始导入真实基金净值数据 (AKShare)")
    print("=" * 60)
    
    conn = create_db_connection()
    cursor = conn.cursor()
    
    # 获取基金列表（优先选择成立时间早的基金）
    cursor.execute("""
        SELECT fund_code, fund_name, establishment_date
        FROM fund 
        WHERE establishment_date IS NOT NULL
        ORDER BY establishment_date ASC
        LIMIT %s
    """, (limit,))
    
    funds = cursor.fetchall()
    cursor.close()
    
    print(f"准备导入 {len(funds)} 只基金的真实净值")
    
    total_inserted = 0
    success_count = 0
    
    for i, (fund_code, fund_name, est_date) in enumerate(funds):
        print(f"[{i+1}/{len(funds)}] {fund_code} - {fund_name}")
        
        # 获取真实净值
        nav_data = fetch_real_nav(fund_code)
        
        if nav_data:
            inserted = save_nav_to_db(conn, nav_data)
            total_inserted += inserted
            if inserted > 0:
                success_count += 1
                print(f"  ✅ 导入了 {inserted} 条真实记录 (从 {nav_data[0][1]} 到 {nav_data[-1][0]})")
        else:
            print(f"  ⚠️ 无数据")
        
        # 避免请求过快
        time.sleep(0.5)
    
    # 统计结果
    cursor = conn.cursor()
    cursor.execute("SELECT COUNT(*) FROM fund_nav")
    total_count = cursor.fetchone()[0]
    cursor.execute("SELECT COUNT(DISTINCT fund_code) FROM fund_nav WHERE snapshot_date = CURDATE()")
    today_count = cursor.fetchone()[0]
    cursor.close()
    conn.close()
    
    print("\n" + "=" * 60)
    print(f"导入完成!")
    print(f"成功导入：{success_count}/{len(funds)} 只基金")
    print(f"新增真实记录：{total_inserted} 条")
    print(f"今日导入基金数：{today_count} 只")
    print(f"数据库总记录：{total_count} 条")
    print("=" * 60)

if __name__ == '__main__':
    import argparse
    parser = argparse.ArgumentParser(description='导入真实净值数据')
    parser.add_argument('--limit', type=int, default=10, help='导入基金数量')
    args = parser.parse_args()
    
    batch_import_real_nav(limit=args.limit)
