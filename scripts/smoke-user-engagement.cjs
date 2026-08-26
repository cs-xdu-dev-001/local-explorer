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

function recordsOf(pageLike) {
  if (Array.isArray(pageLike)) return pageLike;
  return pageLike?.records || [];
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
    throw new Error(`${options.method || "GET"} ${path} failed: HTTP ${response.status}, msg=${payload.msg || "empty response"}`);
  }
  return payload.data;
}

function findCompletedUnreviewedOrder(orders, items) {
  const activeItemIds = new Set(items
    .filter((item) => Number(item.status) === 1)
    .map((item) => String(item.id)));
  return orders.find((order) => Number(order.status) === 2
    && Number(order.orderType) === 1
    && order.itemId
    && !order.hasReview
    && activeItemIds.has(String(order.itemId)));
}

function findCompletedUnreviewedPackageOrder(orders, packages) {
  const activePackageIds = new Set(packages
    .filter((packageEntity) => Number(packageEntity.status) === 1)
    .map((packageEntity) => String(packageEntity.id)));
  return orders.find((order) => Number(order.status) === 2
    && Number(order.orderType) === 2
    && order.packageId
    && !order.hasReview
    && activePackageIds.has(String(order.packageId)));
}

async function findReview(adminToken, itemId, orderId, content) {
  const page = await request(`/admin/review/page?page=1&pageSize=100&itemId=${itemId}`, { adminToken });
  return recordsOf(page).find((review) => Number(review.orderId) === Number(orderId) || review.content === content);
}

async function findReviewByKeyword(adminToken, keyword, orderId, content) {
  const page = await request(`/admin/review/page?page=1&pageSize=100&keyword=${encodeURIComponent(keyword)}`, { adminToken });
  return recordsOf(page).find((review) => Number(review.orderId) === Number(orderId) || review.content === content);
}

async function verifyAdminReviewFilters(adminToken, reviewId, reviewContent) {
  const page = await request(`/admin/review/page?page=1&pageSize=100&keyword=${encodeURIComponent(reviewContent)}&rating=5&replyState=replied`, { adminToken });
  const rows = recordsOf(page);
  const row = rows.find((review) => Number(review.id) === Number(reviewId));
  if (!row) throw new Error("后台评价筛选未返回刚回复的评价。");
  if (Number(row.rating) !== 5 || !row.replyContent) {
    throw new Error("后台评价筛选返回记录状态不符合评分/回复筛选。");
  }
}

async function verifyPackageReviewFilters(adminToken, reviewId, packageEntity) {
  const page = await request(`/admin/review/page?page=1&pageSize=100&keyword=${encodeURIComponent(packageEntity.name)}&rating=5`, { adminToken });
  const rows = recordsOf(page);
  const row = rows.find((review) => Number(review.id) === Number(reviewId));
  if (!row) throw new Error("后台评价筛选未按套餐名返回刚提交的套餐评价。");
  if (row.itemName !== packageEntity.name) {
    throw new Error(`套餐评价在后台显示为 ${row.itemName || "空"}，未显示订单套餐名 ${packageEntity.name}。`);
  }
}

async function submitPackageReview(adminToken, userToken, orders, packages, packageReviewContent) {
  const packageOrder = findCompletedUnreviewedPackageOrder(orders, packages);
  if (!packageOrder?.id) {
    throw new Error("没有可用于评价 smoke 的已完成待评价套餐订单，请用最新初始化 SQL 补齐演示数据。");
  }
  const packageEntity = packages.find((entry) => Number(entry.id) === Number(packageOrder.packageId));
  if (!packageEntity?.id) {
    throw new Error(`套餐订单 ${packageOrder.id} 未找到对应套餐 ${packageOrder.packageId}。`);
  }
  const packageItems = recordsOf(await request(`/user/explore-package/items/${packageEntity.id}`));
  const packageReviewItemId = packageItems.find((entry) => entry.itemId)?.itemId;
  if (!packageReviewItemId) {
    throw new Error(`套餐 ${packageEntity.id} 没有可回读评价的项目明细。`);
  }

  await request("/user/review", {
    method: "POST",
    userToken,
    body: { orderId: packageOrder.id, rating: 5, content: packageReviewContent }
  });
  const createdReview = await findReviewByKeyword(adminToken, packageEntity.name, packageOrder.id, packageReviewContent);
  const packageReviewId = createdReview?.id;
  if (!packageReviewId) throw new Error("套餐评价提交后，后台评价列表未找到对应记录。");

  await verifyPackageReviewFilters(adminToken, packageReviewId, packageEntity);

  const userReviews = recordsOf(await request(`/user/review/item/${packageReviewItemId}?page=1&pageSize=100`));
  const userReview = userReviews.find((review) => Number(review.id) === Number(packageReviewId));
  if (userReview?.content !== packageReviewContent) {
    throw new Error("套餐评价未在用户端项目评价列表正确回读。");
  }

  return { packageEntity, packageOrder, packageReviewId, packageReviewItemId };
}

async function main() {
  const stamp = Date.now().toString().slice(-8);
  const reviewContent = `真实链路评价-${stamp}`;
  const packageReviewContent = `套餐真实链路评价-${stamp}`;
  const replyContent = `商家已核验-${stamp}`;
  let adminToken;
  let userToken;
  let item;
  let orderId;
  let reviewId;
  let packageEntity;
  let packageOrderId;
  let packageReviewId;
  let packageReviewItemId;
  let originalFavorite = false;

  try {
    const admin = await request("/admin/employee/login", {
      method: "POST",
      body: { username: config.adminUsername, password: config.adminPassword }
    });
    adminToken = admin?.token;
    if (!adminToken) throw new Error("管理员登录未返回 token。");

    const user = await request("/user/user/login", {
      method: "POST",
      body: { phone: config.userPhone, password: config.userPassword }
    });
    userToken = user?.token;
    if (!userToken) throw new Error("用户登录未返回 token。");

    const items = recordsOf(await request("/user/explore-item/list?page=1&pageSize=50"));
    const packages = recordsOf(await request("/user/explore-package/list?page=1&pageSize=50"));
    const orders = recordsOf(await request("/user/explore-order/page?page=1&pageSize=100", { userToken }));
    const completedOrder = findCompletedUnreviewedOrder(orders, items);
    if (!completedOrder?.id) {
      throw new Error("没有可用于评价 smoke 的已完成待评价项目订单，请用最新初始化 SQL 补齐演示数据。");
    }
    orderId = completedOrder.id;
    item = items.find((entry) => Number(entry.id) === Number(completedOrder.itemId));

    await request(`/user/favorite/browse/${item.id}`, { method: "POST", userToken });
    const history = recordsOf(await request("/user/favorite/browse?page=1&pageSize=99", { userToken }));
    if (!history.some((entry) => Number(entry.id) === Number(item.id))) {
      throw new Error(`浏览记录中未找到项目 ${item.id}。`);
    }

    originalFavorite = Boolean(await request(`/user/favorite/check/${item.id}`, { userToken }));
    if (originalFavorite) {
      await request(`/user/favorite/${item.id}`, { method: "DELETE", userToken });
    }
    await request(`/user/favorite/${item.id}`, { method: "POST", userToken });
    const favorited = await request(`/user/favorite/check/${item.id}`, { userToken });
    const favorites = recordsOf(await request("/user/favorite?page=1&pageSize=99", { userToken }));
    if (!favorited || !favorites.some((entry) => Number(entry.id) === Number(item.id))) {
      throw new Error(`收藏状态或收藏列表未同步项目 ${item.id}。`);
    }

    await request("/user/review", {
      method: "POST",
      userToken,
      body: { itemId: item.id, orderId, rating: 5, content: reviewContent }
    });
    const createdReview = await findReview(adminToken, item.id, orderId, reviewContent);
    reviewId = createdReview?.id;
    if (!reviewId) throw new Error("用户评价提交后，后台评价列表未找到对应记录。");

    await request("/admin/review/reply", {
      method: "PUT",
      adminToken,
      body: { id: reviewId, replyContent }
    });
    await verifyAdminReviewFilters(adminToken, reviewId, reviewContent);

    const userReviews = recordsOf(await request(`/user/review/item/${item.id}?page=1&pageSize=100`));
    const replied = userReviews.find((review) => Number(review.id) === Number(reviewId));
    if (replied?.replyContent !== replyContent) {
      throw new Error("商家回复未在用户端评价列表正确回读。");
    }

    const packageReview = await submitPackageReview(adminToken, userToken, orders, packages, packageReviewContent);
    packageEntity = packageReview.packageEntity;
    packageOrderId = packageReview.packageOrder.id;
    packageReviewId = packageReview.packageReviewId;
    packageReviewItemId = packageReview.packageReviewItemId;

    console.table([
      { step: "browse history", result: `${item.id} ${item.name}` },
      { step: "favorite add/list/check", result: "passed" },
      { step: "completed order fixture", result: orderId },
      { step: "user review", result: reviewId },
      { step: "admin review filter", result: reviewContent },
      { step: "merchant reply", result: replyContent },
      { step: "package review", result: `${packageOrderId} ${packageEntity.name}` },
      { step: "package review item read-back", result: packageReviewItemId }
    ]);
  } finally {
    if (adminToken && !reviewId && item?.id && orderId) {
      const review = await findReview(adminToken, item.id, orderId, reviewContent).catch(() => null);
      reviewId = review?.id;
    }
    if (adminToken && !packageReviewId && packageEntity?.name && packageOrderId) {
      const review = await findReviewByKeyword(adminToken, packageEntity.name, packageOrderId, packageReviewContent).catch(() => null);
      packageReviewId = review?.id;
    }
    const cleanupReviewIds = [reviewId, packageReviewId].filter(Boolean);
    if (adminToken && cleanupReviewIds.length) {
      await request(`/admin/review?ids=${cleanupReviewIds.join(",")}`, { method: "DELETE", adminToken }).catch(() => {});
    }
    if (userToken && item?.id) {
      const currentFavorite = await request(`/user/favorite/check/${item.id}`, { userToken }).catch(() => originalFavorite);
      if (currentFavorite && !originalFavorite) {
        await request(`/user/favorite/${item.id}`, { method: "DELETE", userToken }).catch(() => {});
      } else if (!currentFavorite && originalFavorite) {
        await request(`/user/favorite/${item.id}`, { method: "POST", userToken }).catch(() => {});
      }
    }
  }
}

main().catch((error) => {
  console.error(error.message);
  process.exit(1);
});
