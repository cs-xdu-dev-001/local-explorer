package com.localexplorer.service.impl;

import com.localexplorer.entity.ExploreItem;
import com.localexplorer.mapper.ExploreItemMapper;
import com.localexplorer.vo.ExploreItemVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserInteractionServiceImplTest {

    private UserInteractionServiceImpl userInteractionService;

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ZSetOperations<String, String> zSetOperations;

    @Mock
    private ExploreItemMapper itemMapper;

    @BeforeEach
    void setUp() {
        userInteractionService = new UserInteractionServiceImpl();
        ReflectionTestUtils.setField(userInteractionService, "stringRedisTemplate", stringRedisTemplate);
        ReflectionTestUtils.setField(userInteractionService, "itemMapper", itemMapper);
        when(stringRedisTemplate.opsForZSet()).thenReturn(zSetOperations);
    }

    @Test
    void addBrowseRecordTrimsOldestRecordsWhenLimitIsExceeded() {
        when(zSetOperations.zCard("user:7:browse")).thenReturn(201L);

        userInteractionService.addBrowseRecord(7L, 1001L);

        verify(zSetOperations).add(eq("user:7:browse"), eq("1001"), anyDouble());
        verify(zSetOperations).removeRange("user:7:browse", 0, 0);
    }

    @Test
    void getBrowseHistoryReturnsItemsInRedisOrderAndSkipsInvalidEntries() {
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        ids.add("1001");
        ids.add("bad-id");
        ids.add("1002");
        when(zSetOperations.reverseRange("user:7:browse", 0, 9)).thenReturn(ids);
        when(itemMapper.listByIds(Arrays.asList(1001L, 1002L))).thenReturn(Arrays.asList(
                item(1002L, "室内攀岩入门"),
                item(1001L, "城市咖啡体验")
        ));

        List<ExploreItemVO> result = userInteractionService.getBrowseHistory(7L, 1, 10);

        assertThat(result)
                .extracting(ExploreItemVO::getName)
                .containsExactly("城市咖啡体验", "室内攀岩入门");
        verify(itemMapper).listByIds(Arrays.asList(1001L, 1002L));
        verify(itemMapper, never()).getById(anyLong());
    }

    @Test
    void getBrowseHistoryDoesNotQueryEachItemIndividually() {
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        ids.add("1001");
        ids.add("1002");
        when(zSetOperations.reverseRange("user:7:browse", 0, 9)).thenReturn(ids);
        when(itemMapper.listByIds(Arrays.asList(1001L, 1002L))).thenReturn(Collections.emptyList());
        lenient().when(itemMapper.getById(anyLong()))
                .thenThrow(new AssertionError("should batch query items"));

        assertThatCode(() -> userInteractionService.getBrowseHistory(7L, 1, 10))
                .doesNotThrowAnyException();

        verify(itemMapper).listByIds(Arrays.asList(1001L, 1002L));
        verify(itemMapper, never()).getById(anyLong());
    }

    @Test
    void favoriteOperationsUseUserScopedZSetKeys() {
        when(zSetOperations.score("user:7:favorite", "1001")).thenReturn(1.0);
        when(zSetOperations.zCard("user:7:favorite")).thenReturn(3L);

        userInteractionService.addFavorite(7L, 1001L);
        boolean favorited = userInteractionService.isFavorited(7L, 1001L);
        Long count = userInteractionService.getFavoriteCount(7L);
        userInteractionService.removeFavorite(7L, 1001L);

        verify(zSetOperations).add(eq("user:7:favorite"), eq("1001"), anyDouble());
        verify(zSetOperations).remove("user:7:favorite", "1001");
        assertThat(favorited).isTrue();
        assertThat(count).isEqualTo(3L);
    }

    @Test
    void favoriteOperationsFallBackToMemoryWhenRedisIsUnavailable() {
        RuntimeException redisDown = new RuntimeException("redis down");
        when(zSetOperations.add(eq("user:7:favorite"), eq("1001"), anyDouble())).thenThrow(redisDown);
        when(zSetOperations.score("user:7:favorite", "1001")).thenThrow(redisDown);
        when(zSetOperations.zCard("user:7:favorite")).thenThrow(redisDown);
        when(zSetOperations.reverseRange("user:7:favorite", 0, 9)).thenThrow(redisDown);
        when(zSetOperations.remove("user:7:favorite", "1001")).thenThrow(redisDown);
        when(itemMapper.listByIds(Collections.singletonList(1001L)))
                .thenReturn(Collections.singletonList(item(1001L, "fallback favorite")));

        assertThatCode(() -> userInteractionService.addFavorite(7L, 1001L))
                .doesNotThrowAnyException();

        assertThat(userInteractionService.isFavorited(7L, 1001L)).isTrue();
        assertThat(userInteractionService.getFavoriteCount(7L)).isEqualTo(1L);
        assertThat(userInteractionService.getFavorites(7L, 1, 10))
                .extracting(ExploreItemVO::getName)
                .containsExactly("fallback favorite");

        assertThatCode(() -> userInteractionService.removeFavorite(7L, 1001L))
                .doesNotThrowAnyException();
        assertThat(userInteractionService.isFavorited(7L, 1001L)).isFalse();
        assertThat(userInteractionService.getFavoriteCount(7L)).isZero();
    }

    @Test
    void browseHistoryFallsBackToMemoryWhenRedisIsUnavailable() {
        RuntimeException redisDown = new RuntimeException("redis down");
        when(zSetOperations.add(eq("user:7:browse"), anyString(), anyDouble())).thenThrow(redisDown);
        when(zSetOperations.reverseRange("user:7:browse", 0, 9)).thenThrow(redisDown);
        when(itemMapper.listByIds(Arrays.asList(1002L, 1001L))).thenReturn(Arrays.asList(
                item(1001L, "first browse"),
                item(1002L, "latest browse")
        ));

        userInteractionService.addBrowseRecord(7L, 1001L);
        userInteractionService.addBrowseRecord(7L, 1002L);

        assertThat(userInteractionService.getBrowseHistory(7L, 1, 10))
                .extracting(ExploreItemVO::getName)
                .containsExactly("latest browse", "first browse");
    }

    private static ExploreItem item(Long id, String name) {
        return ExploreItem.builder()
                .id(id)
                .name(name)
                .price(BigDecimal.valueOf(39))
                .status(1)
                .build();
    }
}
