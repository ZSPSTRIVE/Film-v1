CREATE TABLE IF NOT EXISTS `user` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `username` VARCHAR(50) NOT NULL,
    `password` VARCHAR(100) NOT NULL,
    `phone` VARCHAR(20) DEFAULT NULL,
    `email` VARCHAR(100) DEFAULT NULL,
    `role` VARCHAR(20) NOT NULL DEFAULT 'USER',
    `status` TINYINT NOT NULL DEFAULT 1,
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `media` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `title` VARCHAR(100) NOT NULL,
    `original_title` VARCHAR(100) DEFAULT NULL,
    `type` TINYINT NOT NULL,
    `status` TINYINT NOT NULL DEFAULT 0,
    `release_date` DATE DEFAULT NULL,
    `duration` INT DEFAULT NULL,
    `cover_url` VARCHAR(255) DEFAULT NULL,
    `backdrop_url` VARCHAR(255) DEFAULT NULL,
    `summary` TEXT,
    `rating` DECIMAL(3,1) DEFAULT NULL,
    `trailer_url` VARCHAR(255) DEFAULT NULL,
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_media_type_status` (`type`, `status`),
    KEY `idx_media_release_date` (`release_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `actor` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `name` VARCHAR(50) NOT NULL,
    `foreign_name` VARCHAR(100) DEFAULT NULL,
    `avatar_url` VARCHAR(255) DEFAULT NULL,
    `bio` TEXT,
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `media_cast` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `media_id` BIGINT NOT NULL,
    `actor_id` BIGINT NOT NULL,
    `role_type` TINYINT DEFAULT NULL,
    `character_name` VARCHAR(100) DEFAULT NULL,
    PRIMARY KEY (`id`),
    KEY `idx_media_cast_media` (`media_id`),
    KEY `idx_media_cast_actor` (`actor_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `comment` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `media_id` BIGINT NOT NULL,
    `user_id` BIGINT NOT NULL,
    `content` TEXT,
    `rating` DECIMAL(3,1) DEFAULT NULL,
    `like_count` INT NOT NULL DEFAULT 0,
    `audit_status` TINYINT NOT NULL DEFAULT 0,
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_comment_media` (`media_id`),
    KEY `idx_comment_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `search_keyword_log` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `keyword` VARCHAR(100) NOT NULL,
    `user_id` BIGINT DEFAULT NULL,
    `search_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_search_keyword_time` (`search_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `ai_prompt_template` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `scene_code` VARCHAR(50) NOT NULL,
    `template_content` TEXT NOT NULL,
    `model_name` VARCHAR(50) DEFAULT NULL,
    `temperature` DECIMAL(3,2) DEFAULT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_ai_prompt_scene_code` (`scene_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `ai_generate_record` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `admin_user_id` BIGINT DEFAULT NULL,
    `scene_code` VARCHAR(50) DEFAULT NULL,
    `original_content` TEXT,
    `generated_content` TEXT,
    `tokens_used` INT DEFAULT NULL,
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_ai_generate_scene_code` (`scene_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `media_play_source` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `media_id` BIGINT NOT NULL,
    `source_type` VARCHAR(32) NOT NULL,
    `provider_name` VARCHAR(100) NOT NULL,
    `title` VARCHAR(255) NOT NULL,
    `url` VARCHAR(500) NOT NULL,
    `region` VARCHAR(32) DEFAULT NULL,
    `quality` VARCHAR(32) DEFAULT NULL,
    `is_free` TINYINT NOT NULL DEFAULT 0,
    `sort_order` INT NOT NULL DEFAULT 0,
    `deleted` TINYINT NOT NULL DEFAULT 0,
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_media_play_source_unique` (`media_id`, `source_type`, `provider_name`, `url`),
    KEY `idx_media_play_source_media` (`media_id`, `deleted`, `sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `media_external_resource` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `media_id` BIGINT DEFAULT NULL,
    `provider_name` VARCHAR(100) NOT NULL,
    `external_item_id` VARCHAR(191) NOT NULL,
    `raw_title` VARCHAR(255) NOT NULL,
    `clean_title` VARCHAR(255) NOT NULL,
    `release_year` INT DEFAULT NULL,
    `type` TINYINT DEFAULT NULL,
    `rating` DECIMAL(3,1) DEFAULT NULL,
    `region` VARCHAR(100) DEFAULT NULL,
    `director` VARCHAR(255) DEFAULT NULL,
    `actors` TEXT,
    `description` TEXT,
    `cover_url` VARCHAR(500) DEFAULT NULL,
    `source_key` VARCHAR(100) DEFAULT NULL,
    `raw_payload_json` LONGTEXT,
    `match_confidence` DECIMAL(5,4) DEFAULT NULL,
    `sync_status` VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    `last_synced_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_media_external_provider_item` (`provider_name`, `external_item_id`),
    KEY `idx_media_external_media` (`media_id`, `deleted`, `last_synced_at`),
    KEY `idx_media_external_title` (`clean_title`, `release_year`, `deleted`),
    KEY `idx_media_external_status` (`sync_status`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
