const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const test = require("node:test");

const repoRoot = path.resolve(__dirname, "../../../../");

test("database init script carries the operational fields used by the UI", () => {
  const sql = fs.readFileSync(path.join(repoRoot, "docs", "local-explorer-init.sql"), "utf8");

  for (const table of ["explore_item", "explore_package"]) {
    const createStart = sql.indexOf(`CREATE TABLE IF NOT EXISTS ${table}`);
    const createEnd = sql.indexOf(") ENGINE=InnoDB", createStart);
    const createBlock = sql.slice(createStart, createEnd);
    const insertStart = sql.indexOf(`INSERT INTO ${table}`);
    const insertBlock = sql.slice(insertStart).match(/^[\s\S]*?;\r?\n/)?.[0] || "";

    for (const column of ["duration_minutes", "capacity", "booked", "district", "address", "meeting_point", "cancel_policy"]) {
      assert.match(createBlock, new RegExp(`\\b${column}\\b`), `${table} should create ${column}`);
      assert.match(insertBlock, new RegExp(`\\b${column}\\b`), `${table} should insert ${column}`);
      assert.match(insertBlock, new RegExp(`${column} = VALUES\\(${column}\\)`), `${table} should refresh ${column}`);
    }
  }
});

test("database seed provides a repeatable completed order for review smoke", () => {
  const sql = fs.readFileSync(path.join(repoRoot, "docs", "local-explorer-init.sql"), "utf8");
  const fixtureStart = sql.indexOf("'ORD20260423001'");
  const fixtureBlock = sql.slice(Math.max(0, fixtureStart - 240), fixtureStart + 420);

  assert.notEqual(fixtureStart, -1, "review smoke fixture order should exist");
  assert.match(fixtureBlock, /1, 'ORD20260423001', 1, 1002, NULL/);
  assert.match(fixtureBlock, /'首次体验，已完成待评价', 2/);
  assert.match(sql, /ON DUPLICATE KEY UPDATE order_no = VALUES\(order_no\)/);
});

test("database seed provides a repeatable completed package order for review smoke", () => {
  const sql = fs.readFileSync(path.join(repoRoot, "docs", "local-explorer-init.sql"), "utf8");
  const fixtureStart = sql.indexOf("'ORD20260423002'");
  const fixtureBlock = sql.slice(Math.max(0, fixtureStart - 260), fixtureStart + 460);

  assert.notEqual(fixtureStart, -1, "package review smoke fixture order should exist");
  assert.match(fixtureBlock, /1, 'ORD20260423002', 2, NULL, 2004/);
  assert.match(fixtureBlock, /'套餐体验，已完成待评价', 2/);
  assert.doesNotMatch(sql, /\(\d+,\s*\d+,\s*\d+,\s*\d+,\s*\d+,[^;]*ORD20260423002/);
});

test("sample data script refreshes the same operational fields as init data", () => {
  const sql = fs.readFileSync(path.join(repoRoot, "docs", "local-explorer-sample-data.sql"), "utf8");

  for (const table of ["explore_item", "explore_package"]) {
    const insertStart = sql.indexOf(`INSERT INTO ${table}`);
    const insertBlock = sql.slice(insertStart).match(/^[\s\S]*?;\r?\n/)?.[0] || "";

    for (const column of ["duration_minutes", "capacity", "booked", "district", "address", "meeting_point", "cancel_policy"]) {
      assert.match(insertBlock, new RegExp(`\\b${column}\\b`), `${table} sample data should insert ${column}`);
      assert.match(insertBlock, new RegExp(`${column} = VALUES\\(${column}\\)`), `${table} sample data should refresh ${column}`);
    }
  }
});

test("review table supports merchant replies shown by the admin UI", () => {
  const sql = fs.readFileSync(path.join(repoRoot, "docs", "local-explorer-init.sql"), "utf8");
  const createStart = sql.indexOf("CREATE TABLE IF NOT EXISTS review");
  const createEnd = sql.indexOf(") ENGINE=InnoDB", createStart);
  const createBlock = sql.slice(createStart, createEnd);
  const insertStart = sql.indexOf("INSERT INTO review");
  const insertBlock = sql.slice(insertStart).match(/^[\s\S]*?;\r?\n/)?.[0] || "";

  for (const column of ["reply_content", "reply_time"]) {
    assert.match(createBlock, new RegExp(`\\b${column}\\b`), `review should create ${column}`);
    assert.match(insertBlock, new RegExp(`\\b${column}\\b`), `review should insert ${column}`);
    assert.match(insertBlock, new RegExp(`${column} = VALUES\\(${column}\\)`), `review should refresh ${column}`);
  }
});

test("review seed data does not attach duplicate reviews to one order", () => {
  const sql = fs.readFileSync(path.join(repoRoot, "docs", "local-explorer-init.sql"), "utf8");
  const insertStart = sql.indexOf("INSERT INTO review");
  const insertBlock = sql.slice(insertStart).match(/^[\s\S]*?;\r?\n/)?.[0] || "";
  const orderIds = [...insertBlock.matchAll(/\(\d+,\s*\d+,\s*\d+,\s*(\d+|NULL),\s*\d+,/g)]
    .map((match) => match[1])
    .filter((value) => value !== "NULL");

  assert.deepEqual(orderIds, [...new Set(orderIds)]);
});

test("database migration upgrades existing review tables for merchant replies", () => {
  const sql = fs.readFileSync(path.join(repoRoot, "docs", "local-explorer-migrate.sql"), "utf8");

  for (const column of ["reply_content", "reply_time"]) {
    assert.match(sql, new RegExp(`TABLE_NAME = 'review' AND COLUMN_NAME = '${column}'`));
    assert.match(sql, new RegExp(`ALTER TABLE review ADD COLUMN ${column}\\b`));
  }
});

test("runtime settings have durable storage in fresh and upgraded databases", () => {
  for (const fileName of ["local-explorer-init.sql", "local-explorer-migrate.sql"]) {
    const sql = fs.readFileSync(path.join(repoRoot, "docs", fileName), "utf8");

    assert.match(sql, /CREATE TABLE IF NOT EXISTS runtime_setting/);
    assert.match(sql, /setting_key varchar\(64\) NOT NULL/);
    assert.match(sql, /setting_value text NOT NULL/);
    assert.match(sql, /PRIMARY KEY \(setting_key\)/);
  }
});

test("user table supports account status management", () => {
  const sql = fs.readFileSync(path.join(repoRoot, "docs", "local-explorer-init.sql"), "utf8");
  const createStart = sql.indexOf("CREATE TABLE IF NOT EXISTS user");
  const createEnd = sql.indexOf(") ENGINE=InnoDB", createStart);
  const createBlock = sql.slice(createStart, createEnd);
  const insertStart = sql.indexOf("INSERT INTO user");
  const insertBlock = sql.slice(insertStart).match(/^[\s\S]*?;\r?\n/)?.[0] || "";

  assert.match(createBlock, /\bstatus\b/);
  assert.match(createBlock, /status int\(11\) NOT NULL DEFAULT 1/);
  assert.match(insertBlock, /INSERT INTO user \(id, name, phone, password, sex, avatar, status, create_time\)/);
  assert.match(insertBlock, /status = VALUES\(status\)/);
  assert.match(sql, /ALTER TABLE user ADD COLUMN status int\(11\) NOT NULL DEFAULT 1/);
});

test("database seed data uses project assets instead of random placeholder images", () => {
  const sql = fs.readFileSync(path.join(repoRoot, "docs", "local-explorer-init.sql"), "utf8");

  assert.doesNotMatch(sql, new RegExp(String.fromCharCode(112, 105, 99, 115, 117, 109, 46, 112, 104, 111, 116, 111, 115), "i"));
  assert.doesNotMatch(sql, new RegExp(String.fromCharCode(21344, 20301)));
  assert.match(sql, /\/assets\/images\/coffee\.webp/);
  assert.match(sql, /\/assets\/images\/package\.webp/);
  assert.doesNotMatch(sql, /\/assets\/images\/[^'"\s]+\.svg/);
});
