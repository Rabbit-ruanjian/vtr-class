package com.vtr.repository;

import com.vtr.entity.AdminSchoolAccess;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AdminSchoolAccessRepository extends JpaRepository<AdminSchoolAccess, Long> {
    List<AdminSchoolAccess> findByAdminUserIdAndStatusOrderBySchoolIdAsc(Long adminUserId, String status);
    Optional<AdminSchoolAccess> findByAdminUserIdAndSchoolId(Long adminUserId, Long schoolId);
    List<AdminSchoolAccess> findByAdminUserIdOrderBySchoolIdAsc(Long adminUserId);
    void deleteByAdminUserId(Long adminUserId);
}
