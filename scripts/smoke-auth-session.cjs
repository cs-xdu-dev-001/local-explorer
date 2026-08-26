const DEFAULT_BASE_URL = "http://localhost:8080";

const config = {
  baseUrl: process.argv.find((arg) => arg.startsWith("--base="))?.slice("--base=".length)
    || process.env.BACKEND_BASE_URL
    || DEFAULT_BASE_URL,
  adminUsername: process.env.ADMIN_USERNAME || "admin",
  adminPassword: process.env.ADMIN_PASSWORD || "123456",
  userPhone: process.env.USER_PHONE || "13800001111",
  userPassword: process.env.USER_PASSWORD || "123456",
  replayWaitMs: Number(process.env.AUTH_REPLAY_WAIT_MS || 2300)
};

const scopes = {
  admin: {
    login: "/admin/employee/login",
    refresh: "/admin/employee/refresh",
    logout: "/admin/employee/logout",
    logoutAll: "/admin/employee/logout-all",
    cookieName: "LX_ADMIN_REFRESH",
    cookiePath: "/admin",
    tokenHeader: "token"
  },
  user: {
    login: "/user/user/login",
    refresh: "/user/user/refresh",
    logout: "/user/user/logout",
    logoutAll: "/user/user/logout-all",
    cookieName: "LX_USER_REFRESH",
    cookiePath: "/user",
    tokenHeader: "authentication"
  }
};

function assert(condition, message) {
  if (!condition) throw new Error(message);
}

function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

async function http(path, options = {}) {
  const headers = { "X-Request-Id": `auth-smoke-${Date.now()}-${Math.random().toString(16).slice(2)}`, ...(options.headers || {}) };
  if (options.body !== undefined) headers["Content-Type"] = "application/json";
  if (options.cookie) headers.Cookie = options.cookie;
  if (options.accessToken && options.tokenHeader) headers[options.tokenHeader] = options.accessToken;
  let response;
  try {
    response = await fetch(`${config.baseUrl.replace(/\/$/, "")}${path}`, {
      method: options.method || "GET",
      headers,
      body: options.body === undefined ? undefined : JSON.stringify(options.body)
    });
  } catch (error) {
    throw new Error(`Backend is unreachable: ${config.baseUrl}. Start LocalExplorerApplication first. ${error.message}`);
  }
  const payload = await response.json().catch(() => ({}));
  return {
    response,
    payload,
    setCookie: response.headers.get("set-cookie") || "",
    requestId: response.headers.get("x-request-id") || ""
  };
}

function expectStatus(result, status, description) {
  assert(result.response.status === status,
    `${description}: expected HTTP ${status}, got HTTP ${result.response.status}, code=${result.payload.code}, msg=${result.payload.msg}`);
  assert(result.requestId, `${description}: response is missing X-Request-Id`);
}

function cookieFrom(result, scope) {
  const { cookieName, cookiePath } = scopes[scope];
  assert(result.setCookie.includes(`${cookieName}=`), `${scope} response is missing Set-Cookie`);
  assert(result.setCookie.includes("HttpOnly"), `${scope} refresh cookie is missing HttpOnly`);
  assert(result.setCookie.includes("SameSite=Lax"), `${scope} refresh cookie is missing SameSite=Lax`);
  assert(result.setCookie.includes(`Path=${cookiePath}`), `${scope} refresh cookie has the wrong Path`);
  const value = result.setCookie.match(new RegExp(`${cookieName}=([^;]*)`))?.[1];
  assert(value, `${scope} refresh cookie has no value`);
  return `${cookieName}=${value}`;
}

async function login(scope) {
  const body = scope === "admin"
    ? { username: config.adminUsername, password: config.adminPassword }
    : { phone: config.userPhone, password: config.userPassword };
  const result = await http(scopes[scope].login, { method: "POST", body });
  expectStatus(result, 200, `${scope} login`);
  assert(result.payload.code === 1 && result.payload.data?.token, `${scope} login did not return an Access Token`);
  assert(!Object.prototype.hasOwnProperty.call(result.payload.data, "refreshToken"), `${scope} response exposed Refresh Token`);
  return { accessToken: result.payload.data.token, cookie: cookieFrom(result, scope) };
}

async function refresh(scope, cookie, expectedStatus = 200) {
  const result = await http(scopes[scope].refresh, { method: "POST", cookie });
  expectStatus(result, expectedStatus, `${scope} refresh`);
  if (expectedStatus !== 200) return result;
  assert(result.payload.data?.token, `${scope} refresh did not return a new Access Token`);
  return { result, accessToken: result.payload.data.token, cookie: cookieFrom(result, scope) };
}

async function verifyReplayRevokesFamily() {
  const original = await login("admin");
  const rotated = await refresh("admin", original.cookie);
  assert(rotated.cookie !== original.cookie, "Refresh rotation returned the old cookie value");
  await sleep(config.replayWaitMs);

  const replay = await refresh("admin", original.cookie, 401);
  assert(replay.payload.code === 40100, `Replay should return code 40100, got ${replay.payload.code}`);
  const familyRevoked = await refresh("admin", rotated.cookie, 401);
  assert(familyRevoked.payload.code === 40100, "Replay did not revoke the winning token family");
}

async function verifyLogoutAndLogoutAll() {
  const first = await login("user");
  const second = await login("user");
  const logoutAll = await http(scopes.user.logoutAll, {
    method: "POST",
    cookie: first.cookie,
    accessToken: first.accessToken,
    tokenHeader: scopes.user.tokenHeader
  });
  expectStatus(logoutAll, 200, "user logout-all");
  assert(logoutAll.setCookie.includes("Max-Age=0"), "logout-all did not clear the Refresh Cookie");
  await refresh("user", second.cookie, 401);

  const current = await login("user");
  const logout = await http(scopes.user.logout, { method: "POST", cookie: current.cookie });
  expectStatus(logout, 200, "user logout");
  assert(logout.setCookie.includes("Max-Age=0"), "logout did not clear the Refresh Cookie");
  await refresh("user", current.cookie, 401);
}

async function verifyLockoutAndAdminUnlock() {
  const admin = await login("admin");
  const suffix = Date.now().toString().slice(-8);
  const lockedPhone = `139${suffix}`;
  const expectedHint = `${lockedPhone.slice(0, 3)}****${lockedPhone.slice(-4)}`;

  for (let attempt = 1; attempt <= 5; attempt += 1) {
    const failed = await http(scopes.user.login, {
      method: "POST",
      body: { phone: lockedPhone, password: "definitely-wrong" }
    });
    expectStatus(failed, 401, `failed login ${attempt}`);
    assert(failed.payload.msg === "账号或密码错误", "unknown account leaked a distinguishable error");
  }
  const blocked = await http(scopes.user.login, {
    method: "POST",
    body: { phone: lockedPhone, password: "definitely-wrong" }
  });
  expectStatus(blocked, 429, "locked login");
  assert(blocked.payload.code === 42900, `locked login should return HTTP 429/code 42900, got ${blocked.payload.code}`);

  const lockouts = await http("/admin/auth-security/lockouts?page=1&pageSize=100&principalType=USER", {
    accessToken: admin.accessToken,
    tokenHeader: scopes.admin.tokenHeader
  });
  expectStatus(lockouts, 200, "admin lockout query");
  const records = lockouts.payload.data?.records || [];
  const record = records.find((item) => item.accountHint === expectedHint);
  assert(record?.id, "admin lockout query did not return the locked account hint");

  const unlocked = await http(`/admin/auth-security/lockouts/${record.id}`, {
    method: "DELETE",
    accessToken: admin.accessToken,
    tokenHeader: scopes.admin.tokenHeader
  });
  expectStatus(unlocked, 200, "admin unlock");
  const after = await http("/admin/auth-security/lockouts?page=1&pageSize=100&principalType=USER", {
    accessToken: admin.accessToken,
    tokenHeader: scopes.admin.tokenHeader
  });
  expectStatus(after, 200, "admin lockout query after unlock");
  assert(!(after.payload.data?.records || []).some((item) => item.id === record.id), "unlock left the lockout active");

  const stats = await http("/admin/auth-security/sessions/stats", {
    accessToken: admin.accessToken,
    tokenHeader: scopes.admin.tokenHeader
  });
  expectStatus(stats, 200, "admin session stats");
  for (const key of ["active", "rotated", "revoked", "expired"]) {
    assert(Number.isFinite(Number(stats.payload.data?.[key])), `session stats field ${key} is not numeric`);
  }
}

async function main() {
  await verifyReplayRevokesFamily();
  await verifyLogoutAndLogoutAll();
  await verifyLockoutAndAdminUnlock();
  console.table([
    { flow: "refresh rotation + replay", result: "old token consumed once; family revoked" },
    { flow: "logout + logout-all", result: "server sessions revoked; cookies cleared" },
    { flow: "login lockout + admin unlock", result: "HTTP 429 -> ADMIN recovery" }
  ]);
}

main().catch((error) => {
  console.error(error.message);
  process.exitCode = 1;
});
