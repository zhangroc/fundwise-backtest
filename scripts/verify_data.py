#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
基金数据真实性验证脚本
通过对比数据库数据与真实市场数据来验证数据质量
"""

import pymysql
import requests
import json
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

def fetch_real_nav_from_tiantian(fund_code):
    """
    从天天基金网获取真实净值数据
    http://fund.eastmoney.com/f10/jjjz_000001.html
    """
    try:
        # 获取最近 30 天的净值
        url = f"http://api.fund.eastmoney.com/f10/lsjz"
        params = {
            'fundCode': fund_code,
            'pageIndex': 1,
            'pageSize': 30,
            'startDate': '',
            'endDate': ''
        }
        
        response = requests.get(url, params=params, timeout=10,
                              headers={'User-Agent': 'Mozilla/5.0'})
        response.encoding = 'utf-8'
        
        data = response.json()
        
        if 'Data' not in data or 'LSJZList' not in data['Data']:
            return None
        
        nav_list = data['Data']['LSJZList']
        
        if not nav_list:
            return None
        
        # 提取最近 5 条数据进行对比
        real_data = []
        for item in nav_list[:5]:
            real_data.append({
                'date': item['FSRQ'],
                'nav': float(item['DWJZ']) if item['DWJZ'] else None,
                'accumulated_nav': float(item['LJJZ']) if item['LJJZ'] else None,
                'daily_return': float(item['JZZZL']) if item['JZZZL'] else None
            })
        
        return real_data
        
    except Exception as e:
        print(f"  获取 {fund_code} 失败：{e}")
        return None

def get_db_nav(conn, fund_code, days=5):
    """从数据库获取最近 N 天的净值数据"""
    cursor = conn.cursor()
    cursor.execute("""
        SELECT nav_date, nav, accumulated_nav, daily_return
        FROM fund_nav
        WHERE fund_code = %s
        ORDER BY nav_date DESC
        LIMIT %s
    """, (fund_code, days))
    
    db_data = []
    for row in cursor.fetchall():
        db_data.append({
            'date': row[0].strftime('%Y-%m-%d'),
            'nav': row[1],
            'accumulated_nav': row[2],
            'daily_return': row[3]
        })
    
    cursor.close()
    return db_data

def compare_data(real_data, db_data):
    """对比真实数据和数据库数据"""
    if not real_data or not db_data:
        return None
    
    matches = 0
    total = 0
    nav_diffs = []
    
    for real in real_data:
        for db in db_data:
            if real['date'] == db['date']:
                total += 1
                
                # 对比净值
                if real['nav'] and db['nav']:
                    diff = abs(real['nav'] - db['nav'])
                    nav_diffs.append(diff)
                    
                    # 如果差值小于 0.01，认为匹配
                    if diff < 0.01:
                        matches += 1
                break
    
    if total == 0:
        return {'status': 'no_match', 'message': '日期不匹配'}
    
    avg_diff = sum(nav_diffs) / len(nav_diffs) if nav_diffs else 0
    match_rate = (matches / total * 100) if total > 0 else 0
    
    return {
        'status': 'success' if match_rate > 80 else 'mismatch',
        'total': total,
        'matches': matches,
        'match_rate': match_rate,
        'avg_nav_diff': avg_diff
    }

def verify_fund_data(conn, fund_code, fund_name):
    """验证单只基金的数据"""
    print(f"\n验证 {fund_code} - {fund_name}")
    
    # 获取真实数据
    print("  获取真实市场数据...")
    real_data = fetch_real_nav_from_tiantian(fund_code)
    
    if not real_data:
        print("  ⚠️ 无法获取真实数据")
        return {'status': 'failed', 'reason': '无法获取真实数据'}
    
    # 获取数据库数据
    print("  查询数据库数据...")
    db_data = get_db_nav(conn, fund_code)
    
    if not db_data:
        print("  ❌ 数据库无数据")
        return {'status': 'failed', 'reason': '数据库无数据'}
    
    # 对比数据
    print("  对比数据...")
    result = compare_data(real_data, db_data)
    
    if result:
        if result['status'] == 'success':
            print(f"  ✅ 验证通过：匹配率 {result['match_rate']:.1f}%, 平均差值 {result['avg_nav_diff']:.4f}")
        else:
            print(f"  ⚠️ 验证异常：{result.get('message', '数据不匹配')}")
        
        result['fund_code'] = fund_code
        result['fund_name'] = fund_name
        return result
    
    return {'status': 'failed', 'reason': '对比失败'}

def batch_verify():
    """批量验证基金数据"""
    print("=" * 70)
    print("基金数据真实性验证")
    print("=" * 70)
    
    conn = create_db_connection()
    
    # 随机抽取 20 只优质数据基金
    cursor = conn.cursor()
    cursor.execute("""
        SELECT fund_code, fund_name, data_quality
        FROM fund 
        WHERE data_quality = 'excellent' AND nav_record_count > 300
        ORDER BY RAND()
        LIMIT 20
    """)
    
    funds = cursor.fetchall()
    cursor.close()
    
    print(f"抽取 {len(funds)} 只基金进行验证\n")
    
    results = []
    for fund_code, fund_name, quality in funds:
        result = verify_fund_data(conn, fund_code, fund_name)
        if result:
            results.append(result)
    
    # 统计结果
    print("\n" + "=" * 70)
    print("验证结果统计")
    print("=" * 70)
    
    success_count = sum(1 for r in results if r.get('status') == 'success')
    failed_count = len(results) - success_count
    
    if success_count > 0:
        avg_match_rate = sum(r.get('match_rate', 0) for r in results if r.get('status') == 'success') / success_count
        avg_diff = sum(r.get('avg_nav_diff', 0) for r in results if r.get('status') == 'success') / success_count
    else:
        avg_match_rate = 0
        avg_diff = 0
    
    print(f"验证基金数：{len(results)}")
    print(f"✅ 验证通过：{success_count} 只")
    print(f"❌ 验证失败：{failed_count} 只")
    print(f"平均匹配率：{avg_match_rate:.1f}%")
    print(f"平均净值差值：{avg_diff:.4f}")
    
    # 保存验证结果到数据库
    cursor = conn.cursor()
    cursor.execute("""
        INSERT INTO data_sync_log (
            sync_type, sync_source, sync_status,
            fund_count, records_inserted, records_failed,
            start_time, end_time, duration_seconds,
            operator, batch_id, remarks
        ) VALUES (
            'data_verification',
            'tiantian_fund',
            %s,
            %s,
            %s,
            %s,
            NOW(),
            NOW(),
            0,
            'verify_data.py',
            %s,
            %s
        )
    """, (
        'success' if success_count > failed_count else 'partial',
        len(results),
        success_count,
        failed_count,
        f'VERIFY-{datetime.now().strftime("%Y%m%d")}',
        f'验证{len(results)}只基金，通过{success_count}只，平均匹配率{avg_match_rate:.1f}%'
    ))
    conn.commit()
    cursor.close()
    conn.close()
    
    print("=" * 70)

if __name__ == '__main__':
    batch_verify()
