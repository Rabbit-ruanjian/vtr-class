package com.vtr.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class StudentCourseVO {
    private Long id;
    private String courseName;
    private String courseCode;
    private String description;
    private String coverImage;
    private String semester;
    private String teachingDepartment;
    private String teacherName;
    private List<ClassroomVO> classrooms = new ArrayList<>();
}
