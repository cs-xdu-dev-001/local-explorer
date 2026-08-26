package com.localexplorer.service;

import com.localexplorer.config.ExportJobProperties;
import com.localexplorer.domain.ExportQuerySnapshot;
import com.localexplorer.domain.ExportType;
import com.localexplorer.dto.CreateExportJobDTO;
import com.localexplorer.mapper.ExploreOrderMapper;
import com.localexplorer.mapper.OperationLogMapper;
import com.localexplorer.mapper.ReviewMapper;
import com.localexplorer.mapper.UserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@Service
public class ExportSnapshotService {

    @Autowired private ExploreOrderMapper orderMapper;
    @Autowired private UserMapper userMapper;
    @Autowired private ReviewMapper reviewMapper;
    @Autowired private OperationLogMapper operationLogMapper;
    @Autowired private ExportJobProperties properties;
    @Autowired private ExportSnapshotCipher snapshotCipher;

    public ExportSnapshotPlan freeze(CreateExportJobDTO dto, LocalDateTime now) {
        ExportType type = ExportType.valueOf(dto.getExportType());
        LocalDateTime start = dto.getStartTime() == null ? now.minusDays(properties.getMaxRangeDays()) : dto.getStartTime();
        LocalDateTime end = dto.getEndTime() == null ? now : dto.getEndTime();
        ExportQuerySnapshot snapshot = ExportQuerySnapshot.builder()
                .exportType(dto.getExportType())
                .fileFormat(dto.getFileFormat())
                .keyword(trim(dto.getKeyword()))
                .orderNo(trim(dto.getOrderNo()))
                .contactName(trim(dto.getContactName()))
                .name(trim(dto.getName()))
                .phone(trim(dto.getPhone()))
                .description(trim(dto.getDescription()))
                .dataStatus(dto.getDataStatus())
                .orderType(dto.getOrderType())
                .userId(dto.getUserId())
                .itemId(dto.getItemId())
                .minRating(dto.getMinRating())
                .rating(dto.getRating())
                .replyState(dto.getReplyState())
                .operatorId(dto.getOperatorId())
                .requestMethod(dto.getRequestMethod())
                .startTime(start)
                .endTime(end)
                .snapshotAt(now)
                .columns(columns(type))
                .sort("id ASC")
                .build();
        Long maxId = findMaxId(type, snapshot);
        snapshot.setMaxId(maxId == null ? 0L : maxId);
        long totalRows = snapshot.getMaxId() == 0 ? 0 : count(type, snapshot);
        snapshotCipher.protect(snapshot);
        return new ExportSnapshotPlan(snapshot, totalRows);
    }

    private Long findMaxId(ExportType type, ExportQuerySnapshot snapshot) {
        switch (type) {
            case ORDER: return orderMapper.findMaxIdForExport(snapshot);
            case USER: return userMapper.findMaxIdForExport(snapshot);
            case REVIEW: return reviewMapper.findMaxIdForExport(snapshot);
            case OPERATION_LOG: return operationLogMapper.findMaxIdForExport(snapshot);
            default: throw new IllegalArgumentException("Unsupported export type");
        }
    }

    private long count(ExportType type, ExportQuerySnapshot snapshot) {
        switch (type) {
            case ORDER: return orderMapper.countForExport(snapshot);
            case USER: return userMapper.countForExport(snapshot);
            case REVIEW: return reviewMapper.countForExport(snapshot);
            case OPERATION_LOG: return operationLogMapper.countForExport(snapshot);
            default: throw new IllegalArgumentException("Unsupported export type");
        }
    }

    private List<String> columns(ExportType type) {
        switch (type) {
            case ORDER:
                return Arrays.asList("预约编号", "用户", "类型", "项目/套餐", "金额", "人数", "联系人", "联系电话", "预约时间", "状态", "创建时间");
            case USER:
                return Arrays.asList("姓名", "手机号", "性别", "状态", "注册时间");
            case REVIEW:
                return Arrays.asList("用户", "项目", "评分", "评价内容", "商家回复", "回复时间", "评价时间");
            case OPERATION_LOG:
                return Arrays.asList("操作描述", "操作人", "请求方法", "请求路径", "耗时(ms)", "操作时间");
            default:
                return Collections.emptyList();
        }
    }

    private String trim(String value) {
        return value == null ? null : value.trim();
    }
}
