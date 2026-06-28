package com.chuhezhe.common.context;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;

@Slf4j
public class TenantContextFilter implements Filter {

    private static final String HEADER_TENANT_ID = "X-Tenant-Id";
    private static final String HEADER_USER_ID = "X-User-Id";

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        String tenantIdStr = httpRequest.getHeader(HEADER_TENANT_ID);
        String userIdStr = httpRequest.getHeader(HEADER_USER_ID);

        if (tenantIdStr != null && !tenantIdStr.isBlank()) {
            TenantContext.setTenantId(Long.parseLong(tenantIdStr));
        }
        if (userIdStr != null && !userIdStr.isBlank()) {
            TenantContext.setUserId(Long.parseLong(userIdStr));
        }

        try {
            chain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }
}
