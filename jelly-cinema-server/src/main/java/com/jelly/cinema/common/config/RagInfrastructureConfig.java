package com.jelly.cinema.common.config;

import com.jelly.cinema.common.config.property.RagProperties;
import com.zaxxer.hikari.HikariDataSource;
import io.milvus.client.MilvusServiceClient;
import io.milvus.param.ConnectParam;
import io.milvus.v2.client.ConnectConfig;
import io.milvus.v2.client.MilvusClientV2;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.util.concurrent.TimeUnit;

@Configuration
public class RagInfrastructureConfig {

    @Bean(destroyMethod = "close")
    @ConditionalOnProperty(prefix = "rag", name = "enable", havingValue = "true", matchIfMissing = true)
    public RagJdbcAccess ragJdbcAccess(RagProperties ragProperties) {
        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setDriverClassName(ragProperties.getPostgres().getDriverClassName());
        dataSource.setJdbcUrl(ragProperties.getPostgres().getUrl());
        dataSource.setUsername(ragProperties.getPostgres().getUsername());
        dataSource.setPassword(ragProperties.getPostgres().getPassword());
        dataSource.setPoolName("rag-postgres-pool");
        dataSource.setMaximumPoolSize(6);
        dataSource.setMinimumIdle(1);
        return new RagJdbcAccess(dataSource);
    }

    @Bean(name = "ragJdbcTemplate")
    @ConditionalOnProperty(prefix = "rag", name = "enable", havingValue = "true", matchIfMissing = true)
    public JdbcTemplate ragJdbcTemplate(RagJdbcAccess ragJdbcAccess) {
        return ragJdbcAccess.getJdbcTemplate();
    }

    @Bean(name = "ragNamedJdbcTemplate")
    @ConditionalOnProperty(prefix = "rag", name = "enable", havingValue = "true", matchIfMissing = true)
    public NamedParameterJdbcTemplate ragNamedJdbcTemplate(RagJdbcAccess ragJdbcAccess) {
        return ragJdbcAccess.getNamedParameterJdbcTemplate();
    }

    @Bean(destroyMethod = "close")
    @ConditionalOnProperty(prefix = "rag", name = "enable", havingValue = "true", matchIfMissing = true)
    public MilvusClientV2 milvusClient(RagProperties ragProperties) {
        ConnectConfig.ConnectConfigBuilder builder = ConnectConfig.builder()
                .uri("http://" + ragProperties.getMilvus().getHost() + ":" + ragProperties.getMilvus().getPort())
                .dbName(ragProperties.getMilvus().getDatabaseName())
                .connectTimeoutMs(ragProperties.getMilvus().getConnectTimeoutMs())
                .rpcDeadlineMs(ragProperties.getMilvus().getRpcDeadlineMs());

        if (ragProperties.getMilvus().getUsername() != null && !ragProperties.getMilvus().getUsername().isBlank()
                && ragProperties.getMilvus().getPassword() != null && !ragProperties.getMilvus().getPassword().isBlank()) {
            builder.username(ragProperties.getMilvus().getUsername())
                    .password(ragProperties.getMilvus().getPassword());
        }

        return new MilvusClientV2(builder.build());
    }

    @Bean(destroyMethod = "close")
    @ConditionalOnProperty(prefix = "rag", name = "enable", havingValue = "true", matchIfMissing = true)
    public MilvusServiceClient milvusServiceClient(RagProperties ragProperties) {
        ConnectParam.Builder builder = ConnectParam.newBuilder()
                .withHost(ragProperties.getMilvus().getHost())
                .withPort(ragProperties.getMilvus().getPort())
                .withConnectTimeout(ragProperties.getMilvus().getConnectTimeoutMs(), TimeUnit.MILLISECONDS)
                .withKeepAliveTimeout(ragProperties.getMilvus().getRpcDeadlineMs(), TimeUnit.MILLISECONDS);

        if (ragProperties.getMilvus().getUsername() != null && !ragProperties.getMilvus().getUsername().isBlank()
                && ragProperties.getMilvus().getPassword() != null && !ragProperties.getMilvus().getPassword().isBlank()) {
            builder.withAuthorization(ragProperties.getMilvus().getUsername(), ragProperties.getMilvus().getPassword());
        }

        return new MilvusServiceClient(builder.build());
    }
}
