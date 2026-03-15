-- 创建数据库
CREATE DATABASE IF NOT EXISTS `jelly_cinema` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE `jelly_cinema`;

-- 用户账户表
CREATE TABLE `user` (
  `id` BIGINT NOT MAGNITUDE AUTO_INCREMENT PRIMARY KEY,
  `username` VARCHAR(50) NOT NULL COMMENT '登录账户名',
  `password` VARCHAR(100) NOT NULL COMMENT '密码密文',
  `phone` VARCHAR(20) DEFAULT NULL COMMENT '手机号',
  `email` VARCHAR(100) DEFAULT NULL COMMENT '邮箱',
  `role` VARCHAR(20) DEFAULT 'USER' COMMENT '角色: ADMIN, USER',
  `status` TINYINT DEFAULT 1 COMMENT '状态: 1正常 0封禁',
  `deleted` TINYINT DEFAULT 0 COMMENT '逻辑删除: 0未删除 1已删除',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY `uk_username` (`username`),
  KEY `idx_phone` (`phone`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户账户表';

-- 用户资料表 (冷数据)
CREATE TABLE `user_profile` (
  `user_id` BIGINT NOT NULL PRIMARY KEY COMMENT '关联user表的主键id',
  `nickname` VARCHAR(50) DEFAULT NULL COMMENT '用户昵称',
  `avatar` VARCHAR(255) DEFAULT NULL COMMENT '头像URL',
  `gender` TINYINT DEFAULT 0 COMMENT '性别: 0未知 1男 2女',
  `birthday` DATE DEFAULT NULL COMMENT '生日',
  `bio` VARCHAR(255) DEFAULT NULL COMMENT '个性签名',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户资料表';

-- 影视内容主表
CREATE TABLE `media` (
  `id` BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  `title` VARCHAR(100) NOT NULL COMMENT '内容标题',
  `original_title` VARCHAR(100) DEFAULT NULL COMMENT '原名(外语名)',
  `type` TINYINT NOT NULL COMMENT '类型: 1电影 2电视剧 3动漫',
  `status` TINYINT DEFAULT 0 COMMENT '状态: 0筹备 1待映 2热映 3下架',
  `release_date` DATE DEFAULT NULL COMMENT '发行/上映日期',
  `duration` INT DEFAULT 0 COMMENT '总时长(分钟)',
  `cover_url` VARCHAR(255) DEFAULT NULL COMMENT '竖版海报',
  `backdrop_url` VARCHAR(255) DEFAULT NULL COMMENT '横向剧照',
  `summary` TEXT COMMENT '长简介',
  `rating` DECIMAL(3,1) DEFAULT 0.0 COMMENT '综合评分(0.0-10.0)',
  `trailer_url` VARCHAR(255) DEFAULT NULL COMMENT '预告/播客链接',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY `idx_type_status` (`type`, `status`),
  KEY `idx_release_date` (`release_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='泛影视内容主表';

-- 演职员表
CREATE TABLE `actor` (
  `id` BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  `name` VARCHAR(100) NOT NULL COMMENT '姓名',
  `foreign_name` VARCHAR(100) DEFAULT NULL COMMENT '外文名',
  `avatar_url` VARCHAR(255) DEFAULT NULL COMMENT '头像',
  `bio` TEXT COMMENT '生平简介',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='演职员基础库';


-- 实体影院表
CREATE TABLE `cinema` (
  `id` BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  `name` VARCHAR(100) NOT NULL COMMENT '影院名称',
  `brand_id` BIGINT DEFAULT NULL COMMENT '品牌ID(万达、CGV等)',
  `province` VARCHAR(50) DEFAULT NULL COMMENT '省',
  `city` VARCHAR(50) DEFAULT NULL COMMENT '市',
  `district` VARCHAR(50) DEFAULT NULL COMMENT '区',
  `address` VARCHAR(255) DEFAULT NULL COMMENT '详细地址',
  `longitude` DECIMAL(10,6) DEFAULT NULL COMMENT '经度',
  `latitude` DECIMAL(10,6) DEFAULT NULL COMMENT '纬度',
  `phone` VARCHAR(50) DEFAULT NULL COMMENT '联系电话',
  `services_json` JSON DEFAULT NULL COMMENT '服务列表JSON(如退票、3D眼镜)',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY `idx_city` (`city`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='影院基础信息';

-- 排期表
CREATE TABLE `schedule` (
  `id` BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  `media_id` BIGINT NOT NULL COMMENT '关联影视ID(限定电影)',
  `cinema_id` BIGINT NOT NULL COMMENT '关联影院ID',
  `hall_id` BIGINT NOT NULL COMMENT '关联影厅ID',
  `show_time` DATETIME NOT NULL COMMENT '放映时间',
  `end_time` DATETIME NOT NULL COMMENT '散场时间',
  `price` DECIMAL(10,2) NOT NULL COMMENT '基础票价',
  `status` TINYINT DEFAULT 1 COMMENT '状态: 1正常 0已关闭',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY `idx_media_cinema` (`media_id`, `cinema_id`),
  KEY `idx_show_time` (`show_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='排期/场次池';

-- 订单主表
CREATE TABLE `order` (
  `id` BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  `order_no` VARCHAR(64) NOT NULL COMMENT '业务单号',
  `user_id` BIGINT NOT NULL COMMENT '用户ID',
  `schedule_id` BIGINT NOT NULL COMMENT '场次ID',
  `total_price` DECIMAL(10,2) NOT NULL COMMENT '总价',
  `pay_price` DECIMAL(10,2) NOT NULL COMMENT '实付款',
  `status` TINYINT DEFAULT 0 COMMENT '0待支付 1已支付 2已取消 3已退款',
  `expire_time` DATETIME NOT NULL COMMENT '支付倒计时过期时间',
  `pay_time` DATETIME DEFAULT NULL COMMENT '支付成功时间',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY `uk_order_no` (`order_no`),
  KEY `idx_user_status` (`user_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='交易订单主表';

-- 插入一条初始的 Admin 用户用于测试 (密码为 123456 加密的密文或明文，可以根据项目设定，这里先给默认)
INSERT INTO `user` (`username`, `password`, `role`) VALUES ('admin', '123456', 'ADMIN');
