package dev.dong4j.idea.skill.inspector.test;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiFile;

import dev.dong4j.idea.skill.inspector.model.SkillBody;
import dev.dong4j.idea.skill.inspector.model.SkillFile;
import dev.dong4j.idea.skill.inspector.model.SkillFrontMatter;
import dev.dong4j.idea.skill.inspector.model.SkillMetadata;
import dev.dong4j.idea.skill.inspector.model.SkillYamlEntry;
import dev.dong4j.idea.skill.inspector.parser.FrontMatterParseResult;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * SkillFile 测试工厂
 * <p> 规则层只需要少量 PSI 信息, 因此用 Mockito 构造轻量 {@link SkillFile},
 * 避免普通单元测试启动完整 IntelliJ fixture.
 *
 * @author dong4j
 * @version 1.0.0
 * @email "mailto:dong4j@gmail.com"
 * @date 2026.05.23
 * @since 1.0.0
 */
public final class SkillFileTestFactory {

    /** 私有构造函数, 防止工具类被实例化 */
    private SkillFileTestFactory() {
    }

    /**
     * 创建无真实目录的 SkillFile
     *
     * @param text               SKILL.md 文本
     * @param skillDirectoryName 父目录名
     * @return SkillFile 测试模型
     */
    @NotNull
    public static SkillFile skillFile(@NotNull String text, @NotNull String skillDirectoryName) {
        return skillFile(text, skillDirectoryName, null);
    }

    /**
     * 创建带真实目录路径的 SkillFile
     *
     * @param text               SKILL.md 文本
     * @param skillDirectoryName 父目录名
     * @param skillDirectoryPath 父目录路径, 可为空
     * @return SkillFile 测试模型
     */
    @NotNull
    public static SkillFile skillFile(@NotNull String text,
                                      @NotNull String skillDirectoryName,
                                      @Nullable Path skillDirectoryPath) {
        PsiFile psiFile = mock(PsiFile.class);
        when(psiFile.getName()).thenReturn("SKILL.md");
        when(psiFile.getText()).thenReturn(text);
        when(psiFile.getTextLength()).thenReturn(text.length());

        VirtualFile skillDirectory = null;
        if (skillDirectoryPath != null) {
            skillDirectory = mock(VirtualFile.class);
            when(skillDirectory.getName()).thenReturn(skillDirectoryName);
            when(skillDirectory.getPath()).thenReturn(skillDirectoryPath.toString());
        }

        FrontMatterParseResult parseResult = parseFrontMatterForRuleTests(text);
        return new SkillFile(
            psiFile,
            skillDirectoryName,
            skillDirectory,
            parseResult.frontMatter(),
            parseResult.body()
        );
    }

    /**
     * 为普通规则单元测试构建 frontmatter 模型
     * <p> 生产代码已经通过 Markdown/YAML PSI 解析 frontmatter。规则单元测试只需要稳定的领域模型输入,
     * 因此这里保留一个测试专用构造器, 避免每个规则测试都启动完整 IDE fixture。
     */
    @NotNull
    private static FrontMatterParseResult parseFrontMatterForRuleTests(@NotNull String text) {
        if (!text.startsWith("---\n")) {
            return new FrontMatterParseResult(null, new SkillBody(text, 0, text.length()));
        }

        int contentStartOffset = 4;
        int closingStartOffset = text.indexOf("\n---", contentStartOffset);
        if (closingStartOffset < 0) {
            SkillFrontMatter frontMatter = new SkillFrontMatter(
                0,
                text.length(),
                contentStartOffset,
                text.length(),
                Map.of(),
                List.of(),
                SkillMetadata.from(List.of()),
                "Missing closing frontmatter delimiter"
            );
            return new FrontMatterParseResult(frontMatter, new SkillBody("", text.length(), text.length()));
        }

        int closingEndOffset = closingStartOffset + 4;
        if (closingEndOffset < text.length() && text.charAt(closingEndOffset) == '\n') {
            closingEndOffset++;
        }

        String yamlText = text.substring(contentStartOffset, closingStartOffset);
        List<SkillYamlEntry> entries = parseEntries(yamlText, contentStartOffset);
        Map<String, String> values = new LinkedHashMap<>();
        for (SkillYamlEntry entry : entries) {
            values.put(entry.key(), entry.value());
        }
        SkillFrontMatter frontMatter = new SkillFrontMatter(
            0,
            closingEndOffset,
            contentStartOffset,
            closingStartOffset,
            values,
            entries,
            SkillMetadata.from(entries),
            null
        );
        return new FrontMatterParseResult(frontMatter, new SkillBody(text.substring(closingEndOffset), closingEndOffset, text.length()));
    }

    /**
     * 解析测试文本中的顶层 YAML 字段
     */
    @NotNull
    private static List<SkillYamlEntry> parseEntries(@NotNull String yamlText, int contentStartOffset) {
        List<SkillYamlEntry> entries = new ArrayList<>();
        String[] lines = yamlText.split("\n", -1);
        int offset = contentStartOffset;
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#") || Character.isWhitespace(line.charAt(0))) {
                offset += line.length() + 1;
                continue;
            }
            int colon = line.indexOf(':');
            if (colon <= 0) {
                offset += line.length() + 1;
                continue;
            }
            String key = line.substring(0, colon).trim();
            String rawValue = line.substring(colon + 1).trim();
            String value = stripSimpleQuotes(rawValue);
            int valueStart = offset + colon + 1 + firstNonWhitespaceIndex(line.substring(colon + 1));
            int valueEnd = valueStart + rawValue.length();
            if (rawValue.equals(">") || rawValue.equals("|")) {
                StringBuilder blockValue = new StringBuilder();
                int blockEnd = valueEnd;
                while (i + 1 < lines.length && (lines[i + 1].isBlank() || Character.isWhitespace(lines[i + 1].charAt(0)))) {
                    i++;
                    offset += line.length() + 1;
                    line = lines[i];
                    String blockLine = line.trim();
                    if (!blockLine.isEmpty()) {
                        if (!blockValue.isEmpty()) {
                            blockValue.append(rawValue.equals(">") ? ' ' : '\n');
                        }
                        blockValue.append(blockLine);
                        blockEnd = offset + line.length();
                    }
                }
                value = blockValue.toString();
                valueEnd = blockEnd;
            }
            entries.add(new SkillYamlEntry(key, value, TextRange.create(offset, offset + key.length()), TextRange.create(valueStart, valueEnd)));
            offset += line.length() + 1;
        }
        return entries;
    }

    /**
     * 查找首个非空白字符
     */
    private static int firstNonWhitespaceIndex(@NotNull String text) {
        for (int i = 0; i < text.length(); i++) {
            if (!Character.isWhitespace(text.charAt(i))) {
                return i;
            }
        }
        return 0;
    }

    /**
     * 去掉测试 YAML 中的简单引号
     */
    @NotNull
    private static String stripSimpleQuotes(@NotNull String value) {
        if (value.length() >= 2) {
            char first = value.charAt(0);
            char last = value.charAt(value.length() - 1);
            if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
                return value.substring(1, value.length() - 1);
            }
        }
        return value;
    }
}
