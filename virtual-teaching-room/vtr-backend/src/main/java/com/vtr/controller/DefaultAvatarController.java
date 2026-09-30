package com.vtr.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 返回系统默认头像。
 *
 * 默认头像在 uploads 根目录，而其它上传资源由各自的目录或授权接口处理，
 * 因此这里只开放这一个明确的文件，避免用 /uploads/** 暴露整个上传目录。
 */
@RestController
public class DefaultAvatarController {

    @Value("${upload.path:./uploads/}")
    private String uploadPath;

    @GetMapping(value = "/uploads/default-avatar.png", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<Resource> getDefaultAvatar() {
        Path avatarPath = Paths.get(uploadPath).toAbsolutePath().normalize().resolve("default-avatar.png");
        Resource avatar = new FileSystemResource(avatarPath);
        if (!avatar.exists() || !avatar.isReadable()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).body(avatar);
    }
}
