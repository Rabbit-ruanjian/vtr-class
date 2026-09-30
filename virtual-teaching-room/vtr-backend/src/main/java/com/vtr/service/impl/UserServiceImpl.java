package com.vtr.service.impl;

import com.vtr.common.PageResult;
import com.vtr.common.exception.BusinessException;
import com.vtr.common.exception.NotFoundException;
import com.vtr.dto.*;
import com.vtr.entity.Classroom;
import com.vtr.entity.AcademicClass;
import com.vtr.entity.AcademicClassStudentRoster;
import com.vtr.entity.SchoolTeacherRoster;
import com.vtr.entity.User;
import com.vtr.repository.AcademicClassRepository;
import com.vtr.repository.AssignmentRepository;
import com.vtr.repository.AcademicClassStudentRosterRepository;
import com.vtr.repository.SchoolTeacherRosterRepository;
import com.vtr.repository.AdminSchoolAccessRepository;
import com.vtr.repository.ClassroomRepository;
import com.vtr.repository.ClassroomStudentRelationRepository;
import com.vtr.repository.SchoolRepository;
import com.vtr.repository.UserRepository;
import com.vtr.security.JwtTokenProvider;
import com.vtr.service.UserService;
import com.vtr.service.AdminScopeService;
import com.vtr.service.VerificationCodeSender;
import com.vtr.vo.LoginVO;
import com.vtr.vo.UserStatisticsVO;
import com.vtr.vo.UserVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.security.SecureRandom;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final RedisTemplate<String, Object> redisTemplate;
    private final AcademicClassRepository academicClassRepository;
    private final ClassroomRepository classroomRepository;
    private final ClassroomStudentRelationRepository classroomStudentRelationRepository;
    private final AssignmentRepository assignmentRepository;
    private final VerificationCodeSender verificationCodeSender;
    private final SchoolRepository schoolRepository;
    private final AcademicClassStudentRosterRepository studentRosterRepository;
    private final SchoolTeacherRosterRepository teacherRosterRepository;
    private final AdminSchoolAccessRepository adminSchoolAccessRepository;
    private final AdminScopeService adminScopeService;

    @Value("${upload.path:uploads}")
    private String uploadPath;

    private static final String USER_CACHE_PREFIX = "user:";
    private static final long CACHE_TTL = 24; // hours
    private static final String TEMP_PASSWORD_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final String CODE_KEY_PREFIX = "auth:verification:";
    private static final String CODE_LIMIT_PREFIX = "auth:verification:limit:";
    private static final long CODE_TTL_SECONDS = 5 * 60;
    private static final long CODE_COOLDOWN_SECONDS = 60;
    private static final int MAX_CODE_ATTEMPTS = 5;

    @Override
    @Transactional
    public Long register(UserRegisterDTO dto) {
        String email = StringUtils.hasText(dto.getEmail()) ? dto.getEmail().trim() : null;
        if (email != null) email = normalizeTarget("EMAIL", email);
        String phone = StringUtils.hasText(dto.getPhone()) ? normalizeTarget("PHONE", dto.getPhone()) : null;
        if (email == null && phone == null) {
            throw new BusinessException("请填写邮箱或手机号");
        }
        if (email != null && phone != null) {
            throw new BusinessException("邮箱和手机号只能选择一种注册方式");
        }
        String channel = email != null ? "EMAIL" : "PHONE";
        String target = email != null ? email : phone;

        User.UserRole role;
        try {
            role = User.UserRole.valueOf(StringUtils.hasText(dto.getRole())
                    ? dto.getRole().trim().toUpperCase(Locale.ROOT) : "STUDENT");
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new BusinessException("Unsupported registration role");
        }

        // Public registration must not be usable to create an administrative account.
        if (role != User.UserRole.STUDENT && role != User.UserRole.TEACHER) {
            throw new BusinessException("Only student and teacher accounts can be registered");
        }

        User user = email != null
                ? userRepository.findByEmail(email).orElse(null)
                : userRepository.findByPhone(phone).orElse(null);
        if (user != null && !Boolean.TRUE.equals(user.getIsDeleted())
                && user.getStatus() != User.UserStatus.DELETED) {
            throw new BusinessException("邮箱或手机号已被使用，请直接登录或找回密码");
        }
        if (user != null && (user.getRole() == User.UserRole.ADMIN || user.getRole() == User.UserRole.SUPER_ADMIN)) {
            throw new BusinessException("该账号不能通过公开注册恢复");
        }

        if (user == null) {
            user = new User();
            String username = StringUtils.hasText(dto.getUsername())
                    ? dto.getUsername().trim() : createInternalUsername();
            if (userRepository.existsByUsername(username)) {
                throw new BusinessException("用户名已存在");
            }
            user.setUsername(username);
        }

        // 同一联系方式对应的已删除账号允许重新注册，恢复原账号而不是新建一条冲突记录。
        user.setIsDeleted(false);
        if (email != null) user.setEmail(email);
        if (phone != null) user.setPhone(phone);
        user.setPassword(passwordEncoder.encode(dto.getPassword()));
        user.setRole(role);
        user.setStatus(User.UserStatus.ACTIVE);
        user.setAvatar(StringUtils.hasText(dto.getAvatar()) ? dto.getAvatar() : "/uploads/default-avatar.png");

        Long schoolId = dto.getSchoolId();
        com.vtr.entity.School school = null;
        if (schoolId != null) {
            school = schoolRepository.findById(schoolId)
                    .orElseThrow(() -> new BusinessException("学校不存在"));
            if (!"ACTIVE".equals(school.getStatus())) throw new BusinessException("学校已停用");
        }
        user.setSchoolId(schoolId);

        // 未选择学校时，只创建未绑定账号，不接受用户自行填写身份信息。
        if (schoolId == null) {
            if (role == User.UserRole.TEACHER) {
                throw new BusinessException("教师账号必须选择学校并填写学校预置工号");
            }
            user.setUnitName(null);
            user.setIdentityType(null);
            user.setIdentityNumber(null);
            user.setIdentityStatus(User.IdentityStatus.UNBOUND);
            user.setAcademicClassId(null);
            verifyAndConsumeCode("REGISTER", channel, target, dto.getCode());
            markContactVerified(user, channel);
            userRepository.saveAndFlush(user);
            safeDeleteUserCache(user.getId());
            return user.getId();
        }

        String unitName = StringUtils.hasText(dto.getUnitName()) ? dto.getUnitName().trim() : null;
        String identityNumber = StringUtils.hasText(dto.getIdentityNumber()) ? dto.getIdentityNumber().trim() : null;
        if (role == User.UserRole.STUDENT) {
            if (!StringUtils.hasText(identityNumber)) {
                throw new BusinessException("学生注册必须填写学号");
            }
            unitName = school.getName();
        }
        if (unitName == null && identityNumber != null) unitName = school.getName();
        if ((unitName == null) != (identityNumber == null)) {
            throw new BusinessException("单位和学号/工号必须同时填写");
        }
        AcademicClassStudentRoster matchedRoster = null;
        SchoolTeacherRoster matchedTeacherRoster = null;
        if (unitName != null) {
            User.IdentityType identityType;
            try {
                identityType = User.IdentityType.valueOf(StringUtils.hasText(dto.getIdentityType())
                        ? dto.getIdentityType().trim().toUpperCase(Locale.ROOT)
                        : (role == User.UserRole.TEACHER ? "TEACHER" : "STUDENT"));
            } catch (IllegalArgumentException e) {
                throw new BusinessException("身份类型只能是 STUDENT 或 TEACHER");
            }
            if (role == User.UserRole.STUDENT && identityType != User.IdentityType.STUDENT) {
                throw new BusinessException("学生账号只能绑定学号");
            }
            User existingIdentity = userRepository.findBySchoolIdAndIdentityTypeAndIdentityNumber(
                    schoolId, identityType, identityNumber).orElse(null);
            if (existingIdentity != null && !existingIdentity.getId().equals(user.getId())) {
                throw new BusinessException("该单位的学号或工号已被其他账号绑定");
            }
            if (role == User.UserRole.STUDENT) {
                matchedRoster = studentRosterRepository.findBySchoolIdAndStudentNumber(schoolId, identityNumber)
                        .orElseThrow(() -> new BusinessException("该学号不在所选学校的行政班名单中，请联系管理员导入名单"));
                if (!"ACTIVE".equals(matchedRoster.getStatus())) {
                    throw new BusinessException("该学号在行政班名单中已停用，请联系管理员");
                }
                if (matchedRoster.getUserId() != null && !matchedRoster.getUserId().equals(user.getId())) {
                    throw new BusinessException("该学号已经绑定其他账号");
                }
                if (StringUtils.hasText(matchedRoster.getName())) user.setNickname(matchedRoster.getName());
                user.setAcademicClassId(matchedRoster.getAcademicClassId());
                user.setIdentityStatus(User.IdentityStatus.VERIFIED);
            } else {
                SchoolTeacherRoster teacherRoster = teacherRosterRepository
                        .findBySchoolIdAndEmployeeNumberAndStatus(schoolId, identityNumber, "ACTIVE")
                        .orElseThrow(() -> new BusinessException("该工号不在所选学校的教师名单中，请联系学校管理员录入"));
                if (teacherRoster.getUserId() != null && !teacherRoster.getUserId().equals(user.getId())) {
                    throw new BusinessException("该工号已经绑定其他账号");
                }
                if (StringUtils.hasText(teacherRoster.getName())) user.setNickname(teacherRoster.getName());
                if (StringUtils.hasText(teacherRoster.getDepartment())) unitName = teacherRoster.getDepartment();
                user.setIdentityStatus(User.IdentityStatus.VERIFIED);
                matchedTeacherRoster = teacherRoster;
            }
            user.setUnitName(unitName);
            user.setIdentityType(identityType);
            user.setIdentityNumber(identityNumber);
        } else {
            user.setUnitName(null);
            user.setIdentityType(null);
            user.setIdentityNumber(null);
            user.setIdentityStatus(User.IdentityStatus.UNBOUND);
        }
        verifyAndConsumeCode("REGISTER", channel, target, dto.getCode());
        markContactVerified(user, channel);
        try {
            userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException("邮箱、手机号或用户名已被使用");
        }
        if (matchedRoster != null) {
            matchedRoster.setUserId(user.getId());
            matchedRoster.setStatus("ACTIVE");
            studentRosterRepository.save(matchedRoster);
        }
        if (matchedTeacherRoster != null) {
            matchedTeacherRoster.setUserId(user.getId());
            teacherRosterRepository.save(matchedTeacherRoster);
        }
        safeDeleteUserCache(user.getId());
        log.info("用户注册/恢复成功: userId={}, username={}", user.getId(), user.getUsername());
        return user.getId();
    }

    @Override
    public LoginVO login(LoginDTO dto, HttpServletRequest request) {
        log.info("=== 登录请求开始 ===");
        String account = dto.getUsername() == null ? "" : dto.getUsername().trim();
        log.info("登录账号: {}", account);

        User user = findByLoginAccount(account, dto.getSchoolCode())
                .orElseThrow(() -> {
                    log.warn("账号不存在: {}", account);
                    return new BusinessException("邮箱、手机号或密码错误");
                });

        log.info("找到用户: id={}, username={}", user.getId(), user.getUsername());

        boolean passwordMatches = passwordEncoder.matches(dto.getPassword(), user.getPassword());
        log.info("密码验证结果: {}", passwordMatches);

        if (!passwordMatches) {
            log.warn("密码错误: account={}", account);
            throw new BusinessException("邮箱、手机号或密码错误");
        }

        log.info("密码验证通过");

        if (user.getStatus() == User.UserStatus.PENDING
                && (user.getRole() == User.UserRole.STUDENT || user.getRole() == User.UserRole.TEACHER)) {
            // Preserve access for accounts registered before self-service activation.
            user.setStatus(User.UserStatus.ACTIVE);
            userRepository.save(user);
            safeDeleteUserCache(user.getId());
            log.info("Auto-activated pending self-service account: {}", user.getUsername());
        }
        if (user.getStatus() == User.UserStatus.PENDING) {
            log.warn("账户待审核: {}", user.getUsername());
            throw new BusinessException("账户待审核，请联系管理员");
        }
        if (user.getStatus() == User.UserStatus.BANNED) {
            log.warn("账户已被封禁: {}", user.getUsername());
            throw new BusinessException("账户已被封禁");
        }
        if (user.getStatus() == User.UserStatus.SUSPENDED) {
            log.warn("账户已被冻结: {}", user.getUsername());
            throw new BusinessException("账户已被冻结");
        }
        if (user.getStatus() == User.UserStatus.DELETED) {
            log.warn("账户已被删除: {}", user.getUsername());
            throw new BusinessException("账号已被删除，请联系管理员");
        }

        String ip = getClientIp(request);
        user.updateLastLogin(ip);
        userRepository.save(user);

        List<String> roles = List.of(user.getRole().name());
        String token = jwtTokenProvider.generateToken(user.getId(), user.getUsername(), roles, user.getAvatar());

        safeDeleteUserCache(user.getId());
        cacheUser(user);

        LoginVO vo = new LoginVO();
        vo.setToken(token);
        vo.setTokenType("Bearer");
        vo.setExpiresIn((long) jwtTokenProvider.getExpirationTime());
        vo.setUserInfo(convertToVO(user));

        log.info("用户登录成功: {}, IP: {}, 角色: {}", user.getUsername(), ip, user.getRole().name());
        log.info("=== 登录请求结束 ===");

        return vo;
    }

    @Override
    public void sendVerificationCode(SendVerificationCodeDTO dto, HttpServletRequest request) {
        String channel = normalizeChannel(dto.getChannel());
        String target = normalizeTarget(channel, dto.getTarget());
        String scene = StringUtils.hasText(dto.getScene()) ? dto.getScene().trim().toUpperCase() : "LOGIN";
        if (!"REGISTER".equals(scene) && !"RESET_PASSWORD".equals(scene)) {
            throw new BusinessException("验证码场景无效");
        }
        String codeKey = CODE_KEY_PREFIX + scene + ":" + channel + ":" + target;
        String cooldownKey = codeKey + ":cooldown";
        String ipLimitKey = CODE_LIMIT_PREFIX + getClientIp(request);

        if (Boolean.TRUE.equals(redisTemplate.hasKey(cooldownKey))) {
            throw new BusinessException("验证码发送过于频繁，请稍后再试");
        }
        Long ipCount = redisTemplate.opsForValue().increment(ipLimitKey);
        if (ipCount != null && ipCount == 1) {
            redisTemplate.expire(ipLimitKey, 1, TimeUnit.HOURS);
        }
        if (ipCount != null && ipCount > 30) {
            throw new BusinessException("请求过于频繁，请稍后再试");
        }

        String code = String.format("%06d", SECURE_RANDOM.nextInt(1_000_000));
        redisTemplate.opsForValue().set(codeKey, passwordEncoder.encode(code), CODE_TTL_SECONDS, TimeUnit.SECONDS);
        redisTemplate.opsForValue().set(cooldownKey, "1", CODE_COOLDOWN_SECONDS, TimeUnit.SECONDS);
        try {
            if ("PHONE".equals(channel)) {
                verificationCodeSender.sendSms(target, code);
            } else {
                verificationCodeSender.sendEmail(target, code);
            }
        } catch (RuntimeException e) {
            redisTemplate.delete(codeKey);
            redisTemplate.delete(cooldownKey);
            throw e;
        }
    }

    @Override
    @Transactional
    public LoginVO codeLogin(CodeLoginDTO dto, HttpServletRequest request) {
        String channel = normalizeChannel(dto.getChannel());
        String target = normalizeTarget(channel, dto.getTarget());
        String codeKey = CODE_KEY_PREFIX + "LOGIN:" + channel + ":" + target;
        String attemptsKey = codeKey + ":attempts";
        Object savedHash = redisTemplate.opsForValue().get(codeKey);
        if (savedHash == null || !passwordEncoder.matches(dto.getCode().trim(), String.valueOf(savedHash))) {
            Long attempts = redisTemplate.opsForValue().increment(attemptsKey);
            if (attempts != null && attempts == 1) {
                redisTemplate.expire(attemptsKey, CODE_TTL_SECONDS, TimeUnit.SECONDS);
            }
            if (attempts != null && attempts >= MAX_CODE_ATTEMPTS) {
                redisTemplate.delete(codeKey);
                redisTemplate.delete(attemptsKey);
            }
            throw new BusinessException("验证码错误或已过期");
        }
        redisTemplate.delete(codeKey);
        redisTemplate.delete(attemptsKey);

        User user = "PHONE".equals(channel)
                ? userRepository.findByPhone(target).orElse(null)
                : userRepository.findByEmail(target).orElse(null);
        if (user == null) {
            user = new User();
            user.setUsername(createInternalUsername());
            // 注册页会提交用户设置的密码；普通验证码登录未提交密码时仍使用随机初始密码。
            String initialPassword = StringUtils.hasText(dto.getPassword())
                    ? dto.getPassword().trim()
                    : UUID.randomUUID().toString();
            user.setPassword(passwordEncoder.encode(initialPassword));
            user.setRole(User.UserRole.STUDENT);
            user.setStatus(User.UserStatus.ACTIVE);
            user.setIdentityStatus(User.IdentityStatus.UNBOUND);
            user.setAvatar("/uploads/default-avatar.png");
            if ("PHONE".equals(channel)) {
                user.setPhone(target);
                user.setPhoneVerifiedAt(java.time.LocalDateTime.now());
            } else {
                user.setEmail(target);
                user.setEmailVerifiedAt(java.time.LocalDateTime.now());
            }
            try {
                user = userRepository.saveAndFlush(user);
            } catch (DataIntegrityViolationException e) {
                throw new BusinessException("该联系方式已注册，请重新登录");
            }
        } else {
            if (user.getStatus() == User.UserStatus.BANNED) throw new BusinessException("账户已被封禁");
            if (user.getStatus() == User.UserStatus.SUSPENDED) throw new BusinessException("账户已被冻结");
            if (user.getStatus() == User.UserStatus.DELETED) throw new BusinessException("账号已被删除");
            if ("PHONE".equals(channel)) user.setPhoneVerifiedAt(java.time.LocalDateTime.now());
            else user.setEmailVerifiedAt(java.time.LocalDateTime.now());
        }

        String ip = getClientIp(request);
        user.updateLastLogin(ip);
        userRepository.save(user);
        return createLoginVO(user);
    }

    @Override
    @Transactional
    public void resetPasswordByCode(ResetPasswordByCodeDTO dto) {
        String channel = normalizeChannel(dto.getChannel());
        String target = normalizeTarget(channel, dto.getTarget());
        if (!Objects.equals(dto.getNewPassword(), dto.getConfirmPassword())) {
            throw new BusinessException("两次输入的密码不一致");
        }

        String codeKey = CODE_KEY_PREFIX + "RESET_PASSWORD:" + channel + ":" + target;
        String attemptsKey = codeKey + ":attempts";
        Object savedHash = redisTemplate.opsForValue().get(codeKey);
        if (savedHash == null || !passwordEncoder.matches(dto.getCode().trim(), String.valueOf(savedHash))) {
            Long attempts = redisTemplate.opsForValue().increment(attemptsKey);
            if (attempts != null && attempts == 1) {
                redisTemplate.expire(attemptsKey, CODE_TTL_SECONDS, TimeUnit.SECONDS);
            }
            if (attempts != null && attempts >= MAX_CODE_ATTEMPTS) {
                redisTemplate.delete(codeKey);
                redisTemplate.delete(attemptsKey);
            }
            throw new BusinessException("验证码错误或已过期");
        }

        // 验证码一次性使用，避免同一验证码被重复提交。
        redisTemplate.delete(codeKey);
        redisTemplate.delete(attemptsKey);

        User user = "PHONE".equals(channel)
                ? userRepository.findByPhone(target).orElse(null)
                : userRepository.findByEmail(target).orElse(null);
        if (user == null || Boolean.TRUE.equals(user.getIsDeleted()) || user.getStatus() == User.UserStatus.DELETED) {
            throw new BusinessException("该邮箱或手机号没有可用账号");
        }
        user.setPassword(passwordEncoder.encode(dto.getNewPassword().trim()));
        userRepository.save(user);
        safeDeleteUserCache(user.getId());
        log.info("用户通过验证码重置密码成功: userId={}", user.getId());
    }

    @Override
    @Transactional
    public void bindIdentity(Long userId, IdentityBindingDTO dto) {
        User user = getEntityById(userId);
        if (dto.getSchoolId() == null) {
            detachPreviousRoster(userId, null);
            user.setSchoolId(null);
            user.setUnitName(null);
            user.setIdentityType(null);
            user.setIdentityNumber(null);
            user.setIdentityStatus(User.IdentityStatus.UNBOUND);
            user.setAcademicClassId(null);
            userRepository.save(user);
            safeDeleteUserCache(userId);
            return;
        }
        Long schoolId = dto.getSchoolId();
        com.vtr.entity.School school = schoolRepository.findById(schoolId)
                .orElseThrow(() -> new BusinessException("学校不存在"));
        if (!"ACTIVE".equals(school.getStatus())) throw new BusinessException("学校已停用");
        if (!user.isStudent() && !user.isTeacher()) {
            throw new BusinessException("当前账号类型不支持绑定学生或教师身份");
        }
        User.IdentityType identityType;
        try {
            identityType = User.IdentityType.valueOf(StringUtils.hasText(dto.getIdentityType())
                    ? dto.getIdentityType().trim().toUpperCase(Locale.ROOT)
                    : (user.isTeacher() ? "TEACHER" : "STUDENT"));
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new BusinessException("身份类型只能是 STUDENT 或 TEACHER");
        }
        if (user.isStudent() && identityType != User.IdentityType.STUDENT) {
            throw new BusinessException("学生账号只能绑定学号");
        }
        if (user.isTeacher() && identityType != User.IdentityType.TEACHER) {
            throw new BusinessException("教师账号只能绑定工号");
        }
        String unitName = StringUtils.hasText(dto.getUnitName()) ? dto.getUnitName().trim() : school.getName();
        String identityNumber = StringUtils.hasText(dto.getIdentityNumber()) ? dto.getIdentityNumber().trim() : null;
        if (!StringUtils.hasText(identityNumber)) throw new BusinessException("请填写学号或工号");
        User existing = userRepository.findBySchoolIdAndIdentityTypeAndIdentityNumber(
                schoolId, identityType, identityNumber).orElse(null);
        if (existing != null && !existing.getId().equals(userId)) {
            throw new BusinessException("该单位的学号或工号已被其他账号绑定");
        }
        AcademicClassStudentRoster matchedRoster = null;
        SchoolTeacherRoster matchedTeacherRoster = null;
        if (identityType == User.IdentityType.STUDENT) {
            matchedRoster = studentRosterRepository.findBySchoolIdAndStudentNumber(schoolId, identityNumber)
                    .orElseThrow(() -> new BusinessException("该学号不在所选学校的行政班名单中，请联系管理员导入名单"));
            if (matchedRoster.getUserId() != null && !matchedRoster.getUserId().equals(userId)) {
                throw new BusinessException("该学号已经绑定其他账号");
            }
            user.setAcademicClassId(matchedRoster.getAcademicClassId());
            user.setIdentityStatus(User.IdentityStatus.VERIFIED);
        } else {
            matchedTeacherRoster = teacherRosterRepository
                    .findBySchoolIdAndEmployeeNumberAndStatus(schoolId, identityNumber, "ACTIVE")
                    .orElseThrow(() -> new BusinessException("该工号不在所选学校的教师名单中，请先录入教师名单"));
            if (matchedTeacherRoster.getUserId() != null && !matchedTeacherRoster.getUserId().equals(userId)) {
                throw new BusinessException("该工号已经绑定其他账号");
            }
            user.setAcademicClassId(null);
            user.setIdentityStatus(User.IdentityStatus.VERIFIED);
        }
        if (matchedRoster != null) detachPreviousRoster(userId, matchedRoster.getId());
        if (matchedTeacherRoster != null) detachPreviousTeacherRoster(userId, matchedTeacherRoster.getId());
        user.setUnitName(unitName);
        user.setSchoolId(schoolId);
        user.setIdentityType(identityType);
        user.setIdentityNumber(identityNumber);
        userRepository.save(user);
        if (matchedRoster != null) {
            matchedRoster.setUserId(userId);
            matchedRoster.setStatus("ACTIVE");
            studentRosterRepository.save(matchedRoster);
        }
        if (matchedTeacherRoster != null) {
            matchedTeacherRoster.setUserId(userId);
            teacherRosterRepository.save(matchedTeacherRoster);
        }
        safeDeleteUserCache(userId);
    }

    @Override
    @Transactional
    public void reviewIdentityBinding(Long userId, boolean approved, String reason) {
        User user = getEntityById(userId);
        adminScopeService.requireUser(user);
        if (user.getIdentityType() == null || !StringUtils.hasText(user.getIdentityNumber())) {
            throw new BusinessException("该用户还没有提交身份绑定信息");
        }
        if (approved) {
            user.setRole(user.getIdentityType() == User.IdentityType.TEACHER
                    ? User.UserRole.TEACHER : User.UserRole.STUDENT);
            user.setIdentityStatus(User.IdentityStatus.VERIFIED);
            user.setStatus(User.UserStatus.ACTIVE);
        } else {
            user.setIdentityStatus(User.IdentityStatus.REJECTED);
        }
        userRepository.save(user);
        safeDeleteUserCache(userId);
        log.info("审核身份绑定: userId={}, approved={}, reason={}", userId, approved, reason);
    }

    private LoginVO createLoginVO(User user) {
        List<String> roles = List.of(user.getRole().name());
        LoginVO vo = new LoginVO();
        vo.setToken(jwtTokenProvider.generateToken(user.getId(), user.getUsername(), roles, user.getAvatar()));
        vo.setTokenType("Bearer");
        vo.setExpiresIn((long) jwtTokenProvider.getExpirationTime());
        vo.setUserInfo(convertToVO(user));
        safeDeleteUserCache(user.getId());
        cacheUser(user);
        return vo;
    }

    private String createInternalUsername() {
        String username;
        do {
            username = "u_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        } while (userRepository.existsByUsername(username));
        return username;
    }

    private Optional<User> findByLoginAccount(String account, String schoolCode) {
        if (!StringUtils.hasText(account)) {
            return Optional.empty();
        }
        String value = account.trim();
        if (value.contains("@")) {
            return userRepository.findByEmail(normalizeTarget("EMAIL", value));
        }
        String phone = value.replaceAll("[\\s\\-()]+", "");
        if (phone.matches("^\\+?[0-9]{6,20}$")) {
            return userRepository.findByPhone(phone);
        }
        if (StringUtils.hasText(schoolCode)) {
            Optional<com.vtr.entity.School> school = schoolRepository.findByCodeIgnoreCase(schoolCode.trim());
            if (school.isPresent()) {
                Optional<User> student = userRepository.findBySchoolIdAndIdentityTypeAndIdentityNumber(
                        school.get().getId(), User.IdentityType.STUDENT, value);
                if (student.isPresent()) return student;
                Optional<User> teacher = userRepository.findBySchoolIdAndIdentityTypeAndIdentityNumber(
                        school.get().getId(), User.IdentityType.TEACHER, value);
                if (teacher.isPresent()) return teacher;
            }
        }
        // 保留旧用户名登录兼容性。
        return userRepository.findByUsername(value);
    }

    private String normalizeChannel(String channel) {
        String value = channel == null ? "" : channel.trim().toUpperCase();
        if (!"PHONE".equals(value) && !"EMAIL".equals(value)) {
            throw new BusinessException("验证码渠道只能是 PHONE 或 EMAIL");
        }
        return value;
    }

    private String normalizeTarget(String channel, String target) {
        String value = target == null ? "" : target.trim();
        if ("EMAIL".equals(channel)) {
            if (!value.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
                throw new BusinessException("邮箱格式不正确");
            }
            return value.toLowerCase(Locale.ROOT);
        }
        String phone = value.replaceAll("[\\s\\-()]+", "");
        if (!phone.matches("^\\+?[0-9]{6,20}$")) {
            throw new BusinessException("手机号格式不正确");
        }
        return phone;
    }

    private void verifyAndConsumeCode(String scene, String channel, String target, String code) {
        String codeKey = CODE_KEY_PREFIX + scene + ":" + channel + ":" + target;
        String attemptsKey = codeKey + ":attempts";
        Object savedHash = redisTemplate.opsForValue().get(codeKey);
        if (!StringUtils.hasText(code) || savedHash == null
                || !passwordEncoder.matches(code.trim(), String.valueOf(savedHash))) {
            Long attempts = redisTemplate.opsForValue().increment(attemptsKey);
            if (attempts != null && attempts == 1) {
                redisTemplate.expire(attemptsKey, CODE_TTL_SECONDS, TimeUnit.SECONDS);
            }
            if (attempts != null && attempts >= MAX_CODE_ATTEMPTS) {
                redisTemplate.delete(codeKey);
                redisTemplate.delete(attemptsKey);
            }
            throw new BusinessException("验证码错误或已过期");
        }
        redisTemplate.delete(codeKey);
        redisTemplate.delete(attemptsKey);
    }

    private void markContactVerified(User user, String channel) {
        if ("EMAIL".equals(channel)) {
            user.setEmailVerifiedAt(java.time.LocalDateTime.now());
        } else {
            user.setPhoneVerifiedAt(java.time.LocalDateTime.now());
        }
    }

    private String trim(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    @Override
    public void logout(Long userId) {
        safeDeleteUserCache(userId);
        log.info("用户登出: {}", userId);
    }

    @Override
    @Transactional
    public void changePassword(Long userId, PasswordChangeDTO dto) {
        User user = getEntityById(userId);
        if (!passwordEncoder.matches(dto.getOldPassword(), user.getPassword())) {
            throw new BusinessException("原密码错误");
        }
        if (!dto.getNewPassword().equals(dto.getConfirmPassword())) {
            throw new BusinessException("两次输入的密码不一致");
        }
        user.setPassword(passwordEncoder.encode(dto.getNewPassword()));
        userRepository.save(user);
        safeDeleteUserCache(userId);
        log.info("用户修改密码: {}", user.getUsername());
    }

    @Override
    public UserVO getCurrentUser(Long userId) {
        try {
            if (userId == null) {
                throw new BusinessException("用户ID不能为空");
            }
            User user = getEntityById(userId);
            UserVO vo = convertToVO(user);
            log.info("获取当前用户信息: id={}, username={}, role={}", vo.getId(), vo.getUsername(), vo.getRole());
            return vo;
        } catch (NotFoundException e) {
            throw new BusinessException("用户不存在");
        } catch (Exception e) {
            log.error("获取当前用户信息失败: userId={}", userId, e);
            throw new BusinessException("获取用户信息失败: " + e.getMessage());
        }
    }

    @Override
    public UserVO getUserById(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("用户", id));
        adminScopeService.requireUser(user);
        return convertToVO(user);
    }

    @Override
    public PageResult<UserVO> queryUsers(UserQueryDTO query) {
        Sort sort = Sort.by("createdAt").descending();
        if ("asc".equals(query.getSortOrder())) {
            sort = Sort.by("createdAt").ascending();
        }
        PageRequest pageRequest = PageRequest.of(query.getPage() - 1, query.getSize(), sort);
        User.UserRole role = null;
        User.UserStatus status = null;
        if (StringUtils.hasText(query.getRole())) {
            role = User.UserRole.valueOf(query.getRole());
        }
        if (StringUtils.hasText(query.getStatus())) {
            status = User.UserStatus.valueOf(query.getStatus());
        }
        User current = adminScopeService.currentUser();
        java.util.Set<Long> allowedSchoolIds = adminScopeService.allowedSchoolIds(current);
        if (query.getSchoolId() != null) adminScopeService.requireSchool(query.getSchoolId());
        if (StringUtils.hasText(query.getIdentityStatus())) {
            User.IdentityStatus identityStatus = User.IdentityStatus.valueOf(query.getIdentityStatus());
            if (allowedSchoolIds != null && allowedSchoolIds.isEmpty()) {
                return PageResult.of(List.of(), 0L, query.getPage(), query.getSize());
            }
            Page<User> identityPage = allowedSchoolIds == null
                    ? userRepository.findIdentityReviews(query.getKeyword(), query.getSchoolId(), role, identityStatus, pageRequest)
                    : userRepository.findIdentityReviewsInSchools(query.getKeyword(), query.getSchoolId(), role, identityStatus, allowedSchoolIds, pageRequest);
            List<UserVO> identityList = identityPage.getContent().stream().map(this::convertToVO).collect(Collectors.toList());
            return PageResult.of(identityList, identityPage.getTotalElements(), query.getPage(), query.getSize());
        }
        if (allowedSchoolIds != null && allowedSchoolIds.isEmpty()) {
            if (!adminScopeService.isUnboundUserManager(current)) {
                return PageResult.of(List.of(), 0L, query.getPage(), query.getSize());
            }
            Page<User> unboundPage = userRepository.findUnboundByConditions(
                    query.getKeyword(), role, status, pageRequest);
            List<UserVO> unboundList = unboundPage.getContent().stream()
                    .map(this::convertToVO).collect(Collectors.toList());
            return PageResult.of(unboundList, unboundPage.getTotalElements(), query.getPage(), query.getSize());
        }
        Page<User> page = role == User.UserRole.ADMIN
                ? (allowedSchoolIds == null ? userRepository.findByConditionsWithRoles(
                query.getKeyword(),
                query.getSchoolId(),
                Arrays.asList(User.UserRole.ADMIN, User.UserRole.SUPER_ADMIN),
                status,
                pageRequest) : userRepository.findByConditionsWithRolesAndSchoolIds(
                query.getKeyword(), query.getSchoolId(), Arrays.asList(User.UserRole.ADMIN, User.UserRole.SUPER_ADMIN), status, allowedSchoolIds, pageRequest))
                : (allowedSchoolIds == null ? userRepository.findByConditions(query.getKeyword(), query.getSchoolId(), role, status, pageRequest)
                : userRepository.findByConditionsAndSchoolIds(query.getKeyword(), query.getSchoolId(), role, status, allowedSchoolIds, pageRequest));
        List<UserVO> list = page.getContent().stream().map(this::convertToVO).collect(Collectors.toList());
        return PageResult.of(list, page.getTotalElements(), query.getPage(), query.getSize());
    }

    @Override
    @Transactional
    public void updateUser(Long userId, UserUpdateDTO dto) {
        User user = getEntityById(userId);
        if (StringUtils.hasText(dto.getEmail()) && !dto.getEmail().equals(user.getEmail())) {
            if (userRepository.existsByEmail(dto.getEmail())) {
                throw new BusinessException("邮箱已被使用");
            }
        }
        BeanUtils.copyProperties(dto, user, "username", "password");
        userRepository.save(user);
        cacheUser(user);
    }

    @Override
    @Transactional
    public void updateUser(Long userId, AdminUserUpdateDTO dto) {
        User user = getEntityById(userId);
        adminScopeService.requireUser(user);

        String email = StringUtils.hasText(dto.getEmail()) ? normalizeTarget("EMAIL", dto.getEmail()) : null;
        if (!Objects.equals(email, user.getEmail()) && email != null && userRepository.existsByEmail(email)) {
            throw new BusinessException("邮箱已被使用");
        }
        String phone = StringUtils.hasText(dto.getPhone()) ? normalizeTarget("PHONE", dto.getPhone()) : null;
        if (!Objects.equals(phone, user.getPhone()) && phone != null && userRepository.existsByPhone(phone)) {
            throw new BusinessException("手机号已被使用");
        }

        user.setNickname(trim(dto.getNickname()));
        user.setEmail(email);
        user.setPhone(phone);

        if (user.isAdmin()) {
            // 普通管理员只负责一所学校；SUPER_ADMIN 作为平台账号不绑定学校。
            if (user.getRole() == User.UserRole.SUPER_ADMIN) {
                user.setSchoolId(null);
                adminSchoolAccessRepository.deleteByAdminUserId(userId);
                user.setAdminScopeConfigured(true);
                user.setAdminScopeAll(true);
                user.setAdminScopeUnbound(false);
            } else if (Boolean.TRUE.equals(user.getAdminScopeUnbound())) {
                user.setSchoolId(null);
                adminSchoolAccessRepository.deleteByAdminUserId(userId);
                user.setAdminScopeConfigured(true);
                user.setAdminScopeAll(false);
                user.setAdminScopeUnbound(true);
            } else {
                Long schoolId = dto.getSchoolId();
                if (schoolId == null) throw new BusinessException("管理员必须绑定一所学校");
                com.vtr.entity.School school = schoolRepository.findById(schoolId)
                        .orElseThrow(() -> new BusinessException("学校不存在"));
                if (!"ACTIVE".equals(school.getStatus())) throw new BusinessException("学校已停用");
                user.setSchoolId(schoolId);
                user.setAdminScopeConfigured(true);
                user.setAdminScopeAll(false);
                adminSchoolAccessRepository.deleteByAdminUserId(userId);
                adminSchoolAccessRepository.save(com.vtr.entity.AdminSchoolAccess.builder()
                        .adminUserId(userId).schoolId(schoolId).status("ACTIVE").build());
            }
            user.setUnitName(null);
            user.setIdentityType(null);
            user.setIdentityNumber(null);
            user.setIdentityStatus(User.IdentityStatus.UNBOUND);
            user.setAcademicClassId(null);
        } else {
            Long schoolId = dto.getSchoolId();
            if (schoolId == null) {
                detachPreviousRoster(userId, null);
                user.setSchoolId(null);
                user.setUnitName(null);
                user.setIdentityType(null);
                user.setIdentityNumber(null);
                user.setIdentityStatus(User.IdentityStatus.UNBOUND);
                user.setAcademicClassId(null);
                userRepository.save(user);
                safeDeleteUserCache(userId);
                return;
            }
            com.vtr.entity.School school = schoolRepository.findById(schoolId)
                    .orElseThrow(() -> new BusinessException("学校不存在"));
            if (!"ACTIVE".equals(school.getStatus())) throw new BusinessException("学校已停用");

            User.IdentityType identityType;
            try {
                identityType = User.IdentityType.valueOf(StringUtils.hasText(dto.getIdentityType())
                        ? dto.getIdentityType().trim().toUpperCase(Locale.ROOT)
                        : (user.isStudent() ? "STUDENT" : "TEACHER"));
            } catch (IllegalArgumentException e) {
                throw new BusinessException("身份类型只能是 STUDENT 或 TEACHER");
            }
            String identityNumber = trim(dto.getIdentityNumber());
            if (!StringUtils.hasText(identityNumber)) throw new BusinessException("请填写学号或工号");
            if (user.isStudent() && identityType != User.IdentityType.STUDENT) {
                throw new BusinessException("学生账号只能绑定学号");
            }
            User existing = userRepository.findBySchoolIdAndIdentityTypeAndIdentityNumber(
                    schoolId, identityType, identityNumber).orElse(null);
            if (existing != null && !existing.getId().equals(userId)) {
                throw new BusinessException("该学校的学号或工号已被其他账号绑定");
            }

            user.setSchoolId(schoolId);
            user.setUnitName(StringUtils.hasText(dto.getUnitName()) ? dto.getUnitName().trim() : school.getName());
            user.setIdentityType(identityType);
            user.setIdentityNumber(identityNumber);

            if (user.isStudent()) {
                AcademicClassStudentRoster roster = studentRosterRepository
                        .findBySchoolIdAndStudentNumber(schoolId, identityNumber)
                        .orElseThrow(() -> new BusinessException("该学号不在所选学校的行政班名单中，请先导入名单"));
                if (!"ACTIVE".equals(roster.getStatus())) {
                    throw new BusinessException("该学号在行政班名单中已停用，请联系管理员");
                }
                if (roster.getUserId() != null && !roster.getUserId().equals(userId)) {
                    throw new BusinessException("该学号已经绑定其他账号");
                }
                detachPreviousRoster(userId, roster.getId());
                Long classId = dto.getAcademicClassId() != null ? dto.getAcademicClassId() : roster.getAcademicClassId();
                AcademicClass targetClass = academicClassRepository.findById(classId)
                        .orElseThrow(() -> new BusinessException("行政班不存在"));
                if (!schoolId.equals(targetClass.getSchoolId())) throw new BusinessException("行政班与学校不一致");
                if (!"ACTIVE".equals(targetClass.getStatus())) throw new BusinessException("行政班已归档");
                roster.setAcademicClassId(classId);
                roster.setUserId(userId);
                roster.setName(StringUtils.hasText(dto.getNickname()) ? dto.getNickname().trim() : roster.getName());
                roster.setStatus("ACTIVE");
                user.setAcademicClassId(classId);
                user.setIdentityStatus(User.IdentityStatus.VERIFIED);
                studentRosterRepository.save(roster);
            } else {
                SchoolTeacherRoster teacherRoster = teacherRosterRepository
                        .findBySchoolIdAndEmployeeNumberAndStatus(schoolId, identityNumber, "ACTIVE")
                        .orElseThrow(() -> new BusinessException("该工号不在所选学校的教师名单中，请先录入教师名单"));
                if (teacherRoster.getUserId() != null && !teacherRoster.getUserId().equals(userId)) {
                    throw new BusinessException("该工号已经绑定其他账号");
                }
                if (StringUtils.hasText(teacherRoster.getName())) user.setNickname(teacherRoster.getName());
                if (StringUtils.hasText(teacherRoster.getDepartment())) user.setUnitName(teacherRoster.getDepartment());
                user.setAcademicClassId(null);
                user.setIdentityStatus(User.IdentityStatus.VERIFIED);
                detachPreviousTeacherRoster(userId, teacherRoster.getId());
                teacherRoster.setUserId(userId);
                teacherRosterRepository.save(teacherRoster);
            }
        }

        userRepository.save(user);
        safeDeleteUserCache(userId);
        log.info("管理员编辑用户账号: userId={}", userId);
    }

    private void detachPreviousRoster(Long userId, Long keepRosterId) {
        studentRosterRepository.findByUserId(userId).ifPresent(previous -> {
            if (!Objects.equals(previous.getId(), keepRosterId)) {
                previous.setUserId(null);
                studentRosterRepository.save(previous);
            }
        });
    }

    private void detachPreviousTeacherRoster(Long userId, Long keepRosterId) {
        teacherRosterRepository.findByUserId(userId).ifPresent(previous -> {
            if (!Objects.equals(previous.getId(), keepRosterId)) {
                previous.setUserId(null);
                teacherRosterRepository.save(previous);
            }
        });
    }

    @Override
    @Transactional
    public void updateProfile(Long userId, UserProfileDTO dto) {
        User user = getEntityById(userId);
        if (StringUtils.hasText(dto.getEmail()) && !dto.getEmail().equals(user.getEmail())) {
            if (userRepository.existsByEmail(dto.getEmail())) {
                throw new BusinessException("邮箱已被使用");
            }
            user.setEmail(dto.getEmail());
        }
        if (StringUtils.hasText(dto.getNickname())) user.setNickname(dto.getNickname());
        if (StringUtils.hasText(dto.getPhone())) user.setPhone(dto.getPhone());
        if (StringUtils.hasText(dto.getAvatar())) user.setAvatar(dto.getAvatar());
        if (StringUtils.hasText(dto.getBio())) user.setBio(dto.getBio());
        userRepository.save(user);
        cacheUser(user);
        log.info("用户更新资料: userId={}", userId);
    }

    @Override
    @Transactional
    public void batchReview(BatchReviewDTO dto, Long operatorId) {
        User.UserStatus status = "APPROVE".equals(dto.getAction()) ? User.UserStatus.ACTIVE : User.UserStatus.BANNED;
        int updated = 0;
        for (User user : userRepository.findAllById(dto.getUserIds())) {
            adminScopeService.requireUser(user);
            user.setStatus(status);
            userRepository.save(user);
            updated++;
        }
        dto.getUserIds().forEach(this::safeDeleteUserCache);
        log.info("批量审核用户: 操作人={}, 数量={}, 状态={}", operatorId, updated, status);
    }

    @Override
    @Transactional
    public void updateStatus(Long userId, User.UserStatus status, String reason) {
        User user = getEntityById(userId);
        adminScopeService.requireUser(user);
        user.setStatus(status);
        userRepository.save(user);
        safeDeleteUserCache(userId);
        log.info("修改用户状态: 目标用户={}, 新状态={}, 原因={}", userId, status, reason);
    }

    @Override
    @Transactional
    public void assignRole(Long userId, User.UserRole role, Long operatorId) {
        User user = getEntityById(userId);
        User operator = adminScopeService.currentUser();
        if (user.isAdmin() || role == User.UserRole.ADMIN || role == User.UserRole.SUPER_ADMIN) {
            if (!adminScopeService.canConfigure(operator)) throw new BusinessException(403, "只有平台管理员可以调整管理员账号");
        } else {
            adminScopeService.requireUser(user);
        }
        user.setRole(role);
        if (role == User.UserRole.ADMIN) {
            user.setAdminScopeConfigured(false);
            user.setAdminScopeAll(false);
            user.setAdminScopeUnbound(false);
            adminSchoolAccessRepository.deleteByAdminUserId(userId);
        } else if (role == User.UserRole.SUPER_ADMIN) {
            user.setAdminScopeConfigured(true);
            user.setAdminScopeAll(true);
            user.setAdminScopeUnbound(false);
        }
        userRepository.save(user);
        safeDeleteUserCache(userId);
        log.info("分配角色: 目标用户={}, 新角色={}, 操作人={}", userId, role, operatorId);
    }

    @Override
    @Transactional
    public String resetPassword(Long userId, Long operatorId) {
        User user = getEntityById(userId);
        adminScopeService.requireUser(user);
        if (user.getStatus() == User.UserStatus.DELETED) {
            throw new BusinessException("已删除账号不能重置密码");
        }
        if (user.getId().equals(operatorId)) {
            throw new BusinessException("请在个人资料中修改自己的密码");
        }

        String temporaryPassword = generateTemporaryPassword();
        user.setPassword(passwordEncoder.encode(temporaryPassword));
        userRepository.save(user);
        safeDeleteUserCache(userId);
        log.info("管理员重置用户密码: 目标用户={}, 操作人={}", userId, operatorId);
        return temporaryPassword;
    }

    private String generateTemporaryPassword() {
        StringBuilder password = new StringBuilder(10);
        for (int i = 0; i < 10; i++) {
            password.append(TEMP_PASSWORD_CHARS.charAt(SECURE_RANDOM.nextInt(TEMP_PASSWORD_CHARS.length())));
        }
        return password.toString();
    }

    @Override
    @Transactional
    public void deleteUser(Long userId, Long operatorId) {
        User user = getEntityById(userId);
        adminScopeService.requireUser(user);

        if (user.getId().equals(operatorId)) {
            throw new BusinessException("不能删除自己的账号");
        }

        log.info("开始删除用户: userId={}, username={}, role={}", userId, user.getUsername(), user.getRole());

        // 1. 如果该用户是老师，清理班级和班级学生关系
        if (user.isTeacher()) {
            // 获取该教师的所有班级
            List<Classroom> classrooms = classroomRepository.findByTeacherIdAndStatus(userId, "ACTIVE");
            for (Classroom classroom : classrooms) {
                // 删除班级中的所有学生关系
                classroomStudentRelationRepository.removeAllStudentsFromClassroom(classroom.getId());
                // 归档班级
                classroom.setStatus("ARCHIVED");
                classroomRepository.save(classroom);
                log.info("清理教师班级: 教师Id={}, 班级Id={}, 班级名称={}", userId, classroom.getId(), classroom.getClassName());
            }

            // 关闭该教师发布的作业
            int assignmentsClosed = assignmentRepository.closeByTeacherId(userId);
            log.info("关闭教师作业: 教师Id={}, 关闭作业数={}", userId, assignmentsClosed);
        }

        // 2. 如果该用户是学生，清理班级学生关系
        if (user.isStudent()) {
            // 从所有班级中移除该学生
            classroomStudentRelationRepository.removeAllByStudentId(userId);
            log.info("清理学生班级关系: 学生Id={}", userId);
        }

        // 3. 软删除用户
        if (user.isAdmin()) adminSchoolAccessRepository.deleteByAdminUserId(userId);
        userRepository.softDelete(userId);

        // 4. 将用户状态设置为 DELETED，禁止登录
        user.setStatus(User.UserStatus.DELETED);
        userRepository.save(user);

        // 5. 清理缓存
        safeDeleteUserCache(userId);

        log.info("删除用户完成: 目标用户={}, 用户名={}, 操作人={}",
                userId, user.getUsername(), operatorId);
    }

    @Override
    public UserStatisticsVO getStatistics() {
        UserStatisticsVO vo = new UserStatisticsVO();
        vo.setTotalUsers(userRepository.count());
        vo.setActiveUsers(userRepository.countByStatus(User.UserStatus.ACTIVE));
        vo.setPendingUsers(userRepository.countByStatus(User.UserStatus.PENDING));
        vo.setPendingIdentityBindings(userRepository.countByIdentityStatusAndIsDeletedFalse(User.IdentityStatus.PENDING));
        Map<String, Long> roleDist = userRepository.countByRole().stream()
                .collect(Collectors.groupingBy(
                        entry -> ((User.UserRole) entry[0]) == User.UserRole.SUPER_ADMIN ? "ADMIN" : ((User.UserRole) entry[0]).name(),
                        Collectors.summingLong(item -> (Long) item[1])
                ));
        vo.setRoleDistribution(roleDist);
        Map<String, Long> statusDist = Map.of(
                "ACTIVE", userRepository.countByStatus(User.UserStatus.ACTIVE),
                "PENDING", userRepository.countByStatus(User.UserStatus.PENDING),
                "SUSPENDED", userRepository.countByStatus(User.UserStatus.SUSPENDED),
                "BANNED", userRepository.countByStatus(User.UserStatus.BANNED),
                "DELETED", userRepository.countByStatus(User.UserStatus.DELETED)
        );
        vo.setStatusDistribution(statusDist);
        return vo;
    }

    @Override
    public User getEntityById(Long id) {
        String cacheKey = USER_CACHE_PREFIX + id;
        User cached = safeGetCachedUser(cacheKey, id);
        if (cached != null) {
            return cached;
        }
        User user = userRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("用户", id));
        cacheUser(user);
        return user;
    }

    @Override
    public boolean existsByUsername(String username) {
        return userRepository.existsByUsername(username);
    }

    @Override
    public UserVO getUserByUsername(String username) {
        User user = userRepository.findByUsername(username)
                .orElse(null);
        if (user == null) {
            return null;
        }
        return convertToVO(user);
    }

    @Override
    public User getEntityByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElse(null);
    }

    private void cacheUser(User user) {
        try {
            redisTemplate.opsForValue().set(
                    USER_CACHE_PREFIX + user.getId(),
                    user,
                    CACHE_TTL,
                    TimeUnit.HOURS
            );
        } catch (Exception e) {
            log.warn("Redis 缓存失败，将跳过缓存: {}", e.getMessage());
        }
    }

    private void safeDeleteUserCache(Long userId) {
        try {
            redisTemplate.delete(USER_CACHE_PREFIX + userId);
        } catch (Exception e) {
            log.warn("Redis cache eviction failed, falling back to DB: userId={}, err={}", userId, e.getMessage());
        }
    }

    private User safeGetCachedUser(String cacheKey, Long userId) {
        try {
            return (User) redisTemplate.opsForValue().get(cacheKey);
        } catch (Exception e) {
            log.warn("Redis cache read failed, falling back to DB: userId={}, err={}", userId, e.getMessage());
            return null;
        }
    }
    private UserVO convertToVO(User user) {
        UserVO vo = new UserVO();
        BeanUtils.copyProperties(user, vo);

        if (user.getRole() != null) {
            vo.setRole(user.getRole() == User.UserRole.SUPER_ADMIN ? User.UserRole.ADMIN.name() : user.getRole().name());
        }

        if (user.getStatus() != null) {
            vo.setStatus(user.getStatus().name());
        } else {
            vo.setStatus("PENDING");
        }

        vo.setIdentityType(user.getIdentityType() == null ? null : user.getIdentityType().name());
        vo.setIdentityStatus(user.getIdentityStatus() == null
                ? User.IdentityStatus.UNBOUND.name() : user.getIdentityStatus().name());

        if (vo.getAvatar() == null || vo.getAvatar().isEmpty()) {
            vo.setAvatar("/uploads/default-avatar.png");
        }

        if (user.getAcademicClassId() != null) {
            academicClassRepository.findById(user.getAcademicClassId())
                    .ifPresent(academicClass -> vo.setAcademicClassName(academicClass.getName()));
        }

        if (user.getSchoolId() != null) {
            schoolRepository.findById(user.getSchoolId()).ifPresent(school -> {
                vo.setSchoolId(school.getId());
                vo.setSchoolName(school.getName());
                vo.setSchoolCode(school.getCode());
            });
        }

        return vo;
    }

    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        return ip;
    }

    @Override
    @Transactional
    public String uploadAvatar(Long userId, MultipartFile file) {
        User user = getEntityById(userId);

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.isEmpty()) {
            throw new BusinessException("无效的文件名");
        }
        String extension = originalFilename.substring(originalFilename.lastIndexOf("."));
        List<String> allowedExtensions = Arrays.asList(".jpg", ".jpeg", ".png", ".gif");
        if (!allowedExtensions.contains(extension.toLowerCase())) {
            throw new BusinessException("只支持 JPG、PNG、GIF 格式的图片");
        }
        if (file.getSize() > 5 * 1024 * 1024) {
            throw new BusinessException("图片大小不能超过 5MB");
        }

        String projectRoot = System.getProperty("user.dir");
        String baseUploadDir = projectRoot + File.separator + uploadPath;
        File baseDir = new File(baseUploadDir);
        if (!baseDir.exists() && !baseDir.mkdirs()) {
            throw new BusinessException("创建上传根目录失败");
        }

        String datePath = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM"));
        File destDir = new File(baseDir, datePath);
        if (!destDir.exists() && !destDir.mkdirs()) {
            throw new BusinessException("创建存储目录失败");
        }

        String filename = UUID.randomUUID().toString() + extension;
        File destFile = new File(destDir, filename);

        try {
            file.transferTo(destFile);
        } catch (IOException e) {
            log.error("头像上传失败", e);
            throw new BusinessException("头像上传失败，请稍后重试");
        }

        String avatarUrl = "/uploads/" + datePath + "/" + filename;

        user.setAvatar(avatarUrl);
        userRepository.save(user);
        safeDeleteUserCache(userId);

        log.info("用户头像更新成功: userId={}, avatarUrl={}", userId, avatarUrl);
        return avatarUrl;
    }
}

