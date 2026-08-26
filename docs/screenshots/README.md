# 截图目录

当前截图：

- `console-login.png`：商家后台登录页
- `console-dashboard.png`：商家后台运营概览
- `console-items.png`：特色项目管理
- `console-packages.png`：探店套餐管理
- `client-login.png`：用户端登录页
- `client-home.png`：React 用户端发现页
- `client-orders.png`：用户端我的预约页

这些截图来自静态页 `?demo=1` 模式，不依赖数据库和后端服务。

重新生成：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\capture-demo-screenshots.ps1
```

建议面试展示顺序：先看 `console-login.png` 和 `client-login.png` 说明双端登录入口，再看后台概览、内容管理、用户发现页和预约页。
