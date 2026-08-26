package com.localexplorer.config;

import com.localexplorer.interceptor.AdminAuthorizationInterceptor;
import com.localexplorer.interceptor.JwtTokenAdminInterceptor;
import com.localexplorer.interceptor.JwtTokenUserInterceptor;
import com.localexplorer.json.JacksonObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurationSupport;
import springfox.documentation.builders.ApiInfoBuilder;
import springfox.documentation.builders.PathSelectors;
import springfox.documentation.builders.RequestHandlerSelectors;
import springfox.documentation.service.ApiInfo;
import springfox.documentation.spi.DocumentationType;
import springfox.documentation.spring.web.plugins.Docket;

import java.util.List;

/**
 * 配置类，注册web层相关组件
 */
@Configuration
@Slf4j
public class WebMvcConfiguration extends WebMvcConfigurationSupport {

    @Autowired
    private JwtTokenAdminInterceptor jwtTokenAdminInterceptor;
    @Autowired
    private JwtTokenUserInterceptor jwtTokenUserInterceptor;
    @Autowired
    private AdminAuthorizationInterceptor adminAuthorizationInterceptor;
    /**
     * 注册自定义拦截器
     *
     * @param registry
     */
    protected void addInterceptors(InterceptorRegistry registry) {
        log.info("开始注册自定义拦截器...");
        registry.addInterceptor(jwtTokenAdminInterceptor)
                .addPathPatterns("/admin/**")
                .excludePathPatterns("/admin/employee/login")
                .excludePathPatterns("/admin/employee/refresh")
                .excludePathPatterns("/admin/employee/logout");
        registry.addInterceptor(adminAuthorizationInterceptor)
                .addPathPatterns("/admin/**");
        registry.addInterceptor(jwtTokenUserInterceptor)
                .addPathPatterns("/user/**")
                .excludePathPatterns("/user/user/login")
                .excludePathPatterns("/user/user/refresh")
                .excludePathPatterns("/user/user/logout")
                .excludePathPatterns("/user/merchant/info")
                .excludePathPatterns("/user/shop/status")
                .excludePathPatterns("/user/category/list")
                .excludePathPatterns("/user/explore-item/list")
                .excludePathPatterns("/user/explore-package/list")
                .excludePathPatterns("/user/explore-package/items/**")
                .excludePathPatterns("/user/review/item/**")
                .excludePathPatterns("/user/review/avg/**");
    }

    /**
     * 通过knife4j生成接口文档
     * @return
     */
    @Bean
    public Docket docket1() {
        log.info("正在准备接口文档...");
        ApiInfo apiInfo = new ApiInfoBuilder()
                .title("本地生活探店与商家管理平台接口文档")
                .version("2.0")
                .description("商家后台管理、商家信息、内容分类、特色项目、探店套餐和门店营业状态相关接口")
                .build();
        Docket docket = new Docket(DocumentationType.SWAGGER_2)
                .groupName("商家后台接口")
                .apiInfo(apiInfo)
                .select()
                .apis(RequestHandlerSelectors.basePackage("com.localexplorer.controller.admin"))
                .paths(PathSelectors.any())
                .build();
        return docket;
    }
    @Bean
    public Docket docket2() {
        log.info("正在准备接口文档...");
        ApiInfo apiInfo = new ApiInfoBuilder()
                .title("本地生活探店与商家管理平台接口文档")
                .version("2.0")
                .description("用户端登录、商家信息、门店状态、探店内容浏览和套餐浏览相关接口")
                .build();
        Docket docket = new Docket(DocumentationType.SWAGGER_2)
                .groupName("用户端浏览接口")
                .apiInfo(apiInfo)
                .select()
                .apis(RequestHandlerSelectors.basePackage("com.localexplorer.controller.user"))
                .paths(PathSelectors.any())
                .build();
        return docket;
    }

    /**
     * 设置浏览器手动访问时的前端入口
     * @param registry
     */
    protected void addViewControllers(ViewControllerRegistry registry) {
        registry.addRedirectViewController("/", "/console/login.html");
        registry.addRedirectViewController("/console", "/console/login.html");
        registry.addRedirectViewController("/client", "/client/login.html");
    }

    /**
     * 设置静态资源映射
     * @param registry
     */
    protected void addResourceHandlers(ResourceHandlerRegistry registry) {
        log.info("开始设置静态资源映射...");
        registry.addResourceHandler("/doc.html").addResourceLocations("classpath:/META-INF/resources/");
        registry.addResourceHandler("/webjars/**").addResourceLocations("classpath:/META-INF/resources/webjars/");
        registry.addResourceHandler("/assets/**").addResourceLocations("classpath:/static/assets/");
        registry.addResourceHandler("/console/**").addResourceLocations("classpath:/static/console/");
        registry.addResourceHandler("/client/**").addResourceLocations("classpath:/static/client/");
    }

    /**
     * 扩展Spring MVC框架中的消息转换器
     * @param converters
     */
    protected void extendMessageConverters(List<HttpMessageConverter<?>> converters) {
        log.info("扩展消息转换器...");
        // 创建一个消息转换器对象
        MappingJackson2HttpMessageConverter converter = new MappingJackson2HttpMessageConverter();
        //为消息转换器设置一个对象转换器:将Java对象序列化为json数据
        converter.setObjectMapper(new JacksonObjectMapper());
        //将自定义的消息转换器加入容器,并且将其放到首部
        converters.add(0,converter);
    }
}
