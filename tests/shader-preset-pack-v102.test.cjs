const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const root = path.resolve(__dirname, '..');
const read = rel => fs.readFileSync(path.join(root, rel), 'utf8');

const repo = read('app/src/main/java/com/retra/emulator/ShaderRepository.kt');
const view = read('app/src/main/java/com/retra/emulator/ShaderGameView.kt');
const html = read('app/src/main/assets/retra/index.html');
const settings = read('app/src/main/assets/retra/settings-ui.js');
const css = read('app/src/main/assets/retra/style-layout-polish.css');

test('built-in shader metadata is exposed with performance impact and descriptions', () => {
  assert.match(repo, /val impact: String/);
  assert.match(repo, /val description: String/);
  assert.match(repo, /put\("impact", option\.impact\)/);
  assert.match(repo, /put\("description", option\.description\)/);
  assert.match(settings, /shader-impact/);
  assert.match(settings, /`\$\{option\.impact \|\| 'Custom'\} GPU`/);
  assert.match(css, /#glslShaderOptions \.shader-option/);
  assert.match(html, />Video shader</);
  assert.match(html, /Performance labels show the expected GPU cost/);
});

test('shader renderer caches GL locations and avoids redundant filter state churn', () => {
  assert.match(view, /private fun cacheProgramLocations\(\)/);
  assert.match(view, /positionLocation = GLES20\.glGetAttribLocation/);
  assert.match(view, /textureLocation = GLES20\.glGetUniformLocation/);
  assert.match(view, /if \(filter != appliedTextureFilter\)/);
  assert.match(view, /appliedTextureFilter = filter/);
  assert.match(view, /PRECISION_LINE/);
  assert.match(view, /precision mediump float;/);
  assert.doesNotMatch(view, /GLES20\.glUniform1i\(GLES20\.glGetUniformLocation\(program, "uTexture"\), 0\)/);
});

test('shader path remains opt-in and custom GLSL installation is preserved', () => {
  assert.match(repo, /source = null/);
  assert.match(repo, /ALLOWED_EXTENSIONS = setOf\("glsl", "frag", "fs", "fsh"\)/);
  assert.match(settings, /AndroidBridge\.installGlslShader/);
  assert.match(settings, /AndroidBridge\.setGlslShader/);
});
