const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const test = require("node:test");

const frontendRoot = path.resolve(__dirname, "../../../frontend");
const sourceRoot = path.join(frontendRoot, "src");

function readFrontend(relativePath) {
  return fs.readFileSync(path.join(frontendRoot, relativePath), "utf8");
}

function listTextFiles(dir) {
  const entries = fs.readdirSync(dir, { withFileTypes: true });
  return entries.flatMap((entry) => {
    const fullPath = path.join(dir, entry.name);
    if (entry.isDirectory()) return listTextFiles(fullPath);
    return /\.(jsx?|css|html|json)$/.test(entry.name) ? [fullPath] : [];
  });
}

test("React frontend declares a Vite build for admin and client entries", () => {
  const pkg = JSON.parse(readFrontend("package.json"));
  const viteConfig = readFrontend("vite.config.js");

  assert.equal(pkg.scripts.build, "vite build");
  assert.ok(pkg.dependencies.react);
  assert.ok(pkg.dependencies["react-dom"]);
  assert.ok(pkg.dependencies["lucide-react"]);
  assert.ok(pkg.devDependencies.vite);
  assert.match(viteConfig, /console\/index\.html/);
  assert.match(viteConfig, /client\/index\.html/);
  assert.match(viteConfig, /src\/main\/resources\/static/);
});

test("React login flow keeps admin and user authentication separated", () => {
  const authSource = readFrontend("src/lib/auth.js");
  const legacyIdentityPattern = new RegExp([
    "wx",
    ["we", "chat"].join(""),
    String.fromCharCode(24494, 20449),
    String.fromCharCode(23567, 31243, 24207)
  ].join("|"), "i");

  assert.match(authSource, /\/admin\/employee\/login/);
  assert.match(authSource, /\/user\/user\/login/);
  assert.match(authSource, /localExplorerAdminAccess/);
  assert.match(authSource, /localExplorerUserAccess/);
  assert.match(authSource, /sessionStorage/);
  assert.match(authSource, /\/admin\/employee\/refresh/);
  assert.match(authSource, /\/user\/user\/refresh/);
  assert.doesNotMatch(authSource, /localStorage\.setItem\(config\.tokenKey/);
  assert.doesNotMatch(authSource, legacyIdentityPattern);
});

test("admin role is persisted and sensitive navigation is hidden from staff", () => {
  const authSource = readFrontend("src/lib/auth.js");
  const adminSource = readFrontend("src/apps/admin/AdminApp.jsx");
  const demoSource = readFrontend("public/assets/demo-data.js");

  assert.match(authSource, /roleKey:\s*"localExplorerAdminRole"/);
  assert.match(authSource, /role:\s*config\.roleKey/);
  assert.match(authSource, /data\.role/);
  assert.match(authSource, /refreshCoordinator\.run\(session\.scope/);
  assert.match(authSource, /skipAuthRefresh/);
  assert.match(authSource, /status === 403\) message = "当前账号没有访问权限。"/);
  assert.doesNotMatch(authSource, /if \(response\.status === 403\) \{\s*expireSession/);
  assert.match(adminSource, /adminOnly:\s*true/);
  assert.match(adminSource, /session\.role\s*===\s*"ADMIN"/);
  assert.match(adminSource, /visibleNavItems/);
  assert.match(adminSource, /adminRequest\(session,/);
  assert.match(demoSource, /state\.role\s*=\s*"ADMIN"/);
});

test("login hero copy reads like a mature product instead of implementation notes", () => {
  const clientSource = readFrontend("src/apps/client/ClientApp.jsx");
  const adminSource = readFrontend("src/apps/admin/AdminApp.jsx");
  const combined = `${clientSource}\n${adminSource}`;
  const loginHeadlines = ["发现城市好去处", "管理探店业务"];
  const implementationPhrases = [
    "只保留",
    "进入后可",
    "用于演示",
    "业务闭环",
    "登录方案",
    String.fromCharCode(23567, 31243, 24207),
    String.fromCharCode(24494, 20449),
    ["open", "Id"].join("")
  ];

  for (const headline of loginHeadlines) {
    assert.match(combined, new RegExp(`<h1>${headline}</h1>`));
  }
  for (const headline of loginHeadlines) {
    assert.ok([...headline].length <= 12, `${headline} should be concise`);
  }
  for (const phrase of implementationPhrases) {
    assert.doesNotMatch(combined, new RegExp(phrase, "i"));
  }
});

test("login forms do not prefill demo credentials in the product UI", () => {
  const clientSource = readFrontend("src/apps/client/ClientApp.jsx");
  const adminSource = readFrontend("src/apps/admin/AdminApp.jsx");

  assert.match(adminSource, /useState\(\{ username: "", password: "" \}\)/);
  assert.match(clientSource, /useState\(\{ phone: "", password: "" \}\)/);
  assert.match(adminSource, /placeholder="请输入管理员账号"/);
  assert.match(clientSource, /placeholder="请输入手机号"/);
  assert.doesNotMatch(adminSource, /useState\(\{ username: "admin", password: "123456" \}\)/);
  assert.doesNotMatch(clientSource, /useState\(\{ phone: "13800001111", password: "123456" \}\)/);
});

test("API request errors explain the cause instead of leaking a bare status code", async () => {
  const authSource = readFrontend("src/lib/auth.js");
  const loadAuthHelpers = (
    fetchImpl = async () => ({ ok: true, json: async () => ({ code: 1, data: null }) }),
    windowImpl = {},
    storageImpl = { getItem: () => "", setItem: () => {}, removeItem: () => {} }
  ) => {
    const executable = authSource
      .replace(/^import[^\n]+\n/, "")
      .replace("const ENV_API_BASE = import.meta.env.VITE_API_BASE || DEFAULT_API_BASE;", "const ENV_API_BASE = \"\";")
      .replace(/\bexport\s+/g, "");
    return Function("window", "localStorage", "sessionStorage", "fetch", "createRefreshCoordinator", "clearStoredAuthentication", `${executable}; return { formatRequestError, request };`)(
      windowImpl,
      storageImpl,
      storageImpl,
      fetchImpl,
      () => {
        const flights = new Map();
        return { run(scope, action) { if (!flights.has(scope)) flights.set(scope, Promise.resolve().then(action).finally(() => flights.delete(scope))); return flights.get(scope); } };
      },
      ({ sessionStorage: sessionStore, localStorage: localStore, sessionKeys = [], legacyKeys = [] }) => {
        for (const key of sessionKeys) sessionStore?.removeItem(key);
        for (const key of legacyKeys) localStore?.removeItem(key);
      }
    );
  };

  const { formatRequestError, request } = loadAuthHelpers(async () => {
    throw new TypeError("Failed to fetch");
  });

  assert.equal(formatRequestError({ status: 401 }, {}), "会话已失效，请重新登录。");
  assert.equal(formatRequestError({ status: 403 }, {}), "当前账号没有访问权限。");
  assert.equal(formatRequestError({ status: 404 }, {}), "接口不存在或前端代理路径配置错误，请检查请求地址。");
  assert.equal(formatRequestError({ status: 429 }, {}), "登录尝试过于频繁，请稍后再试。");
  assert.equal(formatRequestError({ status: 500 }, {}), "后端服务未启动或8080端口不可达，请先在IDEA运行LocalExplorerApplication。");
  assert.equal(formatRequestError({ status: 502 }, {}), "后端服务未启动或8080端口不可达，请先在IDEA运行LocalExplorerApplication。");
  assert.equal(formatRequestError({ status: 400 }, { msg: "账号或密码错误" }), "账号或密码错误");
  assert.doesNotMatch(authSource, /请求失败：\{response\.status\}/);

  await assert.rejects(
    () => request({ scope: "client", apiBase: "", token: "", demo: false }, "/user/merchant/info"),
    /后端未启动或接口地址不可达/
  );

  const removedKeys = [];
  const sessionWindow = { location: { href: "" } };
  const sessionStorage = {
    getItem: () => "",
    setItem: () => {},
    removeItem: (key) => removedKeys.push(key)
  };
  sessionWindow.sessionStorage = sessionStorage;
  const removedLegacyKeys = [];
  sessionWindow.localStorage = { removeItem: (key) => removedLegacyKeys.push(key) };
  const unauthorized = loadAuthHelpers(
    async () => ({ ok: false, status: 401, json: async () => ({}) }),
    sessionWindow,
    sessionStorage
  );
  await assert.rejects(
    () => unauthorized.request({ scope: "client", apiBase: "", token: "expired", demo: false }, "/user/explore-order/page"),
    /会话已失效/
  );
  assert.deepEqual([...new Set(removedKeys)].sort(), ["localExplorerUserAccess", "localExplorerUserId", "localExplorerUserName"].sort());
  assert.deepEqual([...new Set(removedLegacyKeys)].sort(), ["localExplorerAdminToken", "localExplorerUserToken"].sort());
  assert.equal(sessionWindow.location.href, "./login.html");
});

test("API requests propagate trace IDs and surface them on errors", () => {
  const authSource = readFrontend("src/lib/auth.js");

  assert.match(authSource, /X-Request-Id/);
  assert.match(authSource, /response\.headers\.get\("X-Request-Id"\)/);
  assert.match(authSource, /请求ID/);
});

test("Vite dev mode can talk to an IDEA-started backend without packaging", () => {
  const viteConfig = readFrontend("vite.config.js");
  const authSource = readFrontend("src/lib/auth.js");

  assert.match(viteConfig, /server:/);
  assert.match(viteConfig, /\/admin/);
  assert.match(viteConfig, /\/user/);
  assert.match(viteConfig, /localhost:8080/);
  assert.match(authSource, /DEFAULT_API_BASE = ""/);
  assert.match(authSource, /import\.meta\.env\.VITE_API_BASE/);
});

test("React source removes legacy mobile identity positioning", () => {
  const legacyTerms = [
    String.fromCharCode(23567, 31243, 24207),
    String.fromCharCode(24494, 20449),
    ["We", "Chat"].join(""),
    ["wx", "login"].join("."),
    ["open", "Id"].join(""),
    ["open", "id"].join("")
  ];
  const forbidden = new RegExp(legacyTerms.join("|"), "i");
  const offenders = listTextFiles(sourceRoot)
    .filter((file) => forbidden.test(fs.readFileSync(file, "utf8")))
    .map((file) => path.relative(frontendRoot, file));

  assert.deepEqual(offenders, []);
});

test("client React app exposes product-grade login, protected states, and booking UI", () => {
  const clientSource = readFrontend("src/apps/client/ClientApp.jsx");
  const cssSource = readFrontend("src/styles/app.css");

  assert.match(clientSource, /function ClientLogin/);
  assert.match(clientSource, /用户登录/);
  assert.match(clientSource, /请输入 11 位手机号/);
  assert.match(clientSource, /function ProtectedPage/);
  assert.match(clientSource, /请先登录/);
  assert.match(clientSource, /function OrderDrawer/);
  assert.match(clientSource, /提交预约/);
  assert.match(clientSource, /我的预约/);
  assert.match(cssSource, /\.login-page/);
  assert.match(cssSource, /\.client-nav-link\.active/);
  assert.match(cssSource, /\.spot-card/);
  assert.match(cssSource, /\.drawer/);
  assert.match(cssSource, /\.empty-state/);
  assert.match(cssSource, /\.error-state/);
});

test("client booking normalizes datetime-local values before sending to the backend", () => {
  const clientSource = readFrontend("src/apps/client/ClientApp.jsx");

  assert.match(clientSource, /function normalizeDateTimeForApi\(value\)/);
  assert.match(clientSource, /return String\(value \|\| ""\)\.replace\("T", " "\)\.slice\(0, 16\)/);
  assert.match(clientSource, /reserveTime: normalizeDateTimeForApi\(form\.reserveTime\)/);
  assert.doesNotMatch(clientSource, /reserveTime: form\.reserveTime/);
});

test("client booking sends a stable idempotency request id", () => {
  const clientSource = readFrontend("src/apps/client/ClientApp.jsx");

  assert.match(clientSource, /function createRequestId\(\)/);
  assert.match(clientSource, /crypto\.randomUUID/);
  assert.match(clientSource, /const requestIdRef = useRef\(createRequestId\(\)\)/);
  assert.match(clientSource, /requestId: requestIdRef\.current/);
  assert.match(clientSource, /disabled=\{submitting \|\| !canBook\}/);
});

test("client booking and lightweight interactions handle request failures", () => {
  const clientSource = readFrontend("src/apps/client/ClientApp.jsx");

  assert.match(clientSource, /async function toggleFavorite\(item\)[\s\S]*try \{[\s\S]*await request\(session, `\/user\/favorite\/\$\{item\.id\}`/);
  assert.match(clientSource, /async function toggleFavorite\(item\)[\s\S]*catch \(err\) \{[\s\S]*onToast\(err\.message\)/);
  assert.match(clientSource, /async function recordBrowse\(item\)[\s\S]*try \{[\s\S]*await request\(session, `\/user\/favorite\/browse\/\$\{item\.id\}`/);
  assert.match(clientSource, /async function recordBrowse\(item\)[\s\S]*catch \(err\) \{[\s\S]*void err/);
  assert.match(clientSource, /async function submit\(event\)[\s\S]*await request\(session, "\/user\/explore-order"/);
  assert.match(clientSource, /async function submit\(event\)[\s\S]*catch \(err\) \{[\s\S]*setError\(err\.message\)/);
});

test("client booking respects closed shop state before opening or submitting an order", () => {
  const clientSource = readFrontend("src/apps/client/ClientApp.jsx");

  assert.match(clientSource, /const shopOpen = Number\(snapshot\?\.shopStatus\) === 1/);
  assert.match(clientSource, /function requestCreateOrder\(item\)/);
  assert.match(clientSource, /showToast\("门店休息中，暂不可预约"\)/);
  assert.match(clientSource, /<ClientPage[\s\S]*canBook=\{shopOpen\}/);
  assert.match(clientSource, /function SpotGrid\(\{ rows, favoriteIds, onFavorite, onOrder, onDetail, onBrowse, canBook = true \}\)/);
  assert.match(clientSource, /disabled=\{!canBook\}/);
  assert.match(clientSource, /\{canBook \? "预约" : "休息中"\}/);
  assert.match(clientSource, /function ItemDetailDrawer\(\{ item, session, onClose, onOrder, canBook \}\)/);
  assert.match(clientSource, /function OrderDrawer\(\{ item, session, onClose, onToast, onReload, canBook \}\)/);
  assert.match(clientSource, /if \(!canBook\) \{[\s\S]*setError\("门店休息中，暂不可预约"\)[\s\S]*return/);
});

test("admin create drawer sends required fields for category, item, and package creation", () => {
  const adminSource = readFrontend("src/apps/admin/AdminApp.jsx");

  assert.match(adminSource, /const categoryOptions = drawer\.categories \|\| \[\]/);
  assert.match(adminSource, /categoryId: form\.categoryId \? Number\(form\.categoryId\) : undefined/);
  assert.match(adminSource, /sort: form\.sort \? Number\(form\.sort\) : 0/);
  assert.match(adminSource, /description: form\.description/);
  assert.match(adminSource, /image: form\.image/);
  assert.match(adminSource, /<select value=\{form\.categoryId\}/);
  assert.match(adminSource, /<textarea value=\{form\.description\}/);
  assert.match(adminSource, /const defaultContentImage = "\/assets\/images\/coffee\.webp"/);
  assert.match(adminSource, /placeholder=\{defaultContentImage\}/);
});

test("admin merchant form persists fields through the real backend contract", () => {
  const adminSource = readFrontend("src/apps/admin/AdminApp.jsx");
  const merchantSource = adminSource.slice(
    adminSource.indexOf("function MerchantPage"),
    adminSource.indexOf("function SearchableTablePage")
  );

  assert.match(adminSource, /request\(session, "\/admin\/merchant\/info", \{ method: "PUT"/);
  assert.doesNotMatch(adminSource, /request\(session, "\/admin\/merchant", \{ method: "PUT"/);
  assert.match(adminSource, /\{ key: "notice", label: "预约须知"/);
  assert.match(adminSource, /\{ key: "coverImage", label: "封面图片"/);
  assert.doesNotMatch(adminSource, /serviceArea/);
  assert.doesNotMatch(merchantSource, /form\.description/);
});

test("admin item drawer edits the same operational fields shown to users", () => {
  const adminSource = readFrontend("src/apps/admin/AdminApp.jsx");

  for (const field of ["durationMinutes", "capacity", "district", "address", "meetingPoint", "cancelPolicy"]) {
    assert.match(adminSource, new RegExp(`${field}: drawer\\.record\\?\\.${field}`));
    assert.match(adminSource, new RegExp(`${field}: form\\.${field}`));
  }
  assert.match(adminSource, /<span>时长\(分钟\)<\/span>/);
  assert.match(adminSource, /<span>可预约容量<\/span>/);
  assert.doesNotMatch(adminSource, /<span>已预约人数<\/span>/);
  assert.doesNotMatch(adminSource, /booked: form\.booked/);
  assert.match(adminSource, /<span>商圈<\/span>/);
  assert.match(adminSource, /<span>集合点<\/span>/);
  assert.match(adminSource, /<span>取消规则<\/span>/);
});

test("admin order and review pages expose real backend operations", () => {
  const adminSource = readFrontend("src/apps/admin/AdminApp.jsx");

  assert.match(adminSource, /function OrdersPage\(\{ rows, total, pager, onPageChange, filter, onFilterChange, session, canAdmin, requestConfirm, onToast, onReload \}\)/);
  assert.match(adminSource, /function requestOrderStatusChange\(row, status, title, message\)/);
  assert.match(adminSource, /requestConfirm\(\{[\s\S]*title,[\s\S]*message,[\s\S]*confirmText: "确认操作"/);
  assert.match(adminSource, /request\(session, `\/admin\/explore-order\/status\?id=\$\{row\.id\}&status=\$\{status\}`, \{ method: "PUT" \}\)/);
  assert.match(adminSource, /requestOrderStatusChange\(row, 1, "确认预约", `确认接受「\$\{row\.itemName\}」的预约吗？`\)/);
  assert.match(adminSource, /requestOrderStatusChange\(row, 2, "标记完成", `确认将「\$\{row\.itemName\}」标记为已完成吗？`\)/);
  assert.match(adminSource, /requestOrderStatusChange\(row, 3, "取消预约", `确认取消「\$\{row\.itemName\}」的预约吗？`\)/);

  assert.match(adminSource, /function ReviewsPage\(\{ rows, total, pager, onPageChange, filter, onFilterChange, session, canAdmin, requestConfirm, onToast, onReload \}\)/);
  assert.match(adminSource, /function deleteReview\(row\)/);
  assert.match(adminSource, /requestConfirm\(\{[\s\S]*title: "删除评价"/);
  assert.match(adminSource, /request\(session, `\/admin\/review\?ids=\$\{row\.id\}`, \{ method: "DELETE" \}\)/);
  assert.match(adminSource, /const \[replyTarget, setReplyTarget\] = useState\(null\)/);
  assert.match(adminSource, /async function replyReview\(row, content\)/);
  assert.match(adminSource, /function ReviewReplyDrawer\(\{ review, onClose, onSubmit \}\)/);
  assert.match(adminSource, /<textarea/);
  assert.match(adminSource, /setReplyTarget\(row\)/);
  assert.match(adminSource, /<ReviewReplyDrawer review=\{replyTarget\} onClose=\{\(\) => setReplyTarget\(null\)\} onSubmit=\{\(content\) => replyReview\(replyTarget, content\)\} \/>/);
  assert.doesNotMatch(adminSource, /window\.prompt\("商家回复"/);
  assert.match(adminSource, /request\(session, "\/admin\/review\/reply", \{/);
  assert.match(adminSource, /method: "PUT"/);
  assert.match(adminSource, /replyContent: content\.trim\(\)/);
  assert.match(adminSource, /row\.replyTime/);
});

test("admin order action buttons follow the order lifecycle", () => {
  const adminSource = readFrontend("src/apps/admin/AdminApp.jsx");

  assert.match(adminSource, /function orderActionsForStatus\(value\)/);
  assert.match(adminSource, /canConfirm: status === 0/);
  assert.match(adminSource, /canComplete: status === 1/);
  assert.match(adminSource, /canCancel: status === 0 \|\| status === 1/);
  assert.match(adminSource, /const orderActions = orderActionsForStatus\(row\.status\)/);
  assert.match(adminSource, /orderActions\.canConfirm &&/);
  assert.match(adminSource, /orderActions\.canComplete &&/);
  assert.match(adminSource, /orderActions\.canCancel &&/);
  assert.match(adminSource, /!orderActions\.canConfirm && !orderActions\.canComplete && !orderActions\.canCancel/);
  assert.match(adminSource, /无需操作/);
});

test("admin order rows expose operational detail drawer", () => {
  const adminSource = readFrontend("src/apps/admin/AdminApp.jsx");

  assert.match(adminSource, /const \[detailOrder, setDetailOrder\] = useState\(null\)/);
  assert.match(adminSource, /function OrderDetailDrawer\(\{ order, onClose \}\)/);
  assert.match(adminSource, /setDetailOrder\(row\)/);
  assert.match(adminSource, /<OrderDetailDrawer order=\{detailOrder\} onClose=\{\(\) => setDetailOrder\(null\)\} \/>/);
  for (const field of ["contactName", "contactPhone", "peopleCount", "reserveTime", "remark"]) {
    assert.match(adminSource, new RegExp(`order\\.${field}`));
  }
});

test("admin order page sends keyword and lifecycle status filters to the backend", () => {
  const adminSource = readFrontend("src/apps/admin/AdminApp.jsx");

  assert.match(adminSource, /function orderFilterParams\(filter\)/);
  assert.match(adminSource, /return \{[\s\S]*keyword: value[\s\S]*status: filter\.status/);
  assert.match(adminSource, /request\(session, pageUrl\("\/admin\/explore-order\/page", pagination\.order, orderFilterParams\(filters\.order\)\)\)/);
  assert.match(adminSource, /orderFilter: filters\.order/);
  assert.match(adminSource, /<OrdersPage[\s\S]*filter=\{snapshot\.orderFilter\}[\s\S]*onFilterChange=\{\(next\) => onFilterChange\("order", next\)\}/);
  assert.match(adminSource, /function OrdersPage\(\{ rows, total, pager, onPageChange, filter, onFilterChange, session, canAdmin, requestConfirm, onToast, onReload \}\)/);
  assert.match(adminSource, /const \[draftFilter, setDraftFilter\] = useState\(filter\)/);
  assert.match(adminSource, /useEffect\(\(\) => setDraftFilter\(filter\), \[filter\]\)/);
  assert.match(adminSource, /function submitOrderFilters\(event\)/);
  assert.match(adminSource, /onFilterChange\(\{ keyword: draftFilter\.keyword\.trim\(\), status: draftFilter\.status \}\)/);
  assert.match(adminSource, /placeholder="订单号、用户、联系人、手机号"/);
  assert.match(adminSource, /<select aria-label="订单状态" value=\{draftFilter\.status\}/);
  assert.match(adminSource, /<option value="0">待确认<\/option>/);
  assert.match(adminSource, /<option value="1">已确认<\/option>/);
  assert.match(adminSource, /<option value="2">已完成<\/option>/);
  assert.match(adminSource, /<option value="3">已取消<\/option>/);
  assert.match(adminSource, /function resetOrderFilters\(\)[\s\S]*setDraftFilter\(\{ keyword: "", status: "" \}\)[\s\S]*onFilterChange\(\{ keyword: "", status: "" \}\)/);
  assert.match(adminSource, /<OrderTable[\s\S]*rows=\{rows\}/);
  assert.doesNotMatch(adminSource, /const filteredOrders = useMemo\(\(\) => \{/);
  assert.doesNotMatch(adminSource, /本页筛选 \{orderVisibleCount\}/);
});

test("admin review page sends keyword, rating, and reply state filters to the backend", () => {
  const adminSource = readFrontend("src/apps/admin/AdminApp.jsx");

  assert.match(adminSource, /function reviewFilterParams\(filter\)/);
  assert.match(adminSource, /return \{[\s\S]*keyword: value[\s\S]*rating: filter\.rating[\s\S]*replyState: filter\.replyState/);
  assert.match(adminSource, /request\(session, pageUrl\("\/admin\/review\/page", pagination\.review, reviewFilterParams\(filters\.review\)\)\)/);
  assert.match(adminSource, /reviewFilter: filters\.review/);
  assert.match(adminSource, /<ReviewsPage[\s\S]*filter=\{snapshot\.reviewFilter\}[\s\S]*onFilterChange=\{\(next\) => onFilterChange\("review", next\)\}/);
  assert.match(adminSource, /function ReviewsPage\(\{ rows, total, pager, onPageChange, filter, onFilterChange, session, canAdmin, requestConfirm, onToast, onReload \}\)/);
  assert.match(adminSource, /const \[draftFilter, setDraftFilter\] = useState\(filter\)/);
  assert.match(adminSource, /useEffect\(\(\) => setDraftFilter\(filter\), \[filter\]\)/);
  assert.match(adminSource, /function submitReviewFilters\(event\)/);
  assert.match(adminSource, /onFilterChange\(\{ keyword: draftFilter\.keyword\.trim\(\), rating: draftFilter\.rating, replyState: draftFilter\.replyState \}\)/);
  assert.match(adminSource, /placeholder="项目、用户、评价内容"/);
  assert.match(adminSource, /<select aria-label="评价评分" value=\{draftFilter\.rating\}/);
  assert.match(adminSource, /<select aria-label="回复状态" value=\{draftFilter\.replyState\}/);
  assert.match(adminSource, /<option value="unreplied">未回复<\/option>/);
  assert.match(adminSource, /<option value="replied">已回复<\/option>/);
  assert.match(adminSource, /function resetReviewFilters\(\)[\s\S]*setDraftFilter\(\{ keyword: "", rating: "", replyState: "" \}\)[\s\S]*onFilterChange\(\{ keyword: "", rating: "", replyState: "" \}\)/);
  assert.match(adminSource, /rows\.length \? rows\.map/);
  assert.match(adminSource, /暂无匹配评价/);
  assert.doesNotMatch(adminSource, /const filteredReviews = useMemo\(\(\) => \{/);
  assert.doesNotMatch(adminSource, /本页筛选 \{reviewVisibleCount\}/);
});

test("admin export actions create authenticated asynchronous jobs", () => {
  const adminSource = readFrontend("src/apps/admin/AdminApp.jsx");
  const authSource = readFrontend("src/lib/auth.js");
  const viteSource = readFrontend("vite.config.js");

  assert.match(authSource, /export async function downloadFile\(session, path, fallbackName = "export\.csv", fallbackContent = ""\)/);
  assert.match(authSource, /headers\[AUTH_CONFIG\[session\.scope\]\.tokenHeader\] = session\.token/);
  assert.match(authSource, /responseFilename\(response, fallbackName\)/);
  assert.match(authSource, /anchor\.download = filename/);
  assert.match(authSource, /URL\.createObjectURL\(blob\)/);
  assert.match(authSource, /URL\.revokeObjectURL\(url\)/);

  assert.match(adminSource, /async function createExportJob\(session, exportType, fileFormat, filters, onToast\)/);
  assert.match(adminSource, /request\(session, "\/admin\/export-jobs", \{[\s\S]*method: "POST"/);
  assert.match(adminSource, /exportType="ORDER"/);
  assert.match(adminSource, /exportType="REVIEW"/);
  assert.match(adminSource, /exportType="USER"/);
  assert.match(adminSource, /exportType="OPERATION_LOG"/);
  assert.match(adminSource, /<option value="XLSX">XLSX<\/option>/);
  assert.match(adminSource, /<option value="CSV">CSV<\/option>/);
  assert.doesNotMatch(adminSource, /\/admin\/export\//);
  assert.match(viteSource, /console\/export-jobs\.html/);
});

test("admin export task center polls, cancels, retries, and downloads jobs", () => {
  const adminSource = readFrontend("src/apps/admin/AdminApp.jsx");

  assert.match(adminSource, /function ExportJobsPage\(\{ session, requestConfirm, onToast \}\)/);
  assert.match(adminSource, /const delays = \[1500, 2500, 4000, 7000, 10000\]/);
  assert.match(adminSource, /return \(\) => window\.clearTimeout\(timer\)/);
  assert.match(adminSource, /`\/admin\/export-jobs\/\$\{job\.jobId\}\/cancel`/);
  assert.match(adminSource, /`\/admin\/export-jobs\/\$\{job\.jobId\}\/retry`/);
  assert.match(adminSource, /downloadFile\(session, `\/admin\/export-jobs\/\$\{job\.jobId\}\/download`/);
  assert.match(adminSource, /row\.status === "SUCCEEDED"/);
  assert.match(adminSource, /row\.status === "FAILED"/);
  assert.match(adminSource, /aria-label=\{`导出进度\$\{Number\(row\.progress \|\| 0\)\}%`\}/);
  assert.match(adminSource, /stats\.recentFailureJobId/);
  assert.match(adminSource, /stats\.recentFailureErrorCode/);
});

test("admin dashboard exposes a real shop status toggle", () => {
  const adminSource = readFrontend("src/apps/admin/AdminApp.jsx");

  assert.doesNotMatch(adminSource, /演示口径/);
  assert.match(adminSource, /function Dashboard\(\{ snapshot, session, onToast, onReload \}\)/);
  assert.match(adminSource, /async function toggleShopStatus\(\)/);
  assert.match(adminSource, /const nextStatus = Number\(snapshot\.shopStatus\) === 1 \? 0 : 1/);
  assert.match(adminSource, /`\/admin\/shop\/\$\{nextStatus\}`/);
  assert.match(adminSource, /method: "PUT"/);
  assert.match(adminSource, /onReload\(\)/);
});

test("admin direct actions surface request failures", () => {
  const adminSource = readFrontend("src/apps/admin/AdminApp.jsx");
  const dashboardSource = adminSource.slice(
    adminSource.indexOf("function Dashboard"),
    adminSource.indexOf("function TrendChart")
  );
  const merchantSource = adminSource.slice(
    adminSource.indexOf("function MerchantPage"),
    adminSource.indexOf("function SearchableTablePage")
  );

  assert.match(dashboardSource, /async function toggleShopStatus\(\)[\s\S]*try \{[\s\S]*await request\(session, `\/admin\/shop\/\$\{nextStatus\}`/);
  assert.match(dashboardSource, /async function toggleShopStatus\(\)[\s\S]*catch \(err\) \{[\s\S]*onToast\(err\.message\)/);
  assert.match(dashboardSource, /async function toggleShopStatus\(\)[\s\S]*finally \{[\s\S]*setSavingStatus\(false\)/);
  assert.match(merchantSource, /const \[error, setError\] = useState\(""\)/);
  assert.match(merchantSource, /async function submit\(event\)[\s\S]*setError\(""\)[\s\S]*try \{[\s\S]*await request\(session, "\/admin\/merchant\/info"/);
  assert.match(merchantSource, /async function submit\(event\)[\s\S]*catch \(err\) \{[\s\S]*setError\(err\.message\)/);
  assert.match(merchantSource, /\{error \? <div className="form-error">\{error\}<\/div> : null\}/);
});

test("admin topbar keeps developer API settings out of the primary chrome", () => {
  const adminSource = readFrontend("src/apps/admin/AdminApp.jsx");
  const topbarSource = adminSource.slice(
    adminSource.indexOf("<header className=\"topbar\">"),
    adminSource.indexOf("</header>", adminSource.indexOf("<header className=\"topbar\">"))
  );

  assert.match(adminSource, /Settings2/);
  assert.match(topbarSource, /<details className="api-settings">/);
  assert.match(topbarSource, /<summary className="icon-button" aria-label="接口设置" title="接口设置">/);
  assert.match(topbarSource, /<Settings2 size=\{17\} \/>/);
  assert.doesNotMatch(topbarSource, /<summary>接口设置<\/summary>/);
  assert.doesNotMatch(topbarSource, /<input aria-label="接口地址"/);
});

test("admin category and package rows can be edited through the drawer", () => {
  const adminSource = readFrontend("src/apps/admin/AdminApp.jsx");

  assert.match(adminSource, /function RowActions\(\{ onEdit, onToggleStatus, onDelete, status \}\)/);
  assert.match(adminSource, /label: "操作"/);
  assert.match(adminSource, /onOpenDrawer\(\{ type: "category", mode: "edit", title: "编辑分类", record: row, categories: rows \}\)/);
  assert.match(adminSource, /<RowActions onEdit=\{\(\) => openPackageEditor\(row\)\}/);
  assert.match(adminSource, /const isEdit = drawer\.mode === "edit"/);
  assert.match(adminSource, /method: isEdit \? "PUT" : "POST"/);
  assert.match(adminSource, /id: isEdit \? drawer\.record\.id : undefined/);
});

test("admin category and package rows can be deleted with confirmation", () => {
  const adminSource = readFrontend("src/apps/admin/AdminApp.jsx");

  assert.match(adminSource, /function RowActions\(\{ onEdit, onToggleStatus, onDelete, status \}\)/);
  assert.match(adminSource, /onDelete/);
  assert.match(adminSource, /function ConfirmDialog\(\{ confirm, onCancel, onConfirm \}\)/);
  assert.match(adminSource, /const \[confirm, setConfirm\] = useState\(null\)/);
  assert.match(adminSource, /requestConfirm\(\{[\s\S]*title: "删除分类"/);
  assert.match(adminSource, /message: `确认删除分类「\$\{row\.name\}」吗？`/);
  assert.match(adminSource, /request\(session, `\/admin\/category\?id=\$\{row\.id\}`, \{ method: "DELETE" \}\)/);
  assert.match(adminSource, /requestConfirm\(\{[\s\S]*title: "删除套餐"/);
  assert.match(adminSource, /message: `确认删除套餐「\$\{row\.name\}」吗？`/);
  assert.match(adminSource, /request\(session, `\/admin\/explore-package\?ids=\$\{row\.id\}`, \{ method: "DELETE" \}\)/);
  assert.match(adminSource, /onReload\(\)/);
  assert.doesNotMatch(adminSource, /window\.confirm/);
});

test("admin category and package rows can be enabled or disabled", () => {
  const adminSource = readFrontend("src/apps/admin/AdminApp.jsx");

  assert.match(adminSource, /function RowActions\(\{ onEdit, onToggleStatus, onDelete, status \}\)/);
  assert.match(adminSource, /Number\(status\) === 1 \? "停用" : "启用"/);
  assert.match(adminSource, /async function toggleCategoryStatus\(row\)/);
  assert.match(adminSource, /request\(session, `\/admin\/category\/status\/\$\{nextStatus\}\?id=\$\{row\.id\}`, \{ method: "POST" \}\)/);
  assert.match(adminSource, /async function togglePackageStatus\(row\)/);
  assert.match(adminSource, /request\(session, `\/admin\/explore-package\/status\/\$\{nextStatus\}\?id=\$\{row\.id\}`, \{ method: "POST" \}\)/);
  assert.match(adminSource, /onToggleStatus=\{\(\) => toggleCategoryStatus\(row\)\}/);
  assert.match(adminSource, /onToggleStatus=\{\(\) => togglePackageStatus\(row\)\}/);
});

test("admin item rows can be edited, deleted, enabled, and disabled", () => {
  const adminSource = readFrontend("src/apps/admin/AdminApp.jsx");

  assert.match(adminSource, /<ItemsPage rows=\{snapshot\.items\} total=\{snapshot\.itemTotal\} pager=\{snapshot\.itemPager\} onPageChange=\{\(next\) => onPageChange\("item", next\)\} searchKeyword=\{snapshot\.itemFilter\.keyword\} onSearch=\{\(keyword\) => onSearch\("item", keyword\)\} categories=\{snapshot\.categoryOptions\} session=\{session\} canAdmin=\{session\.role === "ADMIN"\} onOpenDrawer=\{onOpenDrawer\} requestConfirm=\{requestConfirm\} onToast=\{onToast\} onReload=\{onReload\} \/>/);
  assert.match(adminSource, /function ItemsPage\(\{ rows, total, pager, onPageChange, searchKeyword, onSearch, categories, session, canAdmin, onOpenDrawer, requestConfirm, onToast, onReload \}\)/);
  assert.match(adminSource, /async function openItemEditor\(row\)/);
  assert.match(adminSource, /request\(session, `\/admin\/explore-item\/\$\{row\.id\}`\)/);
  assert.match(adminSource, /onOpenDrawer\(\{ type: "item", mode: "edit", title: "编辑项目", record: \{ \.\.\.row, \.\.\.detail \}, categories \}\)/);
  assert.match(adminSource, /function deleteItem\(row\)/);
  assert.match(adminSource, /requestConfirm\(\{[\s\S]*title: "删除项目"/);
  assert.match(adminSource, /request\(session, `\/admin\/explore-item\?ids=\$\{row\.id\}`, \{ method: "DELETE" \}\)/);
  assert.match(adminSource, /async function toggleItemStatus\(row\)/);
  assert.match(adminSource, /request\(session, `\/admin\/explore-item\/status\/\$\{nextStatus\}\?id=\$\{row\.id\}`, \{ method: "POST" \}\)/);
  assert.match(adminSource, /<RowActions onEdit=\{\(\) => openItemEditor\(row\)\} onToggleStatus=\{\(\) => toggleItemStatus\(row\)\} onDelete=\{\(\) => deleteItem\(row\)\} status=\{row\.status\} \/>/);
});

test("admin item edit keeps existing tag relations", () => {
  const adminSource = readFrontend("src/apps/admin/AdminApp.jsx");

  assert.match(adminSource, /const itemTags = drawer\.record\?\.tags \|\| \[\]/);
  assert.match(adminSource, /tags: drawer\.type === "item" \? itemTags : undefined/);
});

test("admin user management shows real engagement statistics", () => {
  const adminSource = readFrontend("src/apps/admin/AdminApp.jsx");

  assert.match(adminSource, /<UsersPage rows=\{snapshot\.users\} total=\{snapshot\.userTotal\} pager=\{snapshot\.userPager\} onPageChange=\{\(next\) => onPageChange\("user", next\)\} searchKeyword=\{snapshot\.userFilter\.keyword\} onSearch=\{\(keyword\) => onSearch\("user", keyword\)\} session=\{session\} onOpenDrawer=\{onOpenDrawer\} requestConfirm=\{requestConfirm\} onToast=\{onToast\} onReload=\{onReload\} \/>/);
  assert.match(adminSource, /function UsersPage\(\{ rows, total, pager, onPageChange, searchKeyword, onSearch, session, onOpenDrawer, requestConfirm, onToast, onReload \}\)/);
  assert.match(adminSource, /key: "orderCount", label: "预约数"/);
  assert.match(adminSource, /key: "browseCount", label: "浏览数"/);
  assert.match(adminSource, /key: "favoriteCount", label: "收藏数"/);
  assert.match(adminSource, /Number\(row\.orderCount \|\| 0\)/);
  assert.match(adminSource, /Number\(row\.browseCount \|\| 0\)/);
  assert.match(adminSource, /Number\(row\.favoriteCount \|\| 0\)/);
  assert.match(adminSource, /function UserDetailDrawer\(\{ user, onClose \}\)/);
  assert.match(adminSource, /onOpenDrawer\(\{ type: "user", title: "用户详情", record: row \}\)/);
  assert.match(adminSource, /async function openUserEditor\(row\)/);
  assert.match(adminSource, /request\(session, `\/admin\/user-manage\/\$\{row\.id\}`\)/);
  assert.match(adminSource, /onOpenDrawer\(\{ type: "user", mode: "edit", title: "编辑用户", record: \{ \.\.\.row, \.\.\.detail \} \}\)/);
  assert.match(adminSource, /<RowActions onEdit=\{\(\) => openUserEditor\(row\)\}/);
  assert.match(adminSource, /request\(session, `\/admin\/user-manage\/\$\{drawer\.record\.id\}`, \{/);
  assert.match(adminSource, /idNumber: form\.idNumber/);
  assert.match(adminSource, /avatar: form\.avatar/);
  assert.match(adminSource, /function resetUserPassword\(row\)/);
  assert.match(adminSource, /title: "重置密码"/);
  assert.match(adminSource, /message: `确认将用户「\$\{row\.name \|\| row\.phone\}」的密码重置为 123456 吗？`/);
  assert.match(adminSource, /request\(session, `\/admin\/user-manage\/\$\{row\.id\}\/password\/reset`, \{ method: "PUT" \}\)/);
  assert.match(adminSource, /用户密码已重置为 123456/);
  assert.match(adminSource, /drawer\.type === "user"/);
});

test("admin user management can enable and disable client accounts", () => {
  const adminSource = readFrontend("src/apps/admin/AdminApp.jsx");

  assert.match(adminSource, /async function toggleUserStatus\(row\)/);
  assert.match(adminSource, /const nextStatus = Number\(row\.status\) === 1 \? 0 : 1/);
  assert.match(adminSource, /request\(session, `\/admin\/user-manage\/status\/\$\{nextStatus\}\?id=\$\{row\.id\}`, \{ method: "POST" \}\)/);
  assert.match(adminSource, /onToast\(nextStatus === 1 \? "用户已启用" : "用户已禁用"\)/);
  assert.match(adminSource, /\{ key: "status", label: "状态", render: \(row\) => <StatusPill status=\{enabledStatus\(row\.status\)\} \/> \}/);
  assert.match(adminSource, /<RowActions onEdit=\{\(\) => openUserEditor\(row\)\} onToggleStatus=\{\(\) => toggleUserStatus\(row\)\} onDelete=\{null\} status=\{row\.status\} \/>/);
});

test("admin employee rows support management without locking the default admin", () => {
  const adminSource = readFrontend("src/apps/admin/AdminApp.jsx");

  assert.match(adminSource, /<EmployeesPage rows=\{snapshot\.employees\} total=\{snapshot\.employeeTotal\} pager=\{snapshot\.employeePager\} onPageChange=\{\(next\) => onPageChange\("employee", next\)\} searchKeyword=\{snapshot\.employeeFilter\.keyword\} onSearch=\{\(keyword\) => onSearch\("employee", keyword\)\} session=\{session\} onOpenDrawer=\{onOpenDrawer\} requestConfirm=\{requestConfirm\} onToast=\{onToast\} onReload=\{onReload\} \/>/);
  assert.match(adminSource, /function EmployeesPage\(\{ rows, total, pager, onPageChange, searchKeyword, onSearch, session, onOpenDrawer, requestConfirm, onToast, onReload \}\)/);
  assert.match(adminSource, /async function openEmployeeEditor\(row\)/);
  assert.match(adminSource, /request\(session, `\/admin\/employee\/\$\{row\.id\}`\)/);
  assert.match(adminSource, /onOpenDrawer\(\{ type: "employee", mode: "edit", title: "编辑员工", record: \{ \.\.\.row, \.\.\.detail \} \}\)/);
  assert.match(adminSource, /async function toggleEmployeeStatus\(row\)/);
  assert.match(adminSource, /request\(session, `\/admin\/employee\/status\/\$\{nextStatus\}\?id=\$\{row\.id\}`, \{ method: "POST" \}\)/);
  assert.match(adminSource, /function deleteEmployee\(row\)/);
  assert.match(adminSource, /requestConfirm\(\{[\s\S]*title: "删除员工"/);
  assert.match(adminSource, /request\(session, `\/admin\/employee\?id=\$\{row\.id\}`, \{ method: "DELETE" \}\)/);
  assert.match(adminSource, /const isDefaultAdmin = Number\(row\.id\) === 1/);
  assert.match(adminSource, /onToggleStatus=\{isDefaultAdmin \? null : \(\) => toggleEmployeeStatus\(row\)\}/);
  assert.match(adminSource, /onDelete=\{isDefaultAdmin \? null : \(\) => deleteEmployee\(row\)\}/);
  assert.match(adminSource, /phone: drawer\.record\?\.phone \|\| ""/);
  assert.match(adminSource, /username: drawer\.record\?\.username \|\| ""/);
});

test("admin employee drawer does not keep a hidden password field", () => {
  const adminSource = readFrontend("src/apps/admin/AdminApp.jsx");
  const drawerSource = adminSource.slice(
    adminSource.indexOf("function AdminDrawer"),
    adminSource.indexOf("export function AdminApp")
  );

  assert.doesNotMatch(drawerSource, /password: "123456"/);
  assert.doesNotMatch(drawerSource, /form\.password/);
});

test("client browse history records item clicks and refreshes the history snapshot", () => {
  const clientSource = readFrontend("src/apps/client/ClientApp.jsx");

  assert.match(clientSource, /async function recordBrowse\(item\)/);
  assert.match(clientSource, /await request\(session, `\/user\/favorite\/browse\/\$\{item\.id\}`, \{ method: "POST" \}\)/);
  assert.match(clientSource, /onReload\(\)/);
  assert.match(clientSource, /<SpotGrid rows=\{filtered\} favoriteIds=\{favoriteIds\} onFavorite=\{toggleFavorite\} onOrder=\{onOrder\} onDetail=\{onDetail\} onBrowse=\{recordBrowse\} canBook=\{canBook\} \/>/);
  assert.match(clientSource, /function HistoryPage\(\{ rows, onOrder, onDetail, canBook \}\)/);
  assert.match(clientSource, /<SpotGrid rows=\{rows\} favoriteIds=\{new Set\(\)\} onFavorite=\{null\} onOrder=\{onOrder\} onDetail=\{onDetail\} canBook=\{canBook\} \/>/);
  assert.match(clientSource, /<ProtectedPage session=\{session\}><HistoryPage rows=\{snapshot\.history\} onOrder=\{onOrder\} onDetail=\{onDetail\} canBook=\{canBook\} \/><\/ProtectedPage>/);
});

test("client favorites page can remove saved items through the real API", () => {
  const clientSource = readFrontend("src/apps/client/ClientApp.jsx");

  assert.match(clientSource, /<ProtectedPage session=\{session\}><FavoritesPage rows=\{snapshot\.favorites\} session=\{session\} onOrder=\{onOrder\} onDetail=\{onDetail\} onToast=\{onToast\} onReload=\{onReload\} canBook=\{canBook\} \/><\/ProtectedPage>/);
  assert.match(clientSource, /function FavoritesPage\(\{ rows, session, onOrder, onDetail, onToast, onReload, canBook \}\)/);
  assert.match(clientSource, /async function removeFavorite\(item\)/);
  assert.match(clientSource, /await request\(session, `\/user\/favorite\/\$\{item\.id\}`, \{ method: "DELETE" \}\)/);
  assert.match(clientSource, /onToast\("已取消收藏"\)/);
  assert.match(clientSource, /onReload\(\)/);
  assert.match(clientSource, /<SpotGrid rows=\{rows\} favoriteIds=\{new Set\(rows\.map\(\(item\) => String\(item\.id\)\)\)\} onFavorite=\{removeFavorite\} onOrder=\{onOrder\} onDetail=\{onDetail\} canBook=\{canBook\} \/>/);
});

test("client item cards open a detail drawer before booking", () => {
  const clientSource = readFrontend("src/apps/client/ClientApp.jsx");
  const cssSource = readFrontend("src/styles/app.css");

  assert.match(clientSource, /const \[itemDetailTarget, setItemDetailTarget\] = useState\(null\)/);
  assert.match(clientSource, /function openItemDetail\(item\)/);
  assert.match(clientSource, /onDetail=\{openItemDetail\}/);
  assert.match(clientSource, /<ItemDetailDrawer item=\{itemDetailTarget\} session=\{session\} onClose=\{\(\) => setItemDetailTarget\(null\)\} onOrder=\{startOrderFromDetail\} canBook=\{shopOpen\} \/>/);
  assert.match(clientSource, /function ItemDetailDrawer\(\{ item, session, onClose, onOrder, canBook \}\)/);
  assert.match(clientSource, /aria-label="项目详情"/);
  assert.match(clientSource, /查看详情/);
  assert.match(clientSource, /durationText\(item\.durationMinutes\)/);
  assert.match(clientSource, /remainingSlots\(item\)/);
  assert.match(clientSource, /item\.meetingPoint/);
  assert.match(clientSource, /item\.cancelPolicy/);
  assert.match(clientSource, /onClick=\{\(\) => \{ if \(canBook\) onOrder\(item\); \}\}/);
  assert.match(clientSource, /onClick=\{\(\) => \{ onBrowse\?\.\(item\); onDetail\(item\); \}\}/);
  assert.match(cssSource, /\.detail-hero/);
  assert.match(cssSource, /\.detail-meta-list/);
  assert.match(cssSource, /\.detail-cover/);
});

test("client package detail loads and displays package items", () => {
  const clientSource = readFrontend("src/apps/client/ClientApp.jsx");
  const demoSource = fs.readFileSync(path.join(frontendRoot, "../src/main/resources/static/assets/demo-data.js"), "utf8");

  assert.match(clientSource, /function ItemDetailDrawer\(\{ item, session, onClose, onOrder, canBook \}\)/);
  assert.match(clientSource, /const \[packageItems, setPackageItems\] = useState\(\[\]\)/);
  assert.match(clientSource, /request\(session, `\/user\/explore-package\/items\/\$\{item\.id\}`\)/);
  assert.match(clientSource, /套餐包含项目/);
  assert.match(clientSource, /packageItems\.map\(\(row\) =>/);
  assert.match(clientSource, /row\.itemName \|\| row\.name/);
  assert.match(clientSource, /×\{row\.copies \|\| 1\}/);
  assert.match(clientSource, /<ItemDetailDrawer item=\{itemDetailTarget\} session=\{session\} onClose=\{\(\) => setItemDetailTarget\(null\)\} onOrder=\{startOrderFromDetail\} canBook=\{shopOpen\} \/>/);
  assert.match(demoSource, /path\.startsWith\("\/user\/explore-package\/items\/"\)/);
  assert.match(demoSource, /packageEntity\?\.packageItems \|\| \[\]/);
});

test("client order status text covers cancellation instead of showing it as pending", () => {
  const clientSource = readFrontend("src/apps/client/ClientApp.jsx");

  assert.match(clientSource, /function clientOrderStatus\(value\)/);
  assert.match(clientSource, /if \(status === 3\) return "已取消"/);
  assert.match(clientSource, /clientOrderStatus\(order\.status\)/);
});

test("client completed item and package orders can submit reviews", () => {
  const clientSource = readFrontend("src/apps/client/ClientApp.jsx");

  assert.match(clientSource, /function canReviewOrder\(order\)/);
  assert.match(clientSource, /Number\(order\.status\) === 2/);
  assert.doesNotMatch(clientSource, /Number\(order\.orderType \|\| 1\) === 1/);
  assert.match(clientSource, /!order\.hasReview/);
  assert.match(clientSource, /setReviewTarget/);
  assert.match(clientSource, /function ReviewDrawer\(\{ order, session, onClose, onToast, onReload \}\)/);
  assert.match(clientSource, /request\(session, "\/user\/review", \{/);
  assert.match(clientSource, /orderId: order\.id/);
  assert.match(clientSource, /itemId: order\.itemId/);
  assert.match(clientSource, /rating: Number\(form\.rating\)/);
  assert.match(clientSource, /content: form\.content/);
  assert.match(clientSource, /提交评价/);
});

test("client pending or confirmed orders can be canceled", () => {
  const clientSource = readFrontend("src/apps/client/ClientApp.jsx");

  assert.match(clientSource, /function canCancelOrder\(order\)/);
  assert.match(clientSource, /Number\(order\.status\) === 0 \|\| Number\(order\.status\) === 1/);
  assert.match(clientSource, /const \[cancelRequest, setCancelRequest\] = useState\(null\)/);
  assert.match(clientSource, /function requestCancelOrder\(order, onSuccess\)/);
  assert.match(clientSource, /async function confirmCancelOrder\(\)/);
  assert.match(clientSource, /request\(session, `\/user\/explore-order\/\$\{order\.id\}\/cancel`, \{ method: "PUT" \}\)/);
  assert.match(clientSource, /cancelRequest\.onSuccess\?\.\(\)/);
  assert.match(clientSource, /function ClientConfirmDialog\(\{ request: confirmRequest, onCancel, onConfirm \}\)/);
  assert.match(clientSource, /<ClientConfirmDialog request=\{cancelRequest\} onCancel=\{\(\) => setCancelRequest\(null\)\} onConfirm=\{confirmCancelOrder\} \/>/);
  assert.doesNotMatch(clientSource, /window\.confirm/);
  assert.match(clientSource, /onCancel=\{requestCancelOrder\}/);
  assert.match(clientSource, /<ProtectedPage session=\{session\}><OrdersPage rows=\{snapshot\.orders\} onReview=\{onReview\} onCancel=\{onCancel\} \/><\/ProtectedPage>/);
  assert.match(clientSource, /function OrdersPage\(\{ rows, onReview, onCancel \}\)/);
  assert.match(clientSource, /canCancelOrder\(order\)/);
  assert.match(clientSource, /取消预约/);
});

test("client orders expose a complete detail drawer", () => {
  const clientSource = readFrontend("src/apps/client/ClientApp.jsx");

  assert.match(clientSource, /const \[detailTarget, setDetailTarget\] = useState\(null\)/);
  assert.match(clientSource, /function ClientOrderDetailDrawer\(\{ order, onClose, onCancel, onReview \}\)/);
  assert.match(clientSource, /aria-label="预约详情"/);
  assert.match(clientSource, /联系人：\{order\.contactName \|\| "-"\}/);
  assert.match(clientSource, /手机号：\{order\.contactPhone \|\| "-"\}/);
  assert.match(clientSource, /人数/);
  assert.match(clientSource, /备注：\{order\.remark \|\| "-"\}/);
  assert.match(clientSource, /创建时间：\{dateText\(order\.createTime\)\}/);
  assert.match(clientSource, /async function cancelFromDetail\(order\)/);
  assert.match(clientSource, /function reviewFromDetail\(order\)/);
  assert.match(clientSource, /onClick=\{\(\) => setDetailTarget\(order\)\}/);
  assert.match(clientSource, /<ClientOrderDetailDrawer order=\{detailTarget\} onClose=\{\(\) => setDetailTarget\(null\)\} onCancel=\{cancelFromDetail\} onReview=\{reviewFromDetail\} \/>/);
  assert.match(clientSource, /canReviewOrder\(order\)[\s\S]*提交评价/);
  assert.match(clientSource, /canCancelOrder\(order\)[\s\S]*取消预约/);
});

test("client order list receives review state from the backend", () => {
  const mapperSource = fs.readFileSync(path.join(frontendRoot, "../src/main/resources/mapper/ExploreOrderMapper.xml"), "utf8");
  const orderVoSource = fs.readFileSync(path.join(frontendRoot, "../../explorer-model/src/main/java/com/localexplorer/vo/ExploreOrderVO.java"), "utf8");

  assert.match(mapperSource, /exists\s*\(\s*select 1 from review r where r\.order_id = o\.id\s*\) as has_review/i);
  assert.match(orderVoSource, /private Boolean hasReview;/);
});

test("admin package edit keeps existing package item relations", () => {
  const adminSource = readFrontend("src/apps/admin/AdminApp.jsx");

  assert.match(adminSource, /<PackagesPage rows=\{snapshot\.packages\} total=\{snapshot\.packageTotal\} pager=\{snapshot\.packagePager\} onPageChange=\{\(next\) => onPageChange\("package", next\)\} searchKeyword=\{snapshot\.packageFilter\.keyword\} onSearch=\{\(keyword\) => onSearch\("package", keyword\)\} categories=\{snapshot\.categoryOptions\} items=\{snapshot\.items\}/);
  assert.match(adminSource, /function PackagesPage\(\{ rows, total, pager, onPageChange, searchKeyword, onSearch, categories, items, session, onOpenDrawer, requestConfirm, onToast, onReload \}\)/);
  assert.match(adminSource, /async function openPackageEditor\(row\)/);
  assert.match(adminSource, /request\(session, `\/admin\/explore-package\/\$\{row\.id\}`\)/);
  assert.match(adminSource, /record: \{ \.\.\.row, \.\.\.detail \}, categories, items/);
  assert.match(adminSource, /onOpenDrawer\(\{ type: "package", title: "新增套餐", categories, items \}\)/);
  assert.match(adminSource, /function normalizePackageItems\(packageItems, availableItems\)/);
  assert.match(adminSource, /const \[packageItemRows, setPackageItemRows\] = useState\(\(\) => normalizePackageItems\(drawer\.record\?\.packageItems \|\| \[\], drawer\.items \|\| \[\]\)\)/);
  assert.match(adminSource, /function togglePackageItem\(item\)/);
  assert.match(adminSource, /function changePackageItemCopies\(itemId, copies\)/);
  assert.match(adminSource, /套餐包含项目/);
  assert.match(adminSource, /checked=\{packageItemRows\.some\(\(row\) => String\(row\.itemId\) === String\(item\.id\)\)\}/);
  assert.match(adminSource, /packageItems: drawer\.type === "package" \? packageItemRows : undefined/);
});

test("mobile client screens constrain wide content inside the viewport", () => {
  const cssSource = readFrontend("src/styles/app.css");

  assert.match(cssSource, /\.client-shell\s*\{[\s\S]*overflow-x: hidden/);
  assert.match(cssSource, /@media \(max-width: 760px\)[\s\S]*\.login-page[\s\S]*overflow-x: hidden/);
  assert.match(cssSource, /@media \(max-width: 760px\)[\s\S]*\.login-side[\s\S]*min-width: 0/);
  assert.match(cssSource, /@media \(max-width: 760px\)[\s\S]*\.client-nav[\s\S]*overflow: hidden/);
  assert.match(cssSource, /\.order-item[\s\S]*min-width: 0/);
  assert.match(cssSource, /\.client-main[\s\S]*max-width: 100vw/);
  assert.match(cssSource, /@media \(max-width: 760px\)[\s\S]*\.login-visual h1[\s\S]*overflow-wrap: anywhere/);
  assert.match(cssSource, /@media \(max-width: 760px\)[\s\S]*\.login-card[\s\S]*max-width: 100%/);
  assert.match(cssSource, /@media \(max-width: 760px\)[\s\S]*\.login-card input[\s\S]*min-width: 0/);
  assert.match(cssSource, /@media \(max-width: 760px\)[\s\S]*\.client-section \.section-head[\s\S]*grid-template-columns: minmax\(0, 1fr\)/);
  assert.match(cssSource, /@media \(max-width: 760px\)[\s\S]*\.order-item \.section-head[\s\S]*grid-template-columns: minmax\(0, 1fr\)/);
  assert.match(cssSource, /@media \(max-width: 760px\)[\s\S]*\.spot-body \.section-head[\s\S]*grid-template-columns: minmax\(0, 1fr\)/);
  assert.match(cssSource, /#root[\s\S]*overflow-x: hidden/);
  assert.match(cssSource, /\.login-page > \*,[\s\S]*\.client-nav > \*,[\s\S]*min-width: 0/);
  assert.match(cssSource, /@media \(max-width: 760px\)[\s\S]*\.login-page[\s\S]*grid-template-columns: minmax\(0, 1fr\)/);
  assert.match(cssSource, /@media \(max-width: 760px\)[\s\S]*\.client-main[\s\S]*width: 100%/);
  assert.match(cssSource, /@media \(max-width: 760px\)[\s\S]*\.order-item[\s\S]*width: 100%/);
  assert.match(cssSource, /@media \(max-width: 760px\)[\s\S]*\.order-item \.price[\s\S]*justify-self: start/);
  assert.match(cssSource, /@media \(max-width: 760px\)[\s\S]*\.login-card[\s\S]*width: min\(380px, calc\(100vw - 48px\)\)/);
  assert.match(cssSource, /@media \(max-width: 760px\)[\s\S]*\.client-nav[\s\S]*width: calc\(100vw - 28px\)/);
  assert.match(cssSource, /@media \(max-width: 760px\)[\s\S]*\.client-section[\s\S]*width: min\(100%, calc\(100vw - 36px\)\)/);
  assert.match(cssSource, /@media \(max-width: 760px\)[\s\S]*\.client-hero[\s\S]*width: min\(100%, calc\(100vw - 36px\)\)/);
});

test("client discovery uses a real compact search form on mobile", () => {
  const clientSource = readFrontend("src/apps/client/ClientApp.jsx");
  const cssSource = readFrontend("src/styles/app.css");

  assert.match(clientSource, /const \[draftKeyword, setDraftKeyword\] = useState\(""\)/);
  assert.match(clientSource, /function applyFilters\(event\)/);
  assert.match(clientSource, /className="client-section client-discovery"/);
  assert.match(clientSource, /aria-label="刷新特色项目"/);
  assert.match(clientSource, /<form className="filter-shelf client-filter-form" onSubmit=\{applyFilters\}>/);
  assert.match(clientSource, /<button className="button-ghost" type="submit"><Search size=\{16\} \/>搜索<\/button>/);
  assert.match(cssSource, /@media \(max-width: 760px\)[\s\S]*\.client-filter-form[\s\S]*grid-template-columns: repeat\(2, minmax\(0, 1fr\)\)/);
  assert.match(cssSource, /@media \(max-width: 760px\)[\s\S]*\.client-filter-form input[\s\S]*grid-column: 1 \/ -1/);
  assert.match(cssSource, /@media \(max-width: 760px\)[\s\S]*\.client-discovery \.section-head[\s\S]*grid-template-columns: minmax\(0, 1fr\) auto/);
  assert.match(cssSource, /@media \(max-width: 760px\)[\s\S]*\.client-discovery \.client-filter-form select,[\s\S]*grid-column: auto/);
  assert.match(cssSource, /@media \(max-width: 760px\)[\s\S]*\.client-hero \.hero-image[\s\S]*display: none/);
});

test("client top navigation behaves like a compact product bar on mobile", () => {
  const clientSource = readFrontend("src/apps/client/ClientApp.jsx");
  const cssSource = readFrontend("src/styles/app.css");

  assert.match(clientSource, /<div className="client-nav-links">/);
  assert.match(clientSource, /const activeNavRef = useRef\(null\)/);
  assert.match(clientSource, /scrollIntoView\(\{ block: "nearest", inline: "center" \}\)/);
  assert.match(clientSource, /aria-current=\{active \? "page" : undefined\}/);
  assert.match(clientSource, /<div className="client-actions">/);
  assert.match(cssSource, /\.client-nav-links[\s\S]*overflow-x: auto/);
  assert.match(cssSource, /\.client-nav-links[\s\S]*scroll-snap-type: x proximity/);
  assert.match(cssSource, /\.client-nav-link[\s\S]*flex: 0 0 auto/);
  assert.match(cssSource, /@media \(max-width: 760px\)[\s\S]*\.client-nav-link[\s\S]*min-width: 84px/);
  assert.match(cssSource, /@media \(max-width: 760px\)[\s\S]*\.client-nav-link[\s\S]*font-size: 14px/);
  assert.match(cssSource, /\.client-nav-link[\s\S]*scroll-snap-align: start/);
  assert.match(cssSource, /\.client-actions[\s\S]*gap: 8px/);
});

test("admin search chrome shows active result counts and clear action", () => {
  const adminSource = readFrontend("src/apps/admin/AdminApp.jsx");
  const cssSource = readFrontend("src/styles/app.css");
  const searchPageSource = adminSource.slice(
    adminSource.indexOf("function SearchableTablePage"),
    adminSource.indexOf("function CategoriesPage")
  );

  assert.match(searchPageSource, /function SearchableTablePage\(\{[\s\S]*total[\s\S]*pager[\s\S]*onPageChange/);
  assert.match(searchPageSource, /const hasKeyword = Boolean\(searchKeyword\.trim\(\)\)/);
  assert.match(searchPageSource, /function submitSearch\(event\)/);
  assert.match(searchPageSource, /const totalCount = total \?\? rows\.length/);
  assert.match(searchPageSource, /<form className="toolbar" style=\{\{ marginBottom: 14 \}\} onSubmit=\{submitSearch\}>/);
  assert.match(searchPageSource, /<span className="table-summary">/);
  assert.match(searchPageSource, /共 \{totalCount\} 条/);
  assert.match(searchPageSource, /关键词筛选，共 \{totalCount\} 条/);
  assert.match(searchPageSource, /<button className="button-ghost" type="submit"><Search size=\{16\} \/>搜索<\/button>/);
  assert.match(searchPageSource, /onClick=\{\(\) => \{ setKeyword\(""\); onSearch\?\.\(""\); \}\}/);
  assert.doesNotMatch(searchPageSource, /共 \{rows\.length\} 条/);
  assert.doesNotMatch(searchPageSource, /JSON\.stringify\(row\)\.includes\(value\)/);
  assert.doesNotMatch(searchPageSource, /已筛选 \{visibleCount\} \/ \{rows\.length\}/);
  assert.match(searchPageSource, /<PaginationBar total=\{totalCount\} pager=\{pager\} onPageChange=\{onPageChange\} \/>/);
  assert.doesNotMatch(adminSource, /<button className="button-ghost" type="button"><Search size=\{16\} \/>绛涢€<\/button>/);
  assert.match(cssSource, /\.toolbar[\s\S]*display: grid/);
  assert.match(cssSource, /\.toolbar[\s\S]*grid-template-columns: minmax\(0, 1fr\) repeat\(3, auto\)/);
  assert.match(cssSource, /@media \(max-width: 760px\)[\s\S]*\.toolbar[\s\S]*grid-template-columns: 1fr/);
});

test("admin data load uses server pagination state instead of a fixed first page", () => {
  const adminSource = readFrontend("src/apps/admin/AdminApp.jsx");
  const pageResources = [
    ["category", "/admin/category/page", "request"],
    ["item", "/admin/explore-item/page", "request"],
    ["package", "/admin/explore-package/page", "request"],
    ["order", "/admin/explore-order/page", "request"],
    ["review", "/admin/review/page", "request"],
    ["user", "/admin/user-manage/page", "adminRequest"],
    ["employee", "/admin/employee/page", "adminRequest"],
    ["log", "/admin/operation-log/page", "adminRequest"]
  ];

  assert.match(adminSource, /const DEFAULT_ADMIN_PAGE_SIZE = 20/);
  assert.match(adminSource, /function createAdminPagination\(\)/);
  assert.match(adminSource, /function pageUrl\(path, pager, filters = \{\}\)/);
  assert.match(adminSource, /new URLSearchParams\(\{[\s\S]*page: String\(pager\.page\)[\s\S]*pageSize: String\(pager\.pageSize\)/);
  assert.match(adminSource, /Object\.entries\(filters\)[\s\S]*params\.set\(key, String\(value\)\)/);
  assert.match(adminSource, /const \[pagination, setPagination\] = useState\(\(\) => createAdminPagination\(\)\)/);
  assert.match(adminSource, /function updatePagination\(resource, next\)/);
  assert.match(adminSource, /function toPage\(pageLike\)/);
  assert.match(adminSource, /total: Number\(pageLike\?\.total \?\? rows\.length\)/);
  for (const [key, endpoint, requestFunction] of pageResources) {
    const escapedEndpoint = endpoint.replace(/\//g, "\\/");
    assert.match(adminSource, new RegExp(`${requestFunction}\\(session, pageUrl\\("${escapedEndpoint}", pagination\\.${key}`));
    assert.match(adminSource, new RegExp(`${key}Pager: \\{ \\.\\.\\.pagination\\.${key}, total: ${key}Page\\.total \\}`));
  }
  assert.doesNotMatch(adminSource, /\/admin\/(?:category|explore-item|explore-package|explore-order|review|user-manage|employee|operation-log)\/page\?page=1&pageSize=(?:30|100)/);
  for (const key of ["category", "item", "package", "order", "review", "user", "employee", "log"]) {
    assert.match(adminSource, new RegExp(`${key}Total: ${key}Page\\.total`));
  }
});

test("admin searchable lists send keyword filters to backend and reset to the first page", () => {
  const adminSource = readFrontend("src/apps/admin/AdminApp.jsx");
  const searchableRequests = [
    ["category", "/admin/category/page", "keywordFilter(\"name\", filters.category.keyword)"],
    ["item", "/admin/explore-item/page", "keywordFilter(\"name\", filters.item.keyword)"],
    ["package", "/admin/explore-package/page", "keywordFilter(\"name\", filters.package.keyword)"],
    ["user", "/admin/user-manage/page", "userKeywordFilter(filters.user.keyword)"],
    ["employee", "/admin/employee/page", "keywordFilter(\"name\", filters.employee.keyword)"]
  ];

  assert.match(adminSource, /function createAdminFilters\(\)/);
  assert.match(adminSource, /function keywordFilter\(field, keyword\)/);
  assert.match(adminSource, /function userKeywordFilter\(keyword\)/);
  assert.match(adminSource, /function logFilterParams\(filter\)/);
  assert.match(adminSource, /PHONE_REGEX\.test\(value\) \? \{ phone: value \} : \{ name: value \}/);
  assert.match(adminSource, /const \[filters, setFilters\] = useState\(\(\) => createAdminFilters\(\)\)/);
  assert.match(adminSource, /function updateFilter\(resource, keyword\)/);
  assert.match(adminSource, /function updateStructuredFilter\(resource, next\)/);
  assert.match(adminSource, /setPagination\(\(current\) => \(\{[\s\S]*\[resource\]: \{ \.\.\.current\[resource\], page: 1 \}/);
  for (const [key, endpoint, filterExpression] of searchableRequests) {
    const escapedEndpoint = endpoint.replace(/\//g, "\\/");
    const escapedFilter = filterExpression.replace(/[(){}."]/g, "\\$&");
    const requestFunction = ["user", "employee"].includes(key) ? "adminRequest" : "request";
    assert.match(adminSource, new RegExp(`${requestFunction}\\(session, pageUrl\\("${escapedEndpoint}", pagination\\.${key}, ${escapedFilter}\\)`));
    assert.match(adminSource, new RegExp(`${key}Filter: filters\\.${key}`));
  }
  assert.match(adminSource, /adminRequest\(session, pageUrl\("\/admin\/operation-log\/page", pagination\.log, logFilterParams\(filters\.log\)\)/);
  assert.match(adminSource, /<CategoriesPage[\s\S]*searchKeyword=\{snapshot\.categoryFilter\.keyword\}[\s\S]*onSearch=\{\(keyword\) => onSearch\("category", keyword\)\}/);
  assert.match(adminSource, /<ItemsPage[\s\S]*searchKeyword=\{snapshot\.itemFilter\.keyword\}[\s\S]*onSearch=\{\(keyword\) => onSearch\("item", keyword\)\}/);
  assert.match(adminSource, /<PackagesPage[\s\S]*searchKeyword=\{snapshot\.packageFilter\.keyword\}[\s\S]*onSearch=\{\(keyword\) => onSearch\("package", keyword\)\}/);
  assert.match(adminSource, /<UsersPage[\s\S]*searchKeyword=\{snapshot\.userFilter\.keyword\}[\s\S]*onSearch=\{\(keyword\) => onSearch\("user", keyword\)\}/);
  assert.match(adminSource, /<EmployeesPage[\s\S]*searchKeyword=\{snapshot\.employeeFilter\.keyword\}[\s\S]*onSearch=\{\(keyword\) => onSearch\("employee", keyword\)\}/);
  assert.match(adminSource, /<LogsPage[\s\S]*filter=\{snapshot\.logFilter\}[\s\S]*onFilterChange=\{\(next\) => onFilterChange\("log", next\)\}/);
});

test("admin operation log page exposes audit filters and detail drawer", () => {
  const adminSource = readFrontend("src/apps/admin/AdminApp.jsx");

  assert.match(adminSource, /resource === "log"[\s\S]*\{ keyword: "", requestMethod: "" \}/);
  assert.match(adminSource, /function logFilterParams\(filter\)/);
  assert.match(adminSource, /return \{[\s\S]*keyword: value[\s\S]*requestMethod: filter\.requestMethod/);
  assert.match(adminSource, /function LogsPage\(\{ rows, total, pager, onPageChange, filter, onFilterChange, session, onToast \}\)/);
  assert.match(adminSource, /const \[draftFilter, setDraftFilter\] = useState\(filter\)/);
  assert.match(adminSource, /placeholder="操作、人员、路径、IP"/);
  assert.match(adminSource, /<select aria-label="请求方法" value=\{draftFilter\.requestMethod\}/);
  for (const method of ["GET", "POST", "PUT", "DELETE"]) {
    assert.match(adminSource, new RegExp(`<option value="${method}">${method}<\\/option>`));
  }
  assert.match(adminSource, /function LogDetailDrawer\(\{ log, onClose \}\)/);
  assert.match(adminSource, /const \[logDetail, setLogDetail\] = useState\(null\)/);
  assert.match(adminSource, /setLogDetail\(row\)/);
  assert.match(adminSource, /<LogDetailDrawer log=\{logDetail\} onClose=\{\(\) => setLogDetail\(null\)\} \/>/);
  for (const field of ["description", "operatorName", "requestMethod", "requestUri", "clientIp", "costTime", "createTime"]) {
    assert.match(adminSource, new RegExp(`log\\.${field}`));
  }
});

test("admin dashboard displays real operating quality metrics from backend trend", () => {
  const adminSource = readFrontend("src/apps/admin/AdminApp.jsx");
  const dashboardSource = adminSource.slice(
    adminSource.indexOf("function Dashboard"),
    adminSource.indexOf("function TrendChart")
  );

  assert.match(dashboardSource, /label: "确认收入"/);
  assert.match(dashboardSource, /value: money\(snapshot\.trend\.confirmedRevenue\)/);
  assert.match(dashboardSource, /icon: CircleDollarSign/);
  assert.match(dashboardSource, /label: "完成率"/);
  assert.match(dashboardSource, /value: `\$\{snapshot\.trend\.completionRate \|\| 0\}%`/);
  assert.match(dashboardSource, /icon: BarChart3/);
  assert.match(dashboardSource, /完成\$\{snapshot\.trend\.completedOrders \|\| 0\}/);
  assert.match(dashboardSource, /取消\$\{snapshot\.trend\.canceledOrders \|\| 0\}/);
});

test("admin item and package editors keep a full category option snapshot separate from category paging", () => {
  const adminSource = readFrontend("src/apps/admin/AdminApp.jsx");

  assert.match(adminSource, /categoryOptionsSnapshot/);
  assert.match(adminSource, /request\(session, pageUrl\("\/admin\/category\/page", \{ page: 1, pageSize: 100 \}\)\)/);
  assert.match(adminSource, /categoryOptions: toPage\(categoryOptionsSnapshot\)\.rows/);
  assert.match(adminSource, /<ItemsPage[\s\S]*categories=\{snapshot\.categoryOptions\}/);
  assert.match(adminSource, /<PackagesPage[\s\S]*categories=\{snapshot\.categoryOptions\}/);
});

test("admin list pages expose reusable server pagination controls", () => {
  const adminSource = readFrontend("src/apps/admin/AdminApp.jsx");
  const cssSource = readFrontend("src/styles/app.css");

  assert.match(adminSource, /ChevronLeft/);
  assert.match(adminSource, /ChevronRight/);
  assert.match(adminSource, /const ADMIN_PAGE_SIZE_OPTIONS = \[10, 20, 50, 100\]/);
  assert.match(adminSource, /function PaginationBar\(\{ total, pager, onPageChange \}\)/);
  assert.match(adminSource, /const totalPages = Math\.max\(1, Math\.ceil\(totalCount \/ pager\.pageSize\)\)/);
  assert.match(adminSource, /const start = totalCount \? \(pager\.page - 1\) \* pager\.pageSize \+ 1 : 0/);
  assert.match(adminSource, /const end = Math\.min\(totalCount, pager\.page \* pager\.pageSize\)/);
  assert.match(adminSource, /onClick=\{\(\) => onPageChange\(\{ page: pager\.page - 1 \}\)\}/);
  assert.match(adminSource, /onClick=\{\(\) => onPageChange\(\{ page: pager\.page \+ 1 \}\)\}/);
  assert.match(adminSource, /onChange=\{\(event\) => onPageChange\(\{ page: 1, pageSize: Number\(event\.target\.value\) \}\)\}/);
  assert.match(adminSource, /显示 \{start\}-\{end\} \/ \{totalCount\}/);
  assert.match(adminSource, /第 \{pager\.page\} \/ \{totalPages\} 页/);
  assert.match(cssSource, /\.pagination-bar[\s\S]*display: flex/);
  assert.match(cssSource, /\.pagination-meta[\s\S]*font-variant-numeric: tabular-nums/);
  assert.match(cssSource, /@media \(max-width: 760px\)[\s\S]*\.pagination-bar[\s\S]*align-items: stretch/);
});

test("admin order and review filters use backend totals in the product-style summary chrome", () => {
  const adminSource = readFrontend("src/apps/admin/AdminApp.jsx");

  assert.match(adminSource, /function OrdersPage\(\{ rows, total, pager, onPageChange, filter, onFilterChange, session, canAdmin, requestConfirm, onToast, onReload \}\)/);
  assert.match(adminSource, /const orderTotalCount = total \?\? rows\.length/);
  assert.match(adminSource, /<span className="table-summary">/);
  assert.match(adminSource, /关键词筛选，共 \{orderTotalCount\} 条/);
  assert.match(adminSource, /共 \{orderTotalCount\} 条/);
  assert.match(adminSource, /<PaginationBar total=\{orderTotalCount\} pager=\{pager\} onPageChange=\{onPageChange\} \/>/);
  assert.match(adminSource, /function ReviewsPage\(\{ rows, total, pager, onPageChange, filter, onFilterChange, session, canAdmin, requestConfirm, onToast, onReload \}\)/);
  assert.match(adminSource, /const reviewTotalCount = total \?\? rows\.length/);
  assert.match(adminSource, /关键词筛选，共 \{reviewTotalCount\} 条/);
  assert.match(adminSource, /共 \{reviewTotalCount\} 条/);
  assert.match(adminSource, /<PaginationBar total=\{reviewTotalCount\} pager=\{pager\} onPageChange=\{onPageChange\} \/>/);
});

test("admin row actions use clear visual hierarchy for primary, state, and danger operations", () => {
  const adminSource = readFrontend("src/apps/admin/AdminApp.jsx");
  const cssSource = readFrontend("src/styles/app.css");

  assert.match(adminSource, /className="button-ghost table-action table-action-primary"/);
  assert.match(adminSource, /className="button-ghost table-action table-action-state"/);
  assert.match(adminSource, /className="button-danger table-action table-action-danger"/);
  assert.match(adminSource, /table-action-state[\s\S]*确认预约/);
  assert.match(adminSource, /table-action-danger[\s\S]*取消预约/);
  assert.match(adminSource, /table-action-primary[\s\S]*setReplyTarget\(row\)/);
  assert.match(cssSource, /\.table-actions[\s\S]*flex-wrap: nowrap/);
  assert.match(cssSource, /\.table-action[\s\S]*white-space: nowrap/);
  assert.match(cssSource, /\.table-action-primary/);
  assert.match(cssSource, /\.table-action-state/);
  assert.match(cssSource, /\.table-action-danger/);
});

test("client notification center closes the order event loop", () => {
  const clientSource = readFrontend("src/apps/client/ClientApp.jsx");
  const cssSource = readFrontend("src/styles/app.css");
  const demoSource = readFrontend("public/assets/demo-data.js");

  assert.match(clientSource, /Bell/);
  assert.match(clientSource, /function NotificationDrawer/);
  assert.match(clientSource, /`\/user\/notification\/page\?page=\$\{page\}&pageSize=\$\{PAGE_SIZE\}`/);
  assert.match(clientSource, /\/user\/notification\/unread-count/);
  assert.match(clientSource, /notification-pagination/);
  assert.match(clientSource, /aria-label="上一页通知"/);
  assert.match(clientSource, /aria-label="下一页通知"/);
  assert.match(clientSource, /setTotal/);
  assert.doesNotMatch(clientSource, /onUnreadChange\(rows\.filter/);
  assert.match(clientSource, /`\/user\/notification\/\$\{notification\.id\}\/read`/);
  assert.match(clientSource, /\/user\/notification\/read-all/);
  assert.match(clientSource, /notification\.orderId/);
  assert.match(clientSource, /setOrderDetailTarget/);
  assert.match(clientSource, /加载通知中/);
  assert.match(clientSource, /暂无通知/);
  assert.match(clientSource, /通知加载失败/);
  assert.match(clientSource, /aria-label="通知中心"/);
  assert.match(clientSource, /notification-badge/);
  assert.match(clientSource, /status === 4\) return "系统超时取消"/);

  const adminSource = readFrontend("src/apps/admin/AdminApp.jsx");
  assert.match(adminSource, /status === 4\) return \{ text: "系统超时取消", tone: "off" \}/);
  assert.match(adminSource, /<option value="4">系统超时取消<\/option>/);

  assert.match(demoSource, /const notifications = \[/);
  assert.match(demoSource, /\/user\/notification\/page/);
  assert.match(demoSource, /\/user\/notification\/unread-count/);
  assert.match(demoSource, /\/user\/notification\/read-all/);

  assert.match(cssSource, /\.notification-trigger/);
  assert.match(cssSource, /\.notification-badge/);
  assert.match(cssSource, /\.notification-item\.unread/);
  assert.match(cssSource, /@media \(max-width: 760px\)[\s\S]*\.notification-drawer/);
});

test("admin dashboard exposes cache health invalidation and warmup controls only to admins", () => {
  const adminSource = readFrontend("src/apps/admin/AdminApp.jsx");
  const cssSource = readFrontend("src/styles/app.css");

  assert.match(adminSource, /adminRequest\(session, "\/admin\/cache\/stats", null\)/);
  assert.match(adminSource, /session\.role === "ADMIN" && snapshot\.cacheStats/);
  assert.match(adminSource, /function CacheStatusPanel/);
  assert.match(adminSource, /\/admin\/cache\/warmup/);
  assert.match(adminSource, /`\/admin\/cache\/invalidate\/\$\{cacheDomain\}`/);
  assert.match(adminSource, /数据库回源/);
  assert.match(adminSource, /Redis降级/);
  assert.match(cssSource, /\.cache-stat-grid/);
  assert.match(cssSource, /@media \(max-width: 760px\)[\s\S]*\.cache-status-head/);
});

test("product demo uses bundled photographic assets instead of SVG placeholders", () => {
  const repoRoot = path.resolve(frontendRoot, "../..");
  const imageNames = ["coffee", "bookstore", "boardgame", "citywalk", "workshop", "package"];
  const sourceFiles = [
    readFrontend("src/apps/admin/AdminApp.jsx"),
    readFrontend("src/apps/client/ClientApp.jsx"),
    readFrontend("src/styles/app.css"),
    readFrontend("public/assets/demo-data.js"),
    fs.readFileSync(path.join(repoRoot, "explorer-web/src/main/resources/static/assets/demo-data.js"), "utf8"),
    fs.readFileSync(path.join(repoRoot, "docs/local-explorer-init.sql"), "utf8"),
    fs.readFileSync(path.join(repoRoot, "docs/local-explorer-sample-data.sql"), "utf8")
  ];

  for (const name of imageNames) {
    const imagePath = path.join(frontendRoot, `public/assets/images/${name}.webp`);
    assert.equal(fs.existsSync(imagePath), true, `${name}.webp should exist`);
    assert.ok(fs.statSync(imagePath).size > 10000, `${name}.webp should be a real raster asset`);
  }
  for (const source of sourceFiles) {
    assert.doesNotMatch(source, /assets\/images\/[^"')]+\.svg/);
    assert.doesNotMatch(source, /image\("[^"]+\.svg"\)/);
  }
});
