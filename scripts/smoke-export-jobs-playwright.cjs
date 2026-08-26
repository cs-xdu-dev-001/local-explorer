const fs = require("node:fs");
const path = require("node:path");
const { createRequire } = require("node:module");

const root = path.resolve(__dirname, "..");
const frontendRequire = createRequire(path.join(root, "explorer-web", "frontend", "package.json"));
const { chromium } = frontendRequire("playwright");
const outputDir = path.join(root, "docs", "screenshots");
const demo = process.argv.includes("--demo");
const expectRealFailure = process.env.EXPORT_EXPECT_REAL_FAILURE === "true";
const baseUrl = (process.argv.find((arg) => arg.startsWith("--base="))?.slice(7)
  || process.env.FRONTEND_BASE_URL
  || (demo ? "http://127.0.0.1:5173" : "http://localhost:8080")).replace(/\/$/, "");
const adminUsername = process.env.ADMIN_USERNAME || "admin";
const adminPassword = process.env.ADMIN_PASSWORD || "123456";

function assert(condition, message) {
  if (!condition) throw new Error(message);
}

function findBrowser() {
  const explicit = process.env.BROWSER_PATH || process.env.EDGE_PATH || process.env.CHROME_PATH;
  return [
    explicit,
    "C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe",
    "C:\\Program Files (x86)\\Google\\Chrome\\Application\\chrome.exe",
    path.join(process.env.LOCALAPPDATA || "", "Google", "Chrome", "Application", "chrome.exe"),
    "C:\\Program Files\\Microsoft\\Edge\\Application\\msedge.exe",
    "/usr/bin/google-chrome", "/usr/bin/chromium", "/usr/bin/chromium-browser"
  ].filter(Boolean).find((candidate) => fs.existsSync(candidate));
}

function pageUrl(page) {
  return `${baseUrl}/console/${page}.html${demo ? "?demo=1" : ""}`;
}

async function waitForAdminPage(page) {
  await page.getByText("加载运营数据中", { exact: true }).waitFor({ state: "hidden", timeout: 30_000 });
  await page.getByRole("button", { name: "退出" }).waitFor({ timeout: 30_000 });
}

async function login(page) {
  if (demo) {
    await page.goto(pageUrl("orders"));
    await waitForAdminPage(page);
    return;
  }
  await page.goto(pageUrl("login"));
  await page.getByLabel("账号").fill(adminUsername);
  await page.getByLabel("密码").fill(adminPassword);
  await page.getByRole("button", { name: "登录后台" }).click();
  await page.waitForURL(/\/console\/index\.html/);
  await waitForAdminPage(page);
}

async function createExport(page, sourcePage, format = "CSV") {
  await page.goto(pageUrl(sourcePage));
  await waitForAdminPage(page);
  await page.getByLabel("导出格式").selectOption(format);
  await page.getByRole("button", { name: "创建导出" }).click();
  await page.getByText(/导出任务已创建/).waitFor({ timeout: 10_000 });
}

async function createOrderExport(page, format = "CSV") {
  await createExport(page, "orders", format);
}

async function newestTaskRow(page) {
  await page.goto(pageUrl("export-jobs"));
  await waitForAdminPage(page);
  await page.getByText("正在加载导出任务", { exact: true }).waitFor({ state: "hidden", timeout: 30_000 });
  const row = page.locator("tbody tr").first();
  await row.waitFor({ timeout: 20_000 });
  return row;
}

async function completeAndDownload(page) {
  await createOrderExport(page, "CSV");
  const row = await newestTaskRow(page);
  await row.getByText("可下载", { exact: true }).waitFor({ timeout: 45_000 });
  const downloadEvent = page.waitForEvent("download", { timeout: 20_000 });
  await row.getByRole("button", { name: "下载" }).click();
  const download = await downloadEvent;
  assert(download.suggestedFilename().toLowerCase().endsWith(".csv"),
    `导出下载文件扩展名不是CSV：${download.suggestedFilename()}`);
  assert((await download.createReadStream()) !== null, "导出下载流不可读");
}

async function createAndCancel(page) {
  await createOrderExport(page, "XLSX");
  const row = await newestTaskRow(page);
  await row.getByRole("button", { name: "取消" }).click();
  const dialog = page.getByRole("alertdialog", { name: "取消导出任务" });
  await dialog.getByRole("button", { name: "确认取消" }).click();
  await row.getByText("已取消", { exact: true }).waitFor({ timeout: 15_000 });
}

async function seedOperationLogs(page) {
  const results = await page.evaluate(async () => {
    const token = sessionStorage.getItem("localExplorerAdminAccess");
    const request = async (url, options = {}) => {
      const response = await fetch(url, {
        ...options,
        headers: {
          "Content-Type": "application/json",
          "X-Request-Id": crypto.randomUUID().replaceAll("-", ""),
          token,
          ...(options.headers || {})
        }
      });
      return response.json();
    };
    const outcomes = [];
    for (let index = 0; index < 8; index += 1) {
      const created = await request("/admin/export-jobs", {
        method: "POST",
        body: JSON.stringify({
          requestId: crypto.randomUUID().replaceAll("-", ""),
          exportType: "ORDER",
          fileFormat: "CSV"
        })
      });
      outcomes.push(created);
      if (created.code === 1) {
        outcomes.push(await request(`/admin/export-jobs/${created.data.jobId}/cancel`, { method: "POST" }));
      }
    }
    return outcomes;
  });
  assert(results.length === 16 && results.every((result) => result.code === 1),
    `无法准备真实失败场景：${JSON.stringify(results)}`);
  await page.waitForTimeout(1000);
}

async function verifyFailureAndRetry(page) {
  if (!demo && !expectRealFailure) return;
  if (!demo) {
    await seedOperationLogs(page);
    await createExport(page, "operation-logs", "CSV");
  }
  else {
    await page.goto(pageUrl("export-jobs"));
    await waitForAdminPage(page);
  }
  const rows = page.locator("tbody tr");
  if (!demo) {
    await newestTaskRow(page);
    await rows.first().getByText("失败", { exact: true }).waitFor({ timeout: 60_000 });
    await page.getByText(/EXPORT_FILE_TOO_LARGE/).waitFor({ timeout: 10_000 });
  }
  const failedIndex = await rows.evaluateAll((items) => items.findIndex((item) => item.textContent.includes("失败")));
  assert(failedIndex >= 0, "任务中心没有展示失败任务");
  const failedRow = rows.nth(failedIndex);
  await failedRow.waitFor();
  const retryResponse = !demo
    ? page.waitForResponse((response) => response.url().includes("/retry") && response.request().method() === "POST")
    : null;
  await failedRow.getByRole("button", { name: "重试" }).click();
  if (retryResponse) {
    await retryResponse;
    await page.getByText("导出任务已重新排队", { exact: true }).waitFor({ timeout: 10_000 });
    await failedRow.getByText("失败", { exact: true }).waitFor({ state: "hidden", timeout: 10_000 }).catch(() => {});
    await failedRow.getByText("失败", { exact: true }).waitFor({ timeout: 60_000 });
  } else {
    await failedRow.getByText("可下载", { exact: true }).waitFor({ timeout: 15_000 });
  }
}

async function verifyPollingStopsAfterNavigation(page) {
  if (demo) return;
  let taskRow;
  for (let attempt = 0; attempt < 3; attempt += 1) {
    await createOrderExport(page, "CSV");
    taskRow = await newestTaskRow(page);
    const status = await taskRow.locator("td").nth(2).innerText();
    if (status.includes("排队中") || status.includes("生成中")) break;
    taskRow = null;
  }
  assert(taskRow, "无法创建用于轮询清理验证的活动任务");

  let pageRequests = 0;
  const countRequest = (request) => {
    if (request.url().includes("/admin/export-jobs/page")) pageRequests += 1;
  };
  page.on("request", countRequest);
  await page.waitForTimeout(200);
  await page.goto(pageUrl("index"));
  await waitForAdminPage(page);
  const requestsAfterNavigation = pageRequests;
  await page.waitForTimeout(3000);
  page.off("request", countRequest);
  assert(pageRequests === requestsAfterNavigation,
    `离开任务中心后仍在轮询：${requestsAfterNavigation} -> ${pageRequests}`);
}

async function verifyResponsiveTaskCenter(page) {
  await page.goto(pageUrl("export-jobs"));
  await waitForAdminPage(page);
  const prefix = demo ? "export-jobs-demo" : "export-jobs-real";
  await page.screenshot({ path: path.join(outputDir, `${prefix}-desktop.png`), fullPage: true });
  await page.setViewportSize({ width: 375, height: 812 });
  await page.reload();
  await waitForAdminPage(page);
  const layout = await page.evaluate(() => ({
    viewportWidth: document.documentElement.clientWidth,
    documentWidth: document.documentElement.scrollWidth,
    panelWidth: document.querySelector(".export-jobs-panel")?.getBoundingClientRect().width || 0
  }));
  assert(layout.documentWidth <= layout.viewportWidth, "移动端导出任务中心出现页面级横向溢出");
  assert(layout.panelWidth <= layout.viewportWidth, "移动端导出任务面板超出视口");
  await page.screenshot({ path: path.join(outputDir, `${prefix}-mobile.png`), fullPage: true });
}

async function main() {
  fs.mkdirSync(outputDir, { recursive: true });
  const launchOptions = { headless: true, args: ["--disable-dev-shm-usage"] };
  const executablePath = findBrowser();
  if (executablePath) launchOptions.executablePath = executablePath;
  const browser = await chromium.launch(launchOptions);
  const context = await browser.newContext({ viewport: { width: 1366, height: 900 }, acceptDownloads: true });
  const page = await context.newPage();
  const consoleErrors = [];
  const failedResponses = [];
  page.on("console", (message) => { if (message.type() === "error") consoleErrors.push(message.text()); });
  page.on("response", (response) => {
    if (response.status() >= 400) failedResponses.push(`${response.status()} ${response.url()}`);
  });
  try {
    if (demo) {
      await page.goto(pageUrl("login"));
      await page.evaluate(() => localStorage.removeItem("localExplorerDemoExportJobs"));
    }
    await login(page);
    await completeAndDownload(page);
    await createAndCancel(page);
    await verifyFailureAndRetry(page);
    await verifyPollingStopsAfterNavigation(page);
    await verifyResponsiveTaskCenter(page);
    const relevantErrors = consoleErrors.filter((message) => !message.includes("Failed to load resource"));
    assert(relevantErrors.length === 0, `浏览器控制台报错：${relevantErrors.join(" | ")}`);
    assert(failedResponses.length === 0, `页面资源或接口请求失败：${failedResponses.join(" | ")}`);
    console.table([{ mode: demo ? "demo" : "real", checks: "create -> complete -> download -> cancel -> failure/retry -> polling cleanup -> mobile" }]);
  } finally {
    await context.close();
    await browser.close();
  }
}

main().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
