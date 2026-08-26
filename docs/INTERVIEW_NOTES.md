# 本地生活探店项目面试讲解稿

这份文档用于面试前快速复盘项目。重点不是背诵，而是帮你把项目讲得像一个完整工程，而不是一组 CRUD。

## 30 秒介绍

这是一个本地生活探店与商家管理平台，包含Web用户端、商家后台和Spring Boot后端。业务上覆盖用户登录、分类、特色项目、探店套餐、预约、评价、收藏、浏览记录、员工管理、操作日志和异步导出任务中心。技术上主要使用Spring Boot、MyBatis、MySQL、Redis、Caffeine、JWT、AOP和Apache POI。

## 可以重点讲的 5 个点

如果面试岗位偏后端，先看下面五份专项文档，再回到这份讲解稿组织语言：

- `docs/BACKEND_DESIGN.md`：后端分层、认证边界、审计和取舍
- `docs/CONSISTENCY.md`：预约防超卖、状态流转和token立即失效
- `docs/CACHE_AND_REDIS.md`：Caffeine/Redis两级缓存、ZSet和降级策略
- `docs/CACHE_HOT_PATH.md`：热路径、事务后失效、分布式锁、跨实例一致性和性能证据
- `docs/API_AND_ERRORS.md`：接口边界、统一返回和错误码规范
- `docs/DATABASE_DESIGN.md`：表关系、外键、索引、删除限制和状态字段
- `docs/TEST_REPORT.md`：测试结果、CI和JaCoCo报告入口
- `docs/TESTING.md`：单测、契约测试、浏览器smoke和真实后端smoke
- `docs/INTEGRATION_TESTING.md`：Testcontainers真实MySQL、事务、并发和约束证据
- `docs/OBSERVABILITY.md`：requestId、MDC日志、健康检查和业务指标
- `docs/ORDER_RELIABILITY.md`：订单状态机、超时关闭、ShedLock、事务Outbox、租约与最终一致性
- `docs/AUTH_SESSION_SECURITY.md`：短期Access、Refresh轮换、服务端撤销、登录锁定与前端续期
- `docs/ASYNC_EXPORT.md`：导出状态机、数据库租约、流式CSV/XLSX、安全下载和故障恢复

### 0. 订单可靠性主线

后端面试优先讲这一条，它把普通CRUD提升为并发和一致性设计。

可以这样讲：

> 创建预约用requestId唯一键和条件更新保证幂等与不超卖；订单确认、取消、完成和系统超时都走状态机和CAS。超时任务用ShedLock避免多实例重复调度，每个订单用独立事务完成状态、容量和Outbox。Outbox通过带随机令牌的租约领取，失败指数退避并进入DEAD；通知表用eventId唯一键保证重复消费不产生重复通知。Testcontainers真实验证了取消/超时竞态和旧租约回滚。

相关文件：

- `explorer-web/src/main/java/com/localexplorer/domain/ExploreOrderStatus.java`
- `explorer-web/src/main/java/com/localexplorer/service/impl/ExpiredOrderProcessor.java`
- `explorer-web/src/main/java/com/localexplorer/service/impl/OutboxEventTransactionService.java`
- `explorer-web/src/integration-test/java/com/localexplorer/integration/BookingMySqlIT.java`

### 0A. 异步导出与任务调度主线

这条适合回答“大数据量导出为什么不能同步做”和“多实例任务怎样避免重复执行”。

可以这样讲：

> 我把同步CSV改造成异步任务中心。创建时用员工和requestId唯一键保证幂等，并冻结筛选、字段、排序和最大主键；Worker用MySQL CAS抢占租约，定时续租，进程崩溃后由新实例恢复。数据用keyset分块读取，CSV逐行写、XLSX用SXSSF，文件先写临时区，计算SHA-256并原子移动后才CAS标记成功。STAFF只能管理自己的非敏感任务，下载还会重新校验身份、路径、文件大小和checksum。

故障和资源控制：

- 有界Worker线程池、队列、单员工活跃任务数、时间范围、行数、文件大小和执行时长上限。
- 临时故障按有限指数退避，文件过大或执行超时直接失败；取消和租约失效在分块checkpoint终止并回收临时文件。
- 过期文件删除失败会保留数据库引用，下批重试；临时文件和孤儿文件也会清理。
- 查询快照里的姓名、联系人和手机号使用AES-GCM密文，导出手机号只保留脱敏形式。

证据：Testcontainers实际验证两个Processor竞争只生成一个文件、租约恢复、四组EXPLAIN和10000行MySQL CSV/XLSX；100000行性能测试验证201次分块和采样峰值内存。

相关文件：

- `explorer-web/src/main/java/com/localexplorer/service/impl/ExportJobProcessor.java`
- `explorer-web/src/main/java/com/localexplorer/service/ExportFileGenerator.java`
- `explorer-web/src/main/java/com/localexplorer/storage/LocalExportFileStorage.java`
- `explorer-web/src/integration-test/java/com/localexplorer/integration/ExportJobMySqlIT.java`
- `docs/ASYNC_EXPORT.md`

### 1. 双端认证与服务端会话

管理端和用户端使用不同URL前缀、Access Header、JWT secret、Refresh Cookie Path和拦截器，并由MySQL保存可撤销会话。

可以这样讲：

> 我把管理端和用户端认证链路彻底隔离。Access JWT默认30分钟并绑定服务端session，Refresh Token用SecureRandom生成、只存摘要并通过HttpOnly Cookie轮换。刷新用CAS单次消费，事务失败会回滚；过并发宽限后重放旧Token会撤销整个family。退出、禁用和重置密码都能立即失效，登录失败用MySQL原子upsert计数并返回稳定429。

相关文件：

- `explorer-web/src/main/java/com/localexplorer/config/WebMvcConfiguration.java`
- `explorer-web/src/main/java/com/localexplorer/interceptor/JwtTokenAdminInterceptor.java`
- `explorer-web/src/main/java/com/localexplorer/interceptor/JwtTokenUserInterceptor.java`
- `explorer-web/src/main/java/com/localexplorer/service/impl/AuthSessionServiceImpl.java`
- `explorer-web/src/main/java/com/localexplorer/service/impl/LoginProtectionService.java`
- `explorer-web/src/integration-test/java/com/localexplorer/integration/AuthSessionMySqlIT.java`

### 2. Redis ZSet 行为记录

浏览记录和收藏记录用 ZSet，而不是 MySQL 表或 List。

可以这样讲：

> 浏览记录天然需要按最近时间排序、去重和分页。ZSet 的 member 存 itemId，score 存时间戳，`ZADD` 可以更新重复 item 的时间，`ZREVRANGE` 可以直接倒序分页，`ZSCORE` 可以 O(1) 判断是否已收藏。浏览记录还做了 200 条上限淘汰。

相关文件：

- `explorer-web/src/main/java/com/localexplorer/service/impl/UserInteractionServiceImpl.java`
- `explorer-web/src/test/java/com/localexplorer/service/impl/UserInteractionServiceImplTest.java`

### 3. 两级缓存与热点保护

分类、特色项目、套餐属于读多写少数据。

可以这样讲：

> 公共浏览使用有容量上限的Caffeine L1、共享Redis L2和MySQL三级链路。single-flight合并单机热点，带续租的Redis锁限制多实例回源；写操作在事务提交后递增命名空间或精确删除，并用Pub/Sub清理其他实例L1。Redis故障会快速熔断到L1/MySQL，恢复后自动回填。

相关文件：

- `explorer-web/src/main/java/com/localexplorer/service/impl/CategoryServiceImpl.java`
- `explorer-web/src/main/java/com/localexplorer/service/impl/ExploreItemServiceImpl.java`
- `explorer-web/src/main/java/com/localexplorer/service/impl/ExplorePackageServiceImpl.java`
- `explorer-web/src/main/java/com/localexplorer/cache/HotReadCacheService.java`
- `explorer-web/src/integration-test/java/com/localexplorer/integration/HotCacheMySqlRedisIT.java`

### 4. AOP 自动填充和操作日志

项目里有两个横切逻辑。

可以这样讲：

> 公共字段填充和后台操作审计不适合散落在每个Service里，所以我用AOP抽出来。`@AutoFill`在Mapper写操作前填充创建/更新时间和操作人，`@OperationLog`记录后台写操作的URI、带盐IP指纹、耗时和操作者，不保存完整IP。

相关文件：

- `explorer-web/src/main/java/com/localexplorer/aspect/AutoFillAspect.java`
- `explorer-web/src/main/java/com/localexplorer/aspect/OperationLogAspect.java`
- `explorer-web/src/main/java/com/localexplorer/annotation/AutoFill.java`
- `explorer-web/src/main/java/com/localexplorer/annotation/OperationLog.java`

### 5. 工程化和可验证性

项目不是只靠手动点页面。

可以这样讲：

> 我补了 Maven Wrapper、配置样例、Node 文档/前端测试和静态 `?demo=1` 展示模式。面试时可以先用根目录 `.\run.cmd` 看完整前端门面，需要真实接口时再用 IDEA 启动 `LocalExplorerApplication`，然后运行 `.\run.cmd dev` 连接后端。

相关文件：

- `mvnw.cmd`
- `mvnw`
- `explorer-web/src/test/java`
- `explorer-web/src/test/js`
- `scripts/run-demo.ps1`
- `scripts/run-frontend.ps1`

## 面试可能追问

### 为什么浏览记录不用 MySQL？

如果需要强一致、长期留存、复杂统计，可以用 MySQL。但当前浏览记录更像用户行为缓存，需要最近记录、去重、分页和快速判断，Redis ZSet 更合适。后续可以异步落库做长期分析。

### 为什么缓存写操作后要主动失效？

分类、项目、套餐会被用户端频繁读取。如果只靠 TTL，会存在较长时间的脏读窗口；写操作主动清理缓存能让用户端尽快看到最新数据。

### 操作日志为什么用 AOP？

操作日志和业务逻辑正交。如果每个 Controller 手写日志，会产生大量重复代码，并且容易漏记。AOP 可以统一记录写操作，同时保持业务代码干净。

### 项目目前的不足是什么？

可以坦诚说：

- 密码摘要目前是 MD5，生产环境应换成 BCrypt。
- 尚未接入Grafana和自动告警，当前以运营缓存面板、Actuator、Prometheus和requestId日志为主。
- Redis降级到JVM内存后不具备跨进程持久性，正式环境仍需保障Redis可用。
- 缓存性能smoke提供冷/热读分位延迟和命中证据，但仍不是持续数小时的容量压测。
- 当前可靠事件消费者只落用户站内通知；业务规模扩大后可保留Outbox事务边界，将Relay替换为RabbitMQ或Kafka。
- 异步导出当前使用本地文件系统，适合单机或共享盘；多机独立磁盘部署应替换`ExportFileStorage`为对象存储，并保留现有任务状态机与数据库幂等边界。

## 简历条目版本

本地生活探店与商家管理平台  
技术栈：Spring Boot、MyBatis、MySQL、Redis、Caffeine、JWT、React、Vite

- 设计并实现商家后台和 Web 用户端接口，覆盖用户登录、分类、特色项目、套餐、预约、评价、收藏、浏览记录、员工管理和操作日志等模块。
- 使用 React + Vite 重构用户端和商家后台页面，采用多入口构建输出到 Spring Boot 静态资源目录。
- 使用 Redis ZSet 实现用户浏览历史和收藏记录，支持去重、倒序分页、快速计数和浏览记录上限淘汰。
- 为公共浏览实现Caffeine L1、Redis L2和MySQL三级读取链路，以single-flight、带续租分布式锁、空值缓存和TTL抖动保护热点；通过事务提交后失效、命名空间版本和Pub/Sub协调多实例一致性。
- 使用独立双端Access JWT、HttpOnly轮换Refresh Token和MySQL服务端会话实现CAS刷新、重放整族撤销、即时退出及登录防爆破。
- 使用 AOP 实现公共字段自动填充和后台操作审计，减少重复代码并提升可维护性。
- 使用订单状态机、数据库CAS、ShedLock和事务Outbox实现预约超时关闭、容量准确释放、失败重试、DEAD运维和用户通知最终一致。
- 将同步CSV升级为异步导出任务中心，使用MySQL CAS租约、心跳续租和崩溃恢复协调多实例Worker，以keyset分页、SXSSF、原子文件提交和SHA-256完成10000行真实MySQL及100000行流式导出验证。
- 补充Maven Wrapper、Testcontainers、GitHub Actions、JaCoCo、Playwright和真实后端smoke，验证并发、事务、双实例缓存故障恢复、性能分位、通知及移动端闭环。

## 演示顺序

如果现场只需要快速展示界面和业务闭环，先走静态演示模式，不依赖数据库和后端。截图可通过 `scripts\capture-demo-screenshots.ps1` 重新生成。

1. 根目录运行 `.\run.cmd`
2. 打开 `http://127.0.0.1:5173/console/index.html?demo=1`
3. 展示运营概览、分类、特色项目、套餐、预约订单、评价和操作日志
4. 打开 `http://127.0.0.1:5173/client/index.html?demo=1`
5. 展示发现页、收藏、浏览记录、预约入口和我的预约

如果需要展示真实后端链路：

1. IDEA 打开当前项目根目录
2. 确认本机 MySQL / Redis 可用，并已执行 `docs/local-explorer-init.sql`
3. 在 IDEA 中运行 `com.localexplorer.LocalExplorerApplication`
4. 根目录运行 `.\run.cmd dev`
5. 打开 `http://127.0.0.1:5173/console/login.html`，管理员登录 `admin / 123456`
6. 打开 `http://127.0.0.1:5173/client/login.html`，用户登录 `13800001111 / 123456`

