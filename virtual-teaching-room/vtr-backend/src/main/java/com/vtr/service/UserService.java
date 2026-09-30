package com.vtr.service;

import com.vtr.common.PageResult;
import com.vtr.dto.*;
import com.vtr.entity.User;
import com.vtr.vo.LoginVO;
import com.vtr.vo.UserStatisticsVO;
import com.vtr.vo.UserVO;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;

public interface UserService {

    Long register(UserRegisterDTO dto);

    LoginVO login(LoginDTO dto, HttpServletRequest request);

    void sendVerificationCode(SendVerificationCodeDTO dto, HttpServletRequest request);

    LoginVO codeLogin(CodeLoginDTO dto, HttpServletRequest request);

    void resetPasswordByCode(ResetPasswordByCodeDTO dto);

    void bindIdentity(Long userId, IdentityBindingDTO dto);

    void reviewIdentityBinding(Long userId, boolean approved, String reason);

    void logout(Long userId);

    void changePassword(Long userId, PasswordChangeDTO dto);

    UserVO getCurrentUser(Long userId);

    UserVO getUserById(Long id);

    PageResult<UserVO> queryUsers(UserQueryDTO query);

    void updateUser(Long userId, UserUpdateDTO dto);

    void updateUser(Long userId, AdminUserUpdateDTO dto);

    void updateProfile(Long userId, UserProfileDTO dto);

    void batchReview(BatchReviewDTO dto, Long operatorId);

    void updateStatus(Long userId, User.UserStatus status, String reason);

    void assignRole(Long userId, User.UserRole role, Long operatorId);

    String resetPassword(Long userId, Long operatorId);

    void deleteUser(Long userId, Long operatorId);

    UserStatisticsVO getStatistics();

    User getEntityById(Long id);

    boolean existsByUsername(String username);

    String uploadAvatar(Long userId, MultipartFile file);

    UserVO getUserByUsername(String username);

    // ✅ 新增：根据用户名获取 User 实体
    User getEntityByUsername(String username);
}
