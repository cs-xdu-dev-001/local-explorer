package com.localexplorer.service;

import com.localexplorer.vo.ExploreItemVO;

import java.util.List;

/**
 * 用户互动行为服务（浏览记录、收藏）
 */
public interface UserInteractionService {

    /** 添加浏览记录 */
    void addBrowseRecord(Long userId, Long itemId);

    /** 获取浏览记录（分页） */
    List<ExploreItemVO> getBrowseHistory(Long userId, Integer page, Integer pageSize);

    /** 浏览记录总数 */
    Long getBrowseCount(Long userId);

    /** 收藏项目 */
    void addFavorite(Long userId, Long itemId);

    /** 取消收藏 */
    void removeFavorite(Long userId, Long itemId);

    /** 获取收藏列表（分页） */
    List<ExploreItemVO> getFavorites(Long userId, Integer page, Integer pageSize);

    /** 是否已收藏 */
    boolean isFavorited(Long userId, Long itemId);

    /** 收藏总数 */
    Long getFavoriteCount(Long userId);
}
