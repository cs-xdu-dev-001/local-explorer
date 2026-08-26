import {
  BarChart3,
  CalendarCheck,
  ChevronLeft,
  ChevronRight,
  CircleDollarSign,
  Database,
  Download,
  ExternalLink,
  Eye,
  LayoutDashboard,
  LogOut,
  MapPinned,
  MessageSquare,
  PackageOpen,
  Pencil,
  Plus,
  RefreshCcw,
  Save,
  ScrollText,
  Search,
  Settings2,
  ShieldCheck,
  Star,
  Store,
  Tags,
  Trash2,
  UserCog,
  Users,
  X
} from "lucide-react";
import { useCallback, useEffect, useRef, useState } from "react";
import { createSession, downloadFile, getApiBase, loginAdmin, logout, request, restoreSession, setApiBase } from "../../lib/auth.js";

const navItems = [
  { page: "index", label: "运营概览", icon: LayoutDashboard, href: "./index.html" },
  { page: "merchant", label: "商户资料", icon: Store, href: "./merchant.html" },
  { page: "categories", label: "内容分类", icon: Tags, href: "./categories.html" },
  { page: "items", label: "特色项目", icon: MapPinned, href: "./items.html" },
  { page: "packages", label: "探索套餐", icon: PackageOpen, href: "./packages.html" },
  { page: "orders", label: "预约订单", icon: CalendarCheck, href: "./orders.html" },
  { page: "reviews", label: "用户评价", icon: Star, href: "./reviews.html" },
  { page: "export-jobs", label: "导出任务", icon: Download, href: "./export-jobs.html" },
  { page: "users", label: "用户管理", icon: Users, href: "./users.html", adminOnly: true },
  { page: "employees", label: "员工管理", icon: UserCog, href: "./employees.html", adminOnly: true },
  { page: "operation-logs", label: "操作日志", icon: ScrollText, href: "./operation-logs.html", adminOnly: true }
];

const pageTitles = Object.fromEntries(navItems.map((item) => [item.page, item.label]));
const defaultContentImage = "/assets/images/coffee.webp";
const PHONE_PATTERN = "1[3-9][0-9]{9}";
const PHONE_REGEX = new RegExp(`^${PHONE_PATTERN}$`);
const ADMIN_PAGE_SIZE_OPTIONS = [10, 20, 50, 100];
const DEFAULT_ADMIN_PAGE_SIZE = 20;
const adminPageResources = ["category", "item", "package", "order", "review", "user", "employee", "log"];
const merchantFields = [
  { key: "name", label: "商户名称", required: true, maxLength: 32 },
  { key: "slogan", label: "展示标语", required: false, maxLength: 100 },
  { key: "address", label: "门店地址", required: true, maxLength: 255 },
  { key: "phone", label: "联系电话", required: true, maxLength: 32 },
  { key: "businessHours", label: "营业时间", required: true, maxLength: 64 },
  { key: "notice", label: "预约须知", required: false, maxLength: 255 },
  { key: "coverImage", label: "封面图片", required: false, maxLength: 500 }
];

function rootPage() {
  return document.getElementById("root")?.dataset.page || "index";
}

function money(value) {
  return `¥${Number(value || 0).toFixed(2)}`;
}

function toPage(pageLike) {
  if (Array.isArray(pageLike)) return { rows: pageLike, total: pageLike.length };
  const rows = pageLike?.records || [];
  return { rows, total: Number(pageLike?.total ?? rows.length) };
}

function createAdminPagination() {
  return Object.fromEntries(adminPageResources.map((resource) => [
    resource,
    { page: 1, pageSize: DEFAULT_ADMIN_PAGE_SIZE }
  ]));
}

function createAdminFilters() {
  return Object.fromEntries(adminPageResources.map((resource) => [
    resource,
    resource === "order"
      ? { keyword: "", status: "" }
      : resource === "review"
        ? { keyword: "", rating: "", replyState: "" }
        : resource === "log"
          ? { keyword: "", requestMethod: "" }
          : { keyword: "" }
  ]));
}

function keywordFilter(field, keyword) {
  const value = String(keyword || "").trim();
  return value ? { [field]: value } : {};
}

function userKeywordFilter(keyword) {
  const value = String(keyword || "").trim();
  if (!value) return {};
  return PHONE_REGEX.test(value) ? { phone: value } : { name: value };
}

function orderFilterParams(filter) {
  const value = String(filter.keyword || "").trim();
  return {
    keyword: value,
    status: filter.status
  };
}

function reviewFilterParams(filter) {
  const value = String(filter.keyword || "").trim();
  return {
    keyword: value,
    rating: filter.rating,
    replyState: filter.replyState
  };
}

function logFilterParams(filter) {
  const value = String(filter.keyword || "").trim();
  return {
    keyword: value,
    requestMethod: filter.requestMethod
  };
}

function pageUrl(path, pager, filters = {}) {
  const params = new URLSearchParams({
    page: String(pager.page),
    pageSize: String(pager.pageSize)
  });
  Object.entries(filters).forEach(([key, value]) => {
    if (value !== undefined && value !== null && String(value).trim() !== "") {
      params.set(key, String(value));
    }
  });
  return `${path}?${params.toString()}`;
}

function createRequestId() {
  return globalThis.crypto?.randomUUID?.().replaceAll("-", "") || `${Date.now()}_${Math.random().toString(16).slice(2)}`;
}

function compactExportFilters(filters) {
  return Object.fromEntries(Object.entries(filters || {}).filter(([, value]) => (
    value !== undefined && value !== null && String(value).trim() !== ""
  )));
}

async function createExportJob(session, exportType, fileFormat, filters, onToast) {
  try {
    const job = await request(session, "/admin/export-jobs", {
      method: "POST",
      body: JSON.stringify({
        requestId: createRequestId(),
        exportType,
        fileFormat,
        ...compactExportFilters(filters)
      })
    });
    onToast(`导出任务已创建：${String(job.jobId || "").slice(0, 8)}`);
    return job;
  } catch (err) {
    onToast(err.message);
    return null;
  }
}

function ExportCreateAction({ session, exportType, filters, onToast }) {
  const [format, setFormat] = useState("XLSX");
  const [submitting, setSubmitting] = useState(false);

  async function submit() {
    setSubmitting(true);
    await createExportJob(session, exportType, format, filters, onToast);
    setSubmitting(false);
  }

  return (
    <div className="export-create-action">
      <select aria-label="导出格式" value={format} onChange={(event) => setFormat(event.target.value)}>
        <option value="XLSX">XLSX</option>
        <option value="CSV">CSV</option>
      </select>
      <button className="button-ghost" type="button" onClick={submit} disabled={submitting}>
        <Download size={16} />
        {submitting ? "创建中" : "创建导出"}
      </button>
    </div>
  );
}

function adminRequest(session, path, fallback) {
  return session.role === "ADMIN"
    ? request(session, path)
    : Promise.resolve(fallback);
}

function dateText(value) {
  if (!value) return "-";
  return String(value).replace("T", " ").slice(0, 16);
}

function durationText(minutes) {
  const value = Number(minutes || 0);
  if (!value) return "-";
  if (value < 60) return `${value}分钟`;
  const hours = Math.floor(value / 60);
  const rest = value % 60;
  return rest ? `${hours}小时${rest}分钟` : `${hours}小时`;
}

function remainingText(row) {
  if (!Number.isFinite(Number(row.capacity)) || !Number.isFinite(Number(row.booked))) return "-";
  return `${Math.max(0, Number(row.capacity) - Number(row.booked))}/${Number(row.capacity)}`;
}

function orderStatus(value) {
  const status = Number(value);
  if (status === 1) return { text: "已确认", tone: "ok" };
  if (status === 2) return { text: "已完成", tone: "ok" };
  if (status === 3) return { text: "已取消", tone: "off" };
  if (status === 4) return { text: "系统超时取消", tone: "off" };
  return { text: "待确认", tone: "warn" };
}

function orderActionsForStatus(value) {
  const status = Number(value);
  return {
    canConfirm: status === 0,
    canComplete: status === 1,
    canCancel: status === 0 || status === 1
  };
}

function enabledStatus(value) {
  return Number(value) === 1
    ? { text: "启用", tone: "ok" }
    : { text: "停用", tone: "off" };
}

function paymentStatus(value) {
  if (value === "已支付") return { text: value, tone: "ok" };
  if (value === "已退款") return { text: value, tone: "off" };
  return { text: value || "待支付", tone: "warn" };
}

function StatusPill({ status }) {
  return <span className={`status-pill ${status.tone}`}>{status.text}</span>;
}

function Toast({ message }) {
  return <p className={`toast ${message ? "show" : ""}`} role="status" aria-live="polite">{message}</p>;
}

function RowActions({ onEdit, onToggleStatus, onDelete, status }) {
  const statusActionText = Number(status) === 1 ? "停用" : "启用";
  return (
    <div className="table-actions">
      <button className="button-ghost table-action table-action-primary" type="button" onClick={onEdit}>
        <Pencil size={14} />
        编辑
      </button>
      {onToggleStatus ? (
        <button className="button-ghost table-action table-action-state" type="button" onClick={onToggleStatus}>
          {statusActionText}
        </button>
      ) : null}
      {onDelete ? (
        <button className="button-danger table-action table-action-danger" type="button" onClick={onDelete}>
          <Trash2 size={14} />
          删除
        </button>
      ) : null}
    </div>
  );
}

function DataTable({ columns, rows, emptyText = "暂无数据" }) {
  if (!rows?.length) return <div className="empty-state">{emptyText}</div>;
  return (
    <div className="admin-table-wrap">
      <table>
        <thead>
          <tr>{columns.map((column) => <th key={column.key} className={column.num ? "num" : ""}>{column.label}</th>)}</tr>
        </thead>
        <tbody>
          {rows.map((row) => (
            <tr key={row.id || row.jobId || row.orderNo || row.requestUri}>
              {columns.map((column) => (
                <td key={column.key} className={column.num ? "num" : ""}>
                  {column.render ? column.render(row) : row[column.key]}
                </td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

function PageHeader({ title, eyebrow, children }) {
  return (
    <div className="section-head">
      <div>
        <p className="eyebrow">{eyebrow}</p>
        <h2>{title}</h2>
      </div>
      {children}
    </div>
  );
}

function Drawer({ title, onClose, children }) {
  return (
    <div className="drawer-backdrop" role="presentation">
      <aside className="drawer" role="dialog" aria-modal="true" aria-label={title}>
        <div className="section-head">
          <h2>{title}</h2>
          <button className="icon-button" type="button" aria-label="关闭" onClick={onClose}><X size={18} /></button>
        </div>
        {children}
      </aside>
    </div>
  );
}

function ConfirmDialog({ confirm, onCancel, onConfirm }) {
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState("");

  async function submit() {
    setSubmitting(true);
    setError("");
    try {
      await onConfirm();
    } catch (err) {
      setError(err.message);
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="drawer-backdrop confirm-backdrop" role="presentation">
      <aside className="confirm-dialog" role="alertdialog" aria-modal="true" aria-label={confirm.title}>
        <div className="section-head" style={{ marginBottom: 8 }}>
          <div>
            <p className="eyebrow">{confirm.eyebrow || "确认操作"}</p>
            <h2>{confirm.title}</h2>
          </div>
          <button className="icon-button" type="button" aria-label="关闭" onClick={onCancel} disabled={submitting}><X size={18} /></button>
        </div>
        <p className="muted">{confirm.message}</p>
        {error ? <div className="form-error">{error}</div> : null}
        <div className="button-row">
          <button className="button-danger" type="button" onClick={submit} disabled={submitting}>
            <Trash2 size={16} />
            {submitting ? "处理中" : confirm.confirmText || "确认删除"}
          </button>
          <button className="button-ghost" type="button" onClick={onCancel} disabled={submitting}>取消</button>
        </div>
      </aside>
    </div>
  );
}

function AdminLogin() {
  const [session, setSession] = useState(() => createSession("admin"));
  const [form, setForm] = useState({ username: "", password: "" });
  const [error, setError] = useState("");
  const [submitting, setSubmitting] = useState(false);

  async function submit(event) {
    event.preventDefault();
    if (!form.username.trim() || !form.password.trim()) {
      setError("请输入管理员账号和密码");
      return;
    }
    setSubmitting(true);
    setError("");
    try {
      const next = await loginAdmin(session, form.username.trim(), form.password);
      setSession(next);
      window.location.href = "./index.html";
    } catch (err) {
      setError(err.message);
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <main className="login-page">
      <section className="login-visual">
        <div>
          <p className="eyebrow">Local Explorer Console</p>
          <h1>管理探店业务</h1>
          <p>内容、预约、评价和门店状态集中处理，运营节奏一眼清楚。</p>
        </div>
      </section>
      <section className="login-side">
        <div className="login-card">
          <p className="eyebrow">管理员登录</p>
          <h2>进入运营后台</h2>
          <form onSubmit={submit}>
            {error ? <div className="form-error">{error}</div> : null}
            <label className="field">
              <span>账号</span>
              <input value={form.username} placeholder="请输入管理员账号" autoComplete="username" maxLength={32} required onChange={(event) => setForm({ ...form, username: event.target.value })} />
            </label>
            <label className="field">
              <span>密码</span>
              <input type="password" value={form.password} placeholder="请输入密码" autoComplete="current-password" maxLength={64} required onChange={(event) => setForm({ ...form, password: event.target.value })} />
            </label>
            <button className="button" type="submit" disabled={submitting}>
              <ShieldCheck size={18} />
              {submitting ? "登录中" : "登录后台"}
            </button>
          </form>
        </div>
      </section>
    </main>
  );
}

function AdminShell() {
  const page = rootPage();
  const [session, setSession] = useState(() => createSession("admin"));
  const [restoringSession, setRestoringSession] = useState(() => !session.token && !session.demo);
  const [apiInput, setApiInput] = useState(getApiBase());
  const [snapshot, setSnapshot] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [toast, setToast] = useState("");
  const [drawer, setDrawer] = useState(null);
  const [confirm, setConfirm] = useState(null);
  const [pagination, setPagination] = useState(() => createAdminPagination());
  const [filters, setFilters] = useState(() => createAdminFilters());
  const canAdmin = session.role === "ADMIN";
  const visibleNavItems = navItems.filter((item) => !item.adminOnly || canAdmin);
  const restrictedPage = navItems.some((item) => item.page === page && item.adminOnly) && !canAdmin;

  useEffect(() => {
    if (!restoringSession) return;
    restoreSession("admin", session)
      .then((next) => setSession({ ...next }))
      .catch(() => undefined)
      .finally(() => setRestoringSession(false));
  }, [restoringSession, session]);

  const showToast = useCallback((message) => {
    setToast(message);
    window.clearTimeout(showToast.timer);
    showToast.timer = window.setTimeout(() => setToast(""), 2400);
  }, []);

  const load = useCallback(async () => {
    if (restoringSession || !session.token) return;
    setLoading(true);
    setError("");
    try {
      const [
        merchant,
        shopStatus,
        categories,
        categoryOptionsSnapshot,
        items,
        packages,
        trend,
        orders,
        reviews,
        users,
        employees,
        logs,
        cacheStats
      ] = await Promise.all([
        request(session, "/admin/merchant/info"),
        request(session, "/admin/shop/status"),
        request(session, pageUrl("/admin/category/page", pagination.category, keywordFilter("name", filters.category.keyword))),
        request(session, pageUrl("/admin/category/page", { page: 1, pageSize: 100 })),
        request(session, pageUrl("/admin/explore-item/page", pagination.item, keywordFilter("name", filters.item.keyword))),
        request(session, pageUrl("/admin/explore-package/page", pagination.package, keywordFilter("name", filters.package.keyword))),
        request(session, "/admin/explore-order/trend"),
        request(session, pageUrl("/admin/explore-order/page", pagination.order, orderFilterParams(filters.order))),
        request(session, pageUrl("/admin/review/page", pagination.review, reviewFilterParams(filters.review))),
        adminRequest(session, pageUrl("/admin/user-manage/page", pagination.user, userKeywordFilter(filters.user.keyword)), { records: [], total: 0 }),
        adminRequest(session, pageUrl("/admin/employee/page", pagination.employee, keywordFilter("name", filters.employee.keyword)), { records: [], total: 0 }),
        adminRequest(session, pageUrl("/admin/operation-log/page", pagination.log, logFilterParams(filters.log)), { records: [], total: 0 }),
        adminRequest(session, "/admin/cache/stats", null)
      ]);
      const categoryPage = toPage(categories);
      const itemPage = toPage(items);
      const packagePage = toPage(packages);
      const orderPage = toPage(orders);
      const reviewPage = toPage(reviews);
      const userPage = toPage(users);
      const employeePage = toPage(employees);
      const logPage = toPage(logs);
      setSnapshot({
        merchant,
        shopStatus,
        categories: categoryPage.rows,
        categoryOptions: toPage(categoryOptionsSnapshot).rows,
        categoryTotal: categoryPage.total,
        categoryFilter: filters.category,
        categoryPager: { ...pagination.category, total: categoryPage.total },
        items: itemPage.rows,
        itemTotal: itemPage.total,
        itemFilter: filters.item,
        itemPager: { ...pagination.item, total: itemPage.total },
        packages: packagePage.rows,
        packageTotal: packagePage.total,
        packageFilter: filters.package,
        packagePager: { ...pagination.package, total: packagePage.total },
        trend,
        orders: orderPage.rows,
        orderTotal: orderPage.total,
        orderFilter: filters.order,
        orderPager: { ...pagination.order, total: orderPage.total },
        reviews: reviewPage.rows,
        reviewTotal: reviewPage.total,
        reviewFilter: filters.review,
        reviewPager: { ...pagination.review, total: reviewPage.total },
        users: userPage.rows,
        userTotal: userPage.total,
        userFilter: filters.user,
        userPager: { ...pagination.user, total: userPage.total },
        employees: employeePage.rows,
        employeeTotal: employeePage.total,
        employeeFilter: filters.employee,
        employeePager: { ...pagination.employee, total: employeePage.total },
        logs: logPage.rows,
        logTotal: logPage.total,
        logFilter: filters.log,
        logPager: { ...pagination.log, total: logPage.total },
        cacheStats
      });
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  }, [session, pagination, filters, restoringSession]);

  useEffect(() => {
    load();
  }, [load]);

  if (restoringSession) {
    return <main className="session-recovery" aria-live="polite"><div className="loading-panel">正在恢复安全会话...</div></main>;
  }

  if (!session.token) {
    return (
      <main className="login-side" style={{ minHeight: "100vh" }}>
        <div className="login-card">
          <p className="eyebrow">管理员后台</p>
          <h2>请先登录</h2>
          <p className="muted">当前页面需要管理员身份。</p>
          <a className="button" href="./login.html">去登录</a>
        </div>
      </main>
    );
  }

  function saveBase() {
    const next = setApiBase(apiInput);
    setSession({ ...session, apiBase: next });
    showToast("接口地址已保存");
  }

  async function signOut() {
    await logout("admin", session);
    window.location.href = "./login.html";
  }

  function requestConfirm(nextConfirm) {
    setConfirm(nextConfirm);
  }

  function updatePagination(resource, next) {
    setPagination((current) => {
      const currentPager = current[resource] || { page: 1, pageSize: DEFAULT_ADMIN_PAGE_SIZE };
      const nextPageSize = Number(next.pageSize ?? currentPager.pageSize);
      const pageSize = ADMIN_PAGE_SIZE_OPTIONS.includes(nextPageSize) ? nextPageSize : currentPager.pageSize;
      const page = Math.max(1, Number(next.page ?? currentPager.page));
      return {
        ...current,
        [resource]: { page, pageSize }
      };
    });
  }

  function updateFilter(resource, keyword) {
    const nextFilter = typeof keyword === "string" ? { keyword } : keyword;
    setFilters((current) => ({
      ...current,
      [resource]: { ...current[resource], ...nextFilter }
    }));
    setPagination((current) => ({
      ...current,
      [resource]: { ...current[resource], page: 1 }
    }));
  }

  function updateStructuredFilter(resource, next) {
    updateFilter(resource, next);
  }

  async function handleConfirm() {
    if (!confirm) return;
    await confirm.onConfirm();
    setConfirm(null);
  }

  return (
    <div className="admin-shell">
      <aside className="admin-sidebar">
        <div>
          <div className="brand-lockup">
            <div className="brand-mark">L</div>
            <div>
              <p className="brand-title">Local Explorer</p>
              <p className="brand-subtitle">商户运营后台</p>
            </div>
          </div>
          <nav className="side-nav" aria-label="后台导航">
            {visibleNavItems.map((item) => {
              const Icon = item.icon;
              return (
                <a key={item.page} className={`side-link ${page === item.page ? "active" : ""}`} href={item.href}>
                  <Icon size={17} />
                  <span>{item.label}</span>
                </a>
              );
            })}
          </nav>
        </div>
        <div className="side-foot">
          <span>{session.userName || "运营员工"} · {canAdmin ? "管理员" : "员工"}</span>
          <button className="button-ghost" type="button" onClick={signOut}><LogOut size={16} />退出</button>
        </div>
      </aside>

      <main className="admin-main" id="main-content">
        <header className="topbar">
          <div>
            <p className="eyebrow">本地生活探索平台</p>
            <h1>{pageTitles[page] || "运营概览"}</h1>
          </div>
          <div className="top-actions">
            <details className="api-settings">
              <summary className="icon-button" aria-label="接口设置" title="接口设置">
                <Settings2 size={17} />
              </summary>
              <div className="api-settings-panel">
                <label className="field">
                  <span>接口地址</span>
                  <input aria-label="自定义接口地址" value={apiInput} placeholder="默认使用 localhost:8080" onChange={(event) => setApiInput(event.target.value)} />
                </label>
                <button className="button-ghost" type="button" onClick={saveBase}><Save size={16} />保存</button>
              </div>
            </details>
            <button className="button-ghost" type="button" onClick={load}><RefreshCcw size={16} />刷新</button>
            <a className="icon-button" aria-label="接口文档" href={`${session.apiBase.replace(/\/$/, "")}/doc.html`} target="_blank" rel="noreferrer"><ExternalLink size={17} /></a>
          </div>
        </header>

        {loading ? <div className="loading-state">加载运营数据中</div> : null}
        {error ? <div className="error-state">{error}</div> : null}
        {!loading && !error && snapshot ? (
          restrictedPage
            ? <div className="error-state">当前账号没有访问此页面的权限。</div>
            : <AdminPage page={page} snapshot={snapshot} session={session} onOpenDrawer={setDrawer} requestConfirm={requestConfirm} onToast={showToast} onReload={load} onPageChange={updatePagination} onSearch={updateFilter} onFilterChange={updateStructuredFilter} />
        ) : null}
      </main>
      {drawer ? (
        drawer.type === "user" && drawer.mode !== "edit"
          ? <UserDetailDrawer user={drawer.record} onClose={() => setDrawer(null)} />
          : <AdminDrawer drawer={drawer} session={session} onClose={() => setDrawer(null)} onToast={showToast} onReload={load} />
      ) : null}
      {confirm ? <ConfirmDialog confirm={confirm} onCancel={() => setConfirm(null)} onConfirm={handleConfirm} /> : null}
      <Toast message={toast} />
    </div>
  );
}

function AdminPage({ page, snapshot, session, onOpenDrawer, requestConfirm, onToast, onReload, onPageChange, onSearch, onFilterChange }) {
  if (page === "merchant") return <MerchantPage snapshot={snapshot} session={session} onToast={onToast} onReload={onReload} />;
  if (page === "categories") return <CategoriesPage rows={snapshot.categories} total={snapshot.categoryTotal} pager={snapshot.categoryPager} onPageChange={(next) => onPageChange("category", next)} searchKeyword={snapshot.categoryFilter.keyword} onSearch={(keyword) => onSearch("category", keyword)} session={session} onOpenDrawer={onOpenDrawer} requestConfirm={requestConfirm} onToast={onToast} onReload={onReload} />;
  if (page === "items") return <ItemsPage rows={snapshot.items} total={snapshot.itemTotal} pager={snapshot.itemPager} onPageChange={(next) => onPageChange("item", next)} searchKeyword={snapshot.itemFilter.keyword} onSearch={(keyword) => onSearch("item", keyword)} categories={snapshot.categoryOptions} session={session} canAdmin={session.role === "ADMIN"} onOpenDrawer={onOpenDrawer} requestConfirm={requestConfirm} onToast={onToast} onReload={onReload} />;
  if (page === "packages") return <PackagesPage rows={snapshot.packages} total={snapshot.packageTotal} pager={snapshot.packagePager} onPageChange={(next) => onPageChange("package", next)} searchKeyword={snapshot.packageFilter.keyword} onSearch={(keyword) => onSearch("package", keyword)} categories={snapshot.categoryOptions} items={snapshot.items} session={session} onOpenDrawer={onOpenDrawer} requestConfirm={requestConfirm} onToast={onToast} onReload={onReload} />;
  if (page === "orders") return <OrdersPage rows={snapshot.orders} total={snapshot.orderTotal} pager={snapshot.orderPager} onPageChange={(next) => onPageChange("order", next)} filter={snapshot.orderFilter} onFilterChange={(next) => onFilterChange("order", next)} session={session} canAdmin={session.role === "ADMIN"} requestConfirm={requestConfirm} onToast={onToast} onReload={onReload} />;
  if (page === "reviews") return <ReviewsPage rows={snapshot.reviews} total={snapshot.reviewTotal} pager={snapshot.reviewPager} onPageChange={(next) => onPageChange("review", next)} filter={snapshot.reviewFilter} onFilterChange={(next) => onFilterChange("review", next)} session={session} canAdmin={session.role === "ADMIN"} requestConfirm={requestConfirm} onToast={onToast} onReload={onReload} />;
  if (page === "export-jobs") return <ExportJobsPage session={session} requestConfirm={requestConfirm} onToast={onToast} />;
  if (page === "users") return <UsersPage rows={snapshot.users} total={snapshot.userTotal} pager={snapshot.userPager} onPageChange={(next) => onPageChange("user", next)} searchKeyword={snapshot.userFilter.keyword} onSearch={(keyword) => onSearch("user", keyword)} session={session} onOpenDrawer={onOpenDrawer} requestConfirm={requestConfirm} onToast={onToast} onReload={onReload} />;
  if (page === "employees") return <EmployeesPage rows={snapshot.employees} total={snapshot.employeeTotal} pager={snapshot.employeePager} onPageChange={(next) => onPageChange("employee", next)} searchKeyword={snapshot.employeeFilter.keyword} onSearch={(keyword) => onSearch("employee", keyword)} session={session} onOpenDrawer={onOpenDrawer} requestConfirm={requestConfirm} onToast={onToast} onReload={onReload} />;
  if (page === "operation-logs") return <LogsPage rows={snapshot.logs} total={snapshot.logTotal} pager={snapshot.logPager} onPageChange={(next) => onPageChange("log", next)} filter={snapshot.logFilter} onFilterChange={(next) => onFilterChange("log", next)} session={session} onToast={onToast} />;
  return <Dashboard snapshot={snapshot} session={session} onToast={onToast} onReload={onReload} />;
}

function Dashboard({ snapshot, session, onToast, onReload }) {
  const [savingStatus, setSavingStatus] = useState(false);
  const [cacheAction, setCacheAction] = useState("");
  const [cacheDomain, setCacheDomain] = useState("all");
  const isOpen = Number(snapshot.shopStatus) === 1;
  const cards = [
    { label: "总预约", value: snapshot.trend.totalOrders, icon: CalendarCheck, note: "近 7 天累计" },
    { label: "确认收入", value: money(snapshot.trend.confirmedRevenue), icon: CircleDollarSign, note: "确认/完成预约" },
    { label: "完成率", value: `${snapshot.trend.completionRate || 0}%`, icon: BarChart3, note: `完成${snapshot.trend.completedOrders || 0} · 取消${snapshot.trend.canceledOrders || 0}` },
    { label: "总评价", value: snapshot.trend.totalReviews, icon: MessageSquare, note: "近 7 天累计" },
    { label: "注册用户", value: snapshot.trend.totalUsers, icon: Users, note: "近 7 天新增" },
    { label: "待确认", value: snapshot.trend.pendingOrders, icon: Eye, note: "需要处理" }
  ];

  async function toggleShopStatus() {
    const nextStatus = Number(snapshot.shopStatus) === 1 ? 0 : 1;
    setSavingStatus(true);
    try {
      await request(session, `/admin/shop/${nextStatus}`, { method: "PUT" });
      onToast(nextStatus === 1 ? "门店已切换为营业中" : "门店已切换为休息中");
      onReload();
    } catch (err) {
      onToast(err.message);
    } finally {
      setSavingStatus(false);
    }
  }

  async function runCacheAction(action) {
    const isWarmup = action === "warmup";
    setCacheAction(action);
    try {
      await request(session, isWarmup ? "/admin/cache/warmup" : `/admin/cache/invalidate/${cacheDomain}`, {
        method: "POST"
      });
      onToast(isWarmup ? "缓存预热任务已提交" : "缓存失效已完成");
      window.setTimeout(onReload, isWarmup ? 500 : 100);
    } catch (err) {
      onToast(err.message);
    } finally {
      setCacheAction("");
    }
  }

  return (
    <div className="content-stack">
      <section className="metric-grid">
        {cards.map((card) => {
          const Icon = card.icon;
          return (
            <article className="metric-card" key={card.label}>
              <span className="icon-chip"><Icon size={18} /></span>
              <span>{card.label}</span>
              <strong>{card.value}</strong>
              <small>{card.note}</small>
            </article>
          );
        })}
      </section>

      {session.role === "ADMIN" && snapshot.cacheStats ? (
        <CacheStatusPanel
          stats={snapshot.cacheStats}
          busy={cacheAction}
          domain={cacheDomain}
          onDomainChange={setCacheDomain}
          onAction={runCacheAction}
        />
      ) : null}

      <section className="split-grid">
        <div className="panel">
          <PageHeader title="预约与评价趋势" eyebrow="7 日走势" />
          <TrendChart trend={snapshot.trend} />
        </div>
        <div className="panel">
          <PageHeader title={snapshot.merchant.name} eyebrow="商户状态">
            <div className="card-actions">
              <StatusPill status={isOpen ? { text: "营业中", tone: "ok" } : { text: "休息中", tone: "warn" }} />
              <button className={isOpen ? "button-ghost" : "button-secondary"} type="button" onClick={toggleShopStatus} disabled={savingStatus}>
                <Store size={16} />
                {savingStatus ? "切换中" : isOpen ? "暂停营业" : "开始营业"}
              </button>
            </div>
          </PageHeader>
          <p className="muted">{snapshot.merchant.slogan}</p>
          <div className="tag-row" style={{ marginTop: 16 }}>
            <span className="tag">特色项目 {snapshot.itemTotal ?? snapshot.items.length}</span>
            <span className="tag">套餐 {snapshot.packageTotal ?? snapshot.packages.length}</span>
            <span className="tag">评价 {snapshot.reviewTotal ?? snapshot.reviews.length}</span>
            {snapshot.merchant.businessHours ? <span className="tag">{snapshot.merchant.businessHours}</span> : null}
          </div>
        </div>
      </section>

      <section className="panel">
        <PageHeader title="最近预约" eyebrow="订单处理" />
        <OrderTable rows={snapshot.orders.slice(0, 5)} />
      </section>
    </div>
  );
}

function CacheStatusPanel({ stats, busy, domain, onDomainChange, onAction }) {
  const cache = stats.cache || {};
  const hits = Number(cache.l1Hits || 0) + Number(cache.l2Hits || 0);
  const reads = hits + Number(cache.databaseLoads || 0);
  const hitRate = reads ? `${Math.round((hits / reads) * 100)}%` : "-";
  const healthy = !cache.redisCircuitOpen;
  const domains = [
    ["all", "全部业务域"],
    ["category-list", "分类列表"],
    ["item-list", "项目列表"],
    ["item-detail", "项目详情"],
    ["package-list", "套餐列表"],
    ["package-detail", "套餐详情"],
    ["package-items", "套餐项目"],
    ["merchant-info", "商户信息"],
    ["shop-status", "营业状态"]
  ];

  return (
    <section className="panel cache-status-panel">
      <div className="cache-status-head">
        <div className="cache-status-title">
          <span className="icon-chip"><Database size={18} /></span>
          <h2>两级缓存</h2>
          <StatusPill status={healthy ? { text: "Redis可用", tone: "ok" } : { text: "降级运行", tone: "warn" }} />
        </div>
        <span className="muted">L1条目 {cache.l1Entries || 0}</span>
      </div>
      <div className="cache-stat-grid">
        <div><span>命中率</span><strong>{hitRate}</strong></div>
        <div><span>L1命中</span><strong>{cache.l1Hits || 0}</strong></div>
        <div><span>L2命中</span><strong>{cache.l2Hits || 0}</strong></div>
        <div><span>数据库回源</span><strong>{cache.databaseLoads || 0}</strong></div>
        <div><span>锁竞争</span><strong>{cache.lockContentions || 0}</strong></div>
        <div><span>Redis降级</span><strong>{cache.redisDegradations || 0}</strong></div>
      </div>
      <div className="cache-ops">
        <label className="cache-domain-select">
          <span>失效范围</span>
          <select value={domain} onChange={(event) => onDomainChange(event.target.value)}>
            {domains.map(([value, label]) => <option key={value} value={value}>{label}</option>)}
          </select>
        </label>
        <button className="button-ghost" type="button" disabled={Boolean(busy)} onClick={() => onAction("invalidate")}>
          <RefreshCcw size={16} />{busy === "invalidate" ? "处理中" : "立即失效"}
        </button>
        <button className="button-secondary" type="button" disabled={Boolean(busy) || stats.warmupRunning} onClick={() => onAction("warmup")}>
          <Database size={16} />{busy === "warmup" || stats.warmupRunning ? "预热中" : "预热首页"}
        </button>
      </div>
    </section>
  );
}

function TrendChart({ trend }) {
  const values = trend.orderCounts || [];
  const reviewValues = trend.reviewCounts || [];
  const max = Math.max(1, ...values, ...reviewValues);
  const points = values.map((value, index) => `${40 + index * 70},${190 - (value / max) * 142}`).join(" ");
  const reviewPoints = reviewValues.map((value, index) => `${40 + index * 70},${190 - (value / max) * 142}`).join(" ");

  return (
    <svg className="chart" viewBox="0 0 500 230" role="img" aria-label="预约与评价趋势图">
      {[48, 96, 144, 192].map((y) => <line className="chart-grid" key={y} x1="36" x2="472" y1={y} y2={y} />)}
      <polyline className="chart-line" points={points} stroke="var(--brand-2)" />
      <polyline className="chart-line" points={reviewPoints} stroke="var(--coral)" />
      {(trend.dates || []).map((date, index) => <text key={date} x={34 + index * 70} y="218" fill="var(--muted)" fontSize="12">{date}</text>)}
    </svg>
  );
}

function MerchantPage({ snapshot, session, onToast, onReload }) {
  const [form, setForm] = useState(snapshot.merchant);
  const [error, setError] = useState("");
  async function submit(event) {
    event.preventDefault();
    setError("");
    const missingField = merchantFields.find((field) => field.required && !String(form[field.key] || "").trim());
    if (missingField) {
      setError(`${missingField.label}不能为空`);
      return;
    }
    try {
      await request(session, "/admin/merchant/info", { method: "PUT", body: JSON.stringify(form) });
      onToast("商户资料已保存");
      onReload();
    } catch (err) {
      setError(err.message);
    }
  }

  return (
    <section className="split-grid">
      <form className="panel form-grid" onSubmit={submit}>
        <PageHeader title="商户资料" eyebrow="基础信息" />
        {error ? <div className="form-error">{error}</div> : null}
        {merchantFields.map((field) => (
          <label className="field" key={field.key}>
            <span>{field.label}</span>
            <input
              value={form[field.key] || ""}
              maxLength={field.maxLength}
              required={field.required}
              onChange={(event) => setForm({ ...form, [field.key]: event.target.value })}
            />
          </label>
        ))}
        <button className="button" type="submit"><Save size={17} />保存资料</button>
      </form>
      <div className="panel">
        <PageHeader title="用户端预览" eyebrow="品牌呈现" />
        <div className="hero-image" style={{ minHeight: 260 }}>
          <img src="../assets/images/citywalk.webp" alt="城市生活探店馆" />
        </div>
        <h2 style={{ marginTop: 16 }}>{form.name}</h2>
        <p className="muted">{form.slogan}</p>
        <p className="muted">{form.businessHours} · {form.notice}</p>
      </div>
    </section>
  );
}

function SearchableTablePage({ title, eyebrow, rows, total, pager, onPageChange, columns, actionLabel, onAction, headerActions, placeholder = "搜索名称", searchKeyword = "", onSearch }) {
  const [keyword, setKeyword] = useState(searchKeyword);
  useEffect(() => setKeyword(searchKeyword), [searchKeyword]);
  const hasKeyword = Boolean(searchKeyword.trim());
  const totalCount = total ?? rows.length;

  function submitSearch(event) {
    event.preventDefault();
    onSearch?.(keyword.trim());
  }

  return (
    <section className="panel">
      <PageHeader title={title} eyebrow={eyebrow}>
        <div className="card-actions">
          {headerActions}
          {onAction ? <button className="button" type="button" onClick={onAction}><Plus size={16} />{actionLabel}</button> : null}
        </div>
      </PageHeader>
      <form className="toolbar" style={{ marginBottom: 14 }} onSubmit={submitSearch}>
        <label className="search-field">
          <input value={keyword} placeholder={placeholder} onChange={(event) => setKeyword(event.target.value)} />
        </label>
        <span className="table-summary">
          {hasKeyword ? <>关键词筛选，共 {totalCount} 条</> : <>共 {totalCount} 条</>}
        </span>
        <button className="button-ghost" type="submit"><Search size={16} />搜索</button>
        <button className="button-ghost" type="button" onClick={() => { setKeyword(""); onSearch?.(""); }} disabled={!hasKeyword && !keyword}>
          清空
        </button>
      </form>
      <DataTable rows={rows} columns={columns} />
      <PaginationBar total={totalCount} pager={pager} onPageChange={onPageChange} />
    </section>
  );
}

function PaginationBar({ total, pager, onPageChange }) {
  if (!pager || !onPageChange) return null;
  const totalCount = total ?? 0;
  const totalPages = Math.max(1, Math.ceil(totalCount / pager.pageSize));
  const start = totalCount ? (pager.page - 1) * pager.pageSize + 1 : 0;
  const end = Math.min(totalCount, pager.page * pager.pageSize);

  return (
    <div className="pagination-bar">
      <span className="pagination-meta">显示 {start}-{end} / {totalCount}</span>
      <div className="pagination-controls">
        <button className="button-ghost" type="button" onClick={() => onPageChange({ page: pager.page - 1 })} disabled={pager.page <= 1}>
          <ChevronLeft size={16} />
          上一页
        </button>
        <span className="pagination-meta">第 {pager.page} / {totalPages} 页</span>
        <button className="button-ghost" type="button" onClick={() => onPageChange({ page: pager.page + 1 })} disabled={pager.page >= totalPages}>
          下一页
          <ChevronRight size={16} />
        </button>
        <label className="pagination-size">
          <span>每页</span>
          <select value={pager.pageSize} onChange={(event) => onPageChange({ page: 1, pageSize: Number(event.target.value) })}>
            {ADMIN_PAGE_SIZE_OPTIONS.map((size) => <option key={size} value={size}>{size}条</option>)}
          </select>
        </label>
      </div>
    </div>
  );
}

function CategoriesPage({ rows, total, pager, onPageChange, searchKeyword, onSearch, session, onOpenDrawer, requestConfirm, onToast, onReload }) {
  async function toggleCategoryStatus(row) {
    const nextStatus = Number(row.status) === 1 ? 0 : 1;
    try {
      await request(session, `/admin/category/status/${nextStatus}?id=${row.id}`, { method: "POST" });
      onToast(nextStatus === 1 ? "分类已启用" : "分类已停用");
      onReload();
    } catch (err) {
      onToast(err.message);
    }
  }

  function deleteCategory(row) {
    requestConfirm({
      title: "删除分类",
      message: `确认删除分类「${row.name}」吗？`,
      onConfirm: async () => {
        await request(session, `/admin/category?id=${row.id}`, { method: "DELETE" });
        onToast("分类已删除");
        onReload();
      }
    });
  }

  return (
    <SearchableTablePage
      title="内容分类"
      eyebrow="内容货架"
      rows={rows}
      total={total}
      pager={pager}
      onPageChange={onPageChange}
      searchKeyword={searchKeyword}
      onSearch={onSearch}
      actionLabel="新增分类"
      onAction={() => onOpenDrawer({ type: "category", title: "新增分类", categories: rows })}
      columns={[
        { key: "name", label: "名称" },
        { key: "type", label: "类型", render: (row) => Number(row.type) === 2 ? "套餐分类" : "项目分类" },
        { key: "sort", label: "排序", num: true },
        { key: "status", label: "状态", render: (row) => <StatusPill status={enabledStatus(row.status)} /> },
        { key: "actions", label: "操作", render: (row) => <RowActions onEdit={() => onOpenDrawer({ type: "category", mode: "edit", title: "编辑分类", record: row, categories: rows })} onToggleStatus={() => toggleCategoryStatus(row)} onDelete={() => deleteCategory(row)} status={row.status} /> }
      ]}
    />
  );
}

function ItemsPage({ rows, total, pager, onPageChange, searchKeyword, onSearch, categories, session, canAdmin, onOpenDrawer, requestConfirm, onToast, onReload }) {
  async function openItemEditor(row) {
    try {
      const detail = await request(session, `/admin/explore-item/${row.id}`);
      onOpenDrawer({ type: "item", mode: "edit", title: "编辑项目", record: { ...row, ...detail }, categories });
    } catch (err) {
      onToast(err.message);
    }
  }

  function deleteItem(row) {
    requestConfirm({
      title: "删除项目",
      message: `确认删除项目「${row.name}」吗？`,
      onConfirm: async () => {
        await request(session, `/admin/explore-item?ids=${row.id}`, { method: "DELETE" });
        onToast("项目已删除");
        onReload();
      }
    });
  }

  async function toggleItemStatus(row) {
    const nextStatus = Number(row.status) === 1 ? 0 : 1;
    try {
      await request(session, `/admin/explore-item/status/${nextStatus}?id=${row.id}`, { method: "POST" });
      onToast(nextStatus === 1 ? "项目已启用" : "项目已停用");
      onReload();
    } catch (err) {
      onToast(err.message);
    }
  }

  return (
    <SearchableTablePage
      title="特色项目"
      eyebrow="可预约内容"
      rows={rows}
      total={total}
      pager={pager}
      onPageChange={onPageChange}
      searchKeyword={searchKeyword}
      onSearch={onSearch}
      actionLabel="新增项目"
      onAction={() => onOpenDrawer({ type: "item", title: "新增项目", categories })}
      columns={[
        { key: "image", label: "图片", render: (row) => <img className="thumb" src={row.image} alt={row.name} /> },
        { key: "name", label: "名称" },
        { key: "categoryName", label: "分类" },
        { key: "district", label: "商圈" },
        { key: "durationMinutes", label: "时长", render: (row) => durationText(row.durationMinutes) },
        { key: "capacity", label: "余量", num: true, render: (row) => remainingText(row) },
        { key: "price", label: "价格", num: true, render: (row) => money(row.price) },
        { key: "status", label: "状态", render: (row) => <StatusPill status={enabledStatus(row.status)} /> },
        { key: "actions", label: "操作", render: (row) => <RowActions onEdit={() => openItemEditor(row)} onToggleStatus={() => toggleItemStatus(row)} onDelete={() => deleteItem(row)} status={row.status} /> }
      ]}
    />
  );
}

function PackagesPage({ rows, total, pager, onPageChange, searchKeyword, onSearch, categories, items, session, onOpenDrawer, requestConfirm, onToast, onReload }) {
  async function openPackageEditor(row) {
    try {
      const detail = await request(session, `/admin/explore-package/${row.id}`);
      onOpenDrawer({ type: "package", mode: "edit", title: "编辑套餐", record: { ...row, ...detail }, categories, items });
    } catch (err) {
      onToast(err.message);
    }
  }

  function deletePackage(row) {
    requestConfirm({
      title: "删除套餐",
      message: `确认删除套餐「${row.name}」吗？`,
      onConfirm: async () => {
        await request(session, `/admin/explore-package?ids=${row.id}`, { method: "DELETE" });
        onToast("套餐已删除");
        onReload();
      }
    });
  }

  async function togglePackageStatus(row) {
    const nextStatus = Number(row.status) === 1 ? 0 : 1;
    try {
      await request(session, `/admin/explore-package/status/${nextStatus}?id=${row.id}`, { method: "POST" });
      onToast(nextStatus === 1 ? "套餐已启用" : "套餐已停用");
      onReload();
    } catch (err) {
      onToast(err.message);
    }
  }

  return (
    <SearchableTablePage
      title="探索套餐"
      eyebrow="组合售卖"
      rows={rows}
      total={total}
      pager={pager}
      onPageChange={onPageChange}
      searchKeyword={searchKeyword}
      onSearch={onSearch}
      actionLabel="新增套餐"
      onAction={() => onOpenDrawer({ type: "package", title: "新增套餐", categories, items })}
      columns={[
        { key: "image", label: "图片", render: (row) => <img className="thumb" src={row.image} alt={row.name} /> },
        { key: "name", label: "套餐" },
        { key: "categoryName", label: "分类" },
        { key: "durationMinutes", label: "时长", render: (row) => durationText(row.durationMinutes) },
        { key: "capacity", label: "余量", num: true, render: (row) => remainingText(row) },
        { key: "price", label: "价格", num: true, render: (row) => money(row.price) },
        { key: "status", label: "状态", render: (row) => <StatusPill status={enabledStatus(row.status)} /> },
        { key: "actions", label: "操作", render: (row) => <RowActions onEdit={() => openPackageEditor(row)} onToggleStatus={() => togglePackageStatus(row)} onDelete={() => deletePackage(row)} status={row.status} /> }
      ]}
    />
  );
}

function OrderTable({ rows, actions }) {
  const columns = [
    { key: "orderNo", label: "订单号" },
    { key: "itemName", label: "预约内容" },
    { key: "userName", label: "用户" },
    { key: "channel", label: "来源" },
    { key: "paymentStatus", label: "支付", render: (row) => <StatusPill status={paymentStatus(row.paymentStatus)} /> },
    { key: "amount", label: "金额", num: true, render: (row) => money(row.amount) },
    { key: "reserveTime", label: "预约时间", render: (row) => dateText(row.reserveTime) },
    { key: "status", label: "状态", render: (row) => <StatusPill status={orderStatus(row.status)} /> }
  ];
  if (actions) {
    columns.push({ key: "actions", label: "操作", render: actions });
  }

  return (
    <DataTable
      rows={rows}
      columns={columns}
    />
  );
}

function OrdersPage({ rows, total, pager, onPageChange, filter, onFilterChange, session, canAdmin, requestConfirm, onToast, onReload }) {
  const [detailOrder, setDetailOrder] = useState(null);
  const [draftFilter, setDraftFilter] = useState(filter);
  useEffect(() => setDraftFilter(filter), [filter]);
  const hasOrderFilter = Boolean(filter.keyword.trim() || filter.status);
  const orderTotalCount = total ?? rows.length;

  function requestOrderStatusChange(row, status, title, message) {
    requestConfirm({
      title,
      message,
      confirmText: "确认操作",
      onConfirm: async () => {
        await request(session, `/admin/explore-order/status?id=${row.id}&status=${status}`, { method: "PUT" });
        onToast("订单状态已更新");
        onReload();
      }
    });
  }

  function submitOrderFilters(event) {
    event.preventDefault();
    onFilterChange({ keyword: draftFilter.keyword.trim(), status: draftFilter.status });
  }

  function resetOrderFilters() {
    setDraftFilter({ keyword: "", status: "" });
    onFilterChange({ keyword: "", status: "" });
  }

  return (
    <>
      <section className="panel">
        <PageHeader title="预约订单" eyebrow="履约管理">
          <ExportCreateAction
            session={session}
            exportType="ORDER"
            filters={{
              keyword: filter.keyword,
              dataStatus: filter.status === "" ? undefined : Number(filter.status)
            }}
            onToast={onToast}
          />
        </PageHeader>
        <form className="toolbar toolbar-filters" style={{ marginBottom: 14 }} onSubmit={submitOrderFilters}>
          <input
            aria-label="搜索订单"
            value={draftFilter.keyword}
            placeholder="订单号、用户、联系人、手机号"
            onChange={(event) => setDraftFilter({ ...draftFilter, keyword: event.target.value })}
          />
          <select aria-label="订单状态" value={draftFilter.status} onChange={(event) => setDraftFilter({ ...draftFilter, status: event.target.value })}>
            <option value="">全部状态</option>
            <option value="0">待确认</option>
            <option value="1">已确认</option>
            <option value="2">已完成</option>
            <option value="3">已取消</option>
            <option value="4">系统超时取消</option>
          </select>
          <span className="table-summary">
            {hasOrderFilter ? <>关键词筛选，共 {orderTotalCount} 条</> : <>共 {orderTotalCount} 条</>}
          </span>
          <button className="button-ghost" type="submit"><Search size={16} />搜索</button>
          <button className="button-ghost" type="button" onClick={resetOrderFilters} disabled={!hasOrderFilter && !draftFilter.keyword.trim() && !draftFilter.status}>
            <RefreshCcw size={16} />
            清空
          </button>
        </form>
        <OrderTable
          rows={rows}
          actions={(row) => {
            const orderActions = orderActionsForStatus(row.status);
            return (
              <div className="table-actions">
                <button className="button-ghost table-action table-action-primary" type="button" onClick={() => setDetailOrder(row)}>
                  <Eye size={14} />
                  详情
                </button>
                {orderActions.canConfirm && (
                  <button className="button-ghost table-action table-action-state" type="button" onClick={() => requestOrderStatusChange(row, 1, "确认预约", `确认接受「${row.itemName}」的预约吗？`)}>确认预约</button>
                )}
                {orderActions.canComplete && (
                  <button className="button-ghost table-action table-action-state" type="button" onClick={() => requestOrderStatusChange(row, 2, "标记完成", `确认将「${row.itemName}」标记为已完成吗？`)}>标记完成</button>
                )}
                {orderActions.canCancel && (
                  <button className="button-danger table-action table-action-danger" type="button" onClick={() => requestOrderStatusChange(row, 3, "取消预约", `确认取消「${row.itemName}」的预约吗？`)}>取消预约</button>
                )}
                {!orderActions.canConfirm && !orderActions.canComplete && !orderActions.canCancel ? (
                  <span className="muted">无需操作</span>
                ) : null}
              </div>
            );
          }}
        />
        <PaginationBar total={orderTotalCount} pager={pager} onPageChange={onPageChange} />
      </section>
      {detailOrder ? <OrderDetailDrawer order={detailOrder} onClose={() => setDetailOrder(null)} /> : null}
    </>
  );
}

function OrderDetailDrawer({ order, onClose }) {
  const stats = [
    { label: "人数", value: Number(order.peopleCount || 0) },
    { label: "金额", value: money(order.amount) },
    { label: "状态", value: orderStatus(order.status).text }
  ];

  return (
    <Drawer title="订单详情" onClose={onClose}>
      <div className="detail-stack">
        <div>
          <p className="eyebrow">预约内容</p>
          <h3>{order.itemName || "-"}</h3>
          <p className="muted">{order.orderNo || "-"} · {order.userName || "-"}</p>
          <p className="muted">{order.channel || "用户端预约"} · {paymentStatus(order.paymentStatus).text}</p>
        </div>
        <div className="detail-stat-grid">
          {stats.map((stat) => (
            <span className="detail-stat" key={stat.label}>
              <strong>{stat.value}</strong>
              <small>{stat.label}</small>
            </span>
          ))}
        </div>
        <div>
          <p className="eyebrow">联系信息</p>
          <p className="muted">联系人：{order.contactName || "-"}</p>
          <p className="muted">手机号：{order.contactPhone || "-"}</p>
          <p className="muted">预约时间：{dateText(order.reserveTime)}</p>
          <p className="muted">备注：{order.remark || "-"}</p>
        </div>
      </div>
    </Drawer>
  );
}

function ReviewsPage({ rows, total, pager, onPageChange, filter, onFilterChange, session, canAdmin, requestConfirm, onToast, onReload }) {
  const [replyTarget, setReplyTarget] = useState(null);
  const [draftFilter, setDraftFilter] = useState(filter);
  useEffect(() => setDraftFilter(filter), [filter]);
  const hasReviewFilter = Boolean(filter.keyword.trim() || filter.rating || filter.replyState);
  const reviewTotalCount = total ?? rows.length;

  function deleteReview(row) {
    requestConfirm({
      title: "删除评价",
      message: `确认删除评价「${row.itemName}」吗？`,
      onConfirm: async () => {
        await request(session, `/admin/review?ids=${row.id}`, { method: "DELETE" });
        onToast("评价已删除");
        onReload();
      }
    });
  }

  async function replyReview(row, content) {
    try {
      await request(session, "/admin/review/reply", {
        method: "PUT",
        body: JSON.stringify({ id: row.id, replyContent: content.trim() })
      });
      onToast("回复已保存");
      setReplyTarget(null);
      onReload();
    } catch (err) {
      onToast(err.message);
      throw err;
    }
  }

  function submitReviewFilters(event) {
    event.preventDefault();
    onFilterChange({ keyword: draftFilter.keyword.trim(), rating: draftFilter.rating, replyState: draftFilter.replyState });
  }

  function resetReviewFilters() {
    setDraftFilter({ keyword: "", rating: "", replyState: "" });
    onFilterChange({ keyword: "", rating: "", replyState: "" });
  }

  return (
    <>
      <section className="panel">
        <PageHeader title="用户评价" eyebrow="口碑反馈">
          <ExportCreateAction
            session={session}
            exportType="REVIEW"
            filters={{
              keyword: filter.keyword,
              rating: filter.rating === "" ? undefined : Number(filter.rating),
              replyState: filter.replyState
            }}
            onToast={onToast}
          />
        </PageHeader>
        <form className="toolbar toolbar-filters" style={{ marginBottom: 14 }} onSubmit={submitReviewFilters}>
          <input
            aria-label="搜索评价"
            value={draftFilter.keyword}
            placeholder="项目、用户、评价内容"
            onChange={(event) => setDraftFilter({ ...draftFilter, keyword: event.target.value })}
          />
          <select aria-label="评价评分" value={draftFilter.rating} onChange={(event) => setDraftFilter({ ...draftFilter, rating: event.target.value })}>
            <option value="">全部评分</option>
            <option value="5">5 分</option>
            <option value="4">4 分</option>
            <option value="3">3 分</option>
            <option value="2">2 分</option>
            <option value="1">1 分</option>
          </select>
          <select aria-label="回复状态" value={draftFilter.replyState} onChange={(event) => setDraftFilter({ ...draftFilter, replyState: event.target.value })}>
            <option value="">全部回复状态</option>
            <option value="unreplied">未回复</option>
            <option value="replied">已回复</option>
          </select>
          <span className="table-summary">
            {hasReviewFilter ? <>关键词筛选，共 {reviewTotalCount} 条</> : <>共 {reviewTotalCount} 条</>}
          </span>
          <button className="button-ghost" type="submit"><Search size={16} />搜索</button>
          <button className="button-ghost" type="button" onClick={resetReviewFilters} disabled={!hasReviewFilter && !draftFilter.keyword.trim() && !draftFilter.rating && !draftFilter.replyState}>
            <RefreshCcw size={16} />
            清空
          </button>
        </form>
        <div className="review-list">
          {rows.length ? rows.map((row) => (
            <article className="review-item" key={row.id}>
              <div className="section-head" style={{ marginBottom: 0 }}>
                <h3>{row.itemName}</h3>
                <div className="table-actions">
                  <span className="price">{row.rating}.0</span>
                  <button className="button-ghost table-action table-action-primary" type="button" onClick={() => setReplyTarget(row)}>
                    <MessageSquare size={14} />
                    {row.replyContent ? "修改回复" : "回复"}
                  </button>
                  <button className="button-danger table-action table-action-danger" type="button" onClick={() => deleteReview(row)}>
                    <Trash2 size={14} />
                    删除
                  </button>
                </div>
              </div>
              <p className="muted">{row.content}</p>
              {row.replyContent ? (
                <p className="review-reply">
                  商家回复：{row.replyContent}
                  {row.replyTime ? <span> · {dateText(row.replyTime)}</span> : null}
                </p>
              ) : null}
              <span className="muted">{row.userName} · {dateText(row.createTime)}</span>
            </article>
          )) : <div className="empty-state">暂无匹配评价</div>}
        </div>
        <PaginationBar total={reviewTotalCount} pager={pager} onPageChange={onPageChange} />
      </section>
      {replyTarget ? <ReviewReplyDrawer review={replyTarget} onClose={() => setReplyTarget(null)} onSubmit={(content) => replyReview(replyTarget, content)} /> : null}
    </>
  );
}

function ReviewReplyDrawer({ review, onClose, onSubmit }) {
  const [content, setContent] = useState(review.replyContent || "");
  const [error, setError] = useState("");
  const [submitting, setSubmitting] = useState(false);

  async function submit(event) {
    event.preventDefault();
    if (!content.trim()) {
      setError("请输入回复内容");
      return;
    }
    if (content.length > 500) {
      setError("回复内容不能超过500个字符");
      return;
    }
    setSubmitting(true);
    setError("");
    try {
      await onSubmit(content);
    } catch (err) {
      setError(err.message);
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <Drawer title={review.replyContent ? "修改评价回复" : "回复评价"} onClose={onClose}>
      <form className="form-grid" onSubmit={submit}>
        <div>
          <p className="eyebrow">原评价</p>
          <h3>{review.itemName}</h3>
          <p className="muted">{review.userName} · {dateText(review.createTime)} · {review.rating}.0 分</p>
          <p className="review-reply">{review.content}</p>
        </div>
        {error ? <div className="form-error">{error}</div> : null}
        <label className="field">
          <span>商家回复</span>
          <textarea value={content} rows={6} maxLength={500} required onChange={(event) => setContent(event.target.value)} placeholder="输入给用户看的正式回复" />
        </label>
        <div className="button-row">
          <button className="button" type="submit" disabled={submitting}><Save size={17} />{submitting ? "保存中" : "保存回复"}</button>
          <button className="button-ghost" type="button" onClick={onClose}>取消</button>
        </div>
      </form>
    </Drawer>
  );
}

function UsersPage({ rows, total, pager, onPageChange, searchKeyword, onSearch, session, onOpenDrawer, requestConfirm, onToast, onReload }) {
  async function openUserEditor(row) {
    try {
      const detail = await request(session, `/admin/user-manage/${row.id}`);
      onOpenDrawer({ type: "user", mode: "edit", title: "编辑用户", record: { ...row, ...detail } });
    } catch (err) {
      onToast(err.message);
    }
  }

  function resetUserPassword(row) {
    requestConfirm({
      title: "重置密码",
      message: `确认将用户「${row.name || row.phone}」的密码重置为 123456 吗？`,
      confirmText: "确认重置",
      onConfirm: async () => {
        await request(session, `/admin/user-manage/${row.id}/password/reset`, { method: "PUT" });
        onToast("用户密码已重置为 123456");
        onReload();
      }
    });
  }

  async function toggleUserStatus(row) {
    const nextStatus = Number(row.status) === 1 ? 0 : 1;
    try {
      await request(session, `/admin/user-manage/status/${nextStatus}?id=${row.id}`, { method: "POST" });
      onToast(nextStatus === 1 ? "用户已启用" : "用户已禁用");
      onReload();
    } catch (err) {
      onToast(err.message);
    }
  }

  return (
    <SearchableTablePage
      title="用户管理"
      eyebrow="注册用户"
      rows={rows}
      total={total}
      pager={pager}
      onPageChange={onPageChange}
      searchKeyword={searchKeyword}
      onSearch={onSearch}
      headerActions={<ExportCreateAction session={session} exportType="USER" filters={userKeywordFilter(searchKeyword)} onToast={onToast} />}
      columns={[
        { key: "name", label: "姓名" },
        { key: "phone", label: "手机号" },
        { key: "orderCount", label: "预约数", num: true, render: (row) => Number(row.orderCount || 0) },
        { key: "browseCount", label: "浏览数", num: true, render: (row) => Number(row.browseCount || 0) },
        { key: "favoriteCount", label: "收藏数", num: true, render: (row) => Number(row.favoriteCount || 0) },
        { key: "status", label: "状态", render: (row) => <StatusPill status={enabledStatus(row.status)} /> },
        { key: "createTime", label: "注册时间", render: (row) => dateText(row.createTime) },
        {
          key: "actions",
          label: "操作",
          render: (row) => (
            <div className="table-actions">
                  <button className="button-ghost table-action table-action-primary" type="button" onClick={() => onOpenDrawer({ type: "user", title: "用户详情", record: row })}>
                    <Eye size={14} />
                    详情
                  </button>
                  <RowActions onEdit={() => openUserEditor(row)} onToggleStatus={() => toggleUserStatus(row)} onDelete={null} status={row.status} />
                  <button className="button-ghost table-action table-action-state" type="button" onClick={() => resetUserPassword(row)}>
                    <RefreshCcw size={14} />
                    重置密码
                  </button>
            </div>
          )
        }
      ]}
    />
  );
}

function UserDetailDrawer({ user, onClose }) {
  const stats = [
    { label: "预约数", value: Number(user.orderCount || 0) },
    { label: "浏览数", value: Number(user.browseCount || 0) },
    { label: "收藏数", value: Number(user.favoriteCount || 0) }
  ];

  return (
    <Drawer title="用户详情" onClose={onClose}>
      <div className="detail-stack">
        <div>
          <p className="eyebrow">基础信息</p>
          <h3>{user.name || "未命名用户"}</h3>
          <p className="muted">{user.phone || "-"} · {user.sex === "0" ? "女" : user.sex === "1" ? "男" : "未填写性别"}</p>
          <p className="muted">注册时间：{dateText(user.createTime)}</p>
        </div>
        <div className="detail-stat-grid">
          {stats.map((stat) => (
            <span className="detail-stat" key={stat.label}>
              <strong>{stat.value}</strong>
              <small>{stat.label}</small>
            </span>
          ))}
        </div>
      </div>
    </Drawer>
  );
}

function EmployeesPage({ rows, total, pager, onPageChange, searchKeyword, onSearch, session, onOpenDrawer, requestConfirm, onToast, onReload }) {
  async function openEmployeeEditor(row) {
    try {
      const detail = await request(session, `/admin/employee/${row.id}`);
      onOpenDrawer({ type: "employee", mode: "edit", title: "编辑员工", record: { ...row, ...detail } });
    } catch (err) {
      onToast(err.message);
    }
  }

  async function toggleEmployeeStatus(row) {
    const nextStatus = Number(row.status) === 1 ? 0 : 1;
    try {
      await request(session, `/admin/employee/status/${nextStatus}?id=${row.id}`, { method: "POST" });
      onToast(nextStatus === 1 ? "员工已启用" : "员工已禁用");
      onReload();
    } catch (err) {
      onToast(err.message);
    }
  }

  function deleteEmployee(row) {
    requestConfirm({
      title: "删除员工",
      message: `确认删除员工「${row.name}」吗？`,
      onConfirm: async () => {
        await request(session, `/admin/employee?id=${row.id}`, { method: "DELETE" });
        onToast("员工已删除");
        onReload();
      }
    });
  }

  return (
    <SearchableTablePage
      title="员工管理"
      eyebrow="后台账号"
      rows={rows}
      total={total}
      pager={pager}
      onPageChange={onPageChange}
      searchKeyword={searchKeyword}
      onSearch={onSearch}
      actionLabel="新增员工"
      onAction={() => onOpenDrawer({ type: "employee", title: "新增员工" })}
      columns={[
        { key: "name", label: "姓名" },
        { key: "username", label: "账号" },
        { key: "phone", label: "手机号" },
        { key: "status", label: "状态", render: (row) => <StatusPill status={enabledStatus(row.status)} /> },
        {
          key: "actions",
          label: "操作",
          render: (row) => {
            const isDefaultAdmin = Number(row.id) === 1;
            return (
              <RowActions
                onEdit={() => openEmployeeEditor(row)}
                onToggleStatus={isDefaultAdmin ? null : () => toggleEmployeeStatus(row)}
                onDelete={isDefaultAdmin ? null : () => deleteEmployee(row)}
                status={row.status}
              />
            );
          }
        }
      ]}
    />
  );
}

function exportJobStatus(status) {
  const states = {
    PENDING: { text: "排队中", tone: "warn" },
    RUNNING: { text: "生成中", tone: "info" },
    SUCCEEDED: { text: "可下载", tone: "ok" },
    FAILED: { text: "失败", tone: "off" },
    CANCELED: { text: "已取消", tone: "off" },
    EXPIRED: { text: "已过期", tone: "off" }
  };
  return states[status] || { text: status || "未知", tone: "off" };
}

function exportTypeText(type) {
  return ({ ORDER: "预约订单", USER: "用户", REVIEW: "用户评价", OPERATION_LOG: "操作日志" })[type] || type || "-";
}

function fileSizeText(bytes) {
  const value = Number(bytes || 0);
  if (!value) return "-";
  if (value < 1024) return `${value} B`;
  if (value < 1024 * 1024) return `${(value / 1024).toFixed(1)} KB`;
  return `${(value / 1024 / 1024).toFixed(1)} MB`;
}

function ExportJobsPage({ session, requestConfirm, onToast }) {
  const [rows, setRows] = useState([]);
  const [total, setTotal] = useState(0);
  const [stats, setStats] = useState(null);
  const [pager, setPager] = useState({ page: 1, pageSize: 20 });
  const [filter, setFilter] = useState({ exportType: "", status: "" });
  const [draftFilter, setDraftFilter] = useState(filter);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [error, setError] = useState("");
  const pollAttempt = useRef(0);

  const loadJobs = useCallback(async (silent = false) => {
    if (silent) setRefreshing(true); else setLoading(true);
    try {
      const [pageData, statsData] = await Promise.all([
        request(session, pageUrl("/admin/export-jobs/page", pager, filter)),
        session.role === "ADMIN" ? request(session, "/admin/export-jobs/stats") : Promise.resolve(null)
      ]);
      const page = toPage(pageData);
      setRows(page.rows);
      setTotal(page.total);
      setStats(statsData);
      setError("");
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  }, [session, pager, filter]);

  useEffect(() => {
    loadJobs(false);
  }, [loadJobs]);

  useEffect(() => {
    const active = rows.some((row) => row.status === "PENDING" || row.status === "RUNNING");
    if (!active) {
      pollAttempt.current = 0;
      return undefined;
    }
    const delays = [1500, 2500, 4000, 7000, 10000];
    const delay = delays[Math.min(pollAttempt.current, delays.length - 1)];
    const timer = window.setTimeout(() => {
      pollAttempt.current += 1;
      loadJobs(true);
    }, delay);
    return () => window.clearTimeout(timer);
  }, [rows, loadJobs]);

  function submitFilters(event) {
    event.preventDefault();
    setFilter({ ...draftFilter });
    setPager((current) => ({ ...current, page: 1 }));
  }

  function resetFilters() {
    const empty = { exportType: "", status: "" };
    setDraftFilter(empty);
    setFilter(empty);
    setPager((current) => ({ ...current, page: 1 }));
  }

  function cancelJob(job) {
    requestConfirm({
      title: "取消导出任务",
      message: `确认取消${exportTypeText(job.exportType)}导出吗？`,
      confirmText: "确认取消",
      onConfirm: async () => {
        await request(session, `/admin/export-jobs/${job.jobId}/cancel`, { method: "POST" });
        onToast("导出任务已取消");
        loadJobs(true);
      }
    });
  }

  async function retryJob(job) {
    try {
      await request(session, `/admin/export-jobs/${job.jobId}/retry`, { method: "POST" });
      onToast("导出任务已重新排队");
      loadJobs(true);
    } catch (err) {
      onToast(err.message);
    }
  }

  async function downloadJob(job) {
    try {
      await downloadFile(session, `/admin/export-jobs/${job.jobId}/download`, job.fileName || `export.${job.fileFormat.toLowerCase()}`);
      onToast("文件下载已开始");
    } catch (err) {
      onToast(err.message);
    }
  }

  const columns = [
    { key: "exportType", label: "数据", render: (row) => exportTypeText(row.exportType) },
    { key: "fileFormat", label: "格式" },
    { key: "status", label: "状态", render: (row) => <StatusPill status={exportJobStatus(row.status)} /> },
    {
      key: "progress",
      label: "进度",
      render: (row) => (
        <div className="export-progress" aria-label={`导出进度${Number(row.progress || 0)}%`}>
          <span style={{ width: `${Math.max(0, Math.min(100, Number(row.progress || 0)))}%` }} />
          <strong>{Number(row.progress || 0)}%</strong>
        </div>
      )
    },
    { key: "processedRows", label: "行数", num: true, render: (row) => `${Number(row.processedRows || 0)}/${Number(row.totalRows || 0)}` },
    { key: "fileSize", label: "文件", render: (row) => fileSizeText(row.fileSize) },
    { key: "operatorName", label: "创建人", render: (row) => row.operatorName || `员工#${row.operatorId || "-"}` },
    { key: "createTime", label: "创建时间", render: (row) => dateText(row.createTime) },
    { key: "expiresAt", label: "过期时间", render: (row) => dateText(row.expiresAt) },
    {
      key: "actions",
      label: "操作",
      render: (row) => (
        <div className="table-actions">
          {row.status === "SUCCEEDED" ? <button className="button-ghost table-action table-action-primary" type="button" onClick={() => downloadJob(row)}><Download size={14} />下载</button> : null}
          {(row.status === "PENDING" || row.status === "RUNNING") ? <button className="button-ghost table-action table-action-danger" type="button" onClick={() => cancelJob(row)}><X size={14} />取消</button> : null}
          {row.status === "FAILED" ? <button className="button-ghost table-action table-action-state" type="button" onClick={() => retryJob(row)}><RefreshCcw size={14} />重试</button> : null}
          {!['PENDING', 'RUNNING', 'SUCCEEDED', 'FAILED'].includes(row.status) ? <span className="muted">无需操作</span> : null}
        </div>
      )
    }
  ];

  return (
    <section className="panel export-jobs-panel">
      <PageHeader title="导出任务" eyebrow="文件生成队列">
        <button className="button-ghost" type="button" onClick={() => loadJobs(true)} disabled={refreshing}>
          <RefreshCcw size={16} />{refreshing ? "刷新中" : "刷新"}
        </button>
      </PageHeader>
      {stats ? (
        <div className="export-stats" aria-label="导出任务统计">
          <span><strong>{Number(stats.pending || 0)}</strong>排队</span>
          <span><strong>{Number(stats.running || 0)}</strong>运行</span>
          <span><strong>{Number(stats.succeeded || 0)}</strong>成功</span>
          <span><strong>{Number(stats.failed || 0)}</strong>失败</span>
          <span><strong>{Number(stats.successRate || 0)}%</strong>成功率</span>
          <span className="export-recent-failure">
            <strong>{stats.recentFailureJobId ? String(stats.recentFailureJobId).slice(0, 8) : "-"}</strong>
            最近失败{stats.recentFailureErrorCode ? ` · ${stats.recentFailureErrorCode}` : ""}
          </span>
        </div>
      ) : null}
      <form className="toolbar toolbar-filters export-job-filters" onSubmit={submitFilters}>
        <select aria-label="导出数据类型" value={draftFilter.exportType} onChange={(event) => setDraftFilter({ ...draftFilter, exportType: event.target.value })}>
          <option value="">全部数据</option>
          <option value="ORDER">预约订单</option>
          <option value="REVIEW">用户评价</option>
          {session.role === "ADMIN" ? <option value="USER">用户</option> : null}
          {session.role === "ADMIN" ? <option value="OPERATION_LOG">操作日志</option> : null}
        </select>
        <select aria-label="导出任务状态" value={draftFilter.status} onChange={(event) => setDraftFilter({ ...draftFilter, status: event.target.value })}>
          <option value="">全部状态</option>
          <option value="PENDING">排队中</option>
          <option value="RUNNING">生成中</option>
          <option value="SUCCEEDED">可下载</option>
          <option value="FAILED">失败</option>
          <option value="CANCELED">已取消</option>
          <option value="EXPIRED">已过期</option>
        </select>
        <span className="table-summary">共 {total} 个任务</span>
        <button className="button-ghost" type="submit"><Search size={16} />筛选</button>
        <button className="button-ghost" type="button" onClick={resetFilters}>清空</button>
      </form>
      {error ? <div className="error-state">{error}</div> : null}
      {loading ? <div className="loading-state">正在加载导出任务</div> : null}
      {!loading && !error ? <DataTable rows={rows} columns={columns} emptyText="暂无导出任务" /> : null}
      {!loading && !error ? <PaginationBar total={total} pager={pager} onPageChange={setPager} /> : null}
    </section>
  );
}

function LogDetailDrawer({ log, onClose }) {
  const details = [
    { label: "操作", value: log.description },
    { label: "操作人", value: log.operatorName || `员工#${log.operatorId || "-"}` },
    { label: "请求方法", value: log.requestMethod },
    { label: "请求路径", value: log.requestUri },
    { label: "客户端IP指纹", value: log.clientIp },
    { label: "耗时", value: `${log.costTime ?? 0}ms` },
    { label: "记录时间", value: dateText(log.createTime) }
  ];

  return (
    <Drawer title="日志详情" onClose={onClose}>
      <div className="detail-meta-list">
        {details.map((item) => (
          <p key={item.label}>
            <strong>{item.label}</strong>
            <span>{item.value || "-"}</span>
          </p>
        ))}
      </div>
    </Drawer>
  );
}

function LogsPage({ rows, total, pager, onPageChange, filter, onFilterChange, session, onToast }) {
  const [logDetail, setLogDetail] = useState(null);
  const [draftFilter, setDraftFilter] = useState(filter);
  useEffect(() => setDraftFilter(filter), [filter]);
  const hasLogFilter = Boolean(String(filter.keyword || "").trim() || filter.requestMethod);
  const logTotalCount = total ?? rows.length;

  function submitLogFilters(event) {
    event.preventDefault();
    onFilterChange({ keyword: draftFilter.keyword.trim(), requestMethod: draftFilter.requestMethod });
  }

  function resetLogFilters() {
    setDraftFilter({ keyword: "", requestMethod: "" });
    onFilterChange({ keyword: "", requestMethod: "" });
  }

  return (
    <>
      <section className="panel">
        <PageHeader title="操作日志" eyebrow="审计记录">
          <ExportCreateAction
            session={session}
            exportType="OPERATION_LOG"
            filters={{ keyword: filter.keyword, requestMethod: filter.requestMethod }}
            onToast={onToast}
          />
        </PageHeader>
        <form className="toolbar toolbar-filters" style={{ marginBottom: 14 }} onSubmit={submitLogFilters}>
          <input
            aria-label="搜索操作日志"
            value={draftFilter.keyword}
            placeholder="操作、人员、路径、IP"
            onChange={(event) => setDraftFilter({ ...draftFilter, keyword: event.target.value })}
          />
          <select aria-label="请求方法" value={draftFilter.requestMethod} onChange={(event) => setDraftFilter({ ...draftFilter, requestMethod: event.target.value })}>
            <option value="">全部方法</option>
            <option value="GET">GET</option>
            <option value="POST">POST</option>
            <option value="PUT">PUT</option>
            <option value="DELETE">DELETE</option>
          </select>
          <span className="table-summary">
            {hasLogFilter ? <>筛选结果，共 {logTotalCount} 条</> : <>共 {logTotalCount} 条</>}
          </span>
          <button className="button-ghost" type="submit"><Search size={16} />搜索</button>
          <button className="button-ghost" type="button" onClick={resetLogFilters} disabled={!hasLogFilter && !draftFilter.keyword.trim() && !draftFilter.requestMethod}>
            <RefreshCcw size={16} />
            清空
          </button>
        </form>
        <DataTable
          rows={rows}
          columns={[
            { key: "description", label: "操作" },
            { key: "operatorName", label: "操作人" },
            { key: "requestMethod", label: "方法" },
            { key: "requestUri", label: "路径" },
            { key: "clientIp", label: "IP指纹" },
            { key: "createTime", label: "时间", render: (row) => dateText(row.createTime) },
            {
              key: "actions",
              label: "操作",
              render: (row) => (
                <button className="button-ghost table-action table-action-primary" type="button" onClick={() => setLogDetail(row)}>
                  <Eye size={14} />
                  详情
                </button>
              )
            }
          ]}
        />
        <PaginationBar total={logTotalCount} pager={pager} onPageChange={onPageChange} />
      </section>
      {logDetail ? <LogDetailDrawer log={logDetail} onClose={() => setLogDetail(null)} /> : null}
    </>
  );
}

function normalizePackageItems(packageItems, availableItems) {
  return packageItems
    .map((row) => {
      const item = availableItems.find((available) => String(available.id) === String(row.itemId));
      return {
        itemId: row.itemId || item?.id,
        name: row.name || item?.name || "未命名项目",
        price: row.price ?? item?.price ?? 0,
        copies: Math.max(1, Number(row.copies || 1))
      };
    })
    .filter((row) => row.itemId);
}

function validateAdminDrawerForm(type, form, packageItemRows) {
  if (!form.name.trim()) return "请输入名称";
  if (type === "category") {
    const sort = Number(form.sort);
    if (!Number.isInteger(sort) || sort < 0 || sort > 9999) return "排序必须是0-9999的整数";
  }
  if (type === "item" || type === "package") {
    if (!form.categoryId) return "请选择分类";
    const price = Number(form.price);
    if (!Number.isFinite(price) || price < 0.01 || price > 99999999.99) return "价格必须在0.01-99999999.99之间";
    const duration = Number(form.durationMinutes);
    if (!Number.isInteger(duration) || duration < 1 || duration > 10080) return "时长必须是1-10080分钟的整数";
    const capacity = Number(form.capacity);
    if (!Number.isInteger(capacity) || capacity < 1 || capacity > 100000) return "可预约容量必须是1-100000的整数";
  }
  if (type === "package" && packageItemRows.length === 0) return "请选择套餐包含项目";
  if (type === "employee") {
    if (!form.username.trim()) return "请输入员工账号";
    if (form.phone && !PHONE_REGEX.test(form.phone.trim())) return "请输入正确的11位手机号";
  }
  if (type === "user" && !PHONE_REGEX.test(form.phone.trim())) return "请输入正确的11位手机号";
  return "";
}

function AdminDrawer({ drawer, session, onClose, onToast, onReload }) {
  const isEdit = drawer.mode === "edit";
  const categoryOptions = drawer.categories || [];
  const availableItems = drawer.items || [];
  const defaultCategory = categoryOptions.find((category) => Number(category.type) === (drawer.type === "package" ? 2 : 1)) || categoryOptions[0];
  const itemTags = drawer.record?.tags || [];
  const [packageItemRows, setPackageItemRows] = useState(() => normalizePackageItems(drawer.record?.packageItems || [], drawer.items || []));
  const [error, setError] = useState("");
  const [form, setForm] = useState({
    name: drawer.record?.name || "",
    price: drawer.record?.price ? String(drawer.record.price) : "",
    phone: drawer.record?.phone || "",
    sex: drawer.record?.sex || "",
    idNumber: drawer.record?.idNumber || "",
    avatar: drawer.record?.avatar || "",
    username: drawer.record?.username || "",
    type: String(drawer.record?.type || (drawer.type === "package" ? "2" : "1")),
    sort: String(drawer.record?.sort ?? "0"),
    categoryId: drawer.record?.categoryId ? String(drawer.record.categoryId) : defaultCategory ? String(defaultCategory.id) : "",
    description: drawer.record?.description || "",
    image: drawer.record?.image || defaultContentImage,
    durationMinutes: drawer.record?.durationMinutes ? String(drawer.record.durationMinutes) : "",
    capacity: drawer.record?.capacity ? String(drawer.record.capacity) : "",
    district: drawer.record?.district || "",
    address: drawer.record?.address || "",
    meetingPoint: drawer.record?.meetingPoint || "",
    cancelPolicy: drawer.record?.cancelPolicy || "",
    status: String(drawer.record?.status ?? 1)
  });
  const endpoint = {
    category: "/admin/category",
    item: "/admin/explore-item",
    package: "/admin/explore-package",
    employee: "/admin/employee"
  }[drawer.type];

  function togglePackageItem(item) {
    setPackageItemRows((rows) => {
      const selected = rows.some((row) => String(row.itemId) === String(item.id));
      if (selected) {
        return rows.filter((row) => String(row.itemId) !== String(item.id));
      }
      return rows.concat({
        itemId: item.id,
        name: item.name,
        price: item.price ?? 0,
        copies: 1
      });
    });
  }

  function changePackageItemCopies(itemId, copies) {
    const nextCopies = Math.max(1, Number(copies || 1));
    setPackageItemRows((rows) => rows.map((row) => (
      String(row.itemId) === String(itemId) ? { ...row, copies: nextCopies } : row
    )));
  }

  async function submit(event) {
    event.preventDefault();
    setError("");
    const validationError = validateAdminDrawerForm(drawer.type, form, packageItemRows);
    if (validationError) {
      setError(validationError);
      return;
    }

    try {
      if (drawer.type === "user") {
        await request(session, `/admin/user-manage/${drawer.record.id}`, {
          method: "PUT",
          body: JSON.stringify({
            name: form.name,
            phone: form.phone,
            sex: form.sex,
            idNumber: form.idNumber,
            avatar: form.avatar
          })
        });
        onToast(`${drawer.title}已保存`);
        onClose();
        onReload();
        return;
      }
      await request(session, endpoint, {
        method: isEdit ? "PUT" : "POST",
        body: JSON.stringify({
          id: isEdit ? drawer.record.id : undefined,
          ...form,
          price: form.price ? Number(form.price) : undefined,
          categoryId: form.categoryId ? Number(form.categoryId) : undefined,
          sort: form.sort ? Number(form.sort) : 0,
          type: drawer.type === "category" ? Number(form.type) : undefined,
          description: form.description,
          image: form.image,
          durationMinutes: form.durationMinutes ? Number(form.durationMinutes) : undefined,
          capacity: form.capacity ? Number(form.capacity) : 0,
          district: form.district,
          address: form.address,
          meetingPoint: form.meetingPoint,
          cancelPolicy: form.cancelPolicy,
          status: Number(form.status),
          tags: drawer.type === "item" ? itemTags : undefined,
          packageItems: drawer.type === "package" ? packageItemRows : undefined
        })
      });
      onToast(`${drawer.title}已保存`);
      onClose();
      onReload();
    } catch (err) {
      setError(err.message);
    }
  }

  return (
    <Drawer title={drawer.title} onClose={onClose}>
      <form className="form-grid" onSubmit={submit}>
        {error ? <div className="form-error">{error}</div> : null}
        <label className="field">
          <span>名称</span>
          <input value={form.name} onChange={(event) => setForm({ ...form, name: event.target.value })} maxLength={32} required />
        </label>
        {drawer.type === "category" ? (
          <>
            <label className="field">
              <span>类型</span>
              <select value={form.type} onChange={(event) => setForm({ ...form, type: event.target.value })}>
                <option value="1">项目分类</option>
                <option value="2">套餐分类</option>
              </select>
            </label>
            <label className="field">
              <span>排序</span>
              <input type="number" min="0" max="9999" step="1" value={form.sort} onChange={(event) => setForm({ ...form, sort: event.target.value })} required />
            </label>
          </>
        ) : null}
        {drawer.type === "item" || drawer.type === "package" ? (
          <label className="field">
            <span>分类</span>
            <select value={form.categoryId} onChange={(event) => setForm({ ...form, categoryId: event.target.value })} required>
              <option value="">请选择分类</option>
              {categoryOptions
                .filter((category) => Number(category.type) === (drawer.type === "package" ? 2 : 1))
                .map((category) => <option key={category.id} value={category.id}>{category.name}</option>)}
            </select>
          </label>
        ) : null}
        {drawer.type === "item" || drawer.type === "package" ? (
          <>
            <label className="field">
              <span>价格</span>
              <input type="number" min="0.01" max="99999999.99" step="0.01" value={form.price} onChange={(event) => setForm({ ...form, price: event.target.value })} required />
            </label>
            <label className="field">
              <span>时长(分钟)</span>
              <input type="number" min="1" max="10080" step="1" value={form.durationMinutes} onChange={(event) => setForm({ ...form, durationMinutes: event.target.value })} required />
            </label>
            <label className="field">
              <span>可预约容量</span>
              <input type="number" min="1" max="100000" step="1" value={form.capacity} onChange={(event) => setForm({ ...form, capacity: event.target.value })} required />
            </label>
            <label className="field">
              <span>商圈</span>
              <input value={form.district} maxLength={64} onChange={(event) => setForm({ ...form, district: event.target.value })} />
            </label>
            <label className="field">
              <span>详细地址</span>
              <input value={form.address} maxLength={255} onChange={(event) => setForm({ ...form, address: event.target.value })} />
            </label>
            <label className="field">
              <span>集合点</span>
              <input value={form.meetingPoint} maxLength={255} onChange={(event) => setForm({ ...form, meetingPoint: event.target.value })} />
            </label>
            <label className="field">
              <span>取消规则</span>
              <input value={form.cancelPolicy} maxLength={255} onChange={(event) => setForm({ ...form, cancelPolicy: event.target.value })} />
            </label>
            <label className="field">
              <span>图片</span>
              <input value={form.image} placeholder={defaultContentImage} maxLength={255} onChange={(event) => setForm({ ...form, image: event.target.value })} />
            </label>
            <label className="field">
              <span>描述</span>
              <textarea value={form.description} maxLength={255} onChange={(event) => setForm({ ...form, description: event.target.value })} />
            </label>
            {drawer.type === "package" ? (
              <fieldset className="package-item-picker">
                <legend>套餐包含项目</legend>
                {availableItems.length ? availableItems.map((item) => {
                  const selected = packageItemRows.find((row) => String(row.itemId) === String(item.id));
                  return (
                    <div className="package-item-row" key={item.id}>
                      <label className="package-item-check">
                        <input
                          type="checkbox"
                          checked={packageItemRows.some((row) => String(row.itemId) === String(item.id))}
                          onChange={() => togglePackageItem(item)}
                        />
                        <span>
                          <strong>{item.name}</strong>
                          <small>{money(item.price)} · {durationText(item.durationMinutes)}</small>
                        </span>
                      </label>
                      <label className="package-item-copies">
                        <span>份数</span>
                        <input
                          type="number"
                          min="1"
                          max="99"
                          step="1"
                          value={selected?.copies || 1}
                          disabled={!selected}
                          required={Boolean(selected)}
                          onChange={(event) => changePackageItemCopies(item.id, event.target.value)}
                        />
                      </label>
                    </div>
                  );
                }) : <p className="muted">暂无可选项目，请先新增特色项目</p>}
              </fieldset>
            ) : null}
          </>
        ) : null}
        {drawer.type === "user" ? (
          <>
            <label className="field"><span>手机号</span><input value={form.phone} inputMode="numeric" maxLength={11} pattern={PHONE_PATTERN} required onChange={(event) => setForm({ ...form, phone: event.target.value })} /></label>
            <label className="field"><span>性别</span><select value={form.sex} onChange={(event) => setForm({ ...form, sex: event.target.value })}><option value="">未填写</option><option value="0">女</option><option value="1">男</option></select></label>
            <label className="field"><span>身份证号</span><input value={form.idNumber} maxLength={18} pattern="[0-9]{17}[0-9Xx]" onChange={(event) => setForm({ ...form, idNumber: event.target.value })} /></label>
            <label className="field"><span>头像</span><input value={form.avatar} maxLength={500} onChange={(event) => setForm({ ...form, avatar: event.target.value })} placeholder="https://example.com/avatar.png" /></label>
          </>
        ) : null}
        {drawer.type === "employee" ? (
          <>
            <label className="field"><span>账号</span><input value={form.username} maxLength={32} required onChange={(event) => setForm({ ...form, username: event.target.value })} /></label>
            <label className="field"><span>手机号</span><input value={form.phone} inputMode="numeric" maxLength={11} pattern={PHONE_PATTERN} onChange={(event) => setForm({ ...form, phone: event.target.value })} /></label>
            <label className="field"><span>性别</span><select value={form.sex} onChange={(event) => setForm({ ...form, sex: event.target.value })}><option value="">未填写</option><option value="0">女</option><option value="1">男</option></select></label>
            <label className="field"><span>身份证号</span><input value={form.idNumber} maxLength={18} pattern="[0-9]{17}[0-9Xx]" onChange={(event) => setForm({ ...form, idNumber: event.target.value })} /></label>
          </>
        ) : null}
        <div className="button-row">
          <button className="button" type="submit"><Save size={17} />保存</button>
          <button className="button-ghost" type="button" onClick={onClose}>取消</button>
        </div>
      </form>
    </Drawer>
  );
}

export function AdminApp() {
  return rootPage() === "login" ? <AdminLogin /> : <AdminShell />;
}
