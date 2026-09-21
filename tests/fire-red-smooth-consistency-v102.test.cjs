const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const root = path.resolve(__dirname, '..');
const read = rel => fs.readFileSync(path.join(root, rel), 'utf8');
const session = read('app/src/main/java/com/retra/emulator/EmulationSessionManager.kt');

test('ROM-independent turbo presentation uses one stable 60/120 cadence', () => {
  assert.match(session, /turboPresentationHz/);
  assert.match(session, /turboSlicePlanner\.nextFrames\(\)/);
});

test('turbo never engages an automatic renderer-quality fallback', () => {
  assert.doesNotMatch(session, /adaptiveQualityFallbackEngaged|sample\.constrained|sample\.recovered/);
  assert.doesNotMatch(session, /adaptiveCoreFrameSkip/);
});

test('speed changes reset timing but do not switch physical display modes', () => {
  assert.match(session, /if \(kotlin\.math\.abs\(speed - lastSpeed\) > 0\.0001\)/);
  assert.match(session, /framePacer\.reset\(\)/);
  assert.match(session, /turboGovernor\.reset\(\)/);
  const speedBlock = session.match(/if \(kotlin\.math\.abs\(speed - lastSpeed\)[\s\S]*?lastSpeed = speed\n\s*\}/)?.[0] || '';
  assert.doesNotMatch(speedBlock, /applyGameplayMode/);
});
