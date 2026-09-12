const test = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");

const source = fs.readFileSync(path.join(__dirname, "..", "app/src/main/java/com/retra/emulator/GameplayController.kt"), "utf8");

test("quick save controller button writes to slot 0 and shows compact saved notice", () => {
  assert.match(source, /internal fun MainActivity.quickSave\(\) \{[\s\S]*saveStateToSlot\(0, successMessage = \"Saved\", failureMessage = \"Could not save\"\)/);
});

test("quick load controller button loads slot 0 and shows compact loaded notice", () => {
  assert.match(source, /internal fun MainActivity.quickLoad\(\) \{[\s\S]*loadStateFromSlot\(0, successMessage = \"Loaded\", failureMessage = \"Could not load\"\)/);
  assert.match(source, /No quick save yet/);
});
