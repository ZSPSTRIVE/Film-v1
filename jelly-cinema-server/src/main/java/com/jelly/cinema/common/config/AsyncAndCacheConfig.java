package com.jelly.cinema.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * 异步处理与缓存优化配置
 * 
 * 目的: 
 * 1. 为 TVBox 同步提供异步执行能力，避免阻塞搜索请求
 * 2. 配置线程池参数，确保高并发下的稳定性
 */
@Configuration
@EnableAsync
public class AsyncAndCacheConfig {

    /**
     * 配置异步线程池 - 用于 TVBox 预同步
     * 
     * 场景: 标准搜索时调用 tvboxMediaIngestService.syncByKeywordAsync()
     * 效果: 异步在后台执行，不阻塞搜索请求，搜索立即返回本地库结果
     */
    @Bean(name = "tvboxAsyncExecutor")
    public Executor tvboxAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        
        // 核心线程数：2 - 处理常规异步任务
        executor.setCorePoolSize(2);
        
        // 最大线程数：8 - 高并发时扩展
        executor.setMaxPoolSize(8);
        
        // 队列容量：50 - 缓冲待处理任务
        executor.setQueueCapacity(50);
        
        // 线程名前缀：便于日志追踪
        executor.setThreadNamePrefix("tvbox-ingest-");
        
        // 等待时间：60 秒 - 关闭前等待现有任务完成
        executor.setAwaitTerminationSeconds(60);
        
        // 拒绝策略：使用调用线程执行（CallerRunsPolicy）
        // 当队列满且线程池满时，使用提交任务的线程执行
        // 这样可以避免丢弃任务，但会暂时阻塞提交者
        executor.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());
        
        executor.initialize();
        return executor;
    }

    /**
     * 配置异步线程池 - 用于通用异步操作
     * 
     * 场景: AI 搜索、文本生成等耗时操作
     * 效果: 与 TVBox 同步共享，但使用独立池以避免竞争
     */
    @Bean(name = "aiAsyncExecutor")
    public Executor aiAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(3);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("ai-task-");
        executor.setAwaitTerminationSeconds(60);
        executor.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}
