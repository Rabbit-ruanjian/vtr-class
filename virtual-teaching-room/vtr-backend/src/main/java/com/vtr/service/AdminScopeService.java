package com.vtr.service;

import com.vtr.common.exception.BusinessException;
import com.vtr.entity.AdminSchoolAccess;
import com.vtr.entity.School;
import com.vtr.entity.User;
import com.vtr.repository.AdminSchoolAccessRepository;
import com.vtr.repository.SchoolRepository;
import com.vtr.repository.UserRepository;
import com.vtr.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** 统一处理管理员的学校范围，避免每个控制器各写一套权限判断。 */
@Service
@RequiredArgsConstructor
public class AdminScopeService {
    private final UserRepository users;
    private final AdminSchoolAccessRepository accesses;
    private final SchoolRepository schools;

    public User currentUser() {
        Long id = SecurityUtils.getCurrentUserId();
        return id == null ? null : users.findById(id).orElse(null);
    }

    public boolean isGlobalAdmin(User user) {
        // 平台管理员（可管理全部学校）以 adminScopeAll 标记为准；SUPER_ADMIN 角色已废弃，
        // 仅为兼容历史数据保留，同样视为平台管理员。
        return user != null && user.isAdmin()
                && (Boolean.TRUE.equals(user.getAdminScopeAll())
                    || user.getRole() == User.UserRole.SUPER_ADMIN);
    }

    public boolean canConfigure(User user) {
        return isGlobalAdmin(user);
    }

    public boolean isUnboundUserManager(User user) {
        return user != null && user.getRole() == User.UserRole.ADMIN
                && Boolean.TRUE.equals(user.getAdminScopeUnbound());
    }

    /** null 表示平台管理员可查看全部学校；空集合表示尚未绑定学校。 */
    public Set<Long> allowedSchoolIds(User user) {
        if (user == null || !user.isAdmin()) return Collections.emptySet();
        if (isGlobalAdmin(user)) return null;
        if (isUnboundUserManager(user)) return Collections.emptySet();
        if (user.getSchoolId() != null) return Set.of(user.getSchoolId());
        // 兼容旧数据：迁移完成前只取历史范围中的第一所学校。
        List<AdminSchoolAccess> rows = accesses.findByAdminUserIdAndStatusOrderBySchoolIdAsc(user.getId(), "ACTIVE");
        if (!rows.isEmpty()) return Set.of(rows.get(0).getSchoolId());
        return Collections.emptySet();
    }

    public void requireSchool(Long schoolId) {
        User user = currentUser();
        if (schoolId == null) throw new BusinessException(403, "请先选择学校");
        if (user == null) throw new BusinessException(401, "未登录");
        // 教师可以管理本校教学相关数据；行政班导入不能被管理员范围判断误伤。
        if (!user.isAdmin()) {
            if (user.getSchoolId() != null && user.getSchoolId().equals(schoolId)) return;
            throw new BusinessException(403, "只能访问本校数据");
        }
        Set<Long> allowed = allowedSchoolIds(user);
        if (allowed != null && !allowed.contains(schoolId)) {
            throw new BusinessException(403, "你没有管理这所学校的权限");
        }
    }

    public Long resolveSchoolId(Long requestedSchoolId) {
        User user = currentUser();
        if (requestedSchoolId != null) {
            requireSchool(requestedSchoolId);
            return requestedSchoolId;
        }
        if (user == null || !user.isAdmin()) return user == null ? null : user.getSchoolId();
        Set<Long> allowed = allowedSchoolIds(user);
        if (allowed == null) return null;
        if (allowed.size() == 1) return allowed.iterator().next();
        throw new BusinessException(400, "请先选择要管理的学校");
    }

    public void requireUser(User target) {
        if (target == null) throw new BusinessException(404, "用户不存在");
        if (target.isAdmin()) {
            User operator = currentUser();
            if (!canConfigure(operator)) throw new BusinessException(403, "只有平台管理员可以管理管理员账号");
            return;
        }
        User operator = currentUser();
        if (target.getSchoolId() == null) {
            if (!isUnboundUserManager(operator) && !isGlobalAdmin(operator)) {
                throw new BusinessException(403, "未绑定学校的账号由公共账号管理员管理");
            }
            return;
        }
        requireSchool(target.getSchoolId());
    }

    public List<School> managedSchools() {
        User user = currentUser();
        Set<Long> allowed = allowedSchoolIds(user);
        if (allowed == null) return schools.findAllByOrderByNameAsc();
        if (allowed.isEmpty()) return List.of();
        return schools.findAllById(allowed).stream()
                .sorted(java.util.Comparator.comparing(School::getName, String.CASE_INSENSITIVE_ORDER))
                .collect(Collectors.toList());
    }

    @Transactional
    public void replaceAccess(Long adminId, List<Long> schoolIds, boolean allSchools, Long grantedBy) {
        User target = users.findById(adminId).orElseThrow(() -> new BusinessException(404, "管理员不存在"));
        if (!target.isAdmin()) throw new BusinessException("目标用户不是管理员");
        User operator = currentUser();
        if (!canConfigure(operator)) throw new BusinessException(403, "只有平台管理员可以配置管理范围");

        if (target.getRole() == User.UserRole.SUPER_ADMIN) {
            throw new BusinessException("平台管理员不需要绑定学校");
        }
        if (allSchools || schoolIds == null || schoolIds.size() != 1 || schoolIds.get(0) == null) {
            throw new BusinessException("每个管理员只能绑定一所学校");
        }
        Long schoolId = schoolIds.get(0);
        School school = schools.findById(schoolId).orElseThrow(() -> new BusinessException("学校不存在"));
        if (!"ACTIVE".equals(school.getStatus())) throw new BusinessException("学校已停用");
        accesses.deleteByAdminUserId(adminId);
        accesses.save(AdminSchoolAccess.builder()
                .adminUserId(adminId).schoolId(schoolId).grantedBy(grantedBy).status("ACTIVE").build());
        target.setAdminScopeConfigured(true);
        target.setAdminScopeAll(false);
        target.setSchoolId(schoolId);
        users.save(target);
    }
}
