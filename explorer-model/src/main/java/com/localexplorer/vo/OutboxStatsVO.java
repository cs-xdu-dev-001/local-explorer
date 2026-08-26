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
public class OutboxStatsVO implements Serializable {

    private Long pending;
    private Long processing;
    private Long processed;
    private Long dead;
}
