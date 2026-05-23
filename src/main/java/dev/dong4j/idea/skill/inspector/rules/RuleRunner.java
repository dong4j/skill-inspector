package dev.dong4j.idea.skill.inspector.rules;

import dev.dong4j.idea.skill.inspector.model.SkillFile;
import dev.dong4j.idea.skill.inspector.model.SkillProblem;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Skill 规则执行器
 * <p> 按固定顺序执行 V1 规则集合. 结构规则先执行, 因为 frontmatter 解析失败时,
 * 后续依赖字段模型的规则应尽量减少噪音.
 *
 * @author dong4j
 * @version 1.0.0
 * @email "mailto:dong4j@gmail.com"
 * @date 2026.05.23
 * @since 1.0.0
 */
public class RuleRunner {

    /** V1 默认规则集合 */
    private final List<SkillRule> rules;

    /**
     * 创建默认规则执行器
     */
    public RuleRunner() {
        this(List.of(
            new StructuralRules(),
            new QualityRules(),
            new ReferenceRules(),
            new SecurityRules()
        ));
    }

    /**
     * 创建自定义规则执行器
     *
     * @param rules 规则列表
     */
    public RuleRunner(@NotNull List<SkillRule> rules) {
        this.rules = List.copyOf(rules);
    }

    /**
     * 执行所有规则
     *
     * @param skillFile Skill 文件上下文
     * @return 聚合后的问题列表
     */
    @NotNull
    public List<SkillProblem> run(@NotNull SkillFile skillFile) {
        List<SkillProblem> problems = new ArrayList<>();
        for (SkillRule rule : rules) {
            problems.addAll(rule.check(skillFile));
        }
        return problems;
    }
}
