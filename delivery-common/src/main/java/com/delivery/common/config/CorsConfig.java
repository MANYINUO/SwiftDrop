package com.delivery.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 跨域配置（前后端分离必需）。
 *
 * <p>开发阶段前端跑在 Vite dev server（5173），后端跑在 8080，
 * 属于跨域。两种解决方式：
 * <ul>
 *   <li><b>前端代理</b>：vite.config.js 里配 proxy，浏览器看到的是同源请求，不会有跨域
 *       —— 本项目默认用这种方式</li>
 *   <li><b>后端放开 CORS</b>：本类。适合前端独立部署、或者用 Postman / 浏览器直连的场景</li>
 * </ul>
 *
 * <p>注意：{@code allowCredentials(true)} 时不能用 {@code allowedOrigins("*")}，
 * 必须用 {@code allowedOriginPatterns}，否则 Spring 会启动报错。
 *
 * <p><b>生产环境务必把 allowedOriginPatterns 收窄到具体域名</b>，不要留 "*"。
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }
}
