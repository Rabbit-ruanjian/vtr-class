package com.vtr.service;

import com.vtr.common.PageResult;
import com.vtr.dto.CoursewareCreateDTO;
import com.vtr.dto.CoursewareQueryDTO;
import com.vtr.dto.CoursewareUpdateDTO;
import com.vtr.dto.StructuredOutlineCreateDTO;
import com.vtr.dto.VideoAuditDTO;
import com.vtr.dto.VideoProgressDTO;
import com.vtr.vo.CoursewareVO;

import java.util.List;
import java.util.Map;
import org.springframework.web.multipart.MultipartFile;

public interface CoursewareService {

    /**
     * 上传课件
     */
    Long uploadCourseware(CoursewareCreateDTO dto, Long teacherId);

    /** Creates an outline from the structured node editor without requiring an uploaded file. */
    Long createStructuredTeachingOutline(StructuredOutlineCreateDTO dto, Long teacherId);

    /**
     * 更新课件信息
     */
    void updateCourseware(Long id, CoursewareUpdateDTO dto, Long userId, boolean isAdmin);

    /**
     * 删除课件（软删除）
     */
    void deleteCourseware(Long id, Long userId, boolean isAdmin);

    /**
     * 获取课件详情
     */
    CoursewareVO getCoursewareById(Long id, Long userId, String userRole);

    /**
     * 获取教师的课件列表（教师自己的课件 + 公开课件）
     */
    PageResult<CoursewareVO> getCoursewaresForTeacher(Long teacherId, CoursewareQueryDTO queryDTO);

    /**
     * 获取可见课件列表（学生端：公开课件 + 班级课件）
     */
    PageResult<CoursewareVO> getVisibleCoursewares(Long studentId, CoursewareQueryDTO queryDTO);

    /**
     * 获取所有课件列表（管理员端）
     */
    PageResult<CoursewareVO> getAllCoursewares(CoursewareQueryDTO queryDTO);

    PageResult<CoursewareVO> getPendingReviews(Integer page, Integer size, String status);

    /**
     * 下载课件
     */
    String downloadCourseware(Long id, Long userId, String userRole);

    String getAuthorizedFileName(Long id, Long userId, String userRole);

    /** Returns the authorized file URL without changing download statistics. */
    String previewCourseware(Long id, Long userId, String userRole);

    List<CoursewareVO> getTeachingVideos(Long userId, String userRole, CoursewareQueryDTO queryDTO);

    void reviewTeachingVideo(Long id, VideoAuditDTO dto, Long reviewerId);

    void reviewTeachingCourseware(Long id, VideoAuditDTO dto, Long reviewerId);

    void reviewTeachingOutline(Long id, VideoAuditDTO dto, Long reviewerId);

    void archiveTeachingOutline(Long id, Long reviewerId);

    void reviewKnowledgeMap(Long id, VideoAuditDTO dto, Long reviewerId);

    void archiveTeachingCourseware(Long id, Long reviewerId);

    void archiveTeachingVideo(Long id, Long reviewerId);

    void withdrawTeachingVideo(Long id, Long teacherId);

    void saveVideoProgress(Long id, Long studentId, VideoProgressDTO dto);

    Map<String, Object> getMyVideoProgress(Long id, Long studentId);

    Map<String, Object> getVideoStatistics(Long id, Long userId, String userRole);

    Map<String, Object> getTeachingVideoOverview(Long userId, String userRole, Long courseId);

    Long createTeachingVideoVersion(Long id, CoursewareUpdateDTO dto, Long userId, boolean isAdmin);

    /** Uploads a transcript sidecar for an existing teaching video and queues AI re-indexing. */
    void uploadVideoTranscript(Long id, MultipartFile file, Long userId);
}
