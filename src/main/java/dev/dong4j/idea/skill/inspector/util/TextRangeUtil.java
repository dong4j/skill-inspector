package dev.dong4j.idea.skill.inspector.util;

import com.intellij.openapi.util.TextRange;

import org.jetbrains.annotations.NotNull;

/**
 * 文本范围工具类
 * <p> IntelliJ Inspection API 要求问题范围必须落在当前 PSI 文件内。规则层、Inspection
 * 适配层和 Quick Fix 都会处理 offset, 因此集中封装边界裁剪逻辑, 避免各处重复实现并降低越界风险.
 *
 * @author dong4j
 * @version 1.0.0
 * @email "mailto:dong4j@gmail.com"
 * @date 2026.05.23
 * @since 1.0.0
 */
public final class TextRangeUtil {

    /** 私有构造函数, 防止工具类被实例化 */
    private TextRangeUtil() {
    }

    /**
     * 创建文件开头范围
     * <p> 空文件无法创建非空范围, 此时返回 {@code 0..0}; IntelliJ 可以接受该范围用于文件级提示.
     *
     * @param textLength 文件长度
     * @return 文件开头的安全范围
     */
    @NotNull
    public static TextRange fileStart(int textLength) {
        return TextRange.create(0, Math.clamp(textLength, 0, 1));
    }

    /**
     * 将范围限制到文件长度内
     *
     * @param range      原始范围
     * @param textLength 文件长度
     * @return 安全范围
     */
    @NotNull
    public static TextRange clamp(@NotNull TextRange range, int textLength) {
        int safeLength = Math.clamp(textLength, 0, Integer.MAX_VALUE);
        int start = Math.clamp(range.getStartOffset(), 0, safeLength);
        int end = Math.clamp(range.getEndOffset(), start, safeLength);
        return TextRange.create(start, end);
    }
}
