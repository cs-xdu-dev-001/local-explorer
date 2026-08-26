package com.localexplorer.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExportJobStatsVO implements Serializable {
    private Long pending;
    private Long running;
    private Long succeeded;
    private Long failed;
    private Long canceled;
    private Long expired;
    private Long expiredLeases;
    private BigDecimal successRate;
    private String recentFailureJobId;
    private String recentFailureType;
    private String recentFailureErrorCode;
    private LocalDateTime recentFailureTime;
}
