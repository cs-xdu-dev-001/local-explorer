# 真实MySQL与Redis集成测试

默认单元测试追求快速和稳定，Mapper等外部依赖会被Mock；Testcontainers集成测试启动一次性MySQL 8和Redis 7容器，让Spring、MyBatis、事务、缓存协议和数据库约束一起工作。两层测试用途不同，不能互相替代。

## 怎么运行

没有Docker时只跑默认测试，不会尝试连接Docker：

```powershell
.\mvnw.cmd test
```

Docker Desktop已启动时，运行真实MySQL和Redis测试：

```powershell
.\mvnw.cmd verify -Pintegration-test
```

首次运行会下载`mysql:8.0.36`、`redis:7.2-alpine`和Testcontainers辅助镜像，耗时会比后续运行长。容器使用随机宿主机端口，结束后自动销毁，不连接也不修改本机MySQL或Redis。

## 验证范围

`BookingMySqlIT`包含15个订单与可观测性用例：

- 执行`docs/local-explorer-init.sql`，并通过真实Mapper和MySQL元数据验证结构、索引、外键与演示数据。
- 直接提交重复`(user_id, request_id)`，验证数据库拒绝第二条订单。
- 验证预约创建、重复请求幂等、并发相同`requestId`只创建一单、用户取消和容量释放。
- 两线程同时抢最后1个名额，验证只有1个订单成功且`booked`不超容量。
- 两线程同时超时只能成功一次；用户取消与系统超时竞争也只能成功一个，容量和事件只处理一次。
- 模拟容量释放失败，验证订单状态、容量和Outbox整体回滚。
- 验证Outbox失败重试、重复消费通知唯一，以及旧租约不能覆盖新worker或提交通知。
- 通过真实HTTP验证创建幂等、系统超时、容量恢复、通知分页和订单详情。
- 验证`updateStatusIfCurrent`拒绝过期状态写入，外键拒绝不存在的用户。
- 使用MySQL `EXPLAIN`确认超时扫描命中`idx_order_status_expire`。
- 启动随机端口Web服务，验证请求ID、健康检查、HTTP耗时和预约Prometheus指标。
- 验证Springfox接口文档与Actuator同时启用时，`/doc.html`和带`group`参数的`/v2/api-docs`均可正常访问。

测试入口：

```text
explorer-web/src/integration-test/java/com/localexplorer/integration/BookingMySqlIT.java
```

`AuthSessionMySqlIT`包含5个认证并发与事务用例：

- 验证`auth_session`、`login_guard`字段、Refresh摘要唯一键和会话查询索引。
- 两线程同时轮换同一Refresh Token，只有一个成功且数据库只有一个ACTIVE后继。
- 过并发宽限期重放旧Token，验证整个token family被撤销。
- 用MySQL触发器模拟新会话插入失败，验证旧Token状态回滚为ACTIVE并可再次轮换。
- 多线程同时记录首次登录失败，验证原子upsert不丢次数并按阈值锁定。

测试入口：

```text
explorer-web/src/integration-test/java/com/localexplorer/integration/AuthSessionMySqlIT.java
```

`HotCacheMySqlRedisIT`包含4个公共缓存真实故障用例：

- 启动两个独立Spring Web应用上下文，共享同一临时MySQL和Redis。
- 验证真实L2填充、结构损坏删除和MySQL回源。
- 两个应用同时读取慢冷Key，Redis锁watchdog续租且MySQL只执行一次。
- 一个实例提交营业状态修改后，另一个实例通过Pub/Sub及时清理L1；事务回滚不失效。
- 暂停Redis容器模拟网络超时，验证已有L1继续服务、未命中回源MySQL且总等待小于2秒；恢复后无需重启即可重新填充L2。

测试入口：

```text
explorer-web/src/integration-test/java/com/localexplorer/integration/HotCacheMySqlRedisIT.java
```

## 报告位置

完整命令成功后生成：

```text
explorer-web/target/failsafe-reports/
explorer-web/target/site/jacoco-it/index.html
```

普通单测报告仍在：

```text
explorer-web/target/surefire-reports/
explorer-web/target/site/jacoco/index.html
```

GitHub Actions把默认测试和`integration-test`拆成两个任务。默认任务无需Docker；集成任务运行真实MySQL/Redis用例，之后启动后端执行缓存性能、订单可靠性、认证轮换/重放/锁定和双端Playwright续期smoke，并上传Surefire、Failsafe、JaCoCo、性能报告、截图和后端日志。

## 常见问题

| 现象 | 处理 |
| --- | --- |
| `Could not find a valid Docker environment` | 启动Docker Desktop，等待`docker info`成功后重试 |
| `The system cannot find ... dockerDesktopLinuxEngine` | Docker Desktop未启动，或尚未切到Linux容器模式 |
| 首次运行长时间停在拉取镜像 | 查看Docker Desktop下载进度；镜像缓存后后续运行会明显加快 |
| Docker 29拒绝旧API版本 | 项目已使用Testcontainers 1.21.4并按API v1.44连接；先确认没有被旧依赖覆盖 |
| 本机MySQL数据没有出现测试订单 | 这是预期行为；集成测试只写入临时容器数据库 |

## 面试讲法

> 默认单测用Mock快速验证分支，独立Maven Profile再用Testcontainers拉起MySQL 8和Redis 7。真实用例既证明订单、Outbox、认证CAS和数据库约束，也用两个Spring上下文证明分布式缓存锁、提交后跨实例失效、坏值处理和Redis停机恢复；随机端口容器随测试销毁，不污染开发环境。
