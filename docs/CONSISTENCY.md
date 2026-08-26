# 一致性与并发控制

这份文档说明项目里最容易被问到的后端问题：预约名额会不会超卖，取消会不会重复释放，禁用账号后旧token会不会继续可用，失败操作会不会写入成功日志。

## 预约名额防超卖

项目和套餐都有`capacity`和`booked`字段。创建预约时，Service会先读取项目或套餐是否存在、是否启用，再执行原子条件更新。

特色项目占用名额：

```sql
update explore_item
set booked = coalesce(booked, 0) + #{peopleCount}, update_time = now()
where id = #{id}
  and status = 1
  and #{peopleCount} > 0
  and (capacity is null or coalesce(booked, 0) + #{peopleCount} <= capacity)
```

套餐占用名额使用同样逻辑。这个更新在数据库里一次完成，多个并发请求同时进来时，只有满足条件的请求会更新成功。

Service判断影响行数：

- `1`：名额占用成功，继续插入订单。
- `0`：名额不足或项目状态变化，抛出业务异常。

相关文件：

- `ExploreOrderServiceImpl#create`
- `ExploreItemMapper#reserveCapacity`
- `ExplorePackageMapper#reserveCapacity`

## 事务边界

创建预约方法标注`@Transactional`。名额占用成功后插入订单，如果后续插入订单失败，事务回滚，名额占用也会回滚。

取消预约和后台状态流转也在事务中完成，状态更新成功后才释放名额。

## 订单状态流转

订单状态：

| 值 | 含义 |
| --- | --- |
| `0` | 待确认 |
| `1` | 已确认 |
| `2` | 已完成 |
| `3` | 已取消 |
| `4` | 系统超时取消 |

允许流转：

```text
待确认 -> 已确认
待确认 -> 已取消
待确认 -> 系统超时取消
已确认 -> 已完成
已确认 -> 已取消
```

不允许：

- 已取消继续改成已确认或已完成。
- 已完成再取消。
- 状态值不是0、1、2、3、4。
- 管理员手工写入系统超时取消；该状态只允许超时任务产生。

普通状态更新使用`updateStatusIfCurrent`，系统超时使用同时校验`expire_at <= now`的`expireIfDue`。只有数据库里的当前状态和预期一致时才更新，可以处理并发点击、重复请求和后台、用户端、超时任务同时操作。

## 取消释放名额

取消或超时时，只有CAS真正改变状态，才释放`booked`：

```text
读取订单 -> 校验归属或后台权限 -> 校验状态流转 -> 条件更新状态 -> 释放项目或套餐名额
```

如果并发请求中第一个请求已经取消或超时成功，第二个请求不会再次释放名额。状态变化、容量释放和Outbox写入处于同一事务，任一步失败都会整体回滚；完整时序和故障恢复见`docs/ORDER_RELIABILITY.md`。

## 会话立即失效与轮换一致性

JWT本身是无状态的，但项目没有只信任JWT。Access Token绑定`auth_session.session_id`，管理端和用户端拦截器每次都校验会话为ACTIVE、端类型和身份匹配，并查询账号状态：

- `logout`撤销当前session，`logout-all`撤销账号全部ACTIVE会话。
- 员工/用户禁用、删除或密码重置时，同一事务撤销全部会话。
- Refresh Token轮换用`UPDATE ... WHERE status='ACTIVE'`消费一次旧会话；两个线程只能有一个成功。
- 轮换和新会话插入在同一事务，插入失败时旧Token仍可用。
- 过2秒并发宽限后重用已轮换Token，会撤销整个token family。
- 请求结束清理`BaseContext`，防止线程复用串身份。

`AuthSessionMySqlIT`用真实MySQL验证CAS、回滚和整族撤销；`smoke-auth-session.ps1`真实验证登录、刷新、重放、logout、logout-all、账号锁定和管理员解锁。完整设计见`docs/AUTH_SESSION_SECURITY.md`。

## 删除保护

后台删除不是无条件硬删：

- 分类有关联项目或套餐时禁止删除。
- 项目或套餐存在历史预约、评价或套餐引用时禁止删除。
- 默认管理员禁止删除。
- 员工被操作日志引用时不能直接删除。

这样做的目的不是“删除麻烦”，而是保护历史订单、评价和审计数据的可追溯性。

## 成功日志不伪造

`OperationLogAspect`只在Controller返回成功`Result`后记录操作日志。业务失败或校验失败不会写成成功操作。

真实smoke会尝试删除默认管理员，这个操作应该失败，并且不能在操作日志里出现“删除员工”的成功记录。

## 验证入口

单测：

```powershell
.\mvnw.cmd test
```

真实后端一致性smoke：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\smoke-critical-consistency.ps1
```

它覆盖：

- 并发预约只成功一次。
- 取消后`booked`恢复。
- 禁用员工后旧员工token立即401。
- 禁用用户后旧用户token立即401。

面试时可以直接讲这条验证链路，比只说“我考虑了并发”更可信。
