const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const test = require("node:test");

const repoRoot = path.resolve(__dirname, "../../../../");
const controllerDir = path.join(
  repoRoot,
  "explorer-web",
  "src",
  "main",
  "java",
  "com",
  "localexplorer",
  "controller",
  "admin"
);
const operationLogMapperXml = fs.readFileSync(
  path.join(repoRoot, "explorer-web", "src", "main", "resources", "mapper", "OperationLogMapper.xml"),
  "utf8"
);

function mutatingHandlers() {
  const handlers = [];
  for (const fileName of fs.readdirSync(controllerDir).filter((name) => name.endsWith(".java"))) {
    const source = fs.readFileSync(path.join(controllerDir, fileName), "utf8");
    const methodPattern = /((?:\s*@[^\r\n]+\r?\n)+)\s*public\s+[^{;]+?\s+(\w+)\s*\(/g;

    for (const match of source.matchAll(methodPattern)) {
      const annotations = match[1];
      if (!/@(?:PostMapping|PutMapping|DeleteMapping)/.test(annotations)) continue;
      handlers.push({
        key: `${fileName}#${match[2]}`,
        logged: /@OperationLog\("[^"]+"\)/.test(annotations),
        description: annotations.match(/@OperationLog\("([^"]+)"\)/)?.[1] || ""
      });
    }
  }
  return handlers;
}

test("all admin state-changing endpoints are audited except login and logout", () => {
  const allowedWithoutAudit = new Set([
    "EmployeeController.java#login",
    "EmployeeController.java#refresh",
    "EmployeeController.java#logout",
    "EmployeeController.java#logoutAll"
  ]);
  const missing = mutatingHandlers()
    .filter((handler) => !handler.logged && !allowedWithoutAudit.has(handler.key))
    .map((handler) => handler.key);

  assert.deepEqual(missing, []);
});

test("operation log descriptions use consistent Chinese product copy", () => {
  const inconsistent = mutatingHandlers()
    .filter((handler) => handler.logged && !/[\u3400-\u9fff]/.test(handler.description))
    .map((handler) => `${handler.key}: ${handler.description}`);

  assert.deepEqual(inconsistent, []);
});

test("operation log page query supports practical audit filters", () => {
  assert.match(operationLogMapperXml, /keyword\s*!=\s*null[\s\S]*ol\.description\s+like/i);
  assert.match(operationLogMapperXml, /keyword\s*!=\s*null[\s\S]*e\.name\s+like/i);
  assert.match(operationLogMapperXml, /keyword\s*!=\s*null[\s\S]*ol\.request_uri\s+like/i);
  assert.match(operationLogMapperXml, /requestMethod\s*!=\s*null[\s\S]*ol\.request_method\s*=\s*#\{requestMethod\}/i);
});
