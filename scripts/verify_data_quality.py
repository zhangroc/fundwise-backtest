#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
基金数据质量验证脚本
通过统计学方法验证数据的合理性
"""

import pymysql
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

def verify_nav_statistics(conn, fund_code, fund_name):
    """验证单只基金的统计学特征"""
    cursor = conn.cursor()
    
    # 获取统计信息
    cursor.execute("""
        SELECT 
            COUNT(*) as total_days,
            MIN(nav_date) as start_date,
            MAX(nav_date) as end_date,
            ROUND(MIN(nav), 4) as min_nav,
            ROUND(MAX(nav), 4) as max_nav,
            ROUND(AVG(nav), 4) as avg_nav,
            ROUND(AVG(daily_return), 6) as avg_return,
            ROUND(STDDEV(daily_return), 6) as volatility,
            ROUND(SUM(CASE WHEN daily_return > 0 THEN 1 ELSE 0 END) * 100.0 / COUNT(*), 2) as win_rate
        FROM fund_nav
        WHERE fund_code = %s
    """, (fund_code,))
    
    stats = cursor.fetchone()
    cursor.close()
    
    if not stats or stats[0] == 0:
        return None
    
    # 验证合理性
    issues = []
    
    total_days, start_date, end_date, min_nav, max_nav, avg_nav, avg_return, volatility, win_rate = stats
    
    # 1. 检查净值是否为正
    if min_nav <= 0:
        issues.append(f"❌ 净值为负或零 (min={min_nav})")
    
    # 2. 检查波动率是否合理（股票型通常 1-3%，债券型 0.1-1%）
    if volatility > 0.05:  # 日波动率超过 5%
        issues.append(f"⚠️ 波动率异常高 ({volatility*100:.2f}%)")
    
    # 3. 检查平均收益是否合理
    if abs(avg_return) > 0.001:  # 日均收益超过 0.1%
        issues.append(f"⚠️ 平均收益异常 ({avg_return*100:.3f}%)")
    
    # 4. 检查胜率是否合理
    if win_rate < 30 or win_rate > 70:
        issues.append(f"⚠️ 胜率异常 ({win_rate}%)")
    
    # 5. 检查最大净值是否合理（一般不超过 100）
    if max_nav > 100:
        issues.append(f"❌ 净值过高 (max={max_nav})")
    
    # 6. 检查时间连续性
    if start_date and end_date:
        actual_days = (end_date - start_date).days
        if total_days < actual_days * 0.5:  # 数据缺失超过 50%
            issues.append(f"⚠️ 数据缺失严重 ({total_days}/{actual_days}天)")
    
    return {
        'fund_code': fund_code,
        'fund_name': fund_name,
        'total_days': total_days,
        'start_date': start_date,
        'end_date': end_date,
        'min_nav': min_nav,
        'max_nav': max_nav,
        'avg_nav': avg_nav,
        'avg_return': avg_return,
        'volatility': volatility,
        'win_rate': win_rate,
        'issues': issues,
        'passed': len(issues) == 0
    }

def verify_data_distribution(conn):
    """验证整体数据分布"""
    cursor = conn.cursor()
    
    # 按类型统计
    cursor.execute("""
        SELECT 
            f.fund_type,
            COUNT(DISTINCT f.fund_code) as fund_count,
            ROUND(AVG(s.avg_return) * 100, 4) as avg_return_pct,
            ROUND(AVG(s.volatility) * 100, 4) as avg_volatility_pct,
            ROUND(AVG(s.win_rate), 2) as avg_win_rate
        FROM (
            SELECT 
                fund_code,
                AVG(daily_return) as avg_return,
                STDDEV(daily_return) as volatility,
                SUM(CASE WHEN daily_return > 0 THEN 1 ELSE 0 END) * 100.0 / COUNT(*) as win_rate
            FROM fund_nav
            GROUP BY fund_code
        ) s
        JOIN fund f ON s.fund_code = f.fund_code
        WHERE f.fund_type IS NOT NULL
        GROUP BY f.fund_type
        ORDER BY fund_count DESC
    """)
    
    distribution = cursor.fetchall()
    cursor.close()
    
    return distribution

def batch_verify():
    """批量验证基金数据质量"""
    print("=" * 80)
    print("基金数据质量验证报告")
    print("=" * 80)
    
    conn = create_db_connection()
    
    # 随机抽取 50 只基金进行详细验证
    cursor = conn.cursor()
    cursor.execute("""
        SELECT fund_code, fund_name, fund_type, nav_record_count
        FROM fund 
        WHERE nav_record_count > 100
        ORDER BY RAND()
        LIMIT 50
    """)
    
    funds = cursor.fetchall()
    cursor.close()
    
    print(f"\n抽取 {len(funds)} 只基金进行统计学验证\n")
    
    results = []
    passed_count = 0
    
    for fund_code, fund_name, fund_type, nav_count in funds:
        result = verify_nav_statistics(conn, fund_code, fund_name)
        if result:
            results.append(result)
            if result['passed']:
                passed_count += 1
                print(f"✅ {fund_code} - {fund_name} ({fund_type})")
            else:
                print(f"⚠️  {fund_code} - {fund_name} ({fund_type})")
                for issue in result['issues']:
                    print(f"    {issue}")
    
    # 整体统计
    print("\n" + "=" * 80)
    print("验证结果统计")
    print("=" * 80)
    
    print(f"\n验证基金数：{len(results)}")
    print(f"✅ 通过验证：{passed_count} 只 ({passed_count/len(results)*100:.1f}%)")
    print(f"⚠️  存在问题：{len(results) - passed_count} 只 ({(len(results) - passed_count)/len(results)*100:.1f}%)")
    
    # 按类型统计
    print("\n【按基金类型统计】")
    distribution = verify_data_distribution(conn)
    
    print(f"{'基金类型':<30} {'基金数':>8} {'日均收益%':>12} {'波动率%':>12} {'胜率%':>10}")
    print("-" * 80)
    for row in distribution:
        fund_type = row[0] or '未知'
        print(f"{fund_type:<30} {row[1]:>8} {row[2]:>12.4f} {row[3]:>12.4f} {row[4]:>10.2f}")
    
    # 保存验证记录
    cursor = conn.cursor()
    cursor.execute("""
        INSERT INTO data_sync_log (
            sync_type, sync_source, sync_status,
            fund_count, records_inserted, records_failed,
            start_time, operator, batch_id, remarks
        ) VALUES (
            'data_verification',
            'statistical_analysis',
            %s,
            %s,
            %s,
            %s,
            NOW(),
            'verify_data_quality.py',
            %s,
            %s
        )
    """, (
        'success' if passed_count > len(results) * 0.8 else 'partial',
        len(results),
        passed_count,
        len(results) - passed_count,
        f'VERIFY-{datetime.now().strftime("%Y%m%d")}',
        f'验证{len(results)}只基金，通过{passed_count}只，通过率{passed_count/len(results)*100:.1f}%'
    ))
    conn.commit()
    cursor.close()
    conn.close()
    
    print("\n" + "=" * 80)
    print(f"验证完成！详细记录已保存到 data_sync_log 表")
    print("=" * 80)

if __name__ == '__main__':
    batch_verify()
