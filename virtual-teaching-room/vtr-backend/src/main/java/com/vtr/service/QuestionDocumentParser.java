package com.vtr.service;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Converts a teaching question document into editable question drafts. */
@Service
public class QuestionDocumentParser {
    private static final Pattern QUESTION = Pattern.compile("^\\s*(?:第\\s*)?(\\d{1,4})\\s*[.．、)）:]\\s*(.*)$");
    private static final Pattern OPTION = Pattern.compile("^\\s*([A-Ha-h])\\s*[.．、)）:]\\s*(.*)$");
    private static final Pattern ANSWER = Pattern.compile("^\\s*(?:【\\s*)?(?:参考答案|正确答案|答案|答)(?:\\s*】|\\s*[:：])\\s*(.*)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern ANALYSIS = Pattern.compile("^\\s*(?:【\\s*)?(?:答案解析|解析)(?:\\s*】|\\s*[:：])\\s*(.*)$", Pattern.CASE_INSENSITIVE);

    public List<Map<String, String>> parse(MultipartFile file) throws IOException {
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase(Locale.ROOT);
        String text;
        if (name.endsWith(".pdf")) {
            try (var document = Loader.loadPDF(file.getBytes())) {
                text = new PDFTextStripper().getText(document);
            }
        } else if (name.endsWith(".docx")) {
            text = readDocx(file);
        } else if (name.endsWith(".txt") || name.endsWith(".md")) {
            text = new String(file.getBytes(), java.nio.charset.StandardCharsets.UTF_8);
        } else {
            throw new IllegalArgumentException("题库导入仅支持 PDF、DOCX、TXT、MD 文件");
        }
        return parseText(text);
    }

    private String readDocx(MultipartFile file) throws IOException {
        StringBuilder text = new StringBuilder();
        try (XWPFDocument document = new XWPFDocument(file.getInputStream())) {
            for (XWPFParagraph paragraph : document.getParagraphs()) {
                String line = paragraph.getText();
                if (line != null && !line.isBlank()) text.append(line.trim()).append('\n');
            }
        }
        return text.toString();
    }

    private List<Map<String, String>> parseText(String rawText) {
        List<Map<String, String>> result = new ArrayList<>();
        Map<String, String> current = null;
        List<String> options = new ArrayList<>();
        StringBuilder stem = new StringBuilder();
        StringBuilder analysis = new StringBuilder();
        StringBuilder answer = new StringBuilder();
        int currentNumber = 0;
        boolean readingAnswer = false;
        boolean readingAnalysis = false;

        for (String rawLine : rawText.replace('\u00a0', ' ').split("\\R")) {
            String line = rawLine.trim();
            if (line.isBlank()) continue;
            Matcher questionMatcher = QUESTION.matcher(line);
            if (questionMatcher.matches()) {
                int number = Integer.parseInt(questionMatcher.group(1));
                // 简答题答案可能是“1. … 2. …”的多行内容，只有遇到比当前题号更大的题号时，才开始下一题。
                if (current != null && (!readingAnswer || number > currentNumber)) finish(result, current, stem, options, answer, analysis);
                else if (current != null && readingAnswer) {
                    append(answer, line);
                    continue;
                }
                current = new LinkedHashMap<>();
                currentNumber = number;
                current.put("title", "第" + number + "题");
                stem.setLength(0); options.clear(); answer.setLength(0); analysis.setLength(0); readingAnalysis = false;
                readingAnswer = false;
                append(stem, questionMatcher.group(2));
                continue;
            }
            if (current == null) continue;
            Matcher optionMatcher = OPTION.matcher(line);
            if (!readingAnswer && optionMatcher.matches()) {
                options.add(optionMatcher.group(1).toUpperCase(Locale.ROOT) + ". " + optionMatcher.group(2).trim());
                readingAnalysis = false;
                continue;
            }
            Matcher answerMatcher = ANSWER.matcher(line);
            if (answerMatcher.matches()) {
                answer.setLength(0);
                append(answer, answerMatcher.group(1));
                readingAnswer = true;
                readingAnalysis = false;
                continue;
            }
            Matcher analysisMatcher = ANALYSIS.matcher(line);
            if (analysisMatcher.matches()) {
                append(analysis, analysisMatcher.group(1));
                readingAnswer = false;
                readingAnalysis = true;
                continue;
            }
            if (readingAnalysis) append(analysis, line);
            else if (readingAnswer) append(answer, line);
            else append(stem, line);
        }
        if (current != null) finish(result, current, stem, options, answer, analysis);
        return result;
    }

    private void finish(List<Map<String, String>> result, Map<String, String> item,
                        StringBuilder stem, List<String> options, StringBuilder answer, StringBuilder analysis) {
        String stemText = stem.toString().trim();
        if (stemText.isBlank()) return;
        String optionText = String.join("\n", options);
        String normalizedAnswer = answer.toString().trim();
        String type = inferType(optionText, normalizedAnswer);
        item.put("stem", stemText);
        item.put("options", optionText);
        item.put("referenceAnswer", normalizedAnswer);
        String normalizedAnalysis = analysis.toString().trim();
        item.put("analysis", normalizedAnalysis.isBlank() && "TEXT".equals(type) ? normalizedAnswer : normalizedAnalysis);
        item.put("questionType", type);
        item.put("difficulty", "3");
        item.put("knowledgePoint", "");
        result.add(item);
    }

    private String inferType(String options, String answer) {
        String value = answer == null ? "" : answer.trim();
        if (options.isBlank() && ("对".equals(value) || "错".equals(value) || "正确".equals(value) || "错误".equals(value))) return "JUDGMENT";
        if (!options.isBlank() && value.matches(".*[,，、/\\s].*")) return "MULTIPLE_CHOICE";
        if (!options.isBlank()) return "SINGLE_CHOICE";
        return "TEXT";
    }

    private void append(StringBuilder target, String value) {
        if (value == null || value.isBlank()) return;
        if (target.length() > 0) target.append('\n');
        target.append(value.trim());
    }

}
