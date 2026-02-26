#!/bin/bash
# FundWise 基金回测平台 - 停止脚本
# 使用：./stop.sh

set -e

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

info() { echo -e "${BLUE}[INFO]${NC} $1"; }
success() { echo -e "${GREEN}[SUCCESS]${NC} $1"; }
warning() { echo -e "${YELLOW}[WARNING]${NC} $1"; }
error() { echo -e "${RED}[ERROR]${NC} $1"; }

stop_service() {
    local port=$1
    local service_name=$2
    
    # 查找并杀死监听指定端口的进程
    local pids=$(lsof -ti:$port 2>/dev/null || ps aux | grep -E "java.*$port|python.*http.server.*$port" | grep -v grep | awk '{print $2}')
    
    if [[ -n "$pids" ]]; then
        info "停止 $service_name (端口: $port)..."
        echo "$pids" | xargs kill -9 2>/dev/null && {
            success "已停止 $service_name"
        } || {
            warning "停止 $service_name 时遇到问题"
        }
        # 等待进程完全停止
        sleep 2
    else
        info "$service_name 未运行 (端口: $port)"
    fi
}

stop_all_services() {
    echo -e "${BLUE}╔══════════════════════════════════════════╗${NC}"
    echo -e "${BLUE}║    FundWise基金回测平台 - 停止脚本     ║${NC}"
    echo -e "${BLUE}╚══════════════════════════════════════════╝${NC}"
    echo ""
    
    # 停止后端服务 (端口3389)
    stop_service 3389 "后端API服务"
    
    # 停止前端服务 (尝试常见端口)
    stop_service 80 "前端Web服务"
    stop_service 8080 "前端Web服务(8080)"
    stop_service 3000 "前端Web服务(3000)"
    
    # 额外清理：Spring Boot DevTools进程
    local spring_pids=$(ps aux | grep "spring-boot:run" | grep -v grep | awk '{print $2}')
    if [[ -n "$spring_pids" ]]; then
        info "清理Spring Boot进程..."
        echo "$spring_pids" | xargs kill -9 2>/dev/null
    fi
    
    # 清理Python HTTP服务器进程
    local python_pids=$(ps aux | grep "http.server" | grep -v grep | awk '{print $2}')
    if [[ -n "$python_pids" ]]; then
        info "清理Python HTTP服务器进程..."
        echo "$python_pids" | xargs kill -9 2>/dev/null
    fi
}

check_status() {
    echo -e "\n${BLUE}📊 服务状态检查:${NC}"
    
    local running=false
    
    # 检查后端
    if lsof -ti:3389 &>/dev/null; then
        echo -e "${RED}❌ 后端API服务: 仍在运行 (端口: 3389)${NC}"
        running=true
    else
        echo -e "${GREEN}✅ 后端API服务: 已停止${NC}"
    fi
    
    # 检查前端
    if lsof -ti:80 &>/dev/null || lsof -ti:8080 &>/dev/null || lsof -ti:3000 &>/dev/null; then
        echo -e "${RED}❌ 前端Web服务: 仍在运行${NC}"
        running=true
    else
        echo -e "${GREEN}✅ 前端Web服务: 已停止${NC}"
    fi
    
    if [[ $running == true ]]; then
        echo -e "\n${YELLOW}⚠️  仍有服务在运行，请等待或强制停止:${NC}"
        echo "   ps aux | grep -E '(java.*3389|python.*http.server)'"
        echo "   sudo kill -9 <进程ID>"
    else
        echo -e "\n${GREEN}🎉 所有服务已成功停止！${NC}"
    fi
}

main() {
    stop_all_services
    sleep 1  # 等待进程完全停止
    check_status
    
    echo -e "\n${BLUE}🔧 清理完成:${NC}"
    echo "   下次启动: ./start.sh"
    echo "   查看日志: ls -la logs/"
    echo "   完全清理: rm -rf logs/*"
}

main "$@"