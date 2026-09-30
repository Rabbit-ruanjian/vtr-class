package com.vtr.service;

import com.vtr.common.PageResult;
import com.vtr.dto.AssignmentCreateDTO;
import com.vtr.dto.AssignmentQueryDTO;
import com.vtr.dto.AssignmentUpdateDTO;
import com.vtr.entity.Assignment;
import com.vtr.vo.AssignmentVO;
import com.vtr.vo.SubmissionVO;
import com.vtr.vo.TestCaseVO;

import java.util.List;

public interface AssignmentService {

    // ========== 基础 CRUD ==========
    Long create(AssignmentCreateDTO dto, Long userId);

    void update(Long id, AssignmentUpdateDTO dto, Long userId, boolean isAdmin);

    void delete(Long id, Long userId, boolean isAdmin);

    AssignmentVO getById(Long id, Long userId);

    PageResult<AssignmentVO> query(AssignmentQueryDTO query, Long userId);

    // ========== 教师端管理 ==========
    List<AssignmentVO> getTeacherAssignments(Long teacherId);

    PageResult<AssignmentVO> getTeacherAssignments(Long teacherId, AssignmentQueryDTO queryDTO);

    // ========== 学生端 ==========
    List<AssignmentVO> getStudentAssignments(Long studentId);

    PageResult<AssignmentVO> getStudentVisibleAssignments(Long studentId, AssignmentQueryDTO queryDTO);

    List<AssignmentVO> getMyAssignments(Long studentId);

    List<AssignmentVO> getTeachingAssignments(Long userId);

    List<AssignmentVO> getActiveAssignments();

    // ========== 作业状态管理 ==========
    void publish(Long id, Long userId, boolean isAdmin);

    void close(Long id, Long userId, boolean isAdmin);

    void reopen(Long id, Long userId, boolean isAdmin);

    // ========== 学生发布范围管理（新增） ==========
    void publishToStudents(Long assignmentId, List<Long> studentIds, Long teacherId, boolean isAdmin);

    boolean canStudentViewAssignment(Long studentId, Long assignmentId);

    List<Long> getAssignmentVisibleStudentIds(Long assignmentId);

    // ========== 测试用例 ==========
    List<TestCaseVO> getPublicTestCases(Long questionId);

    List<TestCaseVO> getAllTestCases(Long id, Long userId, boolean isAdmin);

    // ========== 提交相关 ==========
    List<SubmissionVO> getAllSubmissions(Long id, Long userId, boolean isAdmin);

    boolean canSubmit(Long assignmentId, Long studentId);

    int getRemainingSubmits(Long assignmentId, Long studentId);

    // ========== 辅助方法 ==========
    Assignment getEntityById(Long id);
}