const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const gradle = fs.readFileSync(path.join(__dirname, '../app/build.gradle.kts'), 'utf8');
const manifest = fs.readFileSync(path.join(__dirname, '../app/src/main/AndroidManifest.xml'), 'utf8');
const main = fs.readFileSync(path.join(__dirname, '../app/src/main/java/com/retra/emulator/MainActivity.kt'), 'utf8');
const webController = fs.readFileSync(path.join(__dirname, '../app/src/main/java/com/retra/emulator/WebUiController.kt'), 'utf8');
const html = fs.readFileSync(path.join(__dirname, '../app/src/main/assets/retra/index.html'), 'utf8');
const shaderRepo = fs.readFileSync(path.join(__dirname, '../app/src/main/java/com/retra/emulator/ShaderRepository.kt'), 'utf8');
const shaderView = fs.readFileSync(path.join(__dirname, '../app/src/main/java/com/retra/emulator/ShaderGameView.kt'), 'utf8');

test('release metadata and optimization are enabled', () => {
  assert.match(gradle, /versionCode = 447/);
  assert.match(gradle, /versionName = "1\.0\.0"/);
  assert.match(gradle, /optimization \{\s*enable = true\s*\}/s);
});

test('WebView uses appassets HTTPS origin and blocks universal file URL access', () => {
  assert.match(webController, /WebViewAssetLoader/);
  assert.match(webController, /allowUniversalAccessFromFileURLs = false/);
  assert.match(webController, /allowFileAccess = false/);
  assert.match(webController, /https:\/\/appassets\.androidplatform\.net\/assets\/retra\/index\.html/);
  assert.match(manifest, /android:usesCleartextTraffic="false"/);
  assert.match(html, /Content-Security-Policy/);
});

test('GLSL shader installer is functional and exposed through the optimized renderer', () => {
  assert.match(html, /id="glslShaderBtn"/);
  assert.match(html, /id="glslShaderModal"/);
  assert.match(html, /id="installGlslShaderBtn"[^>]*>INSTALL SHADER</);
  assert.match(shaderRepo, /fun install\(uri: Uri\): Option/);
  assert.doesNotMatch(shaderRepo, /lcd-grid|crt-lite|grayscale/);
  assert.match(shaderRepo, /"none" to "None"/);
  assert.match(shaderView, /RENDERMODE_WHEN_DIRTY/);
  assert.match(shaderView, /GLUtils\.texSubImage2D/);
});


test('publish-ready repository metadata and CI quality gates are present', () => {
  for (const rel of [
    'README.md',
    'LICENSE',
    'THIRD_PARTY_NOTICES.md',
    'CHANGELOG.md',
    'SECURITY.md',
    'CONTRIBUTING.md',
    'docs/RELEASE_NOTES_v1.0.0.md',
    'docs/RELEASE_CHECKLIST_v1.0.0.md',
    'docs/DEVELOPMENT_HISTORY.md',
    'docs/README.md',
    'tools/release_gate.py',
  ]) {
    assert.equal(fs.existsSync(path.join(__dirname, '..', rel)), true, `${rel} missing`);
  }
  const workflow = fs.readFileSync(path.join(__dirname, '../.github/workflows/regression.yml'), 'utf8');
  assert.match(workflow, /permissions:\s*\n\s*contents: read/);
  assert.match(workflow, /cancel-in-progress: true/);
  assert.match(workflow, /:app:lintDebug/);
  assert.match(gradle, /lint \{[\s\S]*abortOnError = true[\s\S]*checkReleaseBuilds = true/);
});
