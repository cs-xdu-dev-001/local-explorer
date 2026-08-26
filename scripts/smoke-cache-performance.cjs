const fs = require("node:fs");
const path = require("node:path");

const config = {
  baseUrl: process.argv.find((arg) => arg.startsWith("--base="))?.slice("--base=".length)
    || process.env.BACKEND_BASE_URL
    || "http://localhost:8080",
  adminUsername: process.env.ADMIN_USERNAME || "admin",
  adminPassword: process.env.ADMIN_PASSWORD || "123456",
  requests: Math.max(200, Number(process.env.CACHE_SMOKE_REQUESTS || 200)),
  concurrency: Math.max(1, Number(process.env.CACHE_SMOKE_CONCURRENCY || 25)),
  reportPath: process.env.CACHE_SMOKE_REPORT
    || path.resolve(__dirname, "../explorer-web/target/cache-performance-report.json")
};

async function request(apiPath, options = {}) {
  const headers = { ...(options.headers || {}) };
  if (options.body !== undefined) headers["Content-Type"] = "application/json";
  if (options.adminToken) headers.token = options.adminToken;
  const response = await fetch(`${config.baseUrl.replace(/\/$/, "")}${apiPath}`, {
    method: options.method || "GET",
    headers,
    body: options.body === undefined ? undefined : JSON.stringify(options.body)
  });
  const payload = await response.json().catch(() => ({}));
  if (!response.ok || payload.code !== 1) {
    throw new Error(`${options.method || "GET"} ${apiPath} failed: HTTP ${response.status}, ${payload.msg || "empty response"}`);
  }
  return payload.data;
}

async function loginAdmin() {
  const admin = await request("/admin/employee/login", {
    method: "POST",
    body: { username: config.adminUsername, password: config.adminPassword }
  });
  if (!admin?.token) throw new Error("Admin login did not return token.");
  return admin.token;
}

function percentile(values, ratio) {
  const sorted = [...values].sort((left, right) => left - right);
  return sorted[Math.max(0, Math.ceil(sorted.length * ratio) - 1)];
}

function metricDelta(after, before, name) {
  return Number(after?.cache?.[name] || 0) - Number(before?.cache?.[name] || 0);
}

async function runGroup(label, before, adminToken) {
  const durations = new Array(config.requests);
  let cursor = 0;
  const started = process.hrtime.bigint();
  const workers = Array.from({ length: Math.min(config.concurrency, config.requests) }, async () => {
    while (true) {
      const index = cursor++;
      if (index >= config.requests) return;
      const requestStarted = process.hrtime.bigint();
      const rows = await request("/user/category/list?type=1");
      if (!Array.isArray(rows)) throw new Error("Category endpoint did not return a list.");
      durations[index] = Number(process.hrtime.bigint() - requestStarted) / 1e6;
    }
  });
  await Promise.all(workers);
  const elapsedSeconds = Number(process.hrtime.bigint() - started) / 1e9;
  const after = await request("/admin/cache/stats", { adminToken });
  const l1Hits = metricDelta(after, before, "l1Hits");
  const l2Hits = metricDelta(after, before, "l2Hits");
  const databaseLoads = metricDelta(after, before, "databaseLoads");
  const measuredReads = l1Hits + l2Hits + databaseLoads;
  return {
    label,
    requests: config.requests,
    concurrency: config.concurrency,
    p50Ms: Number(percentile(durations, 0.50).toFixed(2)),
    p95Ms: Number(percentile(durations, 0.95).toFixed(2)),
    p99Ms: Number(percentile(durations, 0.99).toFixed(2)),
    throughputRps: Number((config.requests / elapsedSeconds).toFixed(2)),
    l1Hits,
    l2Hits,
    databaseLoads,
    hitRate: measuredReads ? Number(((l1Hits + l2Hits) / measuredReads).toFixed(4)) : 0,
    after
  };
}

async function waitForWarmup(adminToken, previousCompletedAt) {
  const deadline = Date.now() + 15000;
  while (Date.now() < deadline) {
    const stats = await request("/admin/cache/stats", { adminToken });
    if (!stats.warmupRunning && Number(stats.lastWarmupAt || 0) > Number(previousCompletedAt || 0)) return stats;
    await new Promise((resolve) => setTimeout(resolve, 100));
  }
  throw new Error("Cache warmup did not complete within 15 seconds.");
}

function assertEvidence(cold, hot, prewarmed) {
  if (cold.databaseLoads !== 1) {
    throw new Error(`Cold ${cold.requests}-request group expected one database load, got ${cold.databaseLoads}.`);
  }
  if (hot.databaseLoads !== 0 || prewarmed.databaseLoads !== 0) {
    throw new Error(`Hot/prewarmed groups must not load MySQL, got ${hot.databaseLoads}/${prewarmed.databaseLoads}.`);
  }
  if (hot.hitRate < 0.99 || prewarmed.hitRate < 0.99) {
    throw new Error(`Hot/prewarmed hit rate is below 99%: ${hot.hitRate}/${prewarmed.hitRate}.`);
  }
}

async function main() {
  const adminToken = await loginAdmin();
  await request("/admin/cache/invalidate/all", { method: "POST", adminToken });
  const beforeCold = await request("/admin/cache/stats", { adminToken });
  const cold = await runGroup("cold-burst", beforeCold, adminToken);
  const hot = await runGroup("hot", cold.after, adminToken);

  const beforeWarmup = await request("/admin/cache/stats", { adminToken });
  await request("/admin/cache/invalidate/all", { method: "POST", adminToken });
  await request("/admin/cache/warmup", { method: "POST", adminToken });
  const afterWarmup = await waitForWarmup(adminToken, beforeWarmup.lastWarmupAt);
  const prewarmed = await runGroup("prewarmed", afterWarmup, adminToken);
  assertEvidence(cold, hot, prewarmed);

  const report = {
    generatedAt: new Date().toISOString(),
    baseUrl: config.baseUrl,
    endpoint: "/user/category/list?type=1",
    groups: [cold, hot, prewarmed].map(({ after, ...group }) => group)
  };
  fs.mkdirSync(path.dirname(config.reportPath), { recursive: true });
  fs.writeFileSync(config.reportPath, `${JSON.stringify(report, null, 2)}\n`, "utf8");
  console.log(JSON.stringify(report, null, 2));
}

main().catch((error) => {
  console.error(error.stack || error.message);
  process.exitCode = 1;
});
