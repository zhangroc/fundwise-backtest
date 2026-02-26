# ⚡ FundWise 快速部署指南

## 🎯 一句话部署

### 方案A：已有环境（最快）
```bash
git clone https://github.com/zhangroc/fundwise-backtest.git
cd fundwise-backtest/deploy
chmod +x *.sh
./start.sh  # 等待3分钟，访问 http://你的IP
```

### 方案B：全新服务器（全自动）
```bash
# 登录到云服务器
ssh root@your-server-ip

# 下载并运行部署脚本
wget -O deploy.sh https://raw.githubusercontent.com/zhangroc/fundwise-backtest/main/deploy/deploy-cloud.sh
chmod +x deploy.sh
./deploy.sh
```

## 📦 三个脚本，三种场景

### 1. `start.sh` - 我已经有环境
```
✅ 检查：Java 17, Python 3, MySQL, Maven
✅ 安装：Python依赖包
✅ 启动：后端(3389) + 前端(80)
✅ 显示：访问地址 http://你的IP
```

### 2. `stop.sh` - 我要停止服务
```
✅ 停止：后端API服务
✅ 停止：前端Web服务
✅ 清理：相关进程
✅ 显示：服务状态
```

### 3. `deploy-cloud.sh` - 我有一台新服务器
```
✅ 安装：所有系统软件
✅ 配置：数据库 + 防火墙
✅ 部署：代码 + 系统服务
✅ 可选：域名 + HTTPS
✅ 完成：显示管理命令
```

## 🚀 5分钟部署到云服务器

### 步骤1：准备服务器
```bash
# 1. 购买云服务器（阿里云/腾讯云/AWS/VPS）
# 2. 开放端口：22, 80, 3389
# 3. SSH登录
ssh root@你的服务器IP
```

### 步骤2：运行部署脚本
```bash
# 下载脚本
curl -o deploy.sh https://raw.githubusercontent.com/zhangroc/fundwise-backtest/main/deploy/deploy-cloud.sh

# 授权并运行
chmod +x deploy.sh
./deploy.sh
```

### 步骤3：按提示操作
```
📝 脚本会询问：
1. 是否开始部署？ (输入 y)
2. 域名（可选，按Enter跳过）
3. 自动安装所有依赖...
4. 自动配置数据库...
5. 自动启动服务...
```

### 步骤4：访问平台
```
🌐 如果有域名：https://你的域名
🌐 如果没域名：http://你的服务器IP

📊 测试API：
curl http://你的服务器IP:3389/api/health
```

## 🔧 端口说明

### 必需端口：
```
22  -> SSH管理（默认开放）
80  -> 网页访问（HTTP）
3389 -> 后端API服务
```

### 可选端口：
```
443 -> HTTPS安全访问
3306 -> MySQL数据库（建议本地）
```

### 云服务器安全组配置：
```
✅ 入方向：允许 22, 80, 3389
✅ 出方向：允许所有
```

## 📱 访问方式

### 本地访问：
```
http://localhost          # 前端页面
http://localhost:3389/api # 后端API
```

### 局域网访问：
```
http://192.168.1.100      # 你的内网IP
```

### 公网访问：
```
http://你的公网IP
或
https://你的域名
```

## 🛠️ 常用命令

### 查看服务状态：
```bash
# 查看运行状态
sudo systemctl status fundwise-backend.service
sudo systemctl status fundwise-frontend.service

# 查看日志
sudo journalctl -u fundwise-backend.service -f
sudo journalctl -u fundwise-frontend.service -f
```

### 重启服务：
```bash
# 重启单个服务
sudo systemctl restart fundwise-backend.service
sudo systemctl restart fundwise-frontend.service

# 重启所有服务
sudo systemctl restart fundwise-*.service
```

### 停止服务：
```bash
# 使用脚本
./stop.sh

# 手动停止
sudo systemctl stop fundwise-backend.service
sudo systemctl stop fundwise-frontend.service
```

## 🐛 快速排错

### 问题1：无法访问网页
```bash
# 检查端口是否开放
curl -I http://localhost
curl -I http://localhost:3389/api/health

# 检查防火墙
sudo ufw status
sudo firewall-cmd --list-all
```

### 问题2：服务启动失败
```bash
# 查看详细日志
sudo journalctl -u fundwise-backend.service --no-pager
sudo journalctl -u fundwise-frontend.service --no-pager

# 查看进程
ps aux | grep -E '(java|python.*http.server)'
```

### 问题3：数据库连接失败
```bash
# 检查MySQL服务
sudo systemctl status mysql

# 检查数据库用户
mysql -ufundwise -ppassword123 -e "SHOW DATABASES;"
```

## 📞 一键诊断

```bash
# 运行诊断脚本
cat > /tmp/diagnose.sh << 'EOF'
echo "=== 系统信息 ==="
uname -a
java -version 2>&1 | head -3
python3 --version
mysql --version
mvn -v | head -1

echo "\n=== 服务状态 ==="
systemctl is-active fundwise-backend.service 2>/dev/null || echo "后端服务未安装"
systemctl is-active fundwise-frontend.service 2>/dev/null || echo "前端服务未安装"

echo "\n=== 端口监听 ==="
sudo lsof -i:80 -i:3389 -i:3306 2>/dev/null || sudo netstat -tlnp | grep -E ":(80|3389|3306)"

echo "\n=== 访问测试 ==="
curl -s http://localhost:3389/api/health || echo "API服务未响应"
curl -s http://localhost | grep -o "基智回测" | head -1 || echo "前端页面未响应"
EOF

chmod +x /tmp/diagnose.sh
sudo /tmp/diagnose.sh
```

## 🔄 更新部署

### 代码更新：
```bash
cd /opt/fundwise-backtest

# 停止服务
sudo systemctl stop fundwise-*.service

# 拉取最新代码
git pull

# 重启服务
sudo systemctl start fundwise-backend.service
sudo systemctl start fundwise-frontend.service
```

### 配置更新：
```bash
# 修改后端配置
vi /opt/fundwise-backtest/backend/src/main/resources/application.yml

# 修改后重启
sudo systemctl restart fundwise-backend.service
```

## 💡 部署小贴士

1. **首次部署建议**：使用 `deploy-cloud.sh`，最省心
2. **端口选择**：80端口需要root权限，测试可用8080
3. **域名配置**：有域名时自动配置HTTPS，更安全
4. **数据库安全**：生产环境记得修改默认密码
5. **日志查看**：出问题先看日志 `sudo journalctl -f`

## 🎉 部署成功标志

```
✅ 访问 http://你的IP 看到基金回测页面
✅ 点击"开始智能回测"能看到基金推荐
✅ 基金卡片显示规模、年限、质量评分
✅ API接口 http://你的IP:3389/api/health 返回UP
```

---

**只需5分钟，拥有专业的基金智能回测平台！**

**GitHub仓库**: https://github.com/zhangroc/fundwise-backtest
**问题反馈**: 提交GitHub Issue
