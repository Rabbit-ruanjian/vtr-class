package com.vtr.repository;

import com.vtr.entity.SchoolMajor;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SchoolMajorRepository extends JpaRepository<SchoolMajor, Long> {
    List<SchoolMajor> findBySchoolIdOrderByDepartmentIdAscSortOrderAscNameAsc(Long schoolId);
    List<SchoolMajor> findBySchoolIdAndStatusOrderByDepartmentIdAscSortOrderAscNameAsc(Long schoolId, String status);
    java.util.Optional<SchoolMajor> findBySchoolIdAndDepartmentIdAndNameAndStatus(Long schoolId, Long departmentId, String name, String status);
}
