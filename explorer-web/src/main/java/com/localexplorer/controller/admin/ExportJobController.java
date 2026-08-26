package com.localexplorer.controller.admin;

import com.localexplorer.annotation.OperationLog;
import com.localexplorer.dto.CreateExportJobDTO;
import com.localexplorer.dto.ExportJobPageQueryDTO;
import com.localexplorer.result.PageResult;
import com.localexplorer.result.Result;
import com.localexplorer.service.ExportJobService;
import com.localexplorer.storage.ExportDownload;
import com.localexplorer.vo.ExportJobCreatedVO;
import com.localexplorer.vo.ExportJobStatsVO;
import com.localexplorer.vo.ExportJobVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.file.Files;

@RestController
@RequestMapping("/admin/export-jobs")
@Validated
public class ExportJobController {

    @Autowired private ExportJobService exportJobService;

    @PostMapping
    @OperationLog("创建异步导出任务")
    public Result<ExportJobCreatedVO> create(@Valid @RequestBody CreateExportJobDTO dto) {
        return Result.success(exportJobService.create(dto));
    }

    @GetMapping("/page")
    public Result<PageResult> page(@Valid ExportJobPageQueryDTO dto) {
        return Result.success(exportJobService.pageQuery(dto));
    }

    @GetMapping("/{jobId}")
    public Result<ExportJobVO> detail(@PathVariable String jobId) {
        return Result.success(exportJobService.get(jobId));
    }

    @PostMapping("/{jobId}/cancel")
    @OperationLog("取消导出任务")
    public Result<Void> cancel(@PathVariable String jobId) {
        exportJobService.cancel(jobId);
        return Result.success();
    }

    @PostMapping("/{jobId}/retry")
    @OperationLog("重试导出任务")
    public Result<Void> retry(@PathVariable String jobId) {
        exportJobService.retry(jobId);
        return Result.success();
    }

    @GetMapping("/{jobId}/download")
    @OperationLog("下载导出文件")
    public void download(@PathVariable String jobId, HttpServletResponse response) throws IOException {
        ExportDownload download = exportJobService.prepareDownload(jobId);
        String fileName = sanitizeFileName(download.getFileName());
        String encoded = URLEncoder.encode(fileName, "UTF-8").replace("+", "%20");
        String fallback = fileName.toLowerCase().endsWith(".xlsx") ? "export.xlsx" : "export.csv";
        response.setContentType(download.getContentType());
        response.setContentLengthLong(download.getSize());
        response.setHeader("Content-Disposition", "attachment; filename=\"" + fallback
                + "\"; filename*=UTF-8''" + encoded);
        Files.copy(download.getFile(), response.getOutputStream());
        response.flushBuffer();
    }

    @GetMapping("/stats")
    public Result<ExportJobStatsVO> stats() {
        return Result.success(exportJobService.stats());
    }

    private String sanitizeFileName(String fileName) {
        if (fileName == null || fileName.trim().isEmpty()) return "export.csv";
        String value = fileName.replaceAll("[\\r\\n\\\\/:*?\"<>|]", "_");
        return value.length() <= 180 ? value : value.substring(0, 180);
    }
}
