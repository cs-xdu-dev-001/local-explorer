const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const test = require("node:test");

const repoRoot = path.resolve(__dirname, "../../../../");

function readScript(name) {
  return fs.readFileSync(path.join(repoRoot, "scripts", name), "utf8");
}

test("demo run script starts the React demo with static data", () => {
  const script = readScript("run-demo.ps1");

  assert.match(script, /npm install/);
  assert.match(script, /npx\.cmd/);
  assert.match(script, /vite/);
  assert.match(script, /--port/);
  assert.match(script, /\/client\/index\.html\?demo=1/);
  assert.match(script, /\/console\/index\.html\?demo=1/);
});

test("frontend dev run script starts Vite for an IDEA backend", () => {
  const script = readScript("run-frontend.ps1");

  assert.match(script, /npm install/);
  assert.match(script, /npx\.cmd/);
  assert.match(script, /vite/);
  assert.match(script, /--port/);
  assert.match(script, /\/console\/login\.html/);
  assert.match(script, /\/client\/login\.html/);
  assert.doesNotMatch(script, /npm run build/);
  assert.doesNotMatch(script, /docker compose/);
  assert.doesNotMatch(script, /spring-boot:run/);
});

test("README documents one-command run options", () => {
  const readme = fs.readFileSync(path.join(repoRoot, "README.md"), "utf8");

  assert.match(readme, /## 一键运行/);
  assert.match(readme, /scripts\\run-demo\.ps1/);
  assert.match(readme, /scripts\\run-frontend\.ps1/);
  assert.match(readme, /### 最短跑通路径/);
  assert.match(readme, /IDEA/);
  assert.match(readme, /LocalExplorerApplication/);
  assert.doesNotMatch(readme, /scripts\\run-full\.ps1/);
  assert.doesNotMatch(readme, /scripts\\smoke-full\.ps1/);
  assert.doesNotMatch(readme, /mvnw\.cmd package/);
});

test("README documents local setup pitfalls with concrete fixes", () => {
  const readme = fs.readFileSync(path.join(repoRoot, "README.md"), "utf8");

  assert.match(readme, /## 常见报错/);
  assert.match(readme, /cmd \/c 'mysql -u root -p1234 < docs\\local-explorer-init\.sql'/);
  assert.match(readme, /DROP DATABASE IF EXISTS local_explorer/);
  assert.match(readme, /DB_NAME=local_explorer/);
  assert.match(readme, /Redis 7/);
  assert.match(readme, /3306/);
  assert.match(readme, /6379/);
  assert.match(readme, /--explorer\.datasource\.database=local_explorer/);
  assert.match(readme, /\$env:DB_NAME='local_explorer'/);
  assert.doesNotMatch(readme, /-pl explorer-web -am spring-boot:run/);
  assert.doesNotMatch(readme, /Unable to find a suitable main class/);
  assert.match(readme, /End of Central Directory record could not be found/);
  assert.match(readme, /自动校验、删除损坏缓存/);
  assert.match(readme, /Port 8080 was already in use/);
  assert.match(readme, /taskkill \/PID/);
  assert.match(readme, /PowerShell/);
  assert.match(readme, /Program arguments/);
  assert.match(readme, /agent_studio\.employee/);
  assert.match(readme, /Unknown column 'duration_minutes'/);
  assert.match(readme, /Unknown column 'capacity'/);
  assert.match(readme, /Unknown column 'reply_content'/);
  assert.match(readme, /Table 'local_explorer\.runtime_setting' doesn't exist/);
  assert.match(readme, /LocalExplorerApplication/);
  assert.match(readme, /localhost:8080/);
  assert.match(readme, /openjdk-24/);
  assert.match(readme, /lombok 1\.18\.44/);
  assert.match(readme, /Maven Reload/);
  assert.match(readme, /本机 MySQL 和 Docker MySQL 二选一/);
  assert.match(readme, /ports are not available/);
  assert.match(readme, /3306 被占用/);
});

test("backend packaging helpers are removed from the simplified workflow", () => {
  const readme = fs.readFileSync(path.join(repoRoot, "README.md"), "utf8");

  assert.equal(fs.existsSync(path.join(repoRoot, "scripts", "run-full.ps1")), false);
  assert.equal(fs.existsSync(path.join(repoRoot, "scripts", "smoke-full.ps1")), false);
  assert.doesNotMatch(readme, /npm run build/);
  assert.doesNotMatch(readme, /mvnw\.cmd package/);
  assert.doesNotMatch(readme, /\\.\\run\.cmd full/);
  assert.doesNotMatch(readme, /\\.\\run\.cmd smoke/);
});

test("root command wrapper keeps common run commands short", () => {
  const wrapper = fs.readFileSync(path.join(repoRoot, "run.cmd"), "utf8");
  const readme = fs.readFileSync(path.join(repoRoot, "README.md"), "utf8");

  assert.match(wrapper, /scripts\\run-demo\.ps1/);
  assert.match(wrapper, /scripts\\run-frontend\.ps1/);
  assert.match(wrapper, /demo/);
  assert.match(wrapper, /dev/);
  assert.doesNotMatch(wrapper, /scripts\\run-full\.ps1/);
  assert.doesNotMatch(wrapper, /scripts\\smoke-full\.ps1/);

  assert.match(readme, /\.\\run\.cmd/);
  assert.match(readme, /\.\\run\.cmd dev/);
  assert.doesNotMatch(readme, /\.\\run\.cmd full/);
  assert.doesNotMatch(readme, /\.\\run\.cmd smoke/);
});

test("screenshot workflow captures login and client order evidence", () => {
  const script = readScript("capture-demo-screenshots.cjs");
  const screenshotReadme = fs.readFileSync(path.join(repoRoot, "docs", "screenshots", "README.md"), "utf8");
  const wrapper = readScript("capture-demo-screenshots.ps1");

  assert.match(script, /CHROME_PATH/);
  assert.match(script, /Google\\\\Chrome\\\\Application\\\\chrome\.exe/);
  assert.match(wrapper, /\$BrowserPath/);
  assert.match(script, /console-login\.png/);
  assert.match(script, /client-login\.png/);
  assert.match(script, /client-orders\.png/);
  assert.match(script, /console\/login\.html\?demo=1/);
  assert.match(script, /client\/login\.html\?demo=1/);
  assert.match(script, /client\/my-orders\.html\?demo=1/);
  assert.match(script, /Page\.captureScreenshot/);
  assert.match(script, /Emulation\.setDeviceMetricsOverride/);
  assert.match(script, /captureBeyondViewport: false/);
  assert.match(script, /visibleImages\.every/);
  assert.match(script, /readUInt32BE\(16\)/);
  assert.match(script, /async function removeProfile/);
  assert.match(script, /"EBUSY", "EPERM", "ENOTEMPTY"/);
  assert.doesNotMatch(script, /--disable-software-rasterizer/);
  assert.match(screenshotReadme, /console-login\.png/);
  assert.match(screenshotReadme, /client-login\.png/);
  assert.match(screenshotReadme, /client-orders\.png/);
  assert.match(wrapper, /\$LASTEXITCODE/);
  assert.match(wrapper, /Screenshot capture failed/);
});

test("UI smoke workflow verifies rendered admin and client pages", () => {
  const script = readScript("smoke-demo-pages.cjs");
  const wrapper = readScript("smoke-demo-pages.ps1");
  const readme = fs.readFileSync(path.join(repoRoot, "README.md"), "utf8");

  assert.match(script, /BROWSER_PATH/);
  assert.match(script, /EDGE_PATH/);
  assert.match(script, /CHROME_PATH/);
  assert.match(script, /--dump-dom/);
  assert.match(script, /--virtual-time-budget=3000/);
  assert.match(script, /console\/orders\.html\?demo=1/);
  assert.match(script, /console\/reviews\.html\?demo=1/);
  assert.match(script, /client\/index\.html\?demo=1/);
  assert.match(script, /client\/my-orders\.html\?demo=1/);
  assert.match(script, /预约订单/);
  assert.match(script, /用户评价/);
  assert.match(script, /Local Explorer/);
  assert.match(script, /我的预约/);
  assert.match(wrapper, /\$LASTEXITCODE/);
  assert.match(wrapper, /UI smoke failed/);
  assert.match(readme, /scripts\\smoke-demo-pages\.ps1/);
  assert.match(readme, /无头浏览器渲染/);
});

test("interaction smoke workflow clicks through critical admin and client flows", () => {
  const script = readScript("smoke-demo-interactions.cjs");
  const wrapper = readScript("smoke-demo-interactions.ps1");
  const readme = fs.readFileSync(path.join(repoRoot, "README.md"), "utf8");

  assert.match(script, /remote-debugging-port=0/);
  assert.match(script, /Runtime\.evaluate/);
  assert.match(script, /clickButtonByText/);
  assert.match(script, /await waitUntil\([\s\S]*button text/);
  assert.match(script, /console\/orders\.html\?demo=1/);
  assert.match(script, /console\/reviews\.html\?demo=1/);
  assert.match(script, /client\/my-orders\.html\?demo=1/);
  assert.match(script, /client\/index\.html\?demo=1/);
  assert.match(script, /client-order-cancel-confirm/);
  assert.match(script, /width: 375/);
  assert.match(script, /375px confirm-dialog inside viewport/);
  assert.match(script, /active client navigation item inside scroll viewport/);
  assert.match(script, /aria-current=\"page\"/);
  assert.match(script, /client-project-image/);
  assert.match(script, /naturalWidth > 0/);
  assert.match(script, /loaded project image/);
  assert.match(script, /width: 430/);
  assert.match(script, /Emulation\.setDeviceMetricsOverride/);
  assert.match(script, /document\.documentElement\.clientWidth === 430/);
  assert.match(script, /mobile discovery controls inside viewport/);
  assert.match(script, /scrollWidth <= document\.documentElement\.clientWidth/);
  assert.match(script, /nav\.scrollWidth <= nav\.clientWidth/);
  assert.match(script, /async function removeProfile/);
  assert.match(script, /clickCardButtonByText/);
  assert.match(script, /confirm-dialog/);
  assert.match(script, /订单详情/);
  assert.match(script, /暂无匹配评价/);
  assert.match(script, /预约详情/);
  assert.match(script, /套餐包含项目/);
  assert.match(script, /手冲咖啡品鉴/);
  assert.match(wrapper, /\$LASTEXITCODE/);
  assert.match(wrapper, /Interaction smoke failed/);
  assert.match(readme, /scripts\\smoke-demo-interactions\.ps1/);
  assert.match(readme, /关键点击流程/);
});

test("backend chain smoke workflow verifies the IDEA-started API", () => {
  const script = readScript("smoke-backend-chain.cjs");
  const wrapper = readScript("smoke-backend-chain.ps1");
  const readme = fs.readFileSync(path.join(repoRoot, "README.md"), "utf8");

  assert.match(script, /BACKEND_BASE_URL/);
  assert.match(script, /\/admin\/employee\/login/);
  assert.match(script, /\/user\/user\/login/);
  assert.match(script, /\/user\/category\/list\?type=1/);
  assert.match(script, /\/user\/explore-item\/list/);
  assert.match(script, /\/admin\/shop\/status/);
  assert.match(script, /\/admin\/shop\/1/);
  assert.match(script, /\/user\/explore-order/);
  assert.match(script, /\/admin\/explore-order\/status\?id=\$\{orderId\}&status=1/);
  assert.match(script, /verifyAdminOrderFilters/);
  assert.match(script, /\/admin\/explore-order\/page\?page=1&pageSize=20&keyword=\$\{encodeURIComponent\(orderKeyword\)\}&status=1/);
  assert.match(script, /\/user\/explore-order\/\$\{orderId\}\/cancel/);
  assert.match(script, /13800001111/);
  assert.match(script, /admin/);
  assert.match(wrapper, /\$LASTEXITCODE/);
  assert.match(wrapper, /Backend chain smoke failed/);
  assert.match(readme, /scripts\\smoke-backend-chain\.ps1/);
  assert.match(script, /Backend is unreachable/);
  assert.match(script, /API returned code=/);
  assert.match(script, /RedisConnectionFailureException/);
  assert.match(script, /restart the IDEA backend/);
  assert.match(readme, /真实后端链路/);
});

test("order reliability smoke verifies idempotent timeout recovery and notification", () => {
  const scriptPath = path.join(repoRoot, "scripts", "smoke-order-reliability.cjs");
  const wrapperPath = path.join(repoRoot, "scripts", "smoke-order-reliability.ps1");
  assert.equal(fs.existsSync(scriptPath), true, "order reliability smoke script should exist");
  assert.equal(fs.existsSync(wrapperPath), true, "order reliability smoke wrapper should exist");

  const script = fs.readFileSync(scriptPath, "utf8");
  const wrapper = fs.readFileSync(wrapperPath, "utf8");

  assert.match(script, /requestId/);
  assert.match(script, /duplicateOrderId/);
  assert.match(script, /status\) === 4/);
  assert.match(script, /booked/);
  assert.match(script, /\/user\/notification\/page/);
  assert.match(script, /ORDER_EXPIRED/);
  assert.match(script, /\/user\/notification\/unread-count/);
  assert.match(script, /\/user\/notification\/\$\{notification\.id\}\/read/);
  assert.match(script, /ORDER_PENDING_TIMEOUT_MINUTES=1/);
  assert.match(wrapper, /Order reliability smoke failed/);
});

test("authentication smoke verifies rotation replay logout-all and lockout recovery", () => {
  const scriptPath = path.join(repoRoot, "scripts", "smoke-auth-session.cjs");
  const wrapperPath = path.join(repoRoot, "scripts", "smoke-auth-session.ps1");
  assert.equal(fs.existsSync(scriptPath), true, "authentication smoke script should exist");
  assert.equal(fs.existsSync(wrapperPath), true, "authentication smoke wrapper should exist");

  const script = fs.readFileSync(scriptPath, "utf8");
  const wrapper = fs.readFileSync(wrapperPath, "utf8");
  assert.match(script, /\/admin\/employee\/refresh/);
  assert.match(script, /\/user\/user\/refresh/);
  assert.match(script, /\/user\/user\/logout-all/);
  assert.match(script, /replay/i);
  assert.match(script, /HTTP 429/);
  assert.match(script, /\/admin\/auth-security\/lockouts/);
  assert.match(script, /Set-Cookie/);
  assert.match(script, /HttpOnly/);
  assert.match(wrapper, /Authentication session smoke failed/);
});

test("real authentication Playwright verifies both browser session lifecycles", () => {
  const scriptPath = path.join(repoRoot, "scripts", "smoke-auth-playwright.cjs");
  assert.equal(fs.existsSync(scriptPath), true, "real authentication Playwright script should exist");
  const script = fs.readFileSync(scriptPath, "utf8");
  const frontendPackage = fs.readFileSync(path.join(repoRoot, "explorer-web", "frontend", "package.json"), "utf8");

  assert.match(script, /console\/login\.html/);
  assert.match(script, /client\/login\.html/);
  assert.match(script, /sessionStorage\.clear/);
  assert.match(script, /expired-access-token/);
  assert.match(script, /退出登录|退出/);
  assert.match(script, /auth-admin-desktop\.png/);
  assert.match(script, /auth-user-mobile\.png/);
  assert.match(frontendPackage, /"smoke:auth"/);
});

test("asynchronous export Playwright verifies real and failure task flows", () => {
  const scriptPath = path.join(repoRoot, "scripts", "smoke-export-jobs-playwright.cjs");
  assert.equal(fs.existsSync(scriptPath), true, "export Playwright script should exist");
  const script = fs.readFileSync(scriptPath, "utf8");
  const frontendPackage = fs.readFileSync(path.join(repoRoot, "explorer-web", "frontend", "package.json"), "utf8");

  assert.match(script, /pageUrl\("orders"\)/);
  assert.match(script, /pageUrl\("export-jobs"\)/);
  assert.match(script, /创建导出/);
  assert.match(script, /waitForEvent\("download"/);
  assert.match(script, /确认取消/);
  assert.match(script, /任务中心没有展示失败任务/);
  assert.match(script, /EXPORT_EXPECT_REAL_FAILURE/);
  assert.match(script, /createExport\(page, "operation-logs", "CSV"\)/);
  assert.match(script, /EXPORT_FILE_TOO_LARGE/);
  assert.match(script, /verifyPollingStopsAfterNavigation/);
  assert.match(script, /离开任务中心后仍在轮询/);
  assert.match(script, /const prefix = demo \? "export-jobs-demo" : "export-jobs-real"/);
  assert.match(script, /`\$\{prefix\}-mobile\.png`/);
  assert.match(frontendPackage, /"smoke:export"/);
  assert.match(frontendPackage, /"smoke:export:demo"/);
});

test("user engagement smoke verifies browse, favorite, review, and merchant reply", () => {
  const scriptPath = path.join(repoRoot, "scripts", "smoke-user-engagement.cjs");
  const wrapperPath = path.join(repoRoot, "scripts", "smoke-user-engagement.ps1");
  assert.equal(fs.existsSync(scriptPath), true, "user engagement smoke script should exist");
  assert.equal(fs.existsSync(wrapperPath), true, "user engagement smoke wrapper should exist");

  const script = fs.readFileSync(scriptPath, "utf8");
  const wrapper = fs.readFileSync(wrapperPath, "utf8");
  const readme = fs.readFileSync(path.join(repoRoot, "README.md"), "utf8");

  assert.match(script, /\/user\/favorite\/browse\/\$\{item\.id\}/);
  assert.match(script, /\/user\/favorite\/check\/\$\{item\.id\}/);
  assert.match(script, /\/user\/favorite\?page=1&pageSize=99/);
  assert.match(script, /findCompletedUnreviewedOrder/);
  assert.match(script, /findCompletedUnreviewedPackageOrder/);
  assert.match(script, /\/user\/explore-order\/page\?page=1&pageSize=100/);
  assert.match(script, /\/user\/explore-package\/list\?page=1&pageSize=50/);
  assert.match(script, /\/user\/explore-package\/items\/\$\{packageEntity\.id\}/);
  assert.doesNotMatch(script, /method: "POST",[\s\S]{0,160}\/user\/explore-order/);
  assert.doesNotMatch(script, /\/admin\/explore-order\/status/);
  assert.doesNotMatch(script, /occupies 1 slot/);
  assert.match(script, /\/user\/review/);
  assert.match(script, /submitPackageReview/);
  assert.match(script, /\/admin\/review\/reply/);
  assert.match(script, /verifyAdminReviewFilters/);
  assert.match(script, /\/admin\/review\/page\?page=1&pageSize=100&keyword=\$\{encodeURIComponent\(reviewContent\)\}&rating=5&replyState=replied/);
  assert.match(script, /\/admin\/review\/page\?page=1&pageSize=100&keyword=\$\{encodeURIComponent\(packageEntity\.name\)\}&rating=5/);
  assert.match(script, /\/user\/review\/item\/\$\{item\.id\}/);
  assert.match(script, /\/user\/review\/item\/\$\{packageReviewItemId\}/);
  assert.match(script, /DELETE/);
  assert.match(script, /replyContent/);
  assert.match(wrapper, /User engagement smoke failed/);
  assert.match(readme, /scripts\\smoke-user-engagement\.ps1/);
});

test("runtime settings smoke verifies durable merchant operations and audit logs", () => {
  const scriptPath = path.join(repoRoot, "scripts", "smoke-runtime-settings.cjs");
  const wrapperPath = path.join(repoRoot, "scripts", "smoke-runtime-settings.ps1");
  assert.equal(fs.existsSync(scriptPath), true, "runtime settings smoke script should exist");
  assert.equal(fs.existsSync(wrapperPath), true, "runtime settings smoke wrapper should exist");

  const script = fs.readFileSync(scriptPath, "utf8");
  const wrapper = fs.readFileSync(wrapperPath, "utf8");
  const readme = fs.readFileSync(path.join(repoRoot, "README.md"), "utf8");

  assert.match(script, /\/admin\/merchant\/info/);
  assert.match(script, /\/user\/merchant\/info/);
  assert.match(script, /\/admin\/shop\/status/);
  assert.match(script, /\/user\/shop\/status/);
  assert.match(script, /\/admin\/operation-log\/page/);
  assert.match(script, /operation-log\/page\?page=1&pageSize=100/);
  assert.doesNotMatch(script, /pageSize=200/);
  assert.match(script, /waitForAuditLogs/);
  assert.match(script, /finally/);
  assert.match(script, /restoreOriginalSettings/);
  assert.match(wrapper, /Runtime settings smoke failed/);
  assert.match(readme, /scripts\\smoke-runtime-settings\.ps1/);
});

test("admin management smoke workflow verifies CRUD and status endpoints", () => {
  const script = readScript("smoke-admin-management.cjs");
  const wrapper = readScript("smoke-admin-management.ps1");
  const readme = fs.readFileSync(path.join(repoRoot, "README.md"), "utf8");

  assert.match(script, /const prefix = `SM\$\{stamp\.slice\(-6\)\}`/);
  assert.match(script, /\/admin\/employee\/login/);
  assert.match(script, /\/admin\/category/);
  assert.match(script, /\/admin\/category\/status\/\$\{status\}/);
  assert.match(script, /\/admin\/explore-item/);
  assert.match(script, /\/admin\/explore-item\/status\/\$\{status\}/);
  assert.match(script, /\/admin\/explore-package/);
  assert.match(script, /\/admin\/explore-package\/status\/\$\{status\}/);
  assert.match(script, /\/admin\/employee\/status\/\$\{status\}/);
  assert.match(script, /\/admin\/user-manage\/status\/\$\{status\}/);
  assert.match(script, /\/admin\/operation-log\/page/);
  assert.match(script, /operation-log\/page\?page=1&pageSize=100/);
  assert.doesNotMatch(script, /pageSize=200/);
  assert.match(script, /waitForOperationLogs/);
  assert.match(script, /verifyFailedWriteNotAudited/);
  assert.match(script, /\/admin\/employee\?id=1/);
  assert.match(script, /not recorded as success/);
  assert.match(script, /新增员工/);
  assert.match(script, /修改员工资料/);
  assert.match(script, /员工账号启停/);
  assert.match(script, /删除员工/);
  assert.match(script, /Unknown column 'status'/);
  assert.match(script, /docs\/local-explorer-migrate\.sql/);
  assert.match(script, /created category/);
  assert.match(script, /updated item/);
  assert.match(script, /updated user status/);
  assert.match(script, /deleted employee/);
  assert.match(wrapper, /Admin management smoke failed/);
  assert.match(readme, /scripts\\smoke-admin-management\.ps1/);
});

test("critical consistency smoke verifies booking races and immediate session revocation", () => {
  const script = readScript("smoke-critical-consistency.cjs");
  const wrapper = readScript("smoke-critical-consistency.ps1");
  const readme = fs.readFileSync(path.join(repoRoot, "README.md"), "utf8");

  assert.match(script, /Promise\.all/);
  assert.match(script, /successfulReservations/);
  assert.match(script, /bookedAfterReserve/);
  assert.match(script, /bookedAfterCancel/);
  assert.match(script, /disabledEmployeeTokenHttp/);
  assert.match(script, /disabledUserTokenHttp/);
  assert.match(script, /finally/);
  assert.match(script, /restoreState/);
  assert.match(wrapper, /Critical consistency smoke failed/);
  assert.match(readme, /scripts\\smoke-critical-consistency\.ps1/);
});
