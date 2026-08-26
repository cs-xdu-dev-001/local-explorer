const fs = require("node:fs");
const path = require("node:path");
const { createRequire } = require("node:module");

const root = path.resolve(__dirname, "..");
const frontendRequire = createRequire(path.join(root, "explorer-web", "frontend", "package.json"));
const { chromium } = frontendRequire("playwright");
const outputDir = path.join(root, "docs", "screenshots");

function fileUrl(relativePath) {
  const absolute = path.join(root, relativePath);
  return `file:///${absolute.replace(/\\/g, "/")}`;
}

function findBrowser() {
  const explicit = process.argv.find((arg) => arg.startsWith("--browser="))?.slice("--browser=".length)
    || process.env.BROWSER_PATH
    || process.env.EDGE_PATH
    || process.env.CHROME_PATH;
  const candidates = [
    explicit,
    "C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe",
    "C:\\Program Files (x86)\\Google\\Chrome\\Application\\chrome.exe",
    path.join(process.env.LOCALAPPDATA || "", "Google\\Chrome\\Application\\chrome.exe"),
    "C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe",
    "C:\\Program Files\\Microsoft\\Edge\\Application\\msedge.exe",
    "/usr/bin/google-chrome",
    "/usr/bin/chromium",
    "/usr/bin/chromium-browser"
  ].filter(Boolean);
  return candidates.find((candidate) => fs.existsSync(candidate));
}

function assert(condition, message) {
  if (!condition) throw new Error(message);
}

async function openDemoPage(browser, viewport) {
  const context = await browser.newContext({ viewport });
  const page = await context.newPage();
  const consoleErrors = [];
  page.on("console", (message) => {
    if (message.type() === "error") consoleErrors.push(message.text());
  });
  await page.goto(fileUrl("explorer-web/src/main/resources/static/client/my-orders.html?demo=1"));
  await page.getByRole("heading", { name: "我的预约", exact: true }).waitFor();
  return { context, page, consoleErrors };
}

async function desktopFlow(browser) {
  const { context, page, consoleErrors } = await openDemoPage(browser, { width: 1280, height: 900 });
  try {
    const trigger = page.getByRole("button", { name: "通知中心" });
    await trigger.waitFor();
    assert(await trigger.locator(".notification-badge").textContent() === "2", "桌面端未读角标应为2");
    await trigger.click();

    const drawer = page.getByRole("dialog", { name: "通知中心" });
    await drawer.waitFor();
    await drawer.getByText("预约已确认", { exact: true }).waitFor();
    await page.screenshot({ path: path.join(outputDir, "notification-desktop.png"), fullPage: false });

    await drawer.getByText("预约已确认", { exact: true }).click();
    const orderDetail = page.getByRole("dialog", { name: "预约详情" });
    await orderDetail.waitFor();
    assert((await orderDetail.textContent()).includes("咖啡书店半日包"), "通知没有打开对应预约详情");
    assert(consoleErrors.length === 0, `桌面端控制台报错：${consoleErrors.join(" | ")}`);
  } finally {
    await context.close();
  }
}

async function mobileFlow(browser) {
  const { context, page, consoleErrors } = await openDemoPage(browser, { width: 375, height: 812 });
  try {
    await page.getByRole("button", { name: "通知中心" }).click();
    const drawer = page.getByRole("dialog", { name: "通知中心" });
    await drawer.waitFor();
    await drawer.getByRole("button", { name: "全部已读" }).click();
    await page.waitForFunction(() => document.querySelectorAll(".notification-item.unread").length === 0);

    const layout = await page.evaluate(() => {
      const panel = document.querySelector(".notification-drawer");
      const rect = panel?.getBoundingClientRect();
      return {
        documentWidth: document.documentElement.scrollWidth,
        viewportWidth: document.documentElement.clientWidth,
        panelLeft: rect?.left,
        panelRight: rect?.right
      };
    });
    assert(layout.documentWidth <= layout.viewportWidth, "移动端通知页出现横向溢出");
    assert(layout.panelLeft >= 0 && layout.panelRight <= layout.viewportWidth, "移动端通知抽屉超出视口");
    await page.screenshot({ path: path.join(outputDir, "notification-mobile.png"), fullPage: false });
    assert(consoleErrors.length === 0, `移动端控制台报错：${consoleErrors.join(" | ")}`);
  } finally {
    await context.close();
  }
}

async function main() {
  fs.mkdirSync(outputDir, { recursive: true });
  const executablePath = findBrowser();
  const browser = await chromium.launch({
    headless: true,
    executablePath,
    args: ["--allow-file-access-from-files", "--disable-dev-shm-usage"]
  });
  try {
    await desktopFlow(browser);
    await mobileFlow(browser);
    console.table([
      { flow: "notification-desktop", checks: "unread -> read -> order detail" },
      { flow: "notification-mobile", checks: "read all + no overflow" }
    ]);
  } finally {
    await browser.close();
  }
}

main().catch((error) => {
  console.error(error);
  process.exit(1);
});
