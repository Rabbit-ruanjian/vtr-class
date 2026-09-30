package com.vtr.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.nio.file.Paths;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final AiRequestRateLimitInterceptor aiRequestRateLimitInterceptor;

    public WebConfig(AiRequestRateLimitInterceptor aiRequestRateLimitInterceptor) {
        this.aiRequestRateLimitInterceptor = aiRequestRateLimitInterceptor;
    }

    @Value("${upload.path:./uploads/}")
    private String uploadPath;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Resolve the configured folder once so serving files does not depend on
        // the directory from which the application process was started.
        Path uploadDirectory = Paths.get(uploadPath).toAbsolutePath().normalize();
        String location = uploadDirectory.toUri().toString();
        if (!location.endsWith("/")) {
            location += "/";
        }

        // Courseware files are streamed through the authorized controller endpoint.
        registry.addResourceHandler("/uploads/images/**")
                .addResourceLocations(resourceLocation(uploadDirectory.resolve("images")))
                .setCachePeriod(3600);
        registry.addResourceHandler("/uploads/avatars/**")
                .addResourceLocations(resourceLocation(uploadDirectory.resolve("avatars")))
                .setCachePeriod(3600);
        registry.addResourceHandler("/uploads/carousel/**")
                .addResourceLocations(resourceLocation(uploadDirectory.resolve("carousel")))
                .setCachePeriod(3600);  // 缓存1小时
        registry.addResourceHandler("/uploads/notice-attachments/**")
                .addResourceLocations(resourceLocation(uploadDirectory.resolve("notice-attachments")))
                .setCachePeriod(3600);

        // AI 课程资源仍通过 AiCourseResourceController 做权限校验，不在这里暴露目录。

        // 打印配置信息
        System.out.println("=== 静态资源映射配置 ===");
        System.out.println("上传文件路径: " + location);
        System.out.println("访问URL: http://localhost:8080/uploads/文件名");
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(aiRequestRateLimitInterceptor)
                .addPathPatterns("/api/ai/**");
    }

    private String resourceLocation(Path directory) {
        String location = directory.toAbsolutePath().normalize().toUri().toString();
        return location.endsWith("/") ? location : location + "/";
    }
}
