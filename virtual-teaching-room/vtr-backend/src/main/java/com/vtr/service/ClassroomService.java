package com.vtr.service;

import com.vtr.common.PageResult;
import com.vtr.dto.AddStudentToClassroomDTO;
import com.vtr.dto.ClassroomCreateDTO;
import com.vtr.dto.ClassroomUpdateDTO;
import com.vtr.dto.JoinClassroomDTO;
import com.vtr.vo.ClassroomVO;
import com.vtr.vo.UserVO;

import java.util.List;

public interface ClassroomService {

    /**
     * 创建班级
     */
    Long createClassroom(ClassroomCreateDTO dto, Long teacherId);

    /**
     * 更新班级信息
     */
    void updateClassroom(Long id, ClassroomUpdateDTO dto, Long teacherId);

    /**
     * 删除班级（软删除）
     */
    void deleteClassroom(Long id, Long teacherId);

    /**
     * 归档班级
     */
    void archiveClassroom(Long id, Long teacherId);

    /**
     * 恢复归档的班级
     */
    void restoreClassroom(Long id, Long teacherId);

    /**
     * 获取班级详情
     */
    ClassroomVO getClassroomById(Long id, Long teacherId);

    /**
     * 获取教师的班级列表（支持按状态筛选）
     */
    PageResult<ClassroomVO> getMyClassrooms(Long teacherId, Integer page, Integer size, String keyword, String status);

    /**
     * 添加学生到班级
     */
    void addStudentsToClassroom(AddStudentToClassroomDTO dto, Long teacherId);

    /**
     * 从班级移除学生
     */
    void removeStudentFromClassroom(Long classroomId, Long studentId, Long teacherId);

    /**
     * 获取班级的学生列表
     */
    PageResult<UserVO> getClassroomStudents(Long classroomId, Long teacherId, Integer page, Integer size, String keyword);

    /**
     * 获取学生所在的班级列表
     */
    List<ClassroomVO> getStudentClassrooms(Long studentId, Long teacherId);

    void joinClassroom(JoinClassroomDTO dto, Long studentId);

    List<ClassroomVO> getJoinedClassrooms(Long studentId);

    /** 学生主动退出教学班，仅解除教学班关系，不影响账号和行政班归属。 */
    void leaveClassroom(Long classroomId, Long studentId);

    // ==================== 管理员接口 ====================

    /**
     * 管理员获取所有班级列表（支持状态筛选）
     */
    PageResult<ClassroomVO> getAllClassroomsForAdmin(Integer page, Integer size, String keyword, String status);

    /**
     * 管理员安排班级教师
     */
    void assignTeacherToClassroom(Long classroomId, Long teacherId);

    /**
     * 管理员删除班级（物理删除）
     */
    void adminDeleteClassroom(Long id);
}
