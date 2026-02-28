#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
改进的基金数据获取脚本
使用多个数据源获取真实基金数据
"""

import pymysql
import requests
import time
from datetime import datetime
import json

# 数据库配置
DB_CONFIG = {
    'host': 'localhost',
    'port': 3306,
    'user': 'root',
    'unix_socket': '/var/run/mysqld/mysqld.sock',
    'database': 'fundwise_backtest'
}

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

def fetch_from_tiantian_fund(limit=100):
    """
    从天天基金网获取基金列表
    http://fund.eastmoney.com/data/fundranking.html
    """
    print("正在从天天基金网获取数据...")
    
    url = "http://fund.eastmoney.com/data/rankhandler.aspx"
    params = {
        'op': 'ph',
        'dt': 'kf',
        'ft': 'all',  # 全部类型
        'rs': '',
        'gs': '0',
        'sort': 'z1nzb',  # 按近 1 年收益排序
        'st': 'desc',
        'es': '',
        'qdii': '',
        'pi': '1',
        'pn': str(limit),
        'v': '0.123456789'
    }
    
    try:
        response = requests.get(url, params=params, timeout=30)
        response.encoding = 'gbk'  # 天天基金使用 GBK 编码
        
        # 解析返回的 JavaScript 数据
        content = response.text
        # 格式：var rankData = {...};
        start = content.find('{')
        end = content.rfind('}') + 1
        json_str = content[start:end]
        
        # 替换 JS 变量名为 JSON
        json_str = json_str.replace('var rankData = ', '').rstrip(';')
        
        data = json.loads(json_str)
        
        if 'datas' not in data:
            print("未获取到数据")
            return []
        
        funds = []
        for item in data['datas']:
            # 数据格式：基金代码 | 基金简称 | 日期 | 单位净值 | 累计净值 | ...
            parts = item.split('|')
            if len(parts) >= 6:
                funds.append({
                    'code': parts[0],
                    'name': parts[1],
                    'date': parts[2],
                    'unit_nav': parts[3],
                    'accumulated_nav': parts[4],
                    'growth_rate': parts[5] if len(parts) > 5 else '0'
                })
        
        print(f"获取到 {len(funds)} 只基金")
        return funds
        
    except Exception as e:
        print(f"获取失败：{e}")
        return []

def fetch_fund_detail_from_tiantian(fund_code):
    """获取单个基金的详细信息"""
    url = f"http://fund.eastmoney.com/pingzhongdata/{fund_code}.js"
    
    try:
        response = requests.get(url, timeout=10)
        response.encoding = 'utf-8'
        content = response.text
        
        # 提取基金类型
        fund_type = ''
        if '股票型' in content:
            fund_type = '股票型'
        elif '混合型' in content:
            fund_type = '混合型'
        elif '债券型' in content:
            fund_type = '债券型'
        elif '指数型' in content or 'ETF' in content:
            fund_type = '指数型'
        
        # 提取风险等级
        risk_level = ''
        if '低风险' in content:
            risk_level = '低风险'
        elif '中低风险' in content:
            risk_level = '中低风险'
        elif '中风险' in content:
            risk_level = '中风险'
        elif '中高风险' in content:
            risk_level = '中高风险'
        elif '高风险' in content:
            risk_level = '高风险'
        
        return {
            'type': fund_type,
            'risk_level': risk_level
        }
        
    except Exception as e:
        return None

def save_fund_to_db(conn, fund_code, fund_name, fund_type='', risk_level='', company=''):
    """保存基金到数据库"""
    cursor = conn.cursor()
    
    try:
        sql = """
        INSERT INTO fund (code, name, type, company, risk_level, is_index_fund, created_at, updated_at)
        VALUES (%s, %s, %s, %s, %s, %s, NOW(), NOW())
        ON DUPLICATE KEY UPDATE
            name = VALUES(name),
            type = VALUES(type),
            company = VALUES(company),
            risk_level = VALUES(risk_level),
            is_index_fund = VALUES(is_index_fund),
            updated_at = NOW()
        """
        
        is_index = '指数' in fund_type or 'ETF' in fund_name
        
        cursor.execute(sql, (
            fund_code,
            fund_name,
            fund_type,
            company,
            risk_level,
            is_index
        ))
        
        conn.commit()
        return True
        
    except Exception as e:
        conn.rollback()
        print(f"保存基金 {fund_code} 失败：{e}")
        return False
        
    finally:
        cursor.close()

def batch_import_from_tiantian(limit=100):
    """批量导入基金数据"""
    print("=" * 60)
    print("开始从天天基金网导入数据")
    print("=" * 60)
    
    conn = create_db_connection()
    
    try:
        # 获取基金列表
        funds = fetch_from_tiantian_fund(limit=limit)
        
        if not funds:
            print("获取基金列表失败")
            return
        
        success_count = 0
        
        for i, fund in enumerate(funds):
            fund_code = fund['code']
            fund_name = fund['name']
            
            print(f"[{i+1}/{len(funds)}] 处理：{fund_code} - {fund_name}")
            
            # 每 10 只基金获取一次详细信息
            if i % 10 == 0:
                detail = fetch_fund_detail_from_tiantian(fund_code)
                if detail:
                    fund_type = detail.get('type', '')
                    risk_level = detail.get('risk_level', '')
                else:
                    fund_type = ''
                    risk_level = ''
            else:
                fund_type = ''
                risk_level = ''
            
            # 保存到数据库
            if save_fund_to_db(conn, fund_code, fund_name, fund_type, risk_level):
                success_count += 1
            
            # 避免请求过快
            time.sleep(0.3)
        
        print("\n" + "=" * 60)
        print(f"导入完成！成功：{success_count}/{len(funds)}")
        print("=" * 60)
        
    except Exception as e:
        print(f"导入失败：{e}")
        import traceback
        traceback.print_exc()
        
    finally:
        conn.close()

def generate_mock_nav_data(conn, fund_codes, days=365):
    """为基金生成模拟净值数据（用于演示）"""
    print(f"为 {len(fund_codes)} 只基金生成净值数据...")
    
    cursor = conn.cursor()
    
    import random
    from datetime import timedelta
    
    total_inserted = 0
    
    for i, fund_code in enumerate(fund_codes[:20]):  # 只处理前 20 只
        print(f"[{i+1}/{min(20, len(fund_codes))}] 生成 {fund_code} 的净值数据")
        
        # 获取基金（使用 fund_code 作为 ID）
        fund_id = fund_code  # 直接使用基金代码作为外键
        
        # 生成净值数据
        base_nav = 1.0 + random.random()  # 初始净值 1-2
        data = []
        
        for day in range(days):
            nav_date = datetime.now().date() - timedelta(days=days-day)
            daily_change = (random.random() - 0.48) * 0.05  # 日涨跌幅 -2.4% 到 +2.6%
            base_nav *= (1 + daily_change)
            
            data.append((
                fund_id,
                nav_date,
                base_nav * 1.1,  # 累计净值
                base_nav,  # 单位净值
                daily_change,  # 日增长率
                'generated',
                datetime.now().date()
            ))
        
        # 批量插入（使用 fund_code 作为 fund_id）
        sql = """
        INSERT INTO fund_nav (fund_id, nav_date, accumulated_nav, unit_nav, 
                             daily_return, data_source, snapshot_date, created_at)
        VALUES (%s, %s, %s, %s, %s, %s, %s, NOW())
        ON DUPLICATE KEY UPDATE
            accumulated_nav = VALUES(accumulated_nav),
            unit_nav = VALUES(unit_nav),
            daily_return = VALUES(daily_return)
        """
        
        try:
            cursor.executemany(sql, data)
            conn.commit()
        except Exception as e:
            print(f"插入失败：{e}")
            conn.rollback()
        conn.commit()
        total_inserted += len(data)
    
    cursor.close()
    print(f"生成了 {total_inserted} 条净值记录")

if __name__ == '__main__':
    import argparse
    
    parser = argparse.ArgumentParser(description='基金数据导入脚本')
    parser.add_argument('--mode', choices=['fetch', 'import', 'generate'], default='import',
                       help='运行模式')
    parser.add_argument('--limit', type=int, default=50,
                       help='导入数量')
    
    args = parser.parse_args()
    
    if args.mode == 'fetch':
        funds = fetch_from_tiantian_fund(limit=args.limit)
        for f in funds[:10]:
            print(f"{f['code']} - {f['name']}")
    
    elif args.mode == 'import':
        batch_import_from_tiantian(limit=args.limit)
    
    elif args.mode == 'generate':
        conn = create_db_connection()
        cursor = conn.cursor()
        cursor.execute("SELECT fund_code FROM fund LIMIT %s", (args.limit,))
        codes = [row[0] for row in cursor.fetchall()]
        cursor.close()
        generate_mock_nav_data(conn, codes)
        conn.close()
