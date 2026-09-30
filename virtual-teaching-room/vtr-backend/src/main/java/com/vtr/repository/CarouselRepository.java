package com.vtr.repository;

import com.vtr.entity.Carousel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface CarouselRepository extends JpaRepository<Carousel, Long> {

    List<Carousel> findByStatusOrderBySortOrderAsc(Carousel.CarouselStatus status);

    @Query("SELECT c FROM Carousel c WHERE c.status = 'ACTIVE' AND " +
            "(c.startTime IS NULL OR c.startTime <= :now) AND " +
            "(c.endTime IS NULL OR c.endTime > :now) " +
            "ORDER BY c.sortOrder ASC")
    List<Carousel> findActiveCarousels(@Param("now") LocalDateTime now);

    @Modifying
    @Query("UPDATE Carousel c SET c.clickCount = c.clickCount + 1 WHERE c.id = :id")
    void incrementClickCount(@Param("id") Long id);

    @Modifying
    @Query("UPDATE Carousel c SET c.status = 'INACTIVE' WHERE c.id = :id")
    void deactivate(@Param("id") Long id);
}