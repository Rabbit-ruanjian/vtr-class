package com.vtr.controller;

import com.vtr.common.Result;
import com.vtr.common.exception.BusinessException;
import com.vtr.repository.CourseRepository;
import com.vtr.repository.CourseMemberRepository;
import com.vtr.security.CustomUserDetails;
import com.vtr.service.QuestionDocumentParser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/learning-resources/questions")
@RequiredArgsConstructor
public class QuestionDocumentController {
    private final QuestionDocumentParser parser;
    private final CourseRepository courseRepository;
    private final CourseMemberRepository courseMemberRepository;

    @PostMapping("/parse-document")
    @PreAuthorize("hasRole('TEACHER')")
    public Result<List<Map<String, String>>> parseDocument(@RequestParam Long courseId,
                                                            @RequestParam Long sectionId,
                                                            @RequestParam("file") MultipartFile file) {
        CustomUserDetails user = (CustomUserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        boolean canManage = courseRepository.findById(courseId).map(course -> user.getId().equals(course.getCreatedBy())).orElse(false)
                || courseMemberRepository.findByCourseIdAndUserId(courseId, user.getId())
                .map(member -> "ACTIVE".equals(member.getStatus()) && Set.of("OWNER", "CO_TEACHER", "TEACHING_ASSISTANT").contains(member.getRole()))
                .orElse(false);
        if (!canManage) throw new BusinessException("只能建设自己负责课程的题库");
        if (sectionId == null) throw new BusinessException("请先选择课程小节");
        if (file == null || file.isEmpty()) throw new BusinessException("文件不能为空");
        if (file.getSize() > 20 * 1024 * 1024) throw new BusinessException("题库文档不能超过20MB");
        try {
            return Result.success(parser.parse(file));
        } catch (IllegalArgumentException e) {
            throw new BusinessException(e.getMessage());
        } catch (IOException e) {
            throw new BusinessException("题库文档读取失败，请检查文件内容");
        }
    }
}
