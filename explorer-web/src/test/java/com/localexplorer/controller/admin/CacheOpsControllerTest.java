package com.localexplorer.controller.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.localexplorer.cache.CacheSnapshot;
import com.localexplorer.cache.CacheWarmupService;
import com.localexplorer.cache.HotCacheDomain;
import com.localexplorer.cache.HotReadCacheService;
import com.localexplorer.context.BaseContext;
import com.localexplorer.entity.Employee;
import com.localexplorer.filter.RequestTracingFilter;
import com.localexplorer.handler.ApiErrorResponseWriter;
import com.localexplorer.interceptor.AdminAuthorizationInterceptor;
import com.localexplorer.mapper.EmployeeMapper;
import com.localexplorer.service.AdminPermissionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CacheOpsControllerTest {
    private HotReadCacheService cache;
    private CacheWarmupService warmupService;
    private EmployeeMapper employeeMapper;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        cache = mock(HotReadCacheService.class);
        warmupService = mock(CacheWarmupService.class);
        employeeMapper = mock(EmployeeMapper.class);
        when(cache.snapshot()).thenReturn(CacheSnapshot.builder().l1Entries(8).l1Hits(12).build());

        CacheOpsController controller = new CacheOpsController(cache, warmupService);
        AdminAuthorizationInterceptor authorization = new AdminAuthorizationInterceptor(
                new AdminPermissionService(employeeMapper),
                new ApiErrorResponseWriter(new ObjectMapper()));
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .addInterceptors(authorization)
                .addFilter(new RequestTracingFilter())
                .build();
    }

    @AfterEach
    void tearDown() {
        BaseContext.removeCurrentId();
    }

    @Test
    void adminCanInspectAndInvalidateCache() throws Exception {
        BaseContext.setCurrentId(1L);
        when(employeeMapper.getById(1L)).thenReturn(Employee.builder().id(1L).role("ADMIN").build());

        mockMvc.perform(get("/admin/cache/stats").header("X-Request-Id", "cache-admin-test"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1))
                .andExpect(jsonPath("$.data.cache.l1Entries").value(8));
        mockMvc.perform(post("/admin/cache/invalidate/item-list"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1));

        verify(cache).invalidateAll(HotCacheDomain.ITEM_LIST);
    }

    @Test
    void staffReceivesStructuredForbiddenResponseWithRequestId() throws Exception {
        BaseContext.setCurrentId(2L);
        when(employeeMapper.getById(2L)).thenReturn(Employee.builder().id(2L).role("STAFF").build());

        mockMvc.perform(get("/admin/cache/stats").header("X-Request-Id", "cache-staff-test"))
                .andExpect(status().isForbidden())
                .andExpect(header().string("X-Request-Id", "cache-staff-test"))
                .andExpect(jsonPath("$.code").value(40300))
                .andExpect(jsonPath("$.requestId").value("cache-staff-test"));
    }

    @Test
    void warmupReturnsImmediatelyAndDelegatesToAsyncService() throws Exception {
        BaseContext.setCurrentId(1L);
        when(employeeMapper.getById(1L)).thenReturn(Employee.builder().id(1L).role("ADMIN").build());

        mockMvc.perform(post("/admin/cache/warmup"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1));

        verify(warmupService).warmupHomepage();
    }
}
