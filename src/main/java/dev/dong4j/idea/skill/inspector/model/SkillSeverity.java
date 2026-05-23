package dev.dong4j.idea.skill.inspector.model;

/**
 * Skill 检查问题级别
 * <p> 该枚举是插件内部规则层使用的严重程度模型, 避免规则实现直接依赖
 * IntelliJ Inspection 的展示类型, 便于后续将规则引擎复用到 CLI 或构建期校验.
 *
 * @author dong4j
 * @version 1.0.0
 * @email "mailto:dong4j@gmail.com"
 * @date 2026.05.23
 * @since 1.0.0
 */
public enum SkillSeverity {
    /** 阻塞级问题, 通常表示 skill 无法被正确识别或存在明确安全风险 */
    ERROR,

    /** 普通警告, 表示 skill 可用但存在规范、质量或兼容性问题 */
    WARNING,

    /** 弱警告, 表示建议性优化, 不应阻塞用户继续编辑 */
    WEAK_WARNING
}
