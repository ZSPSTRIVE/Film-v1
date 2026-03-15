-- Create database
CREATE DATABASE IF NOT EXISTS j_cinema DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE j_cinema;

-- 2.1 用户中心与行为流表 --
CREATE TABLE IF NOT EXISTS `user` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    `username` VARCHAR(50) NOT NULL COMMENT '用户名',
    `password` VARCHAR(100) NOT NULL COMMENT '密码',
    `phone` VARCHAR(20) COMMENT '手机号',
    `email` VARCHAR(100) COMMENT '邮箱',
    `role` VARCHAR(20) DEFAULT 'USER' COMMENT '角色: ADMIN, USER',
    `status` TINYINT DEFAULT 1 COMMENT '状态: 1正常, 0封禁',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` TINYINT DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_username` (`username`),
    KEY `idx_phone` (`phone`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户账户表';

CREATE TABLE IF NOT EXISTS `user_profile` (
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `nickname` VARCHAR(50) COMMENT '昵称',
    `avatar` VARCHAR(255) COMMENT '头像URL',
    `gender` TINYINT COMMENT '性别: 0未知, 1男, 2女',
    `birthday` DATE COMMENT '生日',
    `bio` VARCHAR(255) COMMENT '个性签名',
    PRIMARY KEY (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户公开资料表';

CREATE TABLE IF NOT EXISTS `user_favorite` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `media_id` BIGINT NOT NULL COMMENT '影视ID',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_user_media` (`user_id`, `media_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户收藏链路';

CREATE TABLE IF NOT EXISTS `search_keyword_log` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `keyword` VARCHAR(100) NOT NULL,
    `user_id` BIGINT COMMENT '用户ID',
    `search_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户搜索日志';

-- 2.2 影视内容生态群 --
CREATE TABLE IF NOT EXISTS `media` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `title` VARCHAR(100) NOT NULL COMMENT '内容标题',
    `original_title` VARCHAR(100) COMMENT '原标题',
    `type` TINYINT NOT NULL COMMENT '类型: 1电影, 2电视剧, 3动漫',
    `status` TINYINT DEFAULT 0 COMMENT '状态: 0筹备, 1待映, 2热映, 3下架',
    `release_date` DATE COMMENT '发行日期',
    `duration` INT COMMENT '总时长分钟',
    `cover_url` VARCHAR(255) COMMENT '竖版海报',
    `backdrop_url` VARCHAR(255) COMMENT '横向剧照',
    `summary` TEXT COMMENT '长简介',
    `rating` DECIMAL(3,1) COMMENT '综合评分',
    `trailer_url` VARCHAR(255) COMMENT '预宣推链接',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` TINYINT DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_type_status` (`type`, `status`),
    KEY `idx_release_date` (`release_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='泛影视内容主表';

CREATE TABLE IF NOT EXISTS `media_category` (
    `id` INT NOT NULL AUTO_INCREMENT,
    `name` VARCHAR(50) NOT NULL,
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `media_tag_relation` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `media_id` BIGINT NOT NULL,
    `category_id` INT NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_media_category` (`media_id`, `category_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `actor` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `name` VARCHAR(50) NOT NULL,
    `foreign_name` VARCHAR(100),
    `avatar_url` VARCHAR(255),
    `bio` TEXT,
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `media_cast` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `media_id` BIGINT NOT NULL,
    `actor_id` BIGINT NOT NULL,
    `role_type` TINYINT COMMENT '1:导演, 2:编剧, 3:领衔主演, 4:配角',
    `character_name` VARCHAR(100),
    PRIMARY KEY (`id`),
    KEY `idx_media_actor` (`media_id`, `actor_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `media_episode` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `media_id` BIGINT NOT NULL,
    `season_num` INT DEFAULT 1,
    `episode_num` INT NOT NULL,
    `title` VARCHAR(100),
    `air_date` DATE,
    PRIMARY KEY (`id`),
    KEY `idx_media_season` (`media_id`, `season_num`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 2.3 实体影院票务 --
CREATE TABLE IF NOT EXISTS `cinema` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `name` VARCHAR(100) NOT NULL,
    `brand_id` INT,
    `province` VARCHAR(50),
    `city` VARCHAR(50),
    `district` VARCHAR(50),
    `address` VARCHAR(255),
    `longitude` DECIMAL(10,6),
    `latitude` DECIMAL(10,6),
    `phone` VARCHAR(50),
    `services_json` JSON,
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `hall` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `cinema_id` BIGINT NOT NULL,
    `name` VARCHAR(50),
    `hall_type` VARCHAR(50),
    `seat_count` INT,
    `rows_count` INT,
    `cols_count` INT,
    PRIMARY KEY (`id`),
    KEY `idx_cinema` (`cinema_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `seat_template` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `hall_id` BIGINT NOT NULL,
    `row_num` INT NOT NULL,
    `col_num` INT NOT NULL,
    `status` TINYINT DEFAULT 1 COMMENT '1:可用 0:走道 -1:损坏',
    `type` TINYINT DEFAULT 0 COMMENT '0:普通 1:情侣 2:VIP',
    PRIMARY KEY (`id`),
    KEY `idx_hall` (`hall_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `schedule` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `media_id` BIGINT NOT NULL,
    `cinema_id` BIGINT NOT NULL,
    `hall_id` BIGINT NOT NULL,
    `show_time` DATETIME NOT NULL,
    `end_time` DATETIME NOT NULL,
    `price` DECIMAL(10,2) NOT NULL,
    `status` TINYINT DEFAULT 1,
    PRIMARY KEY (`id`),
    KEY `idx_cinema_movie_time` (`cinema_id`, `media_id`, `show_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 2.4 订单 --
CREATE TABLE IF NOT EXISTS `order_info` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `order_no` VARCHAR(50) NOT NULL,
    `user_id` BIGINT NOT NULL,
    `schedule_id` BIGINT NOT NULL,
    `total_price` DECIMAL(10,2),
    `pay_price` DECIMAL(10,2),
    `status` TINYINT DEFAULT 0 COMMENT '0:待支付 1:确认 2:取消 3:退款',
    `expire_time` DATETIME,
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `pay_time` DATETIME,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_order_no` (`order_no`),
    KEY `idx_user_status` (`user_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `order_ticket` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `order_info_id` BIGINT NOT NULL,
    `seat_id` BIGINT,
    `seat_row` INT,
    `seat_col` INT,
    `ticket_code` VARCHAR(20),
    PRIMARY KEY (`id`),
    KEY `idx_order` (`order_info_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `payment_record` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `order_no` VARCHAR(50) NOT NULL,
    `transaction_id` VARCHAR(100),
    `pay_channel` VARCHAR(20),
    `pay_amount` DECIMAL(10,2),
    `status` TINYINT,
    `notify_time` DATETIME,
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 2.5 互动社区与平台运营 --
CREATE TABLE IF NOT EXISTS `comment` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `media_id` BIGINT NOT NULL,
    `user_id` BIGINT NOT NULL,
    `content` TEXT,
    `rating` DECIMAL(3,1),
    `like_count` INT DEFAULT 0,
    `audit_status` TINYINT DEFAULT 0,
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_media` (`media_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `banner` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `title` VARCHAR(100),
    `image_url` VARCHAR(255),
    `link_url` VARCHAR(255),
    `position_type` VARCHAR(50),
    `sort_order` INT DEFAULT 0,
    `status` TINYINT DEFAULT 1,
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 2.6 AI设施 --
CREATE TABLE IF NOT EXISTS `ai_prompt_template` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `scene_code` VARCHAR(50) NOT NULL,
    `template_content` TEXT NOT NULL,
    `model_name` VARCHAR(50),
    `temperature` DECIMAL(3,2),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_scene_code` (`scene_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `ai_generate_record` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `admin_user_id` BIGINT,
    `scene_code` VARCHAR(50),
    `original_content` TEXT,
    `generated_content` TEXT,
    `tokens_used` INT,
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
