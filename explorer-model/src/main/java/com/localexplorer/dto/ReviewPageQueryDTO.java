package com.localexplorer.dto;

import lombok.Data;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Positive;
import javax.validation.constraints.Size;
import java.io.Serializable;

@Data
public class ReviewPageQueryDTO implements Serializable {

    @Min(value = 1, message = "页码不能小于1")
    @Max(value = 100000, message = "页码不能超过100000")
    private int page = 1;

    @Min(value = 1, message = "每页数量不能小于1")
    @Max(value = 100, message = "每页数量不能超过100")
    private int pageSize = 10;

    /** 通用关键词：项目、用户、评价内容、商家回复 */
    @Size(max = 100, message = "关键词不能超过100个字符")
    private String keyword;

    /** 特色项目ID */
    @Positive(message = "项目ID不正确")
    private Long itemId;

    /** 用户ID */
    @Positive(message = "用户ID不正确")
    private Long userId;

    /** 最低评分 */
    @Min(value = 1, message = "评分必须在1-5之间")
    @Max(value = 5, message = "评分必须在1-5之间")
    private Integer minRating;

    /** 指定评分 */
    @Min(value = 1, message = "评分必须在1-5之间")
    @Max(value = 5, message = "评分必须在1-5之间")
    private Integer rating;

    /** 回复状态 replied=已回复 unreplied=未回复 */
    @Pattern(regexp = "replied|unreplied", message = "回复状态不正确")
    private String replyState;
}
