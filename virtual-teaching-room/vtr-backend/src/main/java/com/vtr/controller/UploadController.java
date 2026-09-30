package com.vtr.controller;

import com.vtr.common.Result;
import lombok.RequiredArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.io.InputStream;

@Slf4j
@RestController
@RequestMapping("/api/upload")
@RequiredArgsConstructor
public class UploadController {

    @Value("${upload.path:uploads}")
    private String uploadPath;

    /**
     * 上传课件文件
     */
    @PostMapping("/courseware")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN', 'SUPER_ADMIN')")
    public Result<String> uploadCourseware(@RequestParam("file") MultipartFile file) {
        try {
            if (file.isEmpty()) {
                return Result.error(400, "文件不能为空");
            }

            String originalFilename = file.getOriginalFilename();
            if (originalFilename == null) {
                return Result.error(400, "无效的文件名");
            }

            String extension = originalFilename.substring(originalFilename.lastIndexOf(".") + 1).toLowerCase();
            List<String> allowedTypes = Arrays.asList("pdf", "ppt", "pptx", "doc", "docx", "jpg", "jpeg", "png", "gif", "mp4", "webm", "ogg", "mov", "m4v", "zip", "rar", "txt", "md");
            if (!allowedTypes.contains(extension)) {
                return Result.error(400, "不支持的文件类型");
            }

            validateContentSignature(file, extension);

            long maxSize = 100 * 1024 * 1024;
            if (file.getSize() > maxSize) {
                return Result.error(400, "文件大小不能超过100MB");
            }

            String fileUrl = saveFile(file, "courseware");
            log.info("课件上传成功: originalFilename={}, url={}", originalFilename, fileUrl);
            return Result.success(fileUrl);
        } catch (IOException e) {
            log.error("课件上传失败", e);
            return Result.error(500, "上传失败: " + e.getMessage());
        }
    }

    /**
     * 上传公告附件。文件本体与公告记录分开保存，便于先在编辑器中选择文件，保存公告后再建立关联。
     */
    @PostMapping("/notice-attachment")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Result<NoticeAttachmentUploadResult> uploadNoticeAttachment(@RequestParam("file") MultipartFile file) {
        try {
            if (file.isEmpty()) {
                return Result.error(400, "文件不能为空");
            }

            String originalFilename = file.getOriginalFilename();
            if (originalFilename == null || originalFilename.isBlank()) {
                return Result.error(400, "无效的文件名");
            }

            int dotIndex = originalFilename.lastIndexOf('.');
            String extension = dotIndex >= 0 ? originalFilename.substring(dotIndex + 1).toLowerCase() : "";
            List<String> allowedTypes = Arrays.asList(
                    "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt",
                    "zip", "rar", "jpg", "jpeg", "png", "gif", "webp"
            );
            if (!allowedTypes.contains(extension)) {
                return Result.error(400, "不支持的附件类型");
            }

            if (file.getSize() > 100 * 1024 * 1024) {
                return Result.error(400, "附件大小不能超过100MB");
            }

            if (Set.of("jpg", "jpeg", "png", "gif", "webp").contains(extension)) {
                try (InputStream input = file.getInputStream()) {
                    if (ImageIO.read(input) == null) {
                        return Result.error(400, "图片内容无效");
                    }
                }
            }

            String fileUrl = saveFile(file, "notice-attachments");
            NoticeAttachmentUploadResult result = new NoticeAttachmentUploadResult(
                    originalFilename, fileUrl, file.getSize(), file.getContentType()
            );
            log.info("公告附件上传成功: originalFilename={}, url={}", originalFilename, fileUrl);
            return Result.success(result);
        } catch (IOException e) {
            log.error("公告附件上传失败", e);
            return Result.error(500, "上传失败: " + e.getMessage());
        }
    }

    private void validateContentSignature(MultipartFile file, String extension) throws IOException {
        byte[] header = new byte[8];
        int read;
        try (InputStream input = file.getInputStream()) {
            read = input.read(header);
        }
        if (read <= 0) throw new IOException("无法读取文件内容");
        if (Set.of("jpg", "jpeg", "png", "gif").contains(extension)) {
            BufferedImage image;
            try (InputStream input = file.getInputStream()) {
                image = ImageIO.read(input);
            }
            if (image == null) throw new IOException("图片内容无效");
        } else if ("pdf".equals(extension) && !new String(header, 0, Math.min(read, 5), java.nio.charset.StandardCharsets.US_ASCII).startsWith("%PDF-")) {
            throw new IOException("PDF文件内容无效");
        } else if (Set.of("zip", "docx", "pptx").contains(extension)
                && !(header[0] == 'P' && header[1] == 'K')) {
            throw new IOException("压缩文档内容无效");
        }
    }

    /** 课程创建与编辑专用封面上传，仅课程教师和管理员可用。 */
    @PostMapping("/course-cover")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN', 'SUPER_ADMIN')")
    public Result<String> uploadCourseCover(@RequestParam("file") MultipartFile file) {
        try {
            if (file.isEmpty()) return Result.error(400, "文件不能为空");
            String originalFilename = file.getOriginalFilename();
            if (originalFilename == null || !isImageFile(originalFilename)) {
                return Result.error(400, "只支持 JPG、JPEG、PNG、GIF、WebP 格式的课程封面");
            }
            if (file.getSize() > 10 * 1024 * 1024) return Result.error(400, "课程封面不能超过10MB");
            try (InputStream input = file.getInputStream()) {
                BufferedImage image = ImageIO.read(input);
                if (image == null) return Result.error(400, "图片内容无效");
                if (image.getWidth() < 320 || image.getHeight() < 180) {
                    return Result.error(400, "课程封面尺寸不能小于320×180像素");
                }
            }
            return Result.success(saveFile(file, "course-covers"));
        } catch (IOException e) {
            log.error("课程封面上传失败", e);
            return Result.error(500, "课程封面上传失败");
        }
    }

    /**
     * 上传图片（通用）
     */
    @PostMapping("/image")
    @PreAuthorize("isAuthenticated()")
    public Result<String> uploadImage(@RequestParam("file") MultipartFile file) {
        try {
            if (file.isEmpty()) {
                return Result.error(400, "文件不能为空");
            }

            String originalFilename = file.getOriginalFilename();
            if (originalFilename == null || !isImageFile(originalFilename)) {
                return Result.error(400, "只支持 JPG、JPEG、PNG、GIF、WebP 格式的图片");
            }

            if (file.getSize() > 10 * 1024 * 1024) {
                return Result.error(400, "图片大小不能超过 10MB");
            }

            try {
                BufferedImage image = ImageIO.read(file.getInputStream());
                if (image != null) {
                    int width = image.getWidth();
                    int height = image.getHeight();
                    if (width < 800 || height < 300) {
                        return Result.error(400, String.format("图片尺寸过小，当前 %d×%d，建议至少 800×300 像素", width, height));
                    }
                }
            } catch (Exception e) {
                log.warn("读取图片尺寸失败: {}", e.getMessage());
            }

            String fileUrl = saveFile(file, "images");
            log.info("图片上传成功: {}", fileUrl);
            return Result.success(fileUrl);
        } catch (IOException e) {
            log.error("图片上传失败", e);
            return Result.error(500, "上传失败: " + e.getMessage());
        }
    }

    /**
     * 注册时上传头像
     */
    @PostMapping("/register-avatar")
    public Result<String> uploadRegisterAvatar(@RequestParam("file") MultipartFile file) {
        try {
            if (file.isEmpty()) {
                return Result.error(400, "文件不能为空");
            }

            String originalFilename = file.getOriginalFilename();
            if (originalFilename == null || !isImageFile(originalFilename)) {
                return Result.error(400, "只支持 JPG、JPEG、PNG、GIF、WebP 格式的图片");
            }

            if (file.getSize() > 5 * 1024 * 1024) {
                return Result.error(400, "图片大小不能超过 5MB");
            }

            BufferedImage image = ImageIO.read(file.getInputStream());
            if (image == null) {
                return Result.error(400, "无法识别的图片文件");
            }

            if (image.getWidth() < 64 || image.getHeight() < 64) {
                return Result.error(400, "图片尺寸不能小于 64x64 像素");
            }

            String fileUrl = saveFile(file, "avatars");
            log.info("注册头像上传成功: {}", fileUrl);
            return Result.success(fileUrl);
        } catch (IOException e) {
            log.error("注册头像上传失败", e);
            return Result.error(500, "上传失败: " + e.getMessage());
        }
    }

    /**
     * 上传轮播图
     */
    @PostMapping("/carousel")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Result<String> uploadCarouselImage(@RequestParam("file") MultipartFile file) {
        try {
            if (file.isEmpty()) {
                return Result.error(400, "文件不能为空");
            }

            String originalFilename = file.getOriginalFilename();
            if (originalFilename == null || !isImageFile(originalFilename)) {
                return Result.error(400, "只支持 JPG、JPEG、PNG、GIF、WebP 格式的图片");
            }

            if (file.getSize() > 10 * 1024 * 1024) {
                return Result.error(400, "图片大小不能超过 10MB");
            }

            try {
                BufferedImage image = ImageIO.read(file.getInputStream());
                if (image != null) {
                    int width = image.getWidth();
                    int height = image.getHeight();
                    if (width < 800 || height < 300) {
                        return Result.error(400, String.format("图片尺寸过小，当前 %d×%d，建议至少 800×300 像素", width, height));
                    }
                }
            } catch (Exception e) {
                log.warn("读取图片尺寸失败: {}", e.getMessage());
            }

            String fileUrl = saveFile(file, "carousel");
            log.info("轮播图上传成功: {}", fileUrl);
            return Result.success(fileUrl);
        } catch (IOException e) {
            log.error("轮播图上传失败", e);
            return Result.error(500, "上传失败: " + e.getMessage());
        }
    }

    /**
     * 批量上传图片
     */
    @PostMapping("/images")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Result<List<String>> uploadImages(@RequestParam("files") List<MultipartFile> files) {
        List<String> urls = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        for (MultipartFile file : files) {
            try {
                if (file.isEmpty()) {
                    errors.add("文件为空");
                    continue;
                }

                String originalFilename = file.getOriginalFilename();
                if (originalFilename == null || !isImageFile(originalFilename)) {
                    errors.add((originalFilename == null ? "unknown" : originalFilename) + ": 不支持的图片格式");
                    continue;
                }

                if (file.getSize() > 10 * 1024 * 1024) {
                    errors.add(originalFilename + ": 图片大小不能超过 10MB");
                    continue;
                }

                String fileUrl = saveFile(file, "images");
                urls.add(fileUrl);
            } catch (IOException e) {
                log.error("批量上传失败", e);
                errors.add((file.getOriginalFilename() == null ? "unknown" : file.getOriginalFilename()) + ": " + e.getMessage());
            }
        }

        if (!errors.isEmpty()) {
            log.warn("部分文件上传失败: {}", errors);
        }

        return Result.success(urls);
    }

    /**
     * 判断是否为图片文件
     */
    private boolean isImageFile(String filename) {
        if (filename == null) {
            return false;
        }

        int dotIndex = filename.lastIndexOf(".");
        if (dotIndex < 0 || dotIndex == filename.length() - 1) {
            return false;
        }

        String extension = filename.substring(dotIndex).toLowerCase();
        return extension.matches("\\.(jpg|jpeg|png|gif|webp|bmp)$");
    }

    /**
     * 保存文件
     */
    private String saveFile(MultipartFile file, String subDir) throws IOException {
        String projectRoot = System.getProperty("user.dir");

        String baseUploadDir = projectRoot + File.separator + uploadPath;
        File baseDir = new File(baseUploadDir);
        if (!baseDir.exists() && !baseDir.mkdirs()) {
            throw new IOException("创建上传根目录失败");
        }

        String datePath = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM"));
        File destDir = new File(baseDir, subDir + File.separator + datePath);
        if (!destDir.exists() && !destDir.mkdirs()) {
            throw new IOException("创建存储目录失败");
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.lastIndexOf(".") < 0) {
            throw new IOException("无效的文件名");
        }

        String extension = originalFilename.substring(originalFilename.lastIndexOf("."));
        String filename = UUID.randomUUID().toString() + extension;

        File destFile = new File(destDir, filename);
        file.transferTo(destFile);

        return "/uploads/" + subDir + "/" + datePath + "/" + filename;
    }

    @Data
    @AllArgsConstructor
    public static class NoticeAttachmentUploadResult {
        private String fileName;
        private String fileUrl;
        private Long fileSize;
        private String fileType;
    }
}
