import assert from 'node:assert/strict';
import { EventEmitter } from 'node:events';
import { PassThrough } from 'node:stream';
import test from 'node:test';
import { mkdtemp, writeFile, rm, open } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { GameBridge, McpAdapter, PROTOCOL_VERSION, MAX_CAPTURE_BYTES, launchAdapter, parseCliArgs } from './adapter.mjs';
import { validateCommandArguments } from './schemas.mjs';

const flush = () => new Promise((resolve) => setImmediate(resolve));
const png = Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jRZkAAAAASUVORK5CYII=', 'base64');
async function waitFor(predicate) {
  for (let attempt = 0; attempt < 200; attempt += 1) {
    if (predicate()) return;
    await new Promise((resolve) => setTimeout(resolve, 5));
  }
  assert.fail('Timed out waiting for asynchronous tool response');
}

class TestChild extends EventEmitter {
  constructor() {
    super();
    this.stdin = new PassThrough();
    this.stdout = new PassThrough();
    this.stderr = new PassThrough();
    this.requests = [];
    let buffered = '';
    this.stdin.setEncoding('utf8');
    this.stdin.on('data', (chunk) => {
      buffered += chunk;
      let newline;
      while ((newline = buffered.indexOf('\n')) !== -1) {
        this.requests.push(JSON.parse(buffered.slice(0, newline)));
        buffered = buffered.slice(newline + 1);
      }
    });
  }

  emitMessage(message) { this.stdout.write(`${JSON.stringify(message)}\n`); }
  ready() { this.emitMessage({ protocol: 1, type: 'event', event: 'ready', data: { state: 'menu' } }); }
  accepted(request, data = {}) { this.emitMessage({ protocol: 1, type: 'accepted', id: request.id, command: request.command, data }); }
  completed(request, data = {}) { this.emitMessage({ protocol: 1, type: 'completed', id: request.id, command: request.command, data }); }
  error(request, code, message) { this.emitMessage({ protocol: 1, type: 'error', id: request.id, command: request.command, code, message }); }
  exit(code = 0) {
    this.emit('exit', code, null);
    this.stdout.end();
    this.stderr.end();
    this.emit('close', code, null);
  }
}

function harness(t) {
  const child = new TestChild();
  const input = new PassThrough();
  const output = new PassThrough();
  const responses = [];
  const logs = [];
  let buffer = '';
  output.setEncoding('utf8');
  output.on('data', (chunk) => {
    buffer += chunk;
    let newline;
    while ((newline = buffer.indexOf('\n')) !== -1) {
      responses.push(JSON.parse(buffer.slice(0, newline)));
      buffer = buffer.slice(newline + 1);
    }
  });
  const bridge = new GameBridge(child, { log: (text) => logs.push(String(text)) });
  const adapter = new McpAdapter(bridge, { input, output, log: (text) => logs.push(String(text)) });
  t.after(() => { adapter.close(); child.exit(); });
  const send = (message) => input.write(`${JSON.stringify(message)}\n`);
  const call = (id, args) => send({ jsonrpc: '2.0', id, method: 'tools/call', params: { name: 'offcraft_command', arguments: args } });
  const initialize = async (version = PROTOCOL_VERSION) => {
    send({ jsonrpc: '2.0', id: 'init', method: 'initialize', params: { protocolVersion: version, capabilities: {}, clientInfo: { name: 'test', version: '1' } } });
    await flush();
    send({ jsonrpc: '2.0', method: 'notifications/initialized' });
    await flush();
  };
  return { child, input, responses, logs, bridge, adapter, send, call, initialize };
}

const exampleCommands = [
  { command: 'act', ticks: 1, buttons: ['forward', 'attack'], slot: 9, capture: 'shot.png' },
  { command: 'new', world: 'World_1' }, { command: 'load', world: 'World_1' },
  { command: 'connect', address: 'localhost:25565' }, { command: 'disconnect' },
  { command: 'switch_world', world: 'World_2', create: true },
  { command: 'cancel', target: 'offcraft-current' },
  { command: 'look', yaw: 180, pitch: -90 }, { command: 'render', path: 'a b.png' },
  { command: 'status' }, { command: 'observe' }, { command: 'inventory' },
  { command: 'gui', action: 'inventory' }, { command: 'key', key: 'ENTER', modifiers: 2 },
  { command: 'click', x: 10, y: 20, button: 2 }, { command: 'text', text: 'Hello 🌍' },
  { command: 'click_slot', sync_id: 0, slot: 9, button: 0, action: 'quick_move' },
  { command: 'render_policy', mode: 'reduced', fps: 2 }, { command: 'help' }, { command: 'quit' },
];

test('CLI is strict and passes argument boundaries without a shell', () => {
  const options = parseCliArgs(['--client', 'C:\\Program Files\\Java\\bin\\java.exe', '--args-json', '["-cp","a b.jar","Main","--world","x y"]', '--cwd', 'C:\\Game Run']);
  assert.deepEqual(options.args, ['-cp', 'a b.jar', 'Main', '--world', 'x y']);
  assert.equal(options.client, 'C:\\Program Files\\Java\\bin\\java.exe');
  assert.equal(options.cwd, 'C:\\Game Run');
  assert.deepEqual(parseCliArgs(['--help']), { help: true });
  for (const args of [[], ['--client'], ['--client', 'java', '--extra', 'x'], ['--client', 'java', '--client', 'java'], ['--client', 'java', '--args-json', '{}'], ['--client', 'java', '--args-json', '[1]'], ['--client', 'java', '--args-json', '['], ['--client', 'java\0bad'], ['--client', 'java', '--args-json', '["\\u0000"]']]) {
    assert.throws(() => parseCliArgs(args));
  }
});

test('command argument boundaries reject invalid and unsafe inputs', () => {
  for (const args of exampleCommands) assert.deepEqual(validateCommandArguments(args), [], args.command);
  for (const action of ['pickup', 'quick_move', 'throw', 'pickup_all']) {
    assert.deepEqual(validateCommandArguments({ command: 'click_slot', sync_id: 0, slot: 3, button: 1, action }), []);
  }
  assert.deepEqual(validateCommandArguments({ command: 'click_slot', sync_id: 0, slot: -999, button: 0, action: 'pickup' }), []);
  assert.deepEqual(validateCommandArguments({ command: 'click_slot', sync_id: 0, slot: 3, button: 40, action: 'swap' }), []);
  for (const args of [null, [], {}, { command: 'missing' }, { command: 'status', id: 'override' }, { command: 'act', ticks: 0 }, { command: 'act', ticks: 1.1 }, { command: 'act', ticks: 1, buttons: ['teleport'] }, { command: 'new', world: '../escape' }, { command: 'look', yaw: Infinity, pitch: 0 }, { command: 'look', yaw: 0, pitch: 91 }, { command: 'key', key: 'ENTER', modifiers: 16 }, { command: 'click_slot', sync_id: 0, slot: -999, button: 0, action: 'quick_move' }, { command: 'click_slot', sync_id: 0, slot: 0, button: 9, action: 'swap' }, { command: 'render_policy', mode: 'reduced', fps: 61 }]) {
    assert.ok(validateCommandArguments(args).length > 0, JSON.stringify(args));
  }
});

test('MCP initializes and lists tools without waiting for game ready', async (t) => {
  const h = harness(t);
  await h.initialize('2099-01-01');
  assert.equal(h.responses[0].result.protocolVersion, PROTOCOL_VERSION);
  assert.deepEqual(h.responses[0].result.capabilities, { tools: { listChanged: false } });
  h.send({ jsonrpc: '2.0', id: 2, method: 'tools/list' });
  h.send({ jsonrpc: '2.0', id: 3, method: 'ping' });
  await flush();
  assert.equal(h.responses.find((response) => response.id === 2).result.tools[0].name, 'offcraft_command');
  assert.ok(h.responses.find((response) => response.id === 2).result.tools.some((tool) => tool.name === 'offcraft_capture'));
  assert.deepEqual(h.responses.find((response) => response.id === 3).result, {});
  assert.equal(h.child.requests.length, 0);
});

test('game ready gates requests; accepted is never a terminal tool result', async (t) => {
  const h = harness(t);
  await h.initialize();
  h.call('action', { command: 'act', ticks: 20 });
  await flush();
  assert.equal(h.child.requests.length, 0);
  h.child.stdout.write('Minecraft log noise\n{"not":"protocol"}\n');
  h.child.stderr.write('native library log\n');
  h.child.ready();
  await flush();
  const request = h.child.requests[0];
  assert.match(request.id, /^offcraft-/);
  h.child.accepted(request);
  await flush();
  assert.equal(h.responses.filter((response) => response.id === 'action').length, 0);
  h.child.completed(request, { elapsedTicks: 20, finished: true });
  await flush();
  const result = h.responses.find((response) => response.id === 'action').result;
  assert.equal(result.isError, false);
  assert.deepEqual(JSON.parse(result.content[0].text), { protocol: 1, type: 'completed', id: request.id, command: 'act', data: { elapsedTicks: 20, finished: true } });
  assert.ok(h.logs.join('').includes('Minecraft log noise'));
  assert.ok(h.logs.join('').includes('native library log'));
});

test('long actions and status correlate independently, including numeric/string MCP IDs', async (t) => {
  const h = harness(t);
  await h.initialize();
  h.child.ready();
  h.call(1, { command: 'act', ticks: 12000 });
  h.call('1', { command: 'status' });
  await flush();
  const action = h.child.requests.find((request) => request.command === 'act');
  const status = h.child.requests.find((request) => request.command === 'status');
  assert.notEqual(action.id, status.id);
  h.child.accepted(action);
  h.child.completed(status, { active_id: action.id });
  await flush();
  assert.equal(h.responses.find((response) => response.id === 1), undefined);
  assert.equal(JSON.parse(h.responses.find((response) => response.id === '1').result.content[0].text).data.active_id, action.id);
  h.child.error(action, 'interrupted', 'Screen interrupted input');
  await flush();
  const failure = h.responses.find((response) => response.id === 1).result;
  assert.equal(failure.isError, true);
  assert.equal(JSON.parse(failure.content[0].text).code, 'interrupted');
});

test('cancellation targets only its own sent command and detaches without awaiting a terminal', async (t) => {
  const h = harness(t);
  await h.initialize();
  h.child.ready();
  h.call('act', { command: 'act', ticks: 12000 });
  h.call('status', { command: 'status' });
  await flush();
  const action = h.child.requests.find((request) => request.command === 'act');
  const status = h.child.requests.find((request) => request.command === 'status');
  h.send({ jsonrpc: '2.0', method: 'notifications/cancelled', params: { requestId: 'act', reason: 'stop' } });
  await flush();
  const cancellation = h.child.requests.find((request) => request.command === 'cancel');
  assert.equal(cancellation.target, action.id);
  assert.notEqual(cancellation.target, status.id);
  h.child.error(cancellation, 'not_cancellable', 'Unsupported operation');
  h.child.completed(status, { state: 'in_world' });
  h.child.error(action, 'cancelled', 'Cancelled');
  await flush();
  assert.equal(h.responses.filter((response) => response.id === 'act').length, 0);
  assert.equal(h.responses.find((response) => response.id === 'status').result.isError, false);
  assert.ok(h.logs.join('').includes('not_cancellable'));
});

test('pre-ready cancellation prevents a game request, and unknown notifications are silent', async (t) => {
  const h = harness(t);
  await h.initialize();
  h.call('cancel-before-ready', { command: 'act', ticks: 1 });
  h.send({ jsonrpc: '2.0', method: 'notifications/cancelled', params: { requestId: 'cancel-before-ready' } });
  h.send({ jsonrpc: '2.0', method: 'notifications/cancelled', params: { requestId: 'unknown' } });
  h.send({ jsonrpc: '2.0', method: 'notifications/cancelled', params: { requestId: null } });
  h.send({ jsonrpc: '2.0', method: 'unknown/notification' });
  h.child.ready();
  await flush();
  assert.equal(h.child.requests.length, 0);
  assert.equal(h.responses.length, 1);
});

test('direct cancellation reports the actual game error rather than synthetic success', async (t) => {
  const h = harness(t);
  await h.initialize();
  h.child.ready();
  h.call('cancel-tool', { command: 'cancel', target: 'not-an-action' });
  await flush();
  h.child.error(h.child.requests[0], 'not_cancellable', 'Only act can be cancelled');
  await flush();
  const result = h.responses.find((response) => response.id === 'cancel-tool').result;
  assert.equal(result.isError, true);
  assert.equal(JSON.parse(result.content[0].text).code, 'not_cancellable');
});

test('malformed JSON, methods, params, IDs and arguments get JSON-RPC errors', async (t) => {
  const h = harness(t);
  h.input.write('{not json}\n');
  h.send([]);
  h.send({ jsonrpc: '2.0', id: null, method: 'ping' });
  h.send({ jsonrpc: '2.0', id: 'fraction', method: 'ping', params: [] });
  h.send({ jsonrpc: '2.0', id: 'unknown', method: 'unknown' });
  h.send({ jsonrpc: '2.0', id: 'premature', method: 'tools/list' });
  await flush();
  assert.equal(h.responses[0].error.code, -32700);
  assert.equal(h.responses[1].error.code, -32600);
  assert.equal(h.responses[2].error.code, -32600);
  assert.equal(h.responses.find((response) => response.id === 'fraction').error.code, -32602);
  assert.equal(h.responses.find((response) => response.id === 'unknown').error.code, -32601);
  assert.equal(h.responses.find((response) => response.id === 'premature').error.code, -32600);
  await h.initialize();
  h.send({ jsonrpc: '2.0', id: 'bad-tool', method: 'tools/call', params: { name: 'not-a-tool', arguments: {} } });
  h.call('bad-args', { command: 'act', ticks: '1' });
  h.send({ jsonrpc: '2.0', id: 'cursor', method: 'tools/list', params: { cursor: 'no-such-page' } });
  h.send({ jsonrpc: '2.0', id: 'extra', method: 'ping', params: { extra: 1 } });
  h.send({ jsonrpc: '2.0', id: 'extra', method: 'ping' });
  await flush();
  for (const id of ['bad-tool', 'bad-args', 'cursor', 'extra']) assert.equal(h.responses.find((response) => response.id === id).error.code, -32602);
  assert.ok(h.responses.some((response) => response.id === null && response.error.code === -32600));
  assert.equal(h.child.requests.length, 0);
});

test('client exit rejects every pending request and never fabricates completed', async (t) => {
  const h = harness(t);
  await h.initialize();
  h.child.ready();
  h.call('act', { command: 'act', ticks: 12000 });
  h.call('status', { command: 'status' });
  await flush();
  h.child.exit(1);
  await flush();
  for (const id of ['act', 'status']) {
    const result = h.responses.find((response) => response.id === id).result;
    assert.equal(result.isError, true);
    const payload = JSON.parse(result.content[0].text);
    assert.equal(payload.source, 'adapter');
    assert.equal(payload.code, 'client_exited');
    assert.equal(payload.type, 'error');
  }
});

test('game stdout errors reject every pending tool call as client_stdout_failed', async (t) => {
  const h = harness(t);
  await h.initialize();
  h.child.ready();
  h.call('act', { command: 'act', ticks: 12000 });
  h.call('status', { command: 'status' });
  await flush();
  const requests = [...h.child.requests];
  assert.equal(requests.length, 2);
  assert.doesNotThrow(() => h.child.stdout.emit('error', new Error('game stdout read failed')));
  await flush();
  h.call('after-failure', { command: 'observe' });
  await flush();
  for (const id of ['act', 'status', 'after-failure']) {
    const responses = h.responses.filter((response) => response.id === id);
    assert.equal(responses.length, 1);
    assert.equal(responses[0].result.isError, true);
    const payload = JSON.parse(responses[0].result.content[0].text);
    assert.equal(payload.source, 'adapter');
    assert.equal(payload.type, 'error');
    assert.equal(payload.code, 'client_stdout_failed');
    assert.equal(payload.message, 'game stdout read failed');
    if (id !== 'after-failure') {
      const request = requests.find((request) => request.command === id);
      assert.equal(payload.game_id, request.id);
      assert.equal(payload.command, request.command);
    }
  }
  assert.deepEqual(h.child.requests, requests);
});

test('MCP input errors end game stdin orderly, log once and suppress pending replies', async (t) => {
  const h = harness(t);
  await h.initialize();
  h.child.ready();
  h.call('act', { command: 'act', ticks: 12000 });
  h.call('status', { command: 'status' });
  await flush();
  const requests = [...h.child.requests];
  assert.equal(requests.length, 2);
  const responses = [...h.responses];
  let stdinFinishes = 0;
  h.child.stdin.on('finish', () => { stdinFinishes += 1; });
  // Queue a successful reply as well as retaining an unresolved call.
  h.child.completed(requests.find((request) => request.command === 'status'), { state: 'in_world' });
  assert.doesNotThrow(() => h.input.emit('error', new Error('MCP input read failed')));
  await flush();
  assert.equal(h.child.stdin.writableEnded, true);
  assert.equal(stdinFinishes, 1);
  h.child.completed(requests.find((request) => request.command === 'act'), { elapsedTicks: 12000 });
  h.call('after-failure', { command: 'observe' });
  h.send({ jsonrpc: '2.0', id: 'ping-after-failure', method: 'ping' });
  await flush();
  assert.deepEqual(h.responses, responses);
  assert.deepEqual(h.child.requests, requests);
  assert.deepEqual(h.logs.filter((line) => line.startsWith('offcraft MCP input:')), ['offcraft MCP input: MCP input read failed\n']);
  assert.equal(stdinFinishes, 1);
});

test('stdio EOF ends game stdin orderly without a kill or fake response', async (t) => {
  const h = harness(t);
  await h.initialize();
  h.child.ready();
  h.call('act', { command: 'act', ticks: 12000 });
  await flush();
  h.input.end();
  await flush();
  assert.equal(h.child.stdin.writableEnded, true);
  assert.equal(h.responses.filter((response) => response.id === 'act').length, 0);
});

test('quit response drains after process exit before transport closes', async (t) => {
  const child = new TestChild();
  const input = new PassThrough();
  const output = new PassThrough();
  const error = new PassThrough();
  let emitted = '';
  output.setEncoding('utf8');
  output.on('data', (chunk) => { emitted += chunk; });
  const launched = launchAdapter({ client: 'java.exe', args: ['-cp', 'a b.jar', 'Main'], cwd: 'run' }, {
    input, output, error,
    spawnClient: () => child,
  });
  t.after(() => { launched.adapter.close(); child.exit(); });
  input.write(`${JSON.stringify({ jsonrpc: '2.0', id: 1, method: 'initialize', params: { protocolVersion: PROTOCOL_VERSION, capabilities: {}, clientInfo: { name: 'test', version: '1' } } })}\n`);
  await flush();
  input.write(`${JSON.stringify({ jsonrpc: '2.0', method: 'notifications/initialized' })}\n`);
  child.ready();
  input.write(`${JSON.stringify({ jsonrpc: '2.0', id: 2, method: 'tools/call', params: { name: 'offcraft_command', arguments: { command: 'quit' } } })}\n`);
  await flush();
  child.emit('exit', 0, null);
  child.completed(child.requests[0], { state: 'stopped' });
  child.stdout.end();
  child.stderr.end();
  child.emit('close', 0, null);
  await launched.closed;
  const response = emitted.split('\n').filter(Boolean).map((line) => JSON.parse(line)).find((message) => message.id === 2);
  assert.equal(response.result.isError, false);
  assert.equal(JSON.parse(response.result.content[0].text).data.state, 'stopped');
});

test('capture reads only the successful render path and command tools remain text-only', async (t) => {
  const directory = await mkdtemp(join(tmpdir(), 'offcraft-image-'));
  t.after(() => rm(directory, { recursive: true, force: true }));
  const actual = join(directory, 'fresh.png');
  await writeFile(actual, png);
  const h = harness(t);
  await h.initialize();
  h.child.ready();
  h.send({ jsonrpc: '2.0', id: 'image', method: 'tools/call', params: { name: 'offcraft_capture', arguments: { path: join(directory, 'not-written.png') } } });
  await flush();
  const request = h.child.requests[0];
  assert.equal(request.command, 'render');
  h.child.accepted(request);
  await flush();
  assert.equal(h.responses.find((response) => response.id === 'image'), undefined);
  h.child.completed(request, { capture: { path: actual, width: 1, height: 1 } });
  await waitFor(() => h.responses.some((response) => response.id === 'image'));
  const result = h.responses.find((response) => response.id === 'image').result;
  assert.equal(result.isError, false);
  assert.equal(result.content[1].mimeType, 'image/png');
  assert.deepEqual(Buffer.from(result.content[1].data, 'base64'), png);
  assert.equal(JSON.parse(result.content[0].text).id, request.id);
  h.call('text-only', { command: 'render', path: actual });
  await flush();
  h.child.completed(h.child.requests[1], { capture: { path: actual } });
  await flush();
  assert.deepEqual(h.responses.find((response) => response.id === 'text-only').result.content.map((part) => part.type), ['text']);
});

test('capture refuses failed, missing, non-PNG, oversized and non-file results explicitly', async (t) => {
  const directory = await mkdtemp(join(tmpdir(), 'offcraft-image-bad-'));
  t.after(() => rm(directory, { recursive: true, force: true }));
  const bad = join(directory, 'not-png');
  await writeFile(bad, 'not a PNG image');
  const large = join(directory, 'large.png');
  const file = await open(large, 'wx');
  await file.write(png);
  await file.truncate(MAX_CAPTURE_BYTES + 1);
  await file.close();
  const h = harness(t);
  await h.initialize();
  h.child.ready();
  const cases = [undefined, join(directory, 'missing.png'), bad, large, directory];
  for (const [index, path] of cases.entries()) {
    const id = `invalid-image-${index}`;
    h.send({ jsonrpc: '2.0', id, method: 'tools/call', params: { name: 'offcraft_capture', arguments: {} } });
    await flush();
    const request = h.child.requests.at(-1);
    h.child.completed(request, path === undefined ? {} : { capture: { path } });
    await waitFor(() => h.responses.some((response) => response.id === id));
    const result = h.responses.find((response) => response.id === id).result;
    assert.equal(result.isError, true);
    assert.deepEqual(result.content.map((part) => part.type), ['text']);
    const failure = JSON.parse(result.content[0].text);
    assert.equal(failure.source, 'adapter');
    assert.equal(failure.code, 'capture_read_failed');
    assert.equal(failure.game_response.id, request.id);
  }
  h.send({ jsonrpc: '2.0', id: 'game-failed', method: 'tools/call', params: { name: 'offcraft_capture', arguments: { path: bad } } });
  await flush();
  h.child.error(h.child.requests.at(-1), 'capture_failed', 'write failed');
  await flush();
  const result = h.responses.find((response) => response.id === 'game-failed').result;
  assert.equal(JSON.parse(result.content[0].text).code, 'capture_failed');
  assert.deepEqual(result.content.map((part) => part.type), ['text']);
});

test('capture validates arguments and supports silent pre-ready cancellation', async (t) => {
  const h = harness(t);
  await h.initialize();
  for (const [index, args] of [null, { path: '' }, { path: 'bad\0path' }, { command: 'render' }].entries()) {
    h.send({ jsonrpc: '2.0', id: `bad-image-${index}`, method: 'tools/call', params: { name: 'offcraft_capture', arguments: args } });
  }
  await flush();
  for (let index = 0; index < 4; index += 1) assert.equal(h.responses.find((response) => response.id === `bad-image-${index}`).error.code, -32602);
  h.send({ jsonrpc: '2.0', id: 'cancel-image', method: 'tools/call', params: { name: 'offcraft_capture', arguments: {} } });
  h.send({ jsonrpc: '2.0', method: 'notifications/cancelled', params: { requestId: 'cancel-image' } });
  h.child.ready();
  await flush();
  assert.equal(h.child.requests.length, 0);
  assert.equal(h.responses.find((response) => response.id === 'cancel-image'), undefined);
});
