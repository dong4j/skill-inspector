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
