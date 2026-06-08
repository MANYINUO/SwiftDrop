-- =============================================================
-- 同城即时配送平台 · 数据库初始化脚本
-- MySQL 8.0+ / InnoDB / utf8mb4
--
-- 设计约定：
--   1. 金额统一用「分」存 INT UNSIGNED，不用 DECIMAL/FLOAT —— 避免浮点误差
--   2. 经纬度用 DECIMAL(10,7)，约 1cm 精度，够用且比 POINT 类型好调试
--   3. 状态字段用 VARCHAR 而非 TINYINT —— 便于排查和演示，代价是略占空间
--   4. 所有可变状态表带 version 字段，支持乐观锁
--   5. 第一版不分区不分表；数据量上来后按 created_at 或 rider_id 分
-- =============================================================

CREATE DATABASE IF NOT EXISTS swiftdrop
  DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE swiftdrop;

SET NAMES utf8mb4;

-- =============================================================
-- 1. 配送订单主表
-- =============================================================
DROP TABLE IF EXISTS delivery_order;
CREATE TABLE delivery_order (
  id                BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT COMMENT '主键',
  order_no          VARCHAR(32)      NOT NULL                COMMENT '业务订单号（对外暴露，不暴露自增ID）',
  user_id           BIGINT UNSIGNED  NOT NULL                COMMENT '下单用户ID',
  status            VARCHAR(24)      NOT NULL                COMMENT '订单状态，见下方状态枚举',

  -- ---------- 取件信息 ----------
  pickup_lng        DECIMAL(10,7)    NOT NULL                COMMENT '取件点经度',
  pickup_lat        DECIMAL(10,7)    NOT NULL                COMMENT '取件点纬度',
  pickup_address    VARCHAR(255)     NOT NULL                COMMENT '取件地址（文本）',
  pickup_contact    VARCHAR(64)      NOT NULL                COMMENT '取件联系人',
  pickup_phone      VARCHAR(20)      NOT NULL                COMMENT '取件联系电话',

  -- ---------- 送达信息 ----------
  deliver_lng       DECIMAL(10,7)    NOT NULL                COMMENT '送达点经度',
  deliver_lat       DECIMAL(10,7)    NOT NULL                COMMENT '送达点纬度',
  deliver_address   VARCHAR(255)     NOT NULL                COMMENT '送达地址（文本）',
  deliver_contact   VARCHAR(64)      NOT NULL                COMMENT '收件联系人',
  deliver_phone     VARCHAR(20)      NOT NULL                COMMENT '收件联系电话',

  -- ---------- 物品与金额 ----------
  goods_desc        VARCHAR(255)     DEFAULT NULL            COMMENT '物品描述',
  goods_weight_g    INT UNSIGNED     DEFAULT NULL            COMMENT '物品重量（克）',
  amount_fen        INT UNSIGNED     NOT NULL DEFAULT 0      COMMENT '订单金额（分）',
  distance_m        INT UNSIGNED     DEFAULT NULL            COMMENT '预估配送距离（米）',

  -- ---------- 骑手 ----------
  rider_id          BIGINT UNSIGNED  DEFAULT NULL            COMMENT '接单骑手ID，未派单为 NULL',

  -- ---------- 时效节点 ----------
  expect_deliver_at DATETIME         DEFAULT NULL            COMMENT '承诺送达时间',
  dispatched_at     DATETIME         DEFAULT NULL            COMMENT '首次派单时间',
  accepted_at       DATETIME         DEFAULT NULL            COMMENT '骑手接单时间',
  picked_up_at      DATETIME         DEFAULT NULL            COMMENT '骑手取货时间',
  delivered_at      DATETIME         DEFAULT NULL            COMMENT '送达时间',
  cancelled_at      DATETIME         DEFAULT NULL            COMMENT '取消时间',

  -- ---------- 调度相关 ----------
  dispatch_round    TINYINT UNSIGNED NOT NULL DEFAULT 0      COMMENT '已进行到第几轮派单',
  need_manual       TINYINT          NOT NULL DEFAULT 0      COMMENT '1=需人工调度（候选骑手已耗尽）',
  cancel_reason     VARCHAR(255)     DEFAULT NULL            COMMENT '取消原因',

  -- ---------- 通用 ----------
  version           INT UNSIGNED     NOT NULL DEFAULT 0      COMMENT '乐观锁版本号',
  created_at        DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at        DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

  PRIMARY KEY (id),
  UNIQUE KEY uk_order_no (order_no),
  KEY idx_user_created (user_id, created_at),
  KEY idx_status_created (status, created_at),
  KEY idx_rider_status (rider_id, status),
  KEY idx_manual (need_manual, status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='配送订单主表';

-- status 枚举：
--   PENDING_DISPATCH  待派单     —— 订单已创建，等待调度
--   DISPATCHING       派单中     —— 已生成派单任务，等待骑手响应
--   PENDING_PICKUP    待取货     —— 骑手已接单，正在前往取件点
--   DELIVERING        配送中     —— 骑手已取货，配送途中
--   DELIVERED         已送达     —— 终态
--   CANCELLED         已取消     —— 终态
--   EXCEPTION         异常       —— 需人工介入，可改派或取消


-- =============================================================
-- 2. 骑手表
-- =============================================================
DROP TABLE IF EXISTS rider;
CREATE TABLE rider (
  id                BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT COMMENT '主键',
  rider_no          VARCHAR(32)      NOT NULL                COMMENT '骑手编号（对外暴露）',
  name              VARCHAR(64)      NOT NULL                COMMENT '姓名',
  phone             VARCHAR(20)      NOT NULL                COMMENT '手机号',
  online_status     TINYINT          NOT NULL DEFAULT 0      COMMENT '0=离线 1=在线',
  work_status       TINYINT          NOT NULL DEFAULT 0      COMMENT '0=空闲 1=忙碌',
  max_capacity      TINYINT UNSIGNED NOT NULL DEFAULT 1      COMMENT '最大同时接单数',
  -- 用有符号 INT：这个计数器会被减，用 UNSIGNED 的话 0-1 会下溢报错（踩过坑）
  delivering_count  INT              NOT NULL DEFAULT 0      COMMENT '在手订单数（冗余字段，便于快速筛选）',
  score             DECIMAL(3,2)     NOT NULL DEFAULT 5.00   COMMENT '服务评分 0.00~5.00',

  -- 最后上报位置：实时位置在 Redis，这里是冷备（重启后可恢复 / 便于排查）
  last_lng          DECIMAL(10,7)    DEFAULT NULL            COMMENT '最后上报经度（冷备）',
  last_lat          DECIMAL(10,7)    DEFAULT NULL            COMMENT '最后上报纬度（冷备）',
  last_report_at    DATETIME         DEFAULT NULL            COMMENT '最后上报时间',

  version           INT UNSIGNED     NOT NULL DEFAULT 0      COMMENT '乐观锁版本号',
  created_at        DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at        DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

  PRIMARY KEY (id),
  UNIQUE KEY uk_rider_no (rider_no),
  UNIQUE KEY uk_phone (phone),
  KEY idx_online_work (online_status, work_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='骑手表';


-- =============================================================
-- 3. 派单任务表（每次「派给某个骑手」= 一条记录）
--    这张表是量化指标的来源：派单成功率、平均响应时长都靠它
-- =============================================================
DROP TABLE IF EXISTS dispatch_task;
CREATE TABLE dispatch_task (
  id              BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT COMMENT '主键',
  order_id        BIGINT UNSIGNED  NOT NULL                COMMENT '订单ID',
  order_no        VARCHAR(32)      NOT NULL                COMMENT '订单号（冗余，便于排查）',
  round           TINYINT UNSIGNED NOT NULL                COMMENT '第几轮派单，从 1 开始',
  rider_id        BIGINT UNSIGNED  NOT NULL                COMMENT '目标骑手ID',
  candidate_rank  TINYINT UNSIGNED DEFAULT NULL            COMMENT '该骑手在候选列表中的排名',
  candidate_score DECIMAL(8,4)     DEFAULT NULL            COMMENT '派单打分（v2 多目标模型）',
  score_detail    JSON             DEFAULT NULL            COMMENT '打分明细：各因子得分，便于调参与解释',
  status          VARCHAR(16)      NOT NULL                COMMENT 'PENDING/ACCEPTED/REJECTED/TIMEOUT/CANCELLED',
  expire_at       DATETIME         NOT NULL                COMMENT '响应截止时间',
  responded_at    DATETIME         DEFAULT NULL            COMMENT '骑手响应时间',
  created_at      DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP,

  PRIMARY KEY (id),
  UNIQUE KEY uk_order_round_rider (order_id, round, rider_id),
  KEY idx_rider_status (rider_id, status, expire_at),
  KEY idx_status_expire (status, expire_at),          -- 扫描超时任务用
  KEY idx_order (order_id, round)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='派单任务表';

-- status 枚举：
--   PENDING    已推送，等待骑手响应
--   ACCEPTED   骑手接单（一个订单最终只会有一条 ACCEPTED）
--   REJECTED   骑手拒单
--   TIMEOUT    超时未响应
--   CANCELLED  订单取消 / 改派导致任务作废


-- =============================================================
-- 4. 订单事件表（谁在什么时候做了什么）
--    排查问题、复盘调度效果、做数据看板都靠它
-- =============================================================
DROP TABLE IF EXISTS order_event;
CREATE TABLE order_event (
  id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  order_id      BIGINT UNSIGNED NOT NULL                COMMENT '订单ID',
  order_no      VARCHAR(32)     NOT NULL                COMMENT '订单号（冗余）',
  event_type    VARCHAR(32)     NOT NULL                COMMENT '事件类型',
  from_status   VARCHAR(24)     DEFAULT NULL            COMMENT '变更前状态',
  to_status     VARCHAR(24)     DEFAULT NULL            COMMENT '变更后状态',
  operator_type VARCHAR(16)     NOT NULL                COMMENT 'USER/RIDER/SYSTEM/ADMIN',
  operator_id   BIGINT UNSIGNED DEFAULT NULL            COMMENT '操作人ID',
  remark        VARCHAR(255)    DEFAULT NULL            COMMENT '备注',
  ext           JSON            DEFAULT NULL            COMMENT '扩展信息',
  created_at    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,

  PRIMARY KEY (id),
  KEY idx_order_created (order_id, created_at),
  KEY idx_type_created (event_type, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单事件流水表';

-- event_type 建议取值：
--   ORDER_CREATED     创建订单
--   STATUS_CHANGED    状态变更（通用）
--   DISPATCH_CREATED  生成派单任务
--   DISPATCH_ACCEPTED 骑手接单
--   DISPATCH_REJECTED 骑手拒单
--   DISPATCH_TIMEOUT  派单超时
--   DISPATCH_EXHAUSTED 候选骑手耗尽 → 转人工
--   RIDER_OFFLINE     骑手掉线导致改派
--   ADMIN_RESOLVED    后台人工处理


-- =============================================================
-- 5. 骑手位置流水表（可选 · 用于轨迹回放）
--    实时位置在 Redis GEO；这张表只为「回放历史轨迹」和排查
--    数据量大，上线前需按 rider_id 或 report_at 分表
-- =============================================================
DROP TABLE IF EXISTS rider_location_log;
CREATE TABLE rider_location_log (
  id        BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  rider_id  BIGINT UNSIGNED NOT NULL                COMMENT '骑手ID',
  order_no  VARCHAR(32)     DEFAULT NULL            COMMENT '关联订单号（配送中才有）',
  lng       DECIMAL(10,7)   NOT NULL                COMMENT '经度',
  lat       DECIMAL(10,7)   NOT NULL                COMMENT '纬度',
  report_at DATETIME(3)     NOT NULL                COMMENT '上报时间（毫秒精度）',

  PRIMARY KEY (id),
  KEY idx_rider_time (rider_id, report_at),
  KEY idx_order_time (order_no, report_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='骑手位置流水表（轨迹回放用）';


-- =============================================================
-- 6. 幂等记录表（可选 · 也可用 Redis 实现）
--    用途：用户重复提交下单请求时，只产生一笔订单
-- =============================================================
DROP TABLE IF EXISTS idempotent_record;
CREATE TABLE idempotent_record (
  idem_key    VARCHAR(64)     NOT NULL                COMMENT '幂等键：bizType:userId:clientKey',
  biz_type    VARCHAR(32)     NOT NULL                COMMENT '业务类型，如 ORDER_CREATE',
  result_ref  VARCHAR(64)     DEFAULT NULL            COMMENT '首次执行的业务结果引用（如订单号）',
  created_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  expire_at   DATETIME        NOT NULL                COMMENT '过期时间，便于定期清理',

  PRIMARY KEY (idem_key),
  KEY idx_expire (expire_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='幂等记录表';


-- =============================================================
-- 初始化演示数据
-- =============================================================
INSERT INTO rider (rider_no, name, phone, online_status, work_status, max_capacity, score)
VALUES
  ('R0001', '张骑手', '13800000001', 1, 0, 2, 4.90),
  ('R0002', '李骑手', '13800000002', 1, 0, 2, 4.70),
  ('R0003', '王骑手', '13800000003', 1, 0, 1, 4.85),
  ('R0004', '赵骑手', '13800000004', 0, 0, 2, 4.60);
