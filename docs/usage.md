# 本地运行

推荐使用IDEA启动后端，再用独立PowerShell启动React前端。这条路径不依赖Docker Desktop。

## 环境要求

- JDK 8、17或21
- IntelliJ IDEA
- MySQL 8
- Node.js 20及npm
- Redis 7，可选；未启动时部分公共读取会回退到Caffeine/MySQL

## 首次初始化

在项目根目录打开PowerShell。下面示例使用MySQL账号`root`、密码`1234`：

```powershell
cmd /c 'mysql -u root -p1234 -e "DROP DATABASE IF EXISTS local_explorer"'
cmd /c 'mysql -u root -p1234 < docs\local-explorer-init.sql'
```

如果需要保留旧库数据，不要删库，改为执行非破坏迁移：

```powershell
cmd /c 'mysql -u root -p1234 local_explorer < docs\local-explorer-migrate.sql'
```

## PowerShell一：启动后端

1. 用IDEA打开项目根目录。
2. 等待Maven依赖加载完成。
3. 运行`com.localexplorer.LocalExplorerApplication`。
4. 日志出现`Tomcat started on port(s): 8080`后验证健康状态：

```powershell
Invoke-RestMethod http://localhost:8080/actuator/health | ConvertTo-Json -Depth 5
```

如果电脑里残留了其他项目的数据库环境变量，可在IDEA的Run/Debug Configurations中临时设置：

```text
DB_NAME=local_explorer;DB_USERNAME=root;DB_PASSWORD=1234
```

也可以不通过IDEA，直接在项目根目录运行：

```powershell
.\mvnw.cmd -pl explorer-web -am spring-boot:run
```

## PowerShell二：启动前端

确认后端已经监听`8080`，再在项目根目录执行：

```powershell
.\run.cmd dev
```

首次运行脚本会安装前端依赖。启动后访问：

- 管理端：<http://127.0.0.1:5173/console/login.html>
- 用户端：<http://127.0.0.1:5173/client/login.html>

默认账号：

```text
管理员：admin / 123456
用户：13800001111 / 123456
```

## 常用验证

默认单元测试不需要Docker：

```powershell
.\mvnw.cmd test
node --test explorer-web\src\test\js\*.test.cjs
```

构建前端：

```powershell
Set-Location explorer-web\frontend
npm ci
npm run build
```

Docker Desktop可用时，可运行真实MySQL/Redis集成测试：

```powershell
.\mvnw.cmd verify -Pintegration-test
```

## 本地预览文档站

建议使用独立虚拟环境，避免污染系统Python：

```powershell
py -3 -m venv .venv-docs
.\.venv-docs\Scripts\python.exe -m pip install -r requirements-docs.txt
.\.venv-docs\Scripts\python.exe -m mkdocs serve
```

打开<http://127.0.0.1:8000>。提交前执行严格构建：

```powershell
.\.venv-docs\Scripts\python.exe -m mkdocs build --strict
```

生成的`site/`只用于本地检查，已经加入`.gitignore`，不需要提交。

## GitHub Pages首次启用

仓库已经通过`.github/workflows/docs.yml`完成自动构建和发布。第一次推送前，只需在GitHub仓库执行一次：

1. 打开`Settings → Pages`。
2. 在`Build and deployment`的`Source`中选择`GitHub Actions`。
3. 推送`docs/**`、`mkdocs.yml`、`requirements-docs.txt`或工作流文件到`main`。
4. 在`Actions`页面等待`Docs`工作流完成。

当前远程仓库对应的站点地址是：

<https://cs-xdu-dev-001.github.io/local-explorer/>

如果以后修改GitHub用户名、仓库名或默认分支，需要同步更新`mkdocs.yml`中的`site_url`、`repo_url`、`edit_uri`，以及`.github/workflows/docs.yml`中的分支名。

## 常见问题

| 现象 | 处理 |
| --- | --- |
| PowerShell不支持`<`导入SQL | 使用文档中的`cmd /c 'mysql ... < ...'`写法 |
| 后端连接到其他数据库 | 在IDEA临时覆盖`DB_NAME=local_explorer` |
| 表或字段不存在 | 对旧库执行`docs\local-explorer-migrate.sql`，或删库后重新导入初始化SQL |
| 前端提示`8080`不可达 | 先确认IDEA日志和`/actuator/health`，再启动前端 |
| Pages工作流没有部署步骤 | 确认事件不是Pull Request，并在Settings中选择GitHub Actions作为发布源 |
| MkDocs严格构建失败 | 按日志修复无效链接、重复标题或未纳入导航的Markdown文件 |
