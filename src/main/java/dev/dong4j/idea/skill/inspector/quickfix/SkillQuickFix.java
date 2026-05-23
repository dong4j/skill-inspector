package dev.dong4j.idea.skill.inspector.quickfix;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;

import dev.dong4j.idea.skill.inspector.model.SkillFile;
import dev.dong4j.idea.skill.inspector.model.SkillFixType;
import dev.dong4j.idea.skill.inspector.model.SkillFrontMatter;
import dev.dong4j.idea.skill.inspector.model.SkillYamlEntry;
import dev.dong4j.idea.skill.inspector.parser.SkillModelBuilder;
import dev.dong4j.idea.skill.inspector.util.SkillInspectorBundle;

import org.jetbrains.annotations.NotNull;

/**
 * Skill 通用 Quick Fix
 * <p> V1 只实现确定性修复: 创建 frontmatter、补齐必填字段、同步 name 与父目录名.
 * 不自动改写 description 正文, 避免替用户做主观内容生成.
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
     * 读取 SKILL.md 所在父目录名
     * <p> Light PSI 或未保存文件可能没有可靠父目录, 此时使用可识别的 fallback,
     * 避免 Quick Fix 生成空 name 导致修复后仍然违反 specification.
     */
    @NotNull
    private String skillName(@NotNull SkillFile skillFile) {
        return SkillQuickFixTexts.skillNameOrFallback(skillFile.skillDirectoryName());
    }
}
