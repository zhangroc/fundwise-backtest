#!/usr/bin/env python3
import akshare as ak
import pandas as pd

print("akshare版本:", ak.__version__)
print("\n可用函数:")
functions = [f for f in dir(ak) if 'fund' in f.lower()]
for func in functions[:20]:  # 只显示前20个
    print(f"  - {func}")

print("\n尝试获取基金列表...")
try:
    # 尝试不同的函数
    fund_list = ak.fund_em_open_fund_daily()
    print(f"成功获取基金数据，形状: {fund_list.shape}")
    print(f"列名: {list(fund_list.columns)}")
    print("前3行:")
    print(fund_list.head(3))
except Exception as e:
    print(f"错误: {e}")
    
    # 尝试另一种方法
    try:
        print("\n尝试另一种方法...")
        fund_list2 = ak.fund_em_fund_name()
        print(f"成功获取基金数据，形状: {fund_list2.shape}")
        print(f"列名: {list(fund_list2.columns)}")
    except Exception as e2:
        print(f"第二种方法也失败: {e2}")