package dev.dong4j.idea.skill.inspector.rules;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import dev.dong4j.idea.skill.inspector.model.SkillFile;
import dev.dong4j.idea.skill.inspector.model.SkillProblem;

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
            new ResourceRules(),
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
     * @return 聚合后的问题列表 (已按 ruleId + range 去重)
     */
    @NotNull
    public List<SkillProblem> run(@NotNull SkillFile skillFile) {
        List<SkillProblem> problems = new ArrayList<>();
        for (SkillRule rule : rules) {
            problems.addAll(rule.check(skillFile));
            if (hasFatalFrontMatterProblem(problems)) {
                break;
            }
        }
        return deduplicate(problems);
    }

    /**
     * 判断是否已经命中 frontmatter 致命结构问题.
     * <p>缺少 frontmatter 或 frontmatter 本身解析失败时, 继续执行正文质量、引用、资源和安全规则
     * 会产生大量派生噪音。例如缺闭合分隔符会让 body 为空, 继而触发 body.missing-title /
     * body.missing-trigger。这里按 docs/design.md 的约定提前停止后续规则.
     */
    private static boolean hasFatalFrontMatterProblem(@NotNull List<SkillProblem> problems) {
        return problems.stream()
            .map(SkillProblem::ruleId)
            .anyMatch(ruleId -> ruleId.equals("frontmatter.missing") || ruleId.equals("frontmatter.invalid-yaml"));
    }

    /**
     * 按 {@code ruleId + range} 去重.
     * <p>同一个 skill 文件上, 同一条规则不应该对同一段文本范围重复报问题. 这里做一次防御性去重:
     * <ul>
     *   <li>拦截上游规则代码意外重复 add 同一问题 (例如分支遗漏 return);</li>
     *   <li>拦截不同规则巧合产生 ruleId/range 完全一致的问题 (理论上不应发生);</li>
     * </ul>
     * 保证 {@link dev.dong4j.idea.skill.inspector.inspection.SkillMdInspection} 与
     * {@link dev.dong4j.idea.skill.inspector.action.ValidateCurrentSkillFileAction}
     * 拿到的 problems 列表内部唯一, 避免 Problems View 出现完全相同条目的重复项.
     */
    @NotNull
    private static List<SkillProblem> deduplicate(@NotNull List<SkillProblem> problems) {
        if (problems.size() <= 1) {
            return problems;
        }
        Set<String> seen = new HashSet<>(problems.size() * 2);
        List<SkillProblem> result = new ArrayList<>(problems.size());
        for (SkillProblem p : problems) {
            String key = p.ruleId() + "@" + p.range().getStartOffset() + "-" + p.range().getEndOffset();
            if (seen.add(key)) {
                result.add(p);
            }
        }
        return result;
    }
}
