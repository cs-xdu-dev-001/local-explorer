package com.localexplorer.service;

import com.localexplorer.dto.ReviewDTO;
import com.localexplorer.dto.ReviewPageQueryDTO;
import com.localexplorer.result.PageResult;

import java.util.List;

public interface ReviewService {

    /** 用户端：提交评价 */
    void save(ReviewDTO dto, Long userId);

    /** 分页查询 */
    PageResult pageQuery(ReviewPageQueryDTO dto);

    /** 管理端：删除评价 */
    void deleteBatch(List<Long> ids);

    /** 管理端：回复评价 */
    void reply(Long id, String replyContent);

    /** 某项目的平均评分 */
    Double avgRating(Long itemId);

    /** 某项目的评价数 */
    Long countByItemId(Long itemId);
}
