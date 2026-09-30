package com.vtr.service.impl;

import com.vtr.common.PageResult;
import com.vtr.common.exception.BusinessException;
import com.vtr.common.exception.NotFoundException;
import com.vtr.dto.CoursewareCreateDTO;
import com.vtr.dto.CoursewareQueryDTO;
import com.vtr.dto.CoursewareUpdateDTO;
import com.vtr.dto.StructuredOutlineCreateDTO;
import com.vtr.dto.VideoAuditDTO;
import com.vtr.dto.VideoProgressDTO;
import com.vtr.entity.Classroom;
import com.vtr.entity.Course;
import com.vtr.entity.CourseChapter;
import com.vtr.entity.CourseSection;
import com.vtr.entity.Courseware;
import com.vtr.entity.ContentAudit;
import com.vtr.entity.User;
import com.vtr.entity.VideoLearningProgress;
import com.vtr.repository.ClassroomRepository;
import com.vtr.repository.ClassroomStudentRelationRepository;
import com.vtr.repository.CoursewareRepository;
import com.vtr.repository.CourseMemberRepository;
import com.vtr.repository.CourseChapterRepository;
import com.vtr.repository.CourseSectionRepository;
import com.vtr.repository.CourseRepository;
import com.vtr.repository.UserRepository;
import com.vtr.repository.VideoLearningProgressRepository;
import com.vtr.repository.NotificationRepository;
import com.vtr.service.CoursewareService;
import com.vtr.service.AdminScopeService;
import com.vtr.service.ContentAuditService;
import com.vtr.service.ContentRiskService;
import com.vtr.service.NotificationService;
import com.vtr.event.CoursewareChangedEvent;
import com.vtr.vo.CoursewareVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.Set;
import java.util.HashSet;
import java.util.Map;
import java.util.HashMap;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.springframework.beans.factory.annotation.Value;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.web.multipart.MultipartFile;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

@Slf4j
@Service
@RequiredArgsConstructor
public class CoursewareServiceImpl implements CoursewareService {

    private static final String TEACHING_OUTLINE = "teaching-outline";
    private static final String TEACHING_VIDEO = "teaching-video";
    private static final String TEACHING_COURSEWARE = "teaching-courseware";
    private static final String KNOWLEDGE_MAP = "knowledge-map";
    private static final Set<String> VIDEO_TYPES = Set.of("CORE", "EXERCISE", "LAB", "EXTENSION");

    private static final Set<String> SUPPORTED_RESOURCE_TYPES = Set.of(
            "knowledge-map", "assignments",
            "teaching-video", "teaching-outline", "teaching-courseware"
    );

    private final CoursewareRepository coursewareRepository;
    private final CourseMemberRepository courseMemberRepository;
    private final CourseChapterRepository courseChapterRepository;
    private final CourseSectionRepository courseSectionRepository;
    private final UserRepository userRepository;
    private final ClassroomRepository classroomRepository;
    private final ClassroomStudentRelationRepository classroomStudentRelationRepository;
    private final CourseRepository courseRepository;
    private final VideoLearningProgressRepository videoLearningProgressRepository;
    private final ObjectMapper objectMapper;
    private final NotificationService notificationService;
    private final NotificationRepository notificationRepository;
    private final AdminScopeService adminScopeService;
    private final ContentAuditService contentAuditService;
    private final ContentRiskService contentRiskService;
    private final ApplicationEventPublisher eventPublisher;

    @Value("${upload.path:./uploads/}")
    private String uploadPath;

    @Override
    @Transactional
    public Long uploadCourseware(CoursewareCreateDTO dto, Long teacherId) {
        Long schoolId = requireUserSchool(teacherId);
        validateResourceType(dto.getResourceType());
        validateTeachingOutline(dto.getResourceType(), dto.getCourseId(), dto.getChapter(), dto.getSectionId(), dto.getFileName(), dto.getFileType());
        validateTeachingVideo(dto.getResourceType(), dto.getFileName(), dto.getFileType(), dto.getVideoType());
        validateTeachingCourseware(dto.getResourceType(), dto.getFileName(), dto.getFileType());
        boolean structuredKnowledgeMap = KNOWLEDGE_MAP.equals(dto.getResourceType())
                && "structured://knowledge-map".equals(dto.getFileUrl());
        if (!structuredKnowledgeMap) {
            validateFileReference(dto.getFileUrl(), dto.getFileName(), dto.getFileSize());
        }
        validateResourceScope(dto.getCourseId(), dto.getClassroomId(), dto.getVisibility(), teacherId, false);
        if (TEACHING_OUTLINE.equals(dto.getResourceType())) {
            ensureSingleTeachingOutline(dto.getCourseId(), null, null, null);
        }
        if (KNOWLEDGE_MAP.equals(dto.getResourceType())) {
            ensureSingleKnowledgeMap(dto.getCourseId());
        }
        ContentRiskService.RiskAssessment risk = contentRiskService.assess(
                dto.getTitle(), dto.getDescription(), dto.getFileName(), dto.getKnowledgePoint());
        boolean publicRelease = "PUBLIC".equals(dto.getVisibility());
        boolean riskReview = !publicRelease && risk.requiresManualReview();
        Courseware courseware = Courseware.builder()
                .title(dto.getTitle())
                .description(dto.getDescription())
                .fileUrl(dto.getFileUrl())
                .fileName(dto.getFileName())
                .fileType(dto.getFileType())
                .resourceType(dto.getResourceType())
                .videoType(dto.getVideoType())
                .knowledgePoint(dto.getKnowledgePoint())
                .featured(Boolean.TRUE.equals(dto.getFeatured()))
                .courseId(dto.getCourseId()).semester(dto.getSemester())
                .chapter(KNOWLEDGE_MAP.equals(dto.getResourceType()) || TEACHING_OUTLINE.equals(dto.getResourceType()) ? null : dto.getChapter())
                .sectionId(KNOWLEDGE_MAP.equals(dto.getResourceType()) || TEACHING_OUTLINE.equals(dto.getResourceType()) ? null : dto.getSectionId())
                .gradeLevel(dto.getGradeLevel()).version(dto.getVersion())
                .fileSize(dto.getFileSize())
                .teacherId(teacherId)
                .schoolId(schoolId)
                .visibility(dto.getVisibility())
                .targetAudience(dto.getTargetAudience())
                .classroomId(dto.getClassroomId())
                .downloadCount(0)
                .viewCount(0)
                .status(publicRelease || riskReview ? "PENDING" : "ACTIVE")
                .versionNumber(TEACHING_VIDEO.equals(dto.getResourceType()) ? 1 : null)
                .build();

        if (!publicRelease && !riskReview) {
            courseware.setReviewedBy(teacherId);
            courseware.setReviewedAt(LocalDateTime.now());
        } else if (publicRelease && risk.requiresManualReview()) {
            courseware.setAuditRemark("系统风险提示：" + risk.reason());
        }

        Courseware saved = coursewareRepository.save(courseware);
        if (publicRelease) {
            notifyAdminsAboutResource(saved);
        } else if (riskReview) {
            queueResourceRisk(saved, teacherId, risk);
        } else {
            notifyStudentsAboutResource(saved);
        }
        eventPublisher.publishEvent(CoursewareChangedEvent.reindex(saved.getId()));
        log.info("上传课件: teacherId={}, coursewareId={}, title={}", teacherId, saved.getId(), saved.getTitle());
        return saved.getId();
    }

    @Override
    @Transactional
    public Long createStructuredTeachingOutline(StructuredOutlineCreateDTO dto, Long teacherId) {
        Long schoolId = requireUserSchool(teacherId);
        validateTeachingOutlineScope(dto.getCourseId(), null, null);
        validateResourceScope(dto.getCourseId(), dto.getClassroomId(), dto.getVisibility(), teacherId, false);
        ensureSingleTeachingOutline(dto.getCourseId(), null, null, null);

        String serialized = StringUtils.hasText(dto.getDescription()) ? dto.getDescription() : "{}";
        try {
            JsonNode config = objectMapper.readTree(serialized);
            if (config == null || !config.isObject() || !config.has("nodes")
                    || !config.get("nodes").isArray() || config.get("nodes").isEmpty()) {
                throw new BusinessException("请先完成教学大纲节点编辑后再提交");
            }
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException("教学大纲结构解析失败，请检查节点内容");
        }
        String generatedFileName = "teaching-outline-" + UUID.randomUUID() + ".json";
        String generatedFileUrl = saveStructuredOutlineFile(generatedFileName, serialized);
        ContentRiskService.RiskAssessment risk = contentRiskService.assess(dto.getTitle(), serialized);
        boolean publicRelease = "PUBLIC".equals(dto.getVisibility());
        boolean riskReview = !publicRelease && risk.requiresManualReview();
        Courseware outline = Courseware.builder()
                .title(dto.getTitle().trim())
                .description(serialized)
                .fileUrl(generatedFileUrl)
                .fileName(generatedFileName)
                .fileType("json")
                .resourceType(TEACHING_OUTLINE)
                .courseId(dto.getCourseId())
                .chapter(null)
                .sectionId(null)
                .fileSize((long) serialized.getBytes(StandardCharsets.UTF_8).length)
                .teacherId(teacherId)
                .schoolId(schoolId)
                .visibility(dto.getVisibility())
                .targetAudience(dto.getTargetAudience())
                .classroomId(dto.getClassroomId())
                .downloadCount(0)
                .viewCount(0)
                .status(publicRelease || riskReview ? "PENDING" : "ACTIVE")
                .build();
        if (!publicRelease && !riskReview) {
            outline.setReviewedBy(teacherId);
            outline.setReviewedAt(LocalDateTime.now());
        } else if (publicRelease && risk.requiresManualReview()) {
            outline.setAuditRemark("系统风险提示：" + risk.reason());
        }

        Courseware saved = coursewareRepository.save(outline);
        if (publicRelease) {
            notifyAdminsAboutResource(saved);
        } else if (riskReview) {
            queueResourceRisk(saved, teacherId, risk);
        } else {
            notifyStudentsAboutResource(saved);
        }
        eventPublisher.publishEvent(CoursewareChangedEvent.reindex(saved.getId()));
        log.info("结构化教学大纲提交: teacherId={}, coursewareId={}, title={}, aiGenerated={}", teacherId, saved.getId(), saved.getTitle(), dto.isAiGenerated());
        return saved.getId();
    }

    @Override
    @Transactional
    public void updateCourseware(Long id, CoursewareUpdateDTO dto, Long userId, boolean isAdmin) {
        Courseware courseware = coursewareRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("课件", id));

        requireResourceSchool(courseware, userId, isAdmin);

        if (isAdmin && (TEACHING_VIDEO.equals(courseware.getResourceType())
                || TEACHING_OUTLINE.equals(courseware.getResourceType())
                || TEACHING_COURSEWARE.equals(courseware.getResourceType())
                || KNOWLEDGE_MAP.equals(courseware.getResourceType()))) {
            throw new BusinessException("管理员只能处置课程教学内容，不能修改内容本身");
        }
        if (!isAdmin && !courseware.getTeacherId().equals(userId)
                && (!TEACHING_VIDEO.equals(courseware.getResourceType()) && !canContribute(courseware.getCourseId(), userId))) {
            throw new BusinessException("只能修改自己的课件");
        }

        if (dto.getResourceType() != null) validateResourceType(dto.getResourceType());

        String previousFileUrl = courseware.getFileUrl();
        if (dto.getFileUrl() != null) {
            validateFileReference(dto.getFileUrl(), dto.getFileName(), dto.getFileSize());
        }
        if (dto.getTitle() != null) courseware.setTitle(dto.getTitle());
        if (dto.getDescription() != null) courseware.setDescription(dto.getDescription());
        if (dto.getFileUrl() != null) courseware.setFileUrl(dto.getFileUrl());
        if (dto.getFileName() != null) courseware.setFileName(dto.getFileName());
        if (dto.getFileType() != null) courseware.setFileType(dto.getFileType());
        if (dto.getFileSize() != null) courseware.setFileSize(dto.getFileSize());
        if (dto.getResourceType() != null) courseware.setResourceType(dto.getResourceType());
        if (dto.getVideoType() != null) courseware.setVideoType(dto.getVideoType());
        if (dto.getKnowledgePoint() != null) courseware.setKnowledgePoint(dto.getKnowledgePoint());
        if (dto.getFeatured() != null) courseware.setFeatured(dto.getFeatured());
        Long courseId = dto.getCourseId() != null ? dto.getCourseId() : courseware.getCourseId();
        Long classroomId = dto.getClassroomId() != null ? dto.getClassroomId() : courseware.getClassroomId();
        String visibility = dto.getVisibility() != null ? dto.getVisibility() : courseware.getVisibility();
        String resourceType = dto.getResourceType() != null ? dto.getResourceType() : courseware.getResourceType();
        String chapter = dto.getChapter() != null ? dto.getChapter() : courseware.getChapter();
        Long sectionId = dto.getSectionId() != null ? dto.getSectionId() : courseware.getSectionId();
        String fileName = dto.getFileName() != null ? dto.getFileName() : courseware.getFileName();
        String fileType = dto.getFileType() != null ? dto.getFileType() : courseware.getFileType();
        validateTeachingOutline(resourceType, courseId, chapter, sectionId, fileName, fileType);
        String videoType = dto.getVideoType() != null ? dto.getVideoType() : courseware.getVideoType();
        validateTeachingVideo(resourceType, fileName, fileType, videoType);
        validateTeachingCourseware(resourceType, fileName, fileType);
        if (KNOWLEDGE_MAP.equals(resourceType)) {
            chapter = null;
            sectionId = null;
        }
        validateResourceScope(courseId, classroomId, visibility, userId, isAdmin);
        if (TEACHING_OUTLINE.equals(resourceType)) {
            ensureSingleTeachingOutline(courseId, null, null, courseware.getId());
        }

        if (dto.getVisibility() != null) courseware.setVisibility(dto.getVisibility());
        if (dto.getTargetAudience() != null) courseware.setTargetAudience(dto.getTargetAudience());
        if (dto.getClassroomId() != null) courseware.setClassroomId(dto.getClassroomId());
        if (dto.getCourseId() != null) courseware.setCourseId(dto.getCourseId());
        if (dto.getSemester() != null) courseware.setSemester(dto.getSemester());
        if (KNOWLEDGE_MAP.equals(resourceType) || TEACHING_OUTLINE.equals(resourceType)) {
            courseware.setChapter(null);
            courseware.setSectionId(null);
        } else {
            if (dto.getChapter() != null) courseware.setChapter(dto.getChapter());
            if (dto.getSectionId() != null) courseware.setSectionId(dto.getSectionId());
        }
        if (dto.getGradeLevel() != null) courseware.setGradeLevel(dto.getGradeLevel());
        if (dto.getVersion() != null) courseware.setVersion(dto.getVersion());

        boolean publishFieldsChanged = dto.getTitle() != null || dto.getDescription() != null
                || dto.getFileUrl() != null || dto.getFileName() != null || dto.getFileType() != null
                || dto.getResourceType() != null || dto.getVideoType() != null
                || dto.getKnowledgePoint() != null || dto.getFeatured() != null
                || dto.getVisibility() != null || dto.getTargetAudience() != null
                || dto.getClassroomId() != null || dto.getCourseId() != null
                || dto.getSemester() != null || dto.getChapter() != null || dto.getSectionId() != null
                || dto.getGradeLevel() != null || dto.getVersion() != null;
        ContentRiskService.RiskAssessment risk = contentRiskService.assess(
                courseware.getTitle(), courseware.getDescription(), courseware.getFileName(), courseware.getKnowledgePoint());
        boolean publicRelease = "PUBLIC".equals(visibility);
        boolean riskReview = publishFieldsChanged && !publicRelease && risk.requiresManualReview();
        if (publishFieldsChanged && !isAdmin) {
            courseware.setStatus(publicRelease || riskReview ? "PENDING" : "ACTIVE");
            courseware.setAuditRemark(publicRelease && risk.requiresManualReview()
                    ? "系统风险提示：" + risk.reason() : null);
            courseware.setReviewedBy(publicRelease || riskReview ? null : userId);
            courseware.setReviewedAt(publicRelease || riskReview ? null : LocalDateTime.now());
        }

        coursewareRepository.save(courseware);
        eventPublisher.publishEvent(CoursewareChangedEvent.reindex(courseware.getId()));
        if (publishFieldsChanged && !isAdmin && publicRelease) {
            contentAuditService.resolveAutomatedRisk(ContentAudit.ContentType.COURSEWARE, courseware.getId(),
                    userId, resourceAuthorName(userId), "内容已转入全校公开发布申请队列");
            notifyAdminsAboutResource(courseware);
        } else if (riskReview) {
            queueResourceRisk(courseware, userId, risk);
        } else if (publishFieldsChanged && !isAdmin) {
            contentAuditService.resolveAutomatedRisk(ContentAudit.ContentType.COURSEWARE, courseware.getId(),
                    userId, resourceAuthorName(userId), "发布者已修改内容，自动风险复核通过");
            notifyStudentsAboutResource(courseware);
        }
        if (dto.getFileUrl() != null && !dto.getFileUrl().equals(previousFileUrl)) {
            if (coursewareRepository.countByFileUrlAndStatus(previousFileUrl, "ACTIVE") == 0) {
                deleteStoredFile(previousFileUrl);
            }
        }
        log.info("更新课件: coursewareId={}", id);
    }

    @Override
    @Transactional
    public void deleteCourseware(Long id, Long userId, boolean isAdmin) {
        Courseware courseware = coursewareRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("课件", id));

        requireResourceSchool(courseware, userId, isAdmin);

        if (isAdmin && !KNOWLEDGE_MAP.equals(courseware.getResourceType()) && (TEACHING_VIDEO.equals(courseware.getResourceType())
                || TEACHING_OUTLINE.equals(courseware.getResourceType())
                || TEACHING_COURSEWARE.equals(courseware.getResourceType()))) {
            throw new BusinessException("管理员只能审核或归档课程内容，不能删除内容；知识图谱可直接删除后重建");
        }
        if (!isAdmin && !courseware.getTeacherId().equals(userId)) {
            throw new BusinessException("只能删除自己的课件");
        }
        if (TEACHING_VIDEO.equals(courseware.getResourceType())
                && !("PENDING".equals(courseware.getStatus()) || "REJECTED".equals(courseware.getStatus()))) {
            throw new BusinessException("已发布或已归档的视频不能删除，请使用撤回归档或上传新版本");
        }

        coursewareRepository.softDelete(id);
        eventPublisher.publishEvent(CoursewareChangedEvent.syncStatus(id));
        if (coursewareRepository.countByFileUrlAndStatus(courseware.getFileUrl(), "ACTIVE") == 0) {
            deleteStoredFile(courseware.getFileUrl());
        }
        log.info("删除课件: coursewareId={}", id);
    }

    @Override
    public CoursewareVO getCoursewareById(Long id, Long userId, String userRole) {
        Courseware courseware = coursewareRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("课件", id));

        requireAccess(courseware, userId, userRole);
        coursewareRepository.incrementViewCount(id);

        CoursewareVO vo = new CoursewareVO();
        BeanUtils.copyProperties(courseware, vo);

        userRepository.findById(courseware.getTeacherId()).ifPresent(teacher -> {
            vo.setTeacherName(teacher.getNickname() != null ? teacher.getNickname() : teacher.getUsername());
        });

        if (courseware.getClassroomId() != null) {
            classroomRepository.findById(courseware.getClassroomId()).ifPresent(classroom -> {
                vo.setClassName(classroom.getClassName());
            });
        }

        return vo;
    }

    @Override
    public PageResult<CoursewareVO> getCoursewaresForTeacher(Long teacherId, CoursewareQueryDTO queryDTO) {
        normalizeQuery(queryDTO);
        PageRequest pageRequest = PageRequest.of(queryDTO.getPage() - 1, queryDTO.getSize(),
                Sort.by("createdAt").descending());

        // 教师可见：
        // 1. 自己上传的课件（所有）
        // 2. 其他教师上传的公开课件（visibility=PUBLIC 且 targetAudience 包含 TEACHER 或 ALL）
        List<String> audiences = Arrays.asList("ALL", "TEACHER");

        Page<Courseware> page;
        if (StringUtils.hasText(queryDTO.getKeyword())) {
            page = coursewareRepository.findForTeacherWithKeyword(teacherId, audiences,
                    queryDTO.getResourceType(), queryDTO.getVisibility(), queryDTO.getTargetAudience(),
                    queryDTO.getFileType(), queryDTO.getCourseId(), queryDTO.getChapter(), queryDTO.getClassroomId(),
                    requireUserSchool(teacherId), queryDTO.getKeyword(), pageRequest);
        } else {
            page = coursewareRepository.findForTeacher(teacherId, audiences,
                    queryDTO.getResourceType(), queryDTO.getVisibility(), queryDTO.getTargetAudience(),
                    queryDTO.getFileType(), queryDTO.getCourseId(), queryDTO.getChapter(), queryDTO.getClassroomId(),
                    requireUserSchool(teacherId), pageRequest);
        }
        page = filterPageBySection(page, queryDTO.getSectionId());

        List<CoursewareVO> vos = page.getContent().stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());

        log.info("教师获取课件列表: teacherId={}, 总数={}", teacherId, vos.size());
        return PageResult.of(vos, page.getTotalElements(), queryDTO.getPage(), queryDTO.getSize());
    }

    @Override
    public PageResult<CoursewareVO> getVisibleCoursewares(Long studentId, CoursewareQueryDTO queryDTO) {
        normalizeQuery(queryDTO);
        PageRequest pageRequest = PageRequest.of(queryDTO.getPage() - 1, queryDTO.getSize(),
                Sort.by("createdAt").descending());

        // 学生可见：
        // 1. 公开课件（visibility=PUBLIC 且 targetAudience 包含 STUDENT 或 ALL）
        List<String> audiences = Arrays.asList("ALL", "STUDENT");

        Page<Courseware> page;
        if (StringUtils.hasText(queryDTO.getKeyword())) {
            page = coursewareRepository.findForStudentWithKeyword(studentId, audiences,
                    queryDTO.getResourceType(), queryDTO.getVisibility(), queryDTO.getTargetAudience(),
                    queryDTO.getFileType(), queryDTO.getCourseId(), queryDTO.getChapter(), queryDTO.getClassroomId(),
                    requireUserSchool(studentId), queryDTO.getKeyword(), pageRequest);
        } else {
            page = coursewareRepository.findForStudent(studentId, audiences, queryDTO.getResourceType(),
                    queryDTO.getVisibility(), queryDTO.getTargetAudience(), queryDTO.getFileType(), queryDTO.getCourseId(), queryDTO.getChapter(), queryDTO.getClassroomId(),
                    requireUserSchool(studentId), pageRequest);
        }
        page = filterPageBySection(page, queryDTO.getSectionId());

        List<CoursewareVO> vos = page.getContent().stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());

        log.info("学生获取可见课件: studentId={}, 总数={}", studentId, vos.size());
        return PageResult.of(vos, page.getTotalElements(), queryDTO.getPage(), queryDTO.getSize());
    }

    @Override
    public PageResult<CoursewareVO> getAllCoursewares(CoursewareQueryDTO queryDTO) {
        normalizeQuery(queryDTO);
        Long schoolId = managedSchoolId();
        PageRequest pageRequest = PageRequest.of(queryDTO.getPage() - 1, queryDTO.getSize(),
                Sort.by("createdAt").descending());

        Page<Courseware> page;
        if (StringUtils.hasText(queryDTO.getKeyword())) {
            page = coursewareRepository.searchAllWithKeyword(queryDTO.getResourceType(), queryDTO.getVisibility(), queryDTO.getTargetAudience(),
                    queryDTO.getFileType(), queryDTO.getCourseId(), queryDTO.getChapter(), queryDTO.getClassroomId(), schoolId, queryDTO.getKeyword(), pageRequest);
        } else {
            page = coursewareRepository.findAllActiveByResourceType(queryDTO.getResourceType(), queryDTO.getVisibility(), queryDTO.getTargetAudience(),
                    queryDTO.getFileType(), queryDTO.getCourseId(), queryDTO.getChapter(), queryDTO.getClassroomId(), schoolId, pageRequest);
        }
        page = filterPageBySection(page, queryDTO.getSectionId());

        List<CoursewareVO> vos = page.getContent().stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());

        log.info("管理员获取所有课件列表: 总数={}", vos.size());
        return PageResult.of(vos, page.getTotalElements(), queryDTO.getPage(), queryDTO.getSize());
    }

    @Override
    public PageResult<CoursewareVO> getPendingReviews(Integer page, Integer size, String status) {
        int safePage = page == null || page < 1 ? 1 : page;
        int safeSize = size == null || size < 1 || size > 100 ? 20 : size;
        String safeStatus = StringUtils.hasText(status) ? status.trim().toUpperCase() : "PENDING";
        if (!List.of("PENDING", "ACTIVE", "REJECTED", "ARCHIVED").contains(safeStatus)) {
            throw new BusinessException("审核状态无效");
        }
        List<String> resourceTypes = List.of(TEACHING_OUTLINE, TEACHING_VIDEO, TEACHING_COURSEWARE, KNOWLEDGE_MAP);
        Page<Courseware> result = coursewareRepository.findByStatusAndResourceTypeInAndSchoolId(
                safeStatus, resourceTypes, managedSchoolId(), PageRequest.of(safePage - 1, safeSize, Sort.by("createdAt").ascending()));
        List<CoursewareVO> vos = result.getContent().stream().map(this::convertToVO).collect(Collectors.toList());
        return PageResult.of(vos, result.getTotalElements(), safePage, safeSize);
    }

    @Override
    @Transactional
    public String downloadCourseware(Long id, Long userId, String userRole) {
        Courseware courseware = coursewareRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("课件", id));

        requireAccess(courseware, userId, userRole);
        coursewareRepository.incrementDownloadCount(id);
        log.info("下载课件: coursewareId={}, userId={}", id, userId);
        return courseware.getFileUrl();
    }

    @Override
    public String getAuthorizedFileName(Long id, Long userId, String userRole) {
        Courseware courseware = coursewareRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("课程文件", id));
        requireAccess(courseware, userId, userRole);
        return courseware.getFileName();
    }

    @Override
    public String previewCourseware(Long id, Long userId, String userRole) {
        Courseware courseware = coursewareRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("璇句欢", id));
        requireAccess(courseware, userId, userRole);
        return courseware.getFileUrl();
    }

    @Override
    public List<CoursewareVO> getTeachingVideos(Long userId, String userRole, CoursewareQueryDTO queryDTO) {
        normalizeQuery(queryDTO);
        List<Courseware> videos = coursewareRepository.findTeachingVideos(queryDTO.getCourseId(), queryDTO.getChapter(), managedSchoolId());
        boolean isAdmin = "ADMIN".equals(userRole) || "SUPER_ADMIN".equals(userRole);

        return videos.stream()
                .filter(video -> {
                    if (queryDTO.getSectionId() != null && !queryDTO.getSectionId().equals(video.getSectionId())) return false;
                    if (isAdmin) return true;
                    if ("STUDENT".equals(userRole)) return canStudentView(video, userId);
                    return userId.equals(video.getTeacherId()) || canContribute(video.getCourseId(), userId)
                            || ("ACTIVE".equals(video.getStatus()) && "PUBLIC".equals(video.getVisibility())
                            && ("ALL".equals(video.getTargetAudience()) || "TEACHER".equals(video.getTargetAudience())));
                })
                .map(this::convertToVO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void reviewTeachingVideo(Long id, VideoAuditDTO dto, Long reviewerId) {
        Courseware video = requireTeachingVideo(id);
        requireResourceSchool(video, reviewerId, true);
        requirePublicReleaseReview(video);
        String action = dto.getAction().trim().toUpperCase();
        if (!"APPROVE".equals(action) && !"REJECT".equals(action)) {
            throw new BusinessException("视频审核操作无效");
        }
        if ("REJECT".equals(action) && !StringUtils.hasText(dto.getRemark())) {
            throw new BusinessException("驳回视频时请填写原因");
        }
        video.setStatus("APPROVE".equals(action) ? "ACTIVE" : "REJECTED");
        video.setAuditRemark(dto.getRemark());
        video.setReviewedBy(reviewerId);
        video.setReviewedAt(LocalDateTime.now());
        coursewareRepository.save(video);
        eventPublisher.publishEvent(CoursewareChangedEvent.syncStatus(video.getId()));
        notifyTeacherAboutResource(video, action);
        if ("APPROVE".equals(action)) notifyStudentsAboutResource(video);
    }

    @Override
    @Transactional
    public void archiveTeachingVideo(Long id, Long reviewerId) {
        Courseware video = requireTeachingVideo(id);
        requireResourceSchool(video, reviewerId, true);
        requirePublicReleaseReview(video);
        video.setStatus("ARCHIVED");
        video.setReviewedBy(reviewerId);
        video.setReviewedAt(LocalDateTime.now());
        coursewareRepository.save(video);
        eventPublisher.publishEvent(CoursewareChangedEvent.syncStatus(video.getId()));
    }

    @Override
    @Transactional
    public void reviewTeachingCourseware(Long id, VideoAuditDTO dto, Long reviewerId) {
        Courseware courseware = requireTeachingCourseware(id);
        requireResourceSchool(courseware, reviewerId, true);
        requirePublicReleaseReview(courseware);
        String action = dto.getAction().trim().toUpperCase();
        if (!"APPROVE".equals(action) && !"REJECT".equals(action)) {
            throw new BusinessException("教学课件审核操作无效");
        }
        if ("REJECT".equals(action) && !StringUtils.hasText(dto.getRemark())) {
            throw new BusinessException("驳回教学课件时请填写原因");
        }
        courseware.setStatus("APPROVE".equals(action) ? "ACTIVE" : "REJECTED");
        courseware.setAuditRemark(dto.getRemark());
        courseware.setReviewedBy(reviewerId);
        courseware.setReviewedAt(LocalDateTime.now());
        coursewareRepository.save(courseware);
        eventPublisher.publishEvent(CoursewareChangedEvent.syncStatus(courseware.getId()));
        notifyTeacherAboutResource(courseware, action);
        if ("APPROVE".equals(action)) notifyStudentsAboutResource(courseware);
    }

    @Override
    @Transactional
    public void reviewTeachingOutline(Long id, VideoAuditDTO dto, Long reviewerId) {
        Courseware outline = requireTeachingOutline(id);
        requireResourceSchool(outline, reviewerId, true);
        requirePublicReleaseReview(outline);
        String action = dto.getAction().trim().toUpperCase();
        if (!"APPROVE".equals(action) && !"REJECT".equals(action)) {
            throw new BusinessException("教学大纲审核操作无效");
        }
        if ("REJECT".equals(action) && !StringUtils.hasText(dto.getRemark())) {
            throw new BusinessException("驳回教学大纲时请填写原因");
        }
        outline.setStatus("APPROVE".equals(action) ? "ACTIVE" : "REJECTED");
        outline.setAuditRemark(dto.getRemark());
        outline.setReviewedBy(reviewerId);
        outline.setReviewedAt(LocalDateTime.now());
        coursewareRepository.save(outline);
        eventPublisher.publishEvent(CoursewareChangedEvent.syncStatus(outline.getId()));
        notifyTeacherAboutResource(outline, action);
        if ("APPROVE".equals(action)) notifyStudentsAboutResource(outline);
    }

    @Override
    @Transactional
    public void archiveTeachingOutline(Long id, Long reviewerId) {
        Courseware outline = requireTeachingOutline(id);
        requireResourceSchool(outline, reviewerId, true);
        requirePublicReleaseReview(outline);
        if (!"ACTIVE".equals(outline.getStatus())) {
            throw new BusinessException("只有已发布的教学大纲可以归档");
        }
        outline.setStatus("ARCHIVED");
        outline.setReviewedBy(reviewerId);
        outline.setReviewedAt(LocalDateTime.now());
        coursewareRepository.save(outline);
        eventPublisher.publishEvent(CoursewareChangedEvent.syncStatus(outline.getId()));
        notificationService.sendNotification(
                outline.getTeacherId(),
                "OUTLINE_ARCHIVED",
                "教学大纲已归档",
                String.format("您上传的教学大纲《%s》已被管理员归档，学生端将不再展示。", outline.getTitle()),
                outline.getId());
    }

    @Override
    @Transactional
    public void reviewKnowledgeMap(Long id, VideoAuditDTO dto, Long reviewerId) {
        Courseware map = requireKnowledgeMap(id);
        requireResourceSchool(map, reviewerId, true);
        requirePublicReleaseReview(map);
        String action = dto.getAction().trim().toUpperCase();
        if (!"APPROVE".equals(action) && !"REJECT".equals(action)) {
            throw new BusinessException("教学大纲思维导图审核操作无效");
        }
        if ("REJECT".equals(action) && !StringUtils.hasText(dto.getRemark())) {
            throw new BusinessException("驳回教学大纲思维导图时请填写原因");
        }
        if ("APPROVE".equals(action)) {
            validateKnowledgeMapConfig(map);
        }
        map.setStatus("APPROVE".equals(action) ? "ACTIVE" : "REJECTED");
        map.setAuditRemark(dto.getRemark());
        map.setReviewedBy(reviewerId);
        map.setReviewedAt(LocalDateTime.now());
        coursewareRepository.save(map);
        eventPublisher.publishEvent(CoursewareChangedEvent.syncStatus(map.getId()));
        if ("APPROVE".equals(action)) notifyStudentsAboutResource(map);
        notifyTeacherAboutResource(map, action);
    }

    @Override
    @Transactional
    public void archiveTeachingCourseware(Long id, Long reviewerId) {
        Courseware courseware = requireTeachingCourseware(id);
        requireResourceSchool(courseware, reviewerId, true);
        requirePublicReleaseReview(courseware);
        courseware.setStatus("ARCHIVED");
        courseware.setReviewedBy(reviewerId);
        courseware.setReviewedAt(LocalDateTime.now());
        coursewareRepository.save(courseware);
        eventPublisher.publishEvent(CoursewareChangedEvent.syncStatus(courseware.getId()));
    }

    @Override
    @Transactional
    public void withdrawTeachingVideo(Long id, Long teacherId) {
        Courseware video = requireTeachingVideo(id);
        if (!teacherId.equals(video.getTeacherId())) {
            throw new BusinessException("只能撤回自己上传的教学视频");
        }
        if (!"ACTIVE".equals(video.getStatus())) {
            throw new BusinessException("只有已发布的视频可以撤回归档");
        }
        video.setStatus("ARCHIVED");
        coursewareRepository.save(video);
        eventPublisher.publishEvent(CoursewareChangedEvent.syncStatus(video.getId()));
    }

    @Override
    @Transactional
    public void saveVideoProgress(Long id, Long studentId, VideoProgressDTO dto) {
        Courseware video = requireTeachingVideo(id);
        requireAccess(video, studentId, "STUDENT");
        if (!"ACTIVE".equals(video.getStatus())) {
            throw new BusinessException("该视频暂不可学习");
        }

        VideoLearningProgress progress = videoLearningProgressRepository
                .findByCoursewareIdAndStudentId(id, studentId)
                .orElseGet(() -> VideoLearningProgress.builder()
                        .coursewareId(id)
                        .studentId(studentId)
                        .watchedSeconds(0)
                        .durationSeconds(0)
                        .completed(false)
                        .build());
        int previousDuration = progress.getDurationSeconds() == null ? 0 : progress.getDurationSeconds();
        int duration = Math.max(previousDuration, dto.getDurationSeconds());
        int watched = Math.min(dto.getWatchedSeconds(), duration);
        progress.setDurationSeconds(duration);
        progress.setWatchedSeconds(Math.max(progress.getWatchedSeconds(), watched));
        progress.setCompleted(progress.getWatchedSeconds() >= Math.ceil(duration * 0.9));
        progress.setLastWatchedAt(LocalDateTime.now());
        videoLearningProgressRepository.save(progress);
    }

    @Override
    public Map<String, Object> getMyVideoProgress(Long id, Long studentId) {
        Courseware video = requireTeachingVideo(id);
        requireAccess(video, studentId, "STUDENT");
        Map<String, Object> result = new HashMap<>();
        videoLearningProgressRepository.findByCoursewareIdAndStudentId(id, studentId).ifPresent(progress -> {
            result.put("watchedSeconds", progress.getWatchedSeconds());
            result.put("durationSeconds", progress.getDurationSeconds());
            result.put("completed", progress.getCompleted());
            result.put("lastWatchedAt", progress.getLastWatchedAt());
        });
        return result;
    }

    @Override
    public Map<String, Object> getVideoStatistics(Long id, Long userId, String userRole) {
        Courseware video = requireTeachingVideo(id);
        boolean isAdmin = "ADMIN".equals(userRole) || "SUPER_ADMIN".equals(userRole);
        if (!isAdmin && !userId.equals(video.getTeacherId()) && !canContribute(video.getCourseId(), userId)) {
            throw new BusinessException("您没有权限查看本视频学情数据");
        }
        List<Long> classroomIds = classroomRepository.findByCourseIdOrderByCreatedAtDesc(video.getCourseId()).stream()
                .filter(item -> "ACTIVE".equals(item.getStatus()))
                .map(Classroom::getId)
                .collect(Collectors.toList());
        long learners = classroomIds.isEmpty()
                ? 0
                : classroomStudentRelationRepository.countDistinctStudentsByClassroomIdsAndStatus(classroomIds, "ACTIVE");
        long completed = videoLearningProgressRepository.countByCoursewareIdAndCompletedTrue(id);
        completed = Math.min(completed, learners);
        Map<String, Object> result = new HashMap<>();
        result.put("learnerCount", learners);
        result.put("completedCount", completed);
        result.put("completionRate", learners == 0 ? 0 : Math.round(completed * 10000.0 / learners) / 100.0);
        return result;
    }

    @Override
    public Map<String, Object> getTeachingVideoOverview(Long userId, String userRole, Long courseId) {
        boolean isAdmin = "ADMIN".equals(userRole) || "SUPER_ADMIN".equals(userRole);
        if (!isAdmin && (courseId == null || !canContribute(courseId, userId))) {
            throw new BusinessException("您没有权限查看本课程视频建设数据");
        }
        List<Courseware> videos = coursewareRepository.findTeachingVideos(courseId, null, managedSchoolId());
        long published = videos.stream().filter(video -> "ACTIVE".equals(video.getStatus())).count();
        long pending = videos.stream().filter(video -> "PENDING".equals(video.getStatus())).count();
        long chapters = videos.stream().filter(video -> "ACTIVE".equals(video.getStatus()))
                .map(Courseware::getChapter).filter(StringUtils::hasText).distinct().count();
        long learners = 0;
        long completed = 0;
        for (Courseware video : videos) {
            learners += videoLearningProgressRepository.countByCoursewareId(video.getId());
            completed += videoLearningProgressRepository.countByCoursewareIdAndCompletedTrue(video.getId());
        }
        Map<String, Object> result = new HashMap<>();
        result.put("videoCount", videos.size());
        result.put("publishedCount", published);
        result.put("pendingCount", pending);
        result.put("chapterCoverage", chapters);
        result.put("learnerCount", learners);
        result.put("completedCount", completed);
        result.put("completionRate", learners == 0 ? 0 : Math.round(completed * 10000.0 / learners) / 100.0);
        return result;
    }

    @Override
    @Transactional
    public Long createTeachingVideoVersion(Long id, CoursewareUpdateDTO dto, Long userId, boolean isAdmin) {
        Courseware previous = requireTeachingVideo(id);
        if (isAdmin || !userId.equals(previous.getTeacherId())) {
            throw new BusinessException("只能为自己上传的视频创建新版本");
        }
        if (!StringUtils.hasText(dto.getFileUrl()) || !StringUtils.hasText(dto.getFileName())
                || !StringUtils.hasText(dto.getFileType()) || dto.getFileSize() == null) {
            throw new BusinessException("请上传新版本视频文件");
        }
        validateFileReference(dto.getFileUrl(), dto.getFileName(), dto.getFileSize());
        String nextType = dto.getVideoType() != null ? dto.getVideoType() : previous.getVideoType();
        validateTeachingVideo(TEACHING_VIDEO, dto.getFileName(), dto.getFileType(), nextType);
        ContentRiskService.RiskAssessment risk = contentRiskService.assess(
                dto.getTitle() != null ? dto.getTitle() : previous.getTitle(),
                dto.getDescription() != null ? dto.getDescription() : previous.getDescription(),
                dto.getFileName(), dto.getKnowledgePoint());
        boolean publicRelease = "PUBLIC".equals(previous.getVisibility());
        boolean riskReview = !publicRelease && risk.requiresManualReview();

        Courseware next = Courseware.builder()
                .title(dto.getTitle() != null ? dto.getTitle() : previous.getTitle())
                .description(dto.getDescription() != null ? dto.getDescription() : previous.getDescription())
                .fileUrl(dto.getFileUrl())
                .fileName(dto.getFileName())
                .fileType(dto.getFileType())
                .resourceType(TEACHING_VIDEO)
                .videoType(nextType)
                .knowledgePoint(dto.getKnowledgePoint() != null ? dto.getKnowledgePoint() : previous.getKnowledgePoint())
                .featured(dto.getFeatured() != null ? dto.getFeatured() : previous.getFeatured())
                .courseId(previous.getCourseId())
                .semester(previous.getSemester())
                .chapter(previous.getChapter())
                .sectionId(previous.getSectionId())
                .gradeLevel(previous.getGradeLevel())
                .version(previous.getVersion())
                .fileSize(dto.getFileSize())
                .teacherId(previous.getTeacherId())
                .schoolId(previous.getSchoolId())
                .visibility(previous.getVisibility())
                .targetAudience(previous.getTargetAudience())
                .classroomId(previous.getClassroomId())
                .downloadCount(0)
                .viewCount(0)
                .status(publicRelease || riskReview ? "PENDING" : "ACTIVE")
                .previousVersionId(previous.getId())
                .versionGroupId(previous.getVersionGroupId() != null ? previous.getVersionGroupId() : previous.getId())
                .versionNumber((previous.getVersionNumber() == null ? 1 : previous.getVersionNumber()) + 1)
                .build();
        if (publicRelease && risk.requiresManualReview()) {
            next.setAuditRemark("系统风险提示：" + risk.reason());
        }
        previous.setStatus("ARCHIVED");
        coursewareRepository.save(previous);
        eventPublisher.publishEvent(CoursewareChangedEvent.syncStatus(previous.getId()));
        Courseware savedNext = coursewareRepository.save(next);
        eventPublisher.publishEvent(CoursewareChangedEvent.reindex(savedNext.getId()));
        if (publicRelease) {
            notifyAdminsAboutResource(savedNext);
        } else if (riskReview) {
            queueResourceRisk(savedNext, userId, risk);
        } else {
            savedNext.setReviewedBy(userId);
            savedNext.setReviewedAt(LocalDateTime.now());
            coursewareRepository.save(savedNext);
            notifyStudentsAboutResource(savedNext);
        }
        return savedNext.getId();
    }

    @Override
    @Transactional
    public void uploadVideoTranscript(Long id, MultipartFile file, Long userId) {
        Courseware video = requireTeachingVideo(id);
        requireResourceSchool(video, userId, false);
        if (!userId.equals(video.getTeacherId())) {
            throw new BusinessException("只有视频上传教师可以维护视频字幕");
        }
        if (file == null || file.isEmpty()) {
            throw new BusinessException("字幕文件不能为空");
        }
        if (file.getSize() > 20L * 1024 * 1024) {
            throw new BusinessException("字幕文件不能超过20MB");
        }

        String originalName = file.getOriginalFilename();
        String extension = StringUtils.getFilenameExtension(originalName);
        if (!Set.of("srt", "vtt", "txt").contains(String.valueOf(extension).toLowerCase())) {
            throw new BusinessException("字幕仅支持 SRT、VTT、TXT 格式");
        }

        Path videoPath = resolveStoredCoursewareFile(video.getFileUrl());
        if (videoPath == null || !Files.isRegularFile(videoPath)) {
            throw new BusinessException("视频文件不存在，请重新上传视频");
        }
        String videoName = videoPath.getFileName().toString();
        int dot = videoName.lastIndexOf('.');
        String stem = dot > 0 ? videoName.substring(0, dot) : videoName;
        Path transcriptPath = videoPath.getParent().resolve(stem + "." + extension.toLowerCase()).normalize();
        if (!transcriptPath.startsWith(videoPath.getParent())) {
            throw new BusinessException("字幕保存路径无效");
        }
        try {
            file.transferTo(transcriptPath.toFile());
        } catch (IOException exception) {
            throw new BusinessException("字幕文件保存失败", exception);
        }

        video.setAiIndexStatus("NOT_INDEXED");
        video.setAiIndexMessage("字幕已上传，正在重新建立视频 AI 索引");
        video.setAiIndexedAt(null);
        coursewareRepository.save(video);
        eventPublisher.publishEvent(CoursewareChangedEvent.reindex(video.getId()));
    }

    private Courseware requireTeachingVideo(Long id) {
        Courseware video = coursewareRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("教学视频", id));
        if (!TEACHING_VIDEO.equals(video.getResourceType()) || "DELETED".equals(video.getStatus())) {
            throw new NotFoundException("教学视频", id);
        }
        return video;
    }

    private Courseware requireTeachingCourseware(Long id) {
        Courseware courseware = coursewareRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("教学课件", id));
        if (!TEACHING_COURSEWARE.equals(courseware.getResourceType()) || "DELETED".equals(courseware.getStatus())) {
            throw new NotFoundException("教学课件", id);
        }
        return courseware;
    }

    private Courseware requireTeachingOutline(Long id) {
        Courseware outline = coursewareRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("教学大纲", id));
        if (!TEACHING_OUTLINE.equals(outline.getResourceType()) || "DELETED".equals(outline.getStatus())) {
            throw new NotFoundException("教学大纲", id);
        }
        return outline;
    }

    private void queueResourceRisk(Courseware resource, Long teacherId,
                                   ContentRiskService.RiskAssessment risk) {
        ContentAudit audit = contentAuditService.createOrEscalate(
                ContentAudit.ContentType.COURSEWARE, resource.getId(), resource.getTitle(),
                resource.getDescription(), teacherId, resourceAuthorName(teacherId), resource.getSchoolId(),
                risk.level(), risk.reason());
        notifyAdminsAboutResourceRisk(resource, audit);
    }

    private void requirePublicReleaseReview(Courseware resource) {
        if (!"PUBLIC".equals(resource.getVisibility())) {
            throw new BusinessException("课程、班级或个人资源不走公开发布审核，请在风险与举报队列处置异常内容");
        }
    }

    private String resourceAuthorName(Long teacherId) {
        return userRepository.findById(teacherId)
                .map(user -> StringUtils.hasText(user.getNickname()) ? user.getNickname() : user.getUsername())
                .orElse("教师");
    }

    private void notifyAdminsAboutResourceRisk(Courseware resource, ContentAudit audit) {
        List<User> admins = new ArrayList<>(userRepository.findAllByRoleAndStatus(
                User.UserRole.ADMIN, User.UserStatus.ACTIVE));
        admins.addAll(userRepository.findAllByRoleAndStatus(
                User.UserRole.SUPER_ADMIN, User.UserStatus.ACTIVE));
        admins.stream()
                .filter(admin -> admin.getRole() == User.UserRole.SUPER_ADMIN
                        || (resource.getSchoolId() != null && resource.getSchoolId().equals(admin.getSchoolId())))
                .forEach(admin -> notificationService.sendNotification(
                        admin.getId(), "MODERATION_RISK", "教学资源触发风险复核",
                        "课程内资源《" + resource.getTitle() + "》触发风险规则，请前往管理中心的风险与举报队列处理。",
                        audit.getId()));
    }

    private void notifyAdminsAboutResource(Courseware resource) {
        String resourceLabel = resourceTypeLabel(resource.getResourceType());
        String content = String.format("教师提交了%s《%s》，章节：%s，请前往“管理中心 - 审核中心”处理。",
                resourceLabel, resource.getTitle(), resource.getChapter());
        List<User> admins = new ArrayList<>(userRepository.findAllByRoleAndStatus(User.UserRole.ADMIN, User.UserStatus.ACTIVE));
        admins.addAll(userRepository.findAllByRoleAndStatus(User.UserRole.SUPER_ADMIN, User.UserStatus.ACTIVE));
        admins.stream()
                .filter(admin -> admin.getRole() == User.UserRole.SUPER_ADMIN
                        || (resource.getSchoolId() != null && resource.getSchoolId().equals(admin.getSchoolId())))
                .forEach(admin -> notificationService.sendNotification(
                        admin.getId(), "RESOURCE_PENDING", "有新的教学资源待审核", content, resource.getId()));
    }

    private void notifyTeacherAboutResource(Courseware resource, String action) {
        boolean approved = "APPROVE".equals(action);
        String resourceLabel = resourceTypeLabel(resource.getResourceType());
        String title = resourceLabel + (approved ? "审核通过" : "审核未通过");
        String reason = approved || !StringUtils.hasText(resource.getAuditRemark())
                ? ""
                : "驳回原因：" + resource.getAuditRemark();
        String content = String.format("您上传的%s《%s》%s。%s",
                resourceLabel, resource.getTitle(), approved ? "已通过管理员审核" : "未通过管理员审核", reason);
        notificationService.sendNotification(
                resource.getTeacherId(), approved ? "RESOURCE_APPROVED" : "RESOURCE_REJECTED",
                title, content, resource.getId());
    }

    private void notifyStudentsAboutResource(Courseware resource) {
        Set<Long> studentIds = new HashSet<>();
        if ("CLASS".equals(resource.getVisibility()) && resource.getClassroomId() != null) {
            classroomStudentRelationRepository.findByClassroomIdAndStatus(resource.getClassroomId(), "ACTIVE")
                    .forEach(relation -> studentIds.add(relation.getStudentId()));
        } else if ("COURSE".equals(resource.getVisibility()) && resource.getCourseId() != null) {
            courseMemberRepository.findByCourseIdAndStatusOrderByJoinedAtAsc(resource.getCourseId(), "ACTIVE")
                    .forEach(member -> userRepository.findById(member.getUserId())
                            .filter(User::isStudent)
                            .ifPresent(student -> studentIds.add(student.getId())));
        } else if ("PUBLIC".equals(resource.getVisibility())
                && ("ALL".equals(resource.getTargetAudience()) || "STUDENT".equals(resource.getTargetAudience()))) {
            userRepository.findAllByRoleAndStatus(User.UserRole.STUDENT, User.UserStatus.ACTIVE)
                    .stream()
                    .filter(student -> resource.getSchoolId() == null || resource.getSchoolId().equals(student.getSchoolId()))
                    .forEach(student -> studentIds.add(student.getId()));
        }

        String resourceLabel = resourceTypeLabel(resource.getResourceType());
        studentIds.forEach(studentId -> {
            if (notificationRepository.existsByUserIdAndTypeAndRelatedId(studentId,
                    "RESOURCE_PUBLISHED", resource.getId())) return;
            notificationService.sendNotification(
                    studentId,
                    "RESOURCE_PUBLISHED",
                    "有新的" + resourceLabel,
                    "课程中发布了新的" + resourceLabel + "《" + resource.getTitle() + "》，请及时查看。",
                    resource.getId());
        });
    }

    private String resourceTypeLabel(String resourceType) {
        return TEACHING_OUTLINE.equals(resourceType) ? "教学大纲"
                : TEACHING_VIDEO.equals(resourceType) ? "教学视频"
                : TEACHING_COURSEWARE.equals(resourceType) ? "教学课件"
                : KNOWLEDGE_MAP.equals(resourceType) ? "知识图谱" : "教学资源";
    }

    private Courseware requireKnowledgeMap(Long id) {
        Courseware map = coursewareRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("知识图谱", id));
        if (!KNOWLEDGE_MAP.equals(map.getResourceType()) || "DELETED".equals(map.getStatus())) {
            throw new NotFoundException("知识图谱", id);
        }
        return map;
    }

    private void validateKnowledgeMapConfig(Courseware map) {
        try {
            JsonNode parsed = objectMapper.readTree(map.getDescription());
            if (parsed == null || !parsed.isObject() || !parsed.has("nodes") || !parsed.get("nodes").isArray()) {
                throw new BusinessException("请先完成知识图谱节点编辑后再保存");
            }
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException("知识图谱结构解析失败，请检查节点内容");
        }
    }

    private void ensureSingleKnowledgeMap(Long courseId) {
        if (coursewareRepository.findFirstByCourseIdAndResourceTypeAndStatusInOrderByCreatedAtDesc(
                courseId, KNOWLEDGE_MAP, List.of("ACTIVE", "PENDING")).isPresent()) {
            throw new BusinessException("本课程已有知识图谱，请使用编辑或替换功能");
        }
    }

    private boolean canStudentView(Courseware video, Long studentId) {
        if (!"ACTIVE".equals(video.getStatus())) return false;
        if ("PUBLIC".equals(video.getVisibility())) {
            return "ALL".equals(video.getTargetAudience()) || "STUDENT".equals(video.getTargetAudience());
        }
        return "CLASS".equals(video.getVisibility()) && video.getClassroomId() != null
                && classroomStudentRelationRepository.existsByClassroomIdAndStudentIdAndStatus(
                        video.getClassroomId(), studentId, "ACTIVE");
    }

    private void requireAccess(Courseware courseware, Long userId, String userRole) {
        User user = userRepository.findById(userId).orElseThrow(() -> new NotFoundException("用户", userId));
        boolean isOwner = courseware.getTeacherId().equals(userId);
        boolean isAdmin = "ADMIN".equals(userRole) || "SUPER_ADMIN".equals(userRole);
        boolean sameSchool = user.getRole() == User.UserRole.SUPER_ADMIN
                || (user.getSchoolId() != null && user.getSchoolId().equals(courseware.getSchoolId()));

        if (!sameSchool) throw new NotFoundException("课程资源", courseware.getId());

        if (isOwner || isAdmin) {
            return;
        }
        if (!"ACTIVE".equals(courseware.getStatus())) {
            throw new NotFoundException("课程资源", courseware.getId());
        }
        // Course collaborators may work with resources shared inside their course.
        if (canContribute(courseware.getCourseId(), userId)) {
            return;
        }
        if ("PUBLIC".equals(courseware.getVisibility())) {
            if ("ALL".equals(courseware.getTargetAudience()) ||
                    userRole.equals(courseware.getTargetAudience())) {
                return;
            }
        }

        if ("COURSE".equals(courseware.getVisibility()) && courseware.getCourseId() != null
                && courseMemberRepository.findByCourseIdAndUserId(courseware.getCourseId(), userId)
                .map(member -> "ACTIVE".equals(member.getStatus())).orElse(false)) {
            return;
        }

        if ("CLASS".equals(courseware.getVisibility()) && "STUDENT".equals(userRole)) {
            if (courseware.getClassroomId() != null) {
                boolean isInClass = classroomStudentRelationRepository
                        .existsByClassroomIdAndStudentIdAndStatus(
                                courseware.getClassroomId(), userId, "ACTIVE");
                if (isInClass) {
                    return;
                }
            }
        }

        throw new BusinessException("您没有权限访问此课件");
    }

    private void validateResourceType(String resourceType) {
        if (!SUPPORTED_RESOURCE_TYPES.contains(resourceType)) {
            throw new BusinessException("资源类型不受支持");
        }
    }

    private void validateTeachingOutline(String resourceType, Long courseId, String chapter, Long sectionId,
                                         String fileName, String fileType) {
        if (!TEACHING_OUTLINE.equals(resourceType)) return;
        validateTeachingOutlineScope(courseId, null, null);
        if (!isPdf(fileName, fileType)) {
            throw new BusinessException("教学大纲只能使用 PDF 文件");
        }
    }

    private void validateTeachingOutlineScope(Long courseId, String chapter, Long sectionId) {
        if (courseId == null) throw new BusinessException("教学大纲必须归属于具体课程");
    }

    private void validateTeachingVideo(String resourceType, String fileName, String fileType, String videoType) {
        if (!TEACHING_VIDEO.equals(resourceType)) return;
        String extension = StringUtils.getFilenameExtension(fileName);
        Set<String> supportedExtensions = Set.of("mp4", "webm", "ogg", "mov", "m4v");
        if (!supportedExtensions.contains(String.valueOf(extension).toLowerCase())
                && !supportedExtensions.contains(String.valueOf(fileType).toLowerCase())) {
            throw new BusinessException("教学视频仅支持 mp4、webm、ogg、mov、m4v 格式");
        }
        if (StringUtils.hasText(videoType) && !VIDEO_TYPES.contains(videoType.toUpperCase())) {
            throw new BusinessException("教学视频类型无效");
        }
    }

    private void validateTeachingCourseware(String resourceType, String fileName, String fileType) {
        if (!TEACHING_COURSEWARE.equals(resourceType)) return;
        String extension = StringUtils.getFilenameExtension(fileName);
        Set<String> supportedExtensions = Set.of("ppt", "pptx", "pdf");
        if (!supportedExtensions.contains(String.valueOf(extension).toLowerCase())
                && !supportedExtensions.contains(String.valueOf(fileType).toLowerCase())) {
            throw new BusinessException("教学课件仅支持 PPT、PPTX、PDF 格式");
        }
    }

    private boolean isPdf(String fileName, String fileType) {
        return "pdf".equalsIgnoreCase(StringUtils.getFilenameExtension(fileName))
                || "pdf".equalsIgnoreCase(fileType);
    }

    private void ensureSingleTeachingOutline(Long courseId, String chapter, Long sectionId, Long excludeId) {
        long count = excludeId == null
                ? coursewareRepository.countActiveTeachingOutlinesByCourse(courseId)
                : coursewareRepository.countActiveTeachingOutlinesByCourseExcluding(courseId, excludeId);
        if (count > 0) {
            throw new BusinessException("本课程已有教学大纲，请使用编辑或替换功能");
        }
    }

    private CoursewareVO convertToVO(Courseware courseware) {
        CoursewareVO vo = new CoursewareVO();
        BeanUtils.copyProperties(courseware, vo);

        userRepository.findById(courseware.getTeacherId()).ifPresent(teacher -> {
            vo.setTeacherName(teacher.getNickname() != null ? teacher.getNickname() : teacher.getUsername());
        });

        if (courseware.getClassroomId() != null) {
            classroomRepository.findById(courseware.getClassroomId()).ifPresent(classroom -> {
                vo.setClassName(classroom.getClassName());
            });
        }
        if (courseware.getCourseId() != null) courseRepository.findById(courseware.getCourseId()).ifPresent(course -> vo.setCourseName(course.getCourseName()));

        return vo;
    }

    private void validateResourceScope(Long courseId, Long classroomId, String visibility, Long userId, boolean isAdmin) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new NotFoundException("课程", courseId));
        User user = userRepository.findById(userId).orElseThrow(() -> new NotFoundException("用户", userId));
        if (user.getRole() != User.UserRole.SUPER_ADMIN
                && (user.getSchoolId() == null || course.getSchoolId() == null || !user.getSchoolId().equals(course.getSchoolId()))) {
            throw new BusinessException("课程与当前学校不一致");
        }
        if (!"ACTIVE".equals(course.getStatus())) {
            throw new BusinessException("已归档课程不能新增或调整课件");
        }
        if (!isAdmin && !canContribute(courseId, userId)) {
            throw new BusinessException("只能维护自己负责课程的课件");
        }
        if ("CLASS".equals(visibility) && classroomId == null) throw new BusinessException("班级可见资源必须选择班级");
        if (classroomId != null) {
            Classroom classroom = classroomRepository.findById(classroomId)
                    .filter(item -> "ACTIVE".equals(item.getStatus()))
                    .orElseThrow(() -> new BusinessException("教学班不存在或已归档"));
            if (!isAdmin && !userId.equals(classroom.getTeacherId())) {
                throw new BusinessException("只能为自己负责的有效班级上传资源");
            }
            if (!courseId.equals(classroom.getCourseId())) {
                throw new BusinessException("课件所属课程必须与教学班所属课程一致");
            }
        }
    }

    /** Course owners, co-teachers and teaching assistants may contribute resources. */
    private boolean canContribute(Long courseId, Long userId) {
        if (courseId == null || userId == null) return false;
        return courseRepository.findById(courseId)
                .map(course -> userId.equals(course.getCreatedBy()))
                .orElse(false)
                || courseMemberRepository.findByCourseIdAndUserId(courseId, userId)
                .map(member -> "ACTIVE".equals(member.getStatus())
                        && ("OWNER".equals(member.getRole())
                        || "CO_TEACHER".equals(member.getRole())
                        || "TEACHING_ASSISTANT".equals(member.getRole())))
                .orElse(false);
    }

    private Long requireUserSchool(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("用户", userId));
        if (user.getSchoolId() == null) throw new BusinessException("当前账号未绑定学校，不能发布教学资源");
        return user.getSchoolId();
    }

    private Long managedSchoolId() {
        User current = adminScopeService.currentUser();
        if (current == null || current.getRole() == User.UserRole.SUPER_ADMIN) return null;
        if (current.getSchoolId() == null) return -1L;
        return current.getSchoolId();
    }

    private void requireResourceSchool(Courseware resource, Long userId, boolean isAdmin) {
        User user = userRepository.findById(userId).orElseThrow(() -> new NotFoundException("用户", userId));
        if (user.getRole() == User.UserRole.SUPER_ADMIN) return;
        if (user.getSchoolId() == null || resource.getSchoolId() == null
                || !user.getSchoolId().equals(resource.getSchoolId())) {
            throw new BusinessException("无权操作其他学校的教学资源");
        }
    }

    private void validateFileReference(String fileUrl, String fileName, Long fileSize) {
        if (fileUrl == null || !fileUrl.startsWith("/uploads/courseware/")
                || fileUrl.contains("..") || fileUrl.contains("\\")) {
            throw new BusinessException("璇句欢璺緞鏃犳晥");
        }
        if (fileName == null || fileName.isBlank() || fileName.contains("..") || fileName.contains("\\")) {
            throw new BusinessException("璇句欢鍚嶇О鏃犳晥");
        }
        if (fileSize == null || fileSize <= 0 || fileSize > 100L * 1024 * 1024) {
            throw new BusinessException("璇句欢澶у皬鏃犳晥");
        }
        validateStoredFile(fileUrl, fileSize);
    }

    private void validateStoredFile(String fileUrl, Long fileSize) {
        try {
            String relative = fileUrl.substring("/uploads/".length()).replace('/', java.io.File.separatorChar);
            Path base = Paths.get(uploadPath).toAbsolutePath().normalize();
            Path file = base.resolve(relative).normalize();
            if (!file.startsWith(base) || !Files.isRegularFile(file)) {
                throw new BusinessException("上传文件不存在，请重新上传");
            }
            if (Files.size(file) != fileSize) {
                throw new BusinessException("文件大小与实际文件不一致，请重新上传");
            }
        } catch (IOException ex) {
            throw new BusinessException("无法读取上传文件");
        }
    }

    private Path resolveStoredCoursewareFile(String fileUrl) {
        if (!StringUtils.hasText(fileUrl) || !fileUrl.startsWith("/uploads/courseware/")
                || fileUrl.contains("..") || fileUrl.contains("\\")) {
            return null;
        }
        String relative = fileUrl.substring("/uploads/".length())
                .replace('/', java.io.File.separatorChar);
        Path base = Paths.get(uploadPath).toAbsolutePath().normalize();
        Path target = base.resolve(relative).normalize();
        return target.startsWith(base) ? target : null;
    }

    private String saveStructuredOutlineFile(String fileName, String content) {
        try {
            String datePath = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM"));
            Path base = Paths.get(uploadPath).toAbsolutePath().normalize();
            Path directory = base.resolve("courseware").resolve(datePath).normalize();
            if (!directory.startsWith(base)) {
                throw new BusinessException("教学大纲存储路径无效");
            }
            Files.createDirectories(directory);
            Path file = directory.resolve(fileName).normalize();
            if (!file.startsWith(directory)) {
                throw new BusinessException("教学大纲文件名无效");
            }
            Files.writeString(file, content, StandardCharsets.UTF_8);
            return "/uploads/courseware/" + datePath + "/" + fileName;
        } catch (IOException ex) {
            throw new BusinessException("结构化教学大纲保存失败");
        }
    }

    private void normalizeQuery(CoursewareQueryDTO query) {
        if (query == null) return;
        if (!StringUtils.hasText(query.getKeyword())) query.setKeyword(null);
        if (!StringUtils.hasText(query.getVisibility())) query.setVisibility(null);
        if (!StringUtils.hasText(query.getTargetAudience())) query.setTargetAudience(null);
        if (!StringUtils.hasText(query.getFileType())) query.setFileType(null);
        if (!StringUtils.hasText(query.getResourceType())) query.setResourceType(null);
        if (!StringUtils.hasText(query.getChapter())) query.setChapter(null);
        if (query.getSectionId() != null && query.getSectionId() < 1) query.setSectionId(null);
    }

    private Page<Courseware> filterPageBySection(Page<Courseware> page, Long sectionId) {
        if (sectionId == null) return page;
        List<Courseware> filtered = page.getContent().stream()
                .filter(item -> sectionId.equals(item.getSectionId()))
                .collect(Collectors.toList());
        return new PageImpl<>(filtered, page.getPageable(), filtered.size());
    }

    private void deleteStoredFile(String fileUrl) {
        if (fileUrl == null || !fileUrl.startsWith("/uploads/courseware/")
                || fileUrl.contains("..") || fileUrl.contains("\\")) return;
        try {
            String relative = fileUrl.substring("/uploads/".length()).replace('/', java.io.File.separatorChar);
            Path base = Paths.get(uploadPath).toAbsolutePath().normalize();
            Path file = base.resolve(relative).normalize();
            if (file.startsWith(base)) Files.deleteIfExists(file);
        } catch (IOException ex) {
            log.warn("Unable to remove stored courseware file: {}", fileUrl, ex);
        }
    }
}
