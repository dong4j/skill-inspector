package dev.dong4j.idea.skill.inspector.inspection;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link SkillInspectionDuplicateGuard} 单元测试
 * <p>固定 Problems View 重复条目的核心防线: 同一文件版本、同一 rule/range/message
 * 在短窗口内只允许注册一次; 文件版本变化或窗口过期后必须允许重新注册.
 *
 * @author dong4j
 * @version 1.0.0
 * @email "mailto:dong4j@gmail.com"
 * @date 2026.05.25
 * @since 1.0.0
 */
class SkillInspectionDuplicateGuardTest {

    @Test
    void shouldSuppressSameProblemWithinWindow() {
        SkillInspectionDuplicateGuard guard = new SkillInspectionDuplicateGuard(1_000L);
        SkillProblemKey key = key(1L);

        assertThat(guard.shouldRegister(key, 10_000L)).isTrue();
        assertThat(guard.shouldRegister(key, 10_100L)).isFalse();
    }

    @Test
    void shouldAllowSameProblemAfterWindowExpires() {
        SkillInspectionDuplicateGuard guard = new SkillInspectionDuplicateGuard(1_000L);
        SkillProblemKey key = key(1L);

        assertThat(guard.shouldRegister(key, 10_000L)).isTrue();
        assertThat(guard.shouldRegister(key, 11_001L)).isTrue();
    }

    @Test
    void shouldAllowSameRuleWhenFileVersionChanges() {
        SkillInspectionDuplicateGuard guard = new SkillInspectionDuplicateGuard(1_000L);

        assertThat(guard.shouldRegister(key(1L), 10_000L)).isTrue();
        assertThat(guard.shouldRegister(key(2L), 10_100L)).isTrue();
    }

    @Test
    void shouldAllowDifferentProblemInSameFileVersion() {
        SkillInspectionDuplicateGuard guard = new SkillInspectionDuplicateGuard(1_000L);

        assertThat(guard.shouldRegister(key(1L), 10_000L)).isTrue();
        assertThat(guard.shouldRegister(new SkillProblemKey(
            "/tmp/skill/SKILL.md",
            1L,
            "description.missing-usage",
            20,
            30,
            "Description should explain when to use this skill"
        ), 10_100L)).isTrue();
    }

    private static SkillProblemKey key(long modificationStamp) {
        return new SkillProblemKey(
            "/tmp/skill/SKILL.md",
            modificationStamp,
            "body.too-long",
            1,
            10,
            "SKILL.md is longer than 12000 characters"
        );
    }
}
