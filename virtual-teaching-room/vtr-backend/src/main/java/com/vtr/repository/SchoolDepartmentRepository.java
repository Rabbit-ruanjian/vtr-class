package com.vtr.repository;

import com.vtr.entity.SchoolDepartment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SchoolDepartmentRepository extends JpaRepository<SchoolDepartment, Long> {
    List<SchoolDepartment> findBySchoolIdOrderBySortOrderAscNameAsc(Long schoolId);
    List<SchoolDepartment> findBySchoolIdAndStatusOrderBySortOrderAscNameAsc(Long schoolId, String status);
    java.util.Optional<SchoolDepartment> findBySchoolIdAndNameAndStatus(Long schoolId, String name, String status);
}
