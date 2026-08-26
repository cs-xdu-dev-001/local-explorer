const DEFAULT_BASE_URL = "http://localhost:8080";

const config = {
  baseUrl: process.argv.find((arg) => arg.startsWith("--base="))?.slice("--base=".length)
    || process.env.BACKEND_BASE_URL
    || DEFAULT_BASE_URL,
  adminUsername: process.env.ADMIN_USERNAME || "admin",
  adminPassword: process.env.ADMIN_PASSWORD || "123456"
};

const MERCHANT_FIELDS = ["name", "slogan", "phone", "address", "businessHours", "notice", "coverImage"];

async function request(path, options = {}) {
  const headers = { ...(options.headers || {}) };
  if (options.body !== undefined) headers["Content-Type"] = "application/json";
  if (options.adminToken) headers.token = options.adminToken;

  let response;
  try {
    response = await fetch(`${config.baseUrl.replace(/\/$/, "")}${path}`, {
      method: options.method || "GET",
      headers,
      body: options.body === undefined ? undefined : JSON.stringify(options.body)
    });
  } catch (error) {
    throw new Error(`Backend is unreachable: ${config.baseUrl}. Start LocalExplorerApplication in IDEA and retry. Original error: ${error.message}`);
  }

  const payload = await response.json().catch(() => ({}));
  if (!response.ok || payload.code === 0) {
    throw new Error(`${options.method || "GET"} ${path} failed: HTTP ${response.status}, msg=${payload.msg || "empty response"}`);
  }
  return payload.data;
}

function recordsOf(pageLike) {
  if (Array.isArray(pageLike)) return pageLike;
  return pageLike?.records || [];
}

function assertMerchant(actual, expected, context) {
  for (const field of MERCHANT_FIELDS) {
    if ((actual?.[field] || "") !== (expected?.[field] || "")) {
      throw new Error(`${context}: merchant field ${field} did not match.`);
    }
  }
}

async function readAuditLogs(adminToken) {
  return recordsOf(await request("/admin/operation-log/page?page=1&pageSize=100", { adminToken }));
}

async function waitForAuditLogs(adminToken, afterId, expectedPaths) {
  const deadline = Date.now() + 5000;
  let recent = [];

  while (Date.now() < deadline) {
    recent = (await readAuditLogs(adminToken)).filter((row) => Number(row.id) > Number(afterId));
    if (expectedPaths.every((path) => recent.some((row) => row.requestUri === path))) {
      return recent;
    }
    await new Promise((resolve) => setTimeout(resolve, 100));
  }

  throw new Error(`Operation logs did not contain ${expectedPaths.join(", ")}. Recent paths: ${recent.map((row) => row.requestUri).join(", ") || "none"}`);
}

async function restoreOriginalSettings(adminToken, merchant, shopStatus) {
  await request("/admin/merchant/info", {
    method: "PUT",
    adminToken,
    body: merchant
  });
  await request(`/admin/shop/${shopStatus}`, { method: "PUT", adminToken });

  const restoredMerchant = await request("/user/merchant/info");
  const restoredStatus = Number(await request("/user/shop/status"));
  assertMerchant(restoredMerchant, merchant, "restore verification");
  if (restoredStatus !== Number(shopStatus)) {
    throw new Error(`restore verification: expected shop status ${shopStatus}, got ${restoredStatus}.`);
  }
}

async function main() {
  let adminToken;
  let originalMerchant;
  let originalShopStatus;

  try {
    const admin = await request("/admin/employee/login", {
      method: "POST",
      body: { username: config.adminUsername, password: config.adminPassword }
    });
    adminToken = admin?.token;
    if (!adminToken) throw new Error("Admin login did not return token.");

    originalMerchant = await request("/admin/merchant/info", { adminToken });
    originalShopStatus = Number(await request("/admin/shop/status", { adminToken }));
    const baselineLogs = await readAuditLogs(adminToken);
    const baselineId = baselineLogs.reduce((max, row) => Math.max(max, Number(row.id) || 0), 0);

    const stamp = Date.now();
    const changedMerchant = {
      ...originalMerchant,
      slogan: `Runtime settings verified ${stamp}`,
      notice: `runtime-settings-smoke-${stamp}`
    };
    const changedStatus = originalShopStatus === 1 ? 0 : 1;

    await request("/admin/merchant/info", {
      method: "PUT",
      adminToken,
      body: changedMerchant
    });
    await request(`/admin/shop/${changedStatus}`, { method: "PUT", adminToken });

    const adminMerchant = await request("/admin/merchant/info", { adminToken });
    const userMerchant = await request("/user/merchant/info");
    const adminStatus = Number(await request("/admin/shop/status", { adminToken }));
    const userStatus = Number(await request("/user/shop/status"));
    assertMerchant(adminMerchant, changedMerchant, "admin read-back");
    assertMerchant(userMerchant, changedMerchant, "user read-back");
    if (adminStatus !== changedStatus || userStatus !== changedStatus) {
      throw new Error(`Shop status did not synchronize: admin=${adminStatus}, user=${userStatus}, expected=${changedStatus}.`);
    }

    const auditLogs = await waitForAuditLogs(adminToken, baselineId, [
      "/admin/merchant/info",
      `/admin/shop/${changedStatus}`
    ]);

    console.table([
      { step: "merchant admin/user read-back", result: changedMerchant.notice },
      { step: "shop status admin/user read-back", result: changedStatus },
      { step: "new operation logs", result: auditLogs.length }
    ]);
  } finally {
    if (adminToken && originalMerchant && Number.isInteger(originalShopStatus)) {
      await restoreOriginalSettings(adminToken, originalMerchant, originalShopStatus);
    }
  }
}

main().catch((error) => {
  console.error(error.message);
  process.exit(1);
});
