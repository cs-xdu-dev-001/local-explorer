package com.localexplorer.vo;

import com.localexplorer.entity.ExploreItemTag;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExploreItemVO implements Serializable {

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
    //更新时间
    private LocalDateTime updateTime;
    //分类名称
    private String categoryName;
    //特色项目关联的标签
    private List<ExploreItemTag> tags = new ArrayList<>();

    //private Integer copies;
}
