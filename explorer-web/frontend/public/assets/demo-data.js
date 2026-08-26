(function (root, factory) {
  const Demo = factory();
  if (typeof module === "object" && module.exports) {
    module.exports = Demo;
  }
  root.LocalExplorerDemo = Demo;
})(typeof window !== "undefined" ? window : globalThis, function () {
  const image = (name) => `../assets/images/${name}`;

  const categories = [
    { id: 1, name: "咖啡甜品", type: 1, sort: 1, status: 1 },
    { id: 2, name: "书店展览", type: 1, sort: 2, status: 1 },
    { id: 3, name: "桌游社交", type: 1, sort: 3, status: 1 },
    { id: 4, name: "城市漫游", type: 1, sort: 4, status: 1 },
    { id: 101, name: "周末半日游", type: 2, sort: 1, status: 1 },
    { id: 102, name: "双人体验包", type: 2, sort: 2, status: 1 }
  ];

  const items = [
    {
      id: 11,
      name: "手冲咖啡品鉴",
      categoryId: 1,
      categoryName: "咖啡甜品",
      price: 68,
      image: image("coffee.webp"),
      description: "精品咖啡豆品鉴、拉花体验和店内拍照打卡。",
      district: "静安",
      address: "静安区愚园路 88 号 2F",
      meetingPoint: "店内吧台签到",
      durationMinutes: 90,
      capacity: 12,
      booked: 8,
      cancelPolicy: "开始前 2 小时可免费取消",
      hostName: "主理人阿然",
      status: 1,
      tags: [{ name: "适合", value: "双人" }, { name: "风味", value: "浅烘" }]
    },
    {
      id: 12,
      name: "独立书店夜读",
      categoryId: 2,
      categoryName: "书店展览",
      price: 39,
      image: image("bookstore.webp"),
      description: "精选书单、夜间阅读座位和一杯热饮。",
      district: "徐汇",
      address: "徐汇区武康路 126 号",
      meetingPoint: "二楼阅读区入口",
      durationMinutes: 120,
      capacity: 18,
      booked: 11,
      cancelPolicy: "开始前 4 小时可免费取消",
      hostName: "书店策展人南星",
      status: 1,
      tags: [{ name: "氛围", value: "安静" }, { name: "书单", value: "城市散文" }]
    },
    {
      id: 13,
      name: "桌游新手局",
      categoryId: 3,
      categoryName: "桌游社交",
      price: 58,
      image: image("boardgame.webp"),
      description: "店员带局，适合新手快速加入社交桌游。",
      district: "黄浦",
      address: "黄浦区南京东路 299 号 5F",
      meetingPoint: "前台领取桌号",
      durationMinutes: 150,
      capacity: 16,
      booked: 6,
      cancelPolicy: "开始前 1 小时可免费取消",
      hostName: "桌游主持小北",
      status: 1,
      tags: [{ name: "人数", value: "4-6人" }, { name: "难度", value: "入门" }]
    },
    {
      id: 14,
      name: "城市建筑漫游",
      categoryId: 4,
      categoryName: "城市漫游",
      price: 88,
      image: image("citywalk.webp"),
      description: "跟随路线探索街区建筑、咖啡馆和独立小店。",
      district: "长宁",
      address: "长宁区番禺路 390 号集合",
      meetingPoint: "地铁 10 号线交通大学站 6 号口",
      durationMinutes: 150,
      capacity: 20,
      booked: 17,
      cancelPolicy: "开始前 24 小时可免费取消",
      hostName: "城市讲解员林屿",
      status: 1,
      tags: [{ name: "路线", value: "2.5公里" }, { name: "讲解", value: "含" }]
    },
    {
      id: 15,
      name: "手作香薰工作坊",
      categoryId: 2,
      categoryName: "书店展览",
      price: 128,
      image: image("workshop.webp"),
      description: "制作一份可带走的香薰蜡烛，适合周末放松。",
      district: "浦东",
      address: "浦东新区滨江大道 1777 号",
      meetingPoint: "工作坊前台",
      durationMinutes: 120,
      capacity: 10,
      booked: 4,
      cancelPolicy: "开始前 6 小时可免费取消",
      hostName: "手作老师安可",
      status: 1,
      tags: [{ name: "成品", value: "可带走" }, { name: "香型", value: "木质调" }]
    }
  ];

  const packages = [
    {
      id: 21,
      name: "咖啡书店半日包",
      categoryId: 101,
      categoryName: "周末半日游",
      price: 99,
      image: image("package.webp"),
      description: "咖啡品鉴 + 独立书店夜读，适合周末轻体验。",
      district: "静安 / 徐汇",
      address: "愚园路 88 号至武康路 126 号",
      meetingPoint: "愚园路咖啡店吧台",
      durationMinutes: 210,
      capacity: 8,
      booked: 5,
      cancelPolicy: "开始前 24 小时可免费取消",
      status: 1,
      tags: [{ name: "节奏", value: "轻松" }, { name: "交通", value: "步行可达" }],
      packageItems: [{ itemId: 11, name: "手冲咖啡品鉴", copies: 1 }, { itemId: 12, name: "独立书店夜读", copies: 1 }]
    },
    {
      id: 22,
      name: "朋友聚会体验包",
      categoryId: 102,
      categoryName: "双人体验包",
      price: 168,
      image: image("boardgame.webp"),
      description: "桌游新手局 + 咖啡甜品，适合朋友小聚。",
      district: "黄浦",
      address: "黄浦区南京东路 299 号 5F",
      meetingPoint: "桌游区 3 号桌",
      durationMinutes: 180,
      capacity: 12,
      booked: 9,
      cancelPolicy: "开始前 2 小时可免费取消",
      status: 1,
      tags: [{ name: "适合", value: "朋友聚会" }, { name: "难度", value: "入门" }],
      packageItems: [{ itemId: 13, name: "桌游新手局", copies: 2 }, { itemId: 11, name: "手冲咖啡品鉴", copies: 1 }]
    },
    {
      id: 23,
      name: "城市漫游摄影包",
      categoryId: 101,
      categoryName: "周末半日游",
      price: 188,
      image: image("citywalk.webp"),
      description: "街区漫游路线 + 小店休息点，适合拍照打卡。",
      district: "长宁",
      address: "番禺路 390 号集合，愚园路结束",
      meetingPoint: "交通大学站 6 号口",
      durationMinutes: 180,
      capacity: 14,
      booked: 12,
      cancelPolicy: "开始前 24 小时可免费取消",
      status: 1,
      tags: [{ name: "主题", value: "城市建筑" }, { name: "服务", value: "路线讲解" }],
      packageItems: [{ itemId: 14, name: "城市建筑漫游", copies: 1 }]
    }
  ];

  const users = [
    { id: 31, name: "林夏", phone: "13800010001", sex: "0", idNumber: "330100199901010011", avatar: image("coffee.webp"), status: 1, createTime: "2026-05-01T10:20:00" },
    { id: 32, name: "陈一", phone: "13800010002", sex: "1", idNumber: "330100199802020022", avatar: image("bookstore.webp"), status: 1, createTime: "2026-05-02T11:30:00" }
  ];

  const employees = [
    { id: 1, name: "运营管理员", username: "admin", phone: "13900000001", status: 1, updateTime: "2026-05-20T16:20:00" },
    { id: 2, name: "内容运营", username: "editor", phone: "13900000002", status: 1, updateTime: "2026-05-22T09:10:00" }
  ];

  const orders = [
    { id: 41, orderNo: "LE202605240001", userName: "林夏", orderType: 1, itemId: 11, itemName: "手冲咖啡品鉴", amount: 68, peopleCount: 2, contactName: "林夏", contactPhone: "13800010001", reserveTime: "2026-05-30T15:00:00", createTime: "2026-05-24T10:12:00", status: 0, channel: "用户端预约", paymentStatus: "待支付", remark: "希望靠窗座位" },
    { id: 42, orderNo: "LE202605230006", userName: "陈一", orderType: 2, packageId: 21, itemName: "咖啡书店半日包", amount: 99, peopleCount: 1, contactName: "陈一", contactPhone: "13800010002", reserveTime: "2026-05-29T19:30:00", createTime: "2026-05-23T20:08:00", status: 1, channel: "用户端预约", paymentStatus: "已支付", remark: "素食点心优先" },
    { id: 43, orderNo: "LE202605210012", userName: "林夏", orderType: 1, itemId: 14, itemName: "城市建筑漫游", amount: 88, peopleCount: 2, contactName: "林夏", contactPhone: "13800010001", reserveTime: "2026-05-25T09:30:00", createTime: "2026-05-21T18:40:00", status: 2, channel: "后台补录", paymentStatus: "已退款", remark: "天气原因改期完成", hasReview: true },
    { id: 44, orderNo: "LE202605200009", userName: "林夏", orderType: 2, packageId: 23, itemName: "城市漫游摄影包", amount: 188, peopleCount: 2, contactName: "林夏", contactPhone: "13800010001", reserveTime: "2026-05-22T14:00:00", createTime: "2026-05-20T16:18:00", status: 2, channel: "用户端预约", paymentStatus: "已支付", remark: "已完成待评价", hasReview: false }
  ];

  const notifications = [
    { id: 71, eventId: "demo-event-confirmed", orderId: 42, notificationType: "ORDER_CONFIRMED", title: "预约已确认", content: "咖啡书店半日包已确认，点击查看预约详情。", readStatus: 0, createTime: "2026-05-24T11:20:00" },
    { id: 72, eventId: "demo-event-completed", orderId: 44, notificationType: "ORDER_COMPLETED", title: "预约已完成", content: "城市漫游摄影包已完成，欢迎分享体验。", readStatus: 0, createTime: "2026-05-23T20:30:00" },
    { id: 73, eventId: "demo-event-canceled", orderId: 43, notificationType: "ORDER_CANCELED_BY_USER", title: "预约已取消", content: "预约已取消，相关名额已经释放。", readStatus: 1, createTime: "2026-05-22T09:10:00" }
  ];

  const reviews = [
    { id: 51, userName: "林夏", userAvatar: image("coffee.webp"), itemName: "城市建筑漫游", rating: 5, content: "路线安排很舒服，讲解也很细。", replyContent: "感谢反馈，下周会补充两处建筑故事点。", replyTime: "2026-05-22T21:00:00", createTime: "2026-05-22T20:20:00" },
    { id: 52, userName: "陈一", userAvatar: image("bookstore.webp"), itemName: "独立书店夜读", rating: 4, content: "氛围很好，适合下班后放松。", replyContent: "谢谢喜欢，我们会继续更新夜读书单。", replyTime: "2026-05-20T21:30:00", createTime: "2026-05-20T21:10:00" }
  ];

  const operationLogs = [
    { id: 61, description: "更新特色项目", operatorName: "运营管理员", requestMethod: "PUT", requestUri: "/admin/explore-item", clientIp: "8a4f2d913bc672e0", createTime: "2026-05-24T11:30:00" },
    { id: 62, description: "确认预约订单", operatorName: "内容运营", requestMethod: "PUT", requestUri: "/admin/explore-order/status", clientIp: "d19370c2b65f840a", createTime: "2026-05-24T10:45:00" }
  ];

  const exportJobSeed = [
    { jobId: "demoexport0001", requestId: "demo-request-1", exportType: "ORDER", fileFormat: "XLSX", status: "SUCCEEDED", progress: 100, totalRows: 18, processedRows: 18, fileName: "订单导出_20260524_113000.xlsx", fileSize: 18432, checksum: "demo", retryCount: 0, operatorId: 1, operatorName: "运营管理员", createTime: "2026-05-24T11:30:00", finishedAt: "2026-05-24T11:30:02", expiresAt: "2026-05-25T11:30:02" },
    { jobId: "demoexport0002", requestId: "demo-request-2", exportType: "REVIEW", fileFormat: "CSV", status: "RUNNING", progress: 64, totalRows: 25, processedRows: 16, fileName: null, fileSize: null, checksum: null, retryCount: 0, operatorId: 1, operatorName: "运营管理员", createTime: "2026-05-24T11:35:00", expiresAt: null },
    { jobId: "demoexport0003", requestId: "demo-request-3", exportType: "USER", fileFormat: "XLSX", status: "FAILED", progress: 72, totalRows: 40, processedRows: 29, fileName: null, fileSize: null, checksum: null, retryCount: 4, errorCode: "EXPORT_GENERATION_FAILED", errorMessage: "文件写入失败，请重试", operatorId: 1, operatorName: "运营管理员", createTime: "2026-05-24T11:36:00", finishedAt: "2026-05-24T11:36:08", expiresAt: null }
  ];
  const exportStorageKey = "localExplorerDemoExportJobs";
  const savedExportJobs = (() => {
    try {
      const value = JSON.parse(localStorage.getItem(exportStorageKey) || "null");
      return Array.isArray(value) ? value : null;
    } catch (_error) {
      return null;
    }
  })();
  const exportJobs = savedExportJobs || exportJobSeed;

  function persistExportJobs() {
    localStorage.setItem(exportStorageKey, JSON.stringify(exportJobs));
  }

  function finishDemoExport(job) {
    if (job.status !== "PENDING") return;
    job.status = "SUCCEEDED";
    job.progress = 100;
    job.processedRows = job.totalRows;
    job.fileName = `${job.exportType.toLowerCase()}-demo.${job.fileFormat.toLowerCase()}`;
    job.fileSize = 12800;
    job.errorCode = null;
    job.errorMessage = null;
    delete job.completeAt;
    job.finishedAt = new Date().toISOString();
    job.expiresAt = new Date(Date.now() + 86400000).toISOString();
    persistExportJobs();
  }

  exportJobs.filter((job) => job.status === "PENDING" && job.completeAt).forEach((job) => {
    setTimeout(() => finishDemoExport(job), Math.max(50, Number(job.completeAt) - Date.now()));
  });

  const merchant = {
    name: "城市生活探店馆",
    slogan: "发现身边值得体验的本地生活内容",
    address: "城市中心街区 88 号",
    phone: "400-800-2026",
    businessHours: "周二至周日 10:00-22:00",
    notice: "预约前请确认门店营业状态，部分项目需提前联系商家。",
    coverImage: ""
  };

  const trend = {
    dates: ["05-18", "05-19", "05-20", "05-21", "05-22", "05-23", "05-24"],
    orderCounts: [4, 6, 7, 9, 8, 12, 14],
    reviewCounts: [1, 2, 3, 2, 5, 4, 6],
    totalOrders: 60,
    totalReviews: 23,
    totalUsers: 118,
    pendingOrders: 5,
    confirmedRevenue: 12480,
    completedOrders: 38,
    canceledOrders: 7,
    completionRate: 63
  };
  const favoriteItemIds = new Set([11, 14]);

  function isEnabled(locationLike) {
    const source = locationLike || (typeof location !== "undefined" ? location : { search: "" });
    return new URLSearchParams(source.search || "").get("demo") === "1";
  }

  function params(path) {
    return new URL(path, "https://local-explorer.demo").searchParams;
  }

  function page(rows, path) {
    const query = params(path);
    const current = Math.max(1, Number(query.get("page") || 1));
    const size = Math.max(1, Number(query.get("pageSize") || 10));
    return { total: rows.length, records: rows.slice((current - 1) * size, current * size) };
  }

  function byType(path) {
    const type = Number(params(path).get("type"));
    return categories.filter((category) => !type || Number(category.type) === type);
  }

  function filteredItems(path) {
    const query = params(path);
    const categoryId = Number(query.get("categoryId"));
    const name = query.get("name") || "";
    return items.filter((item) => {
      const categoryMatch = !categoryId || Number(item.categoryId) === categoryId;
      const nameMatch = !name || item.name.includes(name);
      return categoryMatch && nameMatch;
    });
  }

  function textIncludes(value, keyword) {
    return String(value || "").includes(keyword);
  }

  function filteredOrders(path) {
    const query = params(path);
    const keyword = query.get("keyword") || "";
    const status = query.get("status") || "";
    return orders.filter((order) => {
      const keywordMatch = !keyword || [
        order.orderNo,
        order.itemName,
        order.userName,
        order.contactName,
        order.contactPhone
      ].some((value) => textIncludes(value, keyword));
      const statusMatch = !status || String(order.status) === status;
      return keywordMatch && statusMatch;
    });
  }

  function filteredReviews(path) {
    const query = params(path);
    const keyword = query.get("keyword") || "";
    const rating = query.get("rating") || "";
    const replyState = query.get("replyState") || "";
    return reviews.filter((review) => {
      const keywordMatch = !keyword || [
        review.itemName,
        review.userName,
        review.content,
        review.replyContent
      ].some((value) => textIncludes(value, keyword));
      const ratingMatch = !rating || String(review.rating) === rating;
      const hasReply = Boolean(review.replyContent);
      const replyMatch = !replyState ||
        (replyState === "replied" && hasReply) ||
        (replyState === "unreplied" && !hasReply);
      return keywordMatch && ratingMatch && replyMatch;
    });
  }

  function favoriteItems() {
    return items.filter((item) => favoriteItemIds.has(Number(item.id)));
  }

  function seedAdminSession(state) {
    state.token = "demo-admin-token";
    state.userName = "运营管理员";
    state.role = "ADMIN";
  }

  function seedClientSession(state) {
    state.token = "demo-user-token";
    state.userId = "31";
    state.userName = "林夏";
  }

  async function adminRequest(path, options = {}) {
    const method = (options.method || "GET").toUpperCase();
    if (path.startsWith("/admin/employee/login")) return { token: "demo-admin-token", name: "运营管理员", userName: "admin", role: "ADMIN" };
    if (path === "/admin/export-jobs" && method === "POST") {
      const payload = JSON.parse(options.body || "{}");
      const job = {
        jobId: `demoexport${Date.now()}`,
        requestId: payload.requestId,
        exportType: payload.exportType,
        fileFormat: payload.fileFormat,
        status: "PENDING",
        progress: 0,
        totalRows: 36,
        processedRows: 0,
        retryCount: 0,
        operatorId: 1,
        operatorName: "运营管理员",
        createTime: new Date().toISOString(),
        completeAt: Date.now() + 2500,
        expiresAt: null
      };
      exportJobs.unshift(job);
      persistExportJobs();
      setTimeout(() => finishDemoExport(job), 2500);
      return { jobId: job.jobId, requestId: job.requestId, status: job.status };
    }
    const exportCancel = path.match(/^\/admin\/export-jobs\/([^/]+)\/cancel/);
    if (exportCancel && method === "POST") {
      const job = exportJobs.find((item) => item.jobId === exportCancel[1]);
      if (job) { job.status = "CANCELED"; job.progress = 0; persistExportJobs(); }
      return true;
    }
    const exportRetry = path.match(/^\/admin\/export-jobs\/([^/]+)\/retry/);
    if (exportRetry && method === "POST") {
      const job = exportJobs.find((item) => item.jobId === exportRetry[1]);
      if (job) {
        job.status = "PENDING";
        job.progress = 0;
        job.processedRows = 0;
        job.retryCount = 0;
        job.completeAt = Date.now() + 1200;
        persistExportJobs();
        setTimeout(() => finishDemoExport(job), 1200);
      }
      return true;
    }
    if (path.startsWith("/admin/user-manage/status/")) {
      const status = Number(path.split("/status/")[1].split("?")[0]);
      const id = Number(params(path).get("id"));
      const user = users.find((item) => Number(item.id) === id);
      if (user) user.status = status;
      return true;
    }
    if (method !== "GET") return true;
    if (path.startsWith("/admin/merchant/info")) return merchant;
    if (path.startsWith("/admin/shop/status")) return 1;
    if (path.startsWith("/admin/category/list")) return byType(path);
    if (path.startsWith("/admin/category/page")) return page(categories, path);
    if (path.startsWith("/admin/explore-item/page")) return page(items, path);
    if (path.startsWith("/admin/explore-item/list")) return items;
    if (path.startsWith("/admin/explore-package/page")) return page(packages, path);
    if (path.startsWith("/admin/explore-order/trend")) return trend;
    if (path.startsWith("/admin/explore-order/page")) return page(filteredOrders(path), path);
    if (path.startsWith("/admin/review/page")) return page(filteredReviews(path), path);
    if (path.startsWith("/admin/user-manage/page")) return page(users, path);
    if (path.startsWith("/admin/user-manage/")) return users.find((user) => Number(user.id) === Number(path.split("/").pop())) || null;
    if (path.startsWith("/admin/employee/page")) return page(employees, path);
    if (path.startsWith("/admin/operation-log/page")) return page(operationLogs, path);
    if (path.startsWith("/admin/export-jobs/stats")) {
      const latestFailure = exportJobs.find((item) => item.status === "FAILED");
      return {
      pending: exportJobs.filter((item) => item.status === "PENDING").length,
      running: exportJobs.filter((item) => item.status === "RUNNING").length,
      succeeded: exportJobs.filter((item) => item.status === "SUCCEEDED").length,
      failed: exportJobs.filter((item) => item.status === "FAILED").length,
      successRate: 96.4,
      recentFailureJobId: latestFailure?.jobId || null,
      recentFailureErrorCode: latestFailure?.errorCode || null
    };
    }
    if (path.startsWith("/admin/export-jobs/page")) {
      const query = params(path);
      const filtered = exportJobs.filter((item) =>
        (!query.get("exportType") || item.exportType === query.get("exportType")) &&
        (!query.get("status") || item.status === query.get("status"))
      );
      return page(filtered, path);
    }
    const exportDetail = path.match(/^\/admin\/export-jobs\/([^/?]+)$/);
    if (exportDetail) return exportJobs.find((item) => item.jobId === exportDetail[1]) || null;
    return true;
  }

  async function clientRequest(path, options = {}) {
    const method = (options.method || "GET").toUpperCase();
    if (path.startsWith("/user/user/login")) return { token: "demo-user-token", id: 31, name: "林夏" };
    if (path.startsWith("/user/notification/read-all") && method === "PUT") {
      notifications.forEach((notification) => { notification.readStatus = 1; });
      return true;
    }
    const notificationReadMatch = path.match(/^\/user\/notification\/(\d+)\/read/);
    if (notificationReadMatch && method === "PUT") {
      const notification = notifications.find((item) => Number(item.id) === Number(notificationReadMatch[1]));
      if (notification) notification.readStatus = 1;
      return true;
    }
    if (path.startsWith("/user/favorite/") && !path.startsWith("/user/favorite/browse/") && !path.startsWith("/user/favorite/check/")) {
      const itemId = Number(path.split("/").pop());
      if (method === "POST") favoriteItemIds.add(itemId);
      if (method === "DELETE") favoriteItemIds.delete(itemId);
      if (method !== "GET") return true;
    }
    if (path.startsWith("/user/review") && method === "POST") {
      const payload = JSON.parse(options.body || "{}");
      const order = orders.find((item) => Number(item.id) === Number(payload.orderId));
      if (!order || Number(order.status) !== 2) throw new Error("订单完成后才能评价");
      if (order.hasReview) throw new Error("该订单已评价");
      const packageEntity = packages.find((item) => Number(item.id) === Number(order.packageId));
      const packageItemId = packageEntity?.packageItems?.find((item) => item.itemId)?.itemId;
      reviews.unshift({
        id: Math.max(...reviews.map((review) => Number(review.id))) + 1,
        userName: order.userName || "林夏",
        userAvatar: image("coffee.webp"),
        itemId: order.itemId || packageItemId,
        packageId: order.packageId,
        orderId: order.id,
        itemName: order.itemName,
        rating: Number(payload.rating || 5),
        content: payload.content || "",
        replyContent: "",
        replyTime: null,
        createTime: new Date().toISOString()
      });
      order.hasReview = true;
      return true;
    }
    if (method !== "GET") return path.startsWith("/user/explore-order") ? 10001 : true;
    if (path.startsWith("/user/merchant/info")) return merchant;
    if (path.startsWith("/user/shop/status")) return 1;
    if (path.startsWith("/user/category/list")) return byType(path);
    if (path.startsWith("/user/explore-item/list")) return page(filteredItems(path), path);
    if (path.startsWith("/user/explore-package/items/")) {
      const packageId = Number(path.split("/").pop());
      const packageEntity = packages.find((item) => Number(item.id) === packageId);
      return packageEntity?.packageItems || [];
    }
    if (path.startsWith("/user/explore-package/list")) return page(packages, path);
    if (path.startsWith("/user/favorite/browse/count")) return 3;
    if (path.startsWith("/user/favorite/browse")) return [items[3], items[0], items[1]];
    if (path.startsWith("/user/favorite/count")) return favoriteItemIds.size;
    if (path.startsWith("/user/favorite/check/")) return favoriteItemIds.has(Number(path.split("/").pop()));
    if (path.startsWith("/user/favorite")) return favoriteItems();
    if (path.startsWith("/user/notification/unread-count")) {
      return notifications.filter((notification) => Number(notification.readStatus) === 0).length;
    }
    if (path.startsWith("/user/notification/page")) return page(notifications, path);
    if (path.startsWith("/user/explore-order/page")) return page(orders, path);
    if (path.startsWith("/user/explore-order/")) {
      return orders.find((order) => Number(order.id) === Number(path.split("/").pop())) || null;
    }
    return true;
  }

  return {
    isEnabled,
    seedAdminSession,
    seedClientSession,
    adminRequest,
    clientRequest
  };
});
