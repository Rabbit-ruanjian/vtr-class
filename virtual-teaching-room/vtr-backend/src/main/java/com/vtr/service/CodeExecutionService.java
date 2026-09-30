package com.vtr.service;

import com.vtr.dto.CodeExecutionRequest;
import com.vtr.dto.CodeExecutionResult;
import com.vtr.dto.TestCaseDTO;

import java.util.List;

public interface CodeExecutionService {

    /**
     * 执行代码并运行测试用例
     * @param code 源代码
     * @param language 编程语言
     * @param testCases 测试用例列表
     * @return 执行结果
     */
    CodeExecutionResult executeCode(String code, String language, List<TestCaseDTO> testCases);

    /**
     * 运行实时测试（不保存到数据库）
     * @param request 请求参数
     * @return 执行结果
     */
    CodeExecutionResult runTest(CodeExecutionRequest request);
}