package com.aimv.interfaces.config;

import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 允许本地开发前端（Vite dev server）跨域访问后端 API 和静态资产。
 * 生产部署通过 aimv.cors.allowed-origin-patterns 配置为实际前端域名。
 */
@Configuration
public class WebCorsConfiguration implements WebMvcConfigurer {

    private final List<String> allowedOriginPatterns;

    public WebCorsConfiguration(
            @Value("${aimv.cors.allowed-origin-patterns:http://localhost:*,http://127.0.0.1:*}")
            List<String> allowedOriginPatterns) {
        this.allowedOriginPatterns = allowedOriginPatterns;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
            .allowedOriginPatterns(allowedOriginPatterns.toArray(String[]::new))
            .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
            .allowedHeaders("*")
            .maxAge(3600);
    }
}
