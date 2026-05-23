package dev.dong4j.idea.skill.inspector.quickfix;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;

import dev.dong4j.idea.skill.inspector.model.SkillFile;
import dev.dong4j.idea.skill.inspector.model.SkillFixType;
import dev.dong4j.idea.skill.inspector.model.SkillFrontMatter;
import dev.dong4j.idea.skill.inspector.model.SkillYamlEntry;
import dev.dong4j.idea.skill.inspector.parser.SkillModelBuilder;
import dev.dong4j.idea.skill.inspector.util.SkillInspectorBundle;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;

/**
 * Skill 通用 Quick Fix
 * <p> V1 只实现确定性修复: 创建 frontmatter、补齐必填字段、同步 name 与父目录名、
 * 将非法 name 规范化为 kebab-case, 以及为缺失的相对引用创建空文件.
 * 不自动改写 description 正文, 不替用户做主观内容生成.
 *
 * @author dong4j
 * @version 1.0.0
 * @email "mailto:dong4j@gmail.com"
 * @date 2026.05.23
 * @since 1.0.0
 */
public class SkillQuickFix implements LocalQuickFix {

    /** 修复类型 */
    private final SkillFixType fixType;

    /**
     * 创建 Quick Fix
     *
     * @param fixType 修复类型
     */
    public SkillQuickFix(@NotNull SkillFixType fixType) {
        this.fixType = fixType;
    }

    @Override
    @NotNull
    public String getFamilyName() {
        return switch (fixType) {
            case ADD_FRONT_MATTER -> SkillInspectorBundle.message("quickfix.frontmatter.add");
            case ADD_NAME -> SkillInspectorBundle.message("quickfix.name.add");
            case ADD_DESCRIPTION -> SkillInspectorBundle.message("quickfix.description.add");
            case SYNC_NAME_WITH_DIRECTORY -> SkillInspectorBundle.message("quickfix.name.sync");
            case CONVERT_NAME_TO_KEBAB -> SkillInspectorBundle.message("quickfix.name.kebab");
            case CREATE_MISSING_REFERENCE -> SkillInspectorBundle.message("quickfix.reference.create.file");
        };
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
        PsiFile psiFile = descriptor.getPsiElement().getContainingFile();
        if (psiFile == null) {
            return;
        }
        Document document = PsiDocumentManager.getInstance(project).getDocument(psiFile);
        if (document == null) {
            return;
        }

        WriteCommandAction.runWriteCommandAction(project, getFamilyName(), null, () -> {
            SkillFile skillFile = SkillModelBuilder.build(psiFile);
            switch (fixType) {
                case ADD_FRONT_MATTER -> addFrontMatter(skillFile, document);
                case ADD_NAME -> addField(skillFile, document, "name", skillName(skillFile));
                case ADD_DESCRIPTION -> addField(skillFile, document, "description", SkillQuickFixTexts.DESCRIPTION_PLACEHOLDER);
                case SYNC_NAME_WITH_DIRECTORY -> syncName(skillFile, document);
                case CONVERT_NAME_TO_KEBAB -> convertNameToKebab(skillFile, document);
                case CREATE_MISSING_REFERENCE -> createMissingReference(skillFile, document, descriptor);
            }
            PsiDocumentManager.getInstance(project).commitDocument(document);
        }, psiFile);
    }

    /**
     * 创建完整 frontmatter 模板
     */
    private void addFrontMatter(@NotNull SkillFile skillFile, @NotNull Document document) {
        document.insertString(0, SkillQuickFixTexts.frontMatterTemplate(skillName(skillFile), document.getTextLength() == 0));
    }

    /**
     * 在已有 frontmatter 中追加字段
     */
    private void addField(@NotNull SkillFile skillFile,
                          @NotNull Document document,
                          @NotNull String key,
                          @NotNull String value) {
        SkillFrontMatter frontMatter = skillFile.frontMatter();
        if (frontMatter == null) {
            addFrontMatter(skillFile, document);
            return;
        }

        int insertOffset = frontMatter.contentEndOffset();
        boolean hasExistingContent = insertOffset > frontMatter.contentStartOffset();
        document.insertString(insertOffset, SkillQuickFixTexts.fieldInsertion(hasExistingContent, key, value));
    }

    /**
     * 将 name 字段替换为父目录名
     */
    private void syncName(@NotNull SkillFile skillFile, @NotNull Document document) {
        SkillFrontMatter frontMatter = skillFile.frontMatter();
        if (frontMatter == null) {
            addFrontMatter(skillFile, document);
            return;
        }

        SkillYamlEntry nameEntry = frontMatter.metadata().name();
        if (nameEntry == null) {
            addField(skillFile, document, "name", skillName(skillFile));
            return;
        }
        document.replaceString(nameEntry.valueRange().getStartOffset(), nameEntry.valueRange().getEndOffset(), skillName(skillFile));
    }

    /**
     * 将不合规的 name 字段就地替换为 kebab-case 合规值
     * <p> 仅依赖 frontmatter 中 name 字段已有的文本范围, 不影响其他字段; 规范化逻辑委托给
     * {@link SkillQuickFixTexts#toKebabCaseName(String)}, 保证可被纯函数单元测试覆盖.
     */
    private void convertNameToKebab(@NotNull SkillFile skillFile, @NotNull Document document) {
        SkillFrontMatter frontMatter = skillFile.frontMatter();
        if (frontMatter == null) {
            return;
        }
        SkillYamlEntry nameEntry = frontMatter.metadata().name();
        if (nameEntry == null) {
            return;
        }
        String kebab = SkillQuickFixTexts.toKebabCaseName(nameEntry.value());
        document.replaceString(nameEntry.valueRange().getStartOffset(), nameEntry.valueRange().getEndOffset(), kebab);
    }

    /**
     * 为缺失的 Markdown 引用创建空文件
     * <p> 修复逻辑保守: 只创建文件占位, 不写入任何模板内容. 父目录按需创建,
     * 路径越界、非法字符等异常情况直接跳过, 避免在 skill 目录外写出文件.
     *
     * @param skillFile  Skill 上下文
     * @param document   当前 Markdown 文档, 用于按 ProblemDescriptor 范围读出引用目标文本
     * @param descriptor IntelliJ ProblemDescriptor, 由 SkillMdInspection 注入并保留命中范围
     */
    private void createMissingReference(@NotNull SkillFile skillFile,
                                        @NotNull Document document,
                                        @NotNull ProblemDescriptor descriptor) {
        VirtualFile skillDirectory = skillFile.skillDirectory();
        if (skillDirectory == null || !skillDirectory.isDirectory()) {
            return;
        }
        String target = extractTargetFromDescriptor(document, descriptor);
        if (target == null || target.isBlank()) {
            return;
        }
        target = SkillQuickFixTexts.stripFragmentAndQuery(target);
        if (target.isBlank()) {
            return;
        }

        Path skillDirectoryPath = Path.of(skillDirectory.getPath()).normalize();
        Path resolved;
        try {
            resolved = skillDirectoryPath.resolve(target).normalize();
        } catch (InvalidPathException ignored) {
            return;
        }
        // 与 ReferenceRules.outside-skill 规则保持一致: 不允许在 skill 目录外创建文件
        if (!resolved.startsWith(skillDirectoryPath)) {
            return;
        }
        Path relative = skillDirectoryPath.relativize(resolved);
        if (relative.toString().isEmpty()) {
            return;
        }

        try {
            createInVfs(skillDirectory, relative);
        } catch (IOException ignored) {
            // VFS 创建失败时仅静默跳过, 让 Inspection 在下一次扫描时重新提示, 而不是抛错打断用户.
        }
    }

    /**
     * 从 ProblemDescriptor 携带的范围中读取链接目标原文
     * <p> SkillMdInspection 把 problem 的 file 级 TextRange 注入到 createProblemDescriptor,
     * 因此这里读取的是 link destination 的原始文本.
     */
    private String extractTargetFromDescriptor(@NotNull Document document, @NotNull ProblemDescriptor descriptor) {
        TextRange range = descriptor.getTextRangeInElement();
        if (range == null) {
            return null;
        }
        int textLength = document.getTextLength();
        int start = Math.clamp(range.getStartOffset(), 0, textLength);
        int end = Math.clamp(range.getEndOffset(), start, textLength);
        if (start == end) {
            return null;
        }
        return document.getText(new TextRange(start, end)).trim();
    }

    /**
     * 在 IntelliJ VFS 内按 segment 创建目录和文件
     * <p> 使用 VFS API 而非 {@code java.nio.file}, 是为了让 IDE 能立即识别新文件,
     * 避免 Quick Fix 完成后用户仍看到"文件不存在"的提示.
     *
     * @param skillDirectory skill 根目录 VirtualFile
     * @param relative       skill 目录内的相对路径
     */
    private void createInVfs(@NotNull VirtualFile skillDirectory, @NotNull Path relative) throws IOException {
        VirtualFile parent = skillDirectory;
        int segmentCount = relative.getNameCount();
        for (int i = 0; i < segmentCount - 1; i++) {
            String segment = relative.getName(i).toString();
            VirtualFile next = parent.findChild(segment);
            if (next == null) {
                next = parent.createChildDirectory(this, segment);
            } else if (!next.isDirectory()) {
                return;
            }
            parent = next;
        }
        String fileName = relative.getName(segmentCount - 1).toString();
        if (parent.findChild(fileName) == null) {
            parent.createChildData(this, fileName);
        }
    }

    /**
     * 读取 SKILL.md 所在父目录名
     * <p> Light PSI 或未保存文件可能没有可靠父目录, 此时使用可识别的 fallback,
     * 避免 Quick Fix 生成空 name 导致修复后仍然违反 specification.
     */
    @NotNull
    private String skillName(@NotNull SkillFile skillFile) {
        return SkillQuickFixTexts.skillNameOrFallback(skillFile.skillDirectoryName());
    }
}
