#!/usr/bin/env python3
import akshare as ak
import pandas as pd

print("akshare版本:", ak.__version__)
print("\n所有基金相关函数:")
functions = [f for f in dir(ak) if 'fund' in f.lower()]
for i, func in enumerate(functions):
    print(f"  {i+1:2d}. {func}")

print("\n尝试基金函数:")
# 尝试不同的函数
possible_functions = [
    'fund_em_open_fund_daily',
    'fund_open_fund_daily', 
    'fund_em_fund_name',
    'fund_name_em',
    'fund_etf_fund_daily_em',
    'fund_etf_fund_info_em',
    'fund_aum_em',
]

for func_name in possible_functions:
    try:
        if hasattr(ak, func_name):
            print(f"\n调用 {func_name}...")
            result = getattr(ak, func_name)()
            print(f"  成功! 形状: {result.shape}")
            print(f"  列名: {list(result.columns)}")
            print(f"  示例数据:\n{result.head(2).to_string()}")
            break
    except Exception as e:
        print(f"  {func_name} 失败: {e}")

# 测试基本的基金信息获取
print("\n测试基础基金查询:")
try:
    # 使用 fund 函数（可能是指令函数）
    if hasattr(ak, 'fund'):
        print("尝试 ak.fund()...")
        fund_info = ak.fund()
        print(f"成功获取: {type(fund_info)}")
        if isinstance(fund_info, pd.DataFrame):
            print(f"形状: {fund_info.shape}")
except Exception as e:
    print(f"fund() 失败: {e}")