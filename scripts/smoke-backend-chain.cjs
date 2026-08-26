const DEFAULT_BASE_URL = "http://localhost:8080";

const config = {
  baseUrl: process.argv.find((arg) => arg.startsWith("--base="))?.slice("--base=".length)
    || process.env.BACKEND_BASE_URL
    || DEFAULT_BASE_URL,
  adminUsername: process.env.ADMIN_USERNAME || "admin",
  adminPassword: process.env.ADMIN_PASSWORD || "123456",
  userPhone: process.env.USER_PHONE || "13800001111",
  userPassword: process.env.USER_PASSWORD || "123456"
};

const PUBLIC_ENDPOINT_HINT = "If this is a public list/settings endpoint and backend logs mention RedisConnectionFailureException, restart the IDEA backend from the current code. If it still fails, start Redis on localhost:6379.";

function tomorrowMorning() {
  const date = new Date(Date.now() + 24 * 60 * 60 * 1000);
  date.setHours(10, 30, 0, 0);
  const pad = (value) => String(value).padStart(2, "0");
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`;
}

async function request(path, options = {}) {
  const headers = { ...(options.headers || {}) };
  if (options.body !== undefined) headers["Content-Type"] = "application/json";
  if (options.adminToken) headers.token = options.adminToken;
  if (options.userToken) headers.authentication = options.userToken;

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
    throw new Error(formatApiFailure(path, options, response, payload));
  }
  return payload.data;
}

function formatApiFailure(path, options, response, payload) {
  const method = options.method || "GET";
  const code = Object.prototype.hasOwnProperty.call(payload, "code") ? payload.code : "missing";
  const message = payload.msg || "empty response";
  const parts = [
    `${method} ${path} failed: HTTP ${response.status}, API returned code=${code}, msg=${message}.`
  ];
  if (method === "GET" && path.startsWith("/user/")) {
    parts.push(PUBLIC_ENDPOINT_HINT);
  }
  return parts.join(" ");
}

function recordsOf(pageLike) {
  if (Array.isArray(pageLike)) return pageLike;
  return pageLike?.records || [];
}

function pickAvailableItem(items) {
  return items.find((item) => {
    const capacity = Number(item.capacity ?? 999999);
    const booked = Number(item.booked ?? 0);
    return Number(item.status) === 1 && booked + 1 <= capacity;
  }) || items[0];
}

async function verifyAdminOrderFilters(adminToken, orderId, orderKeyword) {
  const page = await request(`/admin/explore-order/page?page=1&pageSize=20&keyword=${encodeURIComponent(orderKeyword)}&status=1`, { adminToken });
  const rows = recordsOf(page);
  const row = rows.find((order) => Number(order.id) === Number(orderId));
  if (!row) throw new Error("后台订单筛选未返回刚确认的预约。");
  if (Number(row.status) !== 1) {
    throw new Error(`后台订单筛选应返回已确认状态，实际为 ${row.status}。`);
  }
}

async function ensureShopOpen(adminToken) {
  const originalStatus = Number(await request("/admin/shop/status", { adminToken }));
  if (originalStatus !== 1) {
    await request("/admin/shop/1", {
      method: "PUT",
      adminToken
    });
  }
  return originalStatus;
}

async function restoreShopStatus(adminToken, originalStatus) {
  if (Number(originalStatus) !== 1) {
    await request(`/admin/shop/${Number(originalStatus) === 1 ? 1 : 0}`, {
      method: "PUT",
      adminToken
    });
  }
}

async function main() {
  const admin = await request("/admin/employee/login", {
    method: "POST",
    body: { username: config.adminUsername, password: config.adminPassword }
  });
  if (!admin?.token) throw new Error("管理员登录未返回 token。");

  const originalShopStatus = await ensureShopOpen(admin.token);
  try {
    const user = await request("/user/user/login", {
      method: "POST",
      body: { phone: config.userPhone, password: config.userPassword }
    });
    if (!user?.token) throw new Error("用户登录未返回 token。");

    const categories = recordsOf(await request("/user/category/list?type=1"));
    const category = categories[0];
    const categoryQuery = category?.id ? `?categoryId=${category.id}` : "";
    const items = recordsOf(await request(`/user/explore-item/list${categoryQuery}`));
    if (!items.length) throw new Error("用户端项目列表为空，无法创建预约。请先确认初始化 SQL 已导入。");
    const item = pickAvailableItem(items);
    if (!item?.id) throw new Error("项目列表没有可用 id。");

    const orderKeyword = "Smoke自检";
    const orderId = await request("/user/explore-order", {
      method: "POST",
      userToken: user.token,
      body: {
        orderType: 1,
        itemId: item.id,
        peopleCount: 1,
        contactName: orderKeyword,
        contactPhone: config.userPhone,
        reserveTime: tomorrowMorning(),
        remark: "backend-chain-smoke"
      }
    });
    if (!orderId) throw new Error("创建预约未返回 orderId。");

    await request(`/admin/explore-order/status?id=${orderId}&status=1`, {
      method: "PUT",
      adminToken: admin.token
    });

    const confirmed = await request(`/user/explore-order/${orderId}`, {
      userToken: user.token
    });
    if (Number(confirmed.status) !== 1) {
      throw new Error(`后台确认后，用户端订单状态应为 1，实际为 ${confirmed.status}。`);
    }

    await verifyAdminOrderFilters(admin.token, orderId, orderKeyword);

    await request(`/user/explore-order/${orderId}/cancel`, {
      method: "PUT",
      userToken: user.token
    });

    const canceled = await request(`/user/explore-order/${orderId}`, {
      userToken: user.token
    });
    if (Number(canceled.status) !== 3) {
      throw new Error(`用户取消后，订单状态应为 3，实际为 ${canceled.status}。`);
    }

    console.table([
      { step: "admin login", result: admin.userName || admin.name || config.adminUsername },
      { step: "shop status", result: originalShopStatus === 1 ? "already open" : "opened for smoke" },
      { step: "user login", result: user.name || user.userName || config.userPhone },
      { step: "picked category", result: category?.name || "all" },
      { step: "picked item", result: `${item.id} ${item.name}` },
      { step: "created order", result: orderId },
      { step: "admin confirmed", result: confirmed.status },
      { step: "admin order filter", result: orderKeyword },
      { step: "user canceled", result: canceled.status }
    ]);
  } finally {
    await restoreShopStatus(admin.token, originalShopStatus);
  }
}

main().catch((error) => {
  console.error(error.message);
  process.exit(1);
});
