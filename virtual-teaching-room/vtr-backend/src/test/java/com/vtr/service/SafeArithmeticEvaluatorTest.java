package com.vtr.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SafeArithmeticEvaluatorTest {

    @Test
    void evaluatesBasicArithmeticWithParenthesesAndDecimals() {
        SafeArithmeticEvaluator.CalculationResult result = SafeArithmeticEvaluator
                .tryEvaluateQuestion("请计算 (2 + 3) \\\\\\* 1.5 的结果")
                .orElseThrow();

        assertEquals("(2+3)*1.5", result.expression());
        assertEquals("7.5", result.value());
    }

    @Test
    void evaluatesIntegerPowersAndDivision() {
        assertEquals("0.125", SafeArithmeticEvaluator.tryEvaluateQuestion("请计算 2^-3")
                .orElseThrow().value());
        assertEquals("0.3333333333333333", SafeArithmeticEvaluator.tryEvaluateQuestion("请计算 1/3")
                .orElseThrow().value());
    }

    @Test
    void rejectsEquationsAndUnsafeOrInvalidExpressions() {
        assertTrue(SafeArithmeticEvaluator.tryEvaluateQuestion("请分步解方程 2x + 3 = 11").isEmpty());
        assertTrue(SafeArithmeticEvaluator.tryEvaluateQuestion("请计算 1/0").isEmpty());
        assertFalse(SafeArithmeticEvaluator.tryEvaluateQuestion("请计算 2+3").isEmpty());
    }

    @Test
    void verifiesOnlyPureArithmeticStatements() {
        SafeArithmeticEvaluator.EquationVerification correct = SafeArithmeticEvaluator
                .tryVerifyArithmeticStatement("(2+3) \\\\\\* 1.5 = 7.5")
                .orElseThrow();
        SafeArithmeticEvaluator.EquationVerification incorrect = SafeArithmeticEvaluator
                .tryVerifyArithmeticStatement("(2+3)*1.5 = 8")
                .orElseThrow();

        assertTrue(correct.matches());
        assertEquals("7.5", correct.actualValue());
        assertFalse(incorrect.matches());
        assertEquals("7.5", incorrect.actualValue());
        assertTrue(SafeArithmeticEvaluator.tryVerifyArithmeticStatement("2x + 3 = 11").isEmpty());
    }
}
