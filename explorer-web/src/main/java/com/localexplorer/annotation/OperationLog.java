package com.localexplorer.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 操作日志注解
 *
 * <p>标注在 Controller 方法上，AOP 切面会自动记录操作人、操作时间、请求路径和耗时。</p>
 *
 * <p>使用示例：</p>
 * <pre>
 * {@literal @}OperationLog("新增探店套餐")
 * public Result save(@RequestBody ExplorePackageDTO dto) { ... }
 * </pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface OperationLog {

    /**
     * 操作描述，例如"新增探店套餐"
     */
    String value() default "";
}
