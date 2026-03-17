/**
 * 数据库和缓存服务模块
 * 提供MySQL连接池和Redis客户端
 */
const mysql = require('mysql2/promise');
const Redis = require('ioredis');
require('dotenv').config();

// MySQL连接池
let pool = null;

const DEFAULT_TVBOX_SOURCES = [
    { source_name: '量子资源', api_url: 'https://cj.lziapi.com/api.php/provide/vod/', api_type: 'json', priority: 1 },
    { source_name: '非凡资源', api_url: 'https://cj.ffzyapi.com/api.php/provide/vod/', api_type: 'json', priority: 2 },
    { source_name: '红牛资源', api_url: 'https://www.hongniuzy2.com/api.php/provide/vod/', api_type: 'json', priority: 3 },
    { source_name: '新浪资源', api_url: 'https://api.xinlangapi.com/xinlangapi.php/provide/vod/', api_type: 'json', priority: 5 },
    { source_name: '光速资源', api_url: 'https://api.guangsuapi.com/api.php/provide/vod/', api_type: 'json', priority: 6 },
];

function getPool() {
    if (!pool) {
        pool = mysql.createPool({
            host: process.env.DB_HOST || 'localhost',
            port: parseInt(process.env.DB_PORT) || 3306,
            user: process.env.DB_USER || 'root',
            password: process.env.DB_PASSWORD || '123456',
            database: process.env.DB_NAME || 'jelly_cinema',
            waitForConnections: true,
            connectionLimit: 10,
            queueLimit: 0,
            enableKeepAlive: true,
            keepAliveInitialDelay: 0
        });
        console.log('MySQL connection pool created');
    }
    return pool;
}

// Redis客户端
let redis = null;

function getRedis() {
    if (!redis) {
        redis = new Redis({
            host: process.env.REDIS_HOST || 'localhost',
            port: parseInt(process.env.REDIS_PORT) || 6379,
            password: process.env.REDIS_PASSWORD || undefined,
            retryDelayOnFailover: 1000,
            maxRetriesPerRequest: 3,
            lazyConnect: true
        });

        redis.on('connect', () => {
            console.log('Redis connected');
        });

        redis.on('error', (err) => {
            console.error('Redis error:', err.message);
        });
    }
    return redis;
}

// Redis缓存包装函数
async function cacheGet(key) {
    try {
        const redis = getRedis();
        const data = await redis.get(key);
        return data ? JSON.parse(data) : null;
    } catch (err) {
        console.error('Redis get error:', err.message);
        return null;
    }
}

async function cacheSet(key, value, ttlSeconds = 300) {
    try {
        const redis = getRedis();
        await redis.setex(key, ttlSeconds, JSON.stringify(value));
        return true;
    } catch (err) {
        console.error('Redis set error:', err.message);
        return false;
    }
}

async function cacheDel(key) {
    try {
        const redis = getRedis();
        await redis.del(key);
        return true;
    } catch (err) {
        console.error('Redis del error:', err.message);
        return false;
    }
}

// 数据库查询包装函数
async function query(sql, params = []) {
    try {
        const pool = getPool();
        const [rows] = await pool.execute(sql, params);
        return rows;
    } catch (err) {
        console.error('Database query error:', err.message);
        throw err;
    }
}

async function queryOne(sql, params = []) {
    const rows = await query(sql, params);
    return rows.length > 0 ? rows[0] : null;
}

async function ensureTvboxSourceTable() {
    const pool = getPool();
    await pool.execute(`
        CREATE TABLE IF NOT EXISTS t_tvbox_source (
            id BIGINT NOT NULL AUTO_INCREMENT,
            source_name VARCHAR(100) NOT NULL,
            api_url VARCHAR(500) NOT NULL,
            api_type VARCHAR(20) NOT NULL DEFAULT 'json',
            priority INT NOT NULL DEFAULT 100,
            enabled TINYINT NOT NULL DEFAULT 1,
            create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
            update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
            PRIMARY KEY (id),
            UNIQUE KEY uk_tvbox_source_api_url (api_url),
            KEY idx_tvbox_source_enabled_priority (enabled, priority)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
    `);

    const [rows] = await pool.execute('SELECT COUNT(*) AS cnt FROM t_tvbox_source');
    const count = rows?.[0]?.cnt ?? 0;
    if (count > 0) {
        return;
    }

    for (const source of DEFAULT_TVBOX_SOURCES) {
        await pool.execute(
            `INSERT INTO t_tvbox_source (source_name, api_url, api_type, priority, enabled)
             VALUES (?, ?, ?, ?, 1)`,
            [source.source_name, source.api_url, source.api_type, source.priority]
        );
    }
    console.log('Initialized t_tvbox_source with default sources');
}

// 初始化连接
async function initConnections() {
    try {
        // 测试MySQL连接
        const pool = getPool();
        const conn = await pool.getConnection();
        console.log('MySQL connection test successful');
        conn.release();

        // 初始化 TVBox 源配置表
        await ensureTvboxSourceTable();

        // 测试Redis连接
        const redis = getRedis();
        await redis.ping();
        console.log('Redis connection test successful');

        return true;
    } catch (err) {
        console.error('Connection init failed:', err.message);
        return false;
    }
}

// 关闭连接
async function closeConnections() {
    if (pool) {
        await pool.end();
        pool = null;
    }
    if (redis) {
        redis.disconnect();
        redis = null;
    }
}

module.exports = {
    getPool,
    getRedis,
    query,
    queryOne,
    ensureTvboxSourceTable,
    cacheGet,
    cacheSet,
    cacheDel,
    initConnections,
    closeConnections
};
