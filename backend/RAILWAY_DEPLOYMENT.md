# Railway 部署指南

## 配置文件说明

本项目已添加以下 Railway 部署配置文件：

### 1. railway.toml（推荐）
Railway 的官方配置文件，明确指定使用 Dockerfile 构建。

### 2. Dockerfile
多阶段构建配置：
- 第一阶段：使用 Maven 构建 JAR 包
- 第二阶段：使用 JRE 运行应用

### 3. nixpacks.toml（备选）
如果 Railway 使用 Nixpacks 构建器，这个文件会被使用。

### 4. Procfile（备选）
传统的进程配置文件，定义了 web 进程的启动命令。

## Railway 部署步骤

### 方式一：通过 Railway Dashboard（推荐）

1. **登录 Railway**
   - 访问 https://railway.app/
   - 使用 GitHub 账号登录

2. **创建新项目**
   - 点击 "New Project"
   - 选择 "Deploy from GitHub repo"
   - 选择 `inwardflow/atomic-habit` 仓库
   - 选择 `development` 分支

3. **配置服务**
   - **Root Directory**: 设置为 `backend`
   - **Builder**: 自动检测（会使用 Dockerfile）

4. **添加数据库**
   - 点击 "New" → "Database" → "Add PostgreSQL"
   - Railway 会自动创建数据库并注入环境变量

5. **配置环境变量**
   
   在 Railway 项目的 Variables 标签页添加：

   ```bash
   # JWT 密钥（必须）- 使用 openssl rand -hex 32 生成
   SPRING_JWT_SECRET=your-generated-32-char-hex-string
   
   # AI 模型配置（必须）
   AGENTSCOPE_MODEL_API_KEY=your-siliconflow-api-key
   AGENTSCOPE_MODEL_BASE_URL=https://api.siliconflow.com/v1
   AGENTSCOPE_MODEL_NAME=Qwen/Qwen2.5-72B-Instruct
   
   # 数据库配置（Railway PostgreSQL 会自动注入以下变量）
   # DATABASE_URL - Railway 自动提供
   # 如果需要手动配置：
   # SPRING_DATASOURCE_URL=jdbc:postgresql://<host>:<port>/<database>
   # SPRING_DATASOURCE_USERNAME=<username>
   # SPRING_DATASOURCE_PASSWORD=<password>
   ```

   **注意**：如果使用 Railway PostgreSQL，需要添加以下配置来使用 `DATABASE_URL`：

   ```bash
   SPRING_DATASOURCE_URL=${DATABASE_URL}
   ```

6. **部署**
   - Railway 会自动开始构建和部署
   - 构建时间约 3-5 分钟（首次构建）

7. **获取部署 URL**
   - 在 Settings → Domains 中生成公共域名
   - 或者使用 Railway 提供的默认域名

### 方式二：使用 Railway CLI

```bash
# 安装 Railway CLI
npm install -g @railway/cli

# 登录
railway login

# 初始化项目
cd ~/atomic-habit/backend
railway init

# 链接到 Railway 项目
railway link

# 添加环境变量
railway variables set SPRING_JWT_SECRET=your-secret
railway variables set AGENTSCOPE_MODEL_API_KEY=your-key

# 部署
railway up
```

## 环境变量详细说明

### 必需变量

| 变量名 | 说明 | 如何获取 |
|--------|------|----------|
| `SPRING_JWT_SECRET` | JWT 签名密钥 | `openssl rand -hex 32` |
| `AGENTSCOPE_MODEL_API_KEY` | SiliconFlow API 密钥 | https://cloud.siliconflow.cn/ |

### 数据库变量（Railway PostgreSQL 自动注入）

| 变量名 | 说明 |
|--------|------|
| `DATABASE_URL` | PostgreSQL 连接 URL |
| `PGHOST` | 数据库主机 |
| `PGPORT` | 数据库端口 |
| `PGDATABASE` | 数据库名称 |
| `PGUSER` | 数据库用户 |
| `PGPASSWORD` | 数据库密码 |

### 可选变量

| 变量名 | 默认值 | 说明 |
|--------|--------|------|
| `PORT` | 8080 | 应用监听端口（Railway 会自动设置） |
| `AGENTSCOPE_MODEL_BASE_URL` | https://api.siliconflow.com/v1 | AI 模型 API 基础 URL |
| `AGENTSCOPE_MODEL_NAME` | Qwen/Qwen2.5-72B-Instruct | AI 模型名称 |

## 常见问题

### 1. 构建失败：找不到 start.sh

**原因**：Railway 无法自动检测构建方式。

**解决方案**：
- 确保 `railway.toml` 文件存在于 `backend/` 目录
- 或在 Railway Dashboard 中手动设置 Builder 为 "Dockerfile"

### 2. 数据库连接失败

**原因**：环境变量配置不正确。

**解决方案**：
- 确保已添加 Railway PostgreSQL 服务
- 检查 `SPRING_DATASOURCE_URL` 是否正确设置为 `${DATABASE_URL}`

### 3. JWT 密钥错误

**原因**：使用了默认的示例密钥。

**解决方案**：
```bash
# 生成新密钥
openssl rand -hex 32

# 在 Railway 中设置
railway variables set SPRING_JWT_SECRET=<生成的密钥>
```

### 4. 端口冲突

**原因**：应用没有监听 Railway 提供的 `$PORT` 环境变量。

**解决方案**：
- Railway 会自动设置 `PORT` 环境变量
- Spring Boot 会自动使用 `server.port=${PORT:8080}`（已在 application.yml 中配置）

## 验证部署

部署成功后，访问以下端点验证：

```bash
# 健康检查
curl https://your-app.railway.app/actuator/health

# API 文档
https://your-app.railway.app/swagger-ui/index.html

# 测试注册
curl -X POST https://your-app.railway.app/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username":"test","email":"test@example.com","password":"Test123456"}'
```

## 更新前端配置

部署成功后，需要更新前端的环境变量：

在 Vercel Dashboard 中设置：
```bash
VITE_API_BASE_URL=https://your-app.railway.app/api
VITE_BACKEND_URL=https://your-app.railway.app
```

## 监控和日志

- **日志查看**：Railway Dashboard → Deployments → 选择部署 → Logs
- **指标监控**：Railway Dashboard → Metrics
- **重启服务**：Railway Dashboard → Settings → Restart

## 成本估算

Railway 免费额度：
- $5/月 免费额度
- 超出部分按使用量计费
- PostgreSQL 数据库包含在免费额度内

预计成本：
- 小型应用：免费额度足够
- 中型应用：$5-10/月
