const { spawn } = require("node:child_process");
const fs = require("node:fs");
const os = require("node:os");
const path = require("node:path");

const root = path.resolve(__dirname, "..");
const logDir = path.join(root, ".superpowers", "interaction-smoke-logs");
const profileRoot = path.join(os.tmpdir(), "local-explorer-interaction-smoke-profiles");

const flows = [
  {
    name: "console-order-detail",
    route: "explorer-web/src/main/resources/static/console/orders.html?demo=1",
    run: async (page) => {
      await waitForText(page, "预约订单");
      await clickButtonByText(page, "详情");
      await waitForText(page, "订单详情");
    }
  },
  {
    name: "console-review-filter",
    route: "explorer-web/src/main/resources/static/console/reviews.html?demo=1",
    run: async (page) => {
      await waitForText(page, "用户评价");
      await setInputValue(page, "搜索评价", "不存在评价");
      await clickButtonByText(page, "搜索");
      await waitForText(page, "暂无匹配评价");
    }
  },
  {
    name: "client-order-detail",
    route: "explorer-web/src/main/resources/static/client/my-orders.html?demo=1",
    width: 430,
    height: 900,
    run: async (page) => {
      await waitForText(page, "我的预约");
      await waitUntil(
        () => evaluate(page, `(() => {
          const nav = document.querySelector(".client-nav-links");
          const active = nav?.querySelector('[aria-current="page"]');
          if (!nav || !active) return false;
          const navRect = nav.getBoundingClientRect();
          const activeRect = active.getBoundingClientRect();
          return activeRect.left >= navRect.left && activeRect.right <= navRect.right;
        })()`),
        "active client navigation item inside scroll viewport"
      );
      await clickButtonByText(page, "详情");
      await waitForText(page, "预约详情");
    }
  },
  {
    name: "client-order-cancel-confirm",
    route: "explorer-web/src/main/resources/static/client/my-orders.html?demo=1",
    width: 375,
    height: 812,
    run: async (page) => {
      await waitForText(page, "我的预约");
      await clickButtonByText(page, "取消预约");
      await waitForText(page, "确认取消");
      await waitUntil(
        () => evaluate(page, `(() => {
          const dialog = document.querySelector(".confirm-dialog");
          if (!dialog) return false;
          const rect = dialog.getBoundingClientRect();
          return document.documentElement.clientWidth === 375
            && rect.left >= 0 && rect.right <= document.documentElement.clientWidth
            && document.documentElement.scrollWidth <= document.documentElement.clientWidth;
        })()`),
        "375px confirm-dialog inside viewport"
      );
      await clickButtonByText(page, "确认取消");
      await waitForText(page, "预约已取消");
    }
  },
  {
    name: "client-project-image",
    route: "explorer-web/src/main/resources/static/client/index.html?demo=1",
    width: 430,
    height: 900,
    run: async (page) => {
      await waitForText(page, "手冲咖啡品鉴");
      await waitUntil(
        () => evaluate(page, `(() => {
          const image = document.querySelector(".spot-card img");
          return Boolean(image && image.complete && image.naturalWidth > 0 && image.currentSrc.includes(".webp"));
        })()`),
        "loaded project image"
      );
      await waitUntil(
        () => evaluate(page, `(() => {
          const button = document.querySelector(".discovery-refresh");
          if (!button) return false;
          const rect = button.getBoundingClientRect();
          const nav = document.querySelector(".client-nav-links");
          return document.documentElement.clientWidth === 430
            && rect.left >= 0 && rect.right <= document.documentElement.clientWidth
            && nav && nav.scrollWidth <= nav.clientWidth
            && document.documentElement.scrollWidth <= document.documentElement.clientWidth;
        })()`),
        "mobile discovery controls inside viewport"
      );
    }
  },
  {
    name: "client-package-detail",
    route: "explorer-web/src/main/resources/static/client/index.html?demo=1",
    run: async (page) => {
      await waitForText(page, "探索套餐");
      await clickCardButtonByText(page, "咖啡书店半日包", "查看详情");
      await waitForText(page, "套餐包含项目");
      await waitForText(page, "手冲咖啡品鉴");
      await waitForText(page, "独立书店夜读");
    }
  }
];

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

async function waitForDevToolsPort(profile, browser) {
  const portFile = path.join(profile, "DevToolsActivePort");
  const start = Date.now();
  while (Date.now() - start < 10000) {
    if (browser.exitCode !== null) {
      throw new Error(`Browser exited before DevTools was ready, exit code ${browser.exitCode}.`);
    }
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

    socket.addEventListener("open", () => {
      resolve({
        send(method, params = {}) {
          const id = ++nextId;
          const payload = JSON.stringify({ id, method, params });
          return new Promise((innerResolve, innerReject) => {
            pending.set(id, { resolve: innerResolve, reject: innerReject });
            socket.send(payload);
          });
        },
        close() {
          socket.close();
        }
      });
    });

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

async function openPage(browserPath, flow) {
  const profile = fs.mkdtempSync(path.join(profileRoot, `${flow.name}-`));
  const stderrLog = path.join(logDir, `${flow.name}.err.log`);
  fs.rmSync(stderrLog, { force: true });
  const stderr = fs.openSync(stderrLog, "w");
  const browser = spawn(browserPath, [
    "--headless=new",
    "--disable-gpu",
    "--disable-dev-shm-usage",
    "--allow-file-access-from-files",
    "--run-all-compositor-stages-before-draw",
    "--remote-debugging-port=0",
    `--window-size=${flow.width || 1280},${flow.height || 900}`,
    `--user-data-dir=${profile}`,
    "about:blank"
  ], {
    windowsHide: true,
    stdio: ["ignore", "ignore", stderr]
  });

  const port = await waitForDevToolsPort(profile, browser);
  const target = await findPageTarget(port);
  const client = await createCdpClient(target.webSocketDebuggerUrl);
  await client.send("Page.enable");
  await client.send("Runtime.enable");
  if (flow.width) {
    await client.send("Emulation.setDeviceMetricsOverride", {
      width: flow.width,
      height: flow.height || 900,
      deviceScaleFactor: 1,
      mobile: flow.width <= 760,
      screenWidth: flow.width,
      screenHeight: flow.height || 900
    });
  }
  const url = fileUrl(flow.route);
  await client.send("Page.navigate", { url });
  const expectedUrl = JSON.stringify(url);
  await waitUntil(
    async () => evaluate(client, `location.href === ${expectedUrl} && document.readyState === "complete" && Boolean(document.body)`),
    `page ready for ${flow.name}`
  );

  return {
    client,
    async close() {
      try {
        const html = await evaluate(client, "document.documentElement.outerHTML");
        fs.writeFileSync(path.join(logDir, `${flow.name}.html`), html || "");
      } finally {
        client.close();
        browser.kill();
        fs.closeSync(stderr);
        await sleep(200);
        await removeProfile(profile);
      }
    }
  };
}

async function evaluate(page, expression) {
  const result = await page.send("Runtime.evaluate", {
    expression,
    awaitPromise: true,
    returnByValue: true,
    userGesture: true
  });
  if (result.exceptionDetails) {
    throw new Error(result.exceptionDetails.text || "Runtime.evaluate failed");
  }
  return result.result?.value;
}

async function waitUntil(check, label, timeoutMs = 8000) {
  const start = Date.now();
  while (Date.now() - start < timeoutMs) {
    if (await check()) return;
    await sleep(100);
  }
  throw new Error(`Timed out waiting for ${label}.`);
}

async function waitForText(page, text) {
  const expected = JSON.stringify(text);
  await waitUntil(
    () => evaluate(page, `document.body.innerText.includes(${expected})`),
    `text "${text}"`
  );
}

async function clickButtonByText(page, text) {
  const expected = JSON.stringify(text);
  await waitUntil(
    () => evaluate(page, `(() => {
      const expected = ${expected};
      const normalize = (value) => String(value || "").replace(/\\s+/g, "");
      return Array.from(document.querySelectorAll("button, a, [role='button']"))
        .some((element) => normalize(element.innerText || element.textContent).includes(normalize(expected)));
    })()`),
    `button text "${text}"`
  );
  const clicked = await evaluate(page, `(() => {
    const expected = ${expected};
    const normalize = (value) => String(value || "").replace(/\\s+/g, "");
    const target = Array.from(document.querySelectorAll("button, a, [role='button']"))
      .find((element) => normalize(element.innerText || element.textContent).includes(normalize(expected)));
    if (!target) return false;
    target.click();
    return true;
  })()`);
  if (!clicked) throw new Error(`Button was not found: ${text}`);
}

async function clickCardButtonByText(page, cardText, buttonText) {
  const expectedCard = JSON.stringify(cardText);
  const expectedButton = JSON.stringify(buttonText);
  await waitUntil(
    () => evaluate(page, `(() => {
      const expectedCard = ${expectedCard};
      const expectedButton = ${expectedButton};
      const normalize = (value) => String(value || "").replace(/\\s+/g, "");
      return Array.from(document.querySelectorAll("article, .spot-card, .order-item"))
        .some((card) => normalize(card.innerText).includes(normalize(expectedCard))
          && Array.from(card.querySelectorAll("button, a, [role='button']"))
            .some((button) => normalize(button.innerText || button.textContent).includes(normalize(expectedButton))));
    })()`),
    `card "${cardText}" button "${buttonText}"`
  );
  const clicked = await evaluate(page, `(() => {
    const expectedCard = ${expectedCard};
    const expectedButton = ${expectedButton};
    const normalize = (value) => String(value || "").replace(/\\s+/g, "");
    const card = Array.from(document.querySelectorAll("article, .spot-card, .order-item"))
      .find((element) => normalize(element.innerText).includes(normalize(expectedCard)));
    if (!card) return false;
    const button = Array.from(card.querySelectorAll("button, a, [role='button']"))
      .find((element) => normalize(element.innerText || element.textContent).includes(normalize(expectedButton)));
    if (!button) return false;
    button.click();
    return true;
  })()`);
  if (!clicked) throw new Error(`Button "${buttonText}" was not found in card "${cardText}".`);
}

async function setInputValue(page, label, value) {
  const expectedLabel = JSON.stringify(label);
  const nextValue = JSON.stringify(value);
  const changed = await evaluate(page, `(() => {
    const expectedLabel = ${expectedLabel};
    const nextValue = ${nextValue};
    const input = Array.from(document.querySelectorAll("input, textarea"))
      .find((element) => element.getAttribute("aria-label") === expectedLabel || element.placeholder === expectedLabel);
    if (!input) return false;
    const setter = Object.getOwnPropertyDescriptor(Object.getPrototypeOf(input), "value").set;
    setter.call(input, nextValue);
    input.dispatchEvent(new Event("input", { bubbles: true }));
    input.dispatchEvent(new Event("change", { bubbles: true }));
    return true;
  })()`);
  if (!changed) throw new Error(`Input was not found: ${label}`);
}

async function runFlow(browserPath, flow) {
  const page = await openPage(browserPath, flow);
  try {
    await flow.run(page.client);
    return { flow: flow.name, checks: "passed" };
  } finally {
    await page.close();
  }
}

async function main() {
  fs.mkdirSync(logDir, { recursive: true });
  fs.mkdirSync(profileRoot, { recursive: true });

  const browserPath = findBrowser();
  if (!browserPath) {
    throw new Error("Browser was not found. Pass --browser=<path> or set BROWSER_PATH / EDGE_PATH / CHROME_PATH.");
  }

  const results = [];
  for (const flow of flows) {
    results.push(await runFlow(browserPath, flow));
  }
  console.table(results);
}

main().catch((error) => {
  console.error(error);
  process.exit(1);
});
