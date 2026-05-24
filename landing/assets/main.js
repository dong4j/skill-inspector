/*
 * Skill Inspector — Landing Page Interaction (v2)
 * ----------------------------------------------------------------------------
 * 模块作用 (why this file exists):
 *   纯 vanilla JS, 0 框架. v2 (Strict Editorial 风格) 故意保持极简动效,
 *   只做两件事:
 *     1. Reveal-on-scroll (IntersectionObserver, 替代 Framer Motion)
 *     2. Header 滚动态切换 (sentinel + IO, 0 scroll 监听)
 *
 *   v1 的 hero 逐词入场 + benefit cursor-follow 被故意去掉 —
 *   editorial / 规范手册风讲究 "字停纸上" 的物理感, 任何 JS 驱动的位移都会
 *   破坏纸面的稳重感.
 *
 * 关键约束:
 *   - 所有交互都尊重 prefers-reduced-motion
 *   - 不污染 window 全局
 *   - 老浏览器降级 (IntersectionObserver 不存在时直接显示, 不假死)
 */

(() => {
  'use strict';

  /* ----------------------------------------------------------------
   * 1. Reveal-on-scroll
   *
   *   v2 只做 opacity 过渡, 不做 translate — 文字 "出现在纸上",
   *   而不是 "从下面滑上来"
   * ---------------------------------------------------------------- */
  const setupReveal = () => {
    const targets = document.querySelectorAll('.reveal, .reveal-stagger');
    if (!('IntersectionObserver' in window)) {
      targets.forEach((el) => el.classList.add('is-in'));
      return;
    }

    const io = new IntersectionObserver(
      (entries) => {
        entries.forEach((entry) => {
          if (entry.isIntersecting) {
            entry.target.classList.add('is-in');
            io.unobserve(entry.target);
          }
        });
      },
      { threshold: 0.08, rootMargin: '0px 0px -40px 0px' }
    );

    targets.forEach((el) => io.observe(el));
  };

  /* ----------------------------------------------------------------
   * 2. Header scroll state
   *
   *   在 body 顶部插入 sentinel, sentinel 离开视口 = 已滚动 -> 给 header
   *   一个 data-scrolled='true', CSS 切换毛玻璃 + hairline
   * ---------------------------------------------------------------- */
  const setupHeader = () => {
    const header = document.querySelector('.site-header');
    if (!header) return;

    const sentinel = document.createElement('div');
    sentinel.style.cssText = 'position:absolute;top:0;left:0;width:1px;height:1px;pointer-events:none;';
    document.body.prepend(sentinel);

    if (!('IntersectionObserver' in window)) return;

    const io = new IntersectionObserver(
      ([entry]) => {
        header.dataset.scrolled = entry.isIntersecting ? 'false' : 'true';
      },
      { threshold: 0 }
    );
    io.observe(sentinel);
  };

  /* ----------------------------------------------------------------
   * Boot
   * ---------------------------------------------------------------- */
  const boot = () => {
    setupReveal();
    setupHeader();
  };

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', boot, { once: true });
  } else {
    boot();
  }
})();
