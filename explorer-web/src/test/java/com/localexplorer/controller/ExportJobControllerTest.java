package com.localexplorer.controller;

import com.localexplorer.constant.ErrorCode;
import com.localexplorer.controller.admin.ExportJobController;
import com.localexplorer.exception.BaseException;
import com.localexplorer.handler.GlobalExceptionHandler;
import com.localexplorer.filter.RequestTracingFilter;
import com.localexplorer.result.PageResult;
import com.localexplorer.service.ExportJobService;
import com.localexplorer.storage.ExportDownload;
import com.localexplorer.vo.ExportJobCreatedVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ExportJobControllerTest {

    private MockMvc mockMvc;
    @Mock private ExportJobService exportJobService;
    @TempDir Path tempDir;

    @BeforeEach
    void setUp() {
        ExportJobController controller = new ExportJobController();
        ReflectionTestUtils.setField(controller, "exportJobService", exportJobService);
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .addFilter(new RequestTracingFilter())
                .build();
    }

    @Test
    void createsAsynchronousJobAndReturnsIdempotencyIdentity() throws Exception {
        when(exportJobService.create(any())).thenReturn(ExportJobCreatedVO.builder()
                .jobId("job-1").requestId("req-1").status("PENDING").build());

        mockMvc.perform(post("/admin/export-jobs")
                        .contentType("application/json")
                        .content("{\"requestId\":\"req-1\",\"exportType\":\"ORDER\",\"fileFormat\":\"CSV\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.jobId").value("job-1"))
                .andExpect(jsonPath("$.data.requestId").value("req-1"))
                .andExpect(jsonPath("$.data.status").value("PENDING"));
    }

    @Test
    void rejectsInvalidTypeBeforeServiceCall() throws Exception {
        mockMvc.perform(post("/admin/export-jobs")
                        .contentType("application/json")
                        .content("{\"requestId\":\"req-1\",\"exportType\":\"PASSWORD\",\"fileFormat\":\"CSV\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.INVALID_REQUEST.getCode()));
    }

    @Test
    void pageDelegatesFiltersToService() throws Exception {
        when(exportJobService.pageQuery(any())).thenReturn(new PageResult(0, Collections.emptyList()));

        mockMvc.perform(get("/admin/export-jobs/page?status=RUNNING&exportType=ORDER&page=2&pageSize=20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0));

        verify(exportJobService).pageQuery(any());
    }

    @Test
    void downloadUsesUtf8FilenameWithoutHeaderInjection() throws Exception {
        Path file = tempDir.resolve("export.csv");
        Files.write(file, "name\n林夏\n".getBytes(StandardCharsets.UTF_8));
        when(exportJobService.prepareDownload("job-1")).thenReturn(new ExportDownload(
                file, "订单导出_20260824.csv\r\nX-Bad: yes", "text/csv;charset=UTF-8", Files.size(file)));

        mockMvc.perform(get("/admin/export-jobs/job-1/download"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "text/csv;charset=UTF-8"))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("filename*=UTF-8''")))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("\r"))))
                .andExpect(content().bytes(Files.readAllBytes(file)));
    }

    @Test
    void returnsStableConflictForCancelRace() throws Exception {
        doThrow(new BaseException(ErrorCode.EXPORT_JOB_CONFLICT, "当前任务状态不能取消"))
                .when(exportJobService).cancel("job-1");

        mockMvc.perform(post("/admin/export-jobs/job-1/cancel"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(ErrorCode.EXPORT_JOB_CONFLICT.getCode()));
    }

    @Test
    void crossEmployeeDownloadReturnsForbiddenWithRequestId() throws Exception {
        doThrow(new BaseException(ErrorCode.FORBIDDEN, "不能访问其他员工的导出任务"))
                .when(exportJobService).prepareDownload("job-other");

        mockMvc.perform(get("/admin/export-jobs/job-other/download")
                        .header("X-Request-Id", "export-forbidden-test"))
                .andExpect(status().isForbidden())
                .andExpect(header().string("X-Request-Id", "export-forbidden-test"))
                .andExpect(jsonPath("$.code").value(ErrorCode.FORBIDDEN.getCode()))
                .andExpect(jsonPath("$.requestId").value("export-forbidden-test"));
    }

    @Test
    void unfinishedDownloadReturnsStableBusinessError() throws Exception {
        doThrow(new BaseException(ErrorCode.EXPORT_JOB_NOT_READY, "导出文件尚不可下载"))
                .when(exportJobService).prepareDownload("job-running");

        mockMvc.perform(get("/admin/export-jobs/job-running/download"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(ErrorCode.EXPORT_JOB_NOT_READY.getCode()));
    }
}
