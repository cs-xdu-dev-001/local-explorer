package com.localexplorer.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 特色项目
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExploreItem implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    //特色项目名称
    private String name;

    //特色项目分类id
    private Long categoryId;

    //特色项目价格
    private BigDecimal price;

    //图片
    private String image;

    //描述信息
    private String description;

    private Integer durationMinutes;

    private Integer capacity;

    private Integer booked;

    private String district;

    private String address;

    private String meetingPoint;

    private String cancelPolicy;

    //0 停售 1 起售
    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    private Long createUser;

    private Long updateUser;

}
