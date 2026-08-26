package com.localexplorer.service.impl;

import com.localexplorer.entity.ExploreItem;
import com.localexplorer.mapper.ExploreItemMapper;
import com.localexplorer.service.UserInteractionService;
import com.localexplorer.vo.ExploreItemVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * 用户互动行为服务实现
 *
 * <p>使用 Redis Sorted Set (ZSet) 存储浏览记录和收藏：</p>
 * <ul>
 *   <li>Key: user:{userId}:browse / user:{userId}:favorite</li>
 *   <li>Value: itemId (字符串)</li>
 *   <li>Score: 时间戳 (毫秒)，用于按时间倒序查询</li>
 * </ul>
 *
 * <p>为什么用 ZSet 而不是 List：</p>
 * <ul>
 *   <li>ZSet 自动按 score 排序，天然支持"最近 N 条"查询</li>
 *   <li>ZSet 的 ZREVRANGE 支持分页（offset + count）</li>
 *   <li>ZADD 对已存在的 member 会更新 score，天然去重（同一项目多次浏览只保留最新时间）</li>
 *   <li>ZCARD 可以 O(1) 获取总数</li>
 *   <li>ZSCORE 可以 O(1) 判断是否存在（是否已收藏）</li>
 * </ul>
 */
@Service
@Slf4j
public class UserInteractionServiceImpl implements UserInteractionService {

    private static final String BROWSE_KEY_PREFIX = "user:%d:browse";
    private static final String FAVORITE_KEY_PREFIX = "user:%d:favorite";
    private static final int MAX_BROWSE_RECORDS = 200;

    private final Map<String, Map<Long, Long>> fallbackZSets = new ConcurrentHashMap<>();
    private final AtomicLong fallbackScore = new AtomicLong(System.currentTimeMillis());

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private ExploreItemMapper itemMapper;

    @Override
    public void addBrowseRecord(Long userId, Long itemId) {
        String key = String.format(BROWSE_KEY_PREFIX, userId);
        double score = System.currentTimeMillis();

        try {
            stringRedisTemplate.opsForZSet().add(key, String.valueOf(itemId), score);

            // 限制浏览记录上限，淘汰最旧的记录
            Long size = stringRedisTemplate.opsForZSet().zCard(key);
            if (size != null && size > MAX_BROWSE_RECORDS) {
                stringRedisTemplate.opsForZSet().removeRange(key, 0, size - MAX_BROWSE_RECORDS - 1);
            }
        } catch (RuntimeException ex) {
            logRedisFallback("add browse record", ex);
            addFallbackRecord(key, itemId, MAX_BROWSE_RECORDS);
        }

        log.debug("用户 {} 浏览了项目 {}", userId, itemId);
    }

    @Override
    public List<ExploreItemVO> getBrowseHistory(Long userId, Integer page, Integer pageSize) {
        String key = String.format(BROWSE_KEY_PREFIX, userId);
        return getItemsFromZSet(key, page, pageSize);
    }

    @Override
    public Long getBrowseCount(Long userId) {
        String key = String.format(BROWSE_KEY_PREFIX, userId);
        try {
            Long count = stringRedisTemplate.opsForZSet().zCard(key);
            return count != null ? count : 0L;
        } catch (RuntimeException ex) {
            logRedisFallback("count browse records", ex);
            return fallbackCount(key);
        }
    }

    @Override
    public void addFavorite(Long userId, Long itemId) {
        String key = String.format(FAVORITE_KEY_PREFIX, userId);
        double score = System.currentTimeMillis();
        try {
            stringRedisTemplate.opsForZSet().add(key, String.valueOf(itemId), score);
        } catch (RuntimeException ex) {
            logRedisFallback("add favorite", ex);
            addFallbackRecord(key, itemId, 0);
        }
        log.debug("用户 {} 收藏了项目 {}", userId, itemId);
    }

    @Override
    public void removeFavorite(Long userId, Long itemId) {
        String key = String.format(FAVORITE_KEY_PREFIX, userId);
        try {
            stringRedisTemplate.opsForZSet().remove(key, String.valueOf(itemId));
        } catch (RuntimeException ex) {
            logRedisFallback("remove favorite", ex);
        }
        removeFallbackRecord(key, itemId);
        log.debug("用户 {} 取消收藏了项目 {}", userId, itemId);
    }

    @Override
    public List<ExploreItemVO> getFavorites(Long userId, Integer page, Integer pageSize) {
        String key = String.format(FAVORITE_KEY_PREFIX, userId);
        return getItemsFromZSet(key, page, pageSize);
    }

    @Override
    public boolean isFavorited(Long userId, Long itemId) {
        String key = String.format(FAVORITE_KEY_PREFIX, userId);
        try {
            Double score = stringRedisTemplate.opsForZSet().score(key, String.valueOf(itemId));
            return score != null;
        } catch (RuntimeException ex) {
            logRedisFallback("check favorite", ex);
            return fallbackContains(key, itemId);
        }
    }

    @Override
    public Long getFavoriteCount(Long userId) {
        String key = String.format(FAVORITE_KEY_PREFIX, userId);
        try {
            Long count = stringRedisTemplate.opsForZSet().zCard(key);
            return count != null ? count : 0L;
        } catch (RuntimeException ex) {
            logRedisFallback("count favorites", ex);
            return fallbackCount(key);
        }
    }

    /**
     * 从 ZSet 中分页查询 itemId，再批量查数据库组装 VO
     */
    private List<ExploreItemVO> getItemsFromZSet(String key, Integer page, Integer pageSize) {
        long start = pageStart(page, pageSize);
        long end = start + normalizePageSize(pageSize) - 1;

        // ZREVRANGE 按 score 倒序（最新在前）
        Set<String> itemIds;
        try {
            itemIds = stringRedisTemplate.opsForZSet().reverseRange(key, start, end);
        } catch (RuntimeException ex) {
            logRedisFallback("read interaction list", ex);
            return getItemsByOrderedIds(fallbackIds(key, page, pageSize));
        }
        if (itemIds == null || itemIds.isEmpty()) {
            return Collections.emptyList();
        }

        List<Long> orderedItemIds = new ArrayList<>();
        for (String idStr : itemIds) {
            try {
                orderedItemIds.add(Long.valueOf(idStr));
            } catch (NumberFormatException e) {
                log.warn("无效的 itemId：{}", idStr);
            }
        }

        return getItemsByOrderedIds(orderedItemIds);
    }

    private List<ExploreItemVO> getItemsByOrderedIds(List<Long> orderedItemIds) {
        if (orderedItemIds == null || orderedItemIds.isEmpty()) {
            return Collections.emptyList();
        }

        List<ExploreItem> items = itemMapper.listByIds(orderedItemIds);
        if (items == null || items.isEmpty()) {
            return Collections.emptyList();
        }

        Map<Long, ExploreItem> itemById = new HashMap<>();
        for (ExploreItem item : items) {
            if (item.getId() != null) {
                itemById.put(item.getId(), item);
            }
        }

        List<ExploreItemVO> result = new ArrayList<>();
        for (Long itemId : orderedItemIds) {
            ExploreItem item = itemById.get(itemId);
            if (item != null) {
                ExploreItemVO vo = new ExploreItemVO();
                BeanUtils.copyProperties(item, vo);
                result.add(vo);
            }
        }
        return result;
    }

    private void addFallbackRecord(String key, Long itemId, int maxRecords) {
        Map<Long, Long> records = fallbackZSets.computeIfAbsent(key, ignored -> new ConcurrentHashMap<>());
        records.put(itemId, fallbackScore.incrementAndGet());
        if (maxRecords > 0 && records.size() > maxRecords) {
            records.entrySet().stream()
                    .min((left, right) -> Long.compare(left.getValue(), right.getValue()))
                    .map(Map.Entry::getKey)
                    .ifPresent(records::remove);
        }
    }

    private void removeFallbackRecord(String key, Long itemId) {
        Map<Long, Long> records = fallbackZSets.get(key);
        if (records != null) {
            records.remove(itemId);
        }
    }

    private boolean fallbackContains(String key, Long itemId) {
        Map<Long, Long> records = fallbackZSets.get(key);
        return records != null && records.containsKey(itemId);
    }

    private Long fallbackCount(String key) {
        Map<Long, Long> records = fallbackZSets.get(key);
        return records == null ? 0L : (long) records.size();
    }

    private List<Long> fallbackIds(String key, Integer page, Integer pageSize) {
        Map<Long, Long> records = fallbackZSets.get(key);
        if (records == null || records.isEmpty()) {
            return Collections.emptyList();
        }
        return records.entrySet().stream()
                .sorted((left, right) -> Long.compare(right.getValue(), left.getValue()))
                .skip(pageStart(page, pageSize))
                .limit(normalizePageSize(pageSize))
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

    private long pageStart(Integer page, Integer pageSize) {
        int safePage = page == null || page < 1 ? 1 : page;
        return (long) (safePage - 1) * normalizePageSize(pageSize);
    }

    private int normalizePageSize(Integer pageSize) {
        return pageSize == null || pageSize < 1 ? 10 : pageSize;
    }

    private void logRedisFallback(String operation, RuntimeException ex) {
        log.warn("Redis unavailable while trying to {}, using in-memory fallback: {}", operation, ex.getMessage());
    }
}
