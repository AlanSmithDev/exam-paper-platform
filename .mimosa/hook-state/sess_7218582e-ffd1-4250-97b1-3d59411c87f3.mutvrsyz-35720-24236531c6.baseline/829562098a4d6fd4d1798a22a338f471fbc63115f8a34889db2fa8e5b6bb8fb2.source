package com.examforge.gateway;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** 网关资源公开读规则单测：仅列表/数字详情匿名，其余资源子路径（篮/下载/创作者/申诉/管理）须登录 */
class AuthGlobalFilterPublicReadTest {

    @Test
    void 资源公开读_列表数字详情与公开榜() {
        assertTrue(AuthGlobalFilter.resourcePublicRead("/api/v1/resources"));
        assertTrue(AuthGlobalFilter.resourcePublicRead("/api/v1/resources/5"));
        assertTrue(AuthGlobalFilter.resourcePublicRead("/api/v1/resources/123456"));
        assertTrue(AuthGlobalFilter.resourcePublicRead("/api/v1/resources/creator/board"));
    }

    @Test
    void 资源需登录路径_一概不匿名() {
        assertFalse(AuthGlobalFilter.resourcePublicRead("/api/v1/resources/basket"));
        assertFalse(AuthGlobalFilter.resourcePublicRead("/api/v1/resources/5/basket"));
        assertFalse(AuthGlobalFilter.resourcePublicRead("/api/v1/resources/5/download"));
        assertFalse(AuthGlobalFilter.resourcePublicRead("/api/v1/resources/downloads/mine"));
        assertFalse(AuthGlobalFilter.resourcePublicRead("/api/v1/resources/creator/upload"));
        assertFalse(AuthGlobalFilter.resourcePublicRead("/api/v1/resources/creator/earnings"));
        assertFalse(AuthGlobalFilter.resourcePublicRead("/api/v1/resources/admin/items"));
        assertFalse(AuthGlobalFilter.resourcePublicRead("/api/v1/resources/appeals"));
    }

    @Test
    void 相似路径_不误伤() {
        assertFalse(AuthGlobalFilter.resourcePublicRead("/api/v1/resourcesx"));
        assertFalse(AuthGlobalFilter.resourcePublicRead("/api/v1/resources/"));
        assertFalse(AuthGlobalFilter.resourcePublicRead("/api/v1/resources/5/"));
        assertFalse(AuthGlobalFilter.resourcePublicRead("/api/v2/resources"));
    }
}
