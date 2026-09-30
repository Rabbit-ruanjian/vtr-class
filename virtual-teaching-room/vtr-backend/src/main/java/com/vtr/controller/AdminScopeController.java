package com.vtr.controller;

import com.vtr.common.Result;
import com.vtr.common.exception.BusinessException;
import com.vtr.dto.AdminScopeUpdateDTO;
import com.vtr.entity.AdminSchoolAccess;
import com.vtr.entity.User;
import com.vtr.repository.AdminSchoolAccessRepository;
import com.vtr.repository.UserRepository;
import com.vtr.security.SecurityUtils;
import com.vtr.service.AdminScopeService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin/scope")
@RequiredArgsConstructor
public class AdminScopeController {
    private final AdminScopeService scopeService;
    private final UserRepository users;
    private final AdminSchoolAccessRepository accesses;

    @GetMapping("/schools")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public Result<?> managedSchools() {
        return Result.success(scopeService.managedSchools());
    }

    @GetMapping("/admins")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public Result<List<Map<String, Object>>> admins() {
        User operator = scopeService.currentUser();
        if (!scopeService.canConfigure(operator)) throw new BusinessException(403, "只有平台管理员可以配置管理员范围");
        List<Map<String, Object>> result = users.findByRoleInAndIsDeletedFalse(
                        List.of(User.UserRole.ADMIN, User.UserRole.SUPER_ADMIN)).stream()
                .map(this::toMap).collect(Collectors.toList());
        return Result.success(result);
    }

    @PutMapping("/admins/{adminId}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public Result<Void> update(@PathVariable Long adminId, @RequestBody AdminScopeUpdateDTO dto) {
        User operator = scopeService.currentUser();
        if (!scopeService.canConfigure(operator)) throw new BusinessException(403, "只有平台管理员可以配置管理员范围");
        scopeService.replaceAccess(adminId, dto.getSchoolIds(), dto.isAllSchools(), SecurityUtils.getCurrentUserId());
        return Result.success();
    }

    private Map<String, Object> toMap(User user) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", user.getId());
        item.put("username", user.getUsername());
        item.put("nickname", user.getNickname());
        item.put("role", user.getRole() == null ? null : user.getRole().name());
        item.put("allSchools", Boolean.TRUE.equals(user.getAdminScopeAll()) || user.getRole() == User.UserRole.SUPER_ADMIN);
        item.put("schoolIds", accesses.findByAdminUserIdAndStatusOrderBySchoolIdAsc(user.getId(), "ACTIVE")
                .stream().map(AdminSchoolAccess::getSchoolId).collect(Collectors.toList()));
        return item;
    }
}
