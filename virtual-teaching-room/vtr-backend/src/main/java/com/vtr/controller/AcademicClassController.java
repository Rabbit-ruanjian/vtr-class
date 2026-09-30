package com.vtr.controller;

import com.vtr.common.PageResult;
import com.vtr.common.Result;
import com.vtr.common.exception.BusinessException;
import com.vtr.dto.AcademicClassAssignDTO;
import com.vtr.dto.AcademicClassBatchAssignDTO;
import com.vtr.dto.AcademicClassImportResult;
import com.vtr.dto.AcademicClassStudentImportDTO;
import com.vtr.dto.AcademicClassUpsertDTO;
import com.vtr.entity.AcademicClass;
import com.vtr.entity.AcademicClassStudentRoster;
import com.vtr.entity.User;
import com.vtr.repository.AcademicClassRepository;
import com.vtr.repository.AcademicClassStudentRosterRepository;
import com.vtr.repository.SchoolRepository;
import com.vtr.repository.SchoolDepartmentRepository;
import com.vtr.repository.SchoolMajorRepository;
import com.vtr.repository.UserRepository;
import com.vtr.security.SecurityUtils;
import com.vtr.service.AdminScopeService;
import com.vtr.vo.AcademicClassCollegeSummaryVO;
import com.vtr.vo.AcademicClassStudentVO;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.security.SecureRandom;
import java.util.Set;

@RestController
@RequestMapping("/api/academic-classes")
@RequiredArgsConstructor
public class AcademicClassController {
    private final AcademicClassRepository classes;
    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final SchoolRepository schools;
    private final SchoolDepartmentRepository departments;
    private final SchoolMajorRepository majors;
    private final AcademicClassStudentRosterRepository studentRosters;
    private final AdminScopeService adminScopeService;
    private final SecureRandom random = new SecureRandom();

    @GetMapping
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN','SUPER_ADMIN')")
    public Result<PageResult<AcademicClass>> list(@RequestParam(defaultValue = "1") Integer page, @RequestParam(defaultValue = "20") Integer size, @RequestParam(defaultValue = "ACTIVE") String status, @RequestParam(required = false) String keyword, @RequestParam(required = false) Long schoolId, @RequestParam(required = false) String college, @RequestParam(required = false) String grade, @RequestParam(required = false) String major) {
        int safePage = Math.max(1, page == null ? 1 : page);
        int safeSize = Math.min(100, Math.max(1, size == null ? 20 : size));
        String safeStatus = hasText(status) ? status.trim().toUpperCase(Locale.ROOT) : "ACTIVE";
        if (!"ACTIVE".equals(safeStatus) && !"ARCHIVED".equals(safeStatus)) throw new BusinessException(400, "行政班状态参数无效");
        Long scopedSchoolId = resolveSchoolId(schoolId);
        String safeKeyword = hasText(keyword) ? keyword.trim() : null;
        String safeCollege = hasText(college) ? college.trim() : null;
        String safeGrade = hasText(grade) ? grade.trim() : null;
        String safeMajor = hasText(major) ? major.trim() : null;
        Pageable pageable = PageRequest.of(safePage - 1, safeSize, Sort.by("grade").descending().and(Sort.by("major").ascending()).and(Sort.by("classCode").ascending()));
        var result = scopedSchoolId == null
                ? classes.search(safeStatus, safeKeyword, safeCollege, safeGrade, safeMajor, pageable)
                : classes.searchBySchool(scopedSchoolId, safeStatus, safeKeyword, safeCollege, safeGrade, safeMajor, pageable);
        return Result.success(PageResult.of(result.getContent(), result.getTotalElements(), safePage, safeSize));
    }

    @GetMapping("/summary")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN','SUPER_ADMIN')")
    public Result<List<AcademicClassCollegeSummaryVO>> summary(@RequestParam(defaultValue = "ACTIVE") String status, @RequestParam(required = false) Long schoolId) {
        String safeStatus = hasText(status) ? status.trim().toUpperCase(Locale.ROOT) : "ACTIVE";
        if (!"ACTIVE".equals(safeStatus) && !"ARCHIVED".equals(safeStatus)) throw new BusinessException(400, "行政班状态参数无效");
        Long scopedSchoolId = resolveSchoolId(schoolId);
        var rows = scopedSchoolId == null
                ? classes.summarize(safeStatus)
                : classes.summarizeBySchool(scopedSchoolId, safeStatus);
        // 以学校维护的院系顺序为准，未配置院系顺序的学院排在最后并按名称排序。
        java.util.Map<String, Integer> collegeOrder = new java.util.HashMap<>();
        if (scopedSchoolId != null) {
            int order = 0;
            for (var department : departments.findBySchoolIdAndStatusOrderBySortOrderAscNameAsc(scopedSchoolId, "ACTIVE")) {
                collegeOrder.put(department.getName(), order++);
            }
        }
        java.util.Map<String, AcademicClassCollegeSummaryVO> byCollege = new java.util.LinkedHashMap<>();
        for (var row : rows) {
            String collegeName = hasText(row.getCollege()) ? row.getCollege() : "未分类学院";
            AcademicClassCollegeSummaryVO college = byCollege.computeIfAbsent(collegeName,
                    key -> new AcademicClassCollegeSummaryVO(key, 0, 0, new java.util.ArrayList<>()));
            long classCount = row.getClassCount();
            long studentCount = row.getStudentCount() == null ? 0 : row.getStudentCount();
            college.setClassCount(college.getClassCount() + classCount);
            college.setStudentCount(college.getStudentCount() + studentCount);
            college.getGrades().add(new AcademicClassCollegeSummaryVO.GradeSummary(
                    hasText(row.getGrade()) ? row.getGrade() : "未分年级", classCount, studentCount));
        }
        List<AcademicClassCollegeSummaryVO> summaries = new java.util.ArrayList<>(byCollege.values());
        for (AcademicClassCollegeSummaryVO college : summaries) {
            college.getGrades().sort(java.util.Comparator.comparing(
                    AcademicClassCollegeSummaryVO.GradeSummary::getGrade, java.util.Comparator.reverseOrder()));
        }
        summaries.sort(java.util.Comparator
                .comparingInt((AcademicClassCollegeSummaryVO college) -> collegeOrder.getOrDefault(college.getCollege(), Integer.MAX_VALUE))
                .thenComparing(AcademicClassCollegeSummaryVO::getCollege));
        return Result.success(summaries);
    }

    @GetMapping("/{id}/students")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN','SUPER_ADMIN')")
    public Result<List<AcademicClassStudentVO>> students(@PathVariable Long id) {
        require(id);
        return Result.success(studentRosters.findByAcademicClassIdAndStatusOrderByStudentNumberAsc(id, "ACTIVE")
                .stream().map(AcademicClassStudentVO::from).toList());
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    @Transactional
    public Result<AcademicClass> create(@RequestBody @Validated AcademicClassUpsertDTO dto) { return Result.success(classes.save(toEntity(dto))); }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    @Transactional
    public Result<AcademicClass> update(@PathVariable Long id, @RequestBody @Validated AcademicClassUpsertDTO dto) {
        AcademicClass item = require(id);
        apply(item, dto);
        return Result.success(classes.save(item));
    }

    @PostMapping("/{id}/archive")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    @Transactional
    public Result<Void> archive(@PathVariable Long id) {
        AcademicClass item = require(id);
        if (!"ACTIVE".equals(item.getStatus())) throw new BusinessException("只有有效行政班才能归档");
        item.setStatus("ARCHIVED");
        classes.save(item);
        return Result.success();
    }

    @PostMapping("/{id}/restore")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    @Transactional
    public Result<Void> restore(@PathVariable Long id) {
        AcademicClass item = require(id);
        if (!"ARCHIVED".equals(item.getStatus())) throw new BusinessException("只有已归档行政班才能恢复");
        item.setStatus("ACTIVE");
        classes.save(item);
        return Result.success();
    }

    @PutMapping("/students/{studentId}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    @Transactional
    public Result<Void> assign(@PathVariable Long studentId, @RequestBody @Validated AcademicClassAssignDTO dto) {
        User student = users.findById(studentId).orElseThrow(() -> new BusinessException(404, "学生不存在"));
        if (!student.isStudent()) throw new BusinessException("只有学生账号可以分配到行政班");
        AcademicClass targetClass = requireActive(dto.getAcademicClassId());
        adminScopeService.requireSchool(targetClass.getSchoolId());
        adminScopeService.requireUser(student);
        if (student.getSchoolId() != null && targetClass.getSchoolId() != null && !student.getSchoolId().equals(targetClass.getSchoolId())) {
            throw new BusinessException("学生与行政班不属于同一所学校");
        }
        if (!hasText(student.getIdentityNumber())) throw new BusinessException("该学生没有绑定学号，不能分配行政班");
        student.setSchoolId(targetClass.getSchoolId());
        student.setAcademicClassId(dto.getAcademicClassId());
        student.setIdentityType(User.IdentityType.STUDENT);
        student.setIdentityStatus(User.IdentityStatus.VERIFIED);
        users.save(student);
        AcademicClassStudentRoster roster = studentRosters.findBySchoolIdAndStudentNumber(
                        targetClass.getSchoolId(), student.getIdentityNumber()).orElseGet(AcademicClassStudentRoster::new);
        roster.setSchoolId(targetClass.getSchoolId());
        roster.setAcademicClassId(targetClass.getId());
        roster.setStudentNumber(student.getIdentityNumber());
        roster.setName(student.getNickname());
        roster.setUserId(student.getId());
        roster.setStatus("ACTIVE");
        studentRosters.findByUserId(student.getId()).ifPresent(previous -> {
            if (!previous.getId().equals(roster.getId())) {
                previous.setUserId(null);
                studentRosters.save(previous);
            }
        });
        studentRosters.save(roster);
        return Result.success();
    }

    @DeleteMapping("/{id}/students/{studentId}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    @Transactional
    public Result<Void> removeStudent(@PathVariable Long id, @PathVariable Long studentId) {
        requireActive(id);
        User student = users.findById(studentId).orElseThrow(() -> new BusinessException(404, "学生不存在"));
        if (!id.equals(student.getAcademicClassId())) throw new BusinessException("该学生不在当前行政班");
        student.setAcademicClassId(null);
        users.save(student);
        studentRosters.findByUserId(studentId).ifPresent(roster -> {
            roster.setStatus("REMOVED");
            studentRosters.save(roster);
        });
        return Result.success();
    }

    @DeleteMapping("/{id}/roster/{rosterId}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    @Transactional
    public Result<Void> removeRoster(@PathVariable Long id, @PathVariable Long rosterId) {
        requireActive(id);
        AcademicClassStudentRoster roster = studentRosters.findById(rosterId)
                .orElseThrow(() -> new BusinessException(404, "学生名单不存在"));
        if (!id.equals(roster.getAcademicClassId())) throw new BusinessException("该学生不在当前行政班");
        roster.setStatus("REMOVED");
        if (roster.getUserId() != null) {
            users.findById(roster.getUserId()).ifPresent(student -> {
                student.setAcademicClassId(null);
                users.save(student);
            });
        }
        studentRosters.save(roster);
        return Result.success();
    }

    @DeleteMapping("/{id}/students")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    @Transactional
    public Result<Integer> clearStudents(@PathVariable Long id) {
        requireActive(id);
        List<User> assignedStudents = users.findByAcademicClassIdAndIsDeletedFalse(id);
        if (!assignedStudents.isEmpty()) {
            assignedStudents.forEach(student -> student.setAcademicClassId(null));
            users.saveAll(assignedStudents);
        }
        return Result.success(studentRosters.deleteByAcademicClassId(id));
    }

    @PostMapping("/{id}/students/batch")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    @Transactional
    public Result<AcademicClassImportResult> batch(@PathVariable Long id, @RequestBody @Validated AcademicClassBatchAssignDTO dto) {
        AcademicClass targetClass = requireActive(id);
        List<AcademicClassStudentImportDTO> items = dto.getStudents();
        if (items == null && dto.getStudentNumbers() != null) items = dto.getStudentNumbers().stream().map(this::legacyImport).toList();
        AcademicClassImportResult result = new AcademicClassImportResult();
        if (items == null || items.isEmpty()) throw new BusinessException(400, "请提供至少一名学生");
        Set<String> seen = new LinkedHashSet<>();
        for (AcademicClassStudentImportDTO item : items) importStudent(targetClass, item, seen, result);
        return Result.success(result);
    }

    private void importStudent(AcademicClass targetClass, AcademicClassStudentImportDTO item, Set<String> seen, AcademicClassImportResult result) {
        if (item == null || !hasText(item.getStudentNumber())) { result.setSkipped(result.getSkipped() + 1); return; }
        String number = item.getStudentNumber().trim();
        if (!seen.add(number)) { result.setSkipped(result.getSkipped() + 1); return; }
        result.setTotal(result.getTotal() + 1);
        String name = trim(item.getName());
        AcademicClassStudentRoster roster = studentRosters.findBySchoolIdAndStudentNumber(
                targetClass.getSchoolId(), number).orElseGet(AcademicClassStudentRoster::new);
        roster.setSchoolId(targetClass.getSchoolId());
        roster.setAcademicClassId(targetClass.getId());
        roster.setStudentNumber(number);
        if (hasText(name)) roster.setName(name);
        roster.setStatus("ACTIVE");
        User student = users.findBySchoolIdAndIdentityTypeAndIdentityNumber(
                targetClass.getSchoolId(), User.IdentityType.STUDENT, number).orElse(null);
        if (student == null) student = users.findBySchoolIdAndUsername(targetClass.getSchoolId(), number).orElse(null);
        if (student == null) {
            roster.setUserId(null);
            studentRosters.save(roster);
            result.setUnregistered(result.getUnregistered() + 1);
            result.getUnregisteredNumbers().add(number);
            return;
        }
        if (!student.isStudent() || !student.isEnabled()) { result.getFailedNumbers().add(number); return; }
        if (hasText(name) && (!hasText(student.getNickname()) || student.getNickname().matches("[?？]+"))) student.setNickname(name);
        student.setSchoolId(targetClass.getSchoolId());
        student.setIdentityType(User.IdentityType.STUDENT);
        student.setIdentityNumber(number);
        student.setAcademicClassId(targetClass.getId());
        student.setIdentityStatus(User.IdentityStatus.VERIFIED);
        users.save(student);
        roster.setUserId(student.getId());
        if (!hasText(roster.getName())) roster.setName(student.getNickname());
        studentRosters.save(roster);
        result.setExisting(result.getExisting() + 1); result.getExistingNumbers().add(number);
    }

    private AcademicClassStudentImportDTO legacyImport(String number) { AcademicClassStudentImportDTO item = new AcademicClassStudentImportDTO(); item.setStudentNumber(number); return item; }
    private AcademicClass require(Long id) {
        AcademicClass item = classes.findById(id).orElseThrow(() -> new BusinessException(404, "行政班不存在"));
        adminScopeService.requireSchool(item.getSchoolId());
        return item;
    }
    private AcademicClass requireActive(Long id) {
        AcademicClass item = require(id);
        if (!"ACTIVE".equals(item.getStatus())) throw new BusinessException("行政班已归档，不能执行此操作");
        return item;
    }
    private AcademicClass toEntity(AcademicClassUpsertDTO dto) { AcademicClass item = new AcademicClass(); item.setStatus("ACTIVE"); item.setSchoolId(resolveSchoolId(dto.getSchoolId())); apply(item, dto); return item; }
    private void apply(AcademicClass item, AcademicClassUpsertDTO dto) {
        validateAcademicStructure(item.getSchoolId(), dto.getCollege(), dto.getMajor());
        item.setName(dto.getName().trim());
        item.setCollege(trim(dto.getCollege()));
        item.setCampus(trim(dto.getCampus()));
        item.setMajor(dto.getMajor().trim());
        item.setGrade(dto.getGrade().trim());
        String code = trim(dto.getClassCode());
        if (!hasText(code)) {
            code = item.getId() == null ? generateUniqueCode(item.getSchoolId()) : item.getClassCode();
        }
        if (!hasText(code)) throw new BusinessException("班级编码生成失败，请重试");
        code = code.toUpperCase(Locale.ROOT);
        boolean duplicate = item.getId() == null
                ? classes.existsBySchoolIdAndClassCode(item.getSchoolId(), code)
                : classes.existsBySchoolIdAndClassCodeAndIdNot(item.getSchoolId(), code, item.getId());
        if (duplicate) throw new BusinessException("班级编码已存在，请使用其他编码");
        item.setClassCode(code);
        item.setHeadTeacherName(trim(dto.getHeadTeacherName()));
        item.setCounselorName(trim(dto.getCounselorName()));
    }
    private void validateAcademicStructure(Long schoolId, String college, String major) {
        if (!hasText(college)) throw new BusinessException("请选择所属院系");
        var department = departments.findBySchoolIdAndNameAndStatus(schoolId, college.trim(), "ACTIVE")
                .orElseThrow(() -> new BusinessException("所属院系不存在，请先在学校管理中维护"));
        if (!hasText(major) || majors.findBySchoolIdAndDepartmentIdAndNameAndStatus(schoolId, department.getId(), major.trim(), "ACTIVE").isEmpty()) {
            throw new BusinessException("请选择该院系下已配置的专业");
        }
    }
    private String generateUniqueCode(Long schoolId) {
        final String alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        for (int attempt = 0; attempt < 100; attempt++) {
            StringBuilder code = new StringBuilder("AC-");
            for (int index = 0; index < 8; index++) code.append(alphabet.charAt(random.nextInt(alphabet.length())));
            if (!classes.existsBySchoolIdAndClassCode(schoolId, code.toString())) return code.toString();
        }
        throw new BusinessException("班级编码生成失败，请重试");
    }
    private Long resolveSchoolId(Long requestedSchoolId) {
        return adminScopeService.resolveSchoolId(requestedSchoolId);
    }
    private String createImportUsername(Long schoolId, String number) {
        String code = schools.findById(schoolId).map(item -> item.getCode()).orElse("SCHOOL");
        String base = (code + "_" + number).replaceAll("[^A-Za-z0-9_]", "_");
        if (base.length() > 50) base = base.substring(0, 50);
        String username = base;
        int suffix = 1;
        while (users.existsByUsername(username)) {
            String tail = "_" + suffix++;
            username = base.substring(0, Math.min(50 - tail.length(), base.length())) + tail;
        }
        return username;
    }
    private boolean hasText(String value) { return value != null && !value.trim().isEmpty(); }
    private String trim(String value) { return hasText(value) ? value.trim() : null; }
}
