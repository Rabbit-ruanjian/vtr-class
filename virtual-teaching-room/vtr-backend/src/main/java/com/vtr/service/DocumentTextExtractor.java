package com.vtr.service;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.hslf.usermodel.HSLFShape;
import org.apache.poi.hslf.usermodel.HSLFSlide;
import org.apache.poi.hslf.usermodel.HSLFSlideShow;
import org.apache.poi.hslf.usermodel.HSLFTextShape;
import org.apache.poi.xslf.usermodel.XSLFShape;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTextShape;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;

@Service
public class DocumentTextExtractor {

    /**
     * 课程教材通常远大于几万字。这里保存完整文本，RAG 查询时再只取相关片段，
     * 避免把一本公开教材截成开头几页后才入库。
     */
    private static final int MAX_EXTRACTED_CHARS = 500_000;
    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of("pdf", "doc", "docx", "ppt", "pptx", "txt", "md");

    public String extract(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IOException("文档不能为空");
        }

        String extension = extensionOf(file.getOriginalFilename());
        if (!SUPPORTED_EXTENSIONS.contains(extension)) {
            throw new IOException("暂不支持该文档格式，目前支持 PDF、DOC、DOCX、PPT、PPTX、TXT、MD");
        }

        String text;
        switch (extension) {
            case "pdf" -> text = extractPdf(file);
            case "doc" -> text = extractDoc(file);
            case "docx" -> text = extractDocx(file);
            case "ppt" -> text = extractPpt(file);
            case "pptx" -> text = extractPptx(file);
            case "txt", "md" -> text = extractPlainText(file);
            default -> throw new IOException("暂不支持该文档格式");
        }

        String normalized = normalize(text);
        if (!StringUtils.hasText(normalized)) {
            throw new IOException("文档中没有提取到可识别文字；扫描版 PDF 需要后续接入 OCR");
        }
        return limit(normalized);
    }

    /** 从已经保存在 uploads 目录中的课件文件抽取文本，供上传事件异步建索引。 */
    public String extract(Path path, String originalFilename) throws IOException {
        if (path == null || !Files.isRegularFile(path)) throw new IOException("文档文件不存在");
        String extension = extensionOf(originalFilename);
        if (!SUPPORTED_EXTENSIONS.contains(extension)) {
            throw new IOException("暂不支持该文档格式，目前支持 PDF、DOC、DOCX、PPT、PPTX、TXT、MD");
        }
        String text;
        switch (extension) {
            case "pdf" -> {
                try (PDDocument document = Loader.loadPDF(path.toFile())) {
                    text = new PDFTextStripper().getText(document);
                }
            }
            case "doc" -> {
                try (InputStream input = Files.newInputStream(path); HWPFDocument document = new HWPFDocument(input);
                     WordExtractor extractor = new WordExtractor(document)) {
                    text = extractor.getText();
                }
            }
            case "docx" -> {
                try (InputStream input = Files.newInputStream(path); XWPFDocument document = new XWPFDocument(input);
                     XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
                    text = extractor.getText();
                }
            }
            case "ppt" -> {
                try (InputStream input = Files.newInputStream(path); HSLFSlideShow document = new HSLFSlideShow(input)) {
                    text = extractPptText(document);
                }
            }
            case "pptx" -> {
                try (InputStream input = Files.newInputStream(path); XMLSlideShow document = new XMLSlideShow(input)) {
                    text = extractPptxText(document);
                }
            }
            case "txt", "md" -> text = decode(Files.readAllBytes(path), StandardCharsets.UTF_8);
            default -> throw new IOException("暂不支持该文档格式");
        }
        String normalized = normalize(text);
        if (!StringUtils.hasText(normalized)) {
            throw new IOException("文档中没有提取到可识别文字；扫描版 PDF 需要 OCR 后再建立索引");
        }
        return limit(normalized);
    }

    /** 提取 SRT/VTT/TXT 字幕正文并移除序号和时间轴。 */
    public String extractSubtitle(Path path) throws IOException {
        if (path == null || !Files.isRegularFile(path)) return "";
        String raw = decode(Files.readAllBytes(path), StandardCharsets.UTF_8);
        StringBuilder result = new StringBuilder();
        for (String line : normalize(raw).split("\\n")) {
            String value = line.trim();
            if (!StringUtils.hasText(value) || "WEBVTT".equalsIgnoreCase(value)
                    || value.matches("\\d+")
                    || value.matches(".*\\d{1,2}:\\d{2}(?::\\d{2})?[.,]\\d{3}\\s*-->.*")) {
                continue;
            }
            result.append(value.replaceAll("<[^>]+>", "")).append('\n');
        }
        return limit(result.toString().trim());
    }

    private String extractPdf(MultipartFile file) throws IOException {
        try (PDDocument document = Loader.loadPDF(file.getBytes())) {
            return new PDFTextStripper().getText(document);
        }
    }

    private String extractDocx(MultipartFile file) throws IOException {
        try (InputStream input = file.getInputStream(); XWPFDocument document = new XWPFDocument(input);
             XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
            return extractor.getText();
        }
    }

    private String extractDoc(MultipartFile file) throws IOException {
        try (InputStream input = file.getInputStream(); HWPFDocument document = new HWPFDocument(input);
             WordExtractor extractor = new WordExtractor(document)) {
            return extractor.getText();
        }
    }

    private String extractPpt(MultipartFile file) throws IOException {
        try (InputStream input = file.getInputStream(); HSLFSlideShow document = new HSLFSlideShow(input)) {
            return extractPptText(document);
        }
    }

    private String extractPptx(MultipartFile file) throws IOException {
        try (InputStream input = file.getInputStream(); XMLSlideShow document = new XMLSlideShow(input)) {
            return extractPptxText(document);
        }
    }

    private String extractPptText(HSLFSlideShow document) {
        StringBuilder text = new StringBuilder();
        int slideNumber = 1;
        for (HSLFSlide slide : document.getSlides()) {
            text.append("\n[第").append(slideNumber++).append("页]\n");
            for (HSLFShape shape : slide.getShapes()) {
                if (shape instanceof HSLFTextShape textShape && StringUtils.hasText(textShape.getText())) {
                    text.append(textShape.getText()).append('\n');
                }
            }
        }
        return text.toString();
    }

    private String extractPptxText(XMLSlideShow document) {
        StringBuilder text = new StringBuilder();
        int slideNumber = 1;
        for (XSLFSlide slide : document.getSlides()) {
            text.append("\n[第").append(slideNumber++).append("页]\n");
            for (XSLFShape shape : slide.getShapes()) {
                if (shape instanceof XSLFTextShape textShape && StringUtils.hasText(textShape.getText())) {
                    text.append(textShape.getText()).append('\n');
                }
            }
        }
        return text.toString();
    }

    private String extractPlainText(MultipartFile file) throws IOException {
        byte[] bytes = file.getBytes();
        return decode(bytes, StandardCharsets.UTF_8);
    }

    private String decode(byte[] bytes, Charset preferred) {
        for (Charset charset : new Charset[]{preferred, Charset.forName("GB18030")}) {
            CharsetDecoder decoder = charset.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT);
            try {
                CharBuffer decoded = decoder.decode(ByteBuffer.wrap(bytes));
                return decoded.toString();
            } catch (CharacterCodingException ignored) {
                // 尝试下一种常见编码。
            }
        }
        return new String(bytes, preferred);
    }

    private String normalize(String text) {
        return (text == null ? "" : text)
                .replace("\u0000", "")
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .trim();
    }

    private String limit(String text) {
        if (text.length() <= MAX_EXTRACTED_CHARS) {
            return text;
        }
        String marker = "\n\n[文档内容较长，已截取前 " + MAX_EXTRACTED_CHARS + " 个字符]";
        int contentLength = Math.max(0, MAX_EXTRACTED_CHARS - marker.length());
        return text.substring(0, contentLength) + marker;
    }

    private String extensionOf(String filename) {
        if (filename == null) {
            return "";
        }
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            return "";
        }
        return filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
