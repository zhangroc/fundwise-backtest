#!/bin/bash
# FundWise 基金回测平台 - 一键启动脚本
# 使用：./start.sh
# 确保已安装：Java 17+, Python 3.8+, MySQL 8.0+

set -e  # 遇到错误立即退出

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 打印带颜色的信息
info() { echo -e "${BLUE}[INFO]${NC} $1"; }
success() { echo -e "${GREEN}[SUCCESS]${NC} $1"; }
warning() { echo -e "${YELLOW}[WARNING]${NC} $1"; }
error() { echo -e "${RED}[ERROR]${NC} $1"; }

# 项目根目录
PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BACKEND_DIR="$PROJECT_ROOT/backend"
FRONTEND_DIR="$PROJECT_ROOT/frontend"
SCRIPTS_DIR="$PROJECT_ROOT/scripts"
SQL_DIR="$PROJECT_ROOT/sql"
LOGS_DIR="$PROJECT_ROOT/logs"

# 创建日志目录
mkdir -p "$LOGS_DIR"

# 检查必需工具
check_requirements() {
    info "检查系统依赖..."
    
    # 检查Java
    if ! command -v java &> /dev/null; then
        error "Java未安装，请安装Java 17或更高版本"
        exit 1
    fi
    JAVA_VERSION=$(java -version 2>&1 | head -1 | cut -d'\"' -f2 | cut -d'.' -f1)
    if [[ $JAVA_VERSION -lt 17 ]]; then
        error "需要Java 17或更高版本，当前版本: $JAVA_VERSION"
        exit 1
    fi
    success "Java版本: $(java -version 2>&1 | head -1 | cut -d'\"' -f2)"
    
    # 检查Python
    if ! command -v python3 &> /dev/null; then
        error "Python3未安装，请安装Python 3.8或更高版本"
        exit 1
    fi
    PYTHON_VERSION=$(python3 -c "import sys; print(f'{sys.version_info.major}.{sys.version_info.minor}')")
    success "Python版本: $PYTHON_VERSION"
    
    # 检查MySQL
    if ! command -v mysql &> /dev/null; then
        warning "MySQL客户端未安装，数据库操作可能失败"
    else
        success "MySQL客户端已安装"
    fi
    
    # 检查Maven
    if ! command -v mvn &> /dev/null; then
        error "Maven未安装，请安装Maven 3.6+\nUbuntu/Debian: sudo apt install maven\nCentOS/RHEL: sudo yum install maven\nMac: brew install maven"
        exit 1
    fi
    success "Maven版本: $(mvn -v | head -1 | cut -d' ' -f3)"
}

# 检查并安装Python依赖
install_python_deps() {
    info "检查Python依赖..."
    
    if [[ -f "$SCRIPTS_DIR/requirements.txt" ]]; then
        info "安装Python依赖包..."
        pip3 install -r "$SCRIPTS_DIR/requirements.txt" --quiet
        success "Python依赖安装完成"
    else
        info "安装基础Python包..."
        pip3 install pymysql pandas akshare --quiet
        success "基础Python包安装完成"
    fi
}

# 初始化数据库
init_database() {
    info "初始化数据库..."
    
    # 检查MySQL服务状态
    if ! systemctl is-active --quiet mysql 2>/dev/null && ! service mysql status 2>/dev/null; then
        warning "MySQL服务未运行，尝试启动..."
        sudo systemctl start mysql 2>/dev/null || sudo service mysql start 2>/dev/null || {
            error "无法启动MySQL，请手动启动MySQL服务"
            read -p "是否跳过数据库初始化？(y/n): " -n 1 -r
            echo
            if [[ ! $REPLY =~ ^[Yy]$ ]]; then
                exit 1
            fi
            return
        }
    fi
    
    # 执行SQL脚本
    if [[ -f "$SQL_DIR/schema_v1.sql" ]]; then
        info "创建数据库和表结构..."
        mysql -u root -p < "$SQL_DIR/schema_v1.sql" 2>/dev/null || {
            warning "使用root用户创建数据库失败，尝试使用当前用户"
            mysql < "$SQL_DIR/schema_v1.sql" 2>/dev/null || {
                warning "数据库可能已存在，跳过创建"
            }
        }
        success "数据库初始化完成"
    else
        warning "未找到SQL架构文件，跳过数据库初始化"
    fi
}

# 构建后端项目
build_backend() {
    info "构建后端Spring Boot项目..."
    cd "$BACKEND_DIR"
    
    # 检查pom.xml是否存在
    if [[ ! -f "pom.xml" ]]; then
        error "未找到pom.xml文件"
        exit 1
    fi
    
    info "清理并构建项目..."
    mvn clean compile -q
    success "后端项目构建完成"
}

# 启动后端服务
start_backend() {
    info "启动后端服务 (端口: 3389)..."
    cd "$BACKEND_DIR"
    
    # 检查服务是否已在运行
    if lsof -ti:3389 &>/dev/null; then
        warning "端口3389已被占用，尝试停止现有进程..."
        lsof -ti:3389 | xargs kill -9 2>/dev/null
        sleep 2
    fi
    
    # 启动Spring Boot服务
    info "启动Spring Boot应用..."
    nohup mvn spring-boot:run -Dserver.port=3389 -q > "$LOGS_DIR/backend.log" 2>&1 &
    BACKEND_PID=$!
    
    # 等待服务启动
    info "等待后端服务启动..."
    sleep 10
    
    # 检查服务状态
    if curl -s http://localhost:3389/api/health 2>/dev/null | grep -q "UP"; then
        success "后端服务启动成功! PID: $BACKEND_PID"
        echo "后端日志: $LOGS_DIR/backend.log"
        echo "API地址: http://localhost:3389/api"
    else
        error "后端服务启动失败，检查日志: $LOGS_DIR/backend.log"
        exit 1
    fi
}

# 启动前端服务
start_frontend() {
    info "启动前端服务 (端口: 80)..."
    cd "$FRONTEND_DIR"
    
    # 检查端口80权限
    if [[ $EUID -ne 0 ]] && [[ $(uname -s) == "Linux" ]]; then
        warning "非root用户无法绑定80端口，将使用8080端口"
        FRONTEND_PORT=8080
    else
        FRONTEND_PORT=80
    fi
    
    # 检查端口是否被占用
    if lsof -ti:$FRONTEND_PORT &>/dev/null; then
        warning "端口$FRONTEND_PORT已被占用，尝试停止现有进程..."
        lsof -ti:$FRONTEND_PORT | xargs kill -9 2>/dev/null
        sleep 2
    fi
    
    # 启动Python HTTP服务器
    info "启动前端HTTP服务器..."
    nohup python3 -m http.server $FRONTEND_PORT > "$LOGS_DIR/frontend.log" 2>&1 &
    FRONTEND_PID=$!
    
    # 等待服务启动
    sleep 3
    
    # 检查服务状态
    if curl -s http://localhost:$FRONTEND_PORT 2>/dev/null | grep -q "基智回测"; then
        success "前端服务启动成功! PID: $FRONTEND_PID"
        echo "前端日志: $LOGS_DIR/frontend.log"
        if [[ $FRONTEND_PORT -eq 80 ]]; then
            echo "访问地址: http://localhost"
        else
            echo "访问地址: http://localhost:$FRONTEND_PORT"
        fi
    else
        error "前端服务启动失败，检查日志: $LOGS_DIR/frontend.log"
        exit 1
    fi
}

# 显示服务状态
show_status() {
    echo -e "\n${GREEN}════════════════════════════════════════════════════${NC}"
    echo -e "${GREEN}      FundWise基金回测平台启动完成!${NC}"
    echo -e "${GREEN}════════════════════════════════════════════════════${NC}"
    echo ""
    
    # 后端状态
    if curl -s http://localhost:3389/api/health 2>/dev/null | grep -q "UP"; then
        echo -e "${GREEN}✅ 后端API服务: 运行中${NC}"
        echo "   端口: 3389"
        echo "   API地址: http://localhost:3389/api"
        echo "   健康检查: http://localhost:3389/api/health"
    else
        echo -e "${RED}❌ 后端API服务: 未运行${NC}"
    fi
    
    # 前端状态
    if [[ $FRONTEND_PORT -eq 80 ]]; then
        FRONTEND_URL="http://localhost"
    else
        FRONTEND_URL="http://localhost:$FRONTEND_PORT"
    fi
    
    if curl -s $FRONTEND_URL 2>/dev/null | grep -q "基智回测"; then
        echo -e "\n${GREEN}✅ 前端Web服务: 运行中${NC}"
        echo "   端口: $FRONTEND_PORT"
        echo "   访问地址: $FRONTEND_URL"
        echo "   局域网访问: http://$(hostname -I | awk '{print $1}'):$FRONTEND_PORT"
    else
        echo -e "\n${RED}❌ 前端Web服务: 未运行${NC}"
    fi
    
    echo -e "\n${BLUE}📋 快速测试:${NC}"
    echo "   1. 打开浏览器访问: $FRONTEND_URL"
    echo "   2. 测试API: curl http://localhost:3389/api/health"
    echo "   3. 查看日志: tail -f $LOGS_DIR/backend.log"
    
    echo -e "\n${YELLOW}⚠️  注意事项:${NC}"
    echo "   • 如需外网访问，请配置防火墙开放端口$FRONTEND_PORT和3389"
    echo "   • 停止服务: 运行 ./stop.sh"
    echo "   • 查看所有服务: ps aux | grep -E '(java.*3389|python.*http.server)'"
    echo -e "${GREEN}════════════════════════════════════════════════════${NC}"
}

# 主函数
main() {
    echo -e "${BLUE}╔════════════════════════════════════════════╗${NC}"
    echo -e "${BLUE}║    FundWise基金回测平台 - 一键启动脚本    ║${NC}"
    echo -e "${BLUE}╚════════════════════════════════════════════╝${NC}"
    echo ""
    
    # 执行步骤
    check_requirements
    install_python_deps
    init_database
    build_backend
    start_backend
    start_frontend
    show_status
}

# 执行主函数
main "$@"