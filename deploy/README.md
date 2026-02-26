# FundWise基金回测平台 - 部署指南

## 📦 部署脚本说明

### 三个核心脚本：

#### 1. `start.sh` - **一键启动脚本**
**用途**：在已安装环境的机器上快速启动所有服务
**特点**：
- 自动检查Java 17, Python 3, MySQL, Maven
- 自动安装Python依赖包
- 初始化数据库（如果不存在）
- 启动后端Spring Boot服务（端口3389）
- 启动前端HTTP服务器（端口80/8080）
- 显示服务状态和访问地址

**使用方法**：
```bash
chmod +x start.sh
./start.sh
```

#### 2. `stop.sh` - **一键停止脚本**
**用途**：停止所有运行的服务
**特点**：
- 停止后端API服务（端口3389）
- 停止前端Web服务（端口80/8080/3000）
- 清理相关进程
- 显示停止状态

**使用方法**：
```bash
chmod +x stop.sh
./stop.sh
```

#### 3. `deploy-cloud.sh` - **云服务器部署脚本**
**用途**：在全新的云服务器（阿里云/腾讯云/AWS/VPS）上自动部署
**特点**：
- 自动安装所有系统依赖（Java 17, Python 3, MySQL, Maven, Nginx）
- 配置MySQL数据库和用户
- 克隆GitHub代码库
- 配置系统服务（systemd后台运行）
- 配置防火墙和安全组规则
- **可选**：配置Nginx反向代理和SSL证书
- 支持域名配置和HTTPS

**使用方法**：
```bash
chmod +x deploy-cloud.sh
./deploy-cloud.sh
```

## 🚀 快速开始

### 场景1：已有环境，快速启动
```bash
git clone https://github.com/zhangroc/fundwise-backtest.git
cd fundwise-backtest/deploy
./start.sh
```

### 场景2：全新云服务器部署
```bash
# 登录到你的云服务器
ssh root@your-server-ip

# 下载部署脚本
wget https://raw.githubusercontent.com/zhangroc/fundwise-backtest/main/deploy/deploy-cloud.sh
chmod +x deploy-cloud.sh
./deploy-cloud.sh
```

### 场景3：本地开发测试
```bash
cd fundwise-backtest

# 手动启动后端
cd backend
mvn spring-boot:run -Dserver.port=3389

# 手动启动前端（新终端）
cd frontend
sudo python3 -m http.server 80  # 需要sudo权限绑定80端口
# 或使用其他端口
python3 -m http.server 8080
```

## 🔧 环境要求

### 必需软件：
- **Java 17** 或更高版本
- **Python 3.8** 或更高版本
- **MySQL 8.0** 或更高版本
- **Maven 3.6** 或更高版本

### 可选软件：
- **Nginx**（用于反向代理和SSL）
- **Certbot**（用于SSL证书）

## 🌐 网络配置

### 必需开放的端口：
- **80** - HTTP网页访问
- **3389** - 后端API服务
- **22** - SSH远程管理（建议）

### 可选开放的端口：
- **443** - HTTPS安全访问
- **3306** - MySQL数据库（建议只允许本地访问）

## 📁 项目结构

```
fundwise-backtest/
├── backend/          # Spring Boot后端
│   ├── src/main/java/com/fundwise/
│   ├── pom.xml
│   └── target/
├── frontend/         # 前端网页
│   ├── index.html
│   ├── js/app.js
│   ├── css/style.css
│   └── js/helpers.js
├── scripts/          # 数据处理脚本
│   ├── data_fetcher_fixed.py
│   ├── accurate_nav_fetcher.py
│   └── requirements.txt
├── sql/              # 数据库脚本
│   └── schema_v1.sql
├── deploy/           # 部署脚本
│   ├── start.sh
│   ├── stop.sh
│   ├── deploy-cloud.sh
│   └── README.md
└── logs/             # 日志目录（启动时创建）
```

## ⚙️ 服务配置

### 后端服务（Spring Boot）
- **端口**：3389
- **配置文件**：`backend/src/main/resources/application.yml`
- **数据库连接**：MySQL `fundwise_backtest` 数据库
- **运行用户**：建议非root用户

### 前端服务（Python HTTP Server）
- **端口**：80（需要root权限）或8080
- **API地址**：自动配置为当前服务器IP
- **运行用户**：端口80需要root，端口8080可用普通用户

### 数据库配置
- **数据库名**：`fundwise_backtest`
- **用户名**：`fundwise`
- **密码**：`password123`（生产环境建议修改）
- **字符集**：`utf8mb4_unicode_ci`

## 🔒 安全建议

### 生产环境安全配置：
1. **修改数据库密码**：
   ```bash
   mysql -uroot -p
   ALTER USER 'fundwise'@'localhost' IDENTIFIED BY '新强密码';
   ```

2. **使用HTTPS**：
   ```bash
   # 使用deploy-cloud.sh自动配置
   # 或手动配置：
   sudo certbot --nginx -d your-domain.com
   ```

3. **限制数据库访问**：
   ```sql
   REVOKE ALL PRIVILEGES ON *.* FROM 'fundwise'@'%';
   GRANT ALL PRIVILEGES ON fundwise_backtest.* TO 'fundwise'@'localhost';
   ```

4. **使用非root用户运行服务**：
   ```bash
   useradd fundwise
   chown -R fundwise:fundwise /opt/fundwise-backtest
   ```

## 🐛 故障排除

### 常见问题：

#### 1. 端口被占用
```bash
# 查看端口占用
sudo lsof -i :3389
sudo lsof -i :80

# 停止占用进程
sudo kill -9 <PID>
```

#### 2. Java版本问题
```bash
# 检查Java版本
java -version

# 安装Java 17
sudo apt install openjdk-17-jdk  # Ubuntu/Debian
sudo yum install java-17-openjdk # CentOS/RHEL
```

#### 3. MySQL连接失败
```bash
# 检查MySQL服务状态
sudo systemctl status mysql

# 启动MySQL服务
sudo systemctl start mysql
```

#### 4. Python依赖安装失败
```bash
# 使用pip3安装
pip3 install --upgrade pip
pip3 install pymysql pandas akshare
```

#### 5. 前端无法访问
```bash
# 检查端口
curl -I http://localhost:8080

# 检查防火墙
sudo ufw status
sudo ufw allow 80/tcp
sudo ufw allow 8080/tcp
```

## 📞 获取帮助

### 查看日志：
```bash
# 后端日志
tail -f logs/backend.log
sudo journalctl -u fundwise-backend.service -f

# 前端日志
tail -f logs/frontend.log
sudo journalctl -u fundwise-frontend.service -f

# Nginx日志
sudo tail -f /var/log/nginx/access.log
sudo tail -f /var/log/nginx/error.log
```

### 服务管理：
```bash
# 查看服务状态
sudo systemctl status fundwise-backend.service
sudo systemctl status fundwise-frontend.service

# 重启服务
sudo systemctl restart fundwise-backend.service
sudo systemctl restart fundwise-frontend.service

# 停止服务
sudo systemctl stop fundwise-backend.service
sudo systemctl stop fundwise-frontend.service
```

## 🔄 更新部署

### 更新代码：
```bash
cd /opt/fundwise-backtest
sudo systemctl stop fundwise-backend.service
sudo systemctl stop fundwise-frontend.service

git pull

# 重建后端
cd backend
mvn clean compile

# 重启服务
sudo systemctl start fundwise-backend.service
sudo systemctl start fundwise-frontend.service
```

## 📄 许可证

本项目基于MIT许可证开源。

## 🤝 贡献

欢迎提交Issue和Pull Request。

---

**一键部署，三分钟上线专业的基金回测平台！**