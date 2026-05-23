package dev.dong4j.idea.skill.inspector.detection;

import com.intellij.psi.PsiFile;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link SkillFileDetector} 单元测试
 * <p> Inspection 入口依赖该检测器决定是否运行规则。规范要求入口文件名为
 * {@code SKILL.md}, 因此这里固定使用严格大小写匹配, 避免普通 Markdown 文件被误报。
 *
 * @author dong4j
 * @version 1.0.0
 * @email "mailto:dong4j@gmail.com"
 * @date 2026.05.23
 * @since 1.0.0
 */
class SkillFileDetectorTest {

    @Test
    void shouldDetectSpecificationSkillFileName() {
        PsiFile psiFile = psiFileNamed("SKILL.md");

        assertThat(SkillFileDetector.isSkillFile(psiFile)).isTrue();
    }

    @Test
    void shouldNotDetectLowercaseOrUnrelatedMarkdownFiles() {
        assertThat(SkillFileDetector.isSkillFile(psiFileNamed("skill.md"))).isFalse();
        assertThat(SkillFileDetector.isSkillFile(psiFileNamed("README.md"))).isFalse();
    }

    /**
     * 构造只包含文件名的 PSI mock, 让检测逻辑保持为普通单元测试。
     */
    private PsiFile psiFileNamed(String name) {
        PsiFile psiFile = mock(PsiFile.class);
        when(psiFile.getName()).thenReturn(name);
        return psiFile;
    }
}
