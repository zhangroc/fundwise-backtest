#!/bin/bash
# FundWise 基金回测平台 - 云服务器部署脚本
# 适用于：阿里云/腾讯云/AWS/普通Linux VPS
# 使用：chmod +x deploy-cloud.sh && ./deploy-cloud.sh

set -e

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

# 打印函数
info() { echo -e "${BLUE}[INFO]${NC} $1"; }
success() { echo -e "${GREEN}[SUCCESS]${NC} $1"; }
warning() { echo -e "${YELLOW}[WARNING]${NC} $1"; }
error() { echo -e "${RED}[ERROR]${NC} $1"; }
step() { echo -e "${CYAN}➜${NC} $1"; }

# 配置参数
GIT_REPO="https://github.com/zhangroc/fundwise-backtest.git"
DEPLOY_DIR="/opt/fundwise-backtest"
DB_PASSWORD="Fundwise@2026"  # 生产环境建议修改
SERVER_IP=""
DOMAIN_NAME=""

# 显示横幅
show_banner() {
    clear
    echo -e "${BLUE}╔══════════════════════════════════════════════════════════╗${NC}"
    echo -e "${BLUE}║       FundWise基金回测平台 - 云服务器部署助手        ║${NC}"
    echo -e "${BLUE}╚══════════════════════════════════════════════════════════╝${NC}"
    echo ""
    echo -e "${YELLOW}📦 功能特性:${NC}"
    echo "   • 自动安装Java 17, Python 3, MySQL, Maven"
    echo "   • 克隆GitHub代码库"
    echo "   • 配置数据库和系统服务"
    echo "   • 设置防火墙和安全组"
    echo "   • 配置Nginx反向代理(可选)"
    echo "   • 配置SSL证书(可选)"
    echo ""
}

# 检查并安装系统依赖
install_dependencies() {
    step "1. 更新系统包管理器..."
    if command -v apt &> /dev/null; then
        # Ubuntu/Debian
        sudo apt update && sudo apt upgrade -y
        info "系统更新完成"
    elif command -v yum &> /dev/null; then
        # CentOS/RHEL
        sudo yum update -y
        info "系统更新完成"
    else
        warning "无法识别的包管理器，请手动安装依赖"
    fi
    
    step "2. 安装必需软件包..."
    if command -v apt &> /dev/null; then
        sudo apt install -y curl wget git zip unzip net-tools lsof \
                          default-jdk python3 python3-pip maven \
                          mysql-server mysql-client nginx certbot \
                          python3-certbot-nginx
        info "Ubuntu/Debian依赖安装完成"
    elif command -v yum &> /dev/null; then
        sudo yum install -y curl wget git zip unzip net-tools \
                          java-17-openjdk python3 python3-pip maven \
                          mysql-server mysql nginx certbot python3-certbot-nginx
        info "CentOS/RHEL依赖安装完成"
    fi
    
    # 验证安装
    step "3. 验证软件版本..."
    java -version && success "Java OK"
    python3 --version && success "Python OK"
    mysql --version && success "MySQL OK"
    mvn -v | head -1 && success "Maven OK"
    nginx -v && success "Nginx OK"
}

# 获取服务器信息
get_server_info() {
    step "获取服务器信息..."
    
    # 获取公网IP
    SERVER_IP=$(curl -s http://ifconfig.me || hostname -I | awk '{print $1}')
    info "服务器IP地址: $SERVER_IP"
    
    # 获取域名（如果有）
    read -p "请输入域名(如 fundwise.example.com)，没有则按Enter跳过: " DOMAIN_NAME
    if [[ -n "$DOMAIN_NAME" ]]; then
        info "域名: $DOMAIN_NAME"
        # 验证域名解析
        if host "$DOMAIN_NAME" &>/dev/null; then
            success "域名解析正常"
        else
            warning "域名解析可能未配置，请确保DNS指向 $SERVER_IP"
        fi
    else
        info "未设置域名，将使用IP地址访问"
    fi
    
    # 显示安全提示
    echo -e "\n${YELLOW}⚠️  安全提示:${NC}"
    echo "   请确保云服务器安全组/防火墙已开放以下端口:"
    echo "   • 80 (HTTP) - 网页访问"
    echo "   • 443 (HTTPS) - 安全访问(可选)"
    echo "   • 3389 (API) - 后端服务"
    echo "   • 22 (SSH) - 远程管理"
    echo "   • 3306 (MySQL) - 数据库(建议只允许本地访问)"
}

# 克隆代码库
clone_repository() {
    step "克隆GitHub代码库..."
    
    if [[ -d "$DEPLOY_DIR" ]]; then
        warning "目录已存在，备份并清理..."
        sudo mv "$DEPLOY_DIR" "${DEPLOY_DIR}_backup_$(date +%Y%m%d_%H%M%S)"
    fi
    
    sudo mkdir -p "$DEPLOY_DIR"
    sudo chown -R $(whoami):$(whoami) "$DEPLOY_DIR"
    
    cd "$DEPLOY_DIR"
    git clone "$GIT_REPO" .
    success "代码克隆完成: $DEPLOY_DIR"
    
    # 显示项目结构
    info "项目结构:"
    ls -la
}

# 配置数据库
setup_database() {
    step "配置MySQL数据库..."
    
    # 启动MySQL服务
    sudo systemctl start mysql
    sudo systemctl enable mysql
    
    # 设置root密码（如果未设置）
    if ! sudo mysql -e "SELECT 1" &>/dev/null; then
        info "设置MySQL root密码..."
        sudo mysql <<EOF
ALTER USER 'root'@'localhost' IDENTIFIED WITH mysql_native_password BY '${DB_PASSWORD}';
FLUSH PRIVILEGES;
EOF
    fi
    
    # 创建应用数据库用户
    info "创建应用数据库用户..."
    sudo mysql -uroot -p"${DB_PASSWORD}" <<EOF
CREATE DATABASE IF NOT EXISTS fundwise_backtest CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER IF NOT EXISTS 'fundwise'@'localhost' IDENTIFIED BY 'password123';
GRANT ALL PRIVILEGES ON fundwise_backtest.* TO 'fundwise'@'localhost';
FLUSH PRIVILEGES;
EOF
    
    # 导入数据库架构
    if [[ -f "$DEPLOY_DIR/sql/schema_v1.sql" ]]; then
        info "导入数据库架构..."
        mysql -ufundwise -ppassword123 fundwise_backtest < "$DEPLOY_DIR/sql/schema_v1.sql"
        success "数据库配置完成"
    else
        warning "未找到数据库架构文件，跳过导入"
    fi
    
    # 安全配置：限制MySQL只监听本地
    sudo sed -i 's/^bind-address.*/bind-address = 127.0.0.1/' /etc/mysql/mysql.conf.d/mysqld.cnf 2>/dev/null || \
    sudo sed -i 's/^bind-address.*/bind-address = 127.0.0.1/' /etc/my.cnf 2>/dev/null
    
    sudo systemctl restart mysql
}

# 安装Python依赖
install_python_dependencies() {
    step "安装Python依赖..."
    
    cd "$DEPLOY_DIR/scripts"
    if [[ -f "requirements.txt" ]]; then
        pip3 install -r requirements.txt
    else
        pip3 install pymysql pandas akshare
    fi
    
    success "Python依赖安装完成"
}

# 配置系统服务
setup_systemd_services() {
    step "配置系统服务(后台运行)..."
    
    # 后端服务
    cat > /tmp/fundwise-backend.service <<EOF
[Unit]
Description=FundWise Backend API Service
After=network.target mysql.service

[Service]
Type=simple
User=$(whoami)
WorkingDirectory=$DEPLOY_DIR/backend
ExecStart=/usr/bin/mvn spring-boot:run -Dserver.port=3389
Restart=on-failure
RestartSec=10

[Install]
WantedBy=multi-user.target
EOF
    
    sudo mv /tmp/fundwise-backend.service /etc/systemd/system/
    
    # 前端服务（使用root绑定80端口）
    cat > /tmp/fundwise-frontend.service <<EOF
[Unit]
Description=FundWise Frontend Web Service
After=network.target fundwise-backend.service

[Service]
Type=simple
User=root
WorkingDirectory=$DEPLOY_DIR/frontend
ExecStart=/usr/bin/python3 -m http.server 80
Restart=on-failure
RestartSec=10

[Install]
WantedBy=multi-user.target
EOF
    
    sudo mv /tmp/fundwise-frontend.service /etc/systemd/system/
    
    # 重新加载systemd并启用服务
    sudo systemctl daemon-reload
    sudo systemctl enable fundwise-backend.service
    sudo systemctl enable fundwise-frontend.service
    
    success "系统服务配置完成"
}

# 配置防火墙
setup_firewall() {
    step "配置防火墙..."
    
    # 尝试使用ufw
    if command -v ufw &> /dev/null; then
        sudo ufw allow 22/tcp
        sudo ufw allow 80/tcp
        sudo ufw allow 443/tcp
        sudo ufw allow 3389/tcp
        sudo ufw --force enable
        info "UFW防火墙已配置"
    
    # 尝试使用firewalld
    elif command -v firewall-cmd &> /dev/null; then
        sudo firewall-cmd --permanent --add-port=22/tcp
        sudo firewall-cmd --permanent --add-port=80/tcp
        sudo firewall-cmd --permanent --add-port=443/tcp
        sudo firewall-cmd --permanent --add-port=3389/tcp
        sudo firewall-cmd --reload
        info "FirewallD已配置"
    
    # 使用iptables（最后手段）
    else
        sudo iptables -A INPUT -p tcp --dport 22 -j ACCEPT
        sudo iptables -A INPUT -p tcp --dport 80 -j ACCEPT
        sudo iptables -A INPUT -p tcp --dport 443 -j ACCEPT
        sudo iptables -A INPUT -p tcp --dport 3389 -j ACCEPT
        info "iptables规则已添加"
    fi
    
    warning "请确保云服务器控制台安全组也开放相应端口"
}

# 配置Nginx反向代理（可选）
setup_nginx() {
    if [[ -z "$DOMAIN_NAME" ]]; then
        info "未配置域名，跳过Nginx配置"
        return
    fi
    
    step "配置Nginx反向代理..."
    
    # 创建Nginx配置
    cat > /tmp/fundwise-nginx.conf <<EOF
server {
    listen 80;
    server_name $DOMAIN_NAME;
    
    # 重定向HTTP到HTTPS
    return 301 https://\$server_name\$request_uri;
}

server {
    listen 443 ssl http2;
    server_name $DOMAIN_NAME;
    
    # SSL证书路径（Certbot会自动配置）
    ssl_certificate /etc/letsencrypt/live/$DOMAIN_NAME/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/$DOMAIN_NAME/privkey.pem;
    
    # SSL优化配置
    ssl_protocols TLSv1.2 TLSv1.3;
    ssl_ciphers ECDHE-RSA-AES256-GCM-SHA512:DHE-RSA-AES256-GCM-SHA512;
    ssl_prefer_server_ciphers off;
    
    # 前端代理
    location / {
        proxy_pass http://localhost:80;
        proxy_set_header Host \$host;
        proxy_set_header X-Real-IP \$remote_addr;
        proxy_set_header X-Forwarded-For \$proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto \$scheme;
    }
    
    # API代理
    location /api/ {
        proxy_pass http://localhost:3389/api/;
        proxy_set_header Host \$host;
        proxy_set_header X-Real-IP \$remote_addr;
        proxy_set_header X-Forwarded-For \$proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto \$scheme;
    }
    
    # 静态文件缓存
    location ~* \.(js|css|png|jpg|jpeg|gif|ico|svg)$ {
        expires 1y;
        add_header Cache-Control "public, immutable";
        proxy_pass http://localhost:80;
    }
}
EOF
    
    sudo mv /tmp/fundwise-nginx.conf /etc/nginx/sites-available/fundwise
    sudo ln -sf /etc/nginx/sites-available/fundwise /etc/nginx/sites-enabled/
    sudo rm -f /etc/nginx/sites-enabled/default
    
    # 测试Nginx配置
    sudo nginx -t && {
        sudo systemctl restart nginx
        success "Nginx配置完成"
        
        # 申请SSL证书（如果域名有效）
        step "申请Let's Encrypt SSL证书..."
        sudo certbot --nginx -d "$DOMAIN_NAME" --non-interactive --agree-tos --email admin@example.com || {
            warning "SSL证书申请失败，请手动运行: sudo certbot --nginx"
        }
    } || {
        error "Nginx配置测试失败，请检查配置"
    }
}

# 启动服务
start_services() {
    step "启动所有服务..."
    
    sudo systemctl start fundwise-backend.service
    sudo systemctl start fundwise-frontend.service
    
    if [[ -n "$DOMAIN_NAME" ]]; then
        sudo systemctl restart nginx
    fi
    
    # 等待服务启动
    sleep 10
    
    # 检查服务状态
    info "检查服务状态:"
    sudo systemctl status fundwise-backend.service --no-pager | grep -E "(Active:|PID)"
    sudo systemctl status fundwise-frontend.service --no-pager | grep -E "(Active:|PID)"
    
    success "服务启动完成"
}

# 显示部署结果
show_deployment_result() {
    echo -e "\n${GREEN}══════════════════════════════════════════════════════════${NC}"
    echo -e "${GREEN}             🎉 部署完成！${NC}"
    echo -e "${GREEN}══════════════════════════════════════════════════════════${NC}"
    echo ""
    
    echo -e "${CYAN}📊 服务信息:${NC}"
    if [[ -n "$DOMAIN_NAME" ]]; then
        echo "   前端访问: https://$DOMAIN_NAME"
        echo "   API接口: https://$DOMAIN_NAME/api"
    else
        echo "   前端访问: http://$SERVER_IP"
        echo "   API接口: http://$SERVER_IP:3389/api"
    fi
    
    echo -e "\n${CYAN}🔧 管理命令:${NC}"
    echo "   查看后端日志: sudo journalctl -u fundwise-backend.service -f"
    echo "   查看前端日志: sudo journalctl -u fundwise-frontend.service -f"
    echo "   重启后端: sudo systemctl restart fundwise-backend.service"
    echo "   重启前端: sudo systemctl restart fundwise-frontend.service"
    echo "   停止服务: sudo systemctl stop fundwise-{backend,frontend}.service"
    
    echo -e "\n${CYAN}📁 项目目录:${NC}"
    echo "   代码位置: $DEPLOY_DIR"
    echo "   启动脚本: $DEPLOY_DIR/deploy/start.sh"
    echo "   停止脚本: $DEPLOY_DIR/deploy/stop.sh"
    
    echo -e "\n${CYAN}🧪 测试命令:${NC}"
    echo "   健康检查: curl http://localhost:3389/api/health"
    echo "   前端页面: curl -I http://localhost"
    
    if [[ -n "$DOMAIN_NAME" ]]; then
        echo -e "\n${YELLOW}⚠️  下一步:${NC}"
        echo "   1. 访问 https://$DOMAIN_NAME 验证部署"
        echo "   2. 如有问题检查日志: sudo journalctl -u nginx -f"
        echo "   3. SSL证书自动续期已配置"
    else
        echo -e "\n${YELLOW}⚠️  下一步:${NC}"
        echo "   1. 访问 http://$SERVER_IP 验证部署"
        echo "   2. 如需域名，编辑Nginx配置并运行certbot"
        echo "   3. 生产环境建议配置域名和HTTPS"
    fi
    
    echo -e "\n${GREEN}══════════════════════════════════════════════════════════${NC}"
}

# 主部署流程
main_deployment() {
    show_banner
    
    # 确认部署
    read -p "是否开始部署FundWise到当前服务器？(y/n): " -n 1 -r
    echo
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        info "部署已取消"
        exit 0
    fi
    
    # 执行部署步骤
    install_dependencies
    get_server_info
    clone_repository
    setup_database
    install_python_dependencies
    setup_systemd_services
    setup_firewall
    setup_nginx
    start_services
    show_deployment_result
}

# 执行部署
main_deployment "$@"