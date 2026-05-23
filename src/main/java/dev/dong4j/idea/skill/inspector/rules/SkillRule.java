package dev.dong4j.idea.skill.inspector.rules;

import dev.dong4j.idea.skill.inspector.model.SkillFile;
import dev.dong4j.idea.skill.inspector.model.SkillProblem;

import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Skill 检查规则接口
 * <p> 所有规则都以 {@link SkillFile} 作为输入, 输出 {@link SkillProblem} 列表.
 * 规则不直接注册 IntelliJ ProblemDescriptor, 是为了让规则层可以独立测试和复用.
 *
 * @author dong4j
 * @version 1.0.0
 * @email "mailto:dong4j@gmail.com"
 * @date 2026.05.23
 * @since 1.0.0
 */
public interface SkillRule {

    /**
     * 执行检查规则
     *
     * @param skillFile Skill 文件上下文
     * @return 检查问题列表
     */
    @NotNull
    List<SkillProblem> check(@NotNull SkillFile skillFile);
}
