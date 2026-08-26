package com.localexplorer.constant;

/**
 * 状态常量，启用或者禁用
 */
public class StatusConstant {

    //启用
    public static final Integer ENABLE = 1;

    //禁用
    public static final Integer DISABLE = 0;

    public static boolean isValid(Integer status) {
        return ENABLE.equals(status) || DISABLE.equals(status);
    }
}
