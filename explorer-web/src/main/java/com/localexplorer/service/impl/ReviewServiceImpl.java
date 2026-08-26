package com.localexplorer.service.impl;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.localexplorer.dto.ReviewDTO;
import com.localexplorer.dto.ReviewPageQueryDTO;
import com.localexplorer.entity.ExploreOrder;
import com.localexplorer.entity.ExplorePackageItem;
import com.localexplorer.entity.Review;
import com.localexplorer.exception.BaseException;
import com.localexplorer.mapper.ExploreOrderMapper;
import com.localexplorer.mapper.ExplorePackageItemMapper;
import com.localexplorer.mapper.ReviewMapper;
import com.localexplorer.result.PageResult;
import com.localexplorer.service.ReviewService;
import com.localexplorer.vo.ReviewVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Slf4j
public class ReviewServiceImpl implements ReviewService {

    private static final Integer ORDER_TYPE_ITEM = 1;
    private static final Integer ORDER_TYPE_PACKAGE = 2;
    private static final Integer ORDER_STATUS_COMPLETED = 2;

    @Autowired
    private ReviewMapper reviewMapper;
    @Autowired
    private ExploreOrderMapper orderMapper;
    @Autowired
    private ExplorePackageItemMapper packageItemMapper;

    @Override
    public void save(ReviewDTO dto, Long userId) {
        if (dto.getRating() == null || dto.getRating() < 1 || dto.getRating() > 5) {
            throw new BaseException("\u8bc4\u5206\u5fc5\u987b\u57281-5\u4e4b\u95f4");
        }
        if (dto.getOrderId() == null) {
            throw new BaseException("\u8bc4\u4ef7\u8ba2\u5355\u4e0d\u5b58\u5728");
        }
        ExploreOrder order = orderMapper.getById(dto.getOrderId());
        if (order == null || !userId.equals(order.getUserId())) {
            throw new BaseException("\u8bc4\u4ef7\u8ba2\u5355\u4e0d\u5b58\u5728");
        }
        if (!ORDER_STATUS_COMPLETED.equals(order.getStatus())) {
            throw new BaseException("\u8ba2\u5355\u5b8c\u6210\u540e\u624d\u80fd\u8bc4\u4ef7");
        }
        Long reviewItemId = resolveReviewItemId(order);
        if (reviewMapper.countByOrderId(dto.getOrderId()) > 0) {
            throw new BaseException("\u8be5\u8ba2\u5355\u5df2\u8bc4\u4ef7");
        }

        Review review = Review.builder()
                .userId(userId)
                .itemId(reviewItemId)
                .orderId(order.getId())
                .rating(dto.getRating())
                .content(dto.getContent() != null ? dto.getContent() : "")
                .createTime(LocalDateTime.now())
                .build();
        reviewMapper.insert(review);
        log.info("用户 {} 对项目 {} 提交评价，评分 {}", userId, reviewItemId, dto.getRating());
    }

    private Long resolveReviewItemId(ExploreOrder order) {
        if (ORDER_TYPE_ITEM.equals(order.getOrderType()) && order.getItemId() != null) {
            return order.getItemId();
        }
        if (ORDER_TYPE_PACKAGE.equals(order.getOrderType()) && order.getPackageId() != null) {
            List<ExplorePackageItem> packageItems = packageItemMapper.getByExplorePackageId(order.getPackageId());
            if (packageItems != null) {
                for (ExplorePackageItem packageItem : packageItems) {
                    if (packageItem != null && packageItem.getItemId() != null) {
                        return packageItem.getItemId();
                    }
                }
            }
            throw new BaseException("\u5957\u9910\u5185\u6ca1\u6709\u53ef\u8bc4\u4ef7\u9879\u76ee");
        }
        throw new BaseException("\u53ea\u80fd\u8bc4\u4ef7\u5df2\u5b8c\u6210\u7684\u9879\u76ee\u6216\u5957\u9910\u8ba2\u5355");
    }

    @Override
    public PageResult pageQuery(ReviewPageQueryDTO dto) {
        PageHelper.startPage(dto.getPage(), dto.getPageSize());
        Page<ReviewVO> page = reviewMapper.pageQuery(dto);
        return new PageResult(page.getTotal(), page.getResult());
    }

    @Override
    public void deleteBatch(List<Long> ids) {
        if (ids == null || ids.isEmpty()) return;
        reviewMapper.deleteByIds(ids);
    }

    @Override
    public void reply(Long id, String replyContent) {
        if (replyContent == null || replyContent.trim().isEmpty()) {
            throw new BaseException("\u56de\u590d\u5185\u5bb9\u4e0d\u80fd\u4e3a\u7a7a");
        }
        Review existing = id == null ? null : reviewMapper.getById(id);
        if (existing == null) {
            throw new BaseException("\u8bc4\u4ef7\u4e0d\u5b58\u5728");
        }
        Review review = Review.builder()
                .id(id)
                .replyContent(replyContent.trim())
                .replyTime(LocalDateTime.now())
                .build();
        reviewMapper.updateReply(review);
    }

    @Override
    public Double avgRating(Long itemId) {
        return reviewMapper.avgRatingByItemId(itemId);
    }

    @Override
    public Long countByItemId(Long itemId) {
        return reviewMapper.countByItemId(itemId);
    }
}
