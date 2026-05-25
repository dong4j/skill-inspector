package dev.dong4j.idea.skill.inspector.inspection;

import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Inspection descriptor 幂等保护器.
 * <p>IntelliJ daemon 可能在同一文件版本上并发启动多轮 LocalInspection 会话。每轮会话
 * 都会得到自己的 ProblemsHolder, 平台不会替插件按业务规则做去重。本类把"同一文件版本
 * 的同一 Skill problem 只注册一次"收束为独立策略, 避免将时间窗口和 key 细节散落在
 * {@link SkillMdInspection} 中.
 *
 * @author dong4j
 * @version 1.0.0
 * @email "mailto:dong4j@gmail.com"
 * @date 2026.05.25
 * @since 1.0.0
 */
final class SkillInspectionDuplicateGuard {

    /** 最近注册的问题 key 及首次注册时间 */
    private final Map<SkillProblemKey, Long> recentProblemKeys = new ConcurrentHashMap<>();

    /** 重复 daemon 会话通常在毫秒级完成, 窗口只用于挡同一批 descriptor 的并发重复写入 */
    private final long suppressionWindowMillis;

    /**
     * 创建幂等保护器.
     *
     * @param suppressionWindowMillis 短窗口长度, 单位毫秒
     */
    SkillInspectionDuplicateGuard(long suppressionWindowMillis) {
        this.suppressionWindowMillis = suppressionWindowMillis;
    }

    /**
     * 判断当前问题是否应注册到 ProblemsHolder.
     *
     * @param key 当前文件版本的问题 key
     * @return true 表示未在窗口内注册过, 可以继续注册; false 表示应跳过重复 descriptor
     */
    boolean shouldRegister(@NotNull SkillProblemKey key) {
        return shouldRegister(key, System.currentTimeMillis());
    }

    /**
     * 带显式时间的判断入口, 便于单元测试固定边界.
     */
    boolean shouldRegister(@NotNull SkillProblemKey key, long nowMillis) {
        cleanup(nowMillis);

        Long previous = recentProblemKeys.putIfAbsent(key, nowMillis);
        if (previous == null) {
            return true;
        }
        if (nowMillis - previous <= suppressionWindowMillis) {
            return false;
        }
        recentProblemKeys.put(key, nowMillis);
        return true;
    }

    /**
     * 清理过期 key, 防止长时间 IDE 会话中积累无用状态.
     */
    private void cleanup(long nowMillis) {
        recentProblemKeys.entrySet().removeIf(
            entry -> nowMillis - entry.getValue() > suppressionWindowMillis
        );
    }
}
