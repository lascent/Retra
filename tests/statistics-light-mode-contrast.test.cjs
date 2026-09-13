const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const root = path.resolve(__dirname, '..');
const css = fs.readFileSync(path.join(root, 'app/src/main/assets/retra/style-appearance-light.css'), 'utf8');

test('Light mode gives the Statistics page readable dark text and icons', () => {
  assert.match(css, /body\[data-resolved-mode=\"light\"\] #statisticsPage \.stats-section h3/);
  assert.match(css, /body\[data-resolved-mode=\"light\"\] #statisticsPage \.stats-panel/);
  assert.match(css, /body\[data-resolved-mode=\"light\"\] #statisticsPage \.stats-metric strong[\s\S]*color:var\(--text\)/);
  assert.match(css, /body\[data-resolved-mode=\"light\"\] #statisticsPage \.stats-icon[\s\S]*color:var\(--text\)/);
  assert.match(css, /body\[data-resolved-mode=\"light\"\] #statisticsPage \.stats-empty-message/);
});
