#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
从天天基金网获取真实历史净值数据
"""

import pymysql
import requests
import time
from datetime import datetime, timedelta
import re

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

def fetch_nav_history(fund_code, pages=10):
    """
    从天天基金网获取基金历史净值
    http://fund.eastmoney.com/f10/jjjz_000001.html
    """
    url = f"http://api.fund.eastmoney.com/f10/lsjz"
    
    all_data = []
    
    for page in range(1, pages + 1):
        params = {
            'fundCode': fund_code,
            'pageIndex': page,
            'pageSize': 20,
            'startDate': '',
            'endDate': ''
        }
        
        try:
            response = requests.get(url, params=params, timeout=10, 
                                  headers={'User-Agent': 'Mozilla/5.0'})
            response.encoding = 'utf-8'
            
            data = response.json()
            
            if 'Data' not in data or 'LSJZList' not in data['Data']:
                break
            
            nav_list = data['Data']['LSJZList']
            if not nav_list:
                break
            
            all_data.extend(nav_list)
            
            # 如果返回数据少于 20 条，说明已经是最后一页
            if len(nav_list) < 20:
                break
            
            time.sleep(0.2)
            
        except Exception as e:
            print(f"  获取第{page}页失败：{e}")
            break
    
    return all_data

def save_nav_to_db(conn, fund_code, nav_data):
    """保存净值数据到数据库"""
    cursor = conn.cursor()
    
    sql = """
    INSERT INTO fund_nav (fund_code, nav_date, nav, accumulated_nav, daily_return, 
                         snapshot_date, created_at)
    VALUES (%s, %s, %s, %s, %s, %s, NOW())
    ON DUPLICATE KEY UPDATE
        nav = VALUES(nav),
        accumulated_nav = VALUES(accumulated_nav),
        daily_return = VALUES(daily_return),
        snapshot_date = VALUES(snapshot_date)
    """
    
    data = []
    for item in nav_data:
        try:
            nav_date = datetime.strptime(item['FSRQ'], '%Y-%m-%d').date()
            nav = float(item['DWJZ']) if item['DWJZ'] else 0
            accumulated_nav = float(item['LJJZ']) if item['LJJZ'] else 0
            
            # 计算日增长率
            daily_return = 0
            if item['JZZZL']:
                daily_return = float(item['JZZZL']) / 100.0
            
            data.append((
                fund_code,
                nav_date,
                nav,
                accumulated_nav,
                daily_return,
                datetime.now().date()
            ))
        except Exception as e:
            print(f"  解析数据失败：{e}")
            continue
    
    if data:
        try:
            cursor.executemany(sql, data)
            conn.commit()
            return len(data)
        except Exception as e:
            conn.rollback()
            print(f"  保存失败：{e}")
            return 0
    
    return 0

def batch_import_nav(limit=1000):
    """批量导入净值数据"""
    print("=" * 60)
    print("开始从天天基金网导入真实净值数据")
    print("=" * 60)
    
    conn = create_db_connection()
    cursor = conn.cursor()
    
    # 获取基金列表
    cursor.execute("""
        SELECT fund_code, fund_name 
        FROM fund 
        ORDER BY created_at DESC 
        LIMIT %s
    """, (limit,))
    
    funds = cursor.fetchall()
    cursor.close()
    
    print(f"准备导入 {len(funds)} 只基金的净值数据")
    
    total_inserted = 0
    success_count = 0
    
    for i, (fund_code, fund_name) in enumerate(funds):
        print(f"[{i+1}/{len(funds)}] {fund_code} - {fund_name}")
        
        try:
            # 获取历史净值（最多 10 页，约 200 条记录）
            nav_data = fetch_nav_history(fund_code, pages=10)
            
            if nav_data:
                inserted = save_nav_to_db(conn, fund_code, nav_data)
                total_inserted += inserted
                if inserted > 0:
                    success_count += 1
                    print(f"  导入了 {inserted} 条记录")
            else:
                print(f"  无数据")
            
            # 避免请求过快
            time.sleep(0.5)
            
        except Exception as e:
            print(f"  处理失败：{e}")
            continue
    
    cursor = conn.cursor()
    cursor.execute("SELECT COUNT(*) FROM fund_nav")
    total_count = cursor.fetchone()[0]
    cursor.close()
    conn.close()
    
    print("\n" + "=" * 60)
    print(f"导入完成!")
    print(f"成功导入：{success_count}/{len(funds)} 只基金")
    print(f"新增记录：{total_inserted} 条")
    print(f"数据库总记录：{total_count} 条")
    print("=" * 60)

if __name__ == '__main__':
    import argparse
    parser = argparse.ArgumentParser(description='导入真实净值数据')
    parser.add_argument('--limit', type=int, default=100, help='导入基金数量')
    args = parser.parse_args()
    
    batch_import_nav(limit=args.limit)
