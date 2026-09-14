const fs = require('fs');
const path = require('path');
const assert = require('assert');

const root = path.resolve(__dirname, '..');
const controller = fs.readFileSync(path.join(root, 'app/src/main/java/com/retra/emulator/ShaderController.kt'), 'utf8');
const view = fs.readFileSync(path.join(root, 'app/src/main/java/com/retra/emulator/ShaderGameView.kt'), 'utf8');
const main = fs.readFileSync(path.join(root, 'app/src/main/java/com/retra/emulator/MainActivity.kt'), 'utf8');
const session = fs.readFileSync(path.join(root, 'app/src/main/java/com/retra/emulator/EmulationSessionManager.kt'), 'utf8');
const html = fs.readFileSync(path.join(root, 'app/src/main/assets/retra/index.html'), 'utf8');

assert(controller.includes('private val isGameplayVisible: () -> Boolean'), 'Shader controller must know whether the native gameplay surface is visible');
assert(controller.includes('if (!isGameplayVisible())'), 'Settings selection must defer GL compilation while gameplay is hidden');
assert(main.includes('binding.emulatorOverlay.visibility == View.VISIBLE'), 'Gameplay visibility callback must be wired to the native overlay');
assert(session.includes('shaderController.applySelection(showToast = false)'), 'Selected shader must auto-activate after gameplay becomes visible');
assert(view.includes('stageFragmentShader'), 'Shader selection must stage compilation');
assert(view.includes('applyPendingFragmentShaderIfNeeded()'), 'Compilation must run from the GL draw path with a current context');
assert(view.includes('ensureFragmentPrecision'), 'Fragment precision must be normalized for GLES compatibility');
assert(view.includes('compatibility-mode'), 'Renderer must provide a raw-source compatibility fallback');
assert(view.includes('Shader compile failed on this GPU'), 'Blank OEM compiler logs must get a useful fallback message');
assert(html.includes('IMPORT CUSTOM'), 'Custom file import must be clearly separated from built-in presets');
assert(!html.includes('>INSTALL SHADER</button>'), 'Built-in shaders should not look like separately installed packages');

console.log('shader lazy-activation compatibility regression: ok');
