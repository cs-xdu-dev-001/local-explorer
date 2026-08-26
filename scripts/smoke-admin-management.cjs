const DEFAULT_BASE_URL = "http://localhost:8080";

const config = {
  baseUrl: process.argv.find((arg) => arg.startsWith("--base="))?.slice("--base=".length)
    || process.env.BACKEND_BASE_URL
    || DEFAULT_BASE_URL,
  adminUsername: process.env.ADMIN_USERNAME || "admin",
  adminPassword: process.env.ADMIN_PASSWORD || "123456"
};

const ADMIN_SCHEMA_HINT = "If backend logs mention Unknown column 'duration_minutes', Unknown column 'capacity', Unknown column 'booked', or Unknown column 'status', run docs/local-explorer-migrate.sql against the local_explorer database so the MySQL schema matches the current code.";
const EXPECTED_EMPLOYEE_AUDIT = ["新增员工", "修改员工资料", "员工账号启停", "删除员工"];
const stamp = String(Date.now());
const prefix = `SM${stamp.slice(-6)}`;
const created = {
  categories: [],
  items: [],
  packages: [],
  employees: []
};

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
    throw new Error(formatApiFailure(path, options, response, payload));
  }
  return payload.data;
}

function formatApiFailure(path, options, response, payload) {
  const parts = [
    `${options.method || "GET"} ${path} failed: HTTP ${response.status}, API returned code=${payload.code ?? "missing"}, msg=${payload.msg || "empty response"}.`
  ];
  if (path.startsWith("/admin/explore-item") || path.startsWith("/admin/explore-package") || path.startsWith("/admin/user-manage")) {
    parts.push(ADMIN_SCHEMA_HINT);
  }
  return parts.join(" ");
}

function recordsOf(pageLike) {
  if (Array.isArray(pageLike)) return pageLike;
  return pageLike?.records || [];
}

async function readOperationLogs(adminToken) {
  return recordsOf(await request("/admin/operation-log/page?page=1&pageSize=100", { adminToken }));
}

async function waitForOperationLogs(adminToken, afterId) {
  const deadline = Date.now() + 5000;
  let recent = [];

  while (Date.now() < deadline) {
    recent = (await readOperationLogs(adminToken)).filter((row) => Number(row.id) > Number(afterId));
    const descriptions = new Set(recent.map((row) => row.description));
    if (EXPECTED_EMPLOYEE_AUDIT.every((description) => descriptions.has(description))) {
      return recent;
    }
    await new Promise((resolve) => setTimeout(resolve, 100));
  }

  const descriptions = new Set(recent.map((row) => row.description));
  const missing = EXPECTED_EMPLOYEE_AUDIT.filter((description) => !descriptions.has(description));
  throw new Error(`Operation log smoke is missing: ${missing.join(", ")}.`);
}

async function verifyFailedWriteNotAudited(adminToken, afterId) {
  let rejected = false;
  try {
    await request("/admin/employee?id=1", { method: "DELETE", adminToken });
  } catch (error) {
    rejected = true;
  }
  if (!rejected) {
    throw new Error("Default admin deletion unexpectedly succeeded.");
  }

  await new Promise((resolve) => setTimeout(resolve, 700));
  const falseSuccess = (await readOperationLogs(adminToken)).filter((row) =>
    Number(row.id) > Number(afterId)
    && row.requestUri === "/admin/employee"
    && row.description === "删除员工"
  );
  if (falseSuccess.length) {
    throw new Error("Failed default admin deletion was recorded as success.");
  }
  return "not recorded as success";
}

function query(params) {
  return new URLSearchParams(Object.entries(params).filter(([, value]) => value !== undefined)).toString();
}

async function loginAdmin() {
  const admin = await request("/admin/employee/login", {
    method: "POST",
    body: { username: config.adminUsername, password: config.adminPassword }
  });
  if (!admin?.token) throw new Error("Admin login did not return token.");
  return admin.token;
}

async function findByName(basePath, name, adminToken, extra = {}) {
  const data = await request(`${basePath}/page?${query({ page: 1, pageSize: 50, name, ...extra })}`, { adminToken });
  const row = recordsOf(data).find((record) => record.name === name);
  if (!row?.id) throw new Error(`Could not find ${name} from ${basePath}/page.`);
  return row;
}

async function expectStatus(basePath, name, status, adminToken, extra = {}) {
  const row = await findByName(basePath, name, adminToken, extra);
  if (Number(row.status) !== Number(status)) {
    throw new Error(`${basePath} ${name} expected status ${status}, got ${row.status}.`);
  }
  return row;
}

async function setCategoryStatus(id, status, adminToken) {
  await request(`/admin/category/status/${status}?id=${id}`, { method: "POST", adminToken });
}

async function setItemStatus(id, status, adminToken) {
  await request(`/admin/explore-item/status/${status}?id=${id}`, { method: "POST", adminToken });
}

async function setPackageStatus(id, status, adminToken) {
  await request(`/admin/explore-package/status/${status}?id=${id}`, { method: "POST", adminToken });
}

async function setEmployeeStatus(id, status, adminToken) {
  await request(`/admin/employee/status/${status}?id=${id}`, { method: "POST", adminToken });
}

async function setUserStatus(id, status, adminToken) {
  await request(`/admin/user-manage/status/${status}?id=${id}`, { method: "POST", adminToken });
}

async function cleanup(adminToken) {
  for (const id of created.packages.reverse()) {
    await setPackageStatus(id, 0, adminToken).catch(() => {});
    await request(`/admin/explore-package?ids=${id}`, { method: "DELETE", adminToken }).catch(() => {});
  }
  for (const id of created.items.reverse()) {
    await setItemStatus(id, 0, adminToken).catch(() => {});
    await request(`/admin/explore-item?ids=${id}`, { method: "DELETE", adminToken }).catch(() => {});
  }
  for (const id of created.categories.reverse()) {
    await setCategoryStatus(id, 0, adminToken).catch(() => {});
    await request(`/admin/category?id=${id}`, { method: "DELETE", adminToken }).catch(() => {});
  }
  for (const id of created.employees.reverse()) {
    await request(`/admin/employee?id=${id}`, { method: "DELETE", adminToken }).catch(() => {});
  }
}

async function createCategory(adminToken, type, name) {
  await request("/admin/category", {
    method: "POST",
    adminToken,
    body: { type, name, sort: 999 }
  });
  const category = await findByName("/admin/category", name, adminToken, { type });
  created.categories.push(category.id);
  return category;
}

async function verifyCategory(adminToken, type, originalName) {
  const category = await createCategory(adminToken, type, originalName);
  const editedName = `${originalName}Edited`;
  await request("/admin/category", {
    method: "PUT",
    adminToken,
    body: { id: category.id, type, name: editedName, sort: 998 }
  });
  await findByName("/admin/category", editedName, adminToken, { type });
  await setCategoryStatus(category.id, 1, adminToken);
  await expectStatus("/admin/category", editedName, 1, adminToken, { type });
  await setCategoryStatus(category.id, 0, adminToken);
  await expectStatus("/admin/category", editedName, 0, adminToken, { type });
  return { id: category.id, name: editedName, type };
}

async function verifyItem(adminToken, category) {
  const name = `${prefix}Item`;
  const editedName = `${name}Edited`;
  const body = {
    name,
    categoryId: category.id,
    price: 19.9,
    image: "/assets/images/coffee.svg",
    description: "admin management smoke item",
    durationMinutes: 45,
    capacity: 6,
    booked: 0,
    district: "Smoke",
    address: "Smoke Road",
    meetingPoint: "Smoke Gate",
    cancelPolicy: "Cancelable before visit",
    status: 0,
    tags: []
  };

  await request("/admin/explore-item", { method: "POST", adminToken, body });
  const item = await findByName("/admin/explore-item", name, adminToken);
  created.items.push(item.id);
  await request("/admin/explore-item", {
    method: "PUT",
    adminToken,
    body: { ...body, id: item.id, name: editedName, price: 29.9 }
  });
  const editedItem = await findByName("/admin/explore-item", editedName, adminToken);
  await setItemStatus(item.id, 1, adminToken);
  await expectStatus("/admin/explore-item", editedName, 1, adminToken);
  return { ...editedItem, id: item.id, name: editedName, price: 29.9 };
}

async function verifyPackage(adminToken, category, item) {
  const name = `${prefix}Package`;
  const editedName = `${name}Edited`;
  const body = {
    name,
    categoryId: category.id,
    price: 59.9,
    image: "/assets/images/package.svg",
    description: "admin management smoke package",
    durationMinutes: 90,
    capacity: 5,
    booked: 0,
    district: "Smoke",
    address: "Smoke Road",
    meetingPoint: "Smoke Gate",
    cancelPolicy: "Cancelable before visit",
    status: 0,
    packageItems: [{ itemId: item.id, name: item.name, price: item.price, copies: 1 }]
  };

  await request("/admin/explore-package", { method: "POST", adminToken, body });
  const pack = await findByName("/admin/explore-package", name, adminToken);
  created.packages.push(pack.id);
  const createdDetail = await request(`/admin/explore-package/${pack.id}`, { adminToken });
  if (!createdDetail.packageItems?.length) {
    throw new Error("Created package did not persist packageItems.");
  }
  await request("/admin/explore-package", {
    method: "PUT",
    adminToken,
    body: {
      ...body,
      id: pack.id,
      name: editedName,
      price: 69.9,
      packageItems: [{ itemId: item.id, name: item.name, price: item.price, copies: 2 }]
    }
  });
  const editedDetail = await request(`/admin/explore-package/${pack.id}`, { adminToken });
  if (Number(editedDetail.packageItems?.[0]?.copies) !== 2) {
    throw new Error("Updated package did not preserve edited package item copies.");
  }
  await findByName("/admin/explore-package", editedName, adminToken);
  await setPackageStatus(pack.id, 1, adminToken);
  await expectStatus("/admin/explore-package", editedName, 1, adminToken);
  await setPackageStatus(pack.id, 0, adminToken);
  await expectStatus("/admin/explore-package", editedName, 0, adminToken);
  await request(`/admin/explore-package?ids=${pack.id}`, { method: "DELETE", adminToken });
  created.packages = created.packages.filter((id) => id !== pack.id);
  return pack.id;
}

async function verifyEmployee(adminToken) {
  const name = `${prefix}Employee`;
  const editedName = `${name}Edited`;
  const username = `smoke${stamp.slice(-10)}`;
  const phone = `139${stamp.slice(-8).padStart(8, "0")}`;
  const idNumber = `11010119900101${stamp.slice(-4).padStart(4, "0")}`;

  await request("/admin/employee", {
    method: "POST",
    adminToken,
    body: { username, name, phone, sex: "1", idNumber }
  });
  const employee = await findByName("/admin/employee", name, adminToken);
  created.employees.push(employee.id);
  await request("/admin/employee", {
    method: "PUT",
    adminToken,
    body: { id: employee.id, username, name: editedName, phone, sex: "1", idNumber }
  });
  await findByName("/admin/employee", editedName, adminToken);
  await setEmployeeStatus(employee.id, 0, adminToken);
  await expectStatus("/admin/employee", editedName, 0, adminToken);
  await setEmployeeStatus(employee.id, 1, adminToken);
  await expectStatus("/admin/employee", editedName, 1, adminToken);
  await request(`/admin/employee?id=${employee.id}`, { method: "DELETE", adminToken });
  created.employees = created.employees.filter((id) => id !== employee.id);
  return employee.id;
}

async function verifyUserStatus(adminToken) {
  const data = await request("/admin/user-manage/page?page=1&pageSize=50", { adminToken });
  const user = recordsOf(data).find((row) => row.id);
  if (!user?.id) throw new Error("Could not find a user from /admin/user-manage/page.");
  const originalStatus = Number(user.status ?? 1);
  const nextStatus = originalStatus === 1 ? 0 : 1;
  try {
    await setUserStatus(user.id, nextStatus, adminToken);
    const changed = await request(`/admin/user-manage/${user.id}`, { adminToken });
    if (Number(changed.status) !== nextStatus) {
      throw new Error(`user ${user.id} expected status ${nextStatus}, got ${changed.status}.`);
    }
    return user.id;
  } finally {
    await setUserStatus(user.id, originalStatus, adminToken).catch(() => {});
  }
}

async function main() {
  const adminToken = await loginAdmin();
  const baselineLogs = await readOperationLogs(adminToken);
  const baselineId = baselineLogs.reduce((max, row) => Math.max(max, Number(row.id) || 0), 0);
  try {
    const failedWriteAudit = await verifyFailedWriteNotAudited(adminToken, baselineId);
    const itemCategory = await verifyCategory(adminToken, 1, `${prefix}IC`);
    const packageCategory = await verifyCategory(adminToken, 2, `${prefix}PC`);
    const item = await verifyItem(adminToken, itemCategory);
    const packageId = await verifyPackage(adminToken, packageCategory, item);
    const employeeId = await verifyEmployee(adminToken);
    const userId = await verifyUserStatus(adminToken);

    await cleanup(adminToken);
    const operationLogs = await waitForOperationLogs(adminToken, baselineId);
    console.table([
      { step: "created category", result: itemCategory.id },
      { step: "updated item", result: item.id },
      { step: "updated package", result: packageId },
      { step: "updated user status", result: userId },
      { step: "deleted employee", result: employeeId },
      { step: "new operation logs", result: operationLogs.length },
      { step: "failed write audit", result: failedWriteAudit }
    ]);
  } catch (error) {
    await cleanup(adminToken);
    throw error;
  }
}

main().catch((error) => {
  console.error(error.message);
  process.exit(1);
});
