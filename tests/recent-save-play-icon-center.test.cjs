const test = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");

const root = path.resolve(__dirname, "..");
const css = fs.readFileSync(path.join(root, "app/src/main/assets/retra/style-features.css"), "utf8");

test("Recent-save play icon is visually centered with the same circle size", () => {
  assert.match(css, /#detailEntries \.entry-download\{[\s\S]*width:42px;[\s\S]*height:42px;/);
  assert.match(css, /#detailEntries \.entry-download \.recent-save-play-icon\{[\s\S]*width:27px;[\s\S]*height:27px;[\s\S]*transform:translateX\(-0\.8px\);/);
});
