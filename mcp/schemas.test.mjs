import assert from 'node:assert/strict';
import test from 'node:test';
import { validateCommandArguments, validateCaptureArguments } from './schemas.mjs';

const valid = (args) => assert.deepEqual(validateCommandArguments(args), []);
const invalid = (args) => assert.notDeepEqual(validateCommandArguments(args), []);

test('world seeds preserve both signed 64-bit boundaries without numeric rounding', () => {
  for (const seed of ['-9223372036854775808', '9223372036854775807', '0']) {
    valid({ command: 'new', world: 'seed-test', seed, game_mode: 'survival', difficulty: 'hard' });
  }
  for (const seed of ['-9223372036854775809', '9223372036854775808', '1.5', 'NaN', 42, '', null]) {
    invalid({ command: 'new', world: 'seed-test', seed });
  }
});

test('creation settings cannot silently alter an existing world load', () => {
  valid({ command: 'switch_world', world: 'another', create: true, seed: '42', game_mode: 'adventure' });
  invalid({ command: 'switch_world', world: 'another', seed: '42' });
  invalid({ command: 'switch_world', world: 'another', create: false, difficulty: 'easy' });
  invalid({ command: 'load', world: 'another', seed: '42' });
});

test('message cursors, item slot identity and one-shot input reject unsafe arguments', () => {
  valid({ command: 'messages', after: Number.MAX_SAFE_INTEGER, limit: 256 });
  for (const after of [-1, 1.1, Number.MAX_SAFE_INTEGER + 1]) invalid({ command: 'messages', after });
  for (const limit of [0, 257, 1.5]) invalid({ command: 'messages', limit });
  valid({ command: 'item', sync_id: 0, slot: 40 });
  invalid({ command: 'item', sync_id: 0, slot: -999 });
  invalid({ command: 'item', slot: 40 });
  valid({ command: 'act', ticks: 20, buttons: ['forward', 'sprint'] });
  for (const action of ['drop', 'drop_stack', 'swap_hands']) valid({ command: 'press', action });
  invalid({ command: 'press', action: 'sprint' });
  invalid({ command: 'press', action: 'drop', ticks: 20 });
});

test('capture accepts only an explicit optional output path, never a read-file parameter', () => {
  assert.deepEqual(validateCaptureArguments({}), []);
  assert.deepEqual(validateCaptureArguments({ path: 'output/a b.png' }), []);
  for (const args of [null, { path: '' }, { file: 'existing.png' }, { path: 1 }, { command: 'render' }]) {
    assert.notDeepEqual(validateCaptureArguments(args), []);
  }
});
