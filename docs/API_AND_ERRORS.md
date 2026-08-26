# 接口文档与错误码规范

这份文档用于面试和交付时说明后端接口边界。项目已有Knife4j/Swagger能力，但README需要一份可直接阅读的接口约定，重点说明认证、统一返回、核心接口和错误语义。

## 基本约定

### Base URL

本地开发默认地址：

```text
http://localhost:8080
```

前端Vite开发服务会把接口代理到该地址。

### 请求格式

| 场景 | Content-Type | 说明 |
| --- | --- | --- |
| JSON请求体 | `application/json` | 新增、编辑、登录、预约、评价 |
| 查询参数 | URL query | 分页、筛选、状态切换、删除 |
| 路径参数 | path variable | 详情、收藏、浏览记录、营业状态 |

时间字段使用后端`LocalDateTime`格式，前端提交预约时间时会规范为`yyyy-MM-dd HH:mm`。

### 统一返回

普通业务接口统一返回`Result<T>`：

```json
{
  "code": 1,
  "msg": null,
  "data": {}
}
```

字段含义：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `code` | number | `code = 1`表示成功；失败时返回稳定错误码，如`40000`、`40300`、`40900` |
| `msg` | string | 失败原因，成功时通常为空 |
| `data` | object/array/null | 业务数据，新增预约返回订单id，分页接口返回`PageResult` |
| `requestId` | string/null | 异常响应中的请求追踪ID；成功响应通常省略 |

分页接口返回`PageResult`：

```json
{
  "code": 1,
  "data": {
    "total": 12,
    "records": []
  }
}
```

失败响应同时使用正确的HTTP状态和业务code。例如STAFF访问ADMIN专属接口：

```http
HTTP/1.1 403 Forbidden
Content-Type: application/json
X-Request-Id: 8e04da0f12f04df89ebc252c3d8ea991

{
  "code": 40300,
  "msg": "当前员工没有权限执行该操作",
  "data": null,
  "requestId": "8e04da0f12f04df89ebc252c3d8ea991"
}
```

客户端可以传入`X-Request-Id`，后端会校验后透传；未传时自动生成。前端报错会展示该ID，可用它检索同一次请求的访问日志和错误日志。

## 认证约定

| 端 | 路径前缀 | 登录接口 | Access Header | Refresh Cookie |
| --- | --- | --- | --- | --- |
| 管理端 | `/admin/**` | `POST /admin/employee/login` | `token` | `LX_ADMIN_REFRESH; Path=/admin` |
| 用户端 | `/user/**` | `POST /user/user/login` | `authentication` | `LX_USER_REFRESH; Path=/user` |

登录接口不需要Token。受保护接口必须携带对应端的短期Access Token，跨端Token不能混用。登录和refresh响应JSON只返回Access Token；Refresh Token通过`HttpOnly; SameSite=Lax` Cookie返回，prod默认带`Secure`，禁止写入localStorage或响应体。

Access Token包含`sessionId/tokenType/principalType/jti/iat/exp`。拦截器同时验证签名、端类型、服务端会话ACTIVE和账号启用状态。认证失败、Token过期、会话撤销或账号不可用时返回HTTP 401/code 40100。

每次refresh都会轮换Cookie。旧Refresh Token只允许被CAS消费一次；并发失败返回401，过宽限期重放会撤销整个token family。`logout`撤销当前会话，`logout-all`撤销当前账号全部会话。浏览器的login、refresh和logout请求校验同源或`AUTH_ALLOWED_ORIGINS`。完整设计见`docs/AUTH_SESSION_SECURITY.md`。

连续失败默认按“身份类型+账号摘要+IP摘要”统计，10分钟5次失败后锁定15分钟。第6次及锁定期间返回HTTP 429/code 42900；账号不存在和密码错误统一返回“账号或密码错误”。

### 管理端RBAC

管理端员工分为`ADMIN`和`STAFF`。JWT拦截器先完成身份认证，`AdminAuthorizationInterceptor`再识别`@RequireAdmin`权限边界，Service层的`AdminPermissionService`为关键操作提供第二层校验。

| 能力 | ADMIN | STAFF |
| --- | --- | --- |
| 内容、套餐、预约、评价、商户与营业状态日常运营 | 允许 | 允许 |
| 员工管理 | 允许 | 禁止 |
| 用户资料、状态和密码重置 | 允许 | 禁止 |
| 操作日志 | 允许 | 禁止 |
| 订单/评价异步导出 | 允许 | 允许，仅管理自己的任务 |
| 用户/操作日志异步导出 | 允许 | 禁止 |

未登录或token失效返回401；已登录但角色不足返回403。前端会隐藏STAFF无权入口，但权限判断始终以后端为准。

## 状态值

| 字段 | 值 | 含义 |
| --- | --- | --- |
| 通用`status` | `1` | 启用、上架、营业 |
| 通用`status` | `0` | 禁用、停用、休息 |
| 预约`status` | `0` | 待确认 |
| 预约`status` | `1` | 已确认 |
| 预约`status` | `2` | 已完成 |
| 预约`status` | `3` | 已取消 |
| 预约`status` | `4` | 系统超时取消 |
| 预约`orderType` | `1` | 特色项目 |
| 预约`orderType` | `2` | 探店套餐 |

预约状态流转只允许：

```text
待确认 -> 已确认 / 已取消 / 系统超时取消
已确认 -> 已完成 / 已取消
```

状态变更由Service层校验，数据库更新使用CAS保护并发一致性。管理员只能设置`1/2/3`，`4`只能由超时任务写入；非法转换返回HTTP 409/code 40900。

## 管理端核心接口

### 登录与员工

| 方法 | 路径 | 说明 | 权限 |
| --- | --- | --- | --- |
| POST | `/admin/employee/login` | 员工登录，返回员工信息、role和token | 公开 |
| POST | `/admin/employee/refresh` | 轮换Refresh Cookie并返回新Access Token | Refresh Cookie |
| POST | `/admin/employee/logout` | 撤销当前会话并清除Cookie | Refresh Cookie |
| POST | `/admin/employee/logout-all` | 撤销当前员工全部会话 | ADMIN/STAFF |
| POST | `/admin/employee` | 新增员工 | ADMIN |
| GET | `/admin/employee/page` | 员工分页查询 | ADMIN |
| GET | `/admin/employee/{id}` | 员工详情 | ADMIN |
| PUT | `/admin/employee` | 编辑员工 | ADMIN |
| POST | `/admin/employee/status/{status}?id={id}` | 启用或禁用员工 | ADMIN |
| DELETE | `/admin/employee?id={id}` | 删除员工 | ADMIN |

认证安全运维接口仅ADMIN可用：

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/admin/auth-security/sessions/stats` | ACTIVE/ROTATED/REVOKED/EXPIRED会话统计 |
| GET | `/admin/auth-security/lockouts` | 当前锁定记录分页，只返回脱敏账号提示 |
| DELETE | `/admin/auth-security/lockouts/{id}` | 解除指定登录锁定并记录操作日志 |

登录请求：

```json
{
  "username": "admin",
  "password": "123456"
}
```

### 分类、项目、套餐

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/admin/category` | 新增分类 |
| GET | `/admin/category/page` | 分类分页 |
| GET | `/admin/category/list` | 分类列表 |
| PUT | `/admin/category` | 编辑分类 |
| POST | `/admin/category/status/{status}?id={id}` | 启用或禁用分类 |
| DELETE | `/admin/category?id={id}` | 删除分类 |
| POST | `/admin/explore-item` | 新增特色项目 |
| GET | `/admin/explore-item/page` | 特色项目分页 |
| GET | `/admin/explore-item/{id}` | 特色项目详情 |
| PUT | `/admin/explore-item` | 编辑特色项目 |
| POST | `/admin/explore-item/status/{status}?id={id}` | 上架或停用特色项目 |
| DELETE | `/admin/explore-item?id={id}` | 删除特色项目 |
| POST | `/admin/explore-package` | 新增探店套餐 |
| GET | `/admin/explore-package/page` | 探店套餐分页 |
| GET | `/admin/explore-package/{id}` | 探店套餐详情 |
| PUT | `/admin/explore-package` | 编辑探店套餐 |
| POST | `/admin/explore-package/status/{status}?id={id}` | 上架或停用探店套餐 |
| DELETE | `/admin/explore-package?id={id}` | 删除探店套餐 |

分类创建核心字段：

```json
{
  "type": 1,
  "name": "城市漫游",
  "sort": 10
}
```

特色项目和套餐都包含`categoryId`、`name`、`price`、`durationMinutes`、`capacity`、`district`、`address`、`meetingPoint`、`cancelPolicy`和`status`。套餐还需要`packageItems`，用于保存套餐包含的特色项目。

### 预约、评价、运营

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/admin/explore-order/page` | 预约分页查询 |
| GET | `/admin/explore-order/{id}` | 预约详情 |
| PUT | `/admin/explore-order/status?id={id}&status={status}` | 更新预约状态 |
| GET | `/admin/explore-order/trend` | 运营趋势 |
| GET | `/admin/review/page` | 评价分页 |
| PUT | `/admin/review/reply` | 商家回复评价 |
| DELETE | `/admin/review?id={id}` | 删除评价 |
| GET | `/admin/operation-log/page` | 操作日志分页 |
| POST | `/admin/export-jobs` | 创建订单、用户、评价或操作日志CSV/XLSX任务 |
| GET | `/admin/export-jobs/page` | 任务分页，支持类型、状态、操作者和时间范围 |
| GET | `/admin/export-jobs/{jobId}` | 任务详情 |
| POST | `/admin/export-jobs/{jobId}/cancel` | 取消PENDING或RUNNING任务 |
| POST | `/admin/export-jobs/{jobId}/retry` | 重试FAILED任务 |
| GET | `/admin/export-jobs/{jobId}/download` | 下载已完成且校验通过的文件 |
| GET | `/admin/export-jobs/stats` | 状态、过期租约、成功率和最近失败统计，仅ADMIN |
| GET | `/admin/outbox-event/page` | Outbox事件分页，仅ADMIN |
| GET | `/admin/outbox-event/stats` | Outbox状态统计，仅ADMIN |
| PUT | `/admin/outbox-event/{id}/retry` | 手动重试DEAD事件，仅ADMIN |

创建任务示例：

```json
{
  "requestId": "order-export-20260824-001",
  "exportType": "ORDER",
  "fileFormat": "XLSX",
  "dataStatus": 1,
  "startTime": "2026-08-01T00:00:00",
  "endTime": "2026-08-24T23:59:59"
}
```

同一员工重复提交相同`requestId`时返回原`jobId`。任务状态为`PENDING/RUNNING/SUCCEEDED/FAILED/CANCELED/EXPIRED`；只有`SUCCEEDED`可下载。详细状态机和安全边界见`docs/ASYNC_EXPORT.md`。

后台确认预约示例：

```text
PUT /admin/explore-order/status?id=1001&status=1
token: <admin-token>
```

### 商户、门店、用户管理

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/admin/merchant/info` | 查询商户资料 |
| PUT | `/admin/merchant/info` | 修改商户资料 |
| GET | `/admin/shop/status` | 查询营业状态 |
| PUT | `/admin/shop/{status}` | 修改营业状态 |
| GET | `/admin/user-manage/page` | 用户分页查询 |
| GET | `/admin/user-manage/{id}` | 用户详情 |
| PUT | `/admin/user-manage/{id}` | 编辑用户资料 |
| PUT | `/admin/user-manage/{id}/password/reset` | 重置用户密码 |
| POST | `/admin/user-manage/status/{status}?id={id}` | 启用或禁用用户 |
| GET | `/admin/cache/stats` | 查询两级缓存统计，仅ADMIN |
| POST | `/admin/cache/invalidate/{domain}` | 失效指定缓存域，仅ADMIN |
| POST | `/admin/cache/warmup` | 异步预热首页缓存，仅ADMIN |

用户管理、操作日志查询、缓存运维和导出统计均要求`ADMIN`。STAFF可以创建订单/评价导出，并查看、取消、重试和下载自己的任务；用户/操作日志等敏感导出仅ADMIN可创建。

## 用户端核心接口

### 登录和基础信息

| 方法 | 路径 | 说明 | 认证 |
| --- | --- | --- | --- |
| POST | `/user/user/login` | 用户手机号密码登录 | 否 |
| POST | `/user/user/refresh` | 轮换Refresh Cookie并返回新Access Token | Refresh Cookie |
| POST | `/user/user/logout` | 撤销当前会话并清除Cookie | Refresh Cookie |
| POST | `/user/user/logout-all` | 撤销当前用户全部会话 | 是 |
| GET | `/user/merchant/info` | 商户资料 | 否 |
| GET | `/user/shop/status` | 营业状态 | 否 |
| GET | `/user/category/list?type={type}` | 分类列表 | 否 |
| GET | `/user/explore-item/list` | 特色项目列表 | 否 |
| GET | `/user/explore-package/list` | 探店套餐列表 | 否 |
| GET | `/user/explore-package/items/{id}` | 套餐包含项目 | 否 |

用户登录请求：

```json
{
  "phone": "13800001111",
  "password": "123456"
}
```

## 关键接口速查

面试讲业务链路时，可以直接拿这几条说明端到端流程：

```text
POST /user/explore-order          用户创建预约
PUT /admin/explore-order/status   后台确认、完成或取消预约
GET /admin/operation-log/page     后台查看写操作审计日志
```

### 收藏和浏览记录

| 方法 | 路径 | 说明 | 认证 |
| --- | --- | --- | --- |
| POST | `/user/favorite/browse/{itemId}` | 记录浏览特色项目 | 是 |
| GET | `/user/favorite/browse?page=1&pageSize=10` | 浏览记录分页 | 是 |
| GET | `/user/favorite/browse/count` | 浏览记录数量 | 是 |
| POST | `/user/favorite/{itemId}` | 收藏特色项目 | 是 |
| DELETE | `/user/favorite/{itemId}` | 取消收藏 | 是 |
| GET | `/user/favorite?page=1&pageSize=10` | 收藏列表分页 | 是 |
| GET | `/user/favorite/check/{itemId}` | 查询是否已收藏 | 是 |
| GET | `/user/favorite/count` | 收藏数量 | 是 |

浏览和收藏使用Redis ZSet保存，Redis不可用时降级到JVM内存。

### 预约和评价

| 方法 | 路径 | 说明 | 认证 |
| --- | --- | --- | --- |
| POST | `/user/explore-order` | 创建预约 | 是 |
| GET | `/user/explore-order/page` | 我的预约分页 | 是 |
| GET | `/user/explore-order/{id}` | 我的预约详情 | 是 |
| PUT | `/user/explore-order/{id}/cancel` | 取消本人预约 | 是 |
| GET | `/user/notification/page` | 我的通知分页 | 是 |
| GET | `/user/notification/unread-count` | 未读通知数 | 是 |
| PUT | `/user/notification/{id}/read` | 单条通知已读 | 是 |
| PUT | `/user/notification/read-all` | 全部通知已读 | 是 |
| POST | `/user/review` | 新增评价 | 是 |
| GET | `/user/review/item/{itemId}` | 项目评价列表 | 否 |
| GET | `/user/review/avg/{itemId}` | 项目平均评分 | 否 |

创建预约请求：

```json
{
  "orderType": 1,
  "itemId": 1,
  "peopleCount": 2,
  "contactName": "张三",
  "contactPhone": "13800001111",
  "reserveTime": "2026-08-01 14:30",
  "remark": "希望靠窗",
  "requestId": "booking-20260801-0001"
}
```

`requestId`最长64字符。同一用户重复提交相同`requestId`时，后端返回第一次创建的订单id，不重复占用名额；数据库唯一索引`(user_id, request_id)`处理并发竞争。

新订单会根据`ORDER_PENDING_TIMEOUT_MINUTES`计算`expireAt`。待确认订单到期后由系统以CAS关闭、释放容量并生成`ORDER_EXPIRED`通知；重复任务或用户取消竞争不会重复释放容量。

成功返回：

```json
{
  "code": 1,
  "data": 1001
}
```

## 分页和筛选

分页参数统一使用：

| 参数 | 默认值 | 限制 |
| --- | --- | --- |
| `page` | `1` | 不能小于1，不能超过100000 |
| `pageSize` | `10` | 不能小于1，不能超过100 |
| `keyword` | 空 | 最长100个字符 |

预约分页支持`status`、`orderType`、`orderNo`、`contactName`、`userId`等筛选。操作日志分页支持关键词、请求方法、操作人等筛选，具体以后端DTO为准。

## 错误码规范

成功沿用历史`code = 1`，失败使用`ErrorCode`枚举中的稳定编号，并与HTTP状态保持一致：

| code | HTTP状态 | 含义 | 处理方式 |
| --- | --- | --- | --- |
| `1` | 200 | 成功 | 读取`data` |
| `40000` | 400 | 参数校验、JSON格式或必要参数错误 | 展示`msg`，保留表单内容 |
| `40100` | 401 | token缺失、错误、过期，或账号禁用/删除 | 清理登录态并跳转登录页 |
| `40300` | 403 | 身份有效但角色权限不足 | 保留登录态，提示无权限 |
| `42900` | 429 | 登录失败次数超限，账号/IP组合暂时锁定 | 等待锁定到期或由ADMIN解锁 |
| `40400` | 404 | 请求资源不存在 | 返回上一页或刷新资源 |
| `40500` | 405 | HTTP方法不支持 | 检查接口调用方式 |
| `40900` | 409 | 业务状态冲突 | 展示具体业务`msg` |
| `40901` | 409 | 唯一约束冲突或重复数据 | 提示数据已存在 |
| `40910` | 409 | 导出任务状态冲突 | 刷新任务状态，不重复取消或完成 |
| `40911` | 409 | 导出文件尚不可下载 | 等待任务完成或查看失败原因 |
| `41010` | 410 | 导出文件过期、不存在或校验失败 | 重新创建导出任务 |
| `42910` | 429 | 导出并发、时间范围、行数或文件大小超限 | 缩小范围或等待现有任务结束 |
| `50000` | 500 | 未知系统异常 | 展示通用错误并记录服务端日志 |
| `50300` | 503 | 数据库未初始化或缺少表 | 执行初始化/迁移SQL后重启 |

前端对所有`code !== 1`都按失败处理。只有401清理登录态，403不能误判为token过期。

异步导出执行失败还会在任务详情的`errorCode`字段记录机器可读原因。它不是HTTP响应中的`Result.code`：

| errorCode | 是否自动重试 | 含义 |
| --- | --- | --- |
| `EXPORT_GENERATION_FAILED` | 是，受最大次数和指数退避限制 | 临时I/O或未知生成故障 |
| `EXPORT_FILE_TOO_LARGE` | 否 | 成品或生成中的文件超过配置上限 |
| `EXPORT_RUNTIME_EXCEEDED` | 否 | 单任务执行时间超过配置上限 |

ADMIN可手动重试`FAILED`任务，但若筛选范围或资源配置不变，永久资源错误仍会再次失败。

### 错误来源

| 来源 | 入口 | 示例msg |
| --- | --- | --- |
| 参数校验 | `@Valid`、`GlobalExceptionHandler` | `联系人不能为空`、`每页数量不能超过100` |
| 业务异常 | Service抛出`BaseException`子类 | `账号或密码错误`、`刷新凭证无效或已失效`、`特色项目不存在` |
| 唯一约束 | `SQLIntegrityConstraintViolationException`、`DataIntegrityViolationException` | `数据已存在` |
| 请求体或参数格式错误 | `HttpMessageNotReadableException`、`MissingServletRequestParameterException` | `请求参数错误` |
| 请求方法错误 | `HttpRequestMethodNotSupportedException` | `请求方法不支持` |
| 数据库未初始化 | 缺表异常兜底识别 | `数据库未初始化，请先执行 docs/local-explorer-init.sql` |
| 兜底异常 | `Exception.class` | `未知错误`，响应体不包含堆栈、SQL和连接信息 |

### 常见错误消息

| msg | 常见原因 |
| --- | --- |
| `请求参数错误` | JSON格式错误、缺少必要查询参数、参数类型错误 |
| `状态参数只能为0或1` | 启停用接口传入非法状态 |
| `预约状态不正确` | 预约状态不在0到4之间，或请求了非法状态流转 |
| `系统超时状态只能由超时任务设置` | 管理端试图手工把订单改为状态4 |
| `事件租约已失效，处理结果已回滚` | Outbox事件已被另一个worker接管，本次副作用不会提交 |
| `请勿重复提交` | 评价或订单类操作命中重复提交保护 |
| `当前任务状态不能取消` | 导出任务已完成、失败、取消或过期 |
| `导出文件尚不可下载` | 任务仍在排队或运行中 |
| `导出文件已过期` | 成品超过TTL并进入EXPIRED |
| `特色项目不存在` | 项目已删除或id错误 |
| `探店套餐不存在` | 套餐已删除或id错误 |
| `当前特色项目已有预约记录，不能删除，可改为停用` | 删除被历史预约引用的项目 |
| `当前员工已有操作日志，不能删除，可改为禁用` | 删除被审计日志引用的员工 |
| `数据库未初始化，请先执行 docs/local-explorer-init.sql` | 本机库未导入或连接错库 |

## 健康检查

启动后访问：

```powershell
Invoke-RestMethod http://localhost:8080/actuator/health | ConvertTo-Json -Depth 5
```

Actuator在所有环境开放`health`，展示组件状态但不展示数据库、Redis主机、账号或异常详情。`dev`和`test`额外开放`prometheus`，`prod`默认仍只开放`health`。Outbox出现DEAD事件时整体为`DEGRADED`而不是`DOWN`，具体积压由`/admin/outbox-event/stats`查询。指标与排查方法见`docs/OBSERVABILITY.md`。

| 结果 | 含义 | 处理 |
| --- | --- | --- |
| 总体`UP`，`db`为`UP`，`redisFallback`为`UP` | MySQL和Redis均可用，两级缓存正常 | 可以开始联调 |
| 总体`DEGRADED`，Redis组件`mode`为`l1-mysql-fallback` | MySQL可用，公共缓存已回退L1/MySQL，行为记录进入JVM降级 | 核心浏览可运行；检查Redis并关注降级持续时间 |
| 总体`DOWN`或HTTP 503，`db`不是`UP` | MySQL连接失败或库不可用 | 检查MySQL、库名、账号和初始化SQL |

Redis降级被映射为HTTP 200，方便本地运行；MySQL异常仍由数据库健康组件标记为`DOWN`。

## 面试讲法

可以这样说：

> 我把接口分成认证、授权和异常三层：双端Access JWT与MySQL服务端会话负责身份认证，HttpOnly Refresh Token通过CAS轮换和重放整族撤销延长会话；`@RequireAdmin`区分ADMIN/STAFF，统一异常处理映射稳定HTTP状态和业务code。401、403、429语义分开，SQL、凭证与敏感标识不进入响应体或日志。

对应代码：

- `explorer-common/src/main/java/com/localexplorer/result/Result.java`
- `explorer-common/src/main/java/com/localexplorer/result/PageResult.java`
- `explorer-web/src/main/java/com/localexplorer/handler/GlobalExceptionHandler.java`
- `explorer-web/src/main/java/com/localexplorer/interceptor/AdminAuthorizationInterceptor.java`
- `explorer-web/src/main/java/com/localexplorer/interceptor/JwtTokenAdminInterceptor.java`
- `explorer-web/src/main/java/com/localexplorer/interceptor/JwtTokenUserInterceptor.java`
- `explorer-web/src/main/java/com/localexplorer/health/RedisFallbackHealthIndicator.java`
