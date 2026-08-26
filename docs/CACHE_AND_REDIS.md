# 缓存与Redis设计

项目把Redis用于两类边界不同的能力：公共浏览两级缓存，以及用户浏览/收藏ZSet。MySQL始终是业务事实来源；Redis不可用时，公共浏览回退到Caffeine或MySQL，用户行为回退到有容量上限的JVM存储。

## 公共浏览两级缓存

分类、项目、套餐、商户资料和营业状态采用`Caffeine L1 -> Redis L2 -> MySQL`读取链路。实现入口是`HotReadCacheService`，没有继续使用Spring Cache注解，也没有无上限的`ConcurrentMapCacheManager`。

| 层级 | 默认配置 | 作用 |
| --- | --- | --- |
| L1 Caffeine | 最多2000条，20秒新鲜期 | 降低同实例热点读取延迟 |
| L2 Redis | 10分钟，随机抖动最多60秒 | 多实例共享，减少MySQL回源 |
| 空值缓存 | 30秒 | 阻止不存在详情持续穿透 |
| 有界旧值 | L2绝对期限后最多120秒 | Redis和MySQL同时异常时短暂兜底 |

缓存Key格式：

```text
{prefix}:data:{domain}:s{schemaVersion}:n{namespaceVersion}:k{businessKeySha256前24位}
```

Key包含业务域、结构版本、命名空间版本和业务Key摘要，不记录完整业务标识。列表失效递增命名空间版本，详情失效精确删除当前Key；实现没有使用`KEYS`或全库清空。

## 热点击穿保护

- 同一JVM使用`CompletableFuture` single-flight合并相同冷Key请求。
- 多实例使用Redis `SET NX PX`锁，owner令牌配合Lua安全解锁和续租。
- 获取锁后再次检查L2，防止等待期间重复回源。
- 未获得锁只在配置期限内轮询；超时后允许回源MySQL，但不会把结果写入共享L2。
- Redis错误会打开短时熔断，后续请求直接走L1/MySQL；首次恢复成功会清空L1并重新协调版本。
- TTL增加随机抖动，避免大量Key同一时刻失效。

100线程行为测试证明单实例同一冷Key只加载MySQL一次；真实Testcontainers测试启动两个Spring应用上下文，证明共享Redis锁下只回源一次，慢查询超过初始租约时由watchdog续租。

## 写后失效

所有缓存失效由`CacheInvalidationCoordinator`集中执行：

```text
业务事务写MySQL -> 注册TransactionSynchronization -> COMMIT
    -> 递增列表命名空间或删除详情Key
    -> Redis Pub/Sub通知其他实例清理L1
```

事务回滚不会失效缓存。分类、项目、套餐、商户、营业状态以及预约容量变化都维护了依赖失效关系。Pub/Sub消息丢失时，旧数据最多保留一个L1新鲜周期；后续L1过期会解析Redis命名空间，避免长期读旧值。

## 故障与恢复

| 故障 | 行为 |
| --- | --- |
| 启动时Redis不可用 | 应用仍可启动，公共浏览走L1/MySQL |
| 运行中Redis超时 | 200ms连接/命令超时，短时熔断，避免请求长时间阻塞 |
| Redis恢复 | 新请求自动探测成功、清理本地旧值并重新填充L2 |
| L2 JSON损坏或结构过旧 | 删除坏Key并回源MySQL，不把反序列化异常返回给用户 |
| MySQL失败 | 仅在绝对`staleUntil`内返回已有值，过期后保留原异常语义 |
| 失效时Redis失败 | 记录待重试失效，由恢复任务补发 |

Actuator组件`redisFallback`使用`UP`或`DEGRADED`表达两级缓存状态。Redis降级不会掩盖MySQL健康，详情见`docs/OBSERVABILITY.md`。

## ADMIN运维

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/admin/cache/stats` | L1/L2命中、数据库回源、锁竞争、降级、失效和预热状态 |
| POST | `/admin/cache/invalidate/{domain}` | 失效指定业务域，`all`表示全部业务域 |
| POST | `/admin/cache/warmup` | 异步预热首页分类、项目和套餐 |

这些接口受`@RequireAdmin`保护，STAFF返回HTTP 403/code 40300。运营概览只对ADMIN展示紧凑缓存面板。

## 用户行为ZSet

浏览和收藏仍使用Redis ZSet：

```text
key: user:{userId}:browse / user:{userId}:favorite
member: itemId
score: 毫秒时间戳
```

`ZADD`完成去重更新时间，`ZREVRANGE`倒序分页，`ZSCORE`判断收藏，`ZCARD`计数，`ZREMRANGEBYRANK`限制浏览历史数量。这里只保存轻量id，项目详情批量从MySQL查询并按Redis顺序回填。

Redis不可用时，浏览和收藏临时进入有容量上限的JVM存储；该数据不跨实例、不持久化，不能当作生产长期方案。

## 验证入口

- `HotReadCacheServiceTest`
- `CacheInvalidationCoordinatorTest`
- `CacheOpsControllerTest`
- `RedisFallbackHealthIndicatorTest`
- `HotCacheMySqlRedisIT`
- `scripts/smoke-cache-performance.cjs`
- `docs/CACHE_HOT_PATH.md`

面试时可以这样讲：

> MySQL是唯一事实来源。公共浏览使用有容量上限的Caffeine和共享Redis两级缓存，single-flight与带续租的分布式锁保护冷Key；写操作通过事务提交后失效、版本命名空间和Pub/Sub协调多实例。Redis故障会快速熔断到L1/MySQL，恢复后自动回填；真实双实例Testcontainers和200请求性能smoke提供并发与性能证据。
