package com.vtr.controller;

import com.vtr.common.Result;
import com.vtr.entity.AcademicClass;
import com.vtr.entity.User;
import com.vtr.repository.AcademicClassRepository;
import com.vtr.repository.AcademicClassStudentRosterRepository;
import com.vtr.repository.SchoolDepartmentRepository;
import com.vtr.repository.SchoolMajorRepository;
import com.vtr.repository.SchoolRepository;
import com.vtr.repository.UserRepository;
import com.vtr.service.AdminScopeService;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AcademicClassControllerTest {

    private final AcademicClassRepository classes = mock(AcademicClassRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final PasswordEncoder encoder = mock(PasswordEncoder.class);
    private final SchoolRepository schools = mock(SchoolRepository.class);
    private final SchoolDepartmentRepository departments = mock(SchoolDepartmentRepository.class);
    private final SchoolMajorRepository majors = mock(SchoolMajorRepository.class);
    private final AcademicClassStudentRosterRepository studentRosters = mock(AcademicClassStudentRosterRepository.class);
    private final AdminScopeService adminScopeService = mock(AdminScopeService.class);
    private final AcademicClassController controller = new AcademicClassController(
            classes, users, encoder, schools, departments, majors, studentRosters, adminScopeService);

    @Test
    void clearStudentsUnbindsRegisteredUsersAndDeletesEveryRosterRecord() {
        AcademicClass academicClass = new AcademicClass();
        academicClass.setId(11L);
        academicClass.setSchoolId(3L);
        academicClass.setStatus("ACTIVE");
        User firstStudent = new User();
        firstStudent.setAcademicClassId(11L);
        User secondStudent = new User();
        secondStudent.setAcademicClassId(11L);
        List<User> assignedStudents = List.of(firstStudent, secondStudent);

        when(classes.findById(11L)).thenReturn(Optional.of(academicClass));
        when(users.findByAcademicClassIdAndIsDeletedFalse(11L)).thenReturn(assignedStudents);
        when(studentRosters.deleteByAcademicClassId(11L)).thenReturn(39);

        Result<Integer> result = controller.clearStudents(11L);

        assertEquals(200, result.getCode());
        assertEquals(39, result.getData());
        assertNull(firstStudent.getAcademicClassId());
        assertNull(secondStudent.getAcademicClassId());
        verify(adminScopeService).requireSchool(3L);
        InOrder cleanupOrder = inOrder(users, studentRosters);
        cleanupOrder.verify(users).saveAll(assignedStudents);
        cleanupOrder.verify(studentRosters).deleteByAcademicClassId(11L);
    }
}
