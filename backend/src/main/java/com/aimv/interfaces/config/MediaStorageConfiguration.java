package com.aimv.interfaces.config;

import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 把运行时媒体目录（视频适配器 t2v+TTS+ffmpeg 合成后的带配音 MP4）通过 /media/** 对外暴露，
 * 让前端可以直接播放。目录与 aimv.media.storage-dir 保持一致。
 */
@Configuration
public class MediaStorageConfiguration implements WebMvcConfigurer {

    private final String storageDir;

    public MediaStorageConfiguration(@Value("${aimv.media.storage-dir:./storage/media}") String storageDir) {
        this.storageDir = storageDir;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String location = Path.of(storageDir).toAbsolutePath().normalize().toUri().toString();
        registry.addResourceHandler("/media/**")
            .addResourceLocations(location);
    }
}
