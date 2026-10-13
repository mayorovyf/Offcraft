import assert from 'node:assert/strict';
import { EventEmitter } from 'node:events';
import { PassThrough } from 'node:stream';
import { closeSync } from 'node:fs';
import { mkdtemp, readFile, rm, stat, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import test from 'node:test';
import { launchClient, CommandValidationError } from './sdk.mjs';
import { parseCliArgs } from './adapter.mjs';
import { SessionRecorder, RECORD_QUEUE_LIMIT, DEFAULT_RECORD_MAX_BYTES } from './recording.mjs';

const flush = () => new Promise((resolve) => setImmediate(resolve));
class TestChild extends EventEmitter {
  constructor() {
    super();
    this.stdin = new PassThrough();
    this.stdout = new PassThrough();
    this.stderr = new PassThrough();
    this.requests = [];
    let buffer = '';
    this.stdin.setEncoding('utf8');
    this.stdin.on('data', (chunk) => {
      buffer += chunk;
      let newline;
      while ((newline = buffer.indexOf('\n')) !== -1) {
        this.requests.push(JSON.parse(buffer.slice(0, newline)));
        buffer = buffer.slice(newline + 1);
      }
    });
  }
  message(message) { this.stdout.write(`${JSON.stringify(message)}\n`); }
  ready() { this.message({ protocol: 1, type: 'event', event: 'ready', data: { state: 'menu' } }); }
  complete(request, data = {}) { this.message({ protocol: 1, type: 'completed', id: request.id, command: request.command, data }); }
  exit(code = 0) {
    this.emit('exit', code, null);
    this.stdout.end();
    this.stderr.end();
    this.emit('close', code, null);
  }
}
function clientHarness(t, options = {}, observers = []) {
  const child = new TestChild();
  const logs = [];
  const client = launchClient({ client: 'java.exe', ...options }, { spawnClient: () => child, log: (text) => logs.push(String(text)), observers });
  t.after(async () => { const closing = client.close(); child.exit(); await closing.catch(() => {}); });
  return { child, client, logs };
}

test('SDK awaits actual ready, validates shared arguments, and preserves terminal game errors', async (t) => {
  const h = clientHarness(t);
  let ready = false;
  const waiting = h.client.ready().then((message) => { ready = true; return message; });
  const args = { command: 'act', ticks: 2, buttons: ['forward'] };
  const handle = h.client.submit(args);
  args.ticks = 999;
  args.buttons[0] = 'back';
  assert.match(handle.id, /^offcraft-/);
  await flush();
  assert.equal(ready, false);
  assert.equal(h.child.requests.length, 0);
  assert.throws(() => h.client.submit({ command: 'act', ticks: 0 }), CommandValidationError);
  await assert.rejects(h.client.command({ command: 'status', id: 'fake' }), CommandValidationError);
  h.child.ready();
  assert.equal((await waiting).event, 'ready');
  assert.equal(h.child.requests[0].id, handle.id);
  assert.equal(h.child.requests[0].ticks, 2);
  assert.deepEqual(h.child.requests[0].buttons, ['forward']);
  let terminal = false;
  void handle.promise.then(() => { terminal = true; });
  h.child.message({ protocol: 1, type: 'accepted', id: handle.id, command: 'act' });
  await flush();
  assert.equal(terminal, false);
  h.child.message({ protocol: 1, type: 'error', id: handle.id, command: 'act', code: 'busy', message: 'busy' });
  assert.equal((await handle.promise).code, 'busy');
});

test('SDK aborts before readiness without sending; sent action abort targets only its actual game ID', async (t) => {
  const h = clientHarness(t);
  const before = new AbortController();
  before.abort();
  await assert.rejects(h.client.command({ command: 'act', ticks: 1 }, { signal: before.signal }), { name: 'AbortError' });
  const controller = new AbortController();
  const queued = h.client.submit({ command: 'act', ticks: 3 }, { signal: controller.signal });
  const rejected = assert.rejects(queued.promise, { name: 'AbortError' });
  controller.abort();
  await rejected;
  h.child.ready();
  assert.equal(h.child.requests.length, 0);
  const running = new AbortController();
  const action = h.client.submit({ command: 'act', ticks: 100 }, { signal: running.signal });
  const status = h.client.submit({ command: 'status' });
  const aborted = assert.rejects(action.promise, { name: 'AbortError' });
  running.abort();
  await aborted;
  const cancellation = h.child.requests.find((request) => request.command === 'cancel');
  assert.equal(cancellation.target, action.id);
  assert.notEqual(cancellation.target, status.id);
  h.child.complete(cancellation, { released: true });
  h.child.complete(h.child.requests.find((request) => request.id === status.id), { state: 'in_world' });
  assert.equal((await status.promise).data.state, 'in_world');
});

test('SDK manual cancellation uses actual target and reports its game terminal response', async (t) => {
  const h = clientHarness(t);
  h.child.ready();
  const action = h.client.submit({ command: 'act', ticks: 100 });
  const cancellation = h.client.cancel(action.id);
  const request = h.child.requests.at(-1);
  assert.equal(request.target, action.id);
  h.child.complete(request, { cancelled: action.id });
  assert.equal((await cancellation).data.cancelled, action.id);
  h.child.message({ protocol: 1, type: 'error', id: action.id, command: 'act', code: 'cancelled', message: 'Cancelled' });
  assert.equal((await action.promise).code, 'cancelled');
});

test('SDK ready and commands reject startup failures without hanging', async (t) => {
  const h = clientHarness(t);
  const ready = assert.rejects(h.client.ready(), { code: 'client_spawn_failed' });
  const command = assert.rejects(h.client.command({ command: 'status' }), { code: 'client_spawn_failed' });
  h.child.emit('error', new Error('executable missing'));
  await Promise.all([ready, command]);
  await assert.rejects(h.client.command({ command: 'observe' }), { code: 'client_spawn_failed' });
});

test('SDK close uses orderly EOF, rejects pending work, and waits for child close', async (t) => {
  const h = clientHarness(t);
  const ready = assert.rejects(h.client.ready(), { code: 'adapter_closed' });
  const pending = assert.rejects(h.client.command({ command: 'status' }), { code: 'adapter_closed' });
  let closed = false;
  const closing = h.client.close().then((status) => { closed = true; return status; });
  assert.equal(h.child.stdin.writableEnded, true);
  await Promise.all([ready, pending]);
  assert.equal(closed, false);
  h.child.exit();
  assert.equal((await closing).code, 0);
});

test('SDK quit waits for terminal after exit and pipe close; repeated quit shares one request', async (t) => {
  const h = clientHarness(t);
  h.child.ready();
  const quitting = h.client.quit();
  assert.equal(h.client.quit(), quitting);
  let done = false;
  void quitting.then(() => { done = true; });
  h.child.emit('exit', 0, null);
  h.child.complete(h.child.requests[0], { state: 'stopped' });
  await flush();
  assert.equal(done, false);
  h.child.exit();
  assert.equal((await quitting).data.state, 'stopped');
  assert.equal(h.child.requests.filter((request) => request.command === 'quit').length, 1);
});

test('observers cannot alter correlation or poison transport, including asynchronous failures', async (t) => {
  const seen = [];
  const h = clientHarness(t, {}, [
    (direction, message) => { seen.push(direction); message.id = 'tampered'; message.event = 'tampered'; },
    () => { throw new Error('observer broke'); },
    async () => { throw new Error('async observer broke'); },
  ]);
  h.child.ready();
  const handle = h.client.submit({ command: 'status' });
  h.child.complete(h.child.requests[0], { state: 'menu' });
  assert.equal((await handle.promise).id, handle.id);
  await flush();
  assert.ok(seen.includes('event') && seen.includes('request') && seen.includes('response'));
  assert.ok(h.logs.join('').includes('observer broke'));
  assert.ok(h.logs.join('').includes('async observer broke'));
});

test('recording CLI strictly validates positive integer budget and requires a path', () => {
  assert.equal(parseCliArgs(['--client', 'java', '--record', 'session.jsonl', '--record-max-bytes', '1024']).recordMaxBytes, 1024);
  for (const value of ['0', '-1', '1.5', '1e3', 'NaN', 'Infinity', ' 12', '12x', '9007199254740992']) {
    assert.throws(() => parseCliArgs(['--client', 'java', '--record', 'session.jsonl', '--record-max-bytes', value]));
  }
  assert.throws(() => parseCliArgs(['--client', 'java', '--record-max-bytes', '1024']));
});

test('recording is off by default and exclusively creates protocol-only JSONL with orderly flush', async (t) => {
  const directory = await mkdtemp(join(tmpdir(), 'offcraft-record-'));
  t.after(() => rm(directory, { recursive: true, force: true }));
  const off = clientHarness(t);
  assert.equal(off.client.recording, null);
  const path = join(directory, 'session.jsonl');
  const h = clientHarness(t, { record: path });
  assert.equal(h.client.recording.maxBytes, DEFAULT_RECORD_MAX_BYTES);
  assert.throws(() => launchClient({ client: 'java', record: path }, { spawnClient: () => new TestChild(), log: () => {} }), { code: 'EEXIST' });
  h.child.stdout.write('Minecraft raw stdout log\n');
  h.child.stderr.write('Minecraft raw stderr log\n');
  h.child.ready();
  const handle = h.client.submit({ command: 'render' });
  h.child.message({ protocol: 1, type: 'accepted', id: handle.id, command: 'render' });
  h.child.complete(h.child.requests[0], { capture: { path: 'frame.png', width: 640, height: 360 } });
  await handle.promise;
  const closing = h.client.close();
  h.child.exit();
  await closing;
  const text = await readFile(path, 'utf8');
  const records = text.trim().split('\n').map((line) => JSON.parse(line));
  assert.deepEqual(records.map((entry) => entry.direction), ['event', 'request', 'response', 'response']);
  assert.equal(records[1].message.id, handle.id);
  assert.ok(records.every((entry) => typeof entry.time === 'string'));
  assert.ok(!text.includes('Minecraft raw') && !text.includes('base64'));
  assert.equal(h.client.recording.bytes, Buffer.byteLength(text));
});

test('recording byte exhaustion stops visibly without falsifying game responses', async (t) => {
  const directory = await mkdtemp(join(tmpdir(), 'offcraft-record-budget-'));
  t.after(() => rm(directory, { recursive: true, force: true }));
  const path = join(directory, 'session.jsonl');
  const h = clientHarness(t, { record: path, recordMaxBytes: 200 });
  h.child.ready();
  const handle = h.client.submit({ command: 'status' });
  h.child.complete(h.child.requests[0], { state: 'menu' });
  assert.equal((await handle.promise).type, 'completed');
  const closing = h.client.close();
  h.child.exit();
  await closing;
  assert.ok((await stat(path)).size <= 200);
  assert.equal(h.client.recording.stopped, 'byte_limit');
  assert.ok(h.logs.join('').includes('byte_limit'));
  const text = await readFile(path, 'utf8');
  for (const line of text.trim().split('\n').filter(Boolean)) JSON.parse(line);
});

test('recording queue exhaustion is bounded and reports dropped recording, not game failure', async (t) => {
  const directory = await mkdtemp(join(tmpdir(), 'offcraft-record-queue-'));
  t.after(() => rm(directory, { recursive: true, force: true }));
  const path = join(directory, 'session.jsonl');
  const h = clientHarness(t, { record: path });
  for (let index = 0; index < RECORD_QUEUE_LIMIT + 10; index += 1) h.child.message({ protocol: 1, type: 'event', event: 'loading', data: { index } });
  assert.equal(h.client.recording.stopped, 'queue_limit');
  h.child.ready();
  const pending = h.client.command({ command: 'status' });
  h.child.complete(h.child.requests[0]);
  assert.equal((await pending).type, 'completed');
  const closing = h.client.close();
  h.child.exit();
  await closing;
  const lines = (await readFile(path, 'utf8')).trim().split('\n');
  assert.ok(lines.length <= RECORD_QUEUE_LIMIT);
  assert.ok(h.logs.join('').includes('queue_limit'));
});

test('recording does not overwrite existing files or accept invalid SDK budgets', async (t) => {
  const directory = await mkdtemp(join(tmpdir(), 'offcraft-record-exclusive-'));
  t.after(() => rm(directory, { recursive: true, force: true }));
  const path = join(directory, 'existing');
  await writeFile(path, 'owned by somebody else');
  let spawned = false;
  const dependencies = { spawnClient: () => { spawned = true; return new TestChild(); }, log: () => {} };
  assert.throws(() => launchClient({ client: 'java', record: path }, dependencies), { code: 'EEXIST' });
  assert.equal(spawned, false);
  assert.equal(await readFile(path, 'utf8'), 'owned by somebody else');
  for (const maxBytes of [0, -1, 1.1, Infinity, '1024']) assert.throws(() => launchClient({ client: 'java', record: join(directory, 'new'), recordMaxBytes: maxBytes }, dependencies));
});

test('recording I/O failures are explicit and close rejects instead of claiming success', async (t) => {
  const directory = await mkdtemp(join(tmpdir(), 'offcraft-record-failure-'));
  t.after(() => rm(directory, { recursive: true, force: true }));
  const logs = [];
  const recorder = new SessionRecorder(join(directory, 'session.jsonl'), { log: (text) => logs.push(text) });
  // Deterministically exercise the actual asynchronous write error path.
  closeSync(recorder.fd);
  recorder.observe('event', { protocol: 1, type: 'event', event: 'ready' });
  await assert.rejects(recorder.close(), { code: 'recording_failed' });
  assert.equal(recorder.status.stopped, 'write_failed');
  assert.ok(logs.join('').includes('Cannot record session'));
});
