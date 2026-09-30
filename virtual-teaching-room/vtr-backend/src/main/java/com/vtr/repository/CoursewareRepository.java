package com.vtr.repository;

import com.vtr.entity.Courseware;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

public interface CoursewareRepository extends JpaRepository<Courseware, Long> {

    @Query("SELECT DISTINCT c.chapter FROM Courseware c WHERE c.courseId = :courseId " +
            "AND c.chapter IS NOT NULL AND TRIM(c.chapter) <> '' " +
            "AND c.status NOT IN ('DELETED', 'ARCHIVED')")
    List<String> findDistinctChapterNames(@Param("courseId") Long courseId);

    @Query("SELECT DISTINCT c.chapter FROM Courseware c WHERE c.courseId = :courseId AND c.status = 'ACTIVE' " +
            "AND c.chapter IS NOT NULL AND TRIM(c.chapter) <> '' AND " +
            "((c.visibility = 'PUBLIC' AND c.targetAudience IN ('ALL', 'STUDENT')) OR " +
            "(c.visibility = 'COURSE' AND EXISTS (SELECT 1 FROM CourseMember cm WHERE cm.courseId = c.courseId AND cm.userId = :studentId AND cm.status = 'ACTIVE')) OR " +
            "(c.visibility = 'CLASS' AND c.classroomId IN " +
            "(SELECT csr.classroomId FROM ClassroomStudentRelation csr WHERE csr.studentId = :studentId AND csr.status = 'ACTIVE')))" )
    List<String> findDistinctVisibleChapterNames(@Param("courseId") Long courseId, @Param("studentId") Long studentId);

    long countByCourseIdAndStatus(Long courseId, String status);

    List<Courseware> findByCourseIdAndChapter(Long courseId, String chapter);

    Page<Courseware> findByStatusAndResourceTypeIn(String status, List<String> resourceTypes, Pageable pageable);

    @Query("SELECT c FROM Courseware c WHERE c.status = :status AND c.resourceType IN :resourceTypes " +
            "AND c.visibility = 'PUBLIC' " +
            "AND (:schoolId IS NULL OR c.schoolId = :schoolId)")
    Page<Courseware> findByStatusAndResourceTypeInAndSchoolId(@Param("status") String status,
                                                               @Param("resourceTypes") List<String> resourceTypes,
                                                               @Param("schoolId") Long schoolId,
                                                               Pageable pageable);

    long countByFileUrlAndStatus(String fileUrl, String status);

    @Query("SELECT c FROM Courseware c WHERE c.resourceType = 'teaching-video' " +
            "AND c.status <> 'DELETED' " +
            "AND (:courseId IS NULL OR c.courseId = :courseId) " +
            "AND (:chapter IS NULL OR c.chapter = :chapter) " +
            "AND (:schoolId IS NULL OR c.schoolId = :schoolId) ORDER BY c.featured DESC, c.createdAt DESC")
    List<Courseware> findTeachingVideos(@Param("courseId") Long courseId, @Param("chapter") String chapter,
                                        @Param("schoolId") Long schoolId);

    // ========== 教师端方法 ==========

    // 教师获取课件（自己的 + 公开课件）
    @Query("SELECT c FROM Courseware c WHERE c.status <> 'DELETED' AND " +
            "(c.teacherId = :teacherId OR EXISTS (SELECT 1 FROM CourseMember cm WHERE cm.courseId = c.courseId " +
            "AND cm.userId = :teacherId AND cm.status = 'ACTIVE' AND cm.role IN ('OWNER', 'CO_TEACHER', 'TEACHING_ASSISTANT')) " +
            "OR (c.visibility = 'PUBLIC' AND c.targetAudience IN :audiences)) " +
            "AND (:resourceType IS NULL OR c.resourceType = :resourceType OR " +
            "(:resourceType = 'teaching-courseware' AND c.resourceType IS NULL)) " +
            "AND (:visibility IS NULL OR c.visibility = :visibility) " +
            "AND (:targetAudience IS NULL OR c.targetAudience = :targetAudience) " +
            "AND (:fileType IS NULL OR c.fileType = :fileType) " +
            "AND (:courseId IS NULL OR c.courseId = :courseId) " +
            "AND (:chapter IS NULL OR c.chapter = :chapter) " +
            "AND (:classroomId IS NULL OR c.classroomId = :classroomId) " +
            "AND (:schoolId IS NULL OR c.schoolId = :schoolId)")
    Page<Courseware> findForTeacher(@Param("teacherId") Long teacherId,
                                    @Param("audiences") List<String> audiences,
                                    @Param("resourceType") String resourceType,
                                    @Param("visibility") String visibility, @Param("targetAudience") String targetAudience,
                                     @Param("fileType") String fileType, @Param("courseId") Long courseId, @Param("chapter") String chapter, @Param("classroomId") Long classroomId,
                                     @Param("schoolId") Long schoolId,
                                     Pageable pageable);

    // 教师搜索课件
    @Query("SELECT c FROM Courseware c WHERE c.status <> 'DELETED' AND " +
            "(c.teacherId = :teacherId OR EXISTS (SELECT 1 FROM CourseMember cm WHERE cm.courseId = c.courseId " +
            "AND cm.userId = :teacherId AND cm.status = 'ACTIVE' AND cm.role IN ('OWNER', 'CO_TEACHER', 'TEACHING_ASSISTANT')) " +
            "OR (c.visibility = 'PUBLIC' AND c.targetAudience IN :audiences)) " +
            "AND (:resourceType IS NULL OR c.resourceType = :resourceType OR " +
            "(:resourceType = 'teaching-courseware' AND c.resourceType IS NULL)) " +
            "AND (:visibility IS NULL OR c.visibility = :visibility) " +
            "AND (:targetAudience IS NULL OR c.targetAudience = :targetAudience) " +
            "AND (:fileType IS NULL OR c.fileType = :fileType) " +
            "AND (:courseId IS NULL OR c.courseId = :courseId) " +
            "AND (:chapter IS NULL OR c.chapter = :chapter) " +
            "AND (:classroomId IS NULL OR c.classroomId = :classroomId) " +
            "AND (:schoolId IS NULL OR c.schoolId = :schoolId) " +
            "AND (c.title LIKE %:keyword% OR c.description LIKE %:keyword%)")
    Page<Courseware> findForTeacherWithKeyword(@Param("teacherId") Long teacherId,
                                               @Param("audiences") List<String> audiences,
                                               @Param("resourceType") String resourceType,
                                               @Param("visibility") String visibility, @Param("targetAudience") String targetAudience,
                                               @Param("fileType") String fileType, @Param("courseId") Long courseId, @Param("chapter") String chapter, @Param("classroomId") Long classroomId,
                                               @Param("schoolId") Long schoolId,
                                               @Param("keyword") String keyword,
                                               Pageable pageable);

    // 教师获取自己的课件（原有）
    Page<Courseware> findByTeacherIdAndStatus(Long teacherId, String status, Pageable pageable);

    // 教师搜索自己的课件
    @Query("SELECT c FROM Courseware c WHERE c.teacherId = :teacherId AND c.status = 'ACTIVE' " +
            "AND (c.title LIKE %:keyword% OR c.description LIKE %:keyword%)")
    Page<Courseware> searchByTeacherAndKeyword(@Param("teacherId") Long teacherId,
                                               @Param("keyword") String keyword,
                                               Pageable pageable);

    // ========== 学生端方法 ==========

    // 学生获取公开课件
    @Query("SELECT c FROM Courseware c WHERE c.status = 'ACTIVE' AND " +
            "((c.visibility = 'PUBLIC' AND c.targetAudience IN :audiences) OR " +
            "(c.visibility = 'COURSE' AND EXISTS (SELECT 1 FROM CourseMember cm WHERE cm.courseId = c.courseId AND cm.userId = :studentId AND cm.status = 'ACTIVE')) OR " +
            "(c.visibility = 'CLASS' AND c.classroomId IS NOT NULL AND EXISTS " +
            "(SELECT 1 FROM ClassroomStudentRelation csr WHERE csr.classroomId = c.classroomId " +
            "AND csr.studentId = :studentId AND csr.status = 'ACTIVE'))) " +
            "AND (:resourceType IS NULL OR c.resourceType = :resourceType OR " +
            "(:resourceType = 'teaching-courseware' AND c.resourceType IS NULL)) " +
            "AND (:visibility IS NULL OR c.visibility = :visibility) " +
            "AND (:targetAudience IS NULL OR c.targetAudience = :targetAudience) " +
            "AND (:fileType IS NULL OR c.fileType = :fileType) " +
            "AND (:courseId IS NULL OR c.courseId = :courseId) " +
            "AND (:chapter IS NULL OR c.chapter = :chapter) " +
            "AND (:classroomId IS NULL OR c.classroomId = :classroomId) " +
            "AND (:schoolId IS NULL OR c.schoolId = :schoolId)")
    Page<Courseware> findForStudent(@Param("studentId") Long studentId,
                                    @Param("audiences") List<String> audiences,
                                    @Param("resourceType") String resourceType,
                                    @Param("visibility") String visibility, @Param("targetAudience") String targetAudience,
                                     @Param("fileType") String fileType, @Param("courseId") Long courseId, @Param("chapter") String chapter, @Param("classroomId") Long classroomId,
                                     @Param("schoolId") Long schoolId,
                                     Pageable pageable);

    // 学生搜索公开课件
    @Query("SELECT c FROM Courseware c WHERE c.status = 'ACTIVE' AND " +
            "((c.visibility = 'PUBLIC' AND c.targetAudience IN :audiences) OR " +
            "(c.visibility = 'COURSE' AND EXISTS (SELECT 1 FROM CourseMember cm WHERE cm.courseId = c.courseId AND cm.userId = :studentId AND cm.status = 'ACTIVE')) OR " +
            "(c.visibility = 'CLASS' AND c.classroomId IS NOT NULL AND EXISTS " +
            "(SELECT 1 FROM ClassroomStudentRelation csr WHERE csr.classroomId = c.classroomId " +
            "AND csr.studentId = :studentId AND csr.status = 'ACTIVE'))) " +
            "AND (:resourceType IS NULL OR c.resourceType = :resourceType OR " +
            "(:resourceType = 'teaching-courseware' AND c.resourceType IS NULL)) " +
            "AND (:visibility IS NULL OR c.visibility = :visibility) " +
            "AND (:targetAudience IS NULL OR c.targetAudience = :targetAudience) " +
            "AND (:fileType IS NULL OR c.fileType = :fileType) " +
            "AND (:courseId IS NULL OR c.courseId = :courseId) " +
            "AND (:chapter IS NULL OR c.chapter = :chapter) " +
            "AND (:classroomId IS NULL OR c.classroomId = :classroomId) " +
            "AND (:schoolId IS NULL OR c.schoolId = :schoolId) " +
            "AND (c.title LIKE %:keyword% OR c.description LIKE %:keyword%)")
    Page<Courseware> findForStudentWithKeyword(@Param("studentId") Long studentId,
                                               @Param("audiences") List<String> audiences,
                                               @Param("resourceType") String resourceType,
                                               @Param("visibility") String visibility, @Param("targetAudience") String targetAudience,
                                               @Param("fileType") String fileType, @Param("courseId") Long courseId, @Param("chapter") String chapter, @Param("classroomId") Long classroomId,
                                               @Param("schoolId") Long schoolId,
                                               @Param("keyword") String keyword,
                                               Pageable pageable);

    // ========== 管理员端方法 ==========

    // 管理员搜索所有课件（包括未公开的）
    @Query("SELECT c FROM Courseware c WHERE c.status <> 'DELETED' AND " +
            "(:resourceType IS NULL OR c.resourceType = :resourceType OR " +
            "(:resourceType = 'teaching-courseware' AND c.resourceType IS NULL)) " +
            "AND (:visibility IS NULL OR c.visibility = :visibility) " +
            "AND (:targetAudience IS NULL OR c.targetAudience = :targetAudience) " +
            "AND (:fileType IS NULL OR c.fileType = :fileType) " +
            "AND (:courseId IS NULL OR c.courseId = :courseId) " +
            "AND (:chapter IS NULL OR c.chapter = :chapter) " +
            "AND (:classroomId IS NULL OR c.classroomId = :classroomId) " +
            "AND (:schoolId IS NULL OR c.schoolId = :schoolId) AND " +
            "(c.title LIKE %:keyword% OR c.description LIKE %:keyword%)")
    Page<Courseware> searchAllWithKeyword(@Param("resourceType") String resourceType,
                                          @Param("visibility") String visibility, @Param("targetAudience") String targetAudience,
                                          @Param("fileType") String fileType, @Param("courseId") Long courseId, @Param("chapter") String chapter, @Param("classroomId") Long classroomId,
                                          @Param("schoolId") Long schoolId,
                                          @Param("keyword") String keyword,
                                          Pageable pageable);

    // 管理员获取所有课件（原方法，不分页）
    @Query("SELECT c FROM Courseware c WHERE c.status <> 'DELETED' AND " +
            "(:resourceType IS NULL OR c.resourceType = :resourceType OR " +
            "(:resourceType = 'teaching-courseware' AND c.resourceType IS NULL)) " +
            "AND (:visibility IS NULL OR c.visibility = :visibility) " +
            "AND (:targetAudience IS NULL OR c.targetAudience = :targetAudience) " +
            "AND (:fileType IS NULL OR c.fileType = :fileType) " +
            "AND (:courseId IS NULL OR c.courseId = :courseId) " +
            "AND (:chapter IS NULL OR c.chapter = :chapter) " +
            "AND (:classroomId IS NULL OR c.classroomId = :classroomId) " +
            "AND (:schoolId IS NULL OR c.schoolId = :schoolId)")
    Page<Courseware> findAllActiveByResourceType(@Param("resourceType") String resourceType,
                                                 @Param("visibility") String visibility, @Param("targetAudience") String targetAudience,
                                                 @Param("fileType") String fileType, @Param("courseId") Long courseId, @Param("chapter") String chapter, @Param("classroomId") Long classroomId,
                                                 @Param("schoolId") Long schoolId,
                                                 Pageable pageable);

    // ========== 公用方法 ==========

    // 搜索所有课件
    @Query("SELECT c FROM Courseware c WHERE c.status = 'ACTIVE' AND " +
            "(c.title LIKE %:keyword% OR c.description LIKE %:keyword%)")
    Page<Courseware> searchByKeyword(@Param("keyword") String keyword, Pageable pageable);

    @Query("SELECT COUNT(c) FROM Courseware c WHERE c.courseId = :courseId " +
            "AND c.chapter = :chapter AND c.sectionId = :sectionId " +
            "AND c.resourceType = 'teaching-outline' AND c.status IN ('ACTIVE', 'PENDING')")
    long countActiveTeachingOutlines(@Param("courseId") Long courseId, @Param("chapter") String chapter,
                                     @Param("sectionId") Long sectionId);

    @Query("SELECT COUNT(c) FROM Courseware c WHERE c.courseId = :courseId " +
            "AND c.chapter = :chapter AND c.sectionId = :sectionId " +
            "AND c.resourceType = 'teaching-outline' AND c.status IN ('ACTIVE', 'PENDING') " +
            "AND c.id <> :excludeId")
    long countActiveTeachingOutlinesExcluding(@Param("courseId") Long courseId,
                                              @Param("chapter") String chapter,
                                              @Param("sectionId") Long sectionId,
                                              @Param("excludeId") Long excludeId);

    @Query("SELECT COUNT(c) FROM Courseware c WHERE c.courseId = :courseId " +
            "AND c.resourceType = 'teaching-outline' AND c.status IN ('ACTIVE', 'PENDING')")
    long countActiveTeachingOutlinesByCourse(@Param("courseId") Long courseId);

    @Query("SELECT COUNT(c) FROM Courseware c WHERE c.courseId = :courseId " +
            "AND c.resourceType = 'teaching-outline' AND c.status IN ('ACTIVE', 'PENDING') " +
            "AND c.id <> :excludeId")
    long countActiveTeachingOutlinesByCourseExcluding(@Param("courseId") Long courseId,
                                                      @Param("excludeId") Long excludeId);

    Optional<Courseware> findFirstByCourseIdAndChapterAndResourceTypeAndStatusOrderByCreatedAtDesc(
            Long courseId, String chapter, String resourceType, String status);

    Optional<Courseware> findFirstByCourseIdAndChapterAndResourceTypeAndStatusInOrderByCreatedAtDesc(
            Long courseId, String chapter, String resourceType, List<String> statuses);

    Optional<Courseware> findFirstByCourseIdAndResourceTypeAndStatusInOrderByCreatedAtDesc(
            Long courseId, String resourceType, List<String> statuses);

    Optional<Courseware> findFirstByCourseIdAndChapterAndSectionIdAndResourceTypeAndStatusOrderByCreatedAtDesc(
            Long courseId, String chapter, Long sectionId, String resourceType, String status);

    Optional<Courseware> findFirstByCourseIdAndChapterAndSectionIdAndResourceTypeAndStatusInOrderByCreatedAtDesc(
            Long courseId, String chapter, Long sectionId, String resourceType, List<String> statuses);

    // 更新浏览次数
    @Modifying
    @Transactional
    @Query("UPDATE Courseware c SET c.viewCount = c.viewCount + 1 WHERE c.id = :id")
    void incrementViewCount(@Param("id") Long id);

    // 更新下载次数
    @Modifying
    @Transactional
    @Query("UPDATE Courseware c SET c.downloadCount = c.downloadCount + 1 WHERE c.id = :id")
    void incrementDownloadCount(@Param("id") Long id);

    // 软删除
    @Modifying
    @Transactional
    @Query("UPDATE Courseware c SET c.status = 'DELETED' WHERE c.id = :id")
    void softDelete(@Param("id") Long id);
}
