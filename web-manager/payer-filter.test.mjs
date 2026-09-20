import assert from "node:assert/strict";
import { matchesPayer } from "./payer-filter.js";

assert.equal(matchesPayer("ALL", "Tuấn"), true);
assert.equal(matchesPayer("UNKNOWN", " "), true);
assert.equal(matchesPayer("UNKNOWN", "Hương"), false);
assert.equal(matchesPayer("Tuấn", " Tuấn "), true);
assert.equal(matchesPayer("Tuấn", "Hương"), false);

console.log("payer filter semantics: PASS");
