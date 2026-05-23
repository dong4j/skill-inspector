package dev.dong4j.idea.skill.inspector.model;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;

import org.jetbrains.annotations.Nullable;

/**
 * Skill 文件上下文模型
 * <p> 表示一个被识别为 Agent Skill 入口的 {@code SKILL.md} 文件及其所在目录信息.
 * 规则层依赖该模型获取父目录名、frontmatter 和正文, 避免反复访问 PSI 或 VirtualFile.
 *
 * @param psiFile            当前 PSI 文件
 * @param skillDirectoryName {@code SKILL.md} 父目录名, 用于校验 frontmatter name
 * @param skillDirectory     {@code SKILL.md} 父目录
 * @param frontMatter        YAML frontmatter, 缺失时为空
 * @param body               Markdown 正文
 * @author dong4j
 * @version 1.0.0
 * @email "mailto:dong4j@gmail.com"
 * @date 2026.05.23
 * @since 1.0.0
 */
public record SkillFile(PsiFile psiFile,
                        String skillDirectoryName,
                        @Nullable VirtualFile skillDirectory,
                        @Nullable SkillFrontMatter frontMatter,
                        SkillBody body) {
}
