package com.localexplorer.mapper;

import com.github.pagehelper.Page;
import com.localexplorer.dto.ExportJobPageQueryDTO;
import com.localexplorer.entity.ExportJob;
import com.localexplorer.vo.ExportJobStatsVO;
import com.localexplorer.vo.ExportJobVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface ExportJobMapper {

    void insert(ExportJob job);

    ExportJob getByJobId(String jobId);

    ExportJob getByOperatorAndRequestId(@Param("operatorId") Long operatorId,
                                        @Param("requestId") String requestId);

    Page<ExportJobVO> pageQuery(@Param("query") ExportJobPageQueryDTO query,
                                @Param("visibleOperatorId") Long visibleOperatorId);

    long countActiveByOperator(Long operatorId);

    List<String> findReadyJobIds(@Param("now") LocalDateTime now, @Param("limit") int limit);

    int claim(@Param("jobId") String jobId,
              @Param("leaseOwner") String leaseOwner,
              @Param("now") LocalDateTime now,
              @Param("leaseUntil") LocalDateTime leaseUntil);

    int heartbeat(@Param("jobId") String jobId,
                  @Param("leaseOwner") String leaseOwner,
                  @Param("now") LocalDateTime now,
                  @Param("leaseUntil") LocalDateTime leaseUntil);

    int updateProgress(@Param("jobId") String jobId,
                       @Param("leaseOwner") String leaseOwner,
                       @Param("processedRows") long processedRows,
                       @Param("progress") int progress,
                       @Param("now") LocalDateTime now,
                       @Param("leaseUntil") LocalDateTime leaseUntil);

    int markSucceeded(@Param("jobId") String jobId,
                      @Param("leaseOwner") String leaseOwner,
                      @Param("filePath") String filePath,
                      @Param("fileName") String fileName,
                      @Param("fileSize") long fileSize,
                      @Param("checksum") String checksum,
                      @Param("processedRows") long processedRows,
                      @Param("finishedAt") LocalDateTime finishedAt,
                      @Param("expiresAt") LocalDateTime expiresAt);

    int markRetry(@Param("jobId") String jobId,
                  @Param("leaseOwner") String leaseOwner,
                  @Param("retryCount") int retryCount,
                  @Param("nextRetryAt") LocalDateTime nextRetryAt,
                  @Param("errorCode") String errorCode,
                  @Param("errorMessage") String errorMessage,
                  @Param("now") LocalDateTime now);

    int markFailed(@Param("jobId") String jobId,
                   @Param("leaseOwner") String leaseOwner,
                   @Param("retryCount") int retryCount,
                   @Param("errorCode") String errorCode,
                   @Param("errorMessage") String errorMessage,
                   @Param("now") LocalDateTime now);

    int cancel(@Param("jobId") String jobId, @Param("now") LocalDateTime now);

    int retryFailed(@Param("jobId") String jobId, @Param("now") LocalDateTime now);

    List<String> findExpiredJobIds(@Param("now") LocalDateTime now, @Param("limit") int limit);

    List<String> findFilesToDelete(@Param("now") LocalDateTime now, @Param("limit") int limit);

    List<String> listReferencedFilePaths();

    int markExpired(@Param("jobId") String jobId, @Param("now") LocalDateTime now);

    int clearExpiredFile(@Param("jobId") String jobId, @Param("now") LocalDateTime now);

    ExportJobStatsVO stats(@Param("now") LocalDateTime now);

    long countFailed();

    long countExpiredLeases(@Param("now") LocalDateTime now);
}
