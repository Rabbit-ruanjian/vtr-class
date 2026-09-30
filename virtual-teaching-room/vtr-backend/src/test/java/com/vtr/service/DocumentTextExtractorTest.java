package com.vtr.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DocumentTextExtractorTest {

    private final DocumentTextExtractor extractor = new DocumentTextExtractor();

    @Test
    void extractsSubtitleTextWithoutTimelineAndMarkup(@TempDir Path tempDir) throws Exception {
        Path subtitle = tempDir.resolve("lesson.srt");
        Files.writeString(subtitle,
                "1\n00:00:01,000 --> 00:00:03,000\n"
                        + "<i>页表</i>负责把虚拟地址映射到物理地址。\n\n"
                        + "2\n00:00:04,000 --> 00:00:06,000\n"
                        + "发生缺页时，操作系统会进入缺页处理流程。\n",
                StandardCharsets.UTF_8);

        String text = extractor.extractSubtitle(subtitle);

        assertTrue(text.contains("页表负责把虚拟地址映射到物理地址。"));
        assertTrue(text.contains("发生缺页时，操作系统会进入缺页处理流程。"));
        assertFalse(text.contains("00:00:01"));
        assertFalse(text.contains("<i>"));
    }

    @Test
    void extractsPlainTextCoursewareFromStoredPath(@TempDir Path tempDir) throws Exception {
        Path document = tempDir.resolve("operating-system.md");
        Files.writeString(document, "# 进程\n进程是程序的一次执行。", StandardCharsets.UTF_8);

        String text = extractor.extract(document, document.getFileName().toString());

        assertTrue(text.contains("进程"));
        assertTrue(text.contains("程序的一次执行"));
    }
}
