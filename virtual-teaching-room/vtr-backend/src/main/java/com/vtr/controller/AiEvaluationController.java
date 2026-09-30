package com.vtr.controller;

import com.vtr.common.Result;
import com.vtr.dto.AiEvaluationCaseUpsertDTO;
import com.vtr.service.AiEvaluationService;
import com.vtr.vo.AiEvaluationCaseVO;
import com.vtr.vo.AiEvaluationResultVO;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.util.List;

@Validated
@RestController
@RequestMapping("/api/ai/evaluations")
@RequiredArgsConstructor
public class AiEvaluationController {
    private final AiEvaluationService evaluationService;

    @GetMapping("/{courseId}/cases")
    @PreAuthorize("isAuthenticated()")
    public Result<List<AiEvaluationCaseVO>> list(@PathVariable Long courseId) {
        return Result.success(evaluationService.list(courseId));
    }

    @PostMapping("/{courseId}/cases")
    @PreAuthorize("isAuthenticated()")
    public Result<AiEvaluationCaseVO> create(@PathVariable Long courseId,
                                             @RequestBody @Valid AiEvaluationCaseUpsertDTO dto) {
        return Result.success(evaluationService.create(courseId, dto));
    }

    @PostMapping("/{courseId}/run")
    @PreAuthorize("isAuthenticated()")
    public Result<List<AiEvaluationResultVO>> run(@PathVariable Long courseId) {
        return Result.success(evaluationService.run(courseId));
    }
}
