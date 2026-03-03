#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
修复基金净值数据 - 用真实净值替换模拟数据
"""

import pymysql
import akshare as ak
import time
from datetime import datetime
from concurrent.futures import ThreadPoolExecutor, as_completed

# 数据库配置
DB_CONFIG = {
    'host': 'localhost',
    'port': 3306,
    'user': 'fundwise',
    'password': 'password123',
    'database': 'fundwise_backtest',
    'charset': 'utf8mb4'
}

def create_db_connection():
    return pymysql.connect(**DB_CONFIG)

def fetch_real_nav_open_fund(fund_code):
    """获取开放式基金的真实历史净值"""
    try:
        df = ak.fund_open_fund_info_em(symbol=fund_code, indicator='单位净值走势')
        if df.empty:
            return []
        
        data = []
        for _, row in df.iterrows():
            try:
                nav_date = row['净值日期']
                nav = float(row['单位净值'])
                daily_return = float(row['日增长率']) / 100.0 if row['日增长率'] else 0
                
                data.append({
                    'fund_code': fund_code,
                    'nav_date': nav_date,
                    'nav': nav,
                    'accumulated_nav': nav,  # 暂时用单位净值
                    'daily_return': daily_return
                })
            except:
                continue
        return data
    except Exception as e:
        print(f"  获取 {fund_code} 开放基金数据失败: {e}")
        return []

def fetch_real_nav_etf(fund_code):
    """获取ETF基金的真实历史净值"""
    try:
        # 判断市场
        if fund_code.startswith('51') or fund_code.startswith('58'):
            market = 'sh'
        elif fund_code.startswith('15') or fund_code.startswith('16'):
            market = 'sz'
        else:
            return []
        
        df = ak.fund_etf_hist_sina(symbol=f"{market}{fund_code}")
        if df.empty:
            return []
        
        data = []
        for _, row in df.iterrows():
            try:
                nav_date = row['date']
                nav = float(row['close'])
                
                data.append({
                    'fund_code': fund_code,
                    'nav_date': nav_date,
                    'nav': nav,
                    'accumulated_nav': nav,
                    'daily_return': 0  # 后面计算
                })
            except:
                continue
        
        # 计算日收益率
        for i in range(1, len(data)):
            if data[i-1]['nav'] > 0:
                data[i]['daily_return'] = (data[i]['nav'] - data[i-1]['nav']) / data[i-1]['nav']
        
        return data
    except Exception as e:
        print(f"  获取 {fund_code} ETF数据失败: {e}")
        return []

def save_nav_to_db(conn, nav_data):
    """保存净值数据到数据库"""
    if not nav_data:
        return 0
    
    cursor = conn.cursor()
    
    # 先删除该基金的旧数据
    fund_code = nav_data[0]['fund_code']
    cursor.execute("DELETE FROM fund_nav WHERE fund_code = %s", (fund_code,))
    
    sql = """
    INSERT INTO fund_nav (fund_code, nav_date, nav, accumulated_nav, daily_return, snapshot_date, created_at)
    VALUES (%s, %s, %s, %s, %s, CURDATE(), NOW())
    """
    
    try:
        for item in nav_data:
            cursor.execute(sql, (
                item['fund_code'],
                item['nav_date'],
                item['nav'],
                item['accumulated_nav'],
                item['daily_return']
            ))
        conn.commit()
        return len(nav_data)
    except Exception as e:
        conn.rollback()
        print(f"  保存失败: {e}")
        return 0
    finally:
        cursor.close()

def fix_fund_nav(fund_code, fund_name, fund_type):
    """修复单只基金的净值数据"""
    # 根据基金类型选择获取方式
    if fund_type and 'ETF' in str(fund_type):
        nav_data = fetch_real_nav_etf(fund_code)
    else:
        nav_data = fetch_real_nav_open_fund(fund_code)
    
    if nav_data:
        conn = create_db_connection()
        count = save_nav_to_db(conn, nav_data)
        conn.close()
        return fund_code, count, len(nav_data)
    
    return fund_code, 0, 0

def main():
    import argparse
    parser = argparse.ArgumentParser(description='修复基金净值数据')
    parser.add_argument('--limit', type=int, default=500, help='修复基金数量')
    parser.add_argument('--skip-fixed', action='store_true', help='跳过已修复的基金(记录数>3000)')
    parser.add_argument('--batch', type=int, default=0, help='批次号，用于分批处理')
    args = parser.parse_args()
    
    print("=" * 60)
    print("修复基金净值数据 - 用真实净值替换模拟数据")
    print("=" * 60)
    
    conn = create_db_connection()
    cursor = conn.cursor()
    
    # 获取需要修复的基金列表
    if args.skip_fixed:
        # 跳过已有大量数据的基金（认为它们是真实数据）
        cursor.execute("""
            SELECT f.fund_code, f.fund_name, f.fund_type 
            FROM fund f
            LEFT JOIN (
                SELECT fund_code, COUNT(*) as nav_count
                FROM fund_nav
                GROUP BY fund_code
            ) n ON f.fund_code = n.fund_code
            WHERE f.fund_code REGEXP '^[0-9]{6}$'
            AND (n.nav_count IS NULL OR n.nav_count < 3000)
            ORDER BY f.fund_code
            LIMIT %s OFFSET %s
        """, (args.limit, args.batch * args.limit))
    else:
        cursor.execute("""
            SELECT fund_code, fund_name, fund_type 
            FROM fund 
            WHERE fund_code REGEXP '^[0-9]{6}$'
            ORDER BY fund_code
            LIMIT %s OFFSET %s
        """, (args.limit, args.batch * args.limit))
    
    funds = cursor.fetchall()
    cursor.close()
    conn.close()
    
    print(f"本批次需要修复 {len(funds)} 只基金")
    
    funds_to_fix = funds
    
    success_count = 0
    total_records = 0
    
    for i, (fund_code, fund_name, fund_type) in enumerate(funds_to_fix):
        print(f"[{i+1}/{len(funds_to_fix)}] {fund_code} - {fund_name} ({fund_type or '未知'})")
        
        code, saved, fetched = fix_fund_nav(fund_code, fund_name, fund_type)
        
        if saved > 0:
            success_count += 1
            total_records += saved
            print(f"  ✅ 保存了 {saved} 条记录 (最新净值: {datetime.now().strftime('%Y-%m-%d')})")
        else:
            print(f"  ⚠️ 无数据")
        
        time.sleep(0.3)  # 避免请求过快
    
    # 验证修复结果
    print("\n" + "=" * 60)
    print("验证修复结果...")
    
    conn = create_db_connection()
    cursor = conn.cursor()
    
    # 检查净值数据
    cursor.execute("""
        SELECT f.fund_code, f.fund_name, n.nav_date, n.nav
        FROM fund f
        JOIN fund_nav n ON f.fund_code = n.fund_code
        WHERE f.fund_code IN ('000001', '510300', '161725')
        ORDER BY f.fund_code, n.nav_date DESC
    """)
    
    print("\n样本数据验证:")
    current_fund = None
    for row in cursor.fetchall():
        if row[0] != current_fund:
            current_fund = row[0]
            print(f"\n{row[0]} - {row[1]}:")
        print(f"  {row[2]}: 净值 {row[3]:.4f}")
    
    cursor.close()
    conn.close()
    
    print("\n" + "=" * 60)
    print(f"修复完成!")
    print(f"成功修复: {success_count}/{len(funds_to_fix)} 只基金")
    print(f"总记录数: {total_records}")
    print("=" * 60)

if __name__ == '__main__':
    main()