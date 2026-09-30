package com.vtr.service;

import com.vtr.common.exception.BusinessException;
import com.vtr.dto.SendVerificationCodeDTO;
import com.vtr.dto.UserRegisterDTO;
import com.vtr.entity.User;
import com.vtr.repository.AcademicClassRepository;
import com.vtr.repository.AcademicClassStudentRosterRepository;
import com.vtr.repository.AdminSchoolAccessRepository;
import com.vtr.repository.AssignmentRepository;
import com.vtr.repository.ClassroomRepository;
import com.vtr.repository.ClassroomStudentRelationRepository;
import com.vtr.repository.SchoolRepository;
import com.vtr.repository.SchoolTeacherRosterRepository;
import com.vtr.repository.UserRepository;
import com.vtr.security.JwtTokenProvider;
import com.vtr.service.impl.UserServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.crypto.password.PasswordEncoder;

import javax.servlet.http.HttpServletRequest;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserRegistrationVerificationTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtTokenProvider jwtTokenProvider;
    @Mock private RedisTemplate<String, Object> redisTemplate;
    @Mock private ValueOperations<String, Object> valueOperations;
    @Mock private AcademicClassRepository academicClassRepository;
    @Mock private ClassroomRepository classroomRepository;
    @Mock private ClassroomStudentRelationRepository classroomStudentRelationRepository;
    @Mock private AssignmentRepository assignmentRepository;
    @Mock private VerificationCodeSender verificationCodeSender;
    @Mock private SchoolRepository schoolRepository;
    @Mock private AcademicClassStudentRosterRepository studentRosterRepository;
    @Mock private SchoolTeacherRosterRepository teacherRosterRepository;
    @Mock private AdminSchoolAccessRepository adminSchoolAccessRepository;
    @Mock private AdminScopeService adminScopeService;
    @InjectMocks private UserServiceImpl userService;

    @BeforeEach
    void setUpRedisOperations() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    void registrationWithoutIssuedCodeNeverCreatesAccount() {
        UserRegisterDTO dto = registration("student@example.com", "123456");
        when(userRepository.findByEmail("student@example.com")).thenReturn(Optional.empty());
        when(valueOperations.increment(anyString())).thenReturn(1L);

        assertThrows(BusinessException.class, () -> userService.register(dto));

        verify(userRepository, never()).saveAndFlush(any(User.class));
    }

    @Test
    void validEmailCodeCreatesVerifiedAccountAndConsumesCode() {
        UserRegisterDTO dto = registration("student@example.com", "123456");
        String codeKey = "auth:verification:REGISTER:EMAIL:student@example.com";
        when(userRepository.findByEmail("student@example.com")).thenReturn(Optional.empty());
        when(valueOperations.get(codeKey)).thenReturn("stored-code-hash");
        when(passwordEncoder.matches("123456", "stored-code-hash")).thenReturn(true);
        when(passwordEncoder.encode("secret12")).thenReturn("encoded-password");
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(8L);
            return user;
        });

        Long userId = userService.register(dto);

        ArgumentCaptor<User> savedUser = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(savedUser.capture());
        assertEquals(8L, userId);
        assertEquals("student@example.com", savedUser.getValue().getEmail());
        assertNotNull(savedUser.getValue().getEmailVerifiedAt());
        verify(redisTemplate).delete(codeKey);
        verify(redisTemplate).delete(codeKey + ":attempts");
    }

    @Test
    void registerSceneSendsEmailCode() {
        SendVerificationCodeDTO dto = new SendVerificationCodeDTO();
        dto.setChannel("EMAIL");
        dto.setTarget("student@example.com");
        dto.setScene("REGISTER");
        HttpServletRequest request = org.mockito.Mockito.mock(HttpServletRequest.class);
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");
        when(valueOperations.increment("auth:verification:limit:127.0.0.1")).thenReturn(1L);
        when(passwordEncoder.encode(anyString())).thenReturn("stored-code-hash");

        userService.sendVerificationCode(dto, request);

        verify(valueOperations).set(eq("auth:verification:REGISTER:EMAIL:student@example.com"),
                eq("stored-code-hash"), eq(300L), eq(TimeUnit.SECONDS));
        verify(verificationCodeSender).sendEmail(eq("student@example.com"), anyString());
        verify(redisTemplate).expire("auth:verification:limit:127.0.0.1", 1, TimeUnit.HOURS);
    }

    private UserRegisterDTO registration(String email, String code) {
        UserRegisterDTO dto = new UserRegisterDTO();
        dto.setUsername("student_test");
        dto.setEmail(email);
        dto.setCode(code);
        dto.setPassword("secret12");
        dto.setRole("STUDENT");
        return dto;
    }
}
