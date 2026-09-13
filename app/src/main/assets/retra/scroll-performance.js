/* Retra v1.0.1 — native-scroll coordinator.
   No JS-driven scrolling is used. This module only marks active scrolling and
   postpones non-urgent background work until the gesture/momentum settles. */
(() => {
  'use strict';

  const SCROLL_IDLE_MS = 96;
  let scrolling = false;
  let idleTimer = 0;
  let lastScrollAt = 0;

  function setScrolling(active) {
    if (scrolling === active) return;
    scrolling = active;
    document.documentElement.classList.toggle('retra-scrolling', active);
  }

  function settleScrollState() {
    const elapsed = performance.now() - lastScrollAt;
    const remaining = SCROLL_IDLE_MS - elapsed;
    if (remaining > 4) {
      idleTimer = window.setTimeout(settleScrollState, remaining);
      return;
    }
    setScrolling(false);
  }

  function markScrollActivity() {
    lastScrollAt = performance.now();
    setScrolling(true);
    window.clearTimeout(idleTimer);
    idleTimer = window.setTimeout(settleScrollState, SCROLL_IDLE_MS);
  }

  // Scroll does not bubble, so capture is intentional. The listener is passive
  // and performs no geometry reads, preventing main-thread scroll blocking.
  document.addEventListener('scroll', markScrollActivity, { capture: true, passive: true });
  window.visualViewport?.addEventListener('scroll', markScrollActivity, { passive: true });

  function runWhenIdle(callback, timeout = 700) {
    if (typeof callback !== 'function') return 0;
    const startedAt = performance.now();
    const maxWait = Math.max(SCROLL_IDLE_MS, Number(timeout) || 700);

    const attempt = () => {
      const elapsed = performance.now() - startedAt;
      if (!scrolling || elapsed >= maxWait) {
        callback();
        return;
      }
      window.setTimeout(attempt, SCROLL_IDLE_MS);
    };

    if (!scrolling) {
      callback();
      return 0;
    }
    return window.setTimeout(attempt, SCROLL_IDLE_MS);
  }

  window.RetraScrollPerformance = Object.freeze({
    isScrolling: () => scrolling,
    runWhenIdle
  });
})();
