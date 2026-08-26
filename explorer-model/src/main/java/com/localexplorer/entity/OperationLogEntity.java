package com.localexplorer.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 操作日志（持久化到数据库）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OperationLogEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    /** 操作描述 */
    private String description;

    /** 操作人ID */
    private Long operatorId;

    /** 请求方法 */
    private String requestMethod;

    /** 请求路径 */
    private String requestUri;

    /** 客户端IP */
    private String clientIp;

    /** 耗时(ms) */
    private Long costTime;

    private LocalDateTime createTime;
}
