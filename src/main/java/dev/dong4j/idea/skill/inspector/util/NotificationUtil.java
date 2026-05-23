package dev.dong4j.idea.skill.inspector.util;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.project.Project;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import dev.dong4j.idea.skill.inspector.PluginContents;

/**
 * 通知工具类
 * <p> 基于 IntelliJ Platform 的 {@link NotificationGroupManager} 直接发出气泡通知,
 * 不再依赖任何第三方插件公共库, 以保持 Skill Inspector 的零外部依赖.
 * <p> 通知组 ID 与 {@code plugin.xml} 中 {@code <notificationGroup>} 注册的 ID 一致,
 * 若 ID 不存在会抛出运行时异常, 这种契约更适合在插件层面快速发现配置错误.
 *
 * @author dong4j
 * @version 1.0.0
 * @email "mailto:dong4j@gmail.com"
 * @date 2026.01.02
 * @since 1.0.0
 */
public final class NotificationUtil {

    /**
     * 通知组 ID
     * <p> 必须与 {@code plugin.xml} 中注册的 {@code notificationGroup} 的 {@code id} 完全一致,
     * 否则 {@link NotificationGroupManager#getNotificationGroup(String)} 会抛出异常.
     */
    private static final String NOTIFICATION_GROUP_ID = "Skill Inspector Notifications";

    /**
     * 私有构造函数, 防止外部实例化
     * <p> 该类为工具类, 仅提供静态方法
     */
    private NotificationUtil() {
    }

    /**
     * 显示信息通知
     * <p> 用于在指定项目上下文中显示一条普通的信息通知, 通知标题为插件名称
     *
     * @param project 项目对象, 可为空 (插件初始化场景下可能尚无项目)
     * @param message 通知内容, 不可为空
     */
    public static void showInfo(@Nullable Project project, @NotNull String message) {
        notify(project, message, NotificationType.INFORMATION);
    }

    /**
     * 显示警告通知
     * <p> 调用 IntelliJ Platform 的通知系统显示警告信息
     *
     * @param project 项目对象, 可以为空
     * @param message 要显示的警告内容, 不能为空
     */
    public static void showWarning(@Nullable Project project, @NotNull String message) {
        notify(project, message, NotificationType.WARNING);
    }

    /**
     * 显示错误通知
     * <p> 在指定的项目中显示错误级别的通知信息
     *
     * @param project 项目对象, 可以为空
     * @param message 错误通知的内容, 不能为空
     */
    public static void showError(@Nullable Project project, @NotNull String message) {
        notify(project, message, NotificationType.ERROR);
    }

    /**
     * 统一的通知派发入口
     * <p> 集中处理通知组查找, 标题设置和最终派发, 避免在每个公共方法里重复样板代码.
     *
     * @param project 项目对象, 可为空
     * @param message 通知内容, 不可为空
     * @param type    通知级别, 决定气泡颜色与图标
     */
    private static void notify(@Nullable Project project,
                               @NotNull String message,
                               @NotNull NotificationType type) {
        Notification notification = NotificationGroupManager.getInstance()
            .getNotificationGroup(NOTIFICATION_GROUP_ID)
            .createNotification(PluginContents.PLUGIN_NAME, message, type);
        notification.notify(project);
    }
}
