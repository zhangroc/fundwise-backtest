#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
数据同步日志记录工具
用于记录基金数据同步的历史和统计信息
"""

import pymysql
from datetime import datetime
import time

# 数据库配置
DB_CONFIG = {
    'host': 'localhost',
    'port': 3306,
    'user': 'root',
    'unix_socket': '/var/run/mysqld/mysqld.sock',
    'database': 'fundwise_backtest'
}

class DataSyncLogger:
    """数据同步日志记录器"""
    
    def __init__(self):
        self.conn = None
        self.sync_id = None
        self.start_time = None
    
    def connect(self):
        """连接数据库"""
        self.conn = pymysql.connect(**DB_CONFIG, charset='utf8mb4')
    
    def close(self):
        """关闭连接"""
        if self.conn:
            self.conn.close()
    
    def start_sync(self, sync_type, sync_source, fund_count=0, 
                   date_start=None, date_end=None, operator='script',
                   script_path='', batch_id='', remarks=''):
        """开始同步记录"""
        if not self.conn:
            self.connect()
        
        self.start_time = datetime.now()
        
        cursor = self.conn.cursor()
        sql = """
        INSERT INTO data_sync_log (
            sync_type, sync_source, sync_status,
            fund_count, date_start, date_end,
            start_time, operator, script_path, batch_id, remarks
        ) VALUES (%s, %s, 'running', %s, %s, %s, %s, %s, %s, %s, %s)
        """
        
        cursor.execute(sql, (
            sync_type, sync_source, fund_count,
            date_start, date_end, self.start_time,
            operator, script_path, batch_id, remarks
        ))
        
        self.sync_id = cursor.lastrowid
        self.conn.commit()
        cursor.close()
        
        print(f"[SYNC-{self.sync_id}] 开始同步：{sync_type}")
        return self.sync_id
    
    def update_sync_progress(self, records_inserted=0, records_updated=0, 
                            records_failed=0, error_message=''):
        """更新同步进度"""
        if not self.sync_id:
            return
        
        cursor = self.conn.cursor()
        sql = """
        UPDATE data_sync_log
        SET records_inserted = %s,
            records_updated = %s,
            records_failed = %s,
            error_message = %s,
            sync_status = 'running'
        WHERE id = %s
        """
        
        cursor.execute(sql, (
            records_inserted, records_updated, records_failed,
            error_message, self.sync_id
        ))
        
        self.conn.commit()
        cursor.close()
    
    def complete_sync(self, records_inserted=0, records_updated=0, 
                     records_failed=0, status='success', error_message=''):
        """完成同步记录"""
        if not self.sync_id:
            return
        
        end_time = datetime.now()
        duration = int((end_time - self.start_time).total_seconds())
        
        cursor = self.conn.cursor()
        sql = """
        UPDATE data_sync_log
        SET records_inserted = %s,
            records_updated = %s,
            records_failed = %s,
            sync_status = %s,
            error_message = %s,
            end_time = %s,
            duration_seconds = %s
        WHERE id = %s
        """
        
        cursor.execute(sql, (
            records_inserted, records_updated, records_failed,
            status, error_message, end_time, duration, self.sync_id
        ))
        
        self.conn.commit()
        cursor.close()
        
        print(f"[SYNC-{self.sync_id}] 同步完成：{status}, 耗时 {duration}秒")
    
    def fail_sync(self, error_message, error_detail=''):
        """标记同步失败"""
        if not self.sync_id:
            return
        
        end_time = datetime.now()
        duration = int((end_time - self.start_time).total_seconds())
        
        cursor = self.conn.cursor()
        sql = """
        UPDATE data_sync_log
        SET sync_status = 'failed',
            error_message = %s,
            error_detail = %s,
            end_time = %s,
            duration_seconds = %s
        WHERE id = %s
        """
        
        cursor.execute(sql, (
            error_message, error_detail, end_time, duration, self.sync_id
        ))
        
        self.conn.commit()
        cursor.close()
        
        print(f"[SYNC-{self.sync_id}] 同步失败：{error_message}")
    
    def create_quality_snapshot(self, snapshot_date=None, remarks=''):
        """创建数据质量快照"""
        if not self.conn:
            self.connect()
        
        cursor = self.conn.cursor()
        
        # 统计各质量等级数量
        cursor.execute("""
            SELECT 
                SUM(CASE WHEN data_quality = 'excellent' THEN 1 ELSE 0 END) as excellent,
                SUM(CASE WHEN data_quality = 'good' THEN 1 ELSE 0 END) as good,
                SUM(CASE WHEN data_quality = 'fair' THEN 1 ELSE 0 END) as fair,
                SUM(CASE WHEN data_quality = 'poor' THEN 1 ELSE 0 END) as poor,
                SUM(CASE WHEN data_quality = 'unknown' THEN 1 ELSE 0 END) as unknown,
                COUNT(*) as total,
                COALESCE((SELECT COUNT(*) FROM fund_nav), 0) as nav_records,
                COALESCE((SELECT AVG(nav_record_count) FROM fund WHERE nav_record_count > 0), 0) as avg_records
            FROM fund
        """)
        
        stats = cursor.fetchone()
        
        if snapshot_date is None:
            snapshot_date = datetime.now().date()
        
        sql = """
        INSERT INTO data_quality_snapshot (
            snapshot_date, excellent_count, good_count, fair_count,
            poor_count, unknown_count, total_funds, total_nav_records,
            avg_records_per_fund, remarks
        ) VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s)
        """
        
        cursor.execute(sql, (
            snapshot_date,
            stats[0] or 0,  # excellent
            stats[1] or 0,  # good
            stats[2] or 0,  # fair
            stats[3] or 0,  # poor
            stats[4] or 0,  # unknown
            stats[5] or 0,  # total
            stats[6] or 0,  # nav_records
            round(stats[7] or 0, 2),  # avg_records
            remarks
        ))
        
        self.conn.commit()
        cursor.close()
        
        print(f"创建质量快照：{snapshot_date}, 优质={stats[0]}, 良好={stats[1]}, 无数据={stats[3]}")

# 使用示例
if __name__ == '__main__':
    logger = DataSyncLogger()
    
    try:
        # 开始同步
        logger.start_sync(
            sync_type='nav_data',
            sync_source='akshare',
            fund_count=100,
            date_start='2020-01-01',
            date_end='2026-02-27',
            operator='import_nav_data.py',
            script_path='/root/.openclaw/workspace/fundwise-backtest/scripts/import_nav_data.py',
            batch_id='BATCH-20260227-002',
            remarks='导入 100 只基金的历史净值数据'
        )
        
        # 模拟同步过程
        time.sleep(2)
        
        # 更新进度
        logger.update_sync_progress(
            records_inserted=36500,
            records_failed=2
        )
        
        # 完成同步
        logger.complete_sync(
            records_inserted=36500,
            records_failed=2,
            status='partial'
        )
        
        # 创建质量快照
        logger.create_quality_snapshot(remarks='定期快照')
        
    except Exception as e:
        logger.fail_sync(str(e))
    finally:
        logger.close()
