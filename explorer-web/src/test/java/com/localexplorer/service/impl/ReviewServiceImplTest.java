package com.localexplorer.service.impl;

import com.localexplorer.dto.ReviewDTO;
import com.localexplorer.entity.ExploreOrder;
import com.localexplorer.entity.ExplorePackageItem;
import com.localexplorer.entity.Review;
import com.localexplorer.exception.BaseException;
import com.localexplorer.mapper.ExploreOrderMapper;
import com.localexplorer.mapper.ExplorePackageItemMapper;
import com.localexplorer.mapper.ReviewMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class ReviewServiceImplTest {

    private ReviewServiceImpl reviewService;

    @Mock
    private ReviewMapper reviewMapper;
    @Mock
    private ExploreOrderMapper orderMapper;
    @Mock
    private ExplorePackageItemMapper packageItemMapper;

    @BeforeEach
    void setUp() {
        reviewService = new ReviewServiceImpl();
        ReflectionTestUtils.setField(reviewService, "reviewMapper", reviewMapper);
        ReflectionTestUtils.setField(reviewService, "orderMapper", orderMapper);
        ReflectionTestUtils.setField(reviewService, "packageItemMapper", packageItemMapper);
    }

    @Test
    void saveReviewUsesCompletedItemOrderAsSourceOfTruth() {
        ExploreOrder order = ExploreOrder.builder()
                .id(3001L)
                .userId(7L)
                .orderType(1)
                .itemId(1001L)
                .status(2)
                .build();
        when(orderMapper.getById(3001L)).thenReturn(order);
        when(reviewMapper.countByOrderId(3001L)).thenReturn(0L);

        ReviewDTO dto = new ReviewDTO();
        dto.setOrderId(3001L);
        dto.setItemId(9999L);
        dto.setRating(5);
        dto.setContent("good");

        reviewService.save(dto, 7L);

        ArgumentCaptor<Review> reviewCaptor = ArgumentCaptor.forClass(Review.class);
        verify(reviewMapper).insert(reviewCaptor.capture());
        assertThat(reviewCaptor.getValue().getUserId()).isEqualTo(7L);
        assertThat(reviewCaptor.getValue().getOrderId()).isEqualTo(3001L);
        assertThat(reviewCaptor.getValue().getItemId()).isEqualTo(1001L);
        assertThat(reviewCaptor.getValue().getRating()).isEqualTo(5);
    }

    @Test
    void saveReviewRejectsUnfinishedOrder() {
        ExploreOrder order = ExploreOrder.builder()
                .id(3001L)
                .userId(7L)
                .orderType(1)
                .itemId(1001L)
                .status(1)
                .build();
        when(orderMapper.getById(3001L)).thenReturn(order);

        ReviewDTO dto = reviewDto(3001L, 5);

        assertThatThrownBy(() -> reviewService.save(dto, 7L))
                .isInstanceOf(BaseException.class)
                .hasMessageContaining("\u5b8c\u6210");
        verify(reviewMapper, never()).insert(any());
    }

    @Test
    void saveReviewUsesCompletedPackageOrderFirstItemAsReviewTarget() {
        ExploreOrder order = ExploreOrder.builder()
                .id(3002L)
                .userId(7L)
                .orderType(2)
                .packageId(2001L)
                .status(2)
                .build();
        when(orderMapper.getById(3002L)).thenReturn(order);
        when(reviewMapper.countByOrderId(3002L)).thenReturn(0L);
        lenient().when(packageItemMapper.getByExplorePackageId(2001L)).thenReturn(java.util.Collections.singletonList(
                ExplorePackageItem.builder()
                        .packageId(2001L)
                        .itemId(1001L)
                        .name("城市咖啡体验")
                        .copies(1)
                        .build()
        ));

        ReviewDTO dto = reviewDto(3002L, 5);

        reviewService.save(dto, 7L);

        ArgumentCaptor<Review> reviewCaptor = ArgumentCaptor.forClass(Review.class);
        verify(reviewMapper).insert(reviewCaptor.capture());
        assertThat(reviewCaptor.getValue().getOrderId()).isEqualTo(3002L);
        assertThat(reviewCaptor.getValue().getItemId()).isEqualTo(1001L);
    }

    @Test
    void saveReviewRejectsOtherUsersOrder() {
        ExploreOrder order = ExploreOrder.builder()
                .id(3001L)
                .userId(8L)
                .orderType(1)
                .itemId(1001L)
                .status(2)
                .build();
        when(orderMapper.getById(3001L)).thenReturn(order);

        ReviewDTO dto = reviewDto(3001L, 5);

        assertThatThrownBy(() -> reviewService.save(dto, 7L))
                .isInstanceOf(BaseException.class)
                .hasMessageContaining("\u4e0d\u5b58\u5728");
        verify(reviewMapper, never()).insert(any());
    }

    @Test
    void saveReviewRejectsInvalidRating() {
        ReviewDTO dto = reviewDto(3001L, 6);

        assertThatThrownBy(() -> reviewService.save(dto, 7L))
                .isInstanceOf(BaseException.class)
                .hasMessageContaining("\u8bc4\u5206");
        verify(reviewMapper, never()).insert(any());
    }

    @Test
    void saveReviewRejectsDuplicateOrderReview() {
        ExploreOrder order = ExploreOrder.builder()
                .id(3001L)
                .userId(7L)
                .orderType(1)
                .itemId(1001L)
                .status(2)
                .build();
        when(orderMapper.getById(3001L)).thenReturn(order);
        when(reviewMapper.countByOrderId(3001L)).thenReturn(1L);

        ReviewDTO dto = reviewDto(3001L, 5);

        assertThatThrownBy(() -> reviewService.save(dto, 7L))
                .isInstanceOf(BaseException.class)
                .hasMessageContaining("\u5df2\u8bc4\u4ef7");
        verify(reviewMapper, never()).insert(any());
    }

    @Test
    void replyReviewUpdatesReplyContentAndTime() {
        when(reviewMapper.getById(4001L)).thenReturn(Review.builder().id(4001L).content("good").build());

        reviewService.reply(4001L, "Thanks for your feedback");

        ArgumentCaptor<Review> reviewCaptor = ArgumentCaptor.forClass(Review.class);
        verify(reviewMapper).updateReply(reviewCaptor.capture());
        assertThat(reviewCaptor.getValue().getId()).isEqualTo(4001L);
        assertThat(reviewCaptor.getValue().getReplyContent()).isEqualTo("Thanks for your feedback");
        assertThat(reviewCaptor.getValue().getReplyTime()).isNotNull();
    }

    @Test
    void replyReviewRejectsBlankContent() {
        assertThatThrownBy(() -> reviewService.reply(4001L, " "))
                .isInstanceOf(BaseException.class)
                .hasMessageContaining("\u56de\u590d\u5185\u5bb9");

        verify(reviewMapper, never()).updateReply(any());
    }

    @Test
    void replyReviewRejectsMissingReview() {
        when(reviewMapper.getById(4001L)).thenReturn(null);

        assertThatThrownBy(() -> reviewService.reply(4001L, "Thanks"))
                .isInstanceOf(BaseException.class)
                .hasMessageContaining("\u8bc4\u4ef7\u4e0d\u5b58\u5728");

        verify(reviewMapper, never()).updateReply(any());
    }

    private ReviewDTO reviewDto(Long orderId, Integer rating) {
        ReviewDTO dto = new ReviewDTO();
        dto.setOrderId(orderId);
        dto.setRating(rating);
        dto.setContent("good");
        return dto;
    }
}
