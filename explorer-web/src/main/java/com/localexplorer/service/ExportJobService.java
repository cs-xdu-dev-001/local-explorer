package com.localexplorer.service;

import com.localexplorer.dto.CreateExportJobDTO;
import com.localexplorer.dto.ExportJobPageQueryDTO;
import com.localexplorer.result.PageResult;
import com.localexplorer.storage.ExportDownload;
import com.localexplorer.vo.ExportJobCreatedVO;
import com.localexplorer.vo.ExportJobStatsVO;
import com.localexplorer.vo.ExportJobVO;

public interface ExportJobService {
    ExportJobCreatedVO create(CreateExportJobDTO dto);
    PageResult pageQuery(ExportJobPageQueryDTO dto);
    ExportJobVO get(String jobId);
    void cancel(String jobId);
    void retry(String jobId);
    ExportDownload prepareDownload(String jobId);
    ExportJobStatsVO stats();
}
