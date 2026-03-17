package com.jelly.cinema.common.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.DatabasePopulatorUtils;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
@Component
@ConditionalOnBean(RagJdbcAccess.class)
public class RagSchemaInitializer {

    private final RagJdbcAccess ragJdbcAccess;

    public RagSchemaInitializer(RagJdbcAccess ragJdbcAccess) {
        this.ragJdbcAccess = ragJdbcAccess;
    }

    @PostConstruct
    public void initialize() {
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator(new ClassPathResource("rag-schema.sql"));
        populator.setContinueOnError(false);
        DatabasePopulatorUtils.execute(populator, ragJdbcAccess.getDataSource());
    }
}
