package com.jelly.cinema.common.config;

import com.jelly.cinema.common.config.property.CosProperties;
import com.qcloud.cos.COSClient;
import com.qcloud.cos.ClientConfig;
import com.qcloud.cos.auth.BasicCOSCredentials;
import com.qcloud.cos.auth.COSCredentials;
import com.qcloud.cos.region.Region;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CosConfig {

    @Bean
    public COSClient cosClient(CosProperties properties) {
        if (properties.getSecretId() == null || properties.getSecretId().isEmpty()) {
            return null;
        }
        COSCredentials cred = new BasicCOSCredentials(properties.getSecretId(), properties.getSecretKey());
        ClientConfig clientConfig = new ClientConfig(new Region(properties.getRegion()));
        return new COSClient(cred, clientConfig);
    }
}
