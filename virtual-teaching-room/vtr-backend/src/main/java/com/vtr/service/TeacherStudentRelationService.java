package com.vtr.service;

import com.vtr.common.PageResult;
import com.vtr.dto.AddStudentDTO;
import com.vtr.dto.BatchImportStudentDTO;
import com.vtr.dto.ChangeClassroomDTO;
import com.vtr.vo.ClassroomVO;
import com.vtr.vo.UserVO;

import java.util.List;

public interface TeacherStudentRelationService {

    /**
     * 获取我的学生列表（支持按班级过滤）
     */
    PageResult<UserVO> getMyStudents(Long teacherId, Integer page, Integer size, String keyword, Long classroomId);

    /**
     * 获取我的老师列表（学生端）
     */
    List<UserVO> getMyTeachers(Long studentId);

    /**
     * 根据学号搜索学生
     */
    UserVO searchStudentByStudentId(Long requesterId, String studentNumber);

    /**
     * 添加学生到我的班级（可指定班级）
     */
    void addStudentToTeacher(Long teacherId, AddStudentDTO dto);

    /**
     * 从我的班级移除学生
     */
    void removeStudentFromTeacher(Long teacherId, Long studentId);

    /**
     * 批量导入学生到指定班级
     */
    void batchImportStudents(Long teacherId, BatchImportStudentDTO dto);

    /**
     * 获取教师的所有学生ID列表
     */
    List<Long> getMyStudentIds(Long teacherId);

    /**
     * 获取指定班级的学生ID列表
     */
    List<Long> getClassroomStudentIds(Long teacherId, Long classroomId);

    /**
     * 检查学生是否是我的学生
     */
    boolean isMyStudent(Long teacherId, Long studentId);

    /**
     * 获取可添加的学生列表（支持排除指定班级）
     */
    PageResult<UserVO> getAvailableStudents(Long teacherId, Integer page, Integer size, String keyword, Long excludeClassroomId);

    /**
     * 学生调班
     */
    void changeStudentClassroom(Long teacherId, ChangeClassroomDTO dto);

    /**
     * 获取学生的班级信息
     */
    ClassroomVO getStudentClassroom(Long teacherId, Long studentId);
}
