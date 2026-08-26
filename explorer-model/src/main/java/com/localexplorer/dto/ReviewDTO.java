package com.localexplorer.dto;

import lombok.Data;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.Positive;
import javax.validation.constraints.Size;
import java.io.Serializable;

/**
 * 评价 请求参数
 */
@Data
public class ReviewDTO implements Serializable {

    @Positive(message = "评价ID不正确")
    private Long id;

    /** 特色项目ID */
    @Positive(message = "项目ID不正确")
    private Long itemId;

    /** 关联预约ID */
    @Positive(message = "预约ID不正确")
    private Long orderId;

    /** 评分 1-5 */
    @Min(value = 1, message = "评分必须在1-5之间")
    @Max(value = 5, message = "评分必须在1-5之间")
    private Integer rating;

    /** 评价内容 */
    @Size(max = 500, message = "评价内容不能超过500个字符")
    private String content;

    /** 商家回复内容 */
    @Size(max = 500, message = "回复内容不能超过500个字符")
    private String replyContent;
}
