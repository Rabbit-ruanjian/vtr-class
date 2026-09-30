package com.vtr.repository;

import com.vtr.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    Optional<User> findByEmail(String email);

    Optional<User> findByPhone(String phone);

    boolean existsByPhone(String phone);

    Optional<User> findByUnitNameAndIdentityTypeAndIdentityNumber(
            String unitName, User.IdentityType identityType, String identityNumber);

    Optional<User> findBySchoolIdAndIdentityTypeAndIdentityNumber(
            Long schoolId, User.IdentityType identityType, String identityNumber);

    Optional<User> findBySchoolIdAndUsername(Long schoolId, String username);

    @Query("SELECT u FROM User u WHERE u.isDeleted = false AND " +
            "(:keyword IS NULL OR u.username LIKE %:keyword% OR u.nickname LIKE %:keyword% OR u.email LIKE %:keyword% OR u.identityNumber LIKE %:keyword%) AND " +
            "(:schoolId IS NULL OR u.schoolId = :schoolId) AND " +
            "(:role IS NULL OR u.role = :role) AND " +
            "(:status IS NULL OR u.status = :status)")
    Page<User> findByConditions(@Param("keyword") String keyword,
                                @Param("schoolId") Long schoolId,
                                @Param("role") User.UserRole role,
                                @Param("status") User.UserStatus status,
                                Pageable pageable);

    @Query("SELECT u FROM User u WHERE u.isDeleted = false AND u.identityStatus = :identityStatus AND " +
            "(:keyword IS NULL OR u.username LIKE %:keyword% OR u.nickname LIKE %:keyword% OR u.identityNumber LIKE %:keyword%) AND " +
            "(:schoolId IS NULL OR u.schoolId = :schoolId) AND (:role IS NULL OR u.role = :role)")
    Page<User> findIdentityReviews(@Param("keyword") String keyword,
                                   @Param("schoolId") Long schoolId,
                                   @Param("role") User.UserRole role,
                                   @Param("identityStatus") User.IdentityStatus identityStatus,
                                   Pageable pageable);

    @Query("SELECT u FROM User u WHERE u.isDeleted = false AND u.identityStatus = :identityStatus AND u.schoolId IN :schoolIds AND " +
            "(:keyword IS NULL OR u.username LIKE %:keyword% OR u.nickname LIKE %:keyword% OR u.identityNumber LIKE %:keyword%) AND " +
            "(:schoolId IS NULL OR u.schoolId = :schoolId) AND (:role IS NULL OR u.role = :role)")
    Page<User> findIdentityReviewsInSchools(@Param("keyword") String keyword,
                                            @Param("schoolId") Long schoolId,
                                            @Param("role") User.UserRole role,
                                            @Param("identityStatus") User.IdentityStatus identityStatus,
                                            @Param("schoolIds") Collection<Long> schoolIds,
                                            Pageable pageable);

    @Query("SELECT u FROM User u WHERE u.isDeleted = false AND u.schoolId IS NULL AND u.role IN ('STUDENT', 'TEACHER') AND " +
            "(:keyword IS NULL OR u.username LIKE %:keyword% OR u.nickname LIKE %:keyword% OR u.email LIKE %:keyword% OR u.identityNumber LIKE %:keyword%) AND " +
            "(:role IS NULL OR u.role = :role) AND (:status IS NULL OR u.status = :status)")
    Page<User> findUnboundByConditions(@Param("keyword") String keyword,
                                       @Param("role") User.UserRole role,
                                       @Param("status") User.UserStatus status,
                                       Pageable pageable);

    @Query("SELECT u FROM User u WHERE u.isDeleted = false AND " +
            "(:keyword IS NULL OR u.username LIKE %:keyword% OR u.nickname LIKE %:keyword% OR u.email LIKE %:keyword% OR u.identityNumber LIKE %:keyword%) AND " +
            "(:schoolId IS NULL OR u.schoolId = :schoolId) AND " +
            "u.role IN :roles AND " +
            "(:status IS NULL OR u.status = :status)")
    Page<User> findByConditionsWithRoles(@Param("keyword") String keyword,
                                         @Param("schoolId") Long schoolId,
                                         @Param("roles") Collection<User.UserRole> roles,
                                         @Param("status") User.UserStatus status,
                                         Pageable pageable);

    @Query("SELECT u FROM User u WHERE u.isDeleted = false AND " +
            "(:keyword IS NULL OR u.username LIKE %:keyword% OR u.nickname LIKE %:keyword% OR u.email LIKE %:keyword% OR u.identityNumber LIKE %:keyword%) AND " +
            "(:schoolId IS NULL OR u.schoolId = :schoolId) AND u.schoolId IN :schoolIds AND " +
            "u.role IN :roles AND (:status IS NULL OR u.status = :status)")
    Page<User> findByConditionsWithRolesAndSchoolIds(@Param("keyword") String keyword,
                                                     @Param("schoolId") Long schoolId,
                                                     @Param("roles") Collection<User.UserRole> roles,
                                                     @Param("status") User.UserStatus status,
                                                     @Param("schoolIds") Collection<Long> schoolIds,
                                                     Pageable pageable);

    @Query("SELECT u FROM User u WHERE u.isDeleted = false AND " +
            "(:keyword IS NULL OR u.username LIKE %:keyword% OR u.nickname LIKE %:keyword% OR u.email LIKE %:keyword% OR u.identityNumber LIKE %:keyword%) AND " +
            "(:schoolId IS NULL OR u.schoolId = :schoolId) AND u.schoolId IN :schoolIds AND " +
            "(:role IS NULL OR u.role = :role) AND (:status IS NULL OR u.status = :status)")
    Page<User> findByConditionsAndSchoolIds(@Param("keyword") String keyword,
                                            @Param("schoolId") Long schoolId,
                                            @Param("role") User.UserRole role,
                                            @Param("status") User.UserStatus status,
                                            @Param("schoolIds") Collection<Long> schoolIds,
                                            Pageable pageable);

    @Modifying
    @Query("UPDATE User u SET u.status = :status WHERE u.id IN :ids")
    int updateStatusByIds(@Param("ids") List<Long> ids, @Param("status") User.UserStatus status);

    @Modifying
    @Query("UPDATE User u SET u.lastLoginTime = :time, u.lastLoginIp = :ip WHERE u.id = :id")
    void updateLastLogin(@Param("id") Long id,
                         @Param("time") LocalDateTime time,
                         @Param("ip") String ip);

    @Query("SELECT COUNT(u) FROM User u WHERE u.isDeleted = false AND u.status = :status")
    long countByStatus(@Param("status") User.UserStatus status);

    @Query("SELECT u.role, COUNT(u) FROM User u WHERE u.isDeleted = false GROUP BY u.role")
    List<Object[]> countByRole();

    @Query("SELECT COUNT(u) FROM User u WHERE u.isDeleted = false AND u.status = :status AND u.role = :role")
    long countByStatusAndRole(@Param("status") User.UserStatus status, @Param("role") User.UserRole role);

    List<User> findByStatusAndIsDeletedFalse(User.UserStatus status);

    long countByIdentityStatusAndIsDeletedFalse(User.IdentityStatus identityStatus);

    List<User> findByRoleInAndIsDeletedFalse(Collection<User.UserRole> roles);

    @Modifying
    @Query("UPDATE User u SET u.isDeleted = true WHERE u.id = :id")
    void softDelete(@Param("id") Long id);

    // ========== 新增方法：用于学生管理 ==========

    /**
     * 根据角色和状态查询用户（分页）
     */
    @Query("SELECT u FROM User u WHERE u.isDeleted = false AND u.role = :role AND u.status = :status")
    Page<User> findByRoleAndStatus(@Param("role") User.UserRole role,
                                   @Param("status") User.UserStatus status,
                                   Pageable pageable);

    /**
     * 根据角色、状态和关键字查询用户（按用户名或昵称）
     */
    @Query("SELECT u FROM User u WHERE u.isDeleted = false AND u.role = :role AND u.status = :status " +
            "AND (u.username LIKE %:keyword% OR u.nickname LIKE %:keyword%)")
    Page<User> findByRoleAndStatusAndUsernameContainingOrNicknameContaining(
            @Param("role") User.UserRole role,
            @Param("status") User.UserStatus status,
            @Param("keyword") String keyword,
            Pageable pageable);

    /**
     * 查询不在指定ID列表中的可用学生（所有活跃学生中未在当前教师班级的）
     */
    @Query("SELECT u FROM User u WHERE u.isDeleted = false AND u.id NOT IN :excludeIds " +
            "AND u.role = :role AND u.status = :status")
    Page<User> findAvailableStudentsExcludingIds(
            @Param("excludeIds") List<Long> excludeIds,
            @Param("role") User.UserRole role,
            @Param("status") User.UserStatus status,
            Pageable pageable);

    /**
     * 查询不在指定ID列表中的可用学生（带关键字搜索）
     */
    @Query("SELECT u FROM User u WHERE u.isDeleted = false AND u.id NOT IN :excludeIds " +
            "AND u.role = :role AND u.status = :status " +
            "AND (u.username LIKE %:keyword% OR u.nickname LIKE %:keyword%)")
    Page<User> findAvailableStudentsExcludingIdsWithKeyword(
            @Param("excludeIds") List<Long> excludeIds,
            @Param("role") User.UserRole role,
            @Param("status") User.UserStatus status,
            @Param("keyword") String keyword,
            Pageable pageable);

    /**
     * 获取所有活跃学生（不分页，用于统计）
     */
    @Query("SELECT u FROM User u WHERE u.isDeleted = false AND u.role = :role AND u.status = :status")
    List<User> findAllByRoleAndStatus(@Param("role") User.UserRole role,
                                      @Param("status") User.UserStatus status);

    List<User> findByAcademicClassIdAndRoleAndStatus(Long academicClassId, User.UserRole role, User.UserStatus status);

    List<User> findByAcademicClassIdAndIsDeletedFalse(Long academicClassId);

    long countByAcademicClassIdAndIsDeletedFalse(Long academicClassId);
}
