package com.localexplorer.service.impl;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.localexplorer.dto.OperationLogPageQueryDTO;
import com.localexplorer.entity.OperationLogEntity;
import com.localexplorer.mapper.OperationLogMapper;
import com.localexplorer.result.PageResult;
import com.localexplorer.service.OperationLogService;
import com.localexplorer.vo.OperationLogVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class OperationLogServiceImpl implements OperationLogService {

    @Autowired
    private OperationLogMapper logMapper;

    @Override
    @Async("operationLogExecutor")
    public void save(OperationLogEntity operationLog) {
        try {
            logMapper.insert(operationLog);
        } catch (Exception e) {
            log.warn("保存操作日志失败：{}", e.getMessage());
        }
    }

    @Override
    public PageResult pageQuery(OperationLogPageQueryDTO dto) {
        PageHelper.startPage(dto.getPage(), dto.getPageSize());
        Page<OperationLogVO> page = logMapper.pageQuery(dto);
        return new PageResult(page.getTotal(), page.getResult());
    }
}
