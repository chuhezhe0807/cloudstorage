package com.chuhezhe.common.context;

/**
 * 租户/用户线程上下文，通过 ThreadLocal 传递，请求结束由 TenantContextFilter 清理。
 */
public class TenantContext {

    private static final ThreadLocal<Long> TENANT_HOLDER = new ThreadLocal<>();
    private static final ThreadLocal<Long> USER_HOLDER = new ThreadLocal<>();

    private TenantContext() {
    }

    public static void setTenantId(Long tenantId) {
        TENANT_HOLDER.set(tenantId);
    }

    public static Long getTenantId() {
        return TENANT_HOLDER.get();
    }

    public static void setUserId(Long userId) {
        USER_HOLDER.set(userId);
    }

    public static Long getUserId() {
        return USER_HOLDER.get();
    }

    /** 请求结束后清理，防止内存泄漏 */
    public static void clear() {
        TENANT_HOLDER.remove();
        USER_HOLDER.remove();
    }
}
