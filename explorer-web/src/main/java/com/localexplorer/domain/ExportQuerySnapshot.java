package com.localexplorer.domain;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExportQuerySnapshot {
    private String exportType;
    private String fileFormat;
    private String keyword;
    private String orderNo;
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String contactName;
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String name;
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String phone;
    private String encryptedPii;
    private String description;
    private Integer dataStatus;
    private Integer orderType;
    private Long userId;
    private Long itemId;
    private Integer minRating;
    private Integer rating;
    private String replyState;
    private Long operatorId;
    private String requestMethod;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Long maxId;
    private LocalDateTime snapshotAt;
    private List<String> columns;
    private String sort;
}
