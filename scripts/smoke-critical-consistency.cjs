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

const state = {
  adminToken: "",
  userToken: "",
  userId: null,
  originalUserStatus: null,
  userStatusChanged: false,
  employeeId: null,
  item: null,
  originalShopStatus: null,
  orderIds: []
};

async function rawRequest(path, options = {}) {
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
  return { httpStatus: response.status, payload };
}

async function request(path, options = {}) {
  const result = await rawRequest(path, options);
  if (result.httpStatus >= 400 || Number(result.payload.code) !== 1) {
    throw new Error(`${options.method || "GET"} ${path} failed: HTTP ${result.httpStatus}, code=${result.payload.code ?? "missing"}, msg=${result.payload.msg || "empty response"}.`);
  }
  return result.payload.data;
}

function recordsOf(pageLike) {
  if (Array.isArray(pageLike)) return pageLike;
  return pageLike?.records || [];
}

function tomorrowMorning() {
  const date = new Date(Date.now() + 24 * 60 * 60 * 1000);
  date.setHours(10, 30, 0, 0);
  const pad = (value) => String(value).padStart(2, "0");
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`;
}

function itemUpdateBody(item, capacity) {
  return {
    id: item.id,
    name: item.name,
    categoryId: item.categoryId,
    price: item.price,
    image: item.image,
    description: item.description,
    durationMinutes: item.durationMinutes,
    capacity,
    district: item.district,
    address: item.address,
    meetingPoint: item.meetingPoint,
    cancelPolicy: item.cancelPolicy,
    status: item.status,
    tags: item.tags || []
  };
}

async function login() {
  const admin = await request("/admin/employee/login", {
    method: "POST",
    body: { username: config.adminUsername, password: config.adminPassword }
  });
  const user = await request("/user/user/login", {
    method: "POST",
    body: { phone: config.userPhone, password: config.userPassword }
  });
  if (!admin?.token || !user?.token) throw new Error("Login did not return both admin and user tokens.");
  state.adminToken = admin.token;
  state.userToken = user.token;
}

async function ensureShopOpen() {
  state.originalShopStatus = Number(await request("/admin/shop/status", { adminToken: state.adminToken }));
  if (state.originalShopStatus !== 1) {
    await request("/admin/shop/1", { method: "PUT", adminToken: state.adminToken });
  }
}

async function chooseCapacityItem() {
  const page = await request("/admin/explore-item/page?page=1&pageSize=100&status=1", {
    adminToken: state.adminToken
  });
  const row = recordsOf(page).find((item) => {
    const capacity = Number(item.capacity);
    const booked = Number(item.booked || 0);
    return Number(item.status) === 1 && Number.isFinite(capacity) && Number.isFinite(booked) && capacity > booked;
  });
  if (!row?.id) throw new Error("No enabled item with available capacity was found for the consistency smoke.");
  state.item = await request(`/admin/explore-item/${row.id}`, { adminToken: state.adminToken });
}

async function verifyAtomicBooking() {
  const originalBooked = Number(state.item.booked || 0);
  await request("/admin/explore-item", {
    method: "PUT",
    adminToken: state.adminToken,
    body: itemUpdateBody(state.item, originalBooked + 1)
  });

  const body = {
    orderType: 1,
    itemId: state.item.id,
    peopleCount: 1,
    contactName: "Consistency Smoke",
    contactPhone: config.userPhone,
    reserveTime: tomorrowMorning(),
    remark: "critical-consistency-smoke"
  };
  const attempts = await Promise.all([
    rawRequest("/user/explore-order", { method: "POST", userToken: state.userToken, body }),
    rawRequest("/user/explore-order", { method: "POST", userToken: state.userToken, body })
  ]);
  const successful = attempts.filter((attempt) => Number(attempt.payload.code) === 1 && attempt.payload.data);
  const rejected = attempts.filter((attempt) => Number(attempt.payload.code) === 0);
  state.orderIds.push(...successful.map((attempt) => attempt.payload.data));
  if (successful.length !== 1 || rejected.length !== 1) {
    throw new Error(`Expected one successful and one rejected concurrent reservation, got ${successful.length}/${rejected.length}.`);
  }

  const afterReserve = await request(`/admin/explore-item/${state.item.id}`, { adminToken: state.adminToken });
  const bookedAfterReserve = Number(afterReserve.booked);
  if (bookedAfterReserve !== originalBooked + 1) {
    throw new Error(`Booked count after the race should be ${originalBooked + 1}, got ${bookedAfterReserve}.`);
  }

  const orderId = state.orderIds[0];
  await request(`/user/explore-order/${orderId}/cancel`, {
    method: "PUT",
    userToken: state.userToken
  });
  state.orderIds = state.orderIds.filter((id) => id !== orderId);
  const afterCancel = await request(`/admin/explore-item/${state.item.id}`, { adminToken: state.adminToken });
  const bookedAfterCancel = Number(afterCancel.booked);
  if (bookedAfterCancel !== originalBooked) {
    throw new Error(`Booked count after cancellation should return to ${originalBooked}, got ${bookedAfterCancel}.`);
  }

  return { successfulReservations: successful.length, rejectedReservations: rejected.length, bookedAfterReserve, bookedAfterCancel };
}

async function verifyEmployeeSessionRevocation() {
  const stamp = Date.now();
  const username = `sessioncheck${stamp}`;
  const name = `Session Check ${stamp}`;
  await request("/admin/employee", {
    method: "POST",
    adminToken: state.adminToken,
    body: { username, name, phone: "13900009999", sex: "1", idNumber: "110101199001019999" }
  });
  const page = await request(`/admin/employee/page?page=1&pageSize=20&name=${encodeURIComponent(name)}`, {
    adminToken: state.adminToken
  });
  const employee = recordsOf(page).find((row) => row.username === username);
  if (!employee?.id) throw new Error("Temporary employee could not be read back.");
  state.employeeId = employee.id;

  const login = await request("/admin/employee/login", {
    method: "POST",
    body: { username, password: "123456" }
  });
  const before = await rawRequest("/admin/category/page?page=1&pageSize=1", { adminToken: login.token });
  await request(`/admin/employee/status/0?id=${state.employeeId}`, {
    method: "POST",
    adminToken: state.adminToken
  });
  const after = await rawRequest("/admin/category/page?page=1&pageSize=1", { adminToken: login.token });
  if (before.httpStatus !== 200 || Number(before.payload.code) !== 1 || after.httpStatus !== 401) {
    throw new Error(`Employee token revocation expected 200 -> 401, got ${before.httpStatus} -> ${after.httpStatus}.`);
  }
  return { enabledEmployeeTokenHttp: before.httpStatus, disabledEmployeeTokenHttp: after.httpStatus };
}

async function verifyUserSessionRevocation() {
  const page = await request(`/admin/user-manage/page?page=1&pageSize=20&phone=${encodeURIComponent(config.userPhone)}`, {
    adminToken: state.adminToken
  });
  const user = recordsOf(page).find((row) => row.phone === config.userPhone);
  if (!user?.id) throw new Error("Configured smoke user could not be found in user management.");
  state.userId = user.id;
  state.originalUserStatus = Number(user.status);

  const before = await rawRequest("/user/explore-order/page?page=1&pageSize=1", { userToken: state.userToken });
  await request(`/admin/user-manage/status/0?id=${state.userId}`, {
    method: "POST",
    adminToken: state.adminToken
  });
  state.userStatusChanged = true;
  const after = await rawRequest("/user/explore-order/page?page=1&pageSize=1", { userToken: state.userToken });
  if (before.httpStatus !== 200 || Number(before.payload.code) !== 1 || after.httpStatus !== 401) {
    throw new Error(`User token revocation expected 200 -> 401, got ${before.httpStatus} -> ${after.httpStatus}.`);
  }
  return { enabledUserTokenHttp: before.httpStatus, disabledUserTokenHttp: after.httpStatus };
}

async function restoreState() {
  const errors = [];
  async function restore(action) {
    try {
      await action();
    } catch (error) {
      errors.push(error.message);
    }
  }

  if (state.userStatusChanged && state.userId && state.adminToken) {
    await restore(() => request(`/admin/user-manage/status/${state.originalUserStatus}?id=${state.userId}`, {
      method: "POST",
      adminToken: state.adminToken
    }));
    state.userStatusChanged = false;
  }
  for (const orderId of state.orderIds.splice(0)) {
    await restore(() => request(`/user/explore-order/${orderId}/cancel`, {
      method: "PUT",
      userToken: state.userToken
    }));
  }
  if (state.item && state.adminToken) {
    await restore(() => request("/admin/explore-item", {
      method: "PUT",
      adminToken: state.adminToken,
      body: itemUpdateBody(state.item, state.item.capacity)
    }));
  }
  if (state.employeeId && state.adminToken) {
    await restore(() => request(`/admin/employee?id=${state.employeeId}`, {
      method: "DELETE",
      adminToken: state.adminToken
    }));
  }
  if (state.originalShopStatus !== null && state.originalShopStatus !== 1 && state.adminToken) {
    await restore(() => request(`/admin/shop/${state.originalShopStatus}`, {
      method: "PUT",
      adminToken: state.adminToken
    }));
  }
  if (errors.length) throw new Error(`State restoration failed: ${errors.join(" | ")}`);
}

async function main() {
  let summary;
  try {
    await login();
    await ensureShopOpen();
    await chooseCapacityItem();
    summary = {
      ...(await verifyAtomicBooking()),
      ...(await verifyEmployeeSessionRevocation()),
      ...(await verifyUserSessionRevocation())
    };
  } finally {
    await restoreState();
  }
  console.table(Object.entries(summary).map(([check, result]) => ({ check, result })));
}

main().catch((error) => {
  console.error(error.message);
  process.exit(1);
});
