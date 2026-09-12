const fs = require('node:fs');
const path = require('node:path');

const JS_MODULES = [
  'app-shell.js',
  'library-ui.js',
  'settings-ui.js',
  'controller-editor-ui.js',
  'screen-editor.js',
  'screen-editor-interactions.js',
  'categories-ui.js',
  'layout-profiles.js',
];

const CSS_MODULES = [
  'style-core.css',
  'style-screen-editor.css',
  'style-controls.css',
  'style-layout-polish.css',
  'style-editor-modern.css',
  'style-features.css',
];

function read(root, rel) {
  return fs.readFileSync(path.join(root, rel), 'utf8');
}

function readWebJs(root) {
  return JS_MODULES.map(name => read(root, `app/src/main/assets/retra/${name}`)).join('\n');
}

function readWebCss(root) {
  return CSS_MODULES.map(name => read(root, `app/src/main/assets/retra/${name}`)).join('\n');
}

module.exports = { JS_MODULES, CSS_MODULES, read, readWebJs, readWebCss };
