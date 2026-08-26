package com.localexplorer.service;

import com.localexplorer.domain.ExportQuerySnapshot;
import com.localexplorer.domain.ExportType;
import com.localexplorer.mapper.ExploreOrderMapper;
import com.localexplorer.mapper.OperationLogMapper;
import com.localexplorer.mapper.ReviewMapper;
import com.localexplorer.mapper.UserMapper;
import com.localexplorer.vo.ExploreOrderVO;
import com.localexplorer.vo.OperationLogVO;
import com.localexplorer.vo.ReviewVO;
import com.localexplorer.vo.UserVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Component
public class MapperExportDataReader implements ExportDataReader {

    @Autowired private ExploreOrderMapper orderMapper;
    @Autowired private UserMapper userMapper;
    @Autowired private ReviewMapper reviewMapper;
    @Autowired private OperationLogMapper operationLogMapper;

    @Override
    public ExportChunk fetch(ExportQuerySnapshot snapshot, long lastId, int limit) {
        ExportType type = ExportType.valueOf(snapshot.getExportType());
        List<ExportRow> rows = new ArrayList<>();
        switch (type) {
            case ORDER:
                for (ExploreOrderVO row : orderMapper.findExportChunk(snapshot, lastId, limit)) {
                    rows.add(new ExportRow(row.getId(), Arrays.asList(
                            row.getOrderNo(), row.getUserName(), orderType(row.getOrderType()), row.getItemName(),
                            row.getAmount(), row.getPeopleCount(), row.getContactName(), maskPhone(row.getContactPhone()),
                            row.getReserveTime(), orderStatus(row.getStatus()), row.getCreateTime())));
                }
                break;
            case USER:
                for (UserVO row : userMapper.findExportChunk(snapshot, lastId, limit)) {
                    rows.add(new ExportRow(row.getId(), Arrays.asList(
                            row.getName(), maskPhone(row.getPhone()), row.getSex(), enabledStatus(row.getStatus()), row.getCreateTime())));
                }
                break;
            case REVIEW:
                for (ReviewVO row : reviewMapper.findExportChunk(snapshot, lastId, limit)) {
                    rows.add(new ExportRow(row.getId(), Arrays.asList(
                            row.getUserName(), row.getItemName(), row.getRating(), row.getContent(),
                            row.getReplyContent(), row.getReplyTime(), row.getCreateTime())));
                }
                break;
            case OPERATION_LOG:
                for (OperationLogVO row : operationLogMapper.findExportChunk(snapshot, lastId, limit)) {
                    rows.add(new ExportRow(row.getId(), Arrays.asList(
                            row.getDescription(), row.getOperatorName(), row.getRequestMethod(), row.getRequestUri(),
                            row.getCostTime(), row.getCreateTime())));
                }
                break;
            default:
                throw new IllegalArgumentException("Unsupported export type");
        }
        long nextId = rows.isEmpty() ? lastId : rows.get(rows.size() - 1).getId();
        return new ExportChunk(rows, nextId);
    }

    private String maskPhone(String phone) {
        if (phone == null || phone.length() < 7) return phone == null ? "" : "***";
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }

    private String enabledStatus(Integer status) {
        return Integer.valueOf(1).equals(status) ? "启用" : "禁用";
    }

    private String orderType(Integer type) {
        return Integer.valueOf(1).equals(type) ? "特色项目" : "探店套餐";
    }

    private String orderStatus(Integer status) {
        if (status == null) return "未知";
        switch (status) {
            case 0: return "待确认";
            case 1: return "已确认";
            case 2: return "已完成";
            case 3: return "已取消";
            case 4: return "超时取消";
            default: return "未知";
        }
    }
}
