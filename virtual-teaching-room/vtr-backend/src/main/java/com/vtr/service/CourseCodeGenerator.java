package com.vtr.service;

import com.vtr.common.exception.BusinessException;
import com.vtr.entity.Course;
import com.vtr.repository.CourseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.List;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class CourseCodeGenerator {

    private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final int CODE_LENGTH = 8;
    private static final int MAX_ATTEMPTS = 100;
    private static final Pattern LEGACY_GENERATED_CODE = Pattern.compile("CRS-\\d+(?:-\\d+)?");

    private final CourseRepository courseRepository;
    private final SecureRandom random = new SecureRandom();

    public String generateUniqueCode() {
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            StringBuilder code = new StringBuilder(CODE_LENGTH);
            for (int index = 0; index < CODE_LENGTH; index++) {
                code.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
            }
            String candidate = code.toString();
            if (!courseRepository.existsByCourseCode(candidate)) {
                return candidate;
            }
        }
        throw new BusinessException("课程代码生成失败，请重试");
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void migrateLegacyGeneratedCodes() {
        List<Course> courses = courseRepository.findAll();
        boolean changed = false;
        for (Course course : courses) {
            String code = course.getCourseCode();
            if (code == null || code.isBlank() || LEGACY_GENERATED_CODE.matcher(code).matches()) {
                course.setCourseCode(generateUniqueCode());
                changed = true;
            }
        }
        if (changed) {
            courseRepository.saveAll(courses);
        }
    }
}
