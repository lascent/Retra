const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');

const gameplay = fs.readFileSync('app/src/main/java/com/retra/emulator/GameplayController.kt', 'utf8');
const layout = fs.readFileSync('app/src/main/java/com/retra/emulator/GameplayLayoutController.kt', 'utf8');

test('fast-forward reconciles Java Local Link state with the native core before blocking speed changes', () => {
  assert.match(gameplay, /internal fun MainActivity\.isLocalLinkSpeedRestricted\(\): Boolean/);
  assert.match(gameplay, /isNativeLocalLinkActive\(\)/);
  assert.match(gameplay, /localLinkActive = nativeActive/);
});

test('toggleFastForward blocks only a confirmed active link instead of trusting a stale flag', () => {
  const toggle = gameplay.match(/internal fun MainActivity\.toggleFastForward\(\) \{[\s\S]*?\n\}/)?.[0] || '';
  assert.match(toggle, /if \(isLocalLinkSpeedRestricted\(\)\)/);
  assert.doesNotMatch(toggle, /if \(localLinkActive\)/);
  assert.match(toggle, /preferredEmulationSpeed/);
});

test('gameplay menu speed row reports disabled state using the reconciled link status', () => {
  const menu = gameplay.match(/internal fun MainActivity\.renderGameplayMainMenu\(dialog: Dialog\) \{[\s\S]*?\n\}/)?.[0] || '';
  assert.match(menu, /val speedRestrictedByLink = isLocalLinkSpeedRestricted\(\)/);
  assert.match(menu, /if \(speedRestrictedByLink\) "Disabled while linked"/);
});

test('hold-to-fast-forward also respects the confirmed Local Link restriction', () => {
  const touch = layout.match(/binding\.speedButton\.setOnTouchListener[\s\S]*?\n    }\n    binding\.quickSaveButton/)?.[0] || '';
  assert.match(touch, /if \(holdMode && !isLocalLinkSpeedRestricted\(\)\)/);
});
