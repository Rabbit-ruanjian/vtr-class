package com.vtr.repository;

import com.vtr.entity.SchoolTeacherRoster;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SchoolTeacherRosterRepository extends JpaRepository<SchoolTeacherRoster, Long> {
    Optional<SchoolTeacherRoster> findBySchoolIdAndEmployeeNumberAndStatus(
            Long schoolId, String employeeNumber, String status);

    Optional<SchoolTeacherRoster> findBySchoolIdAndEmployeeNumber(Long schoolId, String employeeNumber);

    Optional<SchoolTeacherRoster> findByUserId(Long userId);

    List<SchoolTeacherRoster> findBySchoolId(Long schoolId, Sort sort);
}
