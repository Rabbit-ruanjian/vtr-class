package com.vtr.controller;

import com.vtr.common.Result;
import com.vtr.dto.AiChatRequest;
import com.vtr.service.AiTeachingAgentService;
import com.vtr.vo.AiAgentArtifactVO;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;

@Validated
@RestController
@RequestMapping("/api/ai/teaching-agent")
@RequiredArgsConstructor
public class AiTeachingAgentController {
    private final AiTeachingAgentService agentService;

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public Result<AiAgentArtifactVO> execute(@RequestBody @Valid AiChatRequest request) {
        return Result.success(agentService.execute(request));
    }

    @GetMapping("/artifacts")
    @PreAuthorize("isAuthenticated()")
    public Result<List<AiAgentArtifactVO>> list(@RequestParam Long courseId) {
        return Result.success(agentService.list(courseId));
    }

    @PostMapping("/artifacts/{id}/review")
    @PreAuthorize("isAuthenticated()")
    public Result<AiAgentArtifactVO> review(@PathVariable Long id,
                                            @RequestParam String action,
                                            @RequestParam(required = false) String remark) {
        return Result.success(agentService.review(id, action, remark));
    }
}
