/* Retra v1.0.1 — low-latency interaction feedback.
   No timers drive gameplay. This module only manages WebView app-chrome motion. */
(() => {
  'use strict';

  const reduceMotion = window.matchMedia?.('(prefers-reduced-motion: reduce)');
  const PRESS_SELECTOR = [
    'button:not(:disabled)',
    '[role="button"]:not([aria-disabled="true"])',
    '.settings-item',
    '.more-item',
    '.detail-row',
    '.frequency-option',
    '.selection-option',
    '.sheet-options-item',
    '.cover-card'
  ].join(',');
  const PRESS_EXCLUDE = [
    '#screenSizePage .screen-preview-shell *',
    '.screen-editor-canvas *',
    'input[type="range"]',
    'input[type="text"]',
    'textarea',
    'select'
  ].join(',');
  const MOVE_CANCEL_PX = 11;
  const HOLD_CANCEL_MS = 420;
  const SUPPRESS_CLICK_MS = 720;
  const TAP_ONLY_SELECTOR = [
    '.settings-item',
    '.more-item',
    '.detail-row.tappable',
    '[data-open-page]'
  ].join(',');
  const activePointers = new Map();
  const suppressedClicks = new WeakMap();
  let pendingNavigationKind = 'forward';
  let motionSequence = 0;

  function prefersReducedMotion() {
    return Boolean(reduceMotion?.matches);
  }

  function suppressNextClick(element) {
    if (!(element instanceof Element)) return;
    suppressedClicks.set(element, performance.now() + SUPPRESS_CLICK_MS);
  }

  function shouldSuppressClick(element) {
    if (!(element instanceof Element)) return false;
    const until = suppressedClicks.get(element) || 0;
    if (performance.now() >= until) {
      suppressedClicks.delete(element);
      return false;
    }
    suppressedClicks.delete(element);
    return true;
  }

  function clearPress(pointerId) {
    const state = activePointers.get(pointerId);
    if (!state) return;
    if (state.holdTimer) window.clearTimeout(state.holdTimer);
    state.element?.classList.remove('retra-pressing');
    activePointers.delete(pointerId);
  }

  function finishPointer(pointerId, { cancelled = false } = {}) {
    const state = activePointers.get(pointerId);
    if (!state) return;

    // Android/WebView can still synthesize a click when a button is released
    // after a long hold or after a small scroll drag. Card navigation is tap-
    // only: once the gesture becomes a hold/drag, consume that synthesized
    // click instead of opening the destination page.
    if (!cancelled && state.tapOnlyElement && (state.held || state.moved)) {
      suppressNextClick(state.tapOnlyElement);
    }
    clearPress(pointerId);
  }

  document.addEventListener('pointerdown', event => {
    if (event.button !== undefined && event.button !== 0) return;
    const target = event.target instanceof Element ? event.target.closest(PRESS_SELECTOR) : null;
    if (!target || target.matches(':disabled,[aria-disabled="true"]')) return;
    if (event.target instanceof Element && event.target.closest(PRESS_EXCLUDE)) return;

    const tapOnlyElement = target.closest(TAP_ONLY_SELECTOR);
    const state = {
      element: target,
      tapOnlyElement,
      x: event.clientX,
      y: event.clientY,
      moved: false,
      held: false,
      holdTimer: 0
    };

    target.classList.add('retra-pressing');
    activePointers.set(event.pointerId, state);

    if (tapOnlyElement) {
      state.holdTimer = window.setTimeout(() => {
        if (activePointers.get(event.pointerId) !== state || state.moved) return;
        state.held = true;
        state.element?.classList.remove('retra-pressing');
      }, HOLD_CANCEL_MS);
    }
  }, { capture: true, passive: true });

  document.addEventListener('pointermove', event => {
    const state = activePointers.get(event.pointerId);
    if (!state) return;
    if (Math.hypot(event.clientX - state.x, event.clientY - state.y) > MOVE_CANCEL_PX) {
      state.moved = true;
      if (state.holdTimer) {
        window.clearTimeout(state.holdTimer);
        state.holdTimer = 0;
      }
      state.element?.classList.remove('retra-pressing');
    }
  }, { capture: true, passive: true });

  document.addEventListener('pointerup', event => finishPointer(event.pointerId), {
    capture: true,
    passive: true
  });

  document.addEventListener('pointercancel', event => finishPointer(event.pointerId, { cancelled: true }), {
    capture: true,
    passive: true
  });

  window.addEventListener('blur', () => {
    activePointers.forEach((_, pointerId) => clearPress(pointerId));
  }, { passive: true });

  /* Capture navigation intent before click handlers run. Back navigation gets a
     subtly reversed settle; bottom tabs use an even faster transition. */
  document.addEventListener('click', event => {
    const target = event.target instanceof Element ? event.target : null;
    if (!target) return;

    const tapOnlyElement = target.closest(TAP_ONLY_SELECTOR);
    if (tapOnlyElement && shouldSuppressClick(tapOnlyElement)) {
      event.preventDefault();
      event.stopImmediatePropagation();
      return;
    }

    if (target.closest('[data-back-to]')) pendingNavigationKind = 'back';
    else if (target.closest('.nav-item[data-page]')) pendingNavigationKind = 'tab';
    else pendingNavigationKind = 'forward';
  }, { capture: true });

  function pageEnter(page, fallbackKind = 'forward') {
    if (!(page instanceof HTMLElement) || prefersReducedMotion()) return;
    const kind = pendingNavigationKind || fallbackKind;
    pendingNavigationKind = 'forward';
    const className = kind === 'tab'
      ? 'retra-page-tab-enter'
      : kind === 'back'
        ? 'retra-page-back-enter'
        : 'retra-page-enter';

    page.classList.remove('retra-page-enter', 'retra-page-tab-enter', 'retra-page-back-enter');
    const token = String(++motionSequence);
    page.dataset.retraMotionToken = token;
    page.classList.add(className);

    const cleanup = () => {
      if (page.dataset.retraMotionToken !== token) return;
      page.classList.remove(className);
      delete page.dataset.retraMotionToken;
      page.removeEventListener('animationend', onAnimationEnd);
    };
    const onAnimationEnd = event => {
      if (event.target === page) cleanup();
    };
    page.addEventListener('animationend', onAnimationEnd);
    window.setTimeout(cleanup, 260);
  }

  window.RetraMotion = Object.freeze({ pageEnter });
  requestAnimationFrame(() => document.body.classList.add('retra-motion-ready'));
})();
