# 本地生活探店与商家管理平台

这是一个面向后端实习面试和完整业务演示的本地生活平台。系统由Spring Boot后端、React商家后台和React用户端组成，覆盖内容管理、预约订单、评价、收藏、浏览记录、员工管理和运营观测。

## 从这里开始

- 第一次在本机启动：[本地运行](usage.md)
- 了解模块和请求链路：[系统架构](architecture.md)
- 查看数据库关系与索引：[数据库设计](DATABASE_DESIGN.md)
- 理解订单状态机、Outbox和通知：[订单可靠性](ORDER_RELIABILITY.md)
- 准备后端面试讲解：[面试说明](INTERVIEW_NOTES.md)

## 核心能力

| 能力 | 实现 |
| --- | --- |
| 认证与授权 | 管理端、用户端独立会话；短期Access Token、Refresh Token轮换、ADMIN/STAFF权限边界 |
| 预约一致性 | `requestId`幂等、数据库CAS、容量原子占用与释放、合法状态转换 |
| 可靠事件 | ShedLock超时调度、事务Outbox、租约领取、指数退避、DEAD事件运维 |
| 用户通知 | 订单状态事件生成幂等站内通知，支持未读数、已读和订单跳转 |
| 数据访问 | MyBatis、MySQL约束与索引、Redis ZSet、Caffeine/Redis两级缓存 |
| 工程验证 | JUnit、Mockito、MockMvc、Node契约测试、Playwright、Testcontainers和GitHub Actions |

## 访问入口

本地真实链路默认使用以下地址：

| 入口 | 地址 |
| --- | --- |
| 管理端 | `http://127.0.0.1:5173/console/login.html` |
| 用户端 | `http://127.0.0.1:5173/client/login.html` |
| Swagger/Knife4j | `http://localhost:8080/doc.html` |
| 健康检查 | `http://localhost:8080/actuator/health` |

默认演示账号为管理员`admin / 123456`、用户`13800001111 / 123456`。账号仅用于本地演示，不应直接用于生产环境。

## 阅读顺序

1. 按[本地运行](usage.md)完成数据库、IDEA后端和Vite前端启动。
2. 阅读[系统架构](architecture.md)，理解双端接口、Service、Mapper和存储边界。
3. 阅读[一致性与并发](CONSISTENCY.md)、[订单可靠性](ORDER_RELIABILITY.md)和[认证与会话安全](AUTH_SESSION_SECURITY.md)。
4. 使用[测试与验证](TESTING.md)中的命令复现单测、集成测试和核心链路smoke。

!!! note "文档与代码同步"
    `docs/`中的Markdown是文档站唯一内容源。推送到`main`后，GitHub Actions会构建并发布GitHub Pages，不需要手工提交`site/`目录。
