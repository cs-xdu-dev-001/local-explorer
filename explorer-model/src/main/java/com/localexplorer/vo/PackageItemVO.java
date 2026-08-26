package com.localexplorer.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PackageItemVO implements Serializable {

    //特色项目id
    private Long itemId;

    //特色项目名称
    private String name;

    //份数
    private Integer copies;

    //特色项目图片
    private String image;

    //特色项目描述
    private String description;
}
