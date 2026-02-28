#!/bin/bash
# FundWise 性能监控脚本
# 使用: ./monitor.sh [check|status|report]

LOG_FILE="/root/.openclaw/workspace/fundwise-backtest/logs/monitor.log"
ALERT_FILE="/root/.openclaw/workspace/fundwise-backtest/logs/alerts.log"

# 阈值配置
CPU_THRESHOLD=80
MEM_THRESHOLD=80
RESPONSE_THRESHOLD=3  # 秒

log() {
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] $1" | tee -a "$LOG_FILE"
}

alert() {
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] [ALERT] $1" | tee -a "$ALERT_FILE"
}

# 检查后端服务
check_backend() {
    local start_time=$(date +%s.%N)
    local response=$(curl -s -o /dev/null -w "%{http_code}" http://localhost:3389/api/health 2>/dev/null)
    local end_time=$(date +%s.%N)
    local duration=$(echo "$end_time - $start_time" | bc)
    
    if [ "$response" == "200" ]; then
        echo "✅ 后端服务: UP (响应时间: ${duration}s)"
        if (( $(echo "$duration > $RESPONSE_THRESHOLD" | bc -l) )); then
            alert "后端响应时间过长: ${duration}s"
        fi
        return 0
    else
        echo "❌ 后端服务: DOWN"
        alert "后端服务不可用"
        return 1
    fi
}

# 检查前端服务
check_frontend() {
    local response=$(curl -s -o /dev/null -w "%{http_code}" http://localhost/ 2>/dev/null)
    if [ "$response" == "200" ]; then
        echo "✅ 前端服务: UP"
        return 0
    else
        echo "❌ 前端服务: DOWN"
        return 1
    fi
}

# 检查数据库
check_database() {
    local result=$(mysql -u fundwise -ppassword123 -e "SELECT 1" 2>/dev/null)
    if [ -n "$result" ]; then
        echo "✅ 数据库: UP"
        return 0
    else
        echo "❌ 数据库: DOWN"
        alert "数据库不可用"
        return 1
    fi
}

# 检查系统资源
check_resources() {
    local cpu_usage=$(top -bn1 | grep "Cpu(s)" | awk '{print $2}' | cut -d'%' -f1)
    local mem_usage=$(free | grep Mem | awk '{printf "%.1f", $3/$2 * 100}')
    local disk_usage=$(df -h / | awk 'NR==2 {print $5}' | tr -d '%')
    
    echo "📊 系统资源:"
    echo "   CPU: ${cpu_usage}%"
    echo "   内存: ${mem_usage}%"
    echo "   磁盘: ${disk_usage}%"
    
    # 告警检查
    if (( $(echo "$cpu_usage > $CPU_THRESHOLD" | bc -l) )); then
        alert "CPU使用率过高: ${cpu_usage}%"
    fi
    if (( $(echo "$mem_usage > $MEM_THRESHOLD" | bc -l) )); then
        alert "内存使用率过高: ${mem_usage}%"
    fi
}

# 检查Java进程
check_java() {
    local java_pid=$(pgrep -f "java.*fundwise")
    if [ -n "$java_pid" ]; then
        local mem=$(ps -p $java_pid -o %mem --no-headers | tr -d ' ')
        local cpu=$(ps -p $java_pid -o %cpu --no-headers | tr -d ' ')
        echo "☕ Java进程: PID=$java_pid, CPU=${cpu}%, MEM=${mem}%"
    else
        echo "⚠️ Java进程未运行"
    fi
}

# 测试API性能
test_api_performance() {
    echo "🚀 API性能测试:"
    
    # 健康检查
    local t1=$(date +%s.%N)
    curl -s http://localhost:3389/api/health > /dev/null 2>&1
    local t2=$(date +%s.%N)
    echo "   /api/health: $(echo "$t2 - $t1" | bc)s"
    
    # 基金筛选
    local t3=$(date +%s.%N)
    curl -s "http://localhost:3389/api/v1/funds/screen?page=1&pageSize=20" > /dev/null 2>&1
    local t4=$(date +%s.%N)
    local screen_time=$(echo "$t4 - $t3" | bc)
    echo "   /api/v1/funds/screen: ${screen_time}s"
    
    if (( $(echo "$screen_time > $RESPONSE_THRESHOLD" | bc -l) )); then
        alert "筛选API响应过慢: ${screen_time}s"
    fi
    
    # 推荐接口
    local t5=$(date +%s.%N)
    curl -s -X POST http://localhost:3389/api/recommend \
        -H "Content-Type: application/json" \
        -d '{"initialCapital":10000,"investmentPeriod":3}' > /dev/null 2>&1
    local t6=$(date +%s.%N)
    echo "   /api/recommend: $(echo "$t6 - $t5" | bc)s"
}

# 检查数据库表状态
check_db_stats() {
    echo "💾 数据库统计:"
    mysql -u fundwise -ppassword123 -N -e "
        USE fundwise_backtest;
        SELECT CONCAT('   基金数量: ', COUNT(*)) FROM fund;
        SELECT CONCAT('   净值记录: ', COUNT(*)) FROM fund_nav;
    " 2>/dev/null
}

# 主检查函数
check_all() {
    echo "======================================"
    echo "  FundWise 系统监控 - $(date '+%Y-%m-%d %H:%M:%S')"
    echo "======================================"
    echo ""
    
    check_backend
    check_frontend
    check_database
    echo ""
    check_resources
    echo ""
    check_java
    echo ""
    test_api_performance
    echo ""
    check_db_stats
    echo ""
    echo "======================================"
}

# 简短状态
status() {
    local backend=$(curl -s http://localhost:3389/api/health 2>/dev/null | grep -c "UP" || echo 0)
    local frontend=$(curl -s -o /dev/null -w "%{http_code}" http://localhost/ 2>/dev/null)
    
    echo "后端: $([ "$backend" == "1" ] && echo "UP" || echo "DOWN")"
    echo "前端: $([ "$frontend" == "200" ] && echo "UP" || echo "DOWN")"
}

case "${1:-check}" in
    check)
        check_all
        ;;
    status)
        status
        ;;
    report)
        check_all > /root/.openclaw/workspace/fundwise-backtest/logs/status_report.txt
        echo "报告已生成: logs/status_report.txt"
        ;;
    *)
        echo "用法: $0 {check|status|report}"
        exit 1
        ;;
esac