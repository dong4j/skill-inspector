package dev.dong4j.idea.skill.inspector.parser;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;

import dev.dong4j.idea.skill.inspector.model.SkillFile;
import dev.dong4j.idea.skill.inspector.model.SkillReference;

import org.intellij.plugins.markdown.lang.MarkdownElementTypes;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link MarkdownReferenceParser} 单元测试
 * <p> 验证解析器从 Markdown PSI 的 link destination 节点中提取引用, 避免退回到文本正则解析.
 *
 * @author dong4j
 * @version 1.0.0
 * @email "mailto:dong4j@gmail.com"
 * @date 2026.05.23
 * @since 1.0.0
 */
class MarkdownReferenceParserTest {

    @Test
    void shouldParseMarkdownLinkDestinationPsiNodes() {
        PsiElement reference = linkDestination("references/guide.md", TextRange.create(62, 81));
        PsiElement image = linkDestination("assets/icon.svg", TextRange.create(95, 110));
        SkillFile skillFile = skillFile(reference, image);

        List<SkillReference> references = MarkdownReferenceParser.parse(skillFile);

        assertThat(references)
            .extracting(SkillReference::target)
            .containsExactly("references/guide.md", "assets/icon.svg");
        assertThat(references.get(0).targetRange()).isEqualTo(TextRange.create(62, 81));
    }

    @Test
    void shouldIgnoreLinkDestinationBeforeMarkdownBody() {
        PsiElement frontMatterValue = linkDestination("https://example.com", TextRange.create(20, 39));
        PsiElement bodyReference = linkDestination("references/guide.md", TextRange.create(80, 99));
        SkillFile skillFile = skillFile(frontMatterValue, bodyReference);

        List<SkillReference> references = MarkdownReferenceParser.parse(skillFile);

        assertThat(references)
            .extracting(SkillReference::target)
            .containsExactly("references/guide.md");
    }

    @Test
    void shouldCollectLinkDestinationFromReferenceStyleDefinition() {
        // 模拟 reference-style 链接:
        //   正文出现 [guide][ref], 在文件尾部有 `[ref]: references/guide.md` 这种定义.
        // JetBrains Markdown PSI 会把定义节点表示为 LINK_DEFINITION, 内部包含一个 LINK_DESTINATION 子节点.
        // 现有递归解析逻辑能直接拿到该 destination, 因此 reference-style 链接天然被支持.
        PsiElement destinationInDefinition = linkDestination("references/guide.md", TextRange.create(70, 89));
        PsiElement linkDefinition = compositeElement(destinationInDefinition);
        SkillFile skillFile = skillFile(linkDefinition);

        List<SkillReference> references = MarkdownReferenceParser.parse(skillFile);

        assertThat(references)
            .extracting(SkillReference::target)
            .containsExactly("references/guide.md");
    }

    @Test
    void shouldStripAngleBracketsFromDestination() {
        PsiElement reference = linkDestination("<references/guide.md>", TextRange.create(70, 91));
        SkillFile skillFile = skillFile(reference);

        List<SkillReference> references = MarkdownReferenceParser.parse(skillFile);

        assertThat(references).singleElement().satisfies(skillReference -> {
            assertThat(skillReference.target()).isEqualTo("references/guide.md");
            assertThat(skillReference.targetRange()).isEqualTo(TextRange.create(71, 90));
        });
    }

    /**
     * 构造带 Markdown link destination 类型的 PSI 节点。
     */
    private PsiElement linkDestination(String text, TextRange range) {
        ASTNode node = mock(ASTNode.class);
        when(node.getElementType()).thenReturn(MarkdownElementTypes.LINK_DESTINATION);

        PsiElement element = mock(PsiElement.class);
        when(element.getNode()).thenReturn(node);
        when(element.getText()).thenReturn(text);
        when(element.getTextRange()).thenReturn(range);
        when(element.getChildren()).thenReturn(PsiElement.EMPTY_ARRAY);
        return element;
    }

    /**
     * 构造一个"非 LINK_DESTINATION"的 composite PSI 节点, 用于模拟 LINK_DEFINITION 等包裹节点。
     * <p> 让 {@code getNode()} 返回 null, 这样 parser 的判断会落到 "node == null" 分支并直接递归 children,
     * 与真实 PSI 中"wrapper 节点的 element type 不是 LINK_DESTINATION"行为等价.
     * <p> 选择 null 而不是 mock IElementType 是因为 IElementType 是 final/sealed, Mockito 无法直接 mock.
     *
     * @param children wrapper 节点下面挂的子节点
     */
    private PsiElement compositeElement(PsiElement... children) {
        PsiElement element = mock(PsiElement.class);
        when(element.getNode()).thenReturn(null);
        when(element.getChildren()).thenReturn(children);
        return element;
    }

    /**
     * 构造包含给定子节点的 SkillFile。
     */
    private SkillFile skillFile(PsiElement... children) {
        PsiFile psiFile = mock(PsiFile.class);
        when(psiFile.getChildren()).thenReturn(children);
        when(psiFile.getTextLength()).thenReturn(120);

        return new SkillFile(
            psiFile,
            "my-skill",
            null,
            null,
            new dev.dong4j.idea.skill.inspector.model.SkillBody("# My Skill", 50, 120)
        );
    }
}
