const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const test = require("node:test");

const frontendRoot = path.resolve(__dirname, "../../../frontend");

function read(relativePath) {
  return fs.readFileSync(path.join(frontendRoot, relativePath), "utf8");
}

test("admin forms mirror backend input boundaries", () => {
  const source = read("src/apps/admin/AdminApp.jsx");

  assert.match(source, /const PHONE_PATTERN = "1\[3-9\]\[0-9\]\{9\}"/);
  assert.match(source, /function validateAdminDrawerForm\(type, form, packageItemRows\)/);
  assert.match(source, /maxLength=\{32\} required/);
  assert.match(source, /type="number" min="0\.01" max="99999999\.99" step="0\.01"/);
  assert.match(source, /type="number" min="1" max="10080"/);
  assert.match(source, /type="number" min="1" max="100000"/);
  assert.match(source, /maxLength=\{255\}/);
  assert.match(source, /pattern=\{PHONE_PATTERN\}/);
  assert.match(source, /maxLength=\{500\}/);
  assert.match(source, /const merchantFields = \[/);
  assert.match(source, /required: true/);
  assert.match(source, /maxLength: 255/);
});

test("client forms reject invalid values before backend submission", () => {
  const source = read("src/apps/client/ClientApp.jsx");

  assert.match(source, /const PHONE_PATTERN = "1\[3-9\]\[0-9\]\{9\}"/);
  assert.match(source, /new RegExp\(`\^\$\{PHONE_PATTERN\}\$`\)/);
  assert.match(source, /maxLength=\{11\} pattern=\{PHONE_PATTERN\} required/);
  assert.match(source, /type="password"[\s\S]*maxLength=\{64\} required/);
  assert.match(source, /textarea value=\{form\.content\}[\s\S]*maxLength=\{500\} required/);
  assert.match(source, /type="number" min="1" max="100000"[\s\S]*required/);
  assert.match(source, /value=\{form\.contactName\}[\s\S]*maxLength=\{32\} required/);
  assert.match(source, /value=\{form\.contactPhone\}[\s\S]*maxLength=\{11\} pattern=\{PHONE_PATTERN\} required/);
  assert.match(source, /type="datetime-local"[\s\S]*required/);
  assert.match(source, /textarea value=\{form\.remark\}[\s\S]*maxLength=\{255\}/);
});
