# 可观测性说明

项目使用Spring Boot Actuator、Micrometer、Prometheus注册表和结构化请求日志提供最小但完整的定位链路：先用requestId找到一次请求，再看状态、耗时和异常日志，最后结合健康与业务指标判断是单次错误还是系统性问题。

## 请求ID

`RequestTracingFilter`处理每个HTTP请求：

1. 客户端传入合法`X-Request-Id`时原样透传。
2. 未传或格式非法时生成32位十六进制ID。
3. 将requestId写入MDC和响应头`X-Request-Id`。
4. 记录方法、路径、HTTP状态和耗时，结束后清理MDC。

异常响应还会返回`requestId`：

```json
{
  "code": 40900,
  "msg": "名额不足",
  "data": null,
  "requestId": "8e04da0f12f04df89ebc252c3d8ea991"
}
```

前端会生成并发送`X-Request-Id`，报错提示同时展示请求ID。排查时直接在后端日志搜索该值。日志不记录请求体、Token、Cookie、密码、完整手机号或完整IP；操作审计只保存16位带盐IP指纹。Mapper日志保持INFO，避免SQL参数值进入控制台。

## 访问日志

格式示例：

```text
2026-08-24 10:20:30.123 INFO [requestId=8e04da0f12f04df89ebc252c3d8ea991 batchId=no-batch] RequestTracingFilter - HTTP POST /user/explore-order -> 409 (36 ms)
```

未知异常会由`GlobalExceptionHandler`记录同一个MDC requestId，但响应体不包含SQL、堆栈和连接信息。

超时关闭、Outbox投递和导出扫描/清理不是HTTP请求，因此每次批处理单独生成`batchId`写入MDC，控制台pattern会同时输出`requestId`和`batchId`。导出执行日志包含jobId、operatorId、processedRows、result和elapsedMs；日志不输出查询快照、文件绝对路径、事件payload、token、密码或完整手机号。

## 健康检查

开发、测试和生产环境都开放：

```text
GET /actuator/health
```

```powershell
Invoke-RestMethod http://localhost:8080/actuator/health | ConvertTo-Json -Depth 5
```

- `UP`：MySQL和Redis均正常。
- `DEGRADED`：MySQL正常，公共缓存已降级为`l1-mysql-fallback`，或Outbox存在DEAD事件；HTTP仍返回200。
- `DOWN`：MySQL或必要组件不可用，通常返回HTTP 503。

响应展示组件状态，但不展示数据库地址、账号、密码或异常详情。

## Prometheus指标

`dev`和`test`环境开放：

```text
GET /actuator/prometheus
```

`prod`默认只开放`health`，不会暴露Prometheus端点。开发环境查看指标：

```powershell
(Invoke-WebRequest http://localhost:8080/actuator/prometheus).Content
```

Spring Boot自动提供`http_server_requests_seconds_*`，可按URI、方法、状态和异常标签观察请求次数与耗时。自定义预约指标包括：

| Micrometer名称 | Prometheus名称 | 低基数标签 |
| --- | --- | --- |
| `local.explorer.booking.created` | `local_explorer_booking_created_total` | `resource_type=item/package/unknown` |
| `local.explorer.booking.failed` | `local_explorer_booking_failed_total` | `reason=capacity/shop_closed/...` |
| `local.explorer.booking.idempotent` | `local_explorer_booking_idempotent_total` | 无 |
| `local.explorer.booking.capacity.exhausted` | `local_explorer_booking_capacity_exhausted_total` | `resource_type` |
| `local.explorer.order.expiration.scanned` | `local_explorer_order_expiration_scanned_total` | 无 |
| `local.explorer.order.expiration.result` | `local_explorer_order_expiration_result_total` | `result=expired/cas_conflict/failed/scan_failed` |
| `local.explorer.order.expiration.batch` | `local_explorer_order_expiration_batch_seconds` | 无 |
| `local.explorer.outbox.result` | `local_explorer_outbox_result_total` | `result=processed/retry/dead/claim_conflict/...` |
| `local.explorer.outbox.batch` | `local_explorer_outbox_batch_seconds` | 无 |
| `local.explorer.outbox.pending` | `local_explorer_outbox_pending` | 无 |
| `local.explorer.outbox.dead` | `local_explorer_outbox_dead` | 无 |
| `local.explorer.auth.login` | `local_explorer_auth_login_total` | `principal_type`、`result` |
| `local.explorer.auth.refresh` | `local_explorer_auth_refresh_total` | `principal_type`、`result` |
| `local.explorer.auth.revoked` | `local_explorer_auth_revoked_total` | `principal_type` |
| `local.explorer.auth.login.latency` | `local_explorer_auth_login_latency_seconds` | `principal_type` |
| `local.explorer.auth.refresh.latency` | `local_explorer_auth_refresh_latency_seconds` | `principal_type` |
| `local.explorer.auth.cleanup` | `local_explorer_auth_cleanup_seconds` | `result` |
| `local.explorer.cache.access` | `local_explorer_cache_access_total` | `domain`、`layer=l1/l2/database`、`result` |
| `local.explorer.cache.load.duration` | `local_explorer_cache_load_duration_seconds` | `domain`、`layer` |
| `local.explorer.cache.lock.contention` | `local_explorer_cache_lock_contention_total` | `domain` |
| `local.explorer.cache.singleflight` | `local_explorer_cache_singleflight_total` | `domain`、`result=follower` |
| `local.explorer.cache.redis.degraded` | `local_explorer_cache_redis_degraded_total` | `domain` |
| `local.explorer.cache.invalidation` | `local_explorer_cache_invalidation_total` | `domain`、`result` |
| `local.explorer.cache.l1.entries` | `local_explorer_cache_l1_entries` | 无 |
| `local.explorer.export.result` | `local_explorer_export_result_total` | `result`、`export_type`、`format` |
| `local.explorer.export.execution` | `local_explorer_export_execution_seconds` | `result` |
| `local.explorer.export.queue.delay` | `local_explorer_export_queue_delay_seconds` | `export_type` |
| `local.explorer.export.retry.count` | `local_explorer_export_retry_count` | `export_type`、`format` |
| `local.explorer.export.rows` | `local_explorer_export_rows` | `export_type` |
| `local.explorer.export.rows.per.second` | `local_explorer_export_rows_per_second` | `export_type` |
| `local.explorer.export.file.bytes` | `local_explorer_export_file_bytes` | `format` |
| `local.explorer.export.pending/running/failed` | `local_explorer_export_pending/running/failed` | 无 |

指标标签不使用userId、orderId、eventId、requestId或batchId等高基数字段，避免时间序列无限增长。单次请求定位使用日志requestId，批任务定位使用batchId，聚合趋势使用指标，三者职责分开。

认证指标的`principal_type`只允许`EMPLOYEE/USER/UNKNOWN`，`result`只允许预定义结果。登录、刷新和撤销日志包含requestId、截断sessionId、结果与耗时；重放日志只标记`replay_family_revoked`，不输出Refresh Token摘要。ADMIN可用`/admin/auth-security/sessions/stats`和`/admin/auth-security/lockouts`查看会话分布与锁定状态。

缓存访问日志只记录requestId、domain、layer、result、elapsedMs和Key摘要，不记录完整业务Key。ADMIN可用`/admin/cache/stats`查看累计命中、数据库回源、锁竞争、Redis降级、L1条目和待重试失效，并从运营概览执行指定域失效或异步预热。

导出指标的标签只接受固定导出类型、格式和结果枚举，不使用jobId或operatorId。`ExportJobHealthIndicator`汇总PENDING、FAILED和过期租约；出现FAILED或过期租约时状态为`DEGRADED`且HTTP仍为200。ADMIN可通过`/admin/export-jobs/stats`查看状态分布、成功率和最近失败，通过日志jobId进一步定位单任务。

## 当前边界

- 已有健康、日志、HTTP、预约、可靠事件、异步导出和缓存指标，尚未接入Grafana仪表盘与自动告警。
- 当前是单体服务，requestId足以关联一次应用内请求；跨服务后可再接OpenTelemetry trace。
- Redis降级能保证本地核心流程，但正式环境仍应监控Redis可用性和降级持续时间。
- Outbox DEAD不会让服务直接DOWN，告警应同时观察`local_explorer_outbox_dead`和ADMIN统计接口，并由运维判断是否人工重试。
- 导出FAILED和清理失败不会让服务直接DOWN，正式环境应结合积压Gauge、`cleanup_failed`结果和ADMIN统计配置持续时间告警。

## 面试讲法

> 每次请求都透传或生成`X-Request-Id`，定时任务每批生成`batchId`。Actuator区分MySQL DOWN、Redis缓存降级、Outbox DEAD和导出FAILED/过期租约；Micrometer提供HTTP、认证、预约、可靠事件、异步导出及L1/L2/DB缓存指标。Prometheus只在dev/test开放，标签使用固定枚举，不放用户、会话、订单、事件、任务ID或完整缓存Key；原始凭证与完整IP不进入日志。
