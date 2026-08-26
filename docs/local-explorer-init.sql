-- ============================================================
-- Local Explorer 初始化脚本
-- ============================================================
-- 默认管理员：admin / 123456（MD5 哈希）
-- 演示用户：自动插入 4 个用户账号，默认密码均为 123456
-- ============================================================

SET NAMES utf8mb4;

CREATE DATABASE IF NOT EXISTS local_explorer
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_general_ci;

USE local_explorer;

-- 员工表
CREATE TABLE IF NOT EXISTS employee (
  id bigint(20) NOT NULL AUTO_INCREMENT,
  name varchar(32) NOT NULL,
  username varchar(32) NOT NULL,
  password varchar(64) NOT NULL,
  phone varchar(11) DEFAULT NULL,
  sex varchar(2) DEFAULT NULL,
  id_number varchar(18) DEFAULT NULL,
  status int(11) NOT NULL DEFAULT 1,
  role varchar(32) NOT NULL DEFAULT 'STAFF',
  create_time datetime DEFAULT NULL,
  update_time datetime DEFAULT NULL,
  create_user bigint(20) DEFAULT NULL,
  update_user bigint(20) DEFAULT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY idx_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 内容分类表
CREATE TABLE IF NOT EXISTS category (
  id bigint(20) NOT NULL AUTO_INCREMENT,
  type int(11) DEFAULT NULL COMMENT '1=特色项目分类 2=套餐分类',
  name varchar(32) NOT NULL,
  sort int(11) NOT NULL DEFAULT 0,
  status int(11) NOT NULL DEFAULT 1,
  create_time datetime DEFAULT NULL,
  update_time datetime DEFAULT NULL,
  create_user bigint(20) DEFAULT NULL,
  update_user bigint(20) DEFAULT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY idx_category_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 特色项目表
CREATE TABLE IF NOT EXISTS explore_item (
  id bigint(20) NOT NULL AUTO_INCREMENT,
  name varchar(32) NOT NULL,
  category_id bigint(20) NOT NULL,
  price decimal(10,2) DEFAULT NULL,
  image varchar(255) DEFAULT NULL,
  description varchar(255) DEFAULT NULL,
  duration_minutes int(11) DEFAULT NULL,
  capacity int(11) DEFAULT 0,
  booked int(11) DEFAULT 0,
  district varchar(64) DEFAULT NULL,
  address varchar(255) DEFAULT NULL,
  meeting_point varchar(255) DEFAULT NULL,
  cancel_policy varchar(255) DEFAULT NULL,
  status int(11) NOT NULL DEFAULT 1,
  create_time datetime DEFAULT NULL,
  update_time datetime DEFAULT NULL,
  create_user bigint(20) DEFAULT NULL,
  update_user bigint(20) DEFAULT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY idx_item_name (name),
  KEY idx_item_category_id (category_id),
  CONSTRAINT fk_item_category FOREIGN KEY (category_id) REFERENCES category (id) ON UPDATE CASCADE ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 特色项目标签表
CREATE TABLE IF NOT EXISTS explore_item_tag (
  id bigint(20) NOT NULL AUTO_INCREMENT,
  item_id bigint(20) NOT NULL,
  name varchar(32) DEFAULT NULL,
  value varchar(255) DEFAULT NULL,
  PRIMARY KEY (id),
  KEY idx_item_tag_item_id (item_id),
  CONSTRAINT fk_item_tag_item FOREIGN KEY (item_id) REFERENCES explore_item (id) ON UPDATE CASCADE ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 探店套餐表
CREATE TABLE IF NOT EXISTS explore_package (
  id bigint(20) NOT NULL AUTO_INCREMENT,
  category_id bigint(20) NOT NULL,
  name varchar(32) NOT NULL,
  price decimal(10,2) NOT NULL,
  status int(11) DEFAULT 1,
  description varchar(255) DEFAULT NULL,
  image varchar(255) DEFAULT NULL,
  duration_minutes int(11) DEFAULT NULL,
  capacity int(11) DEFAULT 0,
  booked int(11) DEFAULT 0,
  district varchar(64) DEFAULT NULL,
  address varchar(255) DEFAULT NULL,
  meeting_point varchar(255) DEFAULT NULL,
  cancel_policy varchar(255) DEFAULT NULL,
  create_time datetime DEFAULT NULL,
  update_time datetime DEFAULT NULL,
  create_user bigint(20) DEFAULT NULL,
  update_user bigint(20) DEFAULT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY idx_package_name (name),
  KEY idx_package_category_id (category_id),
  CONSTRAINT fk_package_category FOREIGN KEY (category_id) REFERENCES category (id) ON UPDATE CASCADE ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 套餐-特色项目关联表
CREATE TABLE IF NOT EXISTS explore_package_item (
  id bigint(20) NOT NULL AUTO_INCREMENT,
  package_id bigint(20) NOT NULL,
  item_id bigint(20) NOT NULL,
  name varchar(32) DEFAULT NULL,
  price decimal(10,2) DEFAULT NULL,
  copies int(11) DEFAULT 1,
  PRIMARY KEY (id),
  KEY idx_package_item_package_id (package_id),
  KEY idx_package_item_item_id (item_id),
  CONSTRAINT fk_package_item_package FOREIGN KEY (package_id) REFERENCES explore_package (id) ON UPDATE CASCADE ON DELETE CASCADE,
  CONSTRAINT fk_package_item_item FOREIGN KEY (item_id) REFERENCES explore_item (id) ON UPDATE CASCADE ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 用户表
CREATE TABLE IF NOT EXISTS user (
  id bigint(20) NOT NULL AUTO_INCREMENT,
  name varchar(32) DEFAULT NULL,
  phone varchar(11) DEFAULT NULL,
  password varchar(64) NOT NULL,
  sex varchar(2) DEFAULT NULL,
  id_number varchar(18) DEFAULT NULL,
  avatar varchar(500) DEFAULT NULL,
  status int(11) NOT NULL DEFAULT 1,
  create_time datetime DEFAULT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY idx_user_phone (phone)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 服务端认证会话：Refresh Token仅保存SHA-256摘要，不保存明文Token、完整IP或User-Agent。
CREATE TABLE IF NOT EXISTS auth_session (
  id bigint(20) NOT NULL AUTO_INCREMENT,
  session_id varchar(64) NOT NULL,
  token_family_id varchar(64) NOT NULL,
  principal_type varchar(16) NOT NULL COMMENT 'EMPLOYEE/USER',
  principal_id bigint(20) NOT NULL,
  refresh_token_hash char(64) NOT NULL,
  status varchar(16) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/ROTATED/REVOKED/EXPIRED',
  expires_at datetime(3) NOT NULL,
  last_used_at datetime(3) DEFAULT NULL,
  revoked_at datetime(3) DEFAULT NULL,
  revoke_reason varchar(64) DEFAULT NULL,
  ip_hash char(64) DEFAULT NULL,
  device_summary varchar(120) DEFAULT NULL,
  create_time datetime(3) NOT NULL,
  update_time datetime(3) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_auth_session_id (session_id),
  UNIQUE KEY uk_auth_refresh_hash (refresh_token_hash),
  KEY idx_auth_principal (principal_type, principal_id, status),
  KEY idx_auth_expiry (status, expires_at),
  KEY idx_auth_family (token_family_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 登录失败窗口按身份类型、账号摘要和IP摘要唯一，避免账号枚举及完整标识落库。
CREATE TABLE IF NOT EXISTS login_guard (
  id bigint(20) NOT NULL AUTO_INCREMENT,
  principal_type varchar(16) NOT NULL,
  account_hash char(64) NOT NULL,
  ip_hash char(64) NOT NULL,
  account_hint varchar(32) DEFAULT NULL,
  failed_count int(11) NOT NULL DEFAULT 0,
  window_started_at datetime NOT NULL,
  locked_until datetime DEFAULT NULL,
  last_failed_at datetime DEFAULT NULL,
  create_time datetime NOT NULL,
  update_time datetime NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_login_guard_tuple (principal_type, account_hash, ip_hash),
  KEY idx_login_guard_locked (locked_until, principal_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

SET @user_status_exists := (
  SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'user' AND column_name = 'status'
);
SET @user_status_sql := IF(
  @user_status_exists = 0,
  'ALTER TABLE user ADD COLUMN status int(11) NOT NULL DEFAULT 1 AFTER avatar',
  'SELECT 1'
);
PREPARE user_status_stmt FROM @user_status_sql;
EXECUTE user_status_stmt;
DEALLOCATE PREPARE user_status_stmt;

-- 可持久化运行配置
CREATE TABLE IF NOT EXISTS runtime_setting (
  setting_key varchar(64) NOT NULL,
  setting_value text NOT NULL,
  update_time datetime NOT NULL,
  PRIMARY KEY (setting_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================================
-- 初始数据
-- ============================================================

-- 默认管理员密码: 123456 (MD5: e10adc3949ba59abbe56e057f20f883e)
INSERT INTO employee (id, name, username, password, phone, sex, id_number, status, role, create_time, update_time, create_user, update_user)
VALUES (1, '管理员', 'admin', 'e10adc3949ba59abbe56e057f20f883e', '18854051167', '1', '610000199901010000', 1, 'ADMIN', NOW(), NOW(), 1, 1)
ON DUPLICATE KEY UPDATE
  name = VALUES(name),
  password = VALUES(password),
  status = VALUES(status),
  role = VALUES(role),
  update_time = NOW();

-- 默认用户密码: 123456 (MD5: e10adc3949ba59abbe56e057f20f883e)
INSERT INTO user (id, name, phone, password, sex, avatar, status, create_time)
VALUES
  (1, '张小明', '13800001111', 'e10adc3949ba59abbe56e057f20f883e', '1', 'https://api.dicebear.com/7.x/avataaars/svg?seed=xiaoming', 1, NOW()),
  (2, '李晓红', '13800002222', 'e10adc3949ba59abbe56e057f20f883e', '0', 'https://api.dicebear.com/7.x/avataaars/svg?seed=xiaohong', 1, NOW()),
  (3, '王大力', '13800003333', 'e10adc3949ba59abbe56e057f20f883e', '1', 'https://api.dicebear.com/7.x/avataaars/svg?seed=dali', 1, NOW()),
  (4, '赵小雨', '13800004444', 'e10adc3949ba59abbe56e057f20f883e', '0', 'https://api.dicebear.com/7.x/avataaars/svg?seed=xiaoyu', 1, NOW())
ON DUPLICATE KEY UPDATE
  name = VALUES(name),
  password = VALUES(password),
  avatar = VALUES(avatar),
  status = VALUES(status);

-- 内容分类
INSERT INTO category (id, type, name, sort, status, create_time, update_time, create_user, update_user)
VALUES
  (101, 1, '周末放松', 10, 1, NOW(), NOW(), 1, 1),
  (102, 1, '运动体验', 20, 1, NOW(), NOW(), 1, 1),
  (103, 1, '拍照打卡', 30, 1, NOW(), NOW(), 1, 1),
  (104, 1, '美食探店', 40, 1, NOW(), NOW(), 1, 1),
  (105, 1, '亲子互动', 50, 1, NOW(), NOW(), 1, 1),
  (201, 2, '双人套餐', 10, 1, NOW(), NOW(), 1, 1),
  (202, 2, '朋友聚会', 20, 1, NOW(), NOW(), 1, 1),
  (203, 2, '周末畅玩', 30, 1, NOW(), NOW(), 1, 1)
ON DUPLICATE KEY UPDATE
  name = VALUES(name),
  type = VALUES(type),
  sort = VALUES(sort),
  status = VALUES(status),
  update_time = NOW();

-- 特色项目
INSERT INTO explore_item (
  id, name, category_id, price, image, description,
  duration_minutes, capacity, booked, district, address, meeting_point, cancel_policy,
  status, create_time, update_time, create_user, update_user
)
VALUES
  (1001, '城市咖啡体验', 101, 39.00, '/assets/images/coffee.webp', '精品咖啡品鉴与店内拍照打卡', 90, 24, 8, '高新路', '高新区科技路 88 号', '一层咖啡吧前台', '开始前 2 小时可免费取消', 1, NOW(), NOW(), 1, 1),
  (1002, '室内攀岩入门', 102, 88.00, '/assets/images/workshop.webp', '含基础教学与安全装备租用', 120, 16, 5, '曲江', '曲江新区芙蓉南路 18 号', '攀岩馆装备区', '开始前 4 小时可免费取消', 1, NOW(), NOW(), 1, 1),
  (1003, '复古写真体验', 103, 168.00, '/assets/images/citywalk.webp', '含妆造建议、布景拍摄与精修照片', 180, 10, 3, '小寨', '雁塔区长安中路 99 号', '三层摄影棚前台', '开始前 24 小时可免费取消', 1, NOW(), NOW(), 1, 1),
  (1004, '桌游剧本体验', 101, 59.00, '/assets/images/boardgame.webp', '适合朋友聚会的轻推理体验', 150, 30, 12, '钟楼', '碑林区南大街 36 号', '二层桌游区', '开始前 2 小时可免费取消', 1, NOW(), NOW(), 1, 1),
  (1005, '手工陶艺制作', 101, 128.00, '/assets/images/workshop.webp', '从拉坯到上釉，全程老师指导', 120, 18, 6, '大雁塔', '雁塔区雁南一路 12 号', '陶艺教室入口', '开始前 12 小时可免费取消', 1, NOW(), NOW(), 1, 1),
  (1006, '密室逃脱挑战', 102, 78.00, '/assets/images/boardgame.webp', '沉浸式剧情密室，适合 2-6 人', 60, 20, 9, '赛格', '雁塔区长安南路 123 号', '密室大厅前台', '开始前 4 小时可免费取消', 1, NOW(), NOW(), 1, 1),
  (1007, '日式料理体验', 104, 158.00, '/assets/images/bookstore.webp', '学习制作寿司和天妇罗', 150, 12, 4, '曲江', '曲江新区雁展路 8 号', '料理教室门口', '开始前 24 小时可免费取消', 1, NOW(), NOW(), 1, 1),
  (1008, '亲子烘焙课堂', 105, 98.00, '/assets/images/package.webp', '家长和孩子一起做蛋糕饼干', 120, 20, 7, '浐灞', '浐灞生态区广运潭大道 66 号', '烘焙教室前台', '开始前 12 小时可免费取消', 1, NOW(), NOW(), 1, 1),
  (1009, '油画零基础课', 103, 118.00, '/assets/images/citywalk.webp', '3 小时完成一幅作品带回家', 180, 14, 5, '高新路', '高新区唐延路 35 号', '画室接待区', '开始前 12 小时可免费取消', 1, NOW(), NOW(), 1, 1),
  (1010, '城市骑行探索', 102, 45.00, '/assets/images/citywalk.webp', '专业领骑，探索城市隐藏路线', 120, 25, 11, '城墙', '碑林区环城南路 2 号', '永宁门外集合点', '开始前 4 小时可免费取消', 1, NOW(), NOW(), 1, 1),
  (1011, '花艺沙龙', 101, 108.00, '/assets/images/workshop.webp', '学习插花技巧，作品可带走', 90, 18, 4, '大明宫', '未央区太华南路 251 号', '花艺工作室门口', '开始前 12 小时可免费取消', 1, NOW(), NOW(), 1, 1),
  (1012, '精酿啤酒品鉴', 104, 68.00, '/assets/images/coffee.webp', '品鉴 6 款精酿，了解酿造工艺', 90, 28, 10, '老城根', '莲湖区星火路 22 号', '精酿吧吧台', '开始前 2 小时可免费取消', 1, NOW(), NOW(), 1, 1)
ON DUPLICATE KEY UPDATE
  category_id = VALUES(category_id),
  price = VALUES(price),
  image = VALUES(image),
  description = VALUES(description),
  duration_minutes = VALUES(duration_minutes),
  capacity = VALUES(capacity),
  booked = VALUES(booked),
  district = VALUES(district),
  address = VALUES(address),
  meeting_point = VALUES(meeting_point),
  cancel_policy = VALUES(cancel_policy),
  status = VALUES(status),
  update_time = NOW();

-- 项目标签
DELETE FROM explore_item_tag WHERE item_id IN (1001,1002,1003,1004,1005,1006,1007,1008,1009,1010,1011,1012);
INSERT INTO explore_item_tag (item_id, name, value) VALUES
  (1001, '适合人群', '情侣 / 闺蜜'),
  (1001, '时长', '约 1.5 小时'),
  (1002, '适合人群', '运动爱好者'),
  (1002, '时长', '约 2 小时'),
  (1003, '适合人群', '女生 / 情侣'),
  (1003, '时长', '约 3 小时'),
  (1004, '适合人群', '朋友聚会'),
  (1004, '时长', '约 2-3 小时'),
  (1005, '适合人群', '情侣 / 亲子'),
  (1005, '时长', '约 2 小时'),
  (1006, '适合人群', '朋友 / 同事'),
  (1006, '时长', '约 1 小时'),
  (1007, '适合人群', '美食爱好者'),
  (1007, '时长', '约 2.5 小时'),
  (1008, '适合人群', '亲子家庭'),
  (1008, '时长', '约 2 小时'),
  (1009, '适合人群', '零基础体验'),
  (1009, '时长', '约 3 小时'),
  (1010, '适合人群', '运动爱好者'),
  (1010, '时长', '约 2 小时'),
  (1011, '适合人群', '闺蜜 / 妈妈'),
  (1011, '时长', '约 1.5 小时'),
  (1012, '适合人群', '朋友聚会'),
  (1012, '时长', '约 1.5 小时');

-- 探店套餐
INSERT INTO explore_package (
  id, category_id, name, price, status, description, image,
  duration_minutes, capacity, booked, district, address, meeting_point, cancel_policy,
  create_time, update_time, create_user, update_user
)
VALUES
  (2001, 201, '双人咖啡探店套餐', 76.00, 1, '两杯精品咖啡加甜品，适合周末探店', '/assets/images/coffee.webp', 120, 16, 6, '高新路', '高新区科技路 88 号', '一层咖啡吧前台', '开始前 2 小时可免费取消', NOW(), NOW(), 1, 1),
  (2002, 202, '四人桌游聚会套餐', 198.00, 1, '四人桌游包场与饮品组合', '/assets/images/boardgame.webp', 180, 20, 9, '钟楼', '碑林区南大街 36 号', '二层桌游区', '开始前 4 小时可免费取消', NOW(), NOW(), 1, 1),
  (2003, 201, '情侣陶艺套餐', 228.00, 1, '双人陶艺体验加下午茶', '/assets/images/workshop.webp', 150, 12, 5, '大雁塔', '雁塔区雁南一路 12 号', '陶艺教室入口', '开始前 12 小时可免费取消', NOW(), NOW(), 1, 1),
  (2004, 203, '周末运动畅玩套餐', 158.00, 1, '攀岩加骑行，畅玩一整天', '/assets/images/citywalk.webp', 240, 14, 4, '曲江', '曲江新区芙蓉南路 18 号', '攀岩馆装备区', '开始前 4 小时可免费取消', NOW(), NOW(), 1, 1),
  (2005, 202, '美食探店三人行', 288.00, 1, '日料体验加精酿品鉴', '/assets/images/package.webp', 210, 12, 3, '曲江', '曲江新区雁展路 8 号', '料理教室门口', '开始前 24 小时可免费取消', NOW(), NOW(), 1, 1),
  (2006, 203, '亲子周末套餐', 178.00, 1, '烘焙课堂加花艺沙龙', '/assets/images/package.webp', 180, 16, 6, '浐灞', '浐灞生态区广运潭大道 66 号', '烘焙教室前台', '开始前 12 小时可免费取消', NOW(), NOW(), 1, 1)
ON DUPLICATE KEY UPDATE
  category_id = VALUES(category_id),
  price = VALUES(price),
  status = VALUES(status),
  description = VALUES(description),
  image = VALUES(image),
  duration_minutes = VALUES(duration_minutes),
  capacity = VALUES(capacity),
  booked = VALUES(booked),
  district = VALUES(district),
  address = VALUES(address),
  meeting_point = VALUES(meeting_point),
  cancel_policy = VALUES(cancel_policy),
  update_time = NOW();

-- 套餐关联项目
DELETE FROM explore_package_item WHERE package_id IN (2001,2002,2003,2004,2005,2006);
INSERT INTO explore_package_item (package_id, item_id, name, price, copies) VALUES
  (2001, 1001, '城市咖啡体验', 39.00, 2),
  (2002, 1004, '桌游剧本体验', 59.00, 4),
  (2003, 1005, '手工陶艺制作', 128.00, 2),
  (2004, 1002, '室内攀岩入门', 88.00, 1),
  (2004, 1010, '城市骑行探索', 45.00, 1),
  (2005, 1007, '日式料理体验', 158.00, 1),
  (2005, 1012, '精酿啤酒品鉴', 68.00, 2),
  (2006, 1008, '亲子烘焙课堂', 98.00, 1),
  (2006, 1011, '花艺沙龙', 108.00, 1);

-- ============================================================
-- 预约/订单表
-- ============================================================
CREATE TABLE IF NOT EXISTS explore_order (
  id bigint(20) NOT NULL AUTO_INCREMENT,
  user_id bigint(20) NOT NULL COMMENT '预约用户',
  order_no varchar(32) NOT NULL COMMENT '预约编号',
  order_type int(11) NOT NULL COMMENT '1=特色项目 2=探店套餐',
  item_id bigint(20) DEFAULT NULL COMMENT '特色项目ID（order_type=1时）',
  package_id bigint(20) DEFAULT NULL COMMENT '探店套餐ID（order_type=2时）',
  item_name varchar(64) DEFAULT NULL COMMENT '项目/套餐名称（冗余）',
  amount decimal(10,2) NOT NULL COMMENT '预约金额',
  people_count int(11) DEFAULT 1 COMMENT '预约人数',
  contact_name varchar(32) DEFAULT NULL COMMENT '联系人',
  contact_phone varchar(11) DEFAULT NULL COMMENT '联系电话',
  reserve_time datetime DEFAULT NULL COMMENT '预约时间',
  request_id varchar(64) DEFAULT NULL COMMENT '客户端幂等请求ID',
  expire_at datetime DEFAULT NULL COMMENT '待确认预约自动关闭时间',
  cancel_type varchar(16) DEFAULT NULL COMMENT 'USER/ADMIN/TIMEOUT',
  cancel_reason varchar(255) DEFAULT NULL COMMENT '取消或超时原因',
  remark varchar(255) DEFAULT NULL COMMENT '备注',
  status int(11) NOT NULL DEFAULT 0 COMMENT '0=待确认 1=已确认 2=已完成 3=已取消 4=超时取消',
  create_time datetime DEFAULT NULL,
  update_time datetime DEFAULT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY idx_order_no (order_no),
  UNIQUE KEY idx_order_user_request (user_id, request_id),
  KEY idx_user_id (user_id),
  KEY idx_item_id (item_id),
  KEY idx_package_id (package_id),
  KEY idx_status (status),
  KEY idx_order_status_expire (status, expire_at),
  CONSTRAINT fk_order_user FOREIGN KEY (user_id) REFERENCES user (id) ON UPDATE CASCADE ON DELETE RESTRICT,
  CONSTRAINT fk_order_item FOREIGN KEY (item_id) REFERENCES explore_item (id) ON UPDATE CASCADE ON DELETE RESTRICT,
  CONSTRAINT fk_order_package FOREIGN KEY (package_id) REFERENCES explore_package (id) ON UPDATE CASCADE ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================================
-- 异步导出任务
-- ============================================================
CREATE TABLE IF NOT EXISTS export_job (
  job_id varchar(32) NOT NULL,
  request_id varchar(64) NOT NULL,
  export_type varchar(32) NOT NULL,
  file_format varchar(8) NOT NULL,
  query_snapshot text NOT NULL COMMENT '冻结后的脱敏查询快照',
  status varchar(16) NOT NULL DEFAULT 'PENDING',
  progress int(11) NOT NULL DEFAULT 0,
  total_rows bigint(20) NOT NULL DEFAULT 0,
  processed_rows bigint(20) NOT NULL DEFAULT 0,
  file_path varchar(500) DEFAULT NULL COMMENT '存储根目录内的相对路径',
  file_name varchar(255) DEFAULT NULL,
  file_size bigint(20) DEFAULT NULL,
  checksum char(64) DEFAULT NULL,
  retry_count int(11) NOT NULL DEFAULT 0,
  next_retry_at datetime NOT NULL,
  lease_owner varchar(128) DEFAULT NULL,
  lease_until datetime DEFAULT NULL,
  error_code varchar(64) DEFAULT NULL,
  error_message varchar(200) DEFAULT NULL,
  operator_id bigint(20) NOT NULL,
  started_at datetime DEFAULT NULL,
  finished_at datetime DEFAULT NULL,
  expires_at datetime DEFAULT NULL,
  create_time datetime NOT NULL,
  update_time datetime NOT NULL,
  PRIMARY KEY (job_id),
  UNIQUE KEY uk_export_operator_request (operator_id, request_id),
  KEY idx_export_ready (status, next_retry_at, job_id),
  KEY idx_export_lease (status, lease_until, job_id),
  KEY idx_export_operator_list (operator_id, create_time, job_id),
  KEY idx_export_admin_list (create_time, job_id),
  KEY idx_export_expiration (status, expires_at),
  KEY idx_export_failure (status, finished_at, job_id),
  CONSTRAINT fk_export_operator FOREIGN KEY (operator_id) REFERENCES employee (id) ON UPDATE CASCADE ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================================
-- 订单事务事件与用户通知
-- ============================================================
CREATE TABLE IF NOT EXISTS order_event_outbox (
  id bigint(20) NOT NULL AUTO_INCREMENT,
  event_id varchar(64) NOT NULL,
  event_type varchar(64) NOT NULL,
  aggregate_id bigint(20) NOT NULL COMMENT '订单ID',
  user_id bigint(20) NOT NULL,
  payload varchar(1000) NOT NULL COMMENT '脱敏事件快照',
  status varchar(16) NOT NULL DEFAULT 'PENDING',
  retry_count int(11) NOT NULL DEFAULT 0,
  next_retry_at datetime NOT NULL,
  locked_until datetime DEFAULT NULL,
  lock_token varchar(64) DEFAULT NULL COMMENT '当前处理租约令牌',
  last_error varchar(200) DEFAULT NULL,
  processed_at datetime DEFAULT NULL,
  create_time datetime NOT NULL,
  update_time datetime NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_outbox_event_id (event_id),
  KEY idx_outbox_ready (status, next_retry_at, locked_until),
  KEY idx_outbox_order (aggregate_id),
  CONSTRAINT fk_outbox_order FOREIGN KEY (aggregate_id) REFERENCES explore_order (id) ON UPDATE CASCADE ON DELETE RESTRICT,
  CONSTRAINT fk_outbox_user FOREIGN KEY (user_id) REFERENCES user (id) ON UPDATE CASCADE ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS user_notification (
  id bigint(20) NOT NULL AUTO_INCREMENT,
  event_id varchar(64) NOT NULL,
  user_id bigint(20) NOT NULL,
  order_id bigint(20) NOT NULL,
  notification_type varchar(64) NOT NULL,
  title varchar(64) NOT NULL,
  content varchar(255) NOT NULL,
  read_status tinyint(1) NOT NULL DEFAULT 0,
  read_time datetime DEFAULT NULL,
  create_time datetime NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_notification_event (event_id),
  KEY idx_notification_user_read (user_id, read_status, create_time),
  KEY idx_notification_order (order_id),
  CONSTRAINT fk_notification_event FOREIGN KEY (event_id) REFERENCES order_event_outbox (event_id) ON UPDATE CASCADE ON DELETE RESTRICT,
  CONSTRAINT fk_notification_user FOREIGN KEY (user_id) REFERENCES user (id) ON UPDATE CASCADE ON DELETE RESTRICT,
  CONSTRAINT fk_notification_order FOREIGN KEY (order_id) REFERENCES explore_order (id) ON UPDATE CASCADE ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS shedlock (
  name varchar(64) NOT NULL,
  lock_until timestamp(3) NOT NULL,
  locked_at timestamp(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  locked_by varchar(255) NOT NULL,
  PRIMARY KEY (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================================
-- 评价表
-- ============================================================
CREATE TABLE IF NOT EXISTS review (
  id bigint(20) NOT NULL AUTO_INCREMENT,
  user_id bigint(20) NOT NULL COMMENT '评价用户',
  item_id bigint(20) NOT NULL COMMENT '评价的特色项目',
  order_id bigint(20) DEFAULT NULL COMMENT '关联预约ID',
  rating int(11) NOT NULL COMMENT '评分 1-5',
  content varchar(500) DEFAULT NULL COMMENT '评价内容',
  reply_content varchar(500) DEFAULT NULL COMMENT '商家回复内容',
  reply_time datetime DEFAULT NULL COMMENT '商家回复时间',
  create_time datetime DEFAULT NULL,
  PRIMARY KEY (id),
  KEY idx_item_id (item_id),
  KEY idx_user_id (user_id),
  KEY idx_order_id (order_id),
  CONSTRAINT fk_review_user FOREIGN KEY (user_id) REFERENCES user (id) ON UPDATE CASCADE ON DELETE RESTRICT,
  CONSTRAINT fk_review_item FOREIGN KEY (item_id) REFERENCES explore_item (id) ON UPDATE CASCADE ON DELETE RESTRICT,
  CONSTRAINT fk_review_order FOREIGN KEY (order_id) REFERENCES explore_order (id) ON UPDATE CASCADE ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

SET @review_reply_content_exists := (
  SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'review' AND column_name = 'reply_content'
);
SET @review_reply_content_sql := IF(
  @review_reply_content_exists = 0,
  'ALTER TABLE review ADD COLUMN reply_content varchar(500) DEFAULT NULL COMMENT ''商家回复内容'' AFTER content',
  'SELECT 1'
);
PREPARE review_reply_content_stmt FROM @review_reply_content_sql;
EXECUTE review_reply_content_stmt;
DEALLOCATE PREPARE review_reply_content_stmt;

SET @review_reply_time_exists := (
  SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'review' AND column_name = 'reply_time'
);
SET @review_reply_time_sql := IF(
  @review_reply_time_exists = 0,
  'ALTER TABLE review ADD COLUMN reply_time datetime DEFAULT NULL COMMENT ''商家回复时间'' AFTER reply_content',
  'SELECT 1'
);
PREPARE review_reply_time_stmt FROM @review_reply_time_sql;
EXECUTE review_reply_time_stmt;
DEALLOCATE PREPARE review_reply_time_stmt;

-- ============================================================
-- 操作日志表
-- ============================================================
CREATE TABLE IF NOT EXISTS operation_log (
  id bigint(20) NOT NULL AUTO_INCREMENT,
  description varchar(255) DEFAULT NULL COMMENT '操作描述',
  operator_id bigint(20) DEFAULT NULL COMMENT '操作人ID',
  request_method varchar(10) DEFAULT NULL COMMENT '请求方法',
  request_uri varchar(255) DEFAULT NULL COMMENT '请求路径',
  client_ip varchar(50) DEFAULT NULL COMMENT '客户端IP指纹',
  cost_time bigint(20) DEFAULT NULL COMMENT '耗时(ms)',
  create_time datetime DEFAULT NULL,
  PRIMARY KEY (id),
  KEY idx_operator_id (operator_id),
  KEY idx_create_time (create_time),
  CONSTRAINT fk_operation_log_employee FOREIGN KEY (operator_id) REFERENCES employee (id) ON UPDATE CASCADE ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================================
-- 演示预约数据
-- ============================================================
INSERT INTO explore_order (id, user_id, order_no, order_type, item_id, package_id, item_name, amount, people_count, contact_name, contact_phone, reserve_time, remark, status, create_time, update_time)
VALUES
  (3001, 1, 'ORD20260425001', 1, 1001, NULL, '城市咖啡体验', 39.00, 2, '张小明', '13800001111', '2026-04-26 14:00:00', '想尝试拿铁', 2, '2026-04-25 10:00:00', '2026-04-26 16:00:00'),
  (3002, 2, 'ORD20260425002', 2, NULL, 2001, '双人咖啡探店套餐', 76.00, 2, '李晓红', '13800002222', '2026-04-27 10:00:00', '', 1, '2026-04-25 11:30:00', '2026-04-25 12:00:00'),
  (3003, 3, 'ORD20260426001', 1, 1006, NULL, '密室逃脱挑战', 78.00, 4, '王大力', '13800003333', '2026-04-28 15:00:00', '4人一起来', 0, '2026-04-26 09:00:00', '2026-04-26 09:00:00'),
  (3004, 1, 'ORD20260426002', 1, 1005, NULL, '手工陶艺制作', 128.00, 1, '张小明', '13800001111', '2026-04-29 13:00:00', '', 0, '2026-04-26 14:00:00', '2026-04-26 14:00:00'),
  (3005, 4, 'ORD20260420001', 2, NULL, 2006, '亲子周末套餐', 178.00, 3, '赵小雨', '13800004444', '2026-04-22 10:00:00', '带孩子来', 2, '2026-04-20 08:00:00', '2026-04-22 12:00:00'),
  (3006, 3, 'ORD20260418001', 1, 1002, NULL, '室内攀岩入门', 88.00, 2, '王大力', '13800003333', '2026-04-19 16:00:00', '', 3, '2026-04-18 20:00:00', '2026-04-18 21:00:00')
ON DUPLICATE KEY UPDATE status = VALUES(status);

-- 保留一条“已完成待评价”项目订单，供用户端评价入口和可重复smoke使用。
INSERT INTO explore_order (
  user_id, order_no, order_type, item_id, package_id, item_name, amount, people_count,
  contact_name, contact_phone, reserve_time, remark, status, create_time, update_time
)
VALUES (
  1, 'ORD20260423001', 1, 1002, NULL, '室内攀岩入门', 88.00, 1,
  '张小明', '13800001111', '2026-04-23 16:00:00', '首次体验，已完成待评价', 2,
  '2026-04-21 19:30:00', '2026-04-23 18:10:00'
)
ON DUPLICATE KEY UPDATE order_no = VALUES(order_no);

-- 保留一条“已完成待评价”套餐订单，供套餐评价入口和可重复smoke使用。
INSERT INTO explore_order (
  user_id, order_no, order_type, item_id, package_id, item_name, amount, people_count,
  contact_name, contact_phone, reserve_time, remark, status, create_time, update_time
)
VALUES (
  1, 'ORD20260423002', 2, NULL, 2004, '周末运动畅玩套餐', 158.00, 1,
  '张小明', '13800001111', '2026-04-23 10:00:00', '套餐体验，已完成待评价', 2,
  '2026-04-21 18:10:00', '2026-04-23 14:20:00'
)
ON DUPLICATE KEY UPDATE
  order_type = VALUES(order_type),
  item_id = VALUES(item_id),
  package_id = VALUES(package_id),
  item_name = VALUES(item_name),
  amount = VALUES(amount),
  people_count = VALUES(people_count),
  contact_name = VALUES(contact_name),
  contact_phone = VALUES(contact_phone),
  reserve_time = VALUES(reserve_time),
  remark = VALUES(remark),
  status = VALUES(status),
  update_time = VALUES(update_time);

-- ============================================================
-- 演示评价数据
-- ============================================================
INSERT INTO review (id, user_id, item_id, order_id, rating, content, reply_content, reply_time, create_time)
VALUES
  (4001, 1, 1001, 3001, 5, '咖啡很好喝，环境也很适合拍照，下次还来！', '感谢反馈，欢迎下次再来体验新品。', '2026-04-26 17:00:00', '2026-04-26 16:30:00'),
  (4002, 4, 1008, 3005, 4, '孩子玩得很开心，老师很有耐心，就是场地有点小', '感谢建议，我们会继续优化现场动线。', '2026-04-22 13:10:00', '2026-04-22 12:30:00'),
  (4003, 4, 1011, NULL, 5, '花艺老师很专业，做出来的花束超好看', NULL, NULL, '2026-04-22 12:35:00'),
  (4004, 2, 1001, NULL, 4, '拿铁不错，甜品也好吃，性价比很高', NULL, NULL, '2026-04-20 15:00:00')
ON DUPLICATE KEY UPDATE
  item_id = VALUES(item_id),
  order_id = VALUES(order_id),
  rating = VALUES(rating),
  content = VALUES(content),
  reply_content = VALUES(reply_content),
  reply_time = VALUES(reply_time);
