-- Non-destructive migration for older local_explorer databases.
-- Run this when the backend log reports missing columns such as
-- duration_minutes, capacity, booked, district, address, meeting_point,
-- cancel_policy, user.status, or review reply fields.

USE local_explorer;

SET @schema_name = DATABASE();

CREATE TABLE IF NOT EXISTS runtime_setting (
  setting_key varchar(64) NOT NULL,
  setting_value text NOT NULL,
  update_time datetime NOT NULL,
  PRIMARY KEY (setting_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS auth_session (
  id bigint(20) NOT NULL AUTO_INCREMENT,
  session_id varchar(64) NOT NULL,
  token_family_id varchar(64) NOT NULL,
  principal_type varchar(16) NOT NULL,
  principal_id bigint(20) NOT NULL,
  refresh_token_hash char(64) NOT NULL,
  status varchar(16) NOT NULL DEFAULT 'ACTIVE',
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

SET @sql = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'user' AND COLUMN_NAME = 'status') = 0,
  'ALTER TABLE user ADD COLUMN status int(11) NOT NULL DEFAULT 1 AFTER avatar',
  'SELECT ''user.status exists''');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'employee' AND COLUMN_NAME = 'role') = 0,
  'ALTER TABLE employee ADD COLUMN role varchar(32) NOT NULL DEFAULT ''STAFF'' AFTER status',
  'SELECT ''employee.role exists''');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

UPDATE employee SET role = 'ADMIN' WHERE id = 1;

SET @sql = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'explore_order' AND COLUMN_NAME = 'request_id') = 0,
  'ALTER TABLE explore_order ADD COLUMN request_id varchar(64) DEFAULT NULL AFTER reserve_time',
  'SELECT ''explore_order.request_id exists''');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF((SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'explore_order' AND INDEX_NAME = 'idx_order_user_request') = 0,
  'ALTER TABLE explore_order ADD UNIQUE KEY idx_order_user_request (user_id, request_id)',
  'SELECT ''explore_order.idx_order_user_request exists''');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'explore_order' AND COLUMN_NAME = 'expire_at') = 0,
  'ALTER TABLE explore_order ADD COLUMN expire_at datetime DEFAULT NULL COMMENT ''待确认预约自动关闭时间'' AFTER request_id',
  'SELECT ''explore_order.expire_at exists''');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'explore_order' AND COLUMN_NAME = 'cancel_type') = 0,
  'ALTER TABLE explore_order ADD COLUMN cancel_type varchar(16) DEFAULT NULL COMMENT ''USER/ADMIN/TIMEOUT'' AFTER expire_at',
  'SELECT ''explore_order.cancel_type exists''');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'explore_order' AND COLUMN_NAME = 'cancel_reason') = 0,
  'ALTER TABLE explore_order ADD COLUMN cancel_reason varchar(255) DEFAULT NULL COMMENT ''取消或超时原因'' AFTER cancel_type',
  'SELECT ''explore_order.cancel_reason exists''');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF((SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'explore_order' AND INDEX_NAME = 'idx_order_status_expire') = 0,
  'ALTER TABLE explore_order ADD KEY idx_order_status_expire (status, expire_at)',
  'SELECT ''explore_order.idx_order_status_expire exists''');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS export_job (
  job_id varchar(32) NOT NULL,
  request_id varchar(64) NOT NULL,
  export_type varchar(32) NOT NULL,
  file_format varchar(8) NOT NULL,
  query_snapshot text NOT NULL,
  status varchar(16) NOT NULL DEFAULT 'PENDING',
  progress int(11) NOT NULL DEFAULT 0,
  total_rows bigint(20) NOT NULL DEFAULT 0,
  processed_rows bigint(20) NOT NULL DEFAULT 0,
  file_path varchar(500) DEFAULT NULL,
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

SET @ready_index_columns = (SELECT GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX)
  FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = @schema_name
  AND TABLE_NAME = 'export_job' AND INDEX_NAME = 'idx_export_ready');
SET @sql = IF(@ready_index_columns IS NOT NULL
  AND @ready_index_columns <> 'status,next_retry_at,job_id',
  'ALTER TABLE export_job DROP INDEX idx_export_ready', 'SELECT ''idx_export_ready compatible''');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF((SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = @schema_name
  AND TABLE_NAME = 'export_job' AND INDEX_NAME = 'idx_export_failure') = 0,
  'ALTER TABLE export_job ADD INDEX idx_export_failure (status, finished_at, job_id)',
  'SELECT ''idx_export_failure exists''');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @sql = IF((SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = @schema_name
  AND TABLE_NAME = 'export_job' AND INDEX_NAME = 'idx_export_ready') = 0,
  'ALTER TABLE export_job ADD INDEX idx_export_ready (status, next_retry_at, job_id)',
  'SELECT ''idx_export_ready exists''');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF((SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = @schema_name
  AND TABLE_NAME = 'export_job' AND INDEX_NAME = 'idx_export_lease') = 0,
  'ALTER TABLE export_job ADD INDEX idx_export_lease (status, lease_until, job_id)',
  'SELECT ''idx_export_lease exists''');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF((SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = @schema_name
  AND TABLE_NAME = 'export_job' AND INDEX_NAME = 'idx_export_admin_list') = 0,
  'ALTER TABLE export_job ADD INDEX idx_export_admin_list (create_time, job_id)',
  'SELECT ''idx_export_admin_list exists''');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS order_event_outbox (
  id bigint(20) NOT NULL AUTO_INCREMENT,
  event_id varchar(64) NOT NULL,
  event_type varchar(64) NOT NULL,
  aggregate_id bigint(20) NOT NULL,
  user_id bigint(20) NOT NULL,
  payload varchar(1000) NOT NULL,
  status varchar(16) NOT NULL DEFAULT 'PENDING',
  retry_count int(11) NOT NULL DEFAULT 0,
  next_retry_at datetime NOT NULL,
  locked_until datetime DEFAULT NULL,
  lock_token varchar(64) DEFAULT NULL,
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

SET @sql = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'order_event_outbox' AND COLUMN_NAME = 'lock_token') = 0,
  'ALTER TABLE order_event_outbox ADD COLUMN lock_token varchar(64) DEFAULT NULL AFTER locked_until',
  'SELECT ''order_event_outbox.lock_token exists''');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

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

SET @sql = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'review' AND COLUMN_NAME = 'reply_content') = 0,
  'ALTER TABLE review ADD COLUMN reply_content varchar(500) DEFAULT NULL AFTER content',
  'SELECT ''review.reply_content exists''');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'review' AND COLUMN_NAME = 'reply_time') = 0,
  'ALTER TABLE review ADD COLUMN reply_time datetime DEFAULT NULL AFTER reply_content',
  'SELECT ''review.reply_time exists''');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'explore_item' AND COLUMN_NAME = 'duration_minutes') = 0,
  'ALTER TABLE explore_item ADD COLUMN duration_minutes int(11) DEFAULT NULL AFTER description',
  'SELECT ''explore_item.duration_minutes exists''');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'explore_item' AND COLUMN_NAME = 'capacity') = 0,
  'ALTER TABLE explore_item ADD COLUMN capacity int(11) DEFAULT 0 AFTER duration_minutes',
  'SELECT ''explore_item.capacity exists''');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'explore_item' AND COLUMN_NAME = 'booked') = 0,
  'ALTER TABLE explore_item ADD COLUMN booked int(11) DEFAULT 0 AFTER capacity',
  'SELECT ''explore_item.booked exists''');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'explore_item' AND COLUMN_NAME = 'district') = 0,
  'ALTER TABLE explore_item ADD COLUMN district varchar(64) DEFAULT NULL AFTER booked',
  'SELECT ''explore_item.district exists''');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'explore_item' AND COLUMN_NAME = 'address') = 0,
  'ALTER TABLE explore_item ADD COLUMN address varchar(255) DEFAULT NULL AFTER district',
  'SELECT ''explore_item.address exists''');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'explore_item' AND COLUMN_NAME = 'meeting_point') = 0,
  'ALTER TABLE explore_item ADD COLUMN meeting_point varchar(255) DEFAULT NULL AFTER address',
  'SELECT ''explore_item.meeting_point exists''');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'explore_item' AND COLUMN_NAME = 'cancel_policy') = 0,
  'ALTER TABLE explore_item ADD COLUMN cancel_policy varchar(255) DEFAULT NULL AFTER meeting_point',
  'SELECT ''explore_item.cancel_policy exists''');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'explore_package' AND COLUMN_NAME = 'duration_minutes') = 0,
  'ALTER TABLE explore_package ADD COLUMN duration_minutes int(11) DEFAULT NULL AFTER image',
  'SELECT ''explore_package.duration_minutes exists''');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'explore_package' AND COLUMN_NAME = 'capacity') = 0,
  'ALTER TABLE explore_package ADD COLUMN capacity int(11) DEFAULT 0 AFTER duration_minutes',
  'SELECT ''explore_package.capacity exists''');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'explore_package' AND COLUMN_NAME = 'booked') = 0,
  'ALTER TABLE explore_package ADD COLUMN booked int(11) DEFAULT 0 AFTER capacity',
  'SELECT ''explore_package.booked exists''');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'explore_package' AND COLUMN_NAME = 'district') = 0,
  'ALTER TABLE explore_package ADD COLUMN district varchar(64) DEFAULT NULL AFTER booked',
  'SELECT ''explore_package.district exists''');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'explore_package' AND COLUMN_NAME = 'address') = 0,
  'ALTER TABLE explore_package ADD COLUMN address varchar(255) DEFAULT NULL AFTER district',
  'SELECT ''explore_package.address exists''');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'explore_package' AND COLUMN_NAME = 'meeting_point') = 0,
  'ALTER TABLE explore_package ADD COLUMN meeting_point varchar(255) DEFAULT NULL AFTER address',
  'SELECT ''explore_package.meeting_point exists''');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'explore_package' AND COLUMN_NAME = 'cancel_policy') = 0,
  'ALTER TABLE explore_package ADD COLUMN cancel_policy varchar(255) DEFAULT NULL AFTER meeting_point',
  'SELECT ''explore_package.cancel_policy exists''');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

UPDATE explore_item SET capacity = 24 WHERE id IN (1001,1002,1003,1004,1005,1006,1007,1008,1009,1010,1011,1012) AND (capacity IS NULL OR capacity = 0);
UPDATE explore_item SET booked = 0 WHERE id IN (1001,1002,1003,1004,1005,1006,1007,1008,1009,1010,1011,1012) AND booked IS NULL;
UPDATE explore_package SET capacity = 20 WHERE id IN (2001,2002,2003,2004,2005,2006) AND (capacity IS NULL OR capacity = 0);
UPDATE explore_package SET booked = 0 WHERE id IN (2001,2002,2003,2004,2005,2006) AND booked IS NULL;

UPDATE operation_log SET description = '删除员工' WHERE description = 'Delete employee';
UPDATE operation_log SET description = '新增特色项目' WHERE description = 'Create explore item';
UPDATE operation_log SET description = '删除特色项目' WHERE description = 'Delete explore items';
UPDATE operation_log SET description = '修改特色项目' WHERE description = 'Update explore item';
UPDATE operation_log SET description = '特色项目上下架' WHERE description = 'Update explore item status';
UPDATE operation_log SET description = '修改用户资料' WHERE description = 'Update user profile';
UPDATE operation_log SET description = '重置用户密码' WHERE description = 'Reset user password';
UPDATE operation_log SET description = '用户账号启停' WHERE description = 'Update user account status';
