package com.vtr.controller;

import com.vtr.common.PageResult;
import com.vtr.common.Result;
import com.vtr.common.exception.BusinessException;
import com.vtr.dto.CoursewareCreateDTO;
import com.vtr.dto.CoursewareQueryDTO;
import com.vtr.dto.CoursewareUpdateDTO;
import com.vtr.dto.StructuredOutlineCreateDTO;
import com.vtr.dto.VideoAuditDTO;
import com.vtr.dto.VideoProgressDTO;
import com.vtr.service.CoursewareService;
import com.vtr.vo.CoursewareVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaTypeFactory;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.awt.Desktop;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

import javax.validation.Valid;

@Slf4j
@RestController
@RequestMapping("/api/courseware")
@RequiredArgsConstructor
public class CoursewareController {

    private final CoursewareService coursewareService;

    @Value("${upload.path:./uploads/}")
    private String uploadPath;

    private Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Object principal = authentication.getPrincipal();
        if (principal instanceof com.vtr.security.CustomUserDetails) {
            return ((com.vtr.security.CustomUserDetails) principal).getId();
        }
        throw new RuntimeException("无法获取用户ID");
    }

    private String getCurrentUserRole() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Object principal = authentication.getPrincipal();
        if (principal instanceof com.vtr.security.CustomUserDetails) {
            String role = ((com.vtr.security.CustomUserDetails) principal).getAuthorities().stream()
                    .findFirst()
                    .map(auth -> auth.getAuthority().replace("ROLE_", ""))
                    .orElse("STUDENT");
            return role;
        }
        return "STUDENT";
    }

    private boolean isAdminRole(String role) {
        return "ADMIN".equals(role) || "SUPER_ADMIN".equals(role);
    }

    /**
     * 获取课件列表（根据角色自动返回不同数据）
     */
    @GetMapping("/list")
    @PreAuthorize("isAuthenticated()")
    public Result<PageResult<CoursewareVO>> getCoursewareList(CoursewareQueryDTO queryDTO) {
        Long userId = getCurrentUserId();
        String userRole = getCurrentUserRole();

        // 管理员/超级管理员：可以看到所有课件
        if ("ADMIN".equals(userRole) || "SUPER_ADMIN".equals(userRole)) {
            return Result.success(coursewareService.getAllCoursewares(queryDTO));
        }
        // 教师：可以看到自己上传的课件 + 公开课件
        if ("TEACHER".equals(userRole)) {
            return Result.success(coursewareService.getCoursewaresForTeacher(userId, queryDTO));
        }
        // 学生：可以看到公开课件 + 班级课件
        return Result.success(coursewareService.getVisibleCoursewares(userId, queryDTO));
    }

    @GetMapping("/pending-reviews")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Result<PageResult<CoursewareVO>> getPendingReviews(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size,
            @RequestParam(defaultValue = "PENDING") String status) {
        return Result.success(coursewareService.getPendingReviews(page, size, status));
    }

    /** Chapter video workspace. Returned records depend on the current user's role. */
    @GetMapping("/videos")
    @PreAuthorize("isAuthenticated()")
    public Result<List<CoursewareVO>> getTeachingVideos(CoursewareQueryDTO queryDTO) {
        return Result.success(coursewareService.getTeachingVideos(getCurrentUserId(), getCurrentUserRole(), queryDTO));
    }

    @GetMapping("/videos/overview")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN', 'SUPER_ADMIN')")
    public Result<Map<String, Object>> getTeachingVideoOverview(@RequestParam Long courseId) {
        return Result.success(coursewareService.getTeachingVideoOverview(
                getCurrentUserId(), getCurrentUserRole(), courseId));
    }

    /**
     * 上传课件（仅教师/管理员）
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN', 'SUPER_ADMIN')")
    public Result<Long> uploadCourseware(@RequestBody @Valid CoursewareCreateDTO dto) {
        if (("teaching-video".equals(dto.getResourceType()) || "knowledge-map".equals(dto.getResourceType())
                || "teaching-outline".equals(dto.getResourceType()) || "teaching-courseware".equals(dto.getResourceType()))
                && isAdminRole(getCurrentUserRole())) {
            throw new BusinessException("管理员不能建设课程教学内容，请由课程教师上传");
        }
        Long teacherId = getCurrentUserId();
        Long id = coursewareService.uploadCourseware(dto, teacherId);
        return Result.success(id);
    }

    /** Creates a teaching outline from the structured editor; no PDF upload is required. */
    @PostMapping("/structured-outline")
    @PreAuthorize("hasRole('TEACHER')")
    public Result<Long> createStructuredTeachingOutline(@RequestBody @Valid StructuredOutlineCreateDTO dto) {
        return Result.success(coursewareService.createStructuredTeachingOutline(dto, getCurrentUserId()));
    }

    /**
     * 更新课件（仅教师/管理员）
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN', 'SUPER_ADMIN')")
    public Result<Void> updateCourseware(@PathVariable Long id, @RequestBody @Valid CoursewareUpdateDTO dto) {
        coursewareService.updateCourseware(id, dto, getCurrentUserId(), isAdminRole(getCurrentUserRole()));
        return Result.success();
    }

    @PostMapping("/{id}/video-version")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN', 'SUPER_ADMIN')")
    public Result<Long> createTeachingVideoVersion(@PathVariable Long id, @RequestBody @Valid CoursewareUpdateDTO dto) {
        return Result.success(coursewareService.createTeachingVideoVersion(
                id, dto, getCurrentUserId(), isAdminRole(getCurrentUserRole())));
    }

    /** 上传视频字幕/转写文本；保存后异步重新建立视频 AI 检索索引。 */
    @PostMapping("/{id}/ai-transcript")
    @PreAuthorize("hasRole('TEACHER')")
    public Result<Void> uploadVideoTranscript(@PathVariable Long id,
                                              @RequestParam("file") MultipartFile file) {
        coursewareService.uploadVideoTranscript(id, file, getCurrentUserId());
        return Result.success();
    }

    @PostMapping("/{id}/video-review")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Result<Void> reviewTeachingVideo(@PathVariable Long id, @RequestBody @Valid VideoAuditDTO dto) {
        coursewareService.reviewTeachingVideo(id, dto, getCurrentUserId());
        return Result.success();
    }

    @PostMapping("/{id}/courseware-review")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Result<Void> reviewTeachingCourseware(@PathVariable Long id, @RequestBody @Valid VideoAuditDTO dto) {
        coursewareService.reviewTeachingCourseware(id, dto, getCurrentUserId());
        return Result.success();
    }

    @PostMapping("/{id}/outline-review")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Result<Void> reviewTeachingOutline(@PathVariable Long id, @RequestBody @Valid VideoAuditDTO dto) {
        coursewareService.reviewTeachingOutline(id, dto, getCurrentUserId());
        return Result.success();
    }

    @PostMapping("/{id}/outline-archive")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Result<Void> archiveTeachingOutline(@PathVariable Long id) {
        coursewareService.archiveTeachingOutline(id, getCurrentUserId());
        return Result.success();
    }

    @PostMapping("/{id}/knowledge-map-review")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Result<Void> reviewKnowledgeMap(@PathVariable Long id, @RequestBody @Valid VideoAuditDTO dto) {
        coursewareService.reviewKnowledgeMap(id, dto, getCurrentUserId());
        return Result.success();
    }

    @PostMapping("/{id}/courseware-archive")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Result<Void> archiveTeachingCourseware(@PathVariable Long id) {
        coursewareService.archiveTeachingCourseware(id, getCurrentUserId());
        return Result.success();
    }

    @PostMapping("/{id}/video-archive")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Result<Void> archiveTeachingVideo(@PathVariable Long id) {
        coursewareService.archiveTeachingVideo(id, getCurrentUserId());
        return Result.success();
    }

    @PostMapping("/{id}/video-withdraw")
    @PreAuthorize("hasRole('TEACHER')")
    public Result<Void> withdrawTeachingVideo(@PathVariable Long id) {
        coursewareService.withdrawTeachingVideo(id, getCurrentUserId());
        return Result.success();
    }

    @PutMapping("/{id}/video-progress")
    @PreAuthorize("hasRole('STUDENT')")
    public Result<Void> saveVideoProgress(@PathVariable Long id, @RequestBody @Valid VideoProgressDTO dto) {
        coursewareService.saveVideoProgress(id, getCurrentUserId(), dto);
        return Result.success();
    }

    @GetMapping("/{id}/video-progress/me")
    @PreAuthorize("hasRole('STUDENT')")
    public Result<Map<String, Object>> getMyVideoProgress(@PathVariable Long id) {
        return Result.success(coursewareService.getMyVideoProgress(id, getCurrentUserId()));
    }

    @GetMapping("/{id}/video-statistics")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN', 'SUPER_ADMIN')")
    public Result<Map<String, Object>> getVideoStatistics(@PathVariable Long id) {
        return Result.success(coursewareService.getVideoStatistics(id, getCurrentUserId(), getCurrentUserRole()));
    }

    /**
     * 删除课件（仅教师/管理员）
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN', 'SUPER_ADMIN')")
    public Result<Void> deleteCourseware(@PathVariable Long id) {
        coursewareService.deleteCourseware(id, getCurrentUserId(), isAdminRole(getCurrentUserRole()));
        return Result.success();
    }

    /**
     * 获取课件详情（所有登录用户）
     */
    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public Result<CoursewareVO> getCourseware(@PathVariable Long id) {
        Long userId = getCurrentUserId();
        String userRole = getCurrentUserRole();
        return Result.success(coursewareService.getCoursewareById(id, userId, userRole));
    }

    /**
     * 下载课件
     */
    @PostMapping("/{id}/download")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Resource> downloadCourseware(@PathVariable Long id) {
        Long userId = getCurrentUserId();
        String userRole = getCurrentUserRole();
        String fileUrl = coursewareService.downloadCourseware(id, userId, userRole);
        String fileName = coursewareService.getAuthorizedFileName(id, userId, userRole);
        return streamFile(fileUrl, true, fileName);
    }

    /** Streams an authorized courseware file for preview without incrementing downloads. */
    @GetMapping("/{id}/preview")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Resource> previewCourseware(@PathVariable("id") Long id) {
        String fileUrl = coursewareService.previewCourseware(id, getCurrentUserId(), getCurrentUserRole());
        return streamFile(fileUrl, false, null);
    }

    /** Opens the original courseware with the operating system's associated app. */
    @PostMapping("/{id}/open-local")
    @PreAuthorize("isAuthenticated()")
    public Result<Void> openCoursewareLocally(@PathVariable("id") Long id) {
        String fileUrl = coursewareService.previewCourseware(id, getCurrentUserId(), getCurrentUserRole());
        Path file = resolveStoredFile(fileUrl);
        if (file == null) {
            throw new BusinessException("课件文件不存在，请重新上传");
        }
        try {
            if (!Desktop.isDesktopSupported()) {
                throw new BusinessException("当前运行环境不支持调用本地软件，请下载后打开");
            }
            Desktop desktop = Desktop.getDesktop();
            if (!desktop.isSupported(Desktop.Action.OPEN)) {
                throw new BusinessException("当前系统未提供本地文件打开功能，请下载后打开");
            }
            desktop.open(file.toFile());
            return Result.success();
        } catch (java.io.IOException | UnsupportedOperationException | SecurityException ex) {
            log.warn("无法调用本地软件打开课件: {}", file, ex);
            throw new BusinessException("无法调用本地软件，请先下载后打开");
        }
    }

    private ResponseEntity<Resource> streamFile(String fileUrl, boolean attachment, String originalFileName) {
        Path file = resolveStoredFile(fileUrl);
        if (file == null) {
            return ResponseEntity.notFound().build();
        }
        try {
            Resource resource = new UrlResource(file.toUri());
            MediaType mediaType = MediaTypeFactory.getMediaType(file.getFileName().toString())
                    .orElse(MediaType.APPLICATION_OCTET_STREAM);
            ResponseEntity.BodyBuilder response = ResponseEntity.ok().contentType(mediaType);
            if (attachment) {
                String safeName = originalFileName == null || originalFileName.isBlank()
                        ? file.getFileName().toString() : originalFileName.replaceAll("[\\\\\"\\r\\n]", "_");
                response.header("Content-Disposition", "attachment; filename=\"" + safeName + "\"");
            }
            return response.body(resource);
        } catch (java.net.MalformedURLException ex) {
            return ResponseEntity.notFound().build();
        }
    }

    private Path resolveStoredFile(String fileUrl) {
        if (fileUrl == null || !fileUrl.startsWith("/uploads/courseware/")) return null;
        String relative = fileUrl.substring("/uploads/".length()).replace('/', File.separatorChar);
        Path base = Paths.get(uploadPath).toAbsolutePath().normalize();
        Path file = base.resolve(relative).normalize();
        return file.startsWith(base) && java.nio.file.Files.isRegularFile(file) ? file : null;
    }
}
