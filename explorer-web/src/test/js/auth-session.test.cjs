const assert = require("node:assert/strict");
const test = require("node:test");
const { pathToFileURL } = require("node:url");
const path = require("node:path");

const moduleUrl = pathToFileURL(
  path.resolve(__dirname, "../../../frontend/src/lib/auth-core.js")
).href;

test("concurrent 401 responses share one refresh and retry each request once", async () => {
  const { createAuthenticatedRequester } = await import(moduleUrl);
  let refreshCalls = 0;
  const attempts = new Map();
  const fetcher = async (path) => {
    attempts.set(path, (attempts.get(path) || 0) + 1);
    if (attempts.get(path) === 1) return { ok: false, status: 401 };
    return { ok: true, status: 200 };
  };
  const requester = createAuthenticatedRequester({
    fetcher,
    refresh: async () => {
      refreshCalls += 1;
      await new Promise((resolve) => setTimeout(resolve, 10));
      return "new-access-token";
    }
  });

  const responses = await Promise.all([requester("/a"), requester("/b")]);

  assert.equal(refreshCalls, 1);
  assert.deepEqual(responses.map((response) => response.status), [200, 200]);
  assert.deepEqual([...attempts.values()], [2, 2]);
});

test("a retried 401 does not recursively refresh", async () => {
  const { createAuthenticatedRequester } = await import(moduleUrl);
  let refreshCalls = 0;
  let requestCalls = 0;
  const requester = createAuthenticatedRequester({
    fetcher: async () => {
      requestCalls += 1;
      return { ok: false, status: 401 };
    },
    refresh: async () => {
      refreshCalls += 1;
      return "new-access-token";
    }
  });

  const response = await requester("/still-unauthorized");

  assert.equal(response.status, 401);
  assert.equal(refreshCalls, 1);
  assert.equal(requestCalls, 2);
});

test("admin and client scopes keep independent refresh flights", async () => {
  const { createRefreshCoordinator } = await import(moduleUrl);
  const coordinator = createRefreshCoordinator();
  let calls = 0;
  const refresh = async () => {
    calls += 1;
    return "token";
  };

  await Promise.all([
    coordinator.run("admin", refresh),
    coordinator.run("admin", refresh),
    coordinator.run("client", refresh)
  ]);

  assert.equal(calls, 2);
});

test("logout cleanup removes session data and legacy localStorage access tokens", async () => {
  const { clearStoredAuthentication } = await import(moduleUrl);
  const makeStorage = (entries) => {
    const values = new Map(Object.entries(entries));
    return {
      getItem: (key) => values.get(key) ?? null,
      removeItem: (key) => values.delete(key),
      has: (key) => values.has(key)
    };
  };
  const sessionStorage = makeStorage({ access: "current", name: "user", unrelated: "keep" });
  const localStorage = makeStorage({ legacy: "old-access", unrelated: "keep" });

  clearStoredAuthentication({
    sessionStorage,
    localStorage,
    sessionKeys: ["access", "name"],
    legacyKeys: ["legacy"]
  });

  assert.equal(sessionStorage.has("access"), false);
  assert.equal(sessionStorage.has("name"), false);
  assert.equal(localStorage.has("legacy"), false);
  assert.equal(sessionStorage.has("unrelated"), true);
  assert.equal(localStorage.has("unrelated"), true);
});
