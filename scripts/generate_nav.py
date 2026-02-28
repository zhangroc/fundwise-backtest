#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
为现有基金生成模拟净值数据（用于演示回测功能）
"""

import pymysql
import random
from datetime import datetime, timedelta

# 数据库配置
DB_CONFIG = {
    'host': 'localhost',
    'port': 3306,
    'user': 'root',
    'unix_socket': '/var/run/mysqld/mysqld.sock',
    'database': 'fundwise_backtest'
}

conn = pymysql.connect(**DB_CONFIG, charset='utf8mb4')
cursor = conn.cursor()

print("获取基金列表...")
cursor.execute("SELECT fund_code, fund_type, risk_level FROM fund LIMIT 1000")
funds = cursor.fetchall()

print(f"将为 {len(funds)} 只基金生成净值数据...")

total_inserted = 0
days = 365  # 生成 1 年的数据

for i, (fund_code, fund_type, risk_level) in enumerate(funds):
    print(f"[{i+1}/{len(funds)}] {fund_code}")
    
    # 根据基金类型设置波动率
    if '股票' in str(fund_type):
        volatility = 0.03
    elif '混合' in str(fund_type):
        volatility = 0.02
    elif '债券' in str(fund_type):
        volatility = 0.005
    else:
        volatility = 0.015
    
    # 生成净值数据
    base_nav = 1.0 + random.random()
    data = []
    
    for day in range(days):
        nav_date = datetime.now().date() - timedelta(days=days-day)
        
        # 生成日收益率（偏正态，略偏向正收益）
        daily_return = (random.random() - 0.45) * volatility * 2
        
        base_nav *= (1 + daily_return)
        
        data.append((
            fund_code,
            nav_date,
            base_nav,  # 净值
            base_nav * 1.05,  # 累计净值（考虑分红）
            daily_return,
            datetime.now().date()
        ))
    
    # 批量插入
    sql = """
    INSERT INTO fund_nav (fund_code, nav_date, nav, accumulated_nav, daily_return, snapshot_date, created_at)
    VALUES (%s, %s, %s, %s, %s, %s, NOW())
    ON DUPLICATE KEY UPDATE
        nav = VALUES(nav),
        accumulated_nav = VALUES(accumulated_nav),
        daily_return = VALUES(daily_return),
        snapshot_date = VALUES(snapshot_date)
    """
    
    try:
        cursor.executemany(sql, data)
        conn.commit()
        total_inserted += len(data)
    except Exception as e:
        print(f"  插入失败：{e}")
        conn.rollback()

cursor.close()
conn.close()

print(f"\n完成！共生成了 {total_inserted} 条净值记录")
