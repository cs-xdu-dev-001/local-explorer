const DEFAULT_BASE_URL = "http://localhost:8080";
const POLL_INTERVAL_MS = 1000;
const TIMEOUT_MS = Number(process.env.RELIABILITY_SMOKE_TIMEOUT_MS || 90000);
const STARTUP_HINT = "Start the dev backend with ORDER_PENDING_TIMEOUT_MINUTES=1, ORDER_EXPIRATION_DELAY_MS=1000 and OUTBOX_DELAY_MS=500.";

const config = {
  baseUrl: process.argv.find((arg) => arg.startsWith("--base="))?.slice("--base=".length)
    || process.env.BACKEND_BASE_URL
    || DEFAULT_BASE_URL,
  adminUsername: process.env.ADMIN_USERNAME || "admin",
  adminPassword: process.env.ADMIN_PASSWORD || "123456",
  userPhone: process.env.USER_PHONE || "13800001111",
  userPassword: process.env.USER_PASSWORD || "123456"
};

function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

function recordsOf(value) {
  if (Array.isArray(value)) return value;
  return value?.records || [];
}

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
    throw new Error(`Backend is unreachable at ${config.baseUrl}. ${STARTUP_HINT} ${error.message}`);
  }

  const payload = await response.json().catch(() => ({}));
  if (!response.ok || payload.code === 0) {
    throw new Error(`${options.method || "GET"} ${path} failed: HTTP ${response.status}, code=${payload.code ?? "missing"}, msg=${payload.msg || "empty response"}.`);
  }
  return payload.data;
}

async function poll(description, probe, predicate) {
  const deadline = Date.now() + TIMEOUT_MS;
  let latest;
  while (Date.now() < deadline) {
    latest = await probe();
    if (predicate(latest)) return latest;
    await sleep(POLL_INTERVAL_MS);
  }
  throw new Error(`${description} timed out after ${TIMEOUT_MS}ms. Last value: ${JSON.stringify(latest)}. ${STARTUP_HINT}`);
}

async function getItem(itemId) {
  const items = recordsOf(await request("/user/explore-item/list"));
  return items.find((item) => Number(item.id) === Number(itemId));
}

async function ensureShopOpen(adminToken) {
  const originalStatus = Number(await request("/admin/shop/status", { adminToken }));
  if (originalStatus !== 1) {
    await request("/admin/shop/1", { method: "PUT", adminToken });
  }
  return originalStatus;
}

async function restoreShopStatus(adminToken, originalStatus) {
  if (originalStatus !== 1) {
    await request("/admin/shop/0", { method: "PUT", adminToken });
  }
}

async function main() {
  const admin = await request("/admin/employee/login", {
    method: "POST",
    body: { username: config.adminUsername, password: config.adminPassword }
  });
  const user = await request("/user/user/login", {
    method: "POST",
    body: { phone: config.userPhone, password: config.userPassword }
  });
  if (!admin?.token || !user?.token) throw new Error("Login did not return both admin and user tokens.");

  const originalShopStatus = await ensureShopOpen(admin.token);
  let orderId;
  try {
    const items = recordsOf(await request("/user/explore-item/list"));
    const item = items.find((candidate) => Number(candidate.status) === 1
      && Number(candidate.booked || 0) < Number(candidate.capacity || 0));
    if (!item) throw new Error("No enabled item with spare capacity is available for the reliability smoke.");

    const originalBooked = Number(item.booked || 0);
    const requestId = `reliability-${Date.now()}-${Math.random().toString(16).slice(2)}`;
    const body = {
      requestId,
      orderType: 1,
      itemId: item.id,
      peopleCount: 1,
      contactName: "可靠性自检",
      contactPhone: config.userPhone,
      reserveTime: tomorrowMorning(),
      remark: "order-reliability-smoke"
    };

    orderId = await request("/user/explore-order", {
      method: "POST",
      userToken: user.token,
      body
    });
    const duplicateOrderId = await request("/user/explore-order", {
      method: "POST",
      userToken: user.token,
      body
    });
    if (Number(duplicateOrderId) !== Number(orderId)) {
      throw new Error(`Idempotent request returned different orders: ${orderId} and ${duplicateOrderId}.`);
    }

    const reservedItem = await poll(
      "single capacity reservation",
      () => getItem(item.id),
      (current) => Number(current?.booked) === originalBooked + 1
    );

    const expiredOrder = await poll(
      "system timeout state",
      () => request(`/user/explore-order/${orderId}`, { userToken: user.token }),
      (order) => Number(order?.status) === 4
    );
    if (expiredOrder.cancelType !== "TIMEOUT") {
      throw new Error(`Expected TIMEOUT cancelType, got ${expiredOrder.cancelType}.`);
    }

    const restoredItem = await poll(
      "capacity restoration",
      () => getItem(item.id),
      (current) => Number(current?.booked) === originalBooked
    );

    const notification = await poll(
      "ORDER_EXPIRED notification",
      async () => {
        const page = await request("/user/notification/page?page=1&pageSize=100", { userToken: user.token });
        return recordsOf(page).find((entry) => Number(entry.orderId) === Number(orderId)
          && entry.notificationType === "ORDER_EXPIRED");
      },
      Boolean
    );
    await request("/user/notification/unread-count", { userToken: user.token });
    await request(`/user/notification/${notification.id}/read`, {
      method: "PUT",
      userToken: user.token
    });
    const readNotification = await poll(
      "notification read state",
      async () => {
        const page = await request("/user/notification/page?page=1&pageSize=100", { userToken: user.token });
        return recordsOf(page).find((entry) => Number(entry.id) === Number(notification.id));
      },
      (entry) => Number(entry?.readStatus) === 1
    );

    console.table([
      { step: "idempotent create", result: `${orderId} = ${duplicateOrderId}` },
      { step: "capacity reserved once", result: `${originalBooked} -> ${reservedItem.booked}` },
      { step: "system timeout", result: `status=${expiredOrder.status}, type=${expiredOrder.cancelType}` },
      { step: "capacity restored", result: `${reservedItem.booked} -> ${restoredItem.booked}` },
      { step: "notification delivered", result: `${readNotification.notificationType}, read=${readNotification.readStatus}` }
    ]);
  } finally {
    if (orderId) {
      try {
        const current = await request(`/user/explore-order/${orderId}`, { userToken: user.token });
        if (Number(current.status) === 0 || Number(current.status) === 1) {
          await request(`/user/explore-order/${orderId}/cancel`, { method: "PUT", userToken: user.token });
        }
      } catch (error) {
        console.warn(`Order cleanup warning: ${error.message}`);
      }
    }
    await restoreShopStatus(admin.token, originalShopStatus);
  }
}

main().catch((error) => {
  console.error(error.message);
  process.exit(1);
});
