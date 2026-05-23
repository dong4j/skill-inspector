package dev.dong4j.idea.skill.inspector.parser;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;

import dev.dong4j.idea.skill.inspector.model.SkillFile;

import org.jetbrains.annotations.NotNull;

/**
 * Skill 领域模型构建器
 * <p> 将 IntelliJ PSI 文件转换为规则层可消费的 {@link SkillFile}. 该类集中处理
 * VirtualFile、父目录名和文本解析, 避免每条规则重复访问 IDE API.
 *
 * @author dong4j
 * @version 1.0.0
 * @email "mailto:dong4j@gmail.com"
 * @date 2026.05.23
 * @since 1.0.0
 */
public final class SkillModelBuilder {

    /** 私有构造函数, 防止工具类被实例化 */
    private SkillModelBuilder() {
    }

    /**
     * 根据 PSI 文件构建 Skill 模型
     *
     * @param psiFile PSI 文件
     * @return Skill 文件上下文
     */
    @NotNull
    public static SkillFile build(@NotNull PsiFile psiFile) {
        VirtualFile virtualFile = psiFile.getVirtualFile();
        VirtualFile skillDirectory = virtualFile == null ? null : virtualFile.getParent();
        String skillDirectoryName = skillDirectory == null ? "" : skillDirectory.getName();
        FrontMatterParseResult parseResult = FrontMatterParser.parse(psiFile);
        return new SkillFile(
            psiFile,
            skillDirectoryName,
            skillDirectory,
            parseResult.frontMatter(),
            parseResult.body()
        );
    }
}
