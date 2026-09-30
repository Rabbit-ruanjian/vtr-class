package com.vtr.controller;

import com.vtr.common.Result;
import com.vtr.dto.CodeExecutionRequest;
import com.vtr.dto.CodeExecutionResult;
import com.vtr.entity.Assignment;
import com.vtr.entity.TestCase;
import com.vtr.service.AssignmentService;
import com.vtr.service.CodeExecutionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/code")
@RequiredArgsConstructor
public class CodeExecutionController {

    private final CodeExecutionService codeExecutionService;
    private final AssignmentService assignmentService;

    @PostMapping("/run")
    public Result<CodeExecutionResult> runCode(@RequestBody CodeExecutionRequest request) {
        log.info("执行代码测试: language={}, assignmentId={}", request.getLanguage(), request.getAssignmentId());

        try {
            // 获取作业的测试用例
            if (request.getAssignmentId() != null && (request.getTestCases() == null || request.getTestCases().isEmpty())) {
                Assignment assignment = assignmentService.getEntityById(request.getAssignmentId());
                List<TestCase> testCases = assignment.getTestCases();

                // 转换为执行所需的格式
                List<com.vtr.dto.TestCaseDTO> testCaseDTOs = new java.util.ArrayList<>();
                for (TestCase tc : testCases) {
                    com.vtr.dto.TestCaseDTO dto = new com.vtr.dto.TestCaseDTO();
                    dto.setDescription(tc.getDescription());
                    dto.setInput(tc.getInput());
                    dto.setExpectedOutput(tc.getExpectedOutput());
                    dto.setScore(tc.getScore());
                    testCaseDTOs.add(dto);
                }
                request.setTestCases(testCaseDTOs);
            }

            CodeExecutionResult result = codeExecutionService.runTest(request);
            return Result.success(result);
        } catch (Exception e) {
            log.error("代码执行失败", e);
            return Result.error(500, "执行失败: " + e.getMessage());
        }
    }
}