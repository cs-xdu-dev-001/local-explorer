package com.localexplorer.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OperationLogVO implements Serializable {

    private Long id;
    private String description;
    private Long operatorId;
    private String requestMethod;
    private String requestUri;
    private String clientIp;
    private Long costTime;
    private LocalDateTime createTime;

    /** 冗余：操作人姓名 */
    private String operatorName;
}
