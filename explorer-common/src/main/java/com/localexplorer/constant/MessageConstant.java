package com.localexplorer.constant;

/**
 * 业务提示信息常量
 */
public class MessageConstant {

    public static final String PASSWORD_ERROR = "密码错误";
    public static final String ACCOUNT_NOT_FOUND = "账号不存在";
    public static final String ACCOUNT_LOCKED = "账号被锁定";
    public static final String UNKNOWN_ERROR = "未知错误";
    public static final String DATABASE_NOT_INITIALIZED = "数据库未初始化，请先执行 docs/local-explorer-init.sql";
    public static final String PARAM_ERROR = "请求参数错误";
    public static final String STATUS_INVALID = "状态参数只能为0或1";
    public static final String ALREADY_EXISTS = "已存在";
    public static final String LOGIN_FAILED = "登录失败";
    public static final String UPLOAD_FAILED = "文件上传失败";
    public static final String CATEGORY_BE_RELATED_BY_PACKAGE = "当前分类关联了探店套餐，不能删除";
    public static final String CATEGORY_BE_RELATED_BY_ITEM = "当前分类关联了特色项目，不能删除";
    public static final String CATEGORY_TYPE_CHANGE_NOT_ALLOWED = "已有关联内容的分类不能修改类型";
    public static final String ITEM_CATEGORY_INVALID = "项目分类不存在或类型不正确";
    public static final String PACKAGE_CATEGORY_INVALID = "套餐分类不存在或类型不正确";
    public static final String PACKAGE_ITEMS_REQUIRED = "探店套餐至少需要包含一个特色项目";
    public static final String PACKAGE_ITEM_NOT_FOUND = "套餐包含的特色项目不存在或已删除";
    public static final String PACKAGE_ITEM_DUPLICATED = "套餐不能重复包含同一特色项目";
    public static final String PACKAGE_ENABLE_FAILED = "探店套餐内包含未上架特色项目，无法上架";
    public static final String EXPLORE_ITEM_ON_SALE = "展示中的特色项目不能删除";
    public static final String EXPLORE_PACKAGE_ON_SALE = "上架中的探店套餐不能删除";
    public static final String ITEM_BE_RELATED_BY_PACKAGE = "当前特色项目关联了探店套餐，不能删除";
    public static final String ITEM_BE_RELATED_BY_ORDER = "当前特色项目已有预约记录，不能删除，可改为停用";
    public static final String ITEM_BE_RELATED_BY_REVIEW = "当前特色项目已有评价记录，不能删除，可改为停用";
    public static final String PACKAGE_BE_RELATED_BY_ORDER = "当前探店套餐已有预约记录，不能删除，可改为停用";
    public static final String EMPLOYEE_BE_RELATED_BY_OPERATION_LOG = "当前员工已有操作日志，不能删除，可改为禁用";
    public static final String EXPLORE_ITEM_NOT_FOUND = "特色项目不存在";
    public static final String EXPLORE_PACKAGE_NOT_FOUND = "探店套餐不存在";
    public static final String EMPLOYEE_NOT_FOUND = "员工不存在";
    public static final String CATEGORY_NOT_FOUND = "分类不存在";
    public static final String DUPLICATE_SUBMIT = "请勿重复提交";
}
