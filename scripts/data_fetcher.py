#!/usr/bin/env python3
"""
基金数据抓取与更新脚本

功能：
1. 从 akshare 获取基金列表和净值数据
2. 清洗、验证数据
3. 增量更新到数据库
4. 支持多源比对和数据版本控制
"""

import sys
import os
import logging
from datetime import datetime, timedelta
from typing import Dict, List, Optional, Tuple
import pandas as pd
import numpy as np

# 将项目根目录添加到 Python 路径，以便导入自定义模块
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

# 配置日志
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s',
    handlers=[
        logging.FileHandler('data_fetcher.log'),
        logging.StreamHandler(sys.stdout)
    ]
)
logger = logging.getLogger(__name__)


def setup_database_connection():
    """
    设置数据库连接
    从环境变量或配置文件中读取数据库连接信息
    """
    # TODO: 实现数据库连接
    # 示例：使用 SQLAlchemy
    # from sqlalchemy import create_engine
    # engine = create_engine('mysql+pymysql://user:pass@localhost/fundwise_backtest')
    # return engine
    pass


def fetch_fund_list_from_akshare() -> pd.DataFrame:
    """
    从 akshare 获取全市场基金列表
    
    Returns:
        DataFrame 包含基金代码、名称、类型等基本信息
    """
    try:
        import akshare as ak
        logger.info("开始从 akshare 获取基金列表...")
        
        # 这里需要根据 akshare 的实际接口调整
        # 示例：获取开放式基金列表
        fund_list = ak.fund_em_open_fund_info()
        
        # 数据清洗和重命名列
        if not fund_list.empty:
            # 重命名列以匹配数据库字段
            column_mapping = {
                '基金代码': 'code',
                '基金简称': 'name',
                '基金类型': 'type',
                '基金管理人': 'company',
                '成立日期': 'establishment_date',
                '最新规模(亿元)': 'latest_size'
            }
            fund_list = fund_list.rename(columns=column_mapping)
            
            # 转换数据类型
            if 'establishment_date' in fund_list.columns:
                fund_list['establishment_date'] = pd.to_datetime(fund_list['establishment_date']).dt.date
            
            if 'latest_size' in fund_list.columns:
                # 处理规模数据（去除单位，转换为数字）
                fund_list['latest_size'] = fund_list['latest_size'].replace({'亿元': '', '亿': ''}, regex=True)
                fund_list['latest_size'] = pd.to_numeric(fund_list['latest_size'], errors='coerce')
            
            # 识别指数基金（通过名称包含'指数'或'ETF'）
            fund_list['is_index_fund'] = fund_list['name'].str.contains(r'指数|ETF', na=False)
            
            logger.info(f"成功获取 {len(fund_list)} 只基金信息")
            return fund_list
        else:
            logger.warning("从 akshare 获取的基金列表为空")
            return pd.DataFrame()
            
    except Exception as e:
        logger.error(f"获取基金列表失败: {e}", exc_info=True)
        return pd.DataFrame()


def fetch_fund_nav_from_akshare(fund_code: str, start_date: str = None, end_date: str = None) -> pd.DataFrame:
    """
    从 akshare 获取单只基金的历史净值
    
    Args:
        fund_code: 基金代码
        start_date: 开始日期 (YYYY-MM-DD)
        end_date: 结束日期 (YYYY-MM-DD)
        
    Returns:
        DataFrame 包含日期和净值数据
    """
    try:
        import akshare as ak
        
        # 设置默认日期范围（过去3年）
        if end_date is None:
            end_date = datetime.now().strftime('%Y-%m-%d')
        if start_date is None:
            start_date = (datetime.now() - timedelta(days=3*365)).strftime('%Y-%m-%d')
        
        logger.debug(f"获取基金 {fund_code} 净值数据，范围: {start_date} 到 {end_date}")
        
        # 根据 akshare 接口调整
        # 示例：获取基金净值
        nav_data = ak.fund_em_open_fund_info(fund=fund_code, indicator="累计净值走势")
        
        if not nav_data.empty:
            # 数据清洗
            nav_data = nav_data.rename(columns={
                '净值日期': 'nav_date',
                '累计净值': 'accumulated_nav',
                '单位净值': 'unit_nav',
                '日增长率': 'daily_return'
            })
            
            # 转换日期格式
            nav_data['nav_date'] = pd.to_datetime(nav_data['nav_date']).dt.date
            
            # 转换数值类型
            numeric_cols = ['accumulated_nav', 'unit_nav', 'daily_return']
            for col in numeric_cols:
                if col in nav_data.columns:
                    nav_data[col] = pd.to_numeric(nav_data[col], errors='coerce')
            
            # 过滤日期范围
            mask = (nav_data['nav_date'] >= pd.to_datetime(start_date).date()) & \
                   (nav_data['nav_date'] <= pd.to_datetime(end_date).date())
            nav_data = nav_data[mask]
            
            logger.debug(f"基金 {fund_code} 获取到 {len(nav_data)} 条净值数据")
            return nav_data
        else:
            logger.warning(f"基金 {fund_code} 净值数据为空")
            return pd.DataFrame()
            
    except Exception as e:
        logger.error(f"获取基金 {fund_code} 净值失败: {e}")
        return pd.DataFrame()


def validate_nav_data(nav_data: pd.DataFrame, fund_code: str) -> Tuple[pd.DataFrame, List[str]]:
    """
    验证净值数据的质量
    
    Args:
        nav_data: 净值数据 DataFrame
        fund_code: 基金代码（用于日志）
        
    Returns:
        (清洗后的数据, 问题列表)
    """
    issues = []
    
    if nav_data.empty:
        issues.append(f"基金 {fund_code} 净值数据为空")
        return nav_data, issues
    
    # 1. 检查缺失值
    missing_accumulated = nav_data['accumulated_nav'].isna().sum()
    if missing_accumulated > 0:
        issues.append(f"基金 {fund_code} 有 {missing_accumulated} 条累计净值缺失")
        # 删除缺失累计净值的数据
        nav_data = nav_data.dropna(subset=['accumulated_nav'])
    
    # 2. 检查日期连续性（简单检查）
    nav_data = nav_data.sort_values('nav_date')
    date_diff = nav_data['nav_date'].diff().dt.days
    gaps = date_diff[date_diff > 1]  # 间隔大于1天
    if not gaps.empty:
        issues.append(f"基金 {fund_code} 存在日期不连续，最大间隔 {gaps.max()} 天")
    
    # 3. 检查极端收益率（简单异常值检测）
    if 'daily_return' in nav_data.columns:
        extreme_returns = nav_data[nav_data['daily_return'].abs() > 0.15]  # 单日涨跌幅超过15%
        if not extreme_returns.empty:
            issues.append(f"基金 {fund_code} 存在极端日收益率: {extreme_returns['daily_return'].tolist()}")
    
    # 4. 检查累计净值单调性（应大致单调递增，除分红外）
    # 这里可以更复杂，但简单版本：检查是否有累计净值下降超过5%的情况（可能数据错误）
    nav_data['nav_change'] = nav_data['accumulated_nav'].pct_change()
    large_drops = nav_data[nav_data['nav_change'] < -0.05]
    if not large_drops.empty:
        issues.append(f"基金 {fund_code} 累计净值出现异常下跌")
    
    return nav_data, issues


def update_fund_to_database(engine, fund_info: pd.DataFrame):
    """
    更新基金基本信息到数据库
    """
    # TODO: 实现数据库更新逻辑
    # 1. 读取数据库中现有的基金
    # 2. 比较并插入新基金
    # 3. 更新已有基金的信息（规模等）
    pass


def update_nav_to_database(engine, fund_id: int, nav_data: pd.DataFrame, data_source: str = 'akshare'):
    """
    更新基金净值数据到数据库
    使用 snapshot_date 实现版本控制
    """
    # TODO: 实现数据库更新逻辑
    # 1. 设置本次的快照日期（今天）
    # 2. 检查哪些日期在数据库中不存在（或同一日期但快照日期更旧）
    # 3. 插入新数据
    pass


def main():
    """主函数"""
    logger.info("=== 开始基金数据更新任务 ===")
    
    # 1. 设置数据库连接
    engine = setup_database_connection()
    if not engine:
        logger.error("数据库连接失败，任务终止")
        return
    
    # 2. 获取基金列表
    fund_list = fetch_fund_list_from_akshare()
    if fund_list.empty:
        logger.error("获取基金列表失败，任务终止")
        return
    
    # 3. 更新基金基本信息到数据库
    update_fund_to_database(engine, fund_list)
    
    # 4. 更新基金净值数据（示例：只更新前10只进行测试）
    sample_funds = fund_list.head(10)['code'].tolist()
    all_issues = []
    
    for fund_code in sample_funds:
        logger.info(f"处理基金: {fund_code}")
        
        # 获取净值数据
        nav_data = fetch_fund_nav_from_akshare(fund_code)
        
        # 验证数据
        cleaned_nav, issues = validate_nav_data(nav_data, fund_code)
        all_issues.extend(issues)
        
        if not cleaned_nav.empty:
            # TODO: 需要先从数据库查询 fund_id
            fund_id = 1  # 示例
            update_nav_to_database(engine, fund_id, cleaned_nav)
        else:
            logger.warning(f"基金 {fund_code} 清洗后无有效净值数据")
    
    # 5. 输出问题总结
    if all_issues:
        logger.warning("数据验证发现以下问题:")
        for issue in all_issues:
            logger.warning(f"  - {issue}")
    else:
        logger.info("数据验证未发现重大问题")
    
    logger.info("=== 基金数据更新任务完成 ===")


if __name__ == "__main__":
    main()