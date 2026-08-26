# 测试报告

## 最近结果

2026-08-24完成全量回归：

```text
Maven tests: 339 run, 0 failures, 0 errors, 0 skipped
Node frontend/docs/run tests: 151 passed
Testcontainers MySQL + Redis: 34 run, 0 failures, 0 errors, 0 skipped
Frontend build: Vite production build succeeded
Frontend dependency audit: 0 vulnerabilities
Cache performance smoke: 600 HTTP requests passed, cold burst loaded MySQL once, hot/prewarmed loaded MySQL zero times
Playwright notification smoke: desktop and mobile passed
Authentication API smoke: rotation, replay family revocation, logout/logout-all, HTTP 429 and ADMIN unlock passed
Authentication Playwright smoke: admin desktop and user mobile automatic renewal, logout and screenshots passed
Dev runtime smoke: health, Prometheus, Swagger, frontend, idempotent create, timeout, cache-visible capacity recovery and notification passed
Async export MySQL smoke: CAS claim, lease recovery, 10000-row CSV/XLSX, PII encryption and checksum passed
Async export Playwright: real create/wait/download/cancel/file-limit failure/retry/polling cleanup and demo failed/retry passed on desktop/mobile
```

## 异步导出证据

2026-08-24已完成专项真实MySQL验证：

| 证据 | 结果 |
| --- | --- |
| 双线程CAS领取同一任务 | 仅一个Worker更新成功 |
| 两个Processor并发处理同一任务 | 仅一个返回成功，成品目录只有一个有效文件 |
| 过期RUNNING租约恢复 | 新owner领取成功，旧owner进度CAS被拒绝，任务最终完成 |
| PII查询快照 | 姓名、联系人和手机号以AES-GCM密文持久化，文件只含脱敏手机号 |
| MySQL EXPLAIN | ready、lease、STAFF列表和ADMIN列表均命中指定联合索引 |
| 10000行真实MySQL CSV | 10000数据行、表头和SHA-256正确；本机约434ms、907979字节 |
| 10000行真实MySQL XLSX | 10000数据行、表头和SHA-256正确；本机约547ms、256829字节 |
| 100000行生成器CSV/XLSX | 均按201次keyset分块、单批最多500行完成；CSV/XLSX采样留存堆增量约1.2MB/4.4MB |

性能数字是本机单次功能性smoke，不作为生产容量承诺。原始证据位于`explorer-web/target/export-performance/`：

- `real-mysql-smoke.json`
- `real-mysql-10000.csv`
- `real-mysql-10000.xlsx`
- `export-performance.json`
- `export-100000.csv`
- `export-100000.xlsx`

## 缓存性能证据

使用独立MySQL 8、Redis 7和当前后端执行`node scripts/smoke-cache-performance.cjs`，每组200请求、并发度25：

| 场景 | p50 | p95 | p99 | 吞吐 | MySQL回源 | 缓存命中率 |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| 冷启动突发 | 13.19ms | 151.33ms | 165.35ms | 702.02req/s | 1 | 99.43% |
| 热缓存 | 13.08ms | 19.81ms | 22.33ms | 1780.17req/s | 0 | 100% |
| 主动预热后 | 11.26ms | 46.09ms | 47.23ms | 1450.42req/s | 0 | 100% |

冷启动200个请求中，25个首批并发由single-flight合并，只有leader记一次数据库加载；followers复用同一结果，因此层级计数不等于HTTP请求总数。原始JSON保存在`explorer-web/target/cache-performance-report.json`，CI会将其作为artifact上传。

## 覆盖范围

| 层级 | 覆盖 | 代表测试 |
| --- | --- | --- |
| Service单元测试 | 状态机、容量、预约幂等、超时回滚、任务隔离、Outbox退避与租约 | `ExploreOrderServiceImplTest`、`ExpiredOrderProcessorTest`、`OutboxEventTransactionServiceTest` |
| MockMvc链路测试 | 创建预约、重复`requestId`、后台确认、用户取消、归属校验 | `BookingApiFlowTest` |
| MySQL/Redis集成测试 | 订单可靠性、Refresh CAS，以及真实L2、分布式锁、跨实例失效、坏值和Redis恢复 | `BookingMySqlIT`、`AuthSessionMySqlIT`、`HotCacheMySqlRedisIT` |
| 缓存并发与一致性 | 100线程single-flight、锁续租、提交后失效、回滚不失效、ADMIN运维权限 | `HotReadCacheServiceTest`、`CacheInvalidationCoordinatorTest`、`CacheOpsControllerTest` |
| 认证与权限测试 | 双端登录、Cookie/Origin、轮换、退出、锁定、会话清理、禁用后失效和STAFF越权 | `AuthenticationServiceTest`、`AuthSessionServiceImplTest`、`AuthControllerSessionTest`、`JwtTokenInterceptorTest` |
| 异常测试 | HTTP状态与业务code映射、SQL/堆栈不进入响应体 | `GlobalExceptionHandlerTest` |
| 健康检查测试 | Redis正常与内存降级状态，不暴露连接信息 | `RedisFallbackHealthIndicatorTest` |
| 异步导出单元测试 | 状态矩阵、幂等、权限、租约、退避、格式、安全存储和清理 | `ExportJob*Test`、`ExportFileGeneratorTest`、`LocalExportFileStorageTest` |
| 异步导出集成/性能 | MySQL CAS/EXPLAIN/恢复、10000行双格式和100000行有界内存 | `ExportJobMySqlIT`、`ExportFileGeneratorPerformanceIT` |
| 契约测试 | README定位、数据库约束、RBAC配置、Actuator配置、CI | Node Test测试集 |
| smoke测试 | 前端渲染、通知、真实后端业务链路、认证轮换/重放/锁定，以及双端Playwright自动续期 | `scripts/smoke-*.ps1`、`npm run smoke:auth` |

`BookingApiFlowTest`使用MockMvc调用真实Controller和真实`ExploreOrderServiceImpl`，只替换Mapper等外部依赖。这样能覆盖HTTP入参、当前用户、业务状态流转和返回结构，同时不要求测试机预装MySQL。

## 验证命令

```powershell
.\mvnw.cmd test
.\mvnw.cmd verify -Pintegration-test
node --test explorer-web\src\test\js\*.test.cjs
Set-Location explorer-web\frontend
npm run build
npm audit --audit-level=high
npm run smoke:notification
npm run smoke:auth
npm run smoke:cache-performance
npm run smoke:export
npm run smoke:export:demo
```

JaCoCo报告生成在：

```text
explorer-web/target/site/jacoco/index.html
explorer-web/target/site/jacoco-it/index.html
explorer-web/target/surefire-reports/
explorer-web/target/failsafe-reports/
explorer-web/target/cache-performance-report.json
explorer-web/target/export-performance/
```

## CI

GitHub Actions配置在`.github/workflows/ci.yml`。快速任务执行JDK8 Maven测试、Node契约、依赖审计、前端构建和通知Playwright；独立`integration-test`任务运行Testcontainers MySQL/Redis，并启动真实后端执行缓存性能、订单可靠性、认证API、异步导出真实/故障场景及双端Playwright。任务会上传Surefire/Failsafe、JaCoCo、缓存与导出性能JSON、CSV/XLSX样例、桌面/移动截图和后端日志。

## 面试讲法

> 我没有只做Service层Mockito测试。MockMvc验证HTTP和权限，Testcontainers执行真实MySQL 8并启动Redis 7和两个Spring上下文，验证订单事务、缓存锁、跨实例失效和故障恢复；异步导出还验证了CAS领取、租约恢复、10000行MySQL双格式和100000行流式生成。CI实际启动后端跑缓存、可靠事件、认证和导出闭环，并保留Surefire、Failsafe、JaCoCo、性能JSON、样例文件、截图和日志。
