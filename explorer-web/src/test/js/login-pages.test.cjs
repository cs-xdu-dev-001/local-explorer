const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const test = require("node:test");

const staticRoot = path.resolve(__dirname, "../../main/resources/static");

function readStatic(relativePath) {
  return fs.readFileSync(path.join(staticRoot, relativePath), "utf8");
}

test("console login entry is a React admin-only shell", () => {
  const html = readStatic("console/login.html");

  assert.match(html, /id="root"/);
  assert.match(html, /data-app="local-explorer-admin"/);
  assert.match(html, /data-page="login"/);
  assert.doesNotMatch(html, /id="userForm"/);
  assert.doesNotMatch(html, /\/user\/user\/login/);
  assert.doesNotMatch(html, /localExplorerUserToken/);
});

test("client login entry is a React user-only shell", () => {
  const html = readStatic("client/login.html");

  assert.match(html, /id="root"/);
  assert.match(html, /data-app="local-explorer-client"/);
  assert.match(html, /data-page="login"/);
  assert.doesNotMatch(html, /\/admin\/employee\/login/);
  assert.doesNotMatch(html, /localExplorerAdminToken/);
});

test("client auth redirects stay inside the client area", () => {
  const js = fs.readFileSync(
    path.resolve(__dirname, "../../../frontend/src/lib/auth.js"),
    "utf8"
  );

  assert.match(js, /location\.href = "\.\/login\.html"/);
  assert.doesNotMatch(js, /\.\.\/console\/login\.html/);
});
