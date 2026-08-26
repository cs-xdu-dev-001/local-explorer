import {
  Bell,
  CalendarCheck,
  CheckCheck,
  ChevronLeft,
  ChevronRight,
  Clock3,
  Compass,
  Eye,
  Heart,
  Home,
  Lock,
  LogOut,
  MapPinned,
  Phone,
  RefreshCcw,
  Search,
  ShoppingBag,
  Star,
  User,
  Users,
  X
} from "lucide-react";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { createSession, loginClient, logout, request, restoreSession } from "../../lib/auth.js";

const navItems = [
  { page: "index", label: "发现", href: "./index.html", icon: Home },
  { page: "history", label: "浏览记录", href: "./history.html", icon: Clock3 },
  { page: "favorites", label: "我的收藏", href: "./favorites.html", icon: Heart },
  { page: "my-orders", label: "我的预约", href: "./my-orders.html", icon: CalendarCheck }
];
const PHONE_PATTERN = "1[3-9][0-9]{9}";
const PHONE_REGEX = new RegExp(`^${PHONE_PATTERN}$`);

function rootPage() {
  return document.getElementById("root")?.dataset.page || "index";
}

function money(value) {
  return `¥${Number(value || 0).toFixed(2)}`;
}

function toRows(pageLike) {
  if (Array.isArray(pageLike)) return pageLike;
  return pageLike?.records || [];
}

function dateText(value) {
  if (!value) return "-";
  return String(value).replace("T", " ").slice(0, 16);
}

function normalizeDateTimeForApi(value) {
  return String(value || "").replace("T", " ").slice(0, 16);
}

function createRequestId() {
  if (window.crypto?.randomUUID) {
    return window.crypto.randomUUID();
  }
  return `req-${Date.now()}-${Math.random().toString(16).slice(2)}`;
}

function durationText(minutes) {
  const value = Number(minutes || 0);
  if (!value) return "";
  if (value < 60) return `${value}分钟`;
  const hours = Math.floor(value / 60);
  const rest = value % 60;
  return rest ? `${hours}小时${rest}分钟` : `${hours}小时`;
}

function remainingSlots(item) {
  if (!Number.isFinite(Number(item.capacity)) || !Number.isFinite(Number(item.booked))) return null;
  return Math.max(0, Number(item.capacity) - Number(item.booked));
}

function clientOrderStatus(value) {
  const status = Number(value);
  if (status === 1) return "已确认";
  if (status === 2) return "已完成";
  if (status === 3) return "已取消";
  if (status === 4) return "系统超时取消";
  return "待确认";
}

function canReviewOrder(order) {
  const orderType = Number(order.orderType || 1);
  const hasReviewTarget = orderType === 1 ? Boolean(order.itemId) : orderType === 2 && Boolean(order.packageId);
  return Number(order.status) === 2 && hasReviewTarget && !order.hasReview;
}

function canCancelOrder(order) {
  return Number(order.status) === 0 || Number(order.status) === 1;
}

function Toast({ message }) {
  return <p className={`toast ${message ? "show" : ""}`} role="status" aria-live="polite">{message}</p>;
}

function ClientConfirmDialog({ request: confirmRequest, onCancel, onConfirm }) {
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState("");
  const order = confirmRequest?.order;

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
      <aside className="confirm-dialog" role="alertdialog" aria-modal="true" aria-label="取消预约">
        <div className="section-head" style={{ marginBottom: 8 }}>
          <div>
            <p className="eyebrow">确认操作</p>
            <h2>取消预约</h2>
          </div>
          <button className="icon-button" type="button" aria-label="关闭" onClick={onCancel} disabled={submitting}><X size={18} /></button>
        </div>
        <p className="muted">确认取消「{order?.itemName || "该项目"}」的预约吗？</p>
        {error ? <div className="form-error">{error}</div> : null}
        <div className="button-row">
          <button className="button-danger" type="button" onClick={submit} disabled={submitting}>
            <X size={16} />
            {submitting ? "取消中" : "确认取消"}
          </button>
          <button className="button-ghost" type="button" onClick={onCancel} disabled={submitting}>暂不取消</button>
        </div>
      </aside>
    </div>
  );
}

function ClientLogin() {
  const [session, setSession] = useState(() => createSession("client"));
  const [form, setForm] = useState({ phone: "", password: "" });
  const [error, setError] = useState("");
  const [submitting, setSubmitting] = useState(false);

  async function submit(event) {
    event.preventDefault();
    if (!PHONE_REGEX.test(form.phone.trim())) {
      setError("请输入 11 位手机号");
      return;
    }
    if (!form.password.trim()) {
      setError("请输入密码");
      return;
    }
    setSubmitting(true);
    setError("");
    try {
      const next = await loginClient(session, form.phone.trim(), form.password);
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
          <p className="eyebrow">City Life Guide</p>
          <h1>发现城市好去处</h1>
          <p>精选路线、活动和套餐，安排一次轻松的本地探索。</p>
        </div>
      </section>
      <section className="login-side">
        <div className="login-card">
          <p className="eyebrow">用户登录</p>
          <h2>进入城市生活探店馆</h2>
          <form onSubmit={submit}>
            {error ? <div className="form-error">{error}</div> : null}
            <label className="field">
              <span>手机号</span>
              <input value={form.phone} placeholder="请输入手机号" inputMode="numeric" autoComplete="tel" maxLength={11} pattern={PHONE_PATTERN} required onChange={(event) => setForm({ ...form, phone: event.target.value })} />
            </label>
            <label className="field">
              <span>密码</span>
              <input type="password" value={form.password} placeholder="请输入密码" autoComplete="current-password" maxLength={64} required onChange={(event) => setForm({ ...form, password: event.target.value })} />
            </label>
            <button className="button" type="submit" disabled={submitting}>
              <Lock size={18} />
              {submitting ? "登录中" : "登录"}
            </button>
          </form>
        </div>
      </section>
    </main>
  );
}

function ClientShell() {
  const page = rootPage();
  const activeNavRef = useRef(null);
  const [session, setSession] = useState(() => createSession("client"));
  const [restoringSession, setRestoringSession] = useState(() => !session.token && !session.demo);
  const [snapshot, setSnapshot] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [toast, setToast] = useState("");
  const [orderTarget, setOrderTarget] = useState(null);
  const [reviewTarget, setReviewTarget] = useState(null);
  const [itemDetailTarget, setItemDetailTarget] = useState(null);
  const [cancelRequest, setCancelRequest] = useState(null);
  const [notificationOpen, setNotificationOpen] = useState(false);
  const [notificationUnread, setNotificationUnread] = useState(0);
  const [orderDetailTarget, setOrderDetailTarget] = useState(null);

  useEffect(() => {
    if (!restoringSession) return;
    restoreSession("client", session)
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
    if (restoringSession) return;
    setLoading(true);
    setError("");
    try {
      const [merchant, shopStatus, categories, items, packages] = await Promise.all([
        request(session, "/user/merchant/info"),
        request(session, "/user/shop/status"),
        request(session, "/user/category/list?type=1"),
        request(session, "/user/explore-item/list?page=1&pageSize=30"),
        request(session, "/user/explore-package/list?page=1&pageSize=30")
      ]);

      let favorites = [];
      let history = [];
      let orders = [];
      let unreadCount = 0;
      if (session.token) {
        [favorites, history, orders, unreadCount] = await Promise.all([
          request(session, "/user/favorite?page=1&pageSize=99").catch(() => []),
          request(session, "/user/favorite/browse?page=1&pageSize=99").catch(() => []),
          request(session, "/user/explore-order/page?page=1&pageSize=30").catch(() => ({ records: [] })),
          request(session, "/user/notification/unread-count").catch(() => 0)
        ]);
      }
      setNotificationUnread(Number(unreadCount || 0));

      setSnapshot({
        merchant,
        shopStatus,
        categories,
        items: toRows(items),
        packages: toRows(packages),
        favorites: toRows(favorites),
        history: toRows(history),
        orders: toRows(orders)
      });
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  }, [session, restoringSession]);

  useEffect(() => {
    load();
  }, [load]);

  useEffect(() => {
    activeNavRef.current?.scrollIntoView({ block: "nearest", inline: "center" });
  }, [page]);

  const shopOpen = Number(snapshot?.shopStatus) === 1;

  async function signOut() {
    await logout("client", session);
    setSession(createSession("client"));
    window.location.href = "./login.html";
  }

  function requestCancelOrder(order, onSuccess) {
    setCancelRequest({ order, onSuccess });
  }

  async function confirmCancelOrder() {
    if (!cancelRequest) return;
    const order = cancelRequest.order;
    await request(session, `/user/explore-order/${order.id}/cancel`, { method: "PUT" });
    showToast("预约已取消");
    setCancelRequest(null);
    cancelRequest.onSuccess?.();
    load();
  }

  function openItemDetail(item) {
    setItemDetailTarget(item);
  }

  function requestCreateOrder(item) {
    if (!shopOpen) {
      showToast("门店休息中，暂不可预约");
      return;
    }
    setOrderTarget(item);
  }

  function startOrderFromDetail(item) {
    if (!shopOpen) {
      showToast("门店休息中，暂不可预约");
      return;
    }
    setItemDetailTarget(null);
    setOrderTarget(item);
  }

  async function openOrderFromNotification(notification) {
    if (!notification.orderId) return;
    const order = await request(session, `/user/explore-order/${notification.orderId}`);
    setNotificationOpen(false);
    setOrderDetailTarget(order);
  }

  function cancelFromNotificationDetail(order) {
    requestCancelOrder(order, () => setOrderDetailTarget(null));
  }

  function reviewFromNotificationDetail(order) {
    setOrderDetailTarget(null);
    setReviewTarget(order);
  }

  return (
    <div className="client-shell">
      <header className="client-topbar">
        <nav className="client-nav" aria-label="用户导航">
          <a className="brand-lockup" href="./index.html" style={{ marginBottom: 0 }}>
            <span className="brand-mark">L</span>
            <span>
              <span className="brand-title" style={{ color: "var(--ink)" }}>Local Explorer</span>
              <span className="brand-subtitle" style={{ color: "var(--muted)", display: "block" }}>城市生活探店馆</span>
            </span>
          </a>
          <div className="client-nav-links">
            {navItems.map((item) => {
              const Icon = item.icon;
              const active = page === item.page;
              return (
                <a
                  key={item.page}
                  ref={active ? activeNavRef : null}
                  className={`client-nav-link ${active ? "active" : ""}`}
                  href={item.href}
                  aria-current={active ? "page" : undefined}
                >
                  <Icon size={16} />
                  {item.label}
                </a>
              );
            })}
          </div>
          <div className="client-actions">
            {session.token ? (
              <button
                className="icon-button notification-trigger"
                type="button"
                aria-label="通知中心"
                title="查看通知"
                aria-expanded={notificationOpen}
                onClick={() => setNotificationOpen(true)}
              >
                <Bell size={17} />
                {notificationUnread > 0 ? (
                  <span className="notification-badge" aria-label={`${notificationUnread}条未读通知`}>
                    {notificationUnread > 99 ? "99+" : notificationUnread}
                  </span>
                ) : null}
              </button>
            ) : null}
            {session.token ? <span className="muted">{session.userName || "本地用户"}</span> : <a className="button-ghost" href="./login.html"><User size={16} />登录</a>}
            {session.token ? <button className="icon-button" type="button" aria-label="退出登录" onClick={signOut}><LogOut size={17} /></button> : null}
          </div>
        </nav>
      </header>

      <main className="client-main" id="main-content">
        {loading ? <div className="loading-state">加载本地生活内容中</div> : null}
        {error ? <div className="error-state">{error}</div> : null}
        {!loading && !error && snapshot ? (
          <ClientPage
            page={page}
            session={session}
            snapshot={snapshot}
            onReload={load}
            onToast={showToast}
            onOrder={requestCreateOrder}
            onDetail={openItemDetail}
            onReview={setReviewTarget}
            onCancel={requestCancelOrder}
            canBook={shopOpen}
          />
        ) : null}
      </main>
      {itemDetailTarget ? <ItemDetailDrawer item={itemDetailTarget} session={session} onClose={() => setItemDetailTarget(null)} onOrder={startOrderFromDetail} canBook={shopOpen} /> : null}
      {orderTarget ? <OrderDrawer item={orderTarget} session={session} onClose={() => setOrderTarget(null)} onToast={showToast} onReload={load} canBook={shopOpen} /> : null}
      {reviewTarget ? <ReviewDrawer order={reviewTarget} session={session} onClose={() => setReviewTarget(null)} onToast={showToast} onReload={load} /> : null}
      {notificationOpen ? (
        <NotificationDrawer
          session={session}
          onClose={() => setNotificationOpen(false)}
          onUnreadChange={setNotificationUnread}
          onOpenOrder={openOrderFromNotification}
        />
      ) : null}
      {orderDetailTarget ? (
        <ClientOrderDetailDrawer
          order={orderDetailTarget}
          onClose={() => setOrderDetailTarget(null)}
          onCancel={cancelFromNotificationDetail}
          onReview={reviewFromNotificationDetail}
        />
      ) : null}
      {cancelRequest ? <ClientConfirmDialog request={cancelRequest} onCancel={() => setCancelRequest(null)} onConfirm={confirmCancelOrder} /> : null}
      <Toast message={toast} />
    </div>
  );
}

function NotificationDrawer({ session, onClose, onUnreadChange, onOpenOrder }) {
  const PAGE_SIZE = 20;
  const [notifications, setNotifications] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [page, setPage] = useState(1);
  const [total, setTotal] = useState(0);
  const [unreadCount, setUnreadCount] = useState(0);

  const loadNotifications = useCallback(async () => {
    setLoading(true);
    setError("");
    try {
      const [result, unread] = await Promise.all([
        request(session, `/user/notification/page?page=${page}&pageSize=${PAGE_SIZE}`),
        request(session, "/user/notification/unread-count")
      ]);
      const rows = toRows(result);
      setNotifications(rows);
      setTotal(Number(result?.total ?? rows.length));
      setUnreadCount(Number(unread || 0));
      onUnreadChange(Number(unread || 0));
    } catch (err) {
      setError(err.message || "请稍后重试");
    } finally {
      setLoading(false);
    }
  }, [onUnreadChange, page, session]);

  useEffect(() => {
    loadNotifications();
  }, [loadNotifications]);

  async function markAllRead() {
    try {
      await request(session, "/user/notification/read-all", { method: "PUT" });
      setNotifications((rows) => rows.map((item) => ({ ...item, readStatus: 1 })));
      setUnreadCount(0);
      onUnreadChange(0);
    } catch (err) {
      setError(err.message || "全部已读操作失败");
    }
  }

  async function openNotification(notification) {
    setError("");
    try {
      if (Number(notification.readStatus) === 0) {
        await request(session, `/user/notification/${notification.id}/read`, { method: "PUT" });
        setNotifications((rows) => rows.map((item) => (
          item.id === notification.id ? { ...item, readStatus: 1 } : item
        )));
        const nextUnread = Math.max(0, unreadCount - 1);
        setUnreadCount(nextUnread);
        onUnreadChange(nextUnread);
      }
      if (notification.orderId) {
        await onOpenOrder(notification);
      }
    } catch (err) {
      setError(err.message || "通知操作失败");
    }
  }

  const hasUnread = unreadCount > 0;
  const pageCount = Math.max(1, Math.ceil(total / PAGE_SIZE));

  return (
    <div className="drawer-backdrop" role="presentation">
      <aside className="drawer notification-drawer" role="dialog" aria-modal="true" aria-label="通知中心">
        <div className="section-head">
          <div>
            <p className="eyebrow">消息</p>
            <h2>通知中心</h2>
          </div>
          <div className="button-row notification-head-actions">
            {hasUnread ? (
              <button className="icon-button" type="button" aria-label="全部已读" title="全部标记已读" onClick={markAllRead}>
                <CheckCheck size={17} />
              </button>
            ) : null}
            <button className="icon-button" type="button" aria-label="关闭通知" onClick={onClose}><X size={18} /></button>
          </div>
        </div>
        {loading ? <div className="loading-state">加载通知中</div> : null}
        {error ? (
          <div className="error-state notification-error">
            <span>通知加载失败：{error}</span>
            <button className="button-ghost" type="button" onClick={loadNotifications}><RefreshCcw size={16} />重试</button>
          </div>
        ) : null}
        {!loading && !error ? (
          <div className="notification-list">
            {notifications.length ? notifications.map((notification) => (
              <button
                className={`notification-item ${Number(notification.readStatus) === 0 ? "unread" : ""}`}
                type="button"
                key={notification.id || notification.eventId}
                onClick={() => openNotification(notification)}
              >
                <span className="notification-item-head">
                  <strong>{notification.title}</strong>
                  {Number(notification.readStatus) === 0 ? <span className="notification-dot" aria-label="未读" /> : null}
                </span>
                <span>{notification.content}</span>
                <time className="notification-time">{dateText(notification.createTime)}</time>
              </button>
            )) : <div className="empty-state">暂无通知</div>}
            {total > PAGE_SIZE ? (
              <div className="notification-pagination" aria-label="通知分页">
                <button
                  className="icon-button"
                  type="button"
                  aria-label="上一页通知"
                  title="上一页"
                  disabled={page <= 1}
                  onClick={() => setPage((current) => Math.max(1, current - 1))}
                >
                  <ChevronLeft size={17} />
                </button>
                <span>{page} / {pageCount}</span>
                <button
                  className="icon-button"
                  type="button"
                  aria-label="下一页通知"
                  title="下一页"
                  disabled={page >= pageCount}
                  onClick={() => setPage((current) => Math.min(pageCount, current + 1))}
                >
                  <ChevronRight size={17} />
                </button>
              </div>
            ) : null}
          </div>
        ) : null}
      </aside>
    </div>
  );
}

function ClientPage({ page, session, snapshot, onReload, onToast, onOrder, onDetail, onReview, onCancel, canBook }) {
  if (page === "history") return <ProtectedPage session={session}><HistoryPage rows={snapshot.history} onOrder={onOrder} onDetail={onDetail} canBook={canBook} /></ProtectedPage>;
  if (page === "favorites") return <ProtectedPage session={session}><FavoritesPage rows={snapshot.favorites} session={session} onOrder={onOrder} onDetail={onDetail} onToast={onToast} onReload={onReload} canBook={canBook} /></ProtectedPage>;
  if (page === "my-orders") return <ProtectedPage session={session}><OrdersPage rows={snapshot.orders} onReview={onReview} onCancel={onCancel} /></ProtectedPage>;
  return <HomePage session={session} snapshot={snapshot} onReload={onReload} onToast={onToast} onOrder={onOrder} onDetail={onDetail} canBook={canBook} />;
}

function ProtectedPage({ session, children }) {
  if (session.token) return children;
  return (
    <section className="client-section">
      <PageTitle icon={Lock} eyebrow="账户" title="请先登录" />
      <a className="button" href="./login.html">去登录</a>
    </section>
  );
}

function PageTitle({ icon: Icon, eyebrow, title, children }) {
  return (
    <div className="section-head">
      <div>
        <p className="eyebrow">{eyebrow}</p>
        <h2>{Icon ? <Icon size={21} style={{ marginRight: 8, verticalAlign: "-3px" }} /> : null}{title}</h2>
      </div>
      {children}
    </div>
  );
}

function HomePage({ session, snapshot, onReload, onToast, onOrder, onDetail, canBook }) {
  const [categoryId, setCategoryId] = useState("");
  const [keyword, setKeyword] = useState("");
  const [draftKeyword, setDraftKeyword] = useState("");
  const favoriteIds = useMemo(() => new Set(snapshot.favorites.map((item) => String(item.id))), [snapshot.favorites]);
  const availableSlots = useMemo(() => {
    return snapshot.items.reduce((sum, item) => sum + (remainingSlots(item) || 0), 0);
  }, [snapshot.items]);
  const filtered = useMemo(() => {
    return snapshot.items.filter((item) => {
      const categoryMatch = !categoryId || String(item.categoryId) === String(categoryId);
      const nameMatch = !keyword.trim() || item.name.includes(keyword.trim());
      return categoryMatch && nameMatch;
    });
  }, [snapshot.items, categoryId, keyword]);

  async function toggleFavorite(item) {
    if (!session.token) {
      window.location.href = "./login.html";
      return;
    }
    const active = favoriteIds.has(String(item.id));
    try {
      await request(session, `/user/favorite/${item.id}`, { method: active ? "DELETE" : "POST" });
      onToast(active ? "已取消收藏" : "已收藏");
      onReload();
    } catch (err) {
      onToast(err.message);
    }
  }

  async function recordBrowse(item) {
    if (!session.token) return;
    try {
      await request(session, `/user/favorite/browse/${item.id}`, { method: "POST" });
      onReload();
    } catch (err) {
      void err;
      // 浏览记录失败不应阻断用户查看详情。
    }
  }

  function applyFilters(event) {
    event.preventDefault();
    setKeyword(draftKeyword.trim());
  }

  function resetFilters() {
    setKeyword("");
    setDraftKeyword("");
    setCategoryId("");
  }

  return (
    <>
      <section className="client-hero">
        <div className="hero-copy">
          <p className="eyebrow">城市生活探店馆</p>
          <h1>{snapshot.merchant.name}</h1>
          <p>{snapshot.merchant.slogan}</p>
          <div className="tag-row">
            <span className="tag">今日{Number(snapshot.shopStatus) === 1 ? "营业中" : "休息中"}</span>
            <span className="tag">可约 {availableSlots} 位</span>
            <span className="tag">覆盖 {snapshot.categories.length} 类</span>
          </div>
        </div>
        <div className="hero-image">
          <img src="../assets/images/citywalk.webp" alt="城市漫游路线" />
        </div>
      </section>

      <section className="client-section client-discovery">
        <PageTitle icon={Compass} eyebrow="发现" title="特色项目">
          <button className="button-ghost discovery-refresh" type="button" aria-label="刷新特色项目" title="刷新特色项目" onClick={onReload}>
            <RefreshCcw size={16} /><span>刷新</span>
          </button>
        </PageTitle>
        <form className="filter-shelf client-filter-form" onSubmit={applyFilters}>
          <select value={categoryId} onChange={(event) => setCategoryId(event.target.value)} aria-label="分类筛选">
            <option value="">全部分类</option>
            {snapshot.categories.map((category) => <option key={category.id} value={category.id}>{category.name}</option>)}
          </select>
          <input value={draftKeyword} placeholder="搜索项目名称" onChange={(event) => setDraftKeyword(event.target.value)} />
          <button className="button-ghost" type="submit"><Search size={16} />搜索</button>
          <button className="button-ghost" type="button" onClick={resetFilters}>重置</button>
        </form>
        <SpotGrid rows={filtered} favoriteIds={favoriteIds} onFavorite={toggleFavorite} onOrder={onOrder} onDetail={onDetail} onBrowse={recordBrowse} canBook={canBook} />
      </section>

      <section className="client-section">
        <PageTitle icon={ShoppingBag} eyebrow="组合" title="探索套餐" />
        <SpotGrid rows={snapshot.packages} favoriteIds={new Set()} onFavorite={null} onOrder={(item) => onOrder({ ...item, orderType: 2 })} onDetail={(item) => onDetail({ ...item, orderType: 2 })} canBook={canBook} />
      </section>
    </>
  );
}

function SpotGrid({ rows, favoriteIds, onFavorite, onOrder, onDetail, onBrowse, canBook = true }) {
  if (!rows.length) return <div className="empty-state">暂无相关内容</div>;
  return (
    <div className="spot-grid">
      {rows.map((item) => {
        const favorite = favoriteIds.has(String(item.id));
        const remaining = remainingSlots(item);
        const duration = durationText(item.durationMinutes);
        return (
          <article className="spot-card" key={item.id} onClick={() => { onBrowse?.(item); onDetail(item); }}>
            <img src={item.image || "../assets/images/package.webp"} alt={item.name} loading="lazy" />
            <div className="spot-body">
              <div className="section-head" style={{ marginBottom: 0 }}>
                <h3>{item.name}</h3>
                <span className="price">{money(item.price)}</span>
              </div>
              <p className="spot-desc">{item.description}</p>
              <div className="spot-meta">
                {item.district || item.address ? <span><MapPinned size={15} />{item.district || item.address}</span> : null}
                {duration ? <span><Clock3 size={15} />{duration}</span> : null}
                {remaining !== null ? <span><Users size={15} />剩余 {remaining} 位</span> : null}
              </div>
              <div className="tag-row">
                {(item.tags || []).map((tag) => <span className="tag" key={`${tag.name}-${tag.value}`}>{tag.name}：{tag.value}</span>)}
                {item.categoryName ? <span className="tag">{item.categoryName}</span> : null}
              </div>
              {item.cancelPolicy ? <p className="spot-policy">{item.cancelPolicy}</p> : null}
              <div className="card-actions">
                <button className="button" type="button" onClick={(event) => { event.stopPropagation(); onBrowse?.(item); onDetail(item); }}><Eye size={16} />查看详情</button>
                <button className="button-ghost" type="button" disabled={!canBook} onClick={(event) => { event.stopPropagation(); if (canBook) onOrder(item); }}><CalendarCheck size={16} />{canBook ? "预约" : "休息中"}</button>
                {onFavorite ? (
                  <button className="icon-button" type="button" aria-label={favorite ? "取消收藏" : "收藏"} onClick={(event) => { event.stopPropagation(); onFavorite(item); }}>
                    <Heart size={17} fill={favorite ? "currentColor" : "none"} />
                  </button>
                ) : null}
              </div>
            </div>
          </article>
        );
      })}
    </div>
  );
}

function HistoryPage({ rows, onOrder, onDetail, canBook }) {
  return (
    <section className="client-section">
      <PageTitle icon={Clock3} eyebrow="记录" title="最近浏览" />
      <SpotGrid rows={rows} favoriteIds={new Set()} onFavorite={null} onOrder={onOrder} onDetail={onDetail} canBook={canBook} />
    </section>
  );
}

function FavoritesPage({ rows, session, onOrder, onDetail, onToast, onReload, canBook }) {
  async function removeFavorite(item) {
    try {
      await request(session, `/user/favorite/${item.id}`, { method: "DELETE" });
      onToast("已取消收藏");
      onReload();
    } catch (err) {
      onToast(err.message);
    }
  }

  return (
    <section className="client-section">
      <PageTitle icon={Heart} eyebrow="收藏" title="我的收藏" />
      <SpotGrid rows={rows} favoriteIds={new Set(rows.map((item) => String(item.id)))} onFavorite={removeFavorite} onOrder={onOrder} onDetail={onDetail} canBook={canBook} />
    </section>
  );
}

function ItemDetailDrawer({ item, session, onClose, onOrder, canBook }) {
  const remaining = remainingSlots(item);
  const duration = durationText(item.durationMinutes);
  const tags = item.tags || [];
  const [packageItems, setPackageItems] = useState([]);

  useEffect(() => {
    let active = true;
    if (item.orderType !== 2) {
      setPackageItems([]);
      return () => {
        active = false;
      };
    }
    setPackageItems(item.packageItems || []);
    request(session, `/user/explore-package/items/${item.id}`)
      .then((rows) => {
        if (active) setPackageItems(Array.isArray(rows) ? rows : []);
      })
      .catch(() => {
        if (active) setPackageItems(item.packageItems || []);
      });
    return () => {
      active = false;
    };
  }, [item, session]);

  return (
    <div className="drawer-backdrop" role="presentation">
      <aside className="drawer" role="dialog" aria-modal="true" aria-label="项目详情">
        <div className="section-head">
          <div>
            <p className="eyebrow">{item.orderType === 2 ? "套餐详情" : "项目详情"}</p>
            <h2>{item.name}</h2>
          </div>
          <button className="icon-button" type="button" aria-label="关闭" onClick={onClose}><X size={18} /></button>
        </div>
        <div className="detail-stack">
          <div className="detail-hero">
            <img className="detail-cover" src={item.image || "../assets/images/package.webp"} alt={item.name} />
            <div>
              <p className="eyebrow">{item.categoryName || item.district || "Local Explorer"}</p>
              <strong className="price">{money(item.price)}</strong>
            </div>
          </div>
          {item.description ? <p className="detail-copy">{item.description}</p> : null}
          <div className="detail-stat-grid">
            <span className="detail-stat">
              <strong>{duration || "-"}</strong>
              <small>时长</small>
            </span>
            <span className="detail-stat">
              <strong>{remaining === null ? "-" : remaining}</strong>
              <small>剩余名额</small>
            </span>
            <span className="detail-stat">
              <strong>{item.categoryName || "-"}</strong>
              <small>分类</small>
            </span>
          </div>
          <div className="detail-meta-list">
            {item.district ? <p><MapPinned size={15} />商圈：{item.district}</p> : null}
            {item.address ? <p><Home size={15} />地址：{item.address}</p> : null}
            {item.meetingPoint ? <p><Users size={15} />集合点：{item.meetingPoint}</p> : null}
            {item.cancelPolicy ? <p><Clock3 size={15} />取消规则：{item.cancelPolicy}</p> : null}
          </div>
          {tags.length ? (
            <div className="tag-row">
              {tags.map((tag) => <span className="tag" key={`${tag.name}-${tag.value}`}>{tag.name}：{tag.value}</span>)}
            </div>
          ) : null}
          {item.orderType === 2 ? (
            <div className="package-item-list">
              <p className="eyebrow">套餐包含项目</p>
              {packageItems.length ? packageItems.map((row) => {
                const itemName = row.itemName || row.name || "未命名项目";
                return (
                  <div className="package-item-summary" key={`${row.itemId || itemName}-${itemName}`}>
                    <strong>{itemName}</strong>
                    <span>×{row.copies || 1}</span>
                  </div>
                );
              }) : <p className="muted">暂无项目明细</p>}
            </div>
          ) : null}
          <div className="button-row">
            <button className="button" type="button" disabled={!canBook} onClick={() => { if (canBook) onOrder(item); }}><CalendarCheck size={17} />{canBook ? "预约" : "休息中"}</button>
            <button className="button-ghost" type="button" onClick={onClose}>关闭</button>
          </div>
        </div>
      </aside>
    </div>
  );
}

function OrdersPage({ rows, onReview, onCancel }) {
  const [detailTarget, setDetailTarget] = useState(null);

  async function cancelFromDetail(order) {
    onCancel(order, () => setDetailTarget(null));
  }

  function reviewFromDetail(order) {
    setDetailTarget(null);
    onReview(order);
  }

  return (
    <>
      <section className="client-section">
        <PageTitle icon={CalendarCheck} eyebrow="预约" title="我的预约" />
        <div className="order-list">
          {rows.length ? rows.map((order) => (
            <article className="order-item" key={order.id || order.orderNo}>
              <div className="section-head" style={{ marginBottom: 0 }}>
                <h3>{order.itemName}</h3>
                <span className="price">{money(order.amount)}</span>
              </div>
              <p className="muted">{order.orderNo} · {dateText(order.reserveTime)}</p>
              <p className="muted">{order.channel || "用户端预约"} · {order.paymentStatus || "待支付"}</p>
              <span className="tag">{clientOrderStatus(order.status)}</span>
              <div className="button-row">
                <button className="button-ghost" type="button" onClick={() => setDetailTarget(order)}>
                  <Eye size={16} />详情
                </button>
                {canReviewOrder(order) ? (
                  <button className="button-ghost" type="button" onClick={() => onReview(order)}>
                    <Star size={16} />提交评价
                  </button>
                ) : null}
                {canCancelOrder(order) ? (
                  <button className="button-danger" type="button" onClick={() => onCancel(order)}>
                    <X size={16} />取消预约
                  </button>
                ) : null}
              </div>
            </article>
          )) : <div className="empty-state">暂无预约记录</div>}
        </div>
      </section>
      {detailTarget ? <ClientOrderDetailDrawer order={detailTarget} onClose={() => setDetailTarget(null)} onCancel={cancelFromDetail} onReview={reviewFromDetail} /> : null}
    </>
  );
}

function ClientOrderDetailDrawer({ order, onClose, onCancel, onReview }) {
  const statusText = clientOrderStatus(order.status);

  return (
    <div className="drawer-backdrop" role="presentation">
      <aside className="drawer" role="dialog" aria-modal="true" aria-label="预约详情">
        <div className="section-head">
          <div>
            <p className="eyebrow">预约详情</p>
            <h2>{order.itemName}</h2>
          </div>
          <button className="icon-button" type="button" aria-label="关闭" onClick={onClose}><X size={18} /></button>
        </div>
        <div className="detail-stack">
          <div>
            <p className="muted">{order.orderNo || "-"} · {dateText(order.reserveTime)}</p>
            <p className="muted">{order.channel || "用户端预约"} · {order.paymentStatus || "待支付"}</p>
          </div>
          <div className="detail-stat-grid">
            <span className="detail-stat">
              <strong>{statusText}</strong>
              <small>状态</small>
            </span>
            <span className="detail-stat">
              <strong>{Number(order.peopleCount || 1)}</strong>
              <small>人数</small>
            </span>
            <span className="detail-stat">
              <strong>{money(order.amount)}</strong>
              <small>金额</small>
            </span>
          </div>
          <div>
            <p className="eyebrow">联系信息</p>
            <p className="muted">联系人：{order.contactName || "-"}</p>
            <p className="muted">手机号：{order.contactPhone || "-"}</p>
          </div>
          <div>
            <p className="eyebrow">预约备注</p>
            <p className="muted">备注：{order.remark || "-"}</p>
            <p className="muted">创建时间：{dateText(order.createTime)}</p>
          </div>
          <div className="button-row">
            {canReviewOrder(order) ? (
              <button className="button" type="button" onClick={() => onReview(order)}>
                <Star size={16} />提交评价
              </button>
            ) : null}
            {canCancelOrder(order) ? (
              <button className="button-danger" type="button" onClick={() => onCancel(order)}>
                <X size={16} />取消预约
              </button>
            ) : null}
            <button className="button-ghost" type="button" onClick={onClose}>关闭</button>
          </div>
        </div>
      </aside>
    </div>
  );
}

function ReviewDrawer({ order, session, onClose, onToast, onReload }) {
  const [form, setForm] = useState({ rating: 5, content: "" });
  const [error, setError] = useState("");

  async function submit(event) {
    event.preventDefault();
    const rating = Number(form.rating);
    if (!Number.isInteger(rating) || rating < 1 || rating > 5) {
      setError("请选择 1-5 分评分");
      return;
    }
    if (!form.content.trim()) {
      setError("请填写评价内容");
      return;
    }
    if (form.content.length > 500) {
      setError("评价内容不能超过500个字符");
      return;
    }
    setError("");
    try {
      await request(session, "/user/review", {
        method: "POST",
        body: JSON.stringify({
          orderId: order.id,
          itemId: order.itemId,
          rating: Number(form.rating),
          content: form.content
        })
      });
      onToast("评价已提交");
      onClose();
      onReload();
    } catch (err) {
      setError(err.message);
    }
  }

  return (
    <div className="drawer-backdrop" role="presentation">
      <aside className="drawer" role="dialog" aria-modal="true" aria-label="提交评价">
        <div className="section-head">
          <div>
            <p className="eyebrow">评价</p>
            <h2>{order.itemName}</h2>
          </div>
          <button className="icon-button" type="button" aria-label="关闭" onClick={onClose}><X size={18} /></button>
        </div>
        <p className="muted">{order.orderNo} · {dateText(order.reserveTime)}</p>
        <form className="form-grid" onSubmit={submit}>
          {error ? <div className="form-error">{error}</div> : null}
          <label className="field">
            <span>评分</span>
            <select value={form.rating} required onChange={(event) => setForm({ ...form, rating: event.target.value })}>
              <option value="5">5 分</option>
              <option value="4">4 分</option>
              <option value="3">3 分</option>
              <option value="2">2 分</option>
              <option value="1">1 分</option>
            </select>
          </label>
          <label className="field">
            <span>评价内容</span>
            <textarea value={form.content} maxLength={500} required onChange={(event) => setForm({ ...form, content: event.target.value })} />
          </label>
          <div className="button-row">
            <button className="button" type="submit"><Star size={17} />提交评价</button>
            <button className="button-ghost" type="button" onClick={onClose}>取消</button>
          </div>
        </form>
      </aside>
    </div>
  );
}

function OrderDrawer({ item, session, onClose, onToast, onReload, canBook }) {
  const [form, setForm] = useState({ peopleCount: 1, contactName: "", contactPhone: "", reserveTime: "", remark: "" });
  const [error, setError] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const requestIdRef = useRef(createRequestId());

  async function submit(event) {
    event.preventDefault();
    if (submitting) {
      return;
    }
    if (!session.token) {
      window.location.href = "./login.html";
      return;
    }
    if (!canBook) {
      setError("门店休息中，暂不可预约");
      return;
    }
    const peopleCount = Number(form.peopleCount);
    if (!Number.isInteger(peopleCount) || peopleCount < 1 || peopleCount > 100000) {
      setError("预约人数必须是1-100000的整数");
      return;
    }
    if (!form.contactName.trim() || form.contactName.length > 32 || !PHONE_REGEX.test(form.contactPhone.trim()) || !form.reserveTime) {
      setError("请补全联系人、手机号和预约时间");
      return;
    }
    if (form.remark.length > 255) {
      setError("备注不能超过255个字符");
      return;
    }
    setError("");
    setSubmitting(true);
    try {
      await request(session, "/user/explore-order", {
        method: "POST",
        body: JSON.stringify({
          requestId: requestIdRef.current,
          orderType: item.orderType || 1,
          itemId: item.orderType === 2 ? null : Number(item.id),
          packageId: item.orderType === 2 ? Number(item.id) : null,
          itemName: item.name,
          amount: Number(item.price),
          peopleCount: Number(form.peopleCount),
          contactName: form.contactName,
          contactPhone: form.contactPhone,
          reserveTime: normalizeDateTimeForApi(form.reserveTime),
          remark: form.remark
        })
      });
      onToast("预约已提交");
      onClose();
      onReload();
    } catch (err) {
      setError(err.message);
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="drawer-backdrop" role="presentation">
      <aside className="drawer" role="dialog" aria-modal="true" aria-label="提交预约">
        <div className="section-head">
          <div>
            <p className="eyebrow">预约</p>
            <h2>{item.name}</h2>
          </div>
          <button className="icon-button" type="button" aria-label="关闭" onClick={onClose} disabled={submitting}><X size={18} /></button>
        </div>
        {item.address ? <p className="muted">{item.address}{item.meetingPoint ? ` · ${item.meetingPoint}` : ""}</p> : null}
        <form className="form-grid" onSubmit={submit}>
          {error ? <div className="form-error">{error}</div> : null}
          <label className="field"><span>人数</span><input type="number" min="1" max="100000" step="1" value={form.peopleCount} required onChange={(event) => setForm({ ...form, peopleCount: event.target.value })} /></label>
          <label className="field"><span>联系人</span><input value={form.contactName} maxLength={32} required onChange={(event) => setForm({ ...form, contactName: event.target.value })} /></label>
          <label className="field"><span>手机号</span><input inputMode="numeric" value={form.contactPhone} maxLength={11} pattern={PHONE_PATTERN} required onChange={(event) => setForm({ ...form, contactPhone: event.target.value })} /></label>
          <label className="field"><span>预约时间</span><input type="datetime-local" value={form.reserveTime} required onChange={(event) => setForm({ ...form, reserveTime: event.target.value })} /></label>
          <label className="field"><span>备注</span><textarea value={form.remark} maxLength={255} onChange={(event) => setForm({ ...form, remark: event.target.value })} /></label>
          <div className="button-row">
            <button className="button" type="submit" disabled={submitting || !canBook}><CalendarCheck size={17} />{submitting ? "提交中" : canBook ? "提交预约" : "休息中"}</button>
            <button className="button-ghost" type="button" onClick={onClose} disabled={submitting}>取消</button>
          </div>
        </form>
      </aside>
    </div>
  );
}

export function ClientApp() {
  return rootPage() === "login" ? <ClientLogin /> : <ClientShell />;
}
