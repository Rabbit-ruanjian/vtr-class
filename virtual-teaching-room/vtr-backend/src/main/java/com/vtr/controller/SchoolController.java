package com.vtr.controller;

import com.vtr.common.Result;
import com.vtr.common.exception.BusinessException;
import com.vtr.dto.SchoolUpsertDTO;
import com.vtr.dto.SchoolAcademicStructureDTO;
import com.vtr.entity.School;
import com.vtr.entity.SchoolDepartment;
import com.vtr.entity.SchoolMajor;
import com.vtr.repository.SchoolRepository;
import com.vtr.repository.SchoolDepartmentRepository;
import com.vtr.repository.SchoolMajorRepository;
import com.vtr.service.AdminScopeService;
import com.vtr.vo.SchoolAcademicStructureVO;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Locale;
import java.security.SecureRandom;
import java.util.Map;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/schools")
@RequiredArgsConstructor
public class SchoolController {
    private final SchoolRepository schools;
    private final SchoolDepartmentRepository departments;
    private final SchoolMajorRepository majors;
    private final AdminScopeService scopeService;
    private final SecureRandom random = new SecureRandom();

    @GetMapping("/active")
    public Result<List<School>> active() {
        return Result.success(schools.findByStatusOrderByNameAsc("ACTIVE"));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public Result<List<School>> list() {
        return Result.success(scopeService.managedSchools());
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    @Transactional
    public Result<School> create(@RequestBody @Validated SchoolUpsertDTO dto) {
        if (!scopeService.canConfigure(scopeService.currentUser())) throw new BusinessException(403, "只有平台管理员可以新增学校");
        String code = normalizeCode(dto.getCode());
        if (code == null) code = generateUniqueCode();
        schools.findByCodeIgnoreCase(code).ifPresent(item -> {
            throw new BusinessException("学校编码已存在");
        });
        School school = new School();
        apply(school, dto, code);
        return Result.success(schools.save(school));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    @Transactional
    public Result<School> update(@PathVariable Long id, @RequestBody @Validated SchoolUpsertDTO dto) {
        scopeService.requireSchool(id);
        School school = require(id);
        String code = normalizeCode(dto.getCode());
        if (code == null) code = school.getCode();
        schools.findByCodeIgnoreCase(code).filter(item -> !item.getId().equals(id)).ifPresent(item -> {
            throw new BusinessException("学校编码已存在");
        });
        apply(school, dto, code);
        return Result.success(schools.save(school));
    }

    @PostMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    @Transactional
    public Result<Void> status(@PathVariable Long id, @RequestParam String value) {
        scopeService.requireSchool(id);
        School school = require(id);
        String next = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (!List.of("ACTIVE", "INACTIVE", "ARCHIVED").contains(next)) {
            throw new BusinessException("学校状态无效");
        }
        school.setStatus(next);
        schools.save(school);
        return Result.success();
    }

    @GetMapping("/{id}/academic-structure")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN','SUPER_ADMIN')")
    public Result<SchoolAcademicStructureVO> academicStructure(@PathVariable Long id) {
        scopeService.requireSchool(id);
        return Result.success(toStructure(id, false));
    }

    @PutMapping("/{id}/academic-structure")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    @Transactional
    public Result<SchoolAcademicStructureVO> replaceAcademicStructure(@PathVariable Long id,
                                                                        @RequestBody @Validated SchoolAcademicStructureDTO dto) {
        scopeService.requireSchool(id);
        Map<String, SchoolDepartment> existingDepartments = departments.findBySchoolIdOrderBySortOrderAscNameAsc(id).stream()
                .collect(Collectors.toMap(item -> item.getName().trim(), item -> item, (left, right) -> left, HashMap::new));
        Set<String> activeDepartmentNames = new HashSet<>();
        int departmentOrder = 0;
        for (SchoolAcademicStructureDTO.Department input : dto.getDepartments() == null ? List.<SchoolAcademicStructureDTO.Department>of() : dto.getDepartments()) {
            String departmentName = input.getName().trim();
            if (!activeDepartmentNames.add(departmentName)) throw new BusinessException("院系名称不能重复：" + departmentName);
            SchoolDepartment department = existingDepartments.getOrDefault(departmentName, new SchoolDepartment());
            department.setSchoolId(id);
            department.setName(departmentName);
            department.setStatus("ACTIVE");
            department.setSortOrder(departmentOrder++);
            department = departments.save(department);
            final SchoolDepartment savedDepartment = department;

            Map<String, SchoolMajor> existingMajors = majors.findBySchoolIdOrderByDepartmentIdAscSortOrderAscNameAsc(id).stream()
                    .filter(item -> id.equals(item.getSchoolId()) && savedDepartment.getId().equals(item.getDepartmentId()))
                    .collect(Collectors.toMap(item -> item.getName().trim(), item -> item, (left, right) -> left, HashMap::new));
            Set<String> activeMajorNames = new HashSet<>();
            int majorOrder = 0;
            for (SchoolAcademicStructureDTO.Major majorInput : input.getMajors() == null ? List.<SchoolAcademicStructureDTO.Major>of() : input.getMajors()) {
                String majorName = majorInput.getName().trim();
                if (!activeMajorNames.add(majorName)) throw new BusinessException("同一院系下专业名称不能重复：" + majorName);
                SchoolMajor major = existingMajors.getOrDefault(majorName, new SchoolMajor());
                major.setSchoolId(id);
                major.setDepartmentId(department.getId());
                major.setName(majorName);
                major.setStatus("ACTIVE");
                major.setSortOrder(majorOrder++);
                majors.save(major);
            }
            existingMajors.values().stream().filter(item -> !activeMajorNames.contains(item.getName().trim())).forEach(item -> {
                item.setStatus("INACTIVE");
                majors.save(item);
            });
        }
        existingDepartments.values().stream().filter(item -> !activeDepartmentNames.contains(item.getName().trim())).forEach(item -> {
            item.setStatus("INACTIVE");
            departments.save(item);
        });
        return Result.success(toStructure(id, false));
    }

    private SchoolAcademicStructureVO toStructure(Long schoolId, boolean includeInactive) {
        List<SchoolDepartment> departmentRows = includeInactive
                ? departments.findBySchoolIdOrderBySortOrderAscNameAsc(schoolId)
                : departments.findBySchoolIdAndStatusOrderBySortOrderAscNameAsc(schoolId, "ACTIVE");
        List<SchoolMajor> majorRows = includeInactive
                ? majors.findBySchoolIdOrderByDepartmentIdAscSortOrderAscNameAsc(schoolId)
                : majors.findBySchoolIdAndStatusOrderByDepartmentIdAscSortOrderAscNameAsc(schoolId, "ACTIVE");
        Map<Long, SchoolAcademicStructureVO.Department> result = new java.util.LinkedHashMap<>();
        for (SchoolDepartment row : departmentRows) {
            result.put(row.getId(), new SchoolAcademicStructureVO.Department(row.getId(), row.getName(), new java.util.ArrayList<>()));
        }
        for (SchoolMajor row : majorRows) {
            SchoolAcademicStructureVO.Department department = result.get(row.getDepartmentId());
            if (department != null) department.getMajors().add(new SchoolAcademicStructureVO.Major(row.getId(), row.getName()));
        }
        SchoolAcademicStructureVO data = new SchoolAcademicStructureVO();
        data.setDepartments(new java.util.ArrayList<>(result.values()));
        return data;
    }

    private School require(Long id) {
        return schools.findById(id).orElseThrow(() -> new BusinessException(404, "学校不存在"));
    }

    private void apply(School school, SchoolUpsertDTO dto, String code) {
        school.setName(dto.getName().trim());
        school.setCode(code);
        school.setRegion(dto.getRegion() == null || dto.getRegion().isBlank() ? null : dto.getRegion().trim());
        if (school.getStatus() == null) school.setStatus("ACTIVE");
    }

    private String normalizeCode(String code) {
        return code == null || code.isBlank() ? null : code.trim().toUpperCase(Locale.ROOT);
    }

    private String generateUniqueCode() {
        final String alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        for (int attempt = 0; attempt < 100; attempt++) {
            StringBuilder code = new StringBuilder("SCH-");
            for (int index = 0; index < 8; index++) {
                code.append(alphabet.charAt(random.nextInt(alphabet.length())));
            }
            String candidate = code.toString();
            if (!schools.findByCodeIgnoreCase(candidate).isPresent()) return candidate;
        }
        throw new BusinessException("学校编码生成失败，请重试");
    }
}
