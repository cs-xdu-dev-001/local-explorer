# 测试与验证体系

这份文档用于说明项目不是只靠手动点页面。后端工程项目要能解释“我怎么证明它没坏”。

## 当前验证结果

最近完整验证：

```text
Maven tests: 339 run, 0 failures, 0 errors, 0 skipped
Node frontend/docs/run tests: 151 passed
Testcontainers MySQL + Redis: 34 run, 0 failures, 0 errors, 0 skipped
Frontend dependency install: npm ci succeeded
UI smoke: 6 rendered pages, 18 text checks
Interaction smoke: 6 flows, including loaded project images
Backend chain smoke: admin/user login, create order, admin confirm, user cancel
Admin management smoke: category/item/package/employee CRUD, packageItems persistence and user/employee status endpoints
User engagement smoke: browse, favorite, completed project and package review fixtures, merchant reply and user read-back
Runtime settings smoke: merchant and shop state survive restart, admin/user read-back, operation logs persisted
Booking concurrency smoke: 2 concurrent requests, 1 success, booked count restored after cancel
Session revocation smoke: disabled employee/user tokens return 401 immediately
Order reliability smoke: duplicate requestId, timeout close, capacity recovery, notification delivery and read state
Authentication API smoke: rotation, replay family revocation, logout/logout-all, lockout and ADMIN unlock
Authentication Playwright: dual-end reload recovery, expired Access recovery, logout and mobile layout
```

## 测试分层

| 层级 | 目的 | 代表测试 |
| --- | --- | --- |
| Service单测 | 验证业务规则、状态流转和预约幂等 | `ExploreOrderServiceImplTest`、`UserInteractionServiceImplTest` |
| 可靠性单测 | 验证超时边界、逐条失败隔离、Outbox租约、退避和DEAD | `ExpiredOrderProcessorTest`、`OrderExpirationJobTest`、`OutboxEventTransactionServiceTest` |
| Controller测试 | 验证参数校验和接口返回结构 | `AuthControllerValidationTest`、`WriteControllerValidationTest` |
| MockMvc链路测试 | 串起真实Controller和Service验证预约主流程 | `BookingApiFlowTest` |
| MySQL/Redis集成测试 | 执行真实初始化SQL，验证Mapper、事务、业务并发、Refresh轮换、缓存锁、跨实例失效和故障恢复 | `BookingMySqlIT`、`AuthSessionMySqlIT`、`HotCacheMySqlRedisIT` |
| 拦截器测试 | 验证JWT隔离、禁用后token失效和STAFF越权403 | `JwtTokenInterceptorTest`、`AdminAuthorizationInterceptorTest` |
| 异常测试 | 验证HTTP状态、稳定code及响应不泄露SQL/堆栈 | `GlobalExceptionHandlerTest` |
| 配置与健康测试 | 验证Redis降级、Actuator状态、静态资源和Maven配置 | `RedisConfigurationTest`、`RedisFallbackHealthIndicatorTest`、`backend-build-config.test.cjs` |
| 可观测性测试 | 验证requestId、异常关联、低基数业务指标、HTTP耗时和Prometheus端点 | `RequestTracingFilterTest`、`BookingMetricsTest`、`BookingMySqlIT` |
| 契约测试 | 扫描源码/SQL/README，防止关键约束被删 | `operation-log-contract.test.cjs`、`data-integrity.test.cjs` |
| 前端静态测试 | 验证React入口、表单边界、演示数据和关键交互代码 | `react-frontend.test.cjs` |
| 浏览器smoke | 用Chrome/Edge渲染页面，避免只剩空白壳 | `smoke-demo-pages.ps1` |
| 真实后端smoke | 真实启动后端，跑登录、预约、CRUD、评价、并发链路 | `smoke-*.ps1` |

## 为什么保留Node契约测试

项目是Java后端，但Node测试不是“前端凑数”。它主要做三类检查：

- 检查README和docs是否还保持当前项目定位，防止旧项目定位残留。
- 扫源码确认接口、脚本和SQL约束没有被误删。
- 检查React入口和静态演示数据，保证展示时不是空页面。

这些测试很适合防止项目在多次修改后“文档说一套，代码做一套”。

## 关键自动化证据

### 预约主链路

`BookingApiFlowTest`通过MockMvc调用真实Controller和`ExploreOrderServiceImpl`，覆盖：

- 用户创建预约。
- 相同用户重复提交同一`requestId`时返回原订单，不重复占用名额。
- 管理员确认预约。
- 用户取消本人预约并释放名额。
- 访问他人订单时返回HTTP 409和业务错误。

### 订单可靠性

- `ExploreOrderStatusTest`和`ExploreOrderServiceImplTest`覆盖合法/非法状态流转、重复请求和CAS冲突。
- `ExpiredOrderProcessorTest`覆盖超时边界、容量释放失败回滚和Outbox只写一次。
- `OrderExpirationJobTest`与`OutboxDispatchJobTest`覆盖单条失败隔离、batchId MDC清理和指标结果。
- `OutboxEventTransactionServiceTest`覆盖通知写入、指数退避、DEAD和失效租约拒绝更新。
- `UserNotificationControllerTest`与`AdminOutboxEventControllerTest`覆盖用户隔离、分页校验、ADMIN权限和稳定错误码。
- `BookingMySqlIT`用真实MySQL证明取消/超时竞态、旧租约回滚、通知唯一性和事务原子性。

### 认证与RBAC

- `AuthControllerValidationTest`和`AuthControllerSessionTest`验证双端登录、Refresh/Logout Cookie、Origin、401/403和requestId。
- `AuthenticationServiceTest`验证账号不存在与密码错误对外同文案、禁用账号刷新后撤销全部会话。
- `AuthSessionServiceImplTest`验证Access claims、Refresh摘要、CAS轮换、并发失败和重放整族撤销。
- `LoginProtectionServiceTest`验证失败窗口、HTTP 429锁定、成功清零；`AuthSessionCleanupJobTest`验证清理成功/失败隔离。
- `JwtTokenInterceptorTest`验证跨端Token拒绝、会话状态和账号状态变化后旧Token立即401。
- `AuthSessionMySqlIT`用真实MySQL验证Refresh摘要唯一、两线程只产生一个ACTIVE后继、重放撤销family、轮换事务回滚和并发失败计数。
- `AdminAuthorizationInterceptorTest`验证ADMIN放行、STAFF返回HTTP 403/code 40300，并锁定员工、用户、操作日志和敏感导出的权限边界。
- `auth-session.test.cjs`验证single-flight、只重试一次、双端隔离和退出清理；`react-frontend.test.cjs`验证角色与403行为。

### 异步导出与任务调度

- `ExportJobStatusTest`穷举全部状态组合，确保只有声明的转换可用。
- `ExportJobServiceImplTest`覆盖requestId幂等、唯一键竞争、任务数/行数限制、STAFF归属隔离和敏感导出权限。
- `ExportJobTransactionServiceTest`、`ExportJobProcessorTest`覆盖CAS领取、续租、取消、租约丢失、成功提交、失败退避、永久资源错误不重试，以及生成中取消后临时文件回收。
- `ExportFileGeneratorTest`验证keyset分块、CSV公式注入、SXSSF文本边界、空值、非法字符、最大执行时间和文件大小。
- `LocalExportFileStorageTest`覆盖相对路径、绝对路径、`..`、非法jobId、符号链接和删除幂等。
- `ExportJobCleanupTaskTest`覆盖先过期、再删文件、最后清路径，以及删除失败保留引用等待重试。
- `ExportJobMySqlIT`使用MySQL 8验证唯一键、外键、四类EXPLAIN、双线程领取、租约恢复、双Processor单文件、PII密文快照和10000行CSV/XLSX。
- `ExportFileGeneratorPerformanceIT`生成100000行CSV/XLSX，解析表头和行数，校验SHA-256、201次分块读取、单批最多500行和采样GC后留存堆增量。
- `react-frontend.test.cjs`和`scripts/smoke-export-jobs-playwright.cjs`验证创建、退避轮询、离开页面停止轮询、取消、真实文件超限失败、重试、下载、权限显示和移动端布局。

### 错误与健康检查

- `GlobalExceptionHandlerTest`验证400/401/403/409/500/503状态与业务code映射，唯一键冲突和未知异常不会把SQL或堆栈写入响应体。
- `RedisFallbackHealthIndicatorTest`验证两级缓存正常时为`UP`，Redis不可用时为`DEGRADED`和`l1-mysql-fallback`，且不暴露主机或密码。
- `backend-build-config.test.cjs`锁定`/actuator/health`、MySQL健康组件和Redis降级配置。

### 公共浏览热路径

- `HotReadCacheServiceTest`覆盖L1、L2、MySQL回源、空值、坏JSON、结构版本、绝对TTL、有界旧值和Redis恢复。
- 100线程行为测试证明同一JVM冷Key只回源一次；双实例单测和真实Redis集成测试证明分布式锁限制跨实例回源。
- 锁测试覆盖等待超时不写共享L2、owner安全释放和慢查询watchdog续租。
- `CacheInvalidationCoordinatorTest`证明事务提交后失效，回滚不失效。
- `CacheOpsControllerTest`通过MockMvc验证ADMIN统计/失效/预热、STAFF 403/code 40300和requestId。
- `HotCacheMySqlRedisIT`启动两个独立Spring应用上下文，使用真实MySQL 8和Redis 7验证L2填充、跨实例Pub/Sub、坏值删除、Redis暂停降级和恢复回填。

## 真实smoke覆盖什么

### 后端链路

`smoke-backend-chain.ps1`覆盖：

- 管理员登录。
- 用户登录。
- 用户创建预约。
- 后台确认预约。
- 用户取消预约。
- 后台订单筛选能查到刚创建的订单。

### 后台管理

`smoke-admin-management.ps1`覆盖：

- 分类CRUD和启停用。
- 项目CRUD、上下架和关联字段保存。
- 套餐CRUD、上下架和`packageItems`持久化。
- 员工新增、编辑、启停用和删除。
- 用户启停用接口。
- 成功写操作进入操作日志。
- 失败写操作不伪记成功。

### 用户互动

`smoke-user-engagement.ps1`覆盖：

- 浏览记录。
- 收藏、取消收藏和状态回读。
- 已完成项目订单评价。
- 已完成套餐订单评价。
- 商家回复评价。
- 用户端评价回读。

### 运行配置

`smoke-runtime-settings.ps1`覆盖：

- 修改商户资料。
- 修改营业状态。
- 管理端和用户端都能读到新值。
- 操作日志写入。
- 脚本结束恢复原值。

### 关键一致性

`smoke-critical-consistency.ps1`覆盖：

- 同时发起2次预约，只有1次成功。
- 取消后名额恢复。
- 禁用员工后旧员工token立即401。
- 禁用用户后旧用户token立即401。

### 订单可靠性

`smoke-order-reliability.ps1`覆盖：

- 同一`requestId`提交两次返回同一订单id。
- 容量只占用一次。
- 定时任务把待确认订单改为系统超时取消。
- 容量恢复到创建前数值。
- Outbox最终生成`ORDER_EXPIRED`通知，用户可见并可标记已读。

PowerShell脚本是Windows入口，内部调用跨平台的`scripts/smoke-order-reliability.cjs`；CI直接执行Node入口，验证内容完全一致。

### 认证会话

`smoke-auth-session.ps1`覆盖：

- 管理端和用户端登录、Refresh Cookie属性与轮换。
- 旧Refresh Token过宽限期重放后撤销整个token family。
- 当前退出和全部退出后Refresh不可恢复。
- 连续失败进入HTTP 429/code 42900，ADMIN查询并解除锁定。
- 会话状态统计字段始终为数值。

`npm run smoke:auth`使用Playwright连接真实8080后端，分别验证管理端和用户端登录、清空sessionStorage后的Cookie恢复、伪造过期Access Token后的single-flight续期、退出后不可恢复，并输出桌面端和移动端截图。

`scripts/smoke-cache-performance.cjs`对同一公共分类接口执行冷态、热态和预热后三组请求，每组至少200次，断言冷态只回源一次、热态命中率不低于99%，并输出p50/p95/p99、吞吐和分层计数。

## 本地怎么跑

单元和契约测试：

```powershell
.\mvnw.cmd test
node --test explorer-web\src\test\js\*.test.cjs
```

上面两条命令不需要Docker。Docker Desktop已启动时运行真实MySQL和Redis测试：

```powershell
.\mvnw.cmd verify -Pintegration-test
```

它使用随机端口临时容器，不污染本机数据库或Redis。详细说明见`docs/INTEGRATION_TESTING.md`。

前端渲染和点击smoke：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\smoke-demo-pages.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\smoke-demo-interactions.ps1
```

真实后端smoke需要先启动MySQL并初始化数据库，再在IDEA里运行`LocalExplorerApplication`：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\smoke-backend-chain.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\smoke-admin-management.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\smoke-user-engagement.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\smoke-runtime-settings.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\smoke-critical-consistency.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\smoke-order-reliability.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\smoke-auth-session.ps1
Set-Location explorer-web\frontend
npm run smoke:auth
npm run smoke:cache-performance
npm run smoke:export
npm run smoke:export:demo
```

可靠性smoke需要在IDEA运行配置中临时设置`ORDER_PENDING_TIMEOUT_MINUTES=1;ORDER_EXPIRATION_DELAY_MS=1000;OUTBOX_DELAY_MS=500`并重启后端。普通开发默认超时30分钟。

## 面试讲法

可以这样讲：

> 我把测试分成Service单测、并发行为测试、MockMvc接口链路、源码契约、浏览器smoke和真实MySQL/Redis集成测试。默认测试无需Docker；独立Profile启动两个Spring上下文，既验证订单事务与认证CAS，也验证跨实例缓存锁、失效和Redis故障恢复。CI还跑真实API、每组200请求性能smoke与双端Playwright闭环。

这能证明项目不是只靠演示数据撑起来的。
