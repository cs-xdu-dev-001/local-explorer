package com.localexplorer.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 特色项目标签
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExploreItemTag implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    //特色项目id
    private Long itemId;

    //标签名称
    private String name;

    //标签数据list
    private String value;

}
