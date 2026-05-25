package org.javaup.ai.manage.service.impl;

import cn.hutool.core.util.StrUtil;
import org.apache.tika.Tika;
import org.javaup.ai.manage.service.DocumentParserService;
import org.javaup.ai.manage.support.DocumentAnalysisResult;
import org.javaup.ai.manage.support.DocumentLineClassifier;
import org.javaup.ai.manage.support.DocumentStructureNodeCandidate;
import org.javaup.ai.manage.support.DocumentStructureNodeExtractor;
import org.javaup.enums.DocumentContentQualityLevelEnum;
import org.javaup.enums.DocumentFileTypeEnum;
import org.javaup.enums.DocumentStructureLevelEnum;
import org.javaup.enums.DocumentStructureNodeTypeEnum;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 
 * @description: 服务实现层

 **/

@Service
public class TikaDocumentParserService implements DocumentParserService {

    private final Tika tika = new Tika();
    private final DocumentLineClassifier documentLineClassifier;
    private final DocumentStructureNodeExtractor structureNodeExtractor;

    public TikaDocumentParserService(DocumentLineClassifier documentLineClassifier,
                                     DocumentStructureNodeExtractor structureNodeExtractor) {
        this.documentLineClassifier = documentLineClassifier;
        this.structureNodeExtractor = structureNodeExtractor;
    }

    @Override

    public DocumentAnalysisResult parse(byte[] bytes, String originalFileName, String mimeType, DocumentFileTypeEnum fileType) {

        //原始文本提取
        //利用 Apache Tika 引擎读取文件的字节流。Tika 会根据文件类型（PDF, DOCX, TXT 等）自动选择对应的解析器，把隐藏在格式背后的文字“抠”出来。
        String rawText = extractRawText(bytes, originalFileName, mimeType, fileType);

        //文本清理
        //去除解析过程中产生的乱码、多余的空行、不可见的控制字符或页眉页脚噪音。这一步是为了保证后续 AI 分析和切块的质量。
        String cleanedText = cleanupText(rawText);

        //结构节点提取
        //这是实现“智能 RAG”的关键。它会分析清洗后的文本，识别出哪些是标题（H1-H6）、哪些是列表项、哪些是普通段落，并构建出一个层级化的结构树。
        //统计：同时统计标题的数量，用于评估文档的结构化程度。
        List<DocumentStructureNodeCandidate> structureNodes = structureNodeExtractor.extract(originalFileName, cleanedText);
        int headingCount = countHeadings(cleanedText, structureNodes);

        List<String> paragraphList = extractParagraphs(cleanedText);

        int maxParagraphLength = paragraphList.stream().mapToInt(String::length).max().orElse(0);

        int charCount = cleanedText.length();

        int tokenCount = estimateTokenCount(cleanedText);
        //质量评估：
        //structureLevel：根据标题数量判断文档是否有清晰的目录结构。
        //contentQualityLevel：根据文本长度和内容密度判断文档是否值得被索引。
        int structureLevel = evaluateStructureLevel(headingCount, paragraphList.size());

        int contentQualityLevel = evaluateContentQuality(cleanedText, charCount);

        return new DocumentAnalysisResult(
            cleanedText,
            charCount,
            tokenCount,
            structureLevel,
            contentQualityLevel,
            headingCount,
            paragraphList.size(),
            maxParagraphLength,
            structureNodes
        );
    }

    private String extractRawText(byte[] bytes, String originalFileName, String mimeType, DocumentFileTypeEnum fileType) {
        try {
            if (fileType == DocumentFileTypeEnum.PDF || fileType == DocumentFileTypeEnum.DOC || fileType == DocumentFileTypeEnum.DOCX) {

                return tika.parseToString(new ByteArrayInputStream(bytes));
            }
            if (fileType == DocumentFileTypeEnum.TXT || fileType == DocumentFileTypeEnum.MD) {

                return new String(bytes, StandardCharsets.UTF_8);
            }
            if (fileType == DocumentFileTypeEnum.HTML) {

                return tika.parseToString(new ByteArrayInputStream(bytes));
            }

            return tika.parseToString(new ByteArrayInputStream(bytes));
        }
        catch (Exception exception) {

            if (fileType == DocumentFileTypeEnum.TXT || fileType == DocumentFileTypeEnum.MD || mimeType != null && mimeType.startsWith("text/")) {
                return new String(bytes, StandardCharsets.UTF_8);
            }
            throw new IllegalStateException("Tika 解析失败: " + exception.getMessage(), exception);
        }
    }

    private String cleanupText(String rawText) {
        if (StrUtil.isBlank(rawText)) {
            return "";
        }

        String cleaned = rawText
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .replace('\u0000', ' ')
            .replaceAll("[\\t\\x0B\\f]+", " ")
            .replaceAll("\\n{3,}", "\n\n")
            .replaceAll("[ ]{2,}", " ")
            .trim();
        return cleaned;
    }

    private int countHeadings(String text,
                              List<DocumentStructureNodeCandidate> structureNodes) {
        if (structureNodes != null && !structureNodes.isEmpty()) {
            long structuredHeadingCount = structureNodes.stream()
                .filter(node -> node != null
                    && DocumentStructureNodeTypeEnum.SECTION.getCode().equals(node.getNodeType())
                    && node.getDepth() != null
                    && node.getDepth() > 0)
                .count();
            if (structuredHeadingCount > 0) {
                return (int) structuredHeadingCount;
            }
        }
        int count = 0;
        for (String line : text.split("\n")) {

            if (documentLineClassifier.classify(line).isHeading()) {
                count++;
            }
        }
        return count;
    }

    private List<String> extractParagraphs(String text) {
        List<String> paragraphList = new ArrayList<>();
        for (String paragraph : text.split("\\n\\s*\\n")) {
            String trimmed = paragraph.trim();
            if (StrUtil.isNotBlank(trimmed)) {

                paragraphList.add(trimmed);
            }
        }
        return paragraphList;
    }

    private int estimateTokenCount(String text) {
        int englishWordCount = 0;
        int chineseCharCount = 0;

        for (String word : text.split("\\s+")) {
            if (word.matches(".*[A-Za-z].*")) {
                englishWordCount++;
            }
        }

        for (char current : text.toCharArray()) {
            if (String.valueOf(current).matches("[\\u4e00-\\u9fa5]")) {
                chineseCharCount++;
            }
        }

        return englishWordCount + chineseCharCount + Math.max(1, (text.length() - chineseCharCount) / 4);
    }

    private int evaluateStructureLevel(int headingCount, int paragraphCount) {

        if (headingCount >= 5) {
            return DocumentStructureLevelEnum.HIGH.getCode();
        }
        if (headingCount >= 2) {
            return DocumentStructureLevelEnum.MEDIUM.getCode();
        }
        if (paragraphCount >= 3) {
            return DocumentStructureLevelEnum.LOW.getCode();
        }
        return DocumentStructureLevelEnum.UNKNOWN.getCode();
    }

    private int evaluateContentQuality(String text, int charCount) {
        if (StrUtil.isBlank(text) || charCount < 20) {

            return DocumentContentQualityLevelEnum.LOW.getCode();
        }

        long brokenCharCount = text.chars().filter(value -> value == '�').count();
        double brokenRatio = charCount == 0 ? 1D : (double) brokenCharCount / (double) charCount;
        if (brokenRatio > 0.02D || charCount < 100) {

            return DocumentContentQualityLevelEnum.LOW.getCode();
        }
        if (brokenRatio > 0.005D || charCount < 500) {

            return DocumentContentQualityLevelEnum.MEDIUM.getCode();
        }

        return DocumentContentQualityLevelEnum.HIGH.getCode();
    }
}
