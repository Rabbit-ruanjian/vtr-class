package com.vtr.repository;

import com.vtr.entity.AcademicClassStudentRoster;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AcademicClassStudentRosterRepository extends JpaRepository<AcademicClassStudentRoster, Long> {
    Optional<AcademicClassStudentRoster> findBySchoolIdAndStudentNumber(Long schoolId, String studentNumber);

    Optional<AcademicClassStudentRoster> findByUserId(Long userId);

    List<AcademicClassStudentRoster> findByAcademicClassIdAndStatusOrderByStudentNumberAsc(Long academicClassId, String status);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM AcademicClassStudentRoster roster WHERE roster.academicClassId = :academicClassId")
    int deleteByAcademicClassId(@Param("academicClassId") Long academicClassId);
}
