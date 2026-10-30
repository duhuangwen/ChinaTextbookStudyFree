import assert from 'node:assert/strict';
import { pickDirectionalCandidate, type FocusGeometry } from '../../apps/web/src/lib/tvNavigation';

type Box = FocusGeometry & { id: string };
const box = (id: string, left: number, top: number, right: number, bottom: number): Box => ({ id, left, top, right, bottom });
const pick = (current: Box, candidates: Box[], direction: 'up' | 'down' | 'left' | 'right') =>
  pickDirectionalCandidate(current, candidates.filter(c => c !== current), direction)?.id ?? null;

// GradePicker at 1920x1080: a 2x3 card grid with the "继续" button under the right column.
const g1 = box('g1', 210, 275, 945, 435), g2 = box('g2', 975, 275, 1710, 435);
const g3 = box('g3', 210, 463, 945, 623), g4 = box('g4', 975, 463, 1710, 623);
const g5 = box('g5', 210, 650, 945, 810), g6 = box('g6', 975, 650, 1710, 810);
const next = box('continue', 1198, 943, 1350, 1020);
const picker = [g1, g2, g3, g4, g5, g6, next];

assert.equal(pick(g1, picker, 'down'), 'g3', 'grid rows still move straight down');
assert.equal(pick(g5, picker, 'down'), 'continue', 'the bottom-left card must reach the off-axis footer action');
assert.equal(pick(g6, picker, 'down'), 'continue');
assert.equal(pick(next, picker, 'up'), 'g6', 'up from the footer returns to the column above it');

// Footer action far to the left of the last card: up still finds a row instead of staying put.
const footerLeft = box('back', 40, 943, 160, 1020);
assert.equal(pick(footerLeft, [g5, g6, footerLeft], 'up'), 'g5');

// Horizontal moves keep the cone: an off-cone control to the left is never a fallback target.
const farTopLeft = box('far', 1300, 0, 1400, 50);
const corner = box('corner', 1500, 900, 1700, 1000);
assert.equal(pick(corner, [farTopLeft, corner], 'left'), null, 'left must not fall back outside the cone');

// Nothing below at all: down stays put.
assert.equal(pick(next, picker, 'down'), null);

// Fallback only applies to controls fully past the current edge, not to overlapping siblings.
const tall = box('tall', 0, 0, 100, 1000);
const sideBySide = box('side', 1000, 900, 1100, 960);
assert.equal(pick(tall, [tall, sideBySide], 'down'), null, 'a control beside the current one is not "below" it');

console.log('PASS: grid rows, off-axis footer fallback (down/up), horizontal cone, empty edges.');
