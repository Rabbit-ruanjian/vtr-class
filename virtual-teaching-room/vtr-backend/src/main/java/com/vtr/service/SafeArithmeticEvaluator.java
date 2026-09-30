package com.vtr.service;

import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.Optional;

/**
 * 面向教学问答的受限基础算术计算器。
 * 仅接受数字、小数点、括号和 + - * / ^，绝不把用户输入交给脚本引擎执行。
 */
final class SafeArithmeticEvaluator {

    private static final int MAX_EXPRESSION_LENGTH = 200;
    private static final int MAX_NUMBER_PRECISION = 80;
    private static final int MAX_ABSOLUTE_EXPONENT = 1_000;
    private static final MathContext MATH_CONTEXT = new MathContext(16, RoundingMode.HALF_UP);

    private SafeArithmeticEvaluator() {
    }

    static Optional<CalculationResult> tryEvaluateQuestion(String question) {
        String expression = extractDirectExpression(question);
        if (!StringUtils.hasText(expression)) {
            return Optional.empty();
        }
        return tryEvaluateExpression(expression)
                .map(value -> new CalculationResult(expression, format(value)));
    }

    /** 仅核验“纯数字算式 = 数字结果”，不处理含变量的方程。 */
    static Optional<EquationVerification> tryVerifyArithmeticStatement(String statement) {
        String candidate = normalizeQuestion(statement).replaceAll("[？?！!。.]$", "");
        int equalsIndex = candidate.indexOf('=');
        if (equalsIndex <= 0 || equalsIndex != candidate.lastIndexOf('=')) {
            return Optional.empty();
        }

        String expression = candidate.substring(0, equalsIndex).replaceAll("\\s+", "");
        String claimedValue = candidate.substring(equalsIndex + 1).trim();
        if (!expression.matches("[0-9+\\-*/^().]+")
                || !claimedValue.matches("[-+]?\\d+(?:\\.\\d+)?")) {
            return Optional.empty();
        }

        Optional<BigDecimal> actualValue = tryEvaluateExpression(expression);
        try {
            BigDecimal expectedValue = new BigDecimal(claimedValue, MATH_CONTEXT);
            if (expectedValue.precision() > MAX_NUMBER_PRECISION) {
                return Optional.empty();
            }
            return actualValue.map(value -> new EquationVerification(
                    expression,
                    format(expectedValue),
                    format(value),
                    value.compareTo(expectedValue) == 0
            ));
        } catch (NumberFormatException exception) {
            return Optional.empty();
        }
    }

    private static String extractDirectExpression(String question) {
        String candidate = normalizeQuestion(question)
                .replaceAll("(?:[，,]\\s*)?(?:并)?(?:请)?(?:写出|说明|展示|给出)?(?:详细)?(?:计算)?(?:步骤|过程|推导|解释).*$", "")
                .replaceAll("(?:的结果|结果是多少|等于多少|是多少)[？?！!。.]?$", "")
                .replaceAll("[？?！!。.]$", "")
                .trim();
        if (candidate.length() > MAX_EXPRESSION_LENGTH
                || !candidate.matches("[0-9+\\-*/^().\\s]+")
                || !candidate.matches("(?s).*[-+*/^].*")) {
            return "";
        }
        return candidate.replaceAll("\\s+", "");
    }

    private static String normalizeQuestion(String question) {
        if (!StringUtils.hasText(question)) {
            return "";
        }
        return question.trim()
                // 聊天内容可能将 Markdown 中的乘号传为 \* 或 \\*，计算前恢复为普通乘号。
                .replaceAll("\\\\+\\*", "*")
                .replace('×', '*')
                .replace('÷', '/')
                .replaceAll("^(?:请|帮我|麻烦)?(?:计算|算一下|求值|帮我算|核验|验证)[：:]?\\s*", "")
                .trim();
    }

    private static Optional<BigDecimal> tryEvaluateExpression(String expression) {
        try {
            return Optional.of(new Parser(expression).parse());
        } catch (IllegalArgumentException | ArithmeticException exception) {
            return Optional.empty();
        }
    }

    private static String format(BigDecimal value) {
        BigDecimal normalized = value.compareTo(BigDecimal.ZERO) == 0
                ? BigDecimal.ZERO
                : value.stripTrailingZeros();
        return normalized.toPlainString();
    }

    record CalculationResult(String expression, String value) {
    }

    record EquationVerification(String expression, String claimedValue, String actualValue, boolean matches) {
    }

    private static final class Parser {
        private final String expression;
        private int position;

        private Parser(String expression) {
            this.expression = expression;
        }

        private BigDecimal parse() {
            BigDecimal value = parseExpression();
            if (position != expression.length()) {
                throw new IllegalArgumentException("存在无法识别的字符");
            }
            return value;
        }

        private BigDecimal parseExpression() {
            BigDecimal value = parseTerm();
            while (position < expression.length()) {
                if (consume('+')) {
                    value = value.add(parseTerm(), MATH_CONTEXT);
                } else if (consume('-')) {
                    value = value.subtract(parseTerm(), MATH_CONTEXT);
                } else {
                    return value;
                }
            }
            return value;
        }

        private BigDecimal parseTerm() {
            BigDecimal value = parseUnary();
            while (position < expression.length()) {
                if (consume('*')) {
                    value = value.multiply(parseUnary(), MATH_CONTEXT);
                } else if (consume('/')) {
                    BigDecimal divisor = parseUnary();
                    if (divisor.compareTo(BigDecimal.ZERO) == 0) {
                        throw new ArithmeticException("除数不能为零");
                    }
                    value = value.divide(divisor, MATH_CONTEXT);
                } else {
                    return value;
                }
            }
            return value;
        }

        private BigDecimal parseUnary() {
            if (consume('+')) {
                return parseUnary();
            }
            if (consume('-')) {
                return parseUnary().negate(MATH_CONTEXT);
            }
            return parsePower();
        }

        private BigDecimal parsePower() {
            BigDecimal base = parsePrimary();
            if (!consume('^')) {
                return base;
            }
            BigDecimal exponent = parseUnary();
            if (exponent.stripTrailingZeros().scale() > 0) {
                throw new IllegalArgumentException("指数必须是整数");
            }
            int exponentValue;
            try {
                exponentValue = exponent.intValueExact();
            } catch (ArithmeticException exception) {
                throw new IllegalArgumentException("指数范围过大");
            }
            if (Math.abs((long) exponentValue) > MAX_ABSOLUTE_EXPONENT) {
                throw new IllegalArgumentException("指数范围过大");
            }
            if (exponentValue >= 0) {
                return base.pow(exponentValue, MATH_CONTEXT);
            }
            if (base.compareTo(BigDecimal.ZERO) == 0) {
                throw new ArithmeticException("零不能使用负指数");
            }
            return BigDecimal.ONE.divide(base.pow(-exponentValue, MATH_CONTEXT), MATH_CONTEXT);
        }

        private BigDecimal parsePrimary() {
            if (consume('(')) {
                BigDecimal value = parseExpression();
                if (!consume(')')) {
                    throw new IllegalArgumentException("括号不匹配");
                }
                return value;
            }
            int start = position;
            boolean decimalPointSeen = false;
            while (position < expression.length()) {
                char character = expression.charAt(position);
                if (Character.isDigit(character)) {
                    position++;
                } else if (character == '.' && !decimalPointSeen) {
                    decimalPointSeen = true;
                    position++;
                } else {
                    break;
                }
            }
            if (start == position) {
                throw new IllegalArgumentException("缺少数字");
            }
            String number = expression.substring(start, position);
            if (".".equals(number)) {
                throw new IllegalArgumentException("数字格式错误");
            }
            BigDecimal value = new BigDecimal(number, MATH_CONTEXT);
            if (value.precision() > MAX_NUMBER_PRECISION) {
                throw new IllegalArgumentException("数字过大");
            }
            return value;
        }

        private boolean consume(char expected) {
            if (position < expression.length() && expression.charAt(position) == expected) {
                position++;
                return true;
            }
            return false;
        }
    }
}
