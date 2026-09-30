package com.vtr.dto;

import com.vtr.dto.TestCaseDTO;
import lombok.Data;
import java.util.ArrayList;
import java.util.List;

@Data
public class CodeExecutionResult {
    private boolean success;
    private String error;
    private List<TestResult> results;
    private int passedCount;
    private int totalCount;
    private int totalScore;

    @Data
    public static class TestResult {
        private String description;
        private String input;
        private String expectedOutput;
        private String actualOutput;
        private boolean passed;
        private int score;

        public TestResult() {}

        // 使用 TestCaseDTO 而不是 TestCase
        public TestResult(TestCaseDTO testCase, String actualOutput, boolean passed) {
            this.description = testCase.getDescription();
            this.input = testCase.getInput();
            this.expectedOutput = testCase.getExpectedOutput();
            this.actualOutput = actualOutput;
            this.passed = passed;
            this.score = passed && testCase.getScore() != null ? testCase.getScore() : 0;
        }
    }

    public static CodeExecutionResult success(List<TestResult> results, int passedCount, int totalCount, int totalScore) {
        CodeExecutionResult result = new CodeExecutionResult();
        result.setSuccess(true);
        result.setResults(results);
        result.setPassedCount(passedCount);
        result.setTotalCount(totalCount);
        result.setTotalScore(totalScore);
        return result;
    }

    public static CodeExecutionResult error(String message) {
        CodeExecutionResult result = new CodeExecutionResult();
        result.setSuccess(false);
        result.setError(message);
        return result;
    }

    public static CodeExecutionResult compileError(String message) {
        CodeExecutionResult result = new CodeExecutionResult();
        result.setSuccess(false);
        result.setError(message);
        return result;
    }
}