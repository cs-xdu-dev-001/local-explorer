const fs = require("node:fs");
const path = require("node:path");
const { createRequire } = require("node:module");

const root = path.resolve(__dirname, "..");
const frontendRequire = createRequire(path.join(root, "explorer-web", "frontend", "package.json"));
const { chromium } = frontendRequire("playwright");
const outputDir = path.join(root, "docs", "screenshots");
const baseUrl = (process.argv.find((arg) => arg.startsWith("--base="))?.slice("--base=".length)
  || process.env.FRONTEND_BASE_URL
  || "http://localhost:8080").replace(/\/$/, "");
const credentials = {
  adminUsername: process.env.ADMIN_USERNAME || "admin",
  adminPassword: process.env.ADMIN_PASSWORD || "123456",
  userPhone: process.env.USER_PHONE || "13800001111",
  userPassword: process.env.USER_PASSWORD || "123456"
};

function assert(condition, message) {
  if (!condition) throw new Error(message);
}

function findBrowser() {
  const explicit = process.env.BROWSER_PATH || process.env.EDGE_PATH || process.env.CHROME_PATH;
  const candidates = [
    explicit,
    "C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe",
    "C:\\Program Files (x86)\\Google\\Chrome\\Application\\chrome.exe",
    path.join(process.env.LOCALAPPDATA || "", "Google", "Chrome", "Application", "chrome.exe"),
    "C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe",
    "C:\\Program Files\\Microsoft\\Edge\\Application\\msedge.exe",
    "/usr/bin/google-chrome",
    "/usr/bin/chromium",
    "/usr/bin/chromium-browser"
  ].filter(Boolean);
  return candidates.find((candidate) => fs.existsSync(candidate));
}

async function newPage(browser, viewport) {
  const context = await browser.newContext({ viewport });
  const page = await context.newPage();
  return { context, page };
}

async function assertRecoveredAccess(page, storageKey, exitName) {
  await page.evaluate(() => sessionStorage.clear());
  await page.reload();
  await page.getByRole("button", { name: exitName }).waitFor();
  const restored = await page.evaluate((key) => sessionStorage.getItem(key), storageKey);
  assert(restored, `${storageKey} was not restored from the HttpOnly refresh session`);

  await page.evaluate(({ key }) => sessionStorage.setItem(key, "expired-access-token"), { key: storageKey });
  await page.reload();
  await page.getByRole("button", { name: exitName }).waitFor();
  await page.waitForFunction(({ key }) => {
    const token = sessionStorage.getItem(key);
    return token && token !== "expired-access-token";
  }, { key: storageKey });
}

async function waitForProductData(page, loadingText) {
  await page.getByText(loadingText, { exact: true }).waitFor({ state: "hidden", timeout: 20_000 });
  await page.waitForLoadState("networkidle");
}

async function adminFlow(browser) {
  const { context, page } = await newPage(browser, { width: 1366, height: 900 });
  try {
    await page.goto(`${baseUrl}/console/login.html`);
    await page.getByLabel("账号").fill(credentials.adminUsername);
    await page.getByLabel("密码").fill(credentials.adminPassword);
    await page.getByRole("button", { name: "登录后台" }).click();
    await page.waitForURL(/\/console\/index\.html/);
    await page.getByRole("button", { name: "退出" }).waitFor();

    await assertRecoveredAccess(page, "localExplorerAdminAccess", "退出");
    await waitForProductData(page, "加载运营数据中");
    await page.screenshot({ path: path.join(outputDir, "auth-admin-desktop.png"), fullPage: false });

    await page.getByRole("button", { name: "退出" }).click();
    await page.waitForURL(/\/console\/login\.html/);
    await page.goto(`${baseUrl}/console/index.html`);
    await page.getByRole("heading", { name: "请先登录" }).waitFor();
    assert(await page.evaluate(() => sessionStorage.getItem("localExplorerAdminAccess")) === null,
      "admin Access Token remained after logout");
  } finally {
    await context.close();
  }
}

async function userFlow(browser) {
  const { context, page } = await newPage(browser, { width: 375, height: 812 });
  try {
    await page.goto(`${baseUrl}/client/login.html`);
    await page.getByLabel("手机号").fill(credentials.userPhone);
    await page.getByLabel("密码").fill(credentials.userPassword);
    await page.getByRole("button", { name: "登录", exact: true }).click();
    await page.waitForURL(/\/client\/index\.html/);
    await page.getByRole("button", { name: "退出登录" }).waitFor();

    await assertRecoveredAccess(page, "localExplorerUserAccess", "退出登录");
    await waitForProductData(page, "加载本地生活内容中");
    const layout = await page.evaluate(() => ({
      viewportWidth: document.documentElement.clientWidth,
      documentWidth: document.documentElement.scrollWidth
    }));
    assert(layout.documentWidth <= layout.viewportWidth, "mobile user page overflows horizontally");
    await page.screenshot({ path: path.join(outputDir, "auth-user-mobile.png"), fullPage: false });

    await page.getByRole("button", { name: "退出登录" }).click();
    await page.waitForURL(/\/client\/login\.html/);
    await page.goto(`${baseUrl}/client/index.html`);
    await page.waitForLoadState("networkidle");
    assert(await page.getByRole("button", { name: "退出登录" }).count() === 0,
      "user session recovered after logout");
    assert(await page.evaluate(() => sessionStorage.getItem("localExplorerUserAccess")) === null,
      "user Access Token remained after logout");
  } finally {
    await context.close();
  }
}

async function main() {
  fs.mkdirSync(outputDir, { recursive: true });
  const launchOptions = {
    headless: true,
    args: ["--disable-dev-shm-usage"]
  };
  const executablePath = findBrowser();
  if (executablePath) launchOptions.executablePath = executablePath;
  const browser = await chromium.launch(launchOptions);
  try {
    await adminFlow(browser);
    await userFlow(browser);
    console.table([
      { flow: "admin", checks: "login -> reload refresh -> expired Access recovery -> logout" },
      { flow: "user", checks: "login -> reload refresh -> expired Access recovery -> logout + mobile" }
    ]);
  } finally {
    await browser.close();
  }
}

main().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
