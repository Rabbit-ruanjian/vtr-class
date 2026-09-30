package com.vtr.repository;

import com.vtr.entity.School;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SchoolRepository extends JpaRepository<School, Long> {
    Optional<School> findByCodeIgnoreCase(String code);
    List<School> findByStatusOrderByNameAsc(String status);
    long countByStatus(String status);
    List<School> findAllByOrderByNameAsc();
    Optional<School> findFirstByOrderByIdAsc();
}
