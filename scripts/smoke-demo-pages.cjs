const { spawnSync } = require("node:child_process");
const fs = require("node:fs");
const os = require("node:os");
const path = require("node:path");

const root = path.resolve(__dirname, "..");
const logDir = path.join(root, ".superpowers", "ui-smoke-logs");
const profileRoot = path.join(os.tmpdir(), "local-explorer-ui-smoke-profiles");

const pages = [
  {
    name: "console-login",
    route: "explorer-web/src/main/resources/static/console/login.html?demo=1",
    expected: ["Local Explorer Console", "管理员登录", "进入运营后台"]
  },
  {
    name: "console-dashboard",
    route: "explorer-web/src/main/resources/static/console/index.html?demo=1",
    expected: ["Local Explorer", "运营概览", "最近预约"]
  },
  {
    name: "console-orders",
    route: "explorer-web/src/main/resources/static/console/orders.html?demo=1",
    expected: ["预约订单", "履约管理", "确认预约"]
  },
  {
    name: "console-reviews",
    route: "explorer-web/src/main/resources/static/console/reviews.html?demo=1",
    expected: ["用户评价", "项目、用户、评价内容", "未回复"]
  },
  {
    name: "client-home",
    route: "explorer-web/src/main/resources/static/client/index.html?demo=1",
    expected: ["Local Explorer", "特色项目", "预约"]
  },
  {
    name: "client-orders",
    route: "explorer-web/src/main/resources/static/client/my-orders.html?demo=1",
    expected: ["我的预约", "详情", "取消预约"]
  }
];

function findBrowser() {
  const explicit = process.argv.find((arg) => arg.startsWith("--browser="))?.slice("--browser=".length)
    || process.argv.find((arg) => arg.startsWith("--edge="))?.slice("--edge=".length)
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
    path.join(process.env.LOCALAPPDATA || "", "Microsoft\\Edge\\Application\\msedge.exe")
  ].filter(Boolean);
  return candidates.find((candidate) => fs.existsSync(candidate));
}

function fileUrl(relativePath) {
  const absolute = path.join(root, relativePath);
  return `file:///${absolute.replace(/\\/g, "/")}`;
}

function renderDom(browserPath, page) {
  const profile = fs.mkdtempSync(path.join(profileRoot, `${page.name}-`));
  const stdoutLog = path.join(logDir, `${page.name}.html`);
  const stderrLog = path.join(logDir, `${page.name}.err.log`);

  fs.rmSync(stdoutLog, { force: true });
  fs.rmSync(stderrLog, { force: true });

  const result = spawnSync(browserPath, [
    "--headless=new",
    "--disable-gpu",
    "--disable-dev-shm-usage",
    "--allow-file-access-from-files",
    "--run-all-compositor-stages-before-draw",
    "--virtual-time-budget=3000",
    `--user-data-dir=${profile}`,
    "--dump-dom",
    fileUrl(page.route)
  ], {
    encoding: "utf8",
    timeout: 30000,
    windowsHide: true
  });

  fs.writeFileSync(stdoutLog, result.stdout || "");
  fs.writeFileSync(stderrLog, result.stderr || "");
  fs.rmSync(profile, { recursive: true, force: true });

  if (result.error) throw result.error;
  if (result.status !== 0) {
    throw new Error(`${page.name} render failed with exit code ${result.status}. See ${stderrLog}`);
  }
  if (!result.stdout || result.stdout.length < 500) {
    throw new Error(`${page.name} rendered too little DOM. See ${stdoutLog}`);
  }
  const missing = page.expected.filter((text) => !result.stdout.includes(text));
  if (missing.length) {
    throw new Error(`${page.name} missed expected text: ${missing.join(", ")}. See ${stdoutLog}`);
  }
  return { page: page.name, checks: page.expected.length };
}

function main() {
  fs.mkdirSync(logDir, { recursive: true });
  fs.mkdirSync(profileRoot, { recursive: true });

  const browserPath = findBrowser();
  if (!browserPath) {
    throw new Error("Browser was not found. Pass --browser=<path> or set BROWSER_PATH / EDGE_PATH / CHROME_PATH.");
  }

  const results = pages.map((page) => renderDom(browserPath, page));
  console.table(results);
}

try {
  main();
} catch (error) {
  console.error(error);
  process.exit(1);
}
