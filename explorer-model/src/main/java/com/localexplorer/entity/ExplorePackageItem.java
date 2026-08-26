package com.localexplorer.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Positive;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 套餐项目关系
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExplorePackageItem implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    //套餐id
    private Long packageId;

    //特色项目id
    @NotNull(message = "套餐项目不能为空")
    @Positive(message = "套餐项目不正确")
    private Long itemId;

    //特色项目名称 （冗余字段）
    private String name;

    //特色项目原价
    private BigDecimal price;

    //份数
    @NotNull(message = "套餐项目份数不能为空")
    @Min(value = 1, message = "套餐项目份数必须大于0")
    @Max(value = 99, message = "套餐项目份数不能超过99")
    private Integer copies;
}
