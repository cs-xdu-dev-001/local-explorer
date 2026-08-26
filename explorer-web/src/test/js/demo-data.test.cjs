const assert = require("node:assert/strict");
const test = require("node:test");

const Demo = require("../../main/resources/static/assets/demo-data.js");

test("detects demo mode from query string", () => {
  assert.equal(Demo.isEnabled({ search: "?demo=1" }), true);
  assert.equal(Demo.isEnabled({ search: "?page=1&demo=1" }), true);
  assert.equal(Demo.isEnabled({ search: "?demo=0" }), false);
});

test("seeds admin and client sessions without writing demo tokens to storage", () => {
  const store = new Map();
  const storage = {
    setItem: (key, value) => store.set(key, value),
    getItem: (key) => store.get(key)
  };
  const adminState = {};
  const clientState = {};

  Demo.seedAdminSession(adminState, storage);
  Demo.seedClientSession(clientState, storage);

  assert.equal(adminState.token, "demo-admin-token");
  assert.equal(adminState.userName, "运营管理员");
  assert.equal(clientState.token, "demo-user-token");
  assert.equal(clientState.userName, "林夏");
  assert.equal(storage.getItem("localExplorerAdminToken"), undefined);
  assert.equal(storage.getItem("localExplorerUserToken"), undefined);
});

test("serves representative admin data for screenshots", async () => {
  const merchant = await Demo.adminRequest("/admin/merchant/info");
  const items = await Demo.adminRequest("/admin/explore-item/page?page=1&pageSize=2");
  const trend = await Demo.adminRequest("/admin/explore-order/trend");
  const user = await Demo.adminRequest("/admin/user-manage/31");

  assert.equal(merchant.name, "城市生活探店馆");
  assert.ok(merchant.notice);
  assert.ok("coverImage" in merchant);
  assert.equal("serviceArea" in merchant, false);
  assert.equal("description" in merchant, false);
  assert.equal(items.records.length, 2);
  assert.ok(items.total >= 4);
  assert.equal(trend.dates.length, trend.orderCounts.length);
  assert.ok(Number(trend.confirmedRevenue) > 0);
  assert.ok(Number(trend.completedOrders) > 0);
  assert.ok(Number(trend.canceledOrders) >= 0);
  assert.ok(Number(trend.completionRate) > 0);
  assert.equal(user.phone, "13800010001");
  assert.equal(user.idNumber, "330100199901010011");
  assert.equal(user.status, 1);
});

test("serves representative client data for screenshots", async () => {
  const categories = await Demo.clientRequest("/user/category/list?type=1");
  const list = await Demo.clientRequest("/user/explore-item/list?page=1&pageSize=3");
  const favorites = await Demo.clientRequest("/user/favorite/count");

  assert.equal(categories[0].name, "咖啡甜品");
  assert.equal(list.records.length, 3);
  assert.ok(favorites > 0);
});

test("demo favorite endpoints mutate list state like the real backend", async () => {
  const before = await Demo.clientRequest("/user/favorite?page=1&pageSize=99");
  assert.ok(before.some((item) => Number(item.id) === 11));

  await Demo.clientRequest("/user/favorite/11", { method: "DELETE" });
  const afterDelete = await Demo.clientRequest("/user/favorite?page=1&pageSize=99");
  const countAfterDelete = await Demo.clientRequest("/user/favorite/count");
  const checkAfterDelete = await Demo.clientRequest("/user/favorite/check/11");

  assert.equal(afterDelete.some((item) => Number(item.id) === 11), false);
  assert.equal(countAfterDelete, afterDelete.length);
  assert.equal(checkAfterDelete, false);

  await Demo.clientRequest("/user/favorite/11", { method: "POST" });
  const restored = await Demo.clientRequest("/user/favorite/check/11");
  assert.equal(restored, true);
});

test("serves business-like demo data for interview walkthroughs", async () => {
  const list = await Demo.clientRequest("/user/explore-item/list?page=1&pageSize=1");
  const orders = await Demo.adminRequest("/admin/explore-order/page?page=1&pageSize=1");
  const reviews = await Demo.adminRequest("/admin/review/page?page=1&pageSize=1");

  const item = list.records[0];
  assert.ok(item.address, "item should include a real visit address");
  assert.ok(item.district, "item should include a city district");
  assert.ok(Number.isInteger(item.durationMinutes), "item should include duration");
  assert.ok(Number.isInteger(item.capacity), "item should include capacity");
  assert.ok(Number.isInteger(item.booked), "item should include current bookings");
  assert.ok(item.cancelPolicy, "item should include cancel policy");

  assert.ok(orders.records[0].channel, "order should include booking channel");
  assert.ok(orders.records[0].paymentStatus, "order should include payment status");
  assert.ok(reviews.records[0].replyContent, "review should include merchant reply");
});

test("demo user management supports status changes", async () => {
  await Demo.adminRequest("/admin/user-manage/status/0?id=31", { method: "POST" });
  const disabled = await Demo.adminRequest("/admin/user-manage/31");
  assert.equal(disabled.status, 0);

  await Demo.adminRequest("/admin/user-manage/status/1?id=31", { method: "POST" });
  const enabled = await Demo.adminRequest("/admin/user-manage/31");
  assert.equal(enabled.status, 1);
});

test("demo completed item orders carry review state", async () => {
  const orders = await Demo.clientRequest("/user/explore-order/page?page=1&pageSize=99");
  const reviewedOrder = orders.records.find((order) => Number(order.status) === 2 && Number(order.orderType) === 1);

  assert.ok(reviewedOrder, "demo should include a completed item order");
  assert.equal(reviewedOrder.hasReview, true);
});

test("demo completed package orders can be reviewed and update order state", async () => {
  const before = await Demo.clientRequest("/user/explore-order/page?page=1&pageSize=99");
  const reviewablePackageOrder = before.records.find((order) => Number(order.status) === 2 && Number(order.orderType) === 2 && !order.hasReview);

  assert.ok(reviewablePackageOrder, "demo should include a completed package order waiting for review");

  await Demo.clientRequest("/user/review", {
    method: "POST",
    body: JSON.stringify({
      orderId: reviewablePackageOrder.id,
      rating: 5,
      content: "套餐组合顺畅，适合半日体验。"
    })
  });

  const after = await Demo.clientRequest("/user/explore-order/page?page=1&pageSize=99");
  const reviewedPackageOrder = after.records.find((order) => Number(order.id) === Number(reviewablePackageOrder.id));
  const reviews = await Demo.adminRequest(`/admin/review/page?page=1&pageSize=99&keyword=${encodeURIComponent(reviewablePackageOrder.itemName)}&rating=5`);

  assert.equal(reviewedPackageOrder.hasReview, true);
  assert.equal(reviews.records.some((review) => review.orderId === reviewablePackageOrder.id), true);
});

test("demo admin order and review pages honor backend-style filters", async () => {
  const filteredOrders = await Demo.adminRequest("/admin/explore-order/page?page=1&pageSize=99&keyword=陈一&status=1");
  const keywordReviews = await Demo.adminRequest("/admin/review/page?page=1&pageSize=99&keyword=建筑&rating=5&replyState=replied");
  const unrepliedReviews = await Demo.adminRequest("/admin/review/page?page=1&pageSize=99&replyState=unreplied");

  assert.equal(filteredOrders.records.length, 1);
  assert.equal(filteredOrders.records[0].contactName, "陈一");
  assert.equal(filteredOrders.records[0].status, 1);

  assert.equal(keywordReviews.records.length, 1);
  assert.match(keywordReviews.records[0].itemName, /建筑/);
  assert.equal(keywordReviews.records[0].rating, 5);
  assert.ok(keywordReviews.records[0].replyContent);

  assert.equal(unrepliedReviews.records.every((review) => !review.replyContent), true);
});

test("demo records avoid product-visible placeholder wording", async () => {
  const employees = await Demo.adminRequest("/admin/employee/page?page=1&pageSize=99");
  const logs = await Demo.adminRequest("/admin/operation-log/page?page=1&pageSize=99");
  const adminLogin = await Demo.adminRequest("/admin/employee/login", { method: "POST" });
  const userLogin = await Demo.clientRequest("/user/user/login", { method: "POST" });
  const visibleText = [
    adminLogin.name,
    userLogin.name,
    ...employees.records.map((employee) => employee.name),
    ...logs.records.map((log) => log.operatorName)
  ].join("\n");

  assert.doesNotMatch(visibleText, /演示|样例|测试用户|体验用户/);
});
