package com.localexplorer.service;

import com.localexplorer.dto.ExploreOrderDTO;
import com.localexplorer.dto.ExploreOrderPageQueryDTO;
import com.localexplorer.entity.ExploreOrder;
import com.localexplorer.result.PageResult;

public interface ExploreOrderService {

    /** 用户端：创建预约 */
    Long create(ExploreOrderDTO dto, Long userId);

    /** 用户端：查询当前用户的预约列表 */
    PageResult pageQuery(ExploreOrderPageQueryDTO dto);

    /** 管理端：查询所有预约 */
    PageResult adminPageQuery(ExploreOrderPageQueryDTO dto);

    /** 根据ID查询 */
    ExploreOrder getById(Long id);

    /** 用户端：查询本人预约详情 */
    ExploreOrder getByIdForUser(Long id, Long userId);

    /** 用户端：取消本人预约 */
    void cancelByUser(Long id, Long userId);

    /** 管理端：更新状态 */
    void updateStatus(Long id, Integer status);

    /** 统计 */
    Long countByUserId(Long userId);
}
