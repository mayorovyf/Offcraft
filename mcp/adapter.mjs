import { spawn } from 'node:child_process';
import { randomUUID } from 'node:crypto';
import { createInterface } from 'node:readline';
import { COMMAND_TOOL, validateCommandArguments } from './schemas.mjs';

export const PROTOCOL_VERSION = '2024-11-05';
export const USAGE = 'Usage: node mcp/index.mjs --client EXECUTABLE [--args-json \'["arg", ...]\'] [--cwd DIRECTORY]';

const record = (value) => value !== null && typeof value === 'object' && !Array.isArray(value);
const requestId = (value) => typeof value === 'string' || (typeof value === 'number' && Number.isSafeInteger(value));
const nonempty = (value) => typeof value === 'string' && value.trim().length > 0 && !value.includes('\0');

export function parseCliArgs(argv) {
  if (argv.length === 1 && argv[0] === '--help') return { help: true };
  const options = { args: [], cwd: process.cwd() };
  const seen = new Set();
  for (let index = 0; index < argv.length; index += 2) {
    const flag = argv[index];
    if (!['--client', '--args-json', '--cwd'].includes(flag)) throw new Error(`Unknown option: ${flag}`);
    if (seen.has(flag)) throw new Error(`Duplicate option: ${flag}`);
    seen.add(flag);
    const value = argv[index + 1];
    if (value === undefined || value.startsWith('--')) throw new Error(`Missing value for ${flag}`);
    if (flag === '--args-json') {
      let args;
      try { args = JSON.parse(value); } catch { throw new Error('--args-json must be a JSON array of strings'); }
      if (!Array.isArray(args) || args.some((arg) => typeof arg !== 'string' || arg.includes('\0'))) throw new Error('--args-json must be a JSON array of strings without NUL characters');
      options.args = args;
    } else {
      if (!nonempty(value)) throw new Error(`${flag} must be a nonempty string without NUL characters`);
      options[flag === '--client' ? 'client' : 'cwd'] = value;
    }
  }
  if (!options.client) throw new Error('--client is required');
  return options;
}

export class GameTransportError extends Error {
  constructor(code, message) {
    super(message);
    this.name = 'GameTransportError';
    this.code = code;
  }
}

class AbandonedRequest extends Error {}

/** Correlates unique game IDs independently of the MCP request IDs. */
export class GameBridge {
  constructor(child, { log = (text) => process.stderr.write(text) } = {}) {
    this.child = child;
    this.log = log;
    this.pending = new Map();
    this.ready = false;
    this.accepting = true;
    this.failure = null;
    this.exited = false;
    this.closed = new Promise((resolve) => { this.resolveClosed = resolve; });
    this.lines = createInterface({ input: child.stdout, crlfDelay: Infinity, terminal: false });
    this.lines.on('line', (line) => this.receive(line));
    this.lines.on('error', (error) => this.fail(new GameTransportError('client_stdout_failed', error.message)));
    child.stderr.on('data', (chunk) => this.log(chunk));
    child.stdin.on('error', (error) => this.fail(new GameTransportError('client_stdin_failed', error.message)));
    child.stdout.on('end', () => this.fail(new GameTransportError('client_stdout_closed', 'Game client stdout closed before all pending requests completed')));
    child.stderr.on('error', (error) => this.log(`offcraft client stderr: ${error.message}\n`));
    child.on('error', (error) => {
      this.log(`offcraft client: ${error.message}\n`);
      this.fail(new GameTransportError('client_spawn_failed', error.message));
    });
    child.on('exit', (code, signal) => {
      this.exited = true;
    });
    // close follows exit AND drains the pipes, preserving quit's final response.
    child.on('close', (code, signal) => {
      this.exited = true;
      this.fail(new GameTransportError('client_exited', `Game client exited (code=${code}, signal=${signal ?? 'none'})`));
      this.resolveClosed({ code, signal });
    });
  }

  submit(args) {
    const id = `offcraft-${randomUUID()}`;
    let resolve;
    let reject;
    const promise = new Promise((accept, fail) => { resolve = accept; reject = fail; });
    const pending = { id, args, sent: false, resolve, reject };
    if (this.failure || !this.accepting || this.exited) {
      reject(this.failure ?? new GameTransportError('client_closed', 'Game client is not accepting requests'));
    } else {
      this.pending.set(id, pending);
      if (this.ready) this.send(pending);
    }
    return { id, command: args.command, promise };
  }

  send(pending) {
    if (!this.pending.has(pending.id) || pending.sent || !this.accepting || this.failure || this.exited) return;
    pending.sent = true;
    try {
      // Generated ID is last, so callers cannot replace the correlation ID.
      this.child.stdin.write(`${JSON.stringify({ ...pending.args, id: pending.id })}\n`, (error) => {
        if (error) this.fail(new GameTransportError('client_stdin_failed', error.message));
      });
    } catch (error) {
      this.fail(new GameTransportError('client_stdin_failed', error.message));
    }
  }

  receive(line) {
    let message;
    try { message = JSON.parse(line); } catch { this.log(`${line}\n`); return; }
    if (!record(message) || message.protocol !== 1 || !['event', 'accepted', 'completed', 'error'].includes(message.type)) {
      this.log(`${line}\n`);
      return;
    }
    if (message.type === 'event') {
      this.log(`${line}\n`);
      if (message.event === 'ready' && !this.ready && this.accepting && !this.failure) {
        this.ready = true;
        for (const pending of this.pending.values()) this.send(pending);
      }
      return;
    }
    const pending = this.pending.get(message.id);
    if (!pending || (message.command !== pending.args.command && !(message.type === 'error' && message.command === null))) {
      this.log(`${line}\n`);
      return;
    }
    if (message.type === 'accepted') return;
    this.pending.delete(message.id);
    pending.resolve(message);
  }

  /** Detach promptly; exact target prevents cancellation of another in-flight action. */
  cancel(id) {
    const pending = this.pending.get(id);
    if (!pending) return;
    this.pending.delete(id);
    pending.reject(new AbandonedRequest('MCP request cancelled'));
    if (!pending.sent || this.failure || !this.accepting || this.exited) return;
    const cancellation = this.submit({ command: 'cancel', target: id });
    void cancellation.promise.then((response) => {
      if (response.type === 'error') this.log(`offcraft cancellation: ${JSON.stringify(response)}\n`);
    }, (error) => this.log(`offcraft cancellation: ${error.message}\n`));
  }

  fail(error) {
    if (!this.failure) this.failure = error;
    this.accepting = false;
    for (const pending of this.pending.values()) pending.reject(this.failure);
    this.pending.clear();
  }

  endInput() {
    if (!this.accepting) {
      if (!this.child.stdin.destroyed && !this.child.stdin.writableEnded) this.child.stdin.end();
      return;
    }
    this.fail(new GameTransportError('adapter_closed', 'MCP input closed; game stdin is ending for orderly shutdown'));
    if (!this.child.stdin.destroyed && !this.child.stdin.writableEnded) this.child.stdin.end();
  }
}

class RpcError extends Error {
  constructor(code, message, data) { super(message); this.code = code; this.data = data; }
}

function paramsObject(params, allowed = []) {
  if (params === undefined) return {};
  if (!record(params)) throw new RpcError(-32602, 'params must be an object');
  for (const key of Object.keys(params)) {
    if (key !== '_meta' && !allowed.includes(key)) throw new RpcError(-32602, `Unknown parameter: ${key}`);
  }
  if (Object.hasOwn(params, '_meta') && !record(params._meta)) throw new RpcError(-32602, '_meta must be an object');
  return params;
}

function toolResult(response, failed = response.type === 'error') {
  return { content: [{ type: 'text', text: JSON.stringify(response) }], isError: failed };
}

/** A line-oriented MCP endpoint; concurrent tools/call requests never serialize. */
export class McpAdapter {
  constructor(bridge, { input = process.stdin, output = process.stdout, log = (text) => process.stderr.write(text) } = {}) {
    this.bridge = bridge;
    this.input = input;
    this.output = output;
    this.log = log;
    this.initializing = false;
    this.initializeResponded = false;
    this.initialized = false;
    this.stopped = false;
    this.calls = new Map();
    this.seenIds = new Set();
    this.lines = createInterface({ input, crlfDelay: Infinity, terminal: false });
    this.lines.on('line', (line) => { void this.handleLine(line); });
    this.lines.on('close', () => this.close());
    this.lines.on('error', (error) => { this.log(`offcraft MCP input: ${error.message}\n`); this.close(); });
    output.on('error', () => this.close());
  }

  send(message) {
    if (this.stopped) return;
    try { this.output.write(`${JSON.stringify(message)}\n`); } catch { this.close(); }
  }

  error(id, code, message, data) {
    this.send({ jsonrpc: '2.0', id, error: { code, message, ...(data === undefined ? {} : { data }) } });
  }

  async handleLine(line) {
    if (this.stopped) return;
    let message;
    try { message = JSON.parse(line); } catch { this.error(null, -32700, 'Parse error'); return; }
    const notification = record(message) && !Object.hasOwn(message, 'id') && typeof message.method === 'string';
    if (!record(message) || message.jsonrpc !== '2.0' || typeof message.method !== 'string' || !message.method) {
      if (!notification) this.error(record(message) && requestId(message.id) ? message.id : null, -32600, 'Invalid Request');
      return;
    }
    if (notification) {
      this.notify(message);
      return;
    }
    if (!requestId(message.id)) { this.error(null, -32600, 'Request id must be a string or safe integer'); return; }
    if (this.seenIds.has(message.id)) {
      // null avoids delivering a second response under a still-running request ID.
      this.error(null, -32600, 'Request IDs must be unique within the MCP session');
      return;
    }
    this.seenIds.add(message.id);
    try {
      const result = await this.request(message);
      if (result !== undefined) {
        this.send({ jsonrpc: '2.0', id: message.id, result });
        if (message.method === 'initialize') this.initializeResponded = true;
      }
    } catch (error) {
      this.error(message.id, error instanceof RpcError ? error.code : -32603, error instanceof RpcError ? error.message : 'Internal error', error instanceof RpcError ? error.data : undefined);
      if (!(error instanceof RpcError)) this.log(`offcraft MCP: ${error.message}\n`);
    }
  }

  notify(message) {
    try {
      if (message.method === 'notifications/initialized') {
        paramsObject(message.params);
        if (this.initializeResponded) this.initialized = true;
      } else if (message.method === 'notifications/cancelled') {
        const params = paramsObject(message.params, ['requestId', 'reason']);
        if (!requestId(params.requestId) || (params.reason !== undefined && typeof params.reason !== 'string')) return;
        const call = this.calls.get(params.requestId);
        if (!call) return;
        call.cancelled = true;
        this.calls.delete(params.requestId);
        this.bridge.cancel(call.game.id);
      }
      // Unknown or malformed notifications never receive a response.
    } catch { /* Notifications are fire-and-forget, including invalid cancellation. */ }
  }

  async request(message) {
    if (message.method === 'ping') { paramsObject(message.params); return {}; }
    if (message.method === 'initialize') {
      if (this.initializing) throw new RpcError(-32600, 'Already initialized');
      const params = paramsObject(message.params, ['protocolVersion', 'capabilities', 'clientInfo']);
      if (!nonempty(params.protocolVersion) || !record(params.capabilities) || !record(params.clientInfo) || !nonempty(params.clientInfo.name) || !nonempty(params.clientInfo.version)) throw new RpcError(-32602, 'initialize requires protocolVersion, capabilities and clientInfo with name/version');
      this.initializing = true;
      return {
        protocolVersion: PROTOCOL_VERSION,
        capabilities: { tools: { listChanged: false } },
        serverInfo: { name: 'offcraft-mcp', version: '1.0.0' },
      };
    }
    if (!['tools/list', 'tools/call'].includes(message.method)) throw new RpcError(-32601, `Method not found: ${message.method}`);
    if (!this.initialized) throw new RpcError(-32600, 'initialize and notifications/initialized are required first');
    if (message.method === 'tools/list') {
      const params = paramsObject(message.params, ['cursor']);
      if (params.cursor !== undefined) throw new RpcError(-32602, 'Invalid cursor: the complete tool list fits in one page');
      return { tools: [COMMAND_TOOL] };
    }
    const params = paramsObject(message.params, ['name', 'arguments']);
    if (params.name !== COMMAND_TOOL.name) throw new RpcError(-32602, `Unknown tool: ${String(params.name)}`);
    const errors = validateCommandArguments(params.arguments);
    if (errors.length) throw new RpcError(-32602, 'Invalid tool arguments', { errors });
    const game = this.bridge.submit(params.arguments);
    const call = { game, cancelled: false };
    this.calls.set(message.id, call);
    try {
      const response = await game.promise;
      if (!call.cancelled) return toolResult(response);
    } catch (error) {
      if (!call.cancelled) {
        // This is explicitly an adapter failure, not a synthetic game envelope.
        return toolResult({ source: 'adapter', type: 'error', code: error.code ?? 'transport_failed', message: error.message, game_id: game.id, command: game.command }, true);
      }
    } finally {
      if (this.calls.get(message.id) === call) this.calls.delete(message.id);
    }
  }

  close() {
    if (this.stopped) return;
    this.stopped = true;
    this.lines.close();
    this.input.pause();
    for (const call of this.calls.values()) call.cancelled = true;
    this.calls.clear();
    this.bridge.endInput();
  }
}

export function launchAdapter(options, {
  input = process.stdin, output = process.stdout, error = process.stderr, spawnClient = spawn,
} = {}) {
  const log = (text) => error.write(text);
  const child = spawnClient(options.client, options.args, {
    cwd: options.cwd, shell: false, windowsHide: true, stdio: ['pipe', 'pipe', 'pipe'],
  });
  const bridge = new GameBridge(child, { log });
  const adapter = new McpAdapter(bridge, { input, output, log });
  const closed = bridge.closed.then((status) => new Promise((resolve) => {
    // Let pending tool promises emit actual final/error responses before stopping.
    setImmediate(() => { adapter.close(); resolve(status); });
  }));
  return { child, bridge, adapter, closed };
}
