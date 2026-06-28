package com.chuhezhe.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import java.net.InetAddress;
import java.net.UnknownHostException;

@Configuration
public class SnowflakeConfig {

    @Bean
    public long workerId(Environment environment) throws UnknownHostException {
        InetAddress address = InetAddress.getLocalHost();
        return Math.abs(address.getHostAddress().hashCode() % 31L);
    }
}
