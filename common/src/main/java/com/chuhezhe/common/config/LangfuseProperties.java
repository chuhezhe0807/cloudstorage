package com.chuhezhe.common.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "langfuse")
public class LangfuseProperties {

    private String baseUrl = "http://localhost:3000";
    private String publicKey;
    private String secretKey;
    private boolean enabled = true;
}
