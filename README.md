# CodeGuard Agent

CodeGuard Agent 是一个基于 Spring Boot 和 LangChain4j 的多 Agent 代码审查平台，支持对 Git Diff 和 GitHub Pull Request 进行自动化审查，输出问题列表、风险评分、合并建议、Agent 执行轨迹、Markdown 报告和 SARIF 报告。

## 技术栈

Spring Boot、LangChain4j、Vue3、PostgreSQL、Redis、Flyway、Spring Security、Docker、SARIF

## 核心功能

- 多 Agent 协同审查：按职责拆分路由、Bug、安全、代码质量、测试覆盖和 LLM 综合审查。
- 异步任务链路：提交审查任务后后台执行，前端轮询展示进度和结果。
- 审计追踪：记录每个 Agent 的执行状态、耗时、输出和错误信息。
- 报告生成：支持 Markdown 审查报告和 SARIF 标准格式导出。
- 项目化管理：支持项目、仓库、审查历史和问题明细持久化。
- 企业化基础能力：登录认证、角色权限、数据库迁移、缓存、健康检查、Docker Compose 部署和 CI 构建。

## 项目结构

```text
multi_agent_java/
  backend/        Spring Boot 后端服务
  frontend/       Vue3 前端工作台
  scripts/        启动、停止、测试脚本
  docker-compose.yml
  codeguard.bat
  run.bat
```

## 快速启动

需要先启动 Docker Desktop，然后在项目根目录执行：

```powershell
.\codeguard.bat start
```

访问地址：

- 前端工作台：http://localhost:3000
- 后端健康检查：http://localhost:18080/api/health
- Actuator 健康检查：http://localhost:18080/actuator/health

演示账号：

```text
admin / codeguard123
developer / developer123
auditor / auditor123
```

停止服务：

```powershell
.\codeguard.bat stop
```

常用命令：

```powershell
.\codeguard.bat start          # 打包后端并启动 Docker 服务
.\codeguard.bat start -Open    # 启动后打开前端页面
.\codeguard.bat restart        # 重启服务
.\codeguard.bat status         # 查看容器状态
.\codeguard.bat logs           # 查看实时日志
.\codeguard.bat test           # 后端测试 + 前端构建
.\codeguard.bat stop           # 停止服务
```

## LLM 配置

公开仓库不包含任何真实 API Key。默认情况下，如果没有配置 Key，LLM 审查会自动跳过，规则 Agent 仍可正常运行。

本地使用真实模型时，可以通过环境变量配置：

```powershell
$env:CODEGUARD_LLM_PROVIDER="deepseek"
$env:CODEGUARD_LLM_API_KEY="your-api-key"
$env:CODEGUARD_LLM_BASE_URL="https://api.deepseek.com"
$env:CODEGUARD_LLM_MODEL="deepseek-v4-flash"
```

也可以参考 `backend/src/main/java/com/codeguard/agent/config/CodeGuardLocalLlmConfig.java.example` 创建本地私有配置文件。该私有配置文件已加入 `.gitignore`，不会被提交到仓库。

## 本地开发

后端：

```powershell
cd backend
mvn spring-boot:run
```

前端：

```powershell
cd frontend
npm install
npm run dev
```

## 测试

```powershell
.\codeguard.bat test
```
