package com.hmdp.config;
import com.hmdp.interceptor.LoginInterceptor;
import com.hmdp.interceptor.RefreshInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {
    @Autowired
    private LoginInterceptor loginInterceptor;
    @Autowired
    private RefreshInterceptor refreshInterceptor;

    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(loginInterceptor)
                .excludePathPatterns("/user/code")
                .excludePathPatterns("/user/login")
                .excludePathPatterns("/shop/**")
                .excludePathPatterns("/shop-type/**")
                .excludePathPatterns("/upload")
                .excludePathPatterns("/blog/hot")
                .excludePathPatterns("/voucher/**")
                .order(1);
        registry.addInterceptor(refreshInterceptor)
                .order(0);
    }
}
