package dev.dong4j.idea.skill.inspector.rules;

import com.intellij.openapi.util.TextRange;

import dev.dong4j.idea.skill.inspector.model.SkillFile;
import dev.dong4j.idea.skill.inspector.model.SkillFrontMatter;
import dev.dong4j.idea.skill.inspector.model.SkillProblem;
import dev.dong4j.idea.skill.inspector.model.SkillSeverity;
import dev.dong4j.idea.skill.inspector.model.SkillYamlEntry;
import dev.dong4j.idea.skill.inspector.util.SkillInspectorBundle;
import dev.dong4j.idea.skill.inspector.util.TextRangeUtil;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Skill 安全规则
 * <p> 用轻量正则捕获最常见的风险内容: 疑似密钥、危险 shell 命令、
 * 过宽工具权限和 prompt injection 文案. V1 只定位和提示, 不自动删除用户内容.
 *
 * @author dong4j
 * @version 1.0.0
 * @email "mailto:dong4j@gmail.com"
 * @date 2026.05.23
 * @since 1.0.0
 */
public class SecurityRules implements SkillRule {

    /** 常见 secret 字段和值模式 */
    private static final Pattern SECRET_PATTERN = Pattern.compile(
        "(?i)(api[_-]?key|token|secret|password|private[_-]?key)\\s*[:=]\\s*['\\\"]?[A-Za-z0-9_./+=-]{16,}"
    );

    /** 高风险命令模式 */
    private static final Pattern DANGEROUS_COMMAND_PATTERN = Pattern.compile(
        "(rm\\s+-rf\\s+/|curl\\s+[^\\n|]+\\|\\s*(sh|bash)|wget\\s+[^\\n|]+\\|\\s*(sh|bash))",
        Pattern.CASE_INSENSITIVE
    );

    @Override
    @NotNull
    public List<SkillProblem> check(@NotNull SkillFile skillFile) {
        List<SkillProblem> problems = new ArrayList<>();
        String text = skillFile.psiFile().getText();
        int textLength = skillFile.psiFile().getTextLength();

        addFirstMatchProblem(problems, textLength, text, SECRET_PATTERN, "security.secret-pattern",
            SkillSeverity.ERROR, SkillInspectorBundle.message("inspection.security.secret"));
        addFirstMatchProblem(problems, textLength, text, DANGEROUS_COMMAND_PATTERN, "security.dangerous-command",
            SkillSeverity.ERROR, SkillInspectorBundle.message("inspection.security.dangerous.command"));
        checkAllowedTools(problems, textLength, skillFile.frontMatter());
        checkSensitiveText(problems, textLength, text);
        return problems;
    }

    /**
     * 对单个正则的首次命中生成问题
     */
    private void addFirstMatchProblem(@NotNull List<SkillProblem> problems,
                                      int textLength,
                                      @NotNull String text,
                                      @NotNull Pattern pattern,
                                      @NotNull String ruleId,
                                      @NotNull SkillSeverity severity,
                                      @NotNull String message) {
        Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            problems.add(new SkillProblem(
                ruleId,
                severity,
                message,
                TextRangeUtil.clamp(TextRange.create(matcher.start(), matcher.end()), textLength),
                List.of()
            ));
        }
    }

    /**
     * 检查 allowed-tools 是否包含过宽 Bash 权限
     */
    private void checkAllowedTools(@NotNull List<SkillProblem> problems,
                                   int textLength,
                                   SkillFrontMatter frontMatter) {
        if (frontMatter == null || frontMatter.parseError() != null) {
            return;
        }
        SkillYamlEntry allowedTools = frontMatter.metadata().allowedTools();
        if (allowedTools != null && allowedTools.value().toLowerCase(Locale.ROOT).contains("bash")) {
            problems.add(new SkillProblem(
                "security.allowed-tools-bash",
                SkillSeverity.WARNING,
                SkillInspectorBundle.message("inspection.security.allowed.tools.bash"),
                TextRangeUtil.clamp(allowedTools.valueRange(), textLength),
                List.of()
            ));
        }
    }

    /**
     * 检查敏感路径和 prompt injection 文案
     */
    private void checkSensitiveText(@NotNull List<SkillProblem> problems, int textLength, @NotNull String text) {
        String lowerText = text.toLowerCase(Locale.ROOT);
        KeywordMatch sensitivePath = firstKeywordMatch(lowerText, ".ssh", ".env", "~/.aws");
        if (sensitivePath != null) {
            problems.add(new SkillProblem(
                "security.sensitive-path",
                SkillSeverity.WARNING,
                SkillInspectorBundle.message("inspection.security.sensitive.path"),
                TextRangeUtil.clamp(TextRange.create(sensitivePath.startOffset(), sensitivePath.endOffset()), textLength),
                List.of()
            ));
        }

        int promptInjection = lowerText.indexOf("ignore previous instructions");
        if (promptInjection >= 0) {
            problems.add(new SkillProblem(
                "security.prompt-injection",
                SkillSeverity.WARNING,
                SkillInspectorBundle.message("inspection.security.prompt.injection"),
                TextRangeUtil.clamp(TextRange.create(promptInjection, promptInjection + "ignore previous instructions".length()), textLength),
                List.of()
            ));
        }
    }

    /**
     * 查找多个关键词中的首次出现位置
     */
    private KeywordMatch firstKeywordMatch(@NotNull String text, String... keywords) {
        KeywordMatch result = null;
        for (String keyword : keywords) {
            int index = text.indexOf(keyword);
            if (index >= 0 && (result == null || index < result.startOffset())) {
                result = new KeywordMatch(index, index + keyword.length());
            }
        }
        return result;
    }

    /**
     * 文本关键词命中范围
     *
     * @param startOffset 命中起始 offset
     * @param endOffset   命中结束 offset
     */
    private record KeywordMatch(int startOffset, int endOffset) {
    }
}
