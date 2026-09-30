package com.vtr.service.impl;

import com.vtr.dto.CodeExecutionRequest;
import com.vtr.dto.CodeExecutionResult;
import com.vtr.dto.TestCaseDTO;
import com.vtr.service.CodeExecutionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.tools.*;
import java.io.*;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
public class CodeExecutionServiceImpl implements CodeExecutionService {

    private static final int EXECUTION_TIMEOUT = 5000;

    @Override
    public CodeExecutionResult executeCode(String code, String language, List<TestCaseDTO> testCases) {
        String lang = language.toLowerCase();
        switch (lang) {
            case "java":
                return executeJava(code, testCases);
            case "javascript":
            case "js":
                return executeJavaScript(code, testCases);
            case "python":
                return executePythonByLogic(code, testCases);
            default:
                // 不支持的语言提示用户
                return CodeExecutionResult.error("当前演示环境仅支持 Java 和 JavaScript，其他语言将作为文本作业处理（仅人工评审）");
        }
    }

    @Override
    public CodeExecutionResult runTest(CodeExecutionRequest request) {
        return executeCode(request.getCode(), request.getLanguage(), request.getTestCases());
    }

    // ==================== Java 执行（完整支持） ====================
    private CodeExecutionResult executeJava(String code, List<TestCaseDTO> testCases) {
        try {
            String className = extractJavaClassName(code);
            if (className == null) className = "Main";

            String tmpDir = System.getProperty("java.io.tmpdir");
            File sourceFile = new File(tmpDir, className + ".java");

            try (FileWriter writer = new FileWriter(sourceFile)) {
                writer.write(code);
            }

            // 编译
            JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
            DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
            StandardJavaFileManager fileManager = compiler.getStandardFileManager(diagnostics, null, null);
            Iterable<? extends JavaFileObject> compilationUnits = fileManager.getJavaFileObjects(sourceFile);

            ByteArrayOutputStream errStream = new ByteArrayOutputStream();
            PrintStream errPrint = new PrintStream(errStream);
            PrintStream oldErr = System.err;
            System.setErr(errPrint);

            JavaCompiler.CompilationTask task = compiler.getTask(null, fileManager, diagnostics, null, null, compilationUnits);
            boolean success = task.call();

            System.setErr(oldErr);
            fileManager.close();

            if (!success) {
                StringBuilder errorMsg = new StringBuilder("编译失败:\n");
                for (Diagnostic<?> diagnostic : diagnostics.getDiagnostics()) {
                    errorMsg.append(diagnostic.toString()).append("\n");
                }
                return CodeExecutionResult.compileError(errorMsg.toString());
            }

            // 执行
            try (URLClassLoader classLoader = URLClassLoader.newInstance(new URL[]{new File(tmpDir).toURI().toURL()})) {
                Class<?> clazz = classLoader.loadClass(className);
                List<CodeExecutionResult.TestResult> results = new ArrayList<>();
                int passedCount = 0;
                int totalScore = 0;

                for (TestCaseDTO testCase : testCases) {
                    CodeExecutionResult.TestResult result = executeJavaTestMethod(clazz, testCase);
                    results.add(result);
                    if (result.isPassed()) {
                        passedCount++;
                        totalScore += testCase.getScore() != null ? testCase.getScore() : 0;
                    }
                }

                sourceFile.delete();
                new File(tmpDir, className + ".class").delete();
                return CodeExecutionResult.success(results, passedCount, testCases.size(), totalScore);
            }
        } catch (Exception e) {
            log.error("Java代码执行失败", e);
            return CodeExecutionResult.error("执行失败: " + e.getMessage());
        }
    }

    private CodeExecutionResult.TestResult executeJavaTestMethod(Class<?> clazz, TestCaseDTO testCase) {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<String> future = executor.submit(() -> {
            try {
                Method method;
                try {
                    method = clazz.getMethod("main", String[].class);
                } catch (NoSuchMethodException e) {
                    try {
                        method = clazz.getMethod("solve");
                    } catch (NoSuchMethodException ex) {
                        return "ERROR: 未找到main或solve方法";
                    }
                }

                ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                PrintStream ps = new PrintStream(outputStream);
                PrintStream oldOut = System.out;
                InputStream oldIn = System.in;

                String input = testCase.getInput() != null ? testCase.getInput() : "";
                ByteArrayInputStream bais = new ByteArrayInputStream(input.getBytes());
                System.setIn(bais);
                System.setOut(ps);

                if (method.getParameterCount() > 0 && method.getParameterTypes()[0] == String[].class) {
                    method.invoke(null, (Object) new String[]{});
                } else {
                    method.invoke(null);
                }

                String output = outputStream.toString();
                System.setOut(oldOut);
                System.setIn(oldIn);
                outputStream.close();
                return output;
            } catch (Exception e) {
                return "ERROR: " + e.getMessage();
            }
        });

        try {
            String output = future.get(EXECUTION_TIMEOUT, TimeUnit.MILLISECONDS);
            boolean passed = compareOutput(output, testCase.getExpectedOutput());
            return new CodeExecutionResult.TestResult(testCase, output, passed);
        } catch (TimeoutException e) {
            future.cancel(true);
            return new CodeExecutionResult.TestResult(testCase, "执行超时", false);
        } catch (Exception e) {
            return new CodeExecutionResult.TestResult(testCase, "执行错误: " + e.getMessage(), false);
        } finally {
            executor.shutdownNow();
        }
    }

    // ==================== JavaScript 执行（使用 Nashorn/Rhino） ====================
    private CodeExecutionResult executeJavaScript(String code, List<TestCaseDTO> testCases) {
        try {
            ScriptEngineManager manager = new ScriptEngineManager();
            ScriptEngine engine = manager.getEngineByName("javascript");

            if (engine == null) {
                return CodeExecutionResult.error("JavaScript引擎不可用，请使用Java 8+或添加依赖");
            }

            List<CodeExecutionResult.TestResult> results = new ArrayList<>();
            int passedCount = 0;
            int totalScore = 0;

            for (TestCaseDTO testCase : testCases) {
                CodeExecutionResult.TestResult result = executeJavaScriptWithEngine(engine, code, testCase);
                results.add(result);
                if (result.isPassed()) {
                    passedCount++;
                    totalScore += testCase.getScore() != null ? testCase.getScore() : 0;
                }
            }

            return CodeExecutionResult.success(results, passedCount, testCases.size(), totalScore);
        } catch (Exception e) {
            log.error("JavaScript代码执行失败", e);
            return CodeExecutionResult.error("执行失败: " + e.getMessage());
        }
    }

    private CodeExecutionResult.TestResult executeJavaScriptWithEngine(ScriptEngine engine, String code, TestCaseDTO testCase) {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<String> future = executor.submit(() -> {
            try {
                // 设置输入
                String input = testCase.getInput() != null ? testCase.getInput() : "";
                engine.put("input", input);

                // 捕获输出
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                PrintStream ps = new PrintStream(baos);
                PrintStream oldOut = System.out;
                System.setOut(ps);

                // 执行代码
                engine.eval(code);

                // 如果有main函数则调用
                try {
                    Object mainFunction = engine.eval("typeof main === 'function' ? main : null");
                    if (mainFunction != null && !"null".equals(String.valueOf(mainFunction))) {
                        engine.eval("main(input)");
                    }
                } catch (Exception e) {
                    // 忽略，可能没有main函数
                }

                String output = baos.toString();
                System.setOut(oldOut);
                ps.close();
                baos.close();

                return output;
            } catch (Exception e) {
                return "ERROR: " + e.getMessage();
            }
        });

        try {
            String output = future.get(EXECUTION_TIMEOUT, TimeUnit.MILLISECONDS);
            boolean passed = compareOutput(output, testCase.getExpectedOutput());
            return new CodeExecutionResult.TestResult(testCase, output, passed);
        } catch (TimeoutException e) {
            future.cancel(true);
            return new CodeExecutionResult.TestResult(testCase, "执行超时", false);
        } catch (Exception e) {
            return new CodeExecutionResult.TestResult(testCase, "执行错误: " + e.getMessage(), false);
        } finally {
            executor.shutdownNow();
        }
    }

    // ==================== Python 逻辑模拟（演示用） ====================
    private CodeExecutionResult executePythonByLogic(String code, List<TestCaseDTO> testCases) {
        // Python 需要外部依赖，演示环境中返回提示
        return CodeExecutionResult.error("演示环境暂不支持Python自动评测，Python代码将作为文本作业处理（仅人工评审）");
    }

    // ==================== 辅助方法 ====================
    private String extractJavaClassName(String code) {
        Pattern pattern = Pattern.compile("public\\s+class\\s+(\\w+)");
        Matcher matcher = pattern.matcher(code);
        return matcher.find() ? matcher.group(1) : null;
    }

    private String normalizeOutput(String output) {
        if (output == null) return "";
        return output.trim().replaceAll("\\s+", " ").replaceAll("\\r\\n", "\n");
    }

    private boolean compareOutput(String actual, String expected) {
        String normalizedActual = normalizeOutput(actual);
        String normalizedExpected = normalizeOutput(expected);

        if (normalizedActual.equals(normalizedExpected)) {
            return true;
        }

        try {
            double actualNum = Double.parseDouble(normalizedActual);
            double expectedNum = Double.parseDouble(normalizedExpected);
            return Math.abs(actualNum - expectedNum) < 0.0001;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}