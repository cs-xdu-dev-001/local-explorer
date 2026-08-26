package com.localexplorer.service;

import com.localexplorer.dto.OperationLogPageQueryDTO;
import com.localexplorer.entity.OperationLogEntity;
import com.localexplorer.result.PageResult;

public interface OperationLogService {

    /** 保存操作日志 */
    void save(OperationLogEntity log);

    /** 分页查询 */
    PageResult pageQuery(OperationLogPageQueryDTO dto);
}
