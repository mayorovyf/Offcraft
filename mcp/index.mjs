#!/usr/bin/env node
import { launchAdapter, parseCliArgs, USAGE } from './adapter.mjs';

let options;
try {
  options = parseCliArgs(process.argv.slice(2));
} catch (error) {
  process.stderr.write(`${error.message}\n${USAGE}\n`);
  process.exitCode = 2;
}

if (options?.help) {
  // stdout is reserved for MCP even when showing CLI help.
  process.stderr.write(`${USAGE}\nNode.js >=22. The client executable is launched directly, without a shell.\nRecording is off by default; --record exclusively creates a JSONL file, default budget 10 MiB.\n`);
} else if (options) {
  try {
    const { adapter, closed } = launchAdapter(options);
    const stop = () => adapter.close();
    process.on('SIGINT', stop);
    process.on('SIGTERM', stop);
    const status = await closed;
    process.removeListener('SIGINT', stop);
    process.removeListener('SIGTERM', stop);
    if (status.code !== 0) process.exitCode = 1;
  } catch (error) {
    process.stderr.write(`offcraft adapter: ${error.message}\n`);
    process.exitCode = 1;
  }
}
