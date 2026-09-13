package com.chuhezhe.core.storage.config;

import io.minio.MinioClient;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MinIO 客户端配置。
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "minio")
public class MinioConfig {

    private String endpoint;
    /** 对外暴露的访问地址，用于生成浏览器可达的预签名 URL；为空时回退到 endpoint */
    private String publicEndpoint;
    /** 固定区域，避免生成预签名 URL 时向服务端探测 region（容器内可能访问不到对外地址） */
    private String region = "us-east-1";
    private String accessKey;
    private String secretKey;
    private String bucket;

    @Bean
    public MinioClient minioClient() {
        return MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .region(region)
                .build();
    }
}
