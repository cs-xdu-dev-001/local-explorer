package com.localexplorer.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthSessionStatsVO {
    private Long active;
    private Long rotated;
    private Long revoked;
    private Long expired;
}
