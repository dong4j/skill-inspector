package dev.dong4j.idea.skill.inspector.parser;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;

import dev.dong4j.idea.skill.inspector.model.SkillFile;
import dev.dong4j.idea.skill.inspector.model.SkillReference;

import org.intellij.plugins.markdown.lang.MarkdownElementTypes;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Markdown 引用解析器
 * <p> 依赖 JetBrains Markdown 插件已经构建好的 PSI 树, 提取 Markdown link destination 节点.
 * 这样可以复用 IDE 对 Markdown 语法的解析结果, 避免用正则逐行扫描正文导致误判复杂语法。
 *
 * @author dong4j
 * @version 1.0.0
 * @email "mailto:dong4j@gmail.com"
 * @date 2026.05.23
 * @since 1.0.0
 */
public final class MarkdownReferenceParser {

    /** 私有构造函数, 防止工具类被实例化 */
    private MarkdownReferenceParser() {
    }

    /**
     * 从 Markdown PSI 中提取链接目标
     *
     * @param skillFile Skill 文件上下文
     * @return 链接目标列表
     */
    @NotNull
    public static List<SkillReference> parse(@NotNull SkillFile skillFile) {
        List<SkillReference> references = new ArrayList<>();
        collectReferences(skillFile.psiFile(), skillFile.body().startOffset(), references);
        return references;
    }

    /**
     * 递归遍历 PSI, 收集 Markdown 链接目标节点
     */
    private static void collectReferences(@NotNull PsiElement element,
                                          int bodyStartOffset,
                                          @NotNull List<SkillReference> references) {
        ASTNode node = element.getNode();
        if (node != null && MarkdownElementTypes.LINK_DESTINATION.equals(node.getElementType())) {
            addReference(element, bodyStartOffset, references);
            return;
        }

        for (PsiElement child : element.getChildren()) {
            collectReferences(child, bodyStartOffset, references);
        }
    }

    /**
     * 将 Markdown link destination PSI 节点转换为规则层引用模型
     */
    private static void addReference(@NotNull PsiElement element,
                                     int bodyStartOffset,
                                     @NotNull List<SkillReference> references) {
        TextRange rawRange = element.getTextRange();
        if (rawRange == null || rawRange.getStartOffset() < bodyStartOffset) {
            return;
        }

        LinkTarget target = normalizeDestination(element.getText(), rawRange);
        if (target.value().isBlank()) {
            return;
        }
        references.add(new SkillReference(target.value(), target.range()));
    }

    /**
     * 规范化 Markdown link destination
     * <p> Markdown 支持 {@code <path with spaces>} 形式, PSI 节点文本会保留尖括号。
     * 规则层只需要真实目标和对应文本范围。
     */
    @NotNull
    private static LinkTarget normalizeDestination(@NotNull String rawText, @NotNull TextRange rawRange) {
        String trimmed = rawText.trim();
        int startOffset = rawRange.getStartOffset() + rawText.indexOf(trimmed);
        if (trimmed.length() >= 2 && trimmed.charAt(0) == '<' && trimmed.charAt(trimmed.length() - 1) == '>') {
            trimmed = trimmed.substring(1, trimmed.length() - 1).trim();
            startOffset++;
        }
        return new LinkTarget(trimmed, TextRange.create(startOffset, startOffset + trimmed.length()));
    }

    /**
     * 解析后的链接目标及其文本范围
     */
    private record LinkTarget(String value, TextRange range) {
    }
}
