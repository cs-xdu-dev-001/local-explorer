const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const test = require("node:test");

const repoRoot = path.resolve(__dirname, "../../../../");

function read(relativePath) {
  return fs.readFileSync(path.join(repoRoot, relativePath), "utf8");
}

test("deletion services can query historical business and audit references", () => {
  const orderMapper = read("explorer-web/src/main/java/com/localexplorer/mapper/ExploreOrderMapper.java");
  const orderXml = read("explorer-web/src/main/resources/mapper/ExploreOrderMapper.xml");
  const operationLogMapper = read("explorer-web/src/main/java/com/localexplorer/mapper/OperationLogMapper.java");

  assert.match(orderMapper, /long countByItemIds\(@Param\("itemIds"\) List<Long> itemIds\)/);
  assert.match(orderMapper, /long countByPackageIds\(@Param\("packageIds"\) List<Long> packageIds\)/);
  assert.match(orderXml, /<select id="countByItemIds"[\s\S]*?item_id in[\s\S]*?collection="itemIds"/);
  assert.match(orderXml, /<select id="countByPackageIds"[\s\S]*?package_id in[\s\S]*?collection="packageIds"/);
  assert.match(operationLogMapper, /long countByOperatorId\(Long operatorId\)/);
  assert.match(operationLogMapper, /select count\(\*\) from operation_log where operator_id = #\{operatorId\}/i);
});

test("fresh database schema rejects orphaned business records", () => {
  const sql = read("docs/local-explorer-init.sql");
  const migrateSql = read("docs/local-explorer-migrate.sql");

  for (const constraint of [
    "fk_item_category",
    "fk_package_category",
    "fk_item_tag_item",
    "fk_package_item_package",
    "fk_package_item_item",
    "fk_order_user",
    "fk_order_item",
    "fk_order_package",
    "fk_review_user",
    "fk_review_item",
    "fk_review_order",
    "fk_operation_log_employee"
  ]) {
    assert.match(sql, new RegExp(`CONSTRAINT ${constraint}\\b`), `${constraint} should be declared`);
  }

  assert.match(sql, /fk_item_tag_item[\s\S]*?ON DELETE CASCADE/);
  assert.match(sql, /fk_package_item_package[\s\S]*?ON DELETE CASCADE/);
  assert.match(sql, /fk_order_item[\s\S]*?ON DELETE RESTRICT/);
  assert.match(sql, /fk_review_item[\s\S]*?ON DELETE RESTRICT/);
  assert.match(sql, /fk_operation_log_employee[\s\S]*?ON DELETE RESTRICT/);
  assert.match(migrateSql, /CREATE TABLE IF NOT EXISTS operation_log/);
  assert.match(migrateSql, /client_ip varchar\(50\)[^\n]*客户端IP指纹/);
});

test("database seed scripts force an utf8mb4 client connection before Chinese data", () => {
  const initSql = read("docs/local-explorer-init.sql");
  const sampleSql = read("docs/local-explorer-sample-data.sql");

  assert.match(initSql, /^\s*(?:--[^\n]*\n\s*)*SET NAMES utf8mb4;/i);
  assert.match(sampleSql, /^\s*(?:--[^\n]*\n\s*)*SET NAMES utf8mb4;/i);
});

test("database schema supports booking idempotency and employee roles", () => {
  const initSql = read("docs/local-explorer-init.sql");
  const migrateSql = read("docs/local-explorer-migrate.sql");

  assert.match(initSql, /request_id varchar\(64\)/);
  assert.match(initSql, /UNIQUE KEY idx_order_user_request \(user_id, request_id\)/);
  assert.match(initSql, /role varchar\(32\) NOT NULL DEFAULT 'STAFF'/);
  assert.match(initSql, /INSERT INTO employee[\s\S]*role[\s\S]*'ADMIN'/);

  assert.match(migrateSql, /TABLE_NAME = 'explore_order' AND COLUMN_NAME = 'request_id'/);
  assert.match(migrateSql, /ADD UNIQUE KEY idx_order_user_request \(user_id, request_id\)/);
  assert.match(migrateSql, /TABLE_NAME = 'employee' AND COLUMN_NAME = 'role'/);
  assert.match(migrateSql, /UPDATE employee SET role = 'ADMIN' WHERE id = 1/);
});

test("reliability schema persists outbox lease ownership and notification idempotency", () => {
  const initSql = read("docs/local-explorer-init.sql");
  const migrateSql = read("docs/local-explorer-migrate.sql");

  assert.match(initSql, /lock_token varchar\(64\)/);
  assert.match(initSql, /UNIQUE KEY uk_notification_event \(event_id\)/);
  assert.match(initSql, /KEY idx_order_status_expire \(status, expire_at\)/);
  assert.match(migrateSql, /COLUMN_NAME = 'lock_token'/);
  assert.match(migrateSql, /ADD COLUMN lock_token varchar\(64\)/);
});

test("authentication schema stores only refresh and client fingerprints with required indexes", () => {
  const initSql = read("docs/local-explorer-init.sql");
  const migrateSql = read("docs/local-explorer-migrate.sql");

  for (const sql of [initSql, migrateSql]) {
    assert.match(sql, /CREATE TABLE IF NOT EXISTS auth_session/);
    assert.match(sql, /refresh_token_hash char\(64\) NOT NULL/);
    assert.match(sql, /UNIQUE KEY uk_auth_refresh_hash \(refresh_token_hash\)/);
    assert.match(sql, /KEY idx_auth_principal \(principal_type, principal_id, status\)/);
    assert.match(sql, /KEY idx_auth_expiry \(status, expires_at\)/);
    assert.match(sql, /KEY idx_auth_family \(token_family_id, status\)/);
    assert.match(sql, /CREATE TABLE IF NOT EXISTS login_guard/);
    assert.match(sql, /UNIQUE KEY uk_login_guard_tuple \(principal_type, account_hash, ip_hash\)/);
    assert.doesNotMatch(sql, /refresh_token\s+varchar/i);
    assert.doesNotMatch(sql, /user_agent\s+/i);
  }
});

test("admin order and review page queries support backend operational filters", () => {
  const orderDto = read("explorer-model/src/main/java/com/localexplorer/dto/ExploreOrderPageQueryDTO.java");
  const orderXml = read("explorer-web/src/main/resources/mapper/ExploreOrderMapper.xml");
  const reviewDto = read("explorer-model/src/main/java/com/localexplorer/dto/ReviewPageQueryDTO.java");
  const reviewXml = read("explorer-web/src/main/resources/mapper/ReviewMapper.xml");

  assert.match(orderDto, /private String keyword;/);
  assert.match(orderXml, /<if test="keyword != null and keyword != ''">[\s\S]*o\.order_no like concat\('%',#\{keyword\},'%'\)[\s\S]*o\.item_name like concat\('%',#\{keyword\},'%'\)[\s\S]*u\.name like concat\('%',#\{keyword\},'%'\)[\s\S]*o\.contact_name like concat\('%',#\{keyword\},'%'\)[\s\S]*o\.contact_phone like concat\('%',#\{keyword\},'%'\)/);
  assert.match(orderXml, /<if test="status != null">[\s\S]*and o\.status = #\{status\}/);

  assert.match(reviewDto, /private String keyword;/);
  assert.match(reviewDto, /private Integer rating;/);
  assert.match(reviewDto, /private String replyState;/);
  assert.match(reviewXml, /<if test="keyword != null and keyword != ''">[\s\S]*i\.name like concat\('%',#\{keyword\},'%'\)[\s\S]*u\.name like concat\('%',#\{keyword\},'%'\)[\s\S]*r\.content like concat\('%',#\{keyword\},'%'\)[\s\S]*r\.reply_content like concat\('%',#\{keyword\},'%'\)/);
  assert.match(reviewXml, /<if test="rating != null">[\s\S]*and r\.rating = #\{rating\}/);
  assert.match(reviewXml, /<if test="replyState != null and replyState == 'replied'">[\s\S]*r\.reply_content is not null[\s\S]*r\.reply_content != ''/);
  assert.match(reviewXml, /<if test="replyState != null and replyState == 'unreplied'">[\s\S]*r\.reply_content is null[\s\S]*r\.reply_content = ''/);
});

test("review page query displays and searches the reviewed order name first", () => {
  const reviewXml = read("explorer-web/src/main/resources/mapper/ReviewMapper.xml");

  assert.match(reviewXml, /left join explore_order o on r\.order_id = o\.id/);
  assert.match(reviewXml, /coalesce\(o\.item_name, i\.name\) as itemName/);
  assert.match(reviewXml, /o\.item_name like concat\('%',#\{keyword\},'%'\)/);
});

test("user package item API exposes item ids for package review read-back", () => {
  const packageItemVo = read("explorer-model/src/main/java/com/localexplorer/vo/PackageItemVO.java");
  const packageMapper = read("explorer-web/src/main/java/com/localexplorer/mapper/ExplorePackageMapper.java");
  const packageItemMapper = read("explorer-web/src/main/java/com/localexplorer/mapper/ExplorePackageItemMapper.java");

  assert.match(packageItemVo, /private Long itemId;/);
  assert.match(packageMapper, /pi\.item_id as itemId/);
  assert.match(packageItemMapper, /pi\.item_id as itemId/);
});
