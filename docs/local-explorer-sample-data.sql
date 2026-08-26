SET NAMES utf8mb4;

USE local_explorer;

INSERT INTO category (id, type, name, sort, status, create_time, update_time, create_user, update_user)
VALUES
  (101, 1, '周末放松', 10, 1, NOW(), NOW(), 1, 1),
  (102, 1, '运动体验', 20, 1, NOW(), NOW(), 1, 1),
  (103, 1, '拍照打卡', 30, 1, NOW(), NOW(), 1, 1),
  (201, 2, '双人套餐', 10, 1, NOW(), NOW(), 1, 1),
  (202, 2, '朋友聚会', 20, 1, NOW(), NOW(), 1, 1)
ON DUPLICATE KEY UPDATE
  name = VALUES(name),
  type = VALUES(type),
  sort = VALUES(sort),
  status = VALUES(status),
  update_time = NOW(),
  update_user = 1;

INSERT INTO explore_item (
  id, name, category_id, price, image, description,
  duration_minutes, capacity, booked, district, address, meeting_point, cancel_policy,
  status, create_time, update_time, create_user, update_user
)
VALUES
  (1001, '城市咖啡体验', 101, 39.00, '/assets/images/coffee.webp', '精品咖啡品鉴与店内拍照打卡', 90, 24, 8, '高新路', '高新区科技路 88 号', '一层咖啡吧前台', '开始前 2 小时可免费取消', 1, NOW(), NOW(), 1, 1),
  (1002, '室内攀岩入门', 102, 88.00, '/assets/images/workshop.webp', '含基础教学与安全装备租用', 120, 16, 5, '曲江', '曲江新区芙蓉南路 18 号', '攀岩馆装备区', '开始前 4 小时可免费取消', 1, NOW(), NOW(), 1, 1),
  (1003, '复古写真体验', 103, 168.00, '/assets/images/citywalk.webp', '含妆造建议、布景拍摄与精修照片', 180, 10, 3, '小寨', '雁塔区长安中路 99 号', '三层摄影棚前台', '开始前 24 小时可免费取消', 1, NOW(), NOW(), 1, 1),
  (1004, '桌游剧本体验', 101, 59.00, '/assets/images/boardgame.webp', '适合朋友聚会的轻推理体验', 150, 30, 12, '钟楼', '碑林区南大街 36 号', '二层桌游区', '开始前 2 小时可免费取消', 1, NOW(), NOW(), 1, 1)
ON DUPLICATE KEY UPDATE
  name = VALUES(name),
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
  update_time = NOW(),
  update_user = 1;

INSERT INTO explore_package (
  id, category_id, name, price, status, description, image,
  duration_minutes, capacity, booked, district, address, meeting_point, cancel_policy,
  create_time, update_time, create_user, update_user
)
VALUES
  (2001, 201, '双人咖啡探店套餐', 76.00, 1, '两杯精品咖啡加甜品，适合周末探店', '/assets/images/coffee.webp', 120, 16, 6, '高新路', '高新区科技路 88 号', '一层咖啡吧前台', '开始前 2 小时可免费取消', NOW(), NOW(), 1, 1),
  (2002, 202, '四人桌游聚会套餐', 198.00, 1, '四人桌游包场与饮品组合', '/assets/images/package.webp', 180, 20, 9, '钟楼', '碑林区南大街 36 号', '二层桌游区', '开始前 4 小时可免费取消', NOW(), NOW(), 1, 1)
ON DUPLICATE KEY UPDATE
  name = VALUES(name),
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
  update_time = NOW(),
  update_user = 1;

DELETE FROM explore_package_item WHERE package_id IN (2001, 2002);

INSERT INTO explore_package_item (package_id, item_id, name, price, copies)
VALUES
  (2001, 1001, '城市咖啡体验', 39.00, 2),
  (2002, 1004, '桌游剧本体验', 59.00, 4);
