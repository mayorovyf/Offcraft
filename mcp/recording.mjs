import { openSync, write, close } from 'node:fs';

export const DEFAULT_RECORD_MAX_BYTES = 10 * 1024 * 1024;
export const RECORD_QUEUE_LIMIT = 256;

export class RecordingError extends Error {
  constructor(message, cause) { super(message, { cause }); this.name = 'RecordingError'; this.code = 'recording_failed'; }
}

/** One exclusive JSONL file; game protocol only, never process log lines or MCP images. */
export class SessionRecorder {
  constructor(path, { maxBytes = DEFAULT_RECORD_MAX_BYTES, log = (text) => process.stderr.write(text) } = {}) {
    if (typeof path !== 'string' || !path.trim() || path.includes('\0')) throw new TypeError('record must be a nonempty path without NUL characters');
    if (!Number.isSafeInteger(maxBytes) || maxBytes < 1) throw new TypeError('recordMaxBytes must be a positive safe integer');
    this.path = path;
    this.maxBytes = maxBytes;
    this.log = log;
    this.queue = [];
    this.bytes = 0;
    this.writtenBytes = 0;
    this.writing = false;
    this.ending = false;
    this.stopped = null;
    this.failure = null;
    this.fd = openSync(path, 'wx');
    this.finished = new Promise((resolve, reject) => { this.resolveFinished = resolve; this.rejectFinished = reject; });
    // Failure is still reported by close(), but does not become an unhandled rejection mid-session.
    void this.finished.catch(() => {});
  }

  get status() {
    return { path: this.path, maxBytes: this.maxBytes, bytes: this.writtenBytes, stopped: this.stopped, ...(this.failure ? { error: this.failure.message } : {}) };
  }

  stop(reason) {
    if (this.stopped) return;
    this.stopped = reason;
    this.log(`offcraft recording: ${reason}; recording stopped (${this.path})\n`);
  }

  observe(direction, message) {
    if (this.ending || this.stopped) return;
    if (this.queue.length + (this.writing ? 1 : 0) >= RECORD_QUEUE_LIMIT) { this.stop('queue_limit'); return; }
    let text;
    try { text = `${JSON.stringify({ time: new Date().toISOString(), direction, message })}\n`; }
    catch (error) { this.fail(error); return; }
    if (Buffer.byteLength(text) > this.maxBytes - this.bytes) { this.stop('byte_limit'); return; }
    const bytes = Buffer.from(text);
    this.bytes += bytes.length;
    this.queue.push(bytes);
    this.drain();
  }

  fail(error) {
    if (this.failure) return;
    this.failure = new RecordingError(`Cannot record session ${this.path}: ${error.message}`, error);
    this.stop('write_failed');
    this.log(`offcraft recording: ${this.failure.message}\n`);
    this.queue.length = 0;
    this.ending = true;
    this.drain();
  }

  drain() {
    if (this.writing || this.fd === null) return;
    const bytes = this.queue.shift();
    if (!bytes) {
      if (this.ending) {
        const fd = this.fd;
        this.fd = null;
        close(fd, (error) => {
          if (error) this.fail(error);
          if (this.failure) this.rejectFinished(this.failure);
          else this.resolveFinished(this.status);
        });
      }
      return;
    }
    this.writing = true;
    let offset = 0;
    const append = () => write(this.fd, bytes, offset, bytes.length - offset, null, (error, count) => {
      if (error || count === 0) {
        this.writing = false;
        this.fail(error ?? new Error('Recording write made no progress'));
        this.drain();
        return;
      }
      offset += count;
      this.writtenBytes += count;
      if (offset < bytes.length) { append(); return; }
      this.writing = false;
      this.drain();
    });
    append();
  }

  close() {
    this.ending = true;
    this.drain();
    return this.finished;
  }
}
