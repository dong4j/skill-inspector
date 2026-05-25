package dev.dong4j.idea.skill.inspector.parser;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiErrorElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;

import dev.dong4j.idea.skill.inspector.model.SkillBody;
import dev.dong4j.idea.skill.inspector.model.SkillFrontMatter;
import dev.dong4j.idea.skill.inspector.model.SkillMetadata;
import dev.dong4j.idea.skill.inspector.model.SkillYamlEntry;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.yaml.YAMLLanguage;
import org.jetbrains.yaml.psi.YAMLDocument;
import org.jetbrains.yaml.psi.YAMLFile;
import org.jetbrains.yaml.psi.YAMLKeyValue;
import org.jetbrains.yaml.psi.YAMLMapping;
import org.jetbrains.yaml.psi.YAMLScalar;
import org.jetbrains.yaml.psi.YAMLValue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * YAML frontmatter 解析器
 * <p> 生产路径复用 JetBrains Markdown PSI 定位 frontmatter, 再通过 YAML PSI 读取顶层字段。
 * 规则层只消费 {@link SkillMetadata}, 不直接理解 Markdown/YAML 的底层语法细节。
 *
 * @author dong4j
 * @version 1.0.0
 * @email "mailto:dong4j@gmail.com"
 * @date 2026.05.23
 * @since 1.0.0
 */
public final class FrontMatterParser {

    /** YAML frontmatter 分隔符 */
    private static final String DELIMITER = "---";

    /** 私有构造函数, 防止工具类被实例化 */
    private FrontMatterParser() {
    }

    /**
     * 解析完整 {@code SKILL.md} PSI 文件
     *
     * <p>双路径设计:
     * <ol>
     *   <li>优先用 JetBrains Markdown PSI 的 {@code FRONT_MATTER} 节点定位 frontmatter, 保留 PSI range 精度;</li>
     *   <li>PSI 识别失败时回退到纯文本扫描. 这是关键: 实际 IDE 中观察到 daemon 早期跑 inspection 时
     *       Markdown PSI 懒构建尚未完成, 或不同 Markdown plugin 版本对 frontmatter 节点命名/启用条件
     *       不一致 (FRONT_MATTER / FRONTMATTER_HEADER / 未识别), 会导致同一份 SKILL.md 在
     *       Inspection 和手动 Action 之间结果不一致. 纯文本扫描是确定性的, 消除该不一致.</li>
     * </ol>
     *
     * @param psiFile Markdown PSI 文件
     * @return frontmatter 和正文解析结果
     */
    @NotNull
    public static FrontMatterParseResult parse(@NotNull PsiFile psiFile) {
        String text = psiFile.getText();
        PsiElement frontMatterElement = findFrontMatterElement(psiFile, leadingFrontMatterOffset(text));
        int offsetAdjustment = 0;
        if (frontMatterElement == null && text.startsWith("\uFEFF")) {
            PsiFile withoutBom = PsiFileFactory.getInstance(psiFile.getProject())
                .createFileFromText("SKILL.md", psiFile.getLanguage(), text.substring(1));
            frontMatterElement = findFrontMatterElement(withoutBom, 0);
            offsetAdjustment = 1;
        }
        if (frontMatterElement == null) {
            // PSI 识别不到, 回退到纯文本扫描. 见方法 javadoc.
            FrontMatterParseResult fallback = parseFromText(psiFile, text);
            if (fallback != null) {
                return fallback;
            }
            return new FrontMatterParseResult(null, new SkillBody(text, 0, text.length()));
        }

        TextRange frontMatterRange = shift(frontMatterElement.getTextRange(), offsetAdjustment);
        FrontMatterContent content = extractFrontMatterContent(frontMatterElement, offsetAdjustment);
        if (content == null) {
            SkillFrontMatter frontMatter = emptyFrontMatter(frontMatterRange, delimiterError(frontMatterElement.getText()));
            return new FrontMatterParseResult(frontMatter, new SkillBody("", frontMatterRange.getEndOffset(), text.length()));
        }

        ParseEntriesResult entriesResult = parseYamlEntries(psiFile, content);
        SkillFrontMatter frontMatter = new SkillFrontMatter(
            frontMatterRange.getStartOffset(),
            frontMatterRange.getEndOffset(),
            content.startOffset(),
            content.endOffset(),
            entriesResult.values(),
            entriesResult.entries(),
            SkillMetadata.from(entriesResult.entries()),
            entriesResult.parseError()
        );
        int bodyStartOffset = skipLineBreak(text, frontMatterRange.getEndOffset());
        String bodyText = text.substring(bodyStartOffset);
        return new FrontMatterParseResult(
            frontMatter,
            new SkillBody(bodyText, bodyStartOffset, text.length())
        );
    }

    /**
     * 不依赖 Markdown PSI 的纯文本 frontmatter 解析回退路径.
     *
     * <p>判定规则:
     * <ul>
     *   <li>文本(去掉可选 BOM)必须以 {@code ---} 开头, 且 {@code ---} 后紧跟换行或文件结束;</li>
     *   <li>查找下一个**在行首**且独占一行的 {@code ---} 作为闭合分隔符;</li>
     *   <li>找不到闭合分隔符时, 返回带 parseError 的 frontMatter (沿用现有"缺闭合"语义),
     *       避免规则层把"明显是 frontmatter 但写错了"误判为"完全没 frontmatter".</li>
     * </ul>
     * YAML 内容部分仍走 {@link #parseYamlEntries}, 保留 YAML PSI 给出的 key/value 范围.
     *
     * @return 非 null 表示成功识别为 frontmatter 格式 (即使内部 YAML 有错); null 表示连分隔符都不匹配
     */
    private static FrontMatterParseResult parseFromText(@NotNull PsiFile psiFile, @NotNull String text) {
        int bomOffset = text.startsWith("\uFEFF") ? 1 : 0;
        int delim1Start = bomOffset;

        if (!text.startsWith(DELIMITER, delim1Start)) {
            return null;
        }
        int delim1End = delim1Start + DELIMITER.length();
        if (delim1End < text.length() && text.charAt(delim1End) != '\n' && text.charAt(delim1End) != '\r') {
            // `---xxxx` 这种不是 frontmatter 起始, 而可能是 thematic break + 文本
            return null;
        }

        int contentStart = skipLineBreak(text, delim1End);
        int delim2Start = findLineStartDelimiter(text, contentStart);
        if (delim2Start < 0) {
            TextRange range = TextRange.create(bomOffset, text.length());
            SkillFrontMatter frontMatter = emptyFrontMatter(range, "Missing closing frontmatter delimiter");
            return new FrontMatterParseResult(frontMatter, new SkillBody("", text.length(), text.length()));
        }

        int contentEnd = trimLineBreakBefore(text, delim2Start);
        int delim2End = delim2Start + DELIMITER.length();

        String yamlContent = text.substring(contentStart, contentEnd);
        FrontMatterContent content = new FrontMatterContent(yamlContent, contentStart, contentEnd);

        ParseEntriesResult entriesResult = parseYamlEntries(psiFile, content);
        SkillFrontMatter frontMatter = new SkillFrontMatter(
            delim1Start,
            delim2End,
            contentStart,
            contentEnd,
            entriesResult.values(),
            entriesResult.entries(),
            SkillMetadata.from(entriesResult.entries()),
            entriesResult.parseError()
        );
        int bodyStartOffset = skipLineBreak(text, delim2End);
        String bodyText = bodyStartOffset < text.length() ? text.substring(bodyStartOffset) : "";
        return new FrontMatterParseResult(
            frontMatter,
            new SkillBody(bodyText, bodyStartOffset, text.length())
        );
    }

    /**
     * 在 text 中从 startOffset 开始, 查找下一个**在行首**且作为整行(后面紧跟换行/EOF)的 {@code ---}.
     *
     * <p>必须是整行: 避免把 {@code ---abc} 这种 thematic break 误当成 frontmatter 闭合.
     *
     * @return 找到的 {@code ---} 起始偏移; 未找到返回 -1
     */
    private static int findLineStartDelimiter(@NotNull String text, int startOffset) {
        int searchFrom = startOffset;
        while (searchFrom < text.length()) {
            int idx = text.indexOf(DELIMITER, searchFrom);
            if (idx < 0) {
                return -1;
            }
            boolean atLineStart = (idx == 0) || text.charAt(idx - 1) == '\n' || text.charAt(idx - 1) == '\r';
            int after = idx + DELIMITER.length();
            boolean atLineEnd = (after >= text.length()) || text.charAt(after) == '\n' || text.charAt(after) == '\r';
            if (atLineStart && atLineEnd) {
                return idx;
            }
            searchFrom = idx + 1;
        }
        return -1;
    }

    /**
     * 查找 Markdown PSI 中位于文件开头的 frontmatter 节点.
     * <p>Markdown PSI 也可能在正文的 {@code ```markdown} 示例里识别出 FRONT_MATTER.
     * Skill 规范只允许文件级 YAML frontmatter, 所以这里只接受起始偏移等于文件开头
     * (或 BOM 之后) 的节点, 防止正文示例污染结构校验.
     */
    private static PsiElement findFrontMatterElement(@NotNull PsiElement element, int expectedStartOffset) {
        ASTNode node = element.getNode();
        if (node != null) {
            String elementType = node.getElementType().toString().toUpperCase(Locale.ROOT);
            if (elementType.contains("FRONT_MATTER")
                && element.getTextRange().getStartOffset() == expectedStartOffset) {
                return element;
            }
        }
        for (PsiElement child : element.getChildren()) {
            PsiElement frontMatter = findFrontMatterElement(child, expectedStartOffset);
            if (frontMatter != null) {
                return frontMatter;
            }
        }
        return null;
    }

    /**
     * 返回合法 frontmatter PSI 节点应处在的起始偏移.
     */
    private static int leadingFrontMatterOffset(@NotNull String text) {
        return text.startsWith("\uFEFF") ? 1 : 0;
    }

    /**
     * 提取 frontmatter 中的 YAML 内容范围
     */
    private static FrontMatterContent extractFrontMatterContent(@NotNull PsiElement frontMatterElement, int offsetAdjustment) {
        String text = frontMatterElement.getText();
        int firstDelimiterEnd = text.indexOf(DELIMITER);
        int lastDelimiterStart = text.lastIndexOf(DELIMITER);
        if (firstDelimiterEnd < 0 || lastDelimiterStart <= firstDelimiterEnd) {
            return null;
        }
        int contentStartInElement = firstDelimiterEnd + DELIMITER.length();
        contentStartInElement = skipLineBreak(text, contentStartInElement);
        int contentEndInElement = trimLineBreakBefore(text, lastDelimiterStart);
        TextRange frontMatterRange = frontMatterElement.getTextRange();
        return new FrontMatterContent(
            text.substring(contentStartInElement, contentEndInElement),
            frontMatterRange.getStartOffset() + contentStartInElement + offsetAdjustment,
            frontMatterRange.getStartOffset() + contentEndInElement + offsetAdjustment
        );
    }

    /**
     * 使用 YAML PSI 解析顶层字段
     */
    @NotNull
    private static ParseEntriesResult parseYamlEntries(@NotNull PsiFile sourceFile, @NotNull FrontMatterContent content) {
        Map<String, String> values = new LinkedHashMap<>();
        List<SkillYamlEntry> entries = new ArrayList<>();
        YAMLFile yamlFile = (YAMLFile) PsiFileFactory.getInstance(sourceFile.getProject())
            .createFileFromText("SKILL.frontmatter.yaml", YAMLLanguage.INSTANCE, content.text());
        String parseError = collectYamlError(yamlFile);
        for (YAMLDocument document : yamlFile.getDocuments()) {
            YAMLValue topLevelValue = document.getTopLevelValue();
            if (topLevelValue instanceof YAMLMapping mapping) {
                for (YAMLKeyValue keyValue : mapping.getKeyValues()) {
                    addEntry(values, entries, keyValue, content.startOffset());
                }
            }
        }
        return new ParseEntriesResult(values, entries, parseError);
    }

    /**
     * 将 YAML PSI key-value 转换为规则层字段模型
     */
    private static void addEntry(@NotNull Map<String, String> values,
                                 @NotNull List<SkillYamlEntry> entries,
                                 @NotNull YAMLKeyValue keyValue,
                                 int contentStartOffset) {
        String key = keyValue.getKeyText();
        YAMLValue valueElement = keyValue.getValue();
        String value = yamlValueText(valueElement);
        TextRange keyRange = offsetRange(keyValue.getKey(), contentStartOffset);
        TextRange valueRange = valueElement == null ? keyRange : offsetRange(valueElement, contentStartOffset);
        values.put(key, value);
        entries.add(new SkillYamlEntry(key, value, keyRange, valueRange));
    }

    /**
     * 读取 YAML value 的规范化文本
     */
    @NotNull
    private static String yamlValueText(YAMLValue valueElement) {
        if (valueElement == null) {
            return "";
        }
        if (valueElement instanceof YAMLScalar scalar) {
            return scalar.getTextValue().stripTrailing();
        }
        return valueElement.getText();
    }

    /**
     * 将 YAML PSI 相对范围转换为原始 Markdown 文件范围
     */
    @NotNull
    private static TextRange offsetRange(PsiElement element, int contentStartOffset) {
        TextRange range = element.getTextRange();
        return TextRange.create(contentStartOffset + range.getStartOffset(), contentStartOffset + range.getEndOffset());
    }

    /**
     * 去掉闭合分隔符前的换行符, 让 frontmatter 内容范围不包含最后一个空行
     */
    private static int trimLineBreakBefore(@NotNull String text, int offset) {
        if (offset >= 2 && text.charAt(offset - 2) == '\r' && text.charAt(offset - 1) == '\n') {
            return offset - 2;
        }
        if (offset >= 1 && (text.charAt(offset - 1) == '\n' || text.charAt(offset - 1) == '\r')) {
            return offset - 1;
        }
        return offset;
    }

    /**
     * 跳过 frontmatter 起始分隔符后的换行
     */
    private static int skipLineBreak(@NotNull String text, int offset) {
        if (offset < text.length() && text.charAt(offset) == '\r') {
            return offset + 1 < text.length() && text.charAt(offset + 1) == '\n' ? offset + 2 : offset + 1;
        }
        if (offset < text.length() && text.charAt(offset) == '\n') {
            return offset + 1;
        }
        return offset;
    }

    /**
     * 汇总 YAML PSI 错误
     */
    private static String collectYamlError(@NotNull PsiElement element) {
        if (element instanceof PsiErrorElement errorElement) {
            return errorElement.getErrorDescription();
        }
        for (PsiElement child : element.getChildren()) {
            String error = collectYamlError(child);
            if (error != null) {
                return error;
            }
        }
        return null;
    }

    /**
     * 生成 frontmatter 分隔符错误信息
     */
    @NotNull
    private static String delimiterError(@NotNull String frontMatterText) {
        return frontMatterText.trim().startsWith(DELIMITER) ? "Missing closing frontmatter delimiter" : "Invalid frontmatter delimiters";
    }

    /**
     * 将临时 PSI 文件范围平移回原始 Markdown 文件范围
     */
    @NotNull
    private static TextRange shift(@NotNull TextRange range, int offsetAdjustment) {
        return TextRange.create(range.getStartOffset() + offsetAdjustment, range.getEndOffset() + offsetAdjustment);
    }

    /**
     * 创建空 frontmatter 模型
     */
    @NotNull
    private static SkillFrontMatter emptyFrontMatter(@NotNull TextRange range, @NotNull String parseError) {
        return new SkillFrontMatter(
            range.getStartOffset(),
            range.getEndOffset(),
            range.getStartOffset(),
            range.getEndOffset(),
            Map.of(),
            List.of(),
            SkillMetadata.from(List.of()),
            parseError
        );
    }

    /**
     * 顶层 YAML 字段解析的内部结果
     */
    private record ParseEntriesResult(Map<String, String> values,
                                      List<SkillYamlEntry> entries,
                                      String parseError) {
    }

    /**
     * frontmatter 中 YAML 内容及其在 Markdown 文件中的范围
     */
    private record FrontMatterContent(String text,
                                      int startOffset,
                                      int endOffset) {
    }
}
