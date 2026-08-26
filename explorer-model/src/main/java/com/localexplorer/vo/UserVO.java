package com.localexplorer.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserVO implements Serializable {

    private Long id;

    private String name;

    private String phone;

    private String sex;

    private String avatar;

    private Integer status;

    private LocalDateTime createTime;

    private Long orderCount;

    private Long browseCount;

    private Long favoriteCount;
}
