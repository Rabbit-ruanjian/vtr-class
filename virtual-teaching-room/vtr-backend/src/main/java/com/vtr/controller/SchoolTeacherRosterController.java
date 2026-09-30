package com.vtr.controller;

import com.vtr.common.Result;
import com.vtr.common.exception.BusinessException;
import com.vtr.dto.SchoolTeacherRosterUpsertDTO;
import com.vtr.entity.SchoolTeacherRoster;
import com.vtr.repository.SchoolRepository;
import com.vtr.repository.SchoolTeacherRosterRepository;
import com.vtr.service.AdminScopeService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Locale;

/** 学校管理员维护教师工号白名单。 */
@RestController
@RequestMapping("/api/schools/{schoolId}/teacher-roster")
@RequiredArgsConstructor
public class SchoolTeacherRosterController {
    private final SchoolTeacherRosterRepository rosters;
    private final SchoolRepository schools;
    private final AdminScopeService scopeService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public Result<List<SchoolTeacherRoster>> list(@PathVariable Long schoolId) {
        requireSchool(schoolId);
        return Result.success(rosters.findBySchoolId(schoolId, Sort.by(Sort.Direction.ASC, "employeeNumber")));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    @Transactional
    public Result<SchoolTeacherRoster> create(@PathVariable Long schoolId,
                                               @RequestBody @Validated SchoolTeacherRosterUpsertDTO dto) {
        requireSchool(schoolId);
        String number = normalize(dto.getEmployeeNumber());
        if (rosters.findBySchoolIdAndEmployeeNumber(schoolId, number).isPresent()) {
            throw new BusinessException("该学校的工号已存在");
        }
        SchoolTeacherRoster roster = new SchoolTeacherRoster();
        roster.setSchoolId(schoolId);
        apply(roster, dto, number);
        return Result.success(rosters.save(roster));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    @Transactional
    public Result<SchoolTeacherRoster> update(@PathVariable Long schoolId, @PathVariable Long id,
                                               @RequestBody @Validated SchoolTeacherRosterUpsertDTO dto) {
        requireSchool(schoolId);
        SchoolTeacherRoster roster = rosters.findById(id)
                .orElseThrow(() -> new BusinessException(404, "教师名单不存在"));
        if (!schoolId.equals(roster.getSchoolId())) throw new BusinessException(403, "无权操作其他学校的教师名单");
        String number = normalize(dto.getEmployeeNumber());
        rosters.findBySchoolIdAndEmployeeNumber(schoolId, number)
                .filter(item -> !item.getId().equals(id))
                .ifPresent(item -> { throw new BusinessException("该学校的工号已存在"); });
        apply(roster, dto, number);
        return Result.success(rosters.save(roster));
    }

    @PostMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    @Transactional
    public Result<Void> status(@PathVariable Long schoolId, @PathVariable Long id,
                               @RequestParam String value) {
        requireSchool(schoolId);
        SchoolTeacherRoster roster = rosters.findById(id)
                .orElseThrow(() -> new BusinessException(404, "教师名单不存在"));
        if (!schoolId.equals(roster.getSchoolId())) throw new BusinessException(403, "无权操作其他学校的教师名单");
        String next = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (!List.of("ACTIVE", "INACTIVE").contains(next)) throw new BusinessException("名单状态无效");
        roster.setStatus(next);
        rosters.save(roster);
        return Result.success();
    }

    private void requireSchool(Long schoolId) {
        scopeService.requireSchool(schoolId);
        schools.findById(schoolId).orElseThrow(() -> new BusinessException(404, "学校不存在"));
    }

    private void apply(SchoolTeacherRoster roster, SchoolTeacherRosterUpsertDTO dto, String number) {
        roster.setEmployeeNumber(number);
        roster.setName(trim(dto.getName()));
        roster.setDepartment(trim(dto.getDepartment()));
        if (roster.getStatus() == null) roster.setStatus("ACTIVE");
    }

    private String normalize(String value) {
        String result = trim(value);
        if (result == null) throw new BusinessException("工号不能为空");
        return result.toUpperCase(Locale.ROOT);
    }

    private String trim(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
