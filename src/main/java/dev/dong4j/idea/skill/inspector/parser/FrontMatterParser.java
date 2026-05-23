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
     * @param psiFile Markdown PSI 文件
     * @return frontmatter 和正文解析结果
     */
    @NotNull
    public static FrontMatterParseResult parse(@NotNull PsiFile psiFile) {
        String text = psiFile.getText();
        PsiElement frontMatterElement = findFrontMatterElement(psiFile);
        int offsetAdjustment = 0;
        if (frontMatterElement == null && text.startsWith("\uFEFF")) {
            PsiFile withoutBom = PsiFileFactory.getInstance(psiFile.getProject())
                .createFileFromText("SKILL.md", psiFile.getLanguage(), text.substring(1));
            frontMatterElement = findFrontMatterElement(withoutBom);
            offsetAdjustment = 1;
        }
        if (frontMatterElement == null) {
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
     * 查找 Markdown PSI 中的 frontmatter 节点
     */
    private static PsiElement findFrontMatterElement(@NotNull PsiElement element) {
        ASTNode node = element.getNode();
        if (node != null) {
            String elementType = node.getElementType().toString().toUpperCase(Locale.ROOT);
            if (elementType.contains("FRONT_MATTER")) {
                return element;
            }
        }
        for (PsiElement child : element.getChildren()) {
            PsiElement frontMatter = findFrontMatterElement(child);
            if (frontMatter != null) {
                return frontMatter;
            }
        }
        return null;
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
