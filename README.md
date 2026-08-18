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
