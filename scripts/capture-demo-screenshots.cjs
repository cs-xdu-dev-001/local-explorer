const { spawn } = require("node:child_process");
const fs = require("node:fs");
const os = require("node:os");
const path = require("node:path");

const root = path.resolve(__dirname, "..");
const outputDir = path.join(root, "docs", "screenshots");
const logDir = path.join(root, ".superpowers", "screenshot-logs");
const profileRoot = path.join(os.tmpdir(), "local-explorer-screenshot-profiles");

const shots = [
  {
    name: "console-login.png",
    route: "explorer-web/src/main/resources/static/console/login.html?demo=1",
    width: 1440,
    height: 900
  },
  {
    name: "console-dashboard.png",
    route: "explorer-web/src/main/resources/static/console/index.html?demo=1",
    width: 1440,
    height: 1200
  },
  {
    name: "console-items.png",
    route: "explorer-web/src/main/resources/static/console/items.html?demo=1",
    width: 1440,
    height: 1200
  },
  {
    name: "console-packages.png",
    route: "explorer-web/src/main/resources/static/console/packages.html?demo=1",
    width: 1440,
    height: 1200
  },
  {
    name: "client-home.png",
    route: "explorer-web/src/main/resources/static/client/index.html?demo=1",
    width: 430,
    height: 1200
  },
  {
    name: "client-login.png",
    route: "explorer-web/src/main/resources/static/client/login.html?demo=1",
    width: 430,
    height: 900
  },
  {
    name: "client-orders.png",
    route: "explorer-web/src/main/resources/static/client/my-orders.html?demo=1",
    width: 430,
    height: 1200
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

function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

async function removeProfile(profile) {
  for (let attempt = 0; attempt < 5; attempt += 1) {
    try {
      fs.rmSync(profile, { recursive: true, force: true });
      return;
    } catch (error) {
      if (!["EBUSY", "EPERM", "ENOTEMPTY"].includes(error.code) || attempt === 4) return;
      await sleep(200 * (attempt + 1));
    }
  }
}

async function waitForDevToolsPort(profile, browser) {
  const portFile = path.join(profile, "DevToolsActivePort");
  const start = Date.now();
  while (Date.now() - start < 10000) {
    if (browser.exitCode !== null) throw new Error(`Browser exited before DevTools was ready: ${browser.exitCode}`);
    if (fs.existsSync(portFile)) {
      const [port] = fs.readFileSync(portFile, "utf8").trim().split(/\r?\n/);
      if (port) return port;
    }
    await sleep(100);
  }
  throw new Error("Timed out waiting for DevToolsActivePort.");
}

async function fetchJson(url) {
  const response = await fetch(url);
  if (!response.ok) throw new Error(`${url} returned ${response.status}`);
  return response.json();
}

async function findPageTarget(port) {
  const start = Date.now();
  while (Date.now() - start < 10000) {
    const targets = await fetchJson(`http://127.0.0.1:${port}/json/list`);
    const target = targets.find((entry) => entry.type === "page" && entry.webSocketDebuggerUrl);
    if (target) return target;
    await sleep(100);
  }
  throw new Error("Timed out waiting for page target.");
}

function createCdpClient(wsUrl) {
  return new Promise((resolve, reject) => {
    const socket = new WebSocket(wsUrl);
    const pending = new Map();
    let nextId = 0;
    socket.addEventListener("open", () => resolve({
      send(method, params = {}) {
        const id = ++nextId;
        return new Promise((innerResolve, innerReject) => {
          pending.set(id, { resolve: innerResolve, reject: innerReject });
          socket.send(JSON.stringify({ id, method, params }));
        });
      },
      close() { socket.close(); }
    }));
    socket.addEventListener("message", (event) => {
      const message = JSON.parse(String(event.data));
      if (!message.id || !pending.has(message.id)) return;
      const callbacks = pending.get(message.id);
      pending.delete(message.id);
      if (message.error) callbacks.reject(new Error(message.error.message));
      else callbacks.resolve(message.result);
    });
    socket.addEventListener("error", () => reject(new Error(`Cannot connect to ${wsUrl}`)));
  });
}

async function evaluate(client, expression) {
  const result = await client.send("Runtime.evaluate", { expression, awaitPromise: true, returnByValue: true });
  if (result.exceptionDetails) throw new Error(result.exceptionDetails.text || "Runtime.evaluate failed");
  return result.result?.value;
}

async function waitUntil(check, label, timeoutMs = 10000) {
  const start = Date.now();
  while (Date.now() - start < timeoutMs) {
    if (await check()) return;
    await sleep(100);
  }
  throw new Error(`Timed out waiting for ${label}.`);
}

async function capture(browserPath, shot) {
  const target = path.join(outputDir, shot.name);
  const profile = fs.mkdtempSync(path.join(profileRoot, `${shot.name.replace(/\W+/g, "-")}-`));
  const stdoutLog = path.join(logDir, `${shot.name}.out.log`);
  const stderrLog = path.join(logDir, `${shot.name}.err.log`);

  fs.rmSync(target, { force: true });
  fs.rmSync(stdoutLog, { force: true });
  fs.rmSync(stderrLog, { force: true });

  fs.writeFileSync(stdoutLog, "");
  const stderr = fs.openSync(stderrLog, "w");
  const browser = spawn(browserPath, [
    "--headless=new",
    "--disable-gpu",
    "--disable-dev-shm-usage",
    "--hide-scrollbars",
    "--allow-file-access-from-files",
    "--run-all-compositor-stages-before-draw",
    "--remote-debugging-port=0",
    `--user-data-dir=${profile}`,
    "about:blank"
  ], {
    windowsHide: true,
    stdio: ["ignore", "ignore", stderr]
  });

  let client;
  try {
    const port = await waitForDevToolsPort(profile, browser);
    const pageTarget = await findPageTarget(port);
    client = await createCdpClient(pageTarget.webSocketDebuggerUrl);
    await client.send("Page.enable");
    await client.send("Runtime.enable");
    await client.send("Emulation.setDeviceMetricsOverride", {
      width: shot.width,
      height: shot.height,
      deviceScaleFactor: 1,
      mobile: shot.width <= 760,
      screenWidth: shot.width,
      screenHeight: shot.height
    });
    const url = fileUrl(shot.route);
    await client.send("Page.navigate", { url });
    const expectedUrl = JSON.stringify(url);
    await waitUntil(
      () => evaluate(client, `location.href === ${expectedUrl} && document.readyState === "complete" && Boolean(document.body)`),
      `${shot.name} page ready`
    );
    await waitUntil(
      () => evaluate(client, `(() => {
        const visibleImages = Array.from(document.images).filter((image) => {
          const rect = image.getBoundingClientRect();
          return rect.bottom > 0 && rect.top < innerHeight && rect.right > 0 && rect.left < innerWidth;
        });
        return !document.querySelector(".loading-state")
          && (!document.fonts || document.fonts.status === "loaded")
          && visibleImages.every((image) => image.complete && image.naturalWidth > 0);
      })()`),
      `${shot.name} visual assets`
    );
    const screenshot = await client.send("Page.captureScreenshot", {
      format: "png",
      fromSurface: true,
      captureBeyondViewport: false
    });
    fs.writeFileSync(target, Buffer.from(screenshot.data, "base64"));
  } finally {
    client?.close();
    browser.kill();
    fs.closeSync(stderr);
    await sleep(200);
    await removeProfile(profile);
  }
  if (!fs.existsSync(target)) {
    throw new Error(`${shot.name} was not created. See ${stderrLog}`);
  }

  const size = fs.statSync(target).size;
  if (size < 10000) {
    throw new Error(`${shot.name} looks too small: ${size} bytes`);
  }

  const png = fs.readFileSync(target);
  const width = png.readUInt32BE(16);
  const height = png.readUInt32BE(20);
  if (width !== shot.width || height !== shot.height) {
    throw new Error(`${shot.name} has ${width}x${height}, expected ${shot.width}x${shot.height}`);
  }

  return { name: shot.name, width, height, bytes: size };
}

async function main() {
  fs.mkdirSync(outputDir, { recursive: true });
  fs.mkdirSync(logDir, { recursive: true });
  fs.mkdirSync(profileRoot, { recursive: true });

  const browserPath = findBrowser();
  if (!browserPath) {
    throw new Error("Browser was not found. Pass --browser=<path> or set BROWSER_PATH / EDGE_PATH / CHROME_PATH.");
  }

  const results = [];
  for (const shot of shots) results.push(await capture(browserPath, shot));
  if (results.length !== shots.length) {
    throw new Error(`Expected ${shots.length} screenshots, captured ${results.length}.`);
  }
  console.table(results);
}

main().catch((error) => {
  console.error(error);
  process.exit(1);
});
