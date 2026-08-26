import { clearStoredAuthentication, createRefreshCoordinator } from "./auth-core.js";

const DEFAULT_API_BASE = "";
const ENV_API_BASE = import.meta.env.VITE_API_BASE || DEFAULT_API_BASE;
const refreshCoordinator = createRefreshCoordinator();
const LEGACY_ACCESS_KEYS = ["localExplorerAdminToken", "localExplorerUserToken"];

const AUTH_CONFIG = {
  admin: {
    tokenKey: "localExplorerAdminAccess",
    nameKey: "localExplorerAdminName",
    roleKey: "localExplorerAdminRole",
    loginPath: "/admin/employee/login",
    refreshPath: "/admin/employee/refresh",
    logoutPath: "/admin/employee/logout",
    tokenHeader: "token"
  },
  client: {
    tokenKey: "localExplorerUserAccess",
    nameKey: "localExplorerUserName",
    idKey: "localExplorerUserId",
    loginPath: "/user/user/login",
    refreshPath: "/user/user/refresh",
    logoutPath: "/user/user/logout",
    tokenHeader: "authentication"
  }
};

export function isDemoMode(locationLike = window.location) {
  return new URLSearchParams(locationLike.search || "").get("demo") === "1";
}

export function getApiBase() {
  return localStorage.getItem("localExplorerApiBase") || ENV_API_BASE;
}

export function setApiBase(value) {
  const next = String(value || "").trim() || DEFAULT_API_BASE;
  localStorage.setItem("localExplorerApiBase", next);
  return next;
}

function sessionStore() {
  return window.sessionStorage;
}

export function createSession(scope) {
  const config = AUTH_CONFIG[scope];
  const store = sessionStore();
  clearStoredAuthentication({
    sessionStorage: store,
    localStorage: window.localStorage,
    legacyKeys: LEGACY_ACCESS_KEYS
  });
  const state = {
    scope,
    apiBase: getApiBase(),
    token: store.getItem(config.tokenKey) || "",
    userName: store.getItem(config.nameKey) || "",
    userId: config.idKey ? store.getItem(config.idKey) || "" : "",
    role: config.roleKey ? store.getItem(config.roleKey) || "" : "",
    demo: isDemoMode()
  };

  const demo = window.LocalExplorerDemo;
  if (state.demo && demo) {
    if (scope === "admin") demo.seedAdminSession(state);
    if (scope === "client") demo.seedClientSession(state);
  }
  return state;
}

function apiUrl(session, path) {
  const base = session.apiBase.replace(/\/$/, "");
  return base ? `${base}${path}` : path;
}

function readPayloadName(scope, data) {
  if (scope === "admin") return data.name || data.userName || "运营管理员";
  return data.name || data.userName || data.nickName || "本地用户";
}

export function saveSession(scope, session, data) {
  const config = AUTH_CONFIG[scope];
  const next = {
    ...session,
    token: data.token || session.token,
    userName: readPayloadName(scope, data),
    userId: String(data.id || data.userId || session.userId || ""),
    role: data.role || session.role || ""
  };
  Object.assign(session, next);

  if (!next.demo) {
    const store = sessionStore();
    store.setItem(config.tokenKey, next.token);
    store.setItem(config.nameKey, next.userName);
    if (config.idKey && next.userId) store.setItem(config.idKey, next.userId);
    if (config.roleKey && next.role) store.setItem(config.roleKey, next.role);
  }
  return next;
}

export function clearSession(scope) {
  const config = AUTH_CONFIG[scope];
  const store = sessionStore();
  clearStoredAuthentication({
    sessionStorage: store,
    localStorage: window.localStorage,
    sessionKeys: [config.tokenKey, config.nameKey, config.idKey, config.roleKey].filter(Boolean),
    legacyKeys: LEGACY_ACCESS_KEYS
  });
}

export function formatRequestError(response, payload = {}) {
  let message = payload?.msg || "";
  const status = Number(response?.status || 0);
  if (!message && status === 401) message = "会话已失效，请重新登录。";
  if (!message && status === 403) message = "当前账号没有访问权限。";
  if (!message && status === 429) message = "登录尝试过于频繁，请稍后再试。";
  if (!message && status === 404) message = "接口不存在或前端代理路径配置错误，请检查请求地址。";
  if (!message && status >= 500) message = "后端服务未启动或8080端口不可达，请先在IDEA运行LocalExplorerApplication。";
  if (!message && status >= 400) message = "请求参数不正确，请检查输入后重试。";
  if (!message) message = "请求失败，请稍后重试。";
  const responseRequestId = response?.headers ? response.headers.get("X-Request-Id") : "";
  const requestId = payload?.requestId || responseRequestId;
  return requestId ? `${message}（请求ID：${requestId}）` : message;
}

function createTraceId() {
  if (globalThis.crypto?.randomUUID) return globalThis.crypto.randomUUID().replaceAll("-", "");
  return `${Date.now().toString(16)}${Math.random().toString(16).slice(2)}`.slice(0, 32);
}

async function fetchJson(session, path, options = {}) {
  const headers = { ...(options.headers || {}) };
  headers["X-Request-Id"] = headers["X-Request-Id"] || createTraceId();
  if (options.body !== undefined && !(options.body instanceof FormData)) {
    headers["Content-Type"] = headers["Content-Type"] || "application/json";
  }
  if (session.token && !options.skipAccessToken) {
    headers[AUTH_CONFIG[session.scope].tokenHeader] = session.token;
  }
  try {
    const response = await fetch(apiUrl(session, path), {
      ...options,
      headers,
      credentials: "same-origin"
    });
    const payload = await response.json().catch(() => ({}));
    return { response, payload };
  } catch (err) {
    throw new Error("后端未启动或接口地址不可达，请确认IDEA已运行后端服务。");
  }
}

async function refreshAccess(session) {
  if (session.demo) return session;
  return refreshCoordinator.run(session.scope, async () => {
    const config = AUTH_CONFIG[session.scope];
    const { response, payload } = await fetchJson(session, config.refreshPath, {
      method: "POST",
      skipAccessToken: true,
      skipAuthRefresh: true
    });
    if (!response.ok || payload.code !== 1) {
      clearSession(session.scope);
      session.token = "";
      throw new Error(formatRequestError(response, payload));
    }
    return saveSession(session.scope, session, payload.data || {});
  });
}

export async function restoreSession(scope, session = createSession(scope)) {
  if (session.demo || session.token) return session;
  return refreshAccess(session);
}

function redirectToLogin(scope) {
  clearSession(scope);
  if (window.location) window.location.href = "./login.html";
}

function successfulData(response, payload) {
  if (!response.ok || (payload.code !== undefined && payload.code !== 1)) {
    throw new Error(formatRequestError(response, payload));
  }
  return payload.data;
}

export async function request(session, path, options = {}) {
  const demo = window.LocalExplorerDemo;
  if (session.demo && demo) {
    return session.scope === "admin" ? demo.adminRequest(path, options) : demo.clientRequest(path, options);
  }

  let result = await fetchJson(session, path, options);
  if (result.response.status === 401 && !options.skipAuthRefresh) {
    try {
      await refreshAccess(session);
      result = await fetchJson(session, path, { ...options, skipAuthRefresh: true });
    } catch (refreshError) {
      redirectToLogin(session.scope);
      throw refreshError;
    }
  }
  if (result.response.status === 401) redirectToLogin(session.scope);
  return successfulData(result.response, result.payload);
}

export async function loginAdmin(session, username, password) {
  const data = await request(session, AUTH_CONFIG.admin.loginPath, {
    method: "POST",
    body: JSON.stringify({ username, password }),
    skipAuthRefresh: true
  });
  return saveSession("admin", session, data);
}

export async function loginClient(session, phone, password) {
  const data = await request(session, AUTH_CONFIG.client.loginPath, {
    method: "POST",
    body: JSON.stringify({ phone, password }),
    skipAuthRefresh: true
  });
  return saveSession("client", session, data);
}

export async function logout(scope, session = createSession(scope)) {
  if (!session.demo) {
    await request(session, AUTH_CONFIG[scope].logoutPath, {
      method: "POST",
      skipAuthRefresh: true,
      skipAccessToken: true
    }).catch(() => undefined);
  }
  clearSession(scope);
  session.token = "";
}

export async function downloadCsv(session, path, filename, fallbackCsv = "") {
  return downloadFile(session, path, filename, fallbackCsv ? `\uFEFF${fallbackCsv}` : "");
}

export async function downloadFile(session, path, fallbackName = "export.csv", fallbackContent = "") {
  if (session.demo) {
    const type = fallbackName.toLowerCase().endsWith(".xlsx")
      ? "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
      : "text/csv;charset=UTF-8";
    saveDownload(new Blob([fallbackContent || "演示导出任务\n"], { type }), fallbackName);
    return;
  }
  const fetchDownload = async () => {
    const headers = { "X-Request-Id": createTraceId() };
    if (session.token) headers[AUTH_CONFIG[session.scope].tokenHeader] = session.token;
    return fetch(apiUrl(session, path), { headers, credentials: "same-origin" });
  };
  let response = await fetchDownload().catch(() => { throw new Error("后端未启动或接口地址不可达。"); });
  if (response.status === 401) {
    await refreshAccess(session);
    response = await fetchDownload();
  }
  if (!response.ok) throw new Error(formatRequestError(response, await response.json().catch(() => ({}))));
  saveDownload(await response.blob(), responseFilename(response, fallbackName));
}

function responseFilename(response, fallbackName) {
  const disposition = response.headers.get("Content-Disposition") || "";
  const utf8Name = disposition.match(/filename\*=UTF-8''([^;]+)/i)?.[1];
  if (utf8Name) {
    try { return decodeURIComponent(utf8Name); } catch { return fallbackName; }
  }
  return disposition.match(/filename="([^"]+)"/i)?.[1] || fallbackName;
}

function saveDownload(blob, filename) {
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement("a");
  anchor.href = url;
  anchor.download = filename;
  document.body.appendChild(anchor);
  anchor.click();
  anchor.remove();
  URL.revokeObjectURL(url);
}

export function requireClientAuth(session) {
  if (!session.token) {
    location.href = "./login.html";
    return false;
  }
  return true;
}
