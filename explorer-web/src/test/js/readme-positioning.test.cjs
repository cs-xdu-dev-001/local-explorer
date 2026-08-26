const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const test = require("node:test");

const repoRoot = path.resolve(__dirname, "../../../../");
const readme = fs.readFileSync(path.join(repoRoot, "README.md"), "utf8");

function listMarkdownFiles(dir) {
  return fs.readdirSync(dir, { withFileTypes: true }).flatMap((entry) => {
    const fullPath = path.join(dir, entry.name);
    if (entry.isDirectory()) return listMarkdownFiles(fullPath);
    return entry.name.endsWith(".md") ? [fullPath] : [];
  });
}

function listTextFiles(dir) {
  const skippedDirs = new Set([".git", ".idea", "node_modules", "target"]);
  const allowedExtensions = new Set([".cmd", ".cjs", ".css", ".html", ".java", ".js", ".json", ".jsx", ".md", ".properties", ".ps1", ".sql", ".xml", ".yml"]);

  return fs.readdirSync(dir, { withFileTypes: true }).flatMap((entry) => {
    const fullPath = path.join(dir, entry.name);
    if (entry.isDirectory()) {
      if (skippedDirs.has(entry.name)) return [];
      if (fullPath.includes(path.join("src", "main", "resources", "static", "assets", "app"))) return [];
      return listTextFiles(fullPath);
    }
    return allowedExtensions.has(path.extname(entry.name)) ? [fullPath] : [];
  });
}

const forbiddenTerms = [
  String.fromCharCode(35838, 31243),
  String.fromCharCode(23567, 31243, 24207),
  String.fromCharCode(24494, 20449),
  String.fromCharCode(21407, 29983, 32, 72, 84, 77, 76),
  String.fromCharCode(38745, 24577, 23458, 25143, 31471),
  String.fromCharCode(29992, 25143, 38745, 24577, 31471),
  String.fromCharCode(112, 105, 99, 115, 117, 109, 46, 112, 104, 111, 116, 111, 115),
  ["We", "Chat"].join(""),
  ["open", "Id"].join(""),
  ["open", "id"].join("")
];
const forbidden = new RegExp(forbiddenTerms.join("|"), "i");

test("README presents a clear internship interview walkthrough", () => {
  assert.match(readme, /## 演示路径/);
  assert.match(readme, /## 可讲实现点/);
  assert.match(readme, /## 后端工程材料/);
  assert.match(readme, /docs\/BACKEND_DESIGN\.md/);
  assert.match(readme, /docs\/CONSISTENCY\.md/);
  assert.match(readme, /docs\/CACHE_AND_REDIS\.md/);
  assert.match(readme, /docs\/CACHE_HOT_PATH\.md/);
  assert.match(readme, /docs\/API_AND_ERRORS\.md/);
  assert.match(readme, /docs\/DATABASE_DESIGN\.md/);
  assert.match(readme, /docs\/TEST_REPORT\.md/);
  assert.match(readme, /docs\/TESTING\.md/);
  assert.match(readme, /docs\/INTEGRATION_TESTING\.md/);
  assert.match(readme, /docs\/OBSERVABILITY\.md/);
  assert.match(readme, /docs\/ORDER_RELIABILITY\.md/);
  assert.match(readme, /## 最近验证/);
  assert.match(readme, /\/console\/index\.html\?demo=1/);
  assert.match(readme, /\/client\/index\.html\?demo=1/);
  const mavenResult = readme.match(/Maven tests: (\d+) run, 0 failures, 0 errors, 0 skipped/);
  const nodeResult = readme.match(/Node frontend\/docs\/run tests: (\d+) passed/);
  assert.equal(Number(mavenResult?.[1]), 339, "README should keep the current verified Maven test count");
  assert.equal(Number(nodeResult?.[1]), 151, "README should keep the current verified Node test count");
  assert.match(readme, /Maven tests: 339 run, 0 failures, 0 errors, 0 skipped/);
  assert.match(readme, /Node frontend\/docs\/run tests: 151 passed/);
  assert.match(readme, /Testcontainers MySQL \+ Redis: 34 run, 0 failures, 0 errors, 0 skipped/);
  assert.match(readme, /Frontend dependency install: npm ci succeeded/);
  assert.match(readme, /Frontend dependency audit: 0 vulnerabilities/);
  assert.match(readme, /UI smoke: 6 rendered pages, 18 text checks/);
  assert.match(readme, /Interaction smoke: 6 flows, including loaded project images/);
  assert.match(readme, /Backend static assets: \/console\/index\.html and \/assets\/app resources returned 200/);
  assert.match(readme, /Backend entry redirects: \/, \/console and \/client route to login entries/);
  assert.match(readme, /Backend chain smoke: admin\/user login, create order, admin confirm, user cancel/);
  assert.match(readme, /Runtime settings smoke: merchant and shop state survive restart, admin\/user read-back, operation logs persisted/);
  assert.match(readme, /Admin management smoke: category\/item\/package\/employee CRUD, packageItems persistence and user\/employee status endpoints/);
  assert.match(readme, /User engagement smoke: browse, favorite, completed project and package review fixtures, merchant reply and user read-back/);
  assert.match(readme, /Missing-resource smoke: 9 detail\/edit\/delete paths rejected with business errors/);
  assert.match(readme, /Operation log audit: protected admin writes covered; failed writes are not recorded as success/);
  assert.match(readme, /Booking concurrency smoke: 2 concurrent requests, 1 success, booked count restored after cancel/);
  assert.match(readme, /Order reliability smoke: duplicate requestId, scheduled timeout, capacity restored, ORDER_EXPIRED notification visible and readable/);
  assert.match(readme, /Session revocation smoke: disabled employee\/user tokens return 401 immediately/);
  assert.match(readme, /Frontend build: Vite production build succeeded/);
});

test("README keeps the product positioning clean", () => {
  assert.doesNotMatch(readme, forbidden);
  assert.match(readme, /本地生活探店/);
  assert.match(readme, /Spring Boot/);
  assert.match(readme, /React/);
  assert.match(readme, /Vite/);
  assert.match(readme, /用户可以对项目或套餐订单进行评价/);
  assert.match(readme, /用户可以收藏和浏览特色项目/);
});

test("project markdown docs keep the same product positioning", () => {
  const markdownFiles = [path.join(repoRoot, "README.md"), ...listMarkdownFiles(path.join(repoRoot, "docs"))];
  const offenders = markdownFiles
    .filter((file) => forbidden.test(fs.readFileSync(file, "utf8")))
    .map((file) => path.relative(repoRoot, file));

  assert.deepEqual(offenders, []);
});

test("project text files avoid old mobile-platform positioning residue", () => {
  const offenders = listTextFiles(repoRoot)
    .filter((file) => forbidden.test(fs.readFileSync(file, "utf8")))
    .map((file) => path.relative(repoRoot, file));

  assert.deepEqual(offenders, []);
});

test("interview notes keep IDEA backend and simple frontend commands as the walkthrough", () => {
  const notes = fs.readFileSync(path.join(repoRoot, "docs", "INTERVIEW_NOTES.md"), "utf8");

  assert.match(notes, /IDEA/);
  assert.match(notes, /LocalExplorerApplication/);
  assert.match(notes, /BACKEND_DESIGN\.md/);
  assert.match(notes, /CONSISTENCY\.md/);
  assert.match(notes, /CACHE_AND_REDIS\.md/);
  assert.match(notes, /API_AND_ERRORS\.md/);
  assert.match(notes, /TESTING\.md/);
  assert.match(notes, /ORDER_RELIABILITY\.md/);
  assert.match(notes, /ASYNC_EXPORT\.md/);
  assert.match(notes, /\.\\run\.cmd/);
  assert.match(notes, /\.\\run\.cmd dev/);
  assert.doesNotMatch(notes, /mvnw\.cmd package/);
  assert.doesNotMatch(notes, /spring-boot:run/);
  assert.doesNotMatch(notes, /docker compose up -d/);
});

test("backend engineering docs exist and stay evidence-based", () => {
  const docs = [
    ["BACKEND_DESIGN.md", [/JwtTokenAdminInterceptor/, /AdminAuthorizationInterceptor/, /reserveCapacity/, /OperationLogAspect/, /RedisFallbackHealthIndicator/, /ExportJobProcessorTest/]],
    ["CONSISTENCY.md", [/updateStatusIfCurrent/, /smoke-critical-consistency\.ps1/, /禁用员工后旧员工token立即401/]],
    ["CACHE_AND_REDIS.md", [/Caffeine L1/, /Redis L2/, /single-flight/, /CacheInvalidationCoordinator/, /ZREVRANGE/, /HotCacheMySqlRedisIT/]],
    ["CACHE_HOT_PATH.md", [/schemaVersion/, /namespaceVersion/, /SET NX PX/, /afterCommit/, /l1-mysql-fallback/, /smoke-cache-performance/]],
    ["API_AND_ERRORS.md", [/Result<T>/, /code = 1/, /40000/, /40100/, /40300/, /40900/, /50000/, /GlobalExceptionHandler/, /POST \/user\/explore-order/, /PUT \/admin\/explore-order\/status/, /GET \/admin\/operation-log\/page/]],
    ["DATABASE_DESIGN.md", [/explore_order/, /request_id/, /idx_order_user_request/, /fk_order_user/, /状态流转/, /删除限制/, /RBAC权限控制/, /STAFF/]],
    ["TEST_REPORT.md", [/Maven tests: 339 run/, /Node frontend\/docs\/run tests: 151 passed/, /Testcontainers MySQL \+ Redis: 34 run/, /GitHub Actions/, /JaCoCo/, /BookingApiFlowTest/, /ExportJobMySqlIT/]],
    ["TESTING.md", [/Maven tests: 339 run/, /Node frontend\/docs\/run tests: 151 passed/, /Testcontainers MySQL \+ Redis: 34 run/, /BookingApiFlowTest/, /ExportJobMySqlIT/, /smoke:export/]],
    ["INTEGRATION_TESTING.md", [/Testcontainers/, /integration-test/, /HotCacheMySqlRedisIT/, /AuthSessionMySqlIT/, /MySQL/, /Redis/, /Docker/, /旧租约/]],
    ["OBSERVABILITY.md", [/X-Request-Id/, /MDC/, /prometheus/, /local\.explorer\.booking/, /local\.explorer\.cache/, /batchId/, /outbox/]],
    ["ORDER_RELIABILITY.md", [/ExploreOrderStatus/, /ShedLock/, /lock_token/, /REQUIRES_NEW/, /ORDER_EXPIRED/, /smoke-order-reliability/]],
    ["AUTH_SESSION_SECURITY.md", [/HttpOnly/, /token family/, /CAS/, /登录保护/, /smoke-auth-session/, /AuthSessionMySqlIT/]],
    ["ASYNC_EXPORT.md", [/PENDING/, /leaseOwner/, /SXSSF/, /SHA-256/, /ExportFileStorage/, /ExportJobMySqlIT/, /100000/]]
  ];

  for (const [filename, patterns] of docs) {
    const content = fs.readFileSync(path.join(repoRoot, "docs", filename), "utf8");
    for (const pattern of patterns) {
      assert.match(content, pattern, `${filename} should include ${pattern}`);
    }
  }
});
