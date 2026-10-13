import { launchGame } from './adapter.mjs';
import { validateCommandArguments } from './schemas.mjs';

export class CommandValidationError extends TypeError {
  constructor(errors) { super(`Invalid command arguments: ${errors.join('; ')}`); this.name = 'CommandValidationError'; this.errors = errors; }
}

/** Launch a real child with the same GameBridge used by MCP; no shell and no second transport. */
export function launchClient(options, dependencies = {}) {
  const { log = options?.log ?? ((text) => process.stderr.write(text)), spawnClient, observers = options?.observers ?? [] } = dependencies;
  const game = launchGame(options, { log, ...(spawnClient ? { spawnClient } : {}), observers });
  const { bridge } = game;
  let quitting;
  const submit = (args, { signal } = {}) => {
    const errors = validateCommandArguments(args);
    if (errors.length) throw new CommandValidationError(errors);
    if (signal !== undefined && !(signal instanceof AbortSignal)) throw new TypeError('signal must be an AbortSignal');
    // Freeze submitted values in time; later caller mutation cannot change a queued command.
    const handle = bridge.submit(structuredClone(args), { signal });
    return { ...handle, cancel: () => bridge.cancel(handle.id) };
  };
  const command = async (args, settings) => submit(args, settings).promise;
  const close = () => { bridge.endInput(); return game.closed; };
  return {
    ready: () => bridge.waitReady(),
    submit,
    command,
    cancel: (target) => command(target === undefined ? { command: 'cancel' } : { command: 'cancel', target }),
    close,
    quit: () => {
      if (!quitting) quitting = (async () => {
        try {
          const response = await command({ command: 'quit' });
          await close();
          return response;
        } catch (error) {
          await close();
          throw error;
        }
      })();
      return quitting;
    },
    closed: game.closed,
    get recording() { return game.recorder?.status ?? null; },
  };
}
