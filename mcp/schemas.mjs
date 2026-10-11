const integer = (minimum, maximum, description) => ({
  type: 'integer', minimum, maximum, ...(description ? { description } : {}),
});
const nonemptyString = (description) => ({ type: 'string', minLength: 1, description });
const worldName = { type: 'string', minLength: 1, pattern: '^[A-Za-z0-9_-]+$', description: 'Local world directory name.' };
const modifiers = integer(0, 15, 'GLFW modifier bits: SHIFT=1, CONTROL=2, ALT=4, SUPER=8.');

function command(name, description, properties = {}, required = []) {
  return {
    description,
    type: 'object',
    properties: { command: { const: name, type: 'string' }, ...properties },
    required: ['command', ...required],
    additionalProperties: false,
  };
}

const slotActions = ['pickup', 'quick_move', 'swap', 'throw', 'pickup_all'];
const slotVariant = (action) => command('click_slot', `Vanilla ${action} slot action.`, {
  sync_id: integer(0, 2147483647, 'Current handler sync_id, obtained from inventory.'),
  slot: action === 'pickup'
    ? { anyOf: [integer(0, 2147483647), { const: -999, type: 'integer' }], description: 'Handler slot ID; -999 clicks outside.' }
    : integer(0, 2147483647, 'Handler slot ID, not an inventory index.'),
  button: action === 'swap'
    ? { type: 'integer', enum: [0, 1, 2, 3, 4, 5, 6, 7, 8, 40], description: 'Hotbar index 0..8, or 40 for offhand.' }
    : integer(0, 1, 'Vanilla slot action button.'),
  action: { type: 'string', const: action },
}, ['sync_id', 'slot', 'button', 'action']);

export const COMMAND_SCHEMAS = Object.freeze({
  act: command('act', 'Hold vanilla gameplay input for client ticks; wait for completion, optionally capturing the resulting frame.', {
    ticks: integer(1, 12000),
    buttons: { type: 'array', items: { type: 'string', enum: ['forward', 'back', 'left', 'right', 'jump', 'sneak', 'attack', 'use'] } },
    slot: integer(1, 9, 'Selected hotbar slot, numbered 1..9.'),
    capture: nonemptyString('PNG path relative to the game working directory, or an absolute path.'),
  }, ['ticks']),
  new: command('new', 'Create a new local creative world; complete only when its player and world are ready.', { world: worldName }, ['world']),
  load: command('load', 'Load an existing local world; complete only when its player and world are ready.', { world: worldName }, ['world']),
  connect: command('connect', 'Connect the actual Minecraft client to a multiplayer server.', {
    address: nonemptyString('Server host with optional port, using Minecraft address syntax.'),
  }, ['address']),
  disconnect: command('disconnect', 'Disconnect and save the current world, returning to the menu.'),
  switch_world: command('switch_world', 'Disconnect the current session and load or create the named local world.', {
    world: worldName,
    create: { type: 'boolean', default: false, description: 'Create a new world rather than loading an existing one.' },
  }, ['world']),
  cancel: command('cancel', 'Cancel the active act command and release held input. Other operations cannot be cancelled by this command.', {
    target: { type: 'string', minLength: 1, maxLength: 128, description: 'Exact active game request ID from status.active_id; omission targets the currently active action.' },
  }),
  look: command('look', 'Set the player camera rotation using vanilla client state.', {
    yaw: { type: 'number' }, pitch: { type: 'number', minimum: -90, maximum: 90 },
  }, ['yaw', 'pitch']),
  render: command('render', 'Capture the next rendered game frame as PNG; complete after writing it.', {
    path: nonemptyString('PNG path; omission uses the game frame filename sequence.'),
  }),
  status: command('status', 'Read client lifecycle, render, screen and active-request state. Available while an action is running.'),
  observe: command('observe', 'Read player position/rotation, health, food, saturation, effects, held items and the actual crosshair target. Available while busy.'),
  inventory: command('inventory', 'Read current vanilla handler sync_id, revision, numbered slots and cursor stack. Available while busy.'),
  gui: command('gui', 'Open the vanilla player inventory or close the current GUI.', {
    action: { type: 'string', enum: ['inventory', 'close'] },
  }, ['action']),
  key: command('key', 'Send one logical keyboard press/release to the current screen, or open inventory/chat/menu without a screen.', {
    key: {
      anyOf: [{ type: 'string', minLength: 1 }, integer(32, 348)],
      description: 'Case-insensitive key name: ESCAPE, INVENTORY, CHAT, ENTER, TAB, BACKSPACE, LEFT, RIGHT, UP, DOWN, DELETE, HOME, END, PAGE_UP, PAGE_DOWN, SPACE; or a valid GLFW key code (32..348, excluding reserved codes).',
    },
    modifiers,
  }, ['key']),
  click: command('click', 'Send a vanilla mouse press/release to the current screen, in scaled GUI coordinates.', {
    x: { type: 'number', minimum: 0, description: 'Scaled x coordinate, less than the current screen width.' },
    y: { type: 'number', minimum: 0, description: 'Scaled y coordinate, less than the current screen height.' },
    button: { ...integer(0, 2), default: 0 }, modifiers,
  }, ['x', 'y']),
  text: command('text', 'Type Unicode text through the current vanilla screen charTyped handler.', {
    text: { type: 'string' },
  }, ['text']),
  click_slot: {
    description: 'Dispatch a vanilla slot operation using the current handler: clickSlot for ordinary containers, the creative screen callback for an open creative inventory. Completion describes dispatched/local state, not server confirmation; catalogue/cursor-only operations need not send a packet.',
    type: 'object',
    properties: {
      command: { type: 'string', const: 'click_slot' },
      sync_id: integer(0, 2147483647), slot: { type: 'integer' }, button: { type: 'integer' },
      action: { type: 'string', enum: slotActions },
    },
    required: ['command', 'sync_id', 'slot', 'button', 'action'],
    additionalProperties: false,
    oneOf: slotActions.map(slotVariant),
  },
  render_policy: command('render_policy', 'Select normal rendering or reduced background frames while preserving game ticking and requested captures.', {
    mode: { type: 'string', enum: ['normal', 'reduced'] },
    fps: { ...integer(1, 60), description: 'Reduced render FPS; default 2. Normal mode uses the configured window FPS cap.' },
  }, ['mode']),
  help: command('help', 'Read the commands and input ranges implemented by this game client.'),
  quit: command('quit', 'Release input, save the world and stop the game client; complete only after client cleanup.'),
});

export const COMMAND_NAMES = Object.freeze(Object.keys(COMMAND_SCHEMAS));
export const COMMAND_TOOL = Object.freeze({
  name: 'offcraft_command',
  description: 'Execute an Offcraft Fabric JSONL command. The result is the actual completed/error game envelope, never an accepted acknowledgement. One long mutation may be active; status/observe/inventory/help can interleave. Game input and inventory operations follow vanilla client/server behavior. Use status.active_id for an explicit cancel target.',
  inputSchema: {
    type: 'object',
    properties: { command: { type: 'string', enum: COMMAND_NAMES } },
    required: ['command'],
    oneOf: Object.values(COMMAND_SCHEMAS),
  },
});

// This deliberately implements only the JSON Schema vocabulary used above.
// The advertised schema is also the validation source, so its ranges cannot drift.
function violations(schema, value, path) {
  const errors = [];
  if (Object.hasOwn(schema, 'const') && value !== schema.const) errors.push(`${path} must equal ${JSON.stringify(schema.const)}`);
  if (schema.enum && !schema.enum.includes(value)) errors.push(`${path} must be one of ${schema.enum.map((item) => JSON.stringify(item)).join(', ')}`);
  if (schema.anyOf && !schema.anyOf.some((variant) => violations(variant, value, path).length === 0)) errors.push(`${path} does not match a permitted type/value`);
  if (schema.oneOf) {
    const candidates = schema.oneOf.map((variant) => violations(variant, value, path));
    if (candidates.filter((candidate) => candidate.length === 0).length !== 1) {
      const selected = schema.oneOf.findIndex((variant) => variant.properties?.action?.const !== undefined && variant.properties.action.const === value?.action);
      errors.push(...(selected >= 0 ? candidates[selected] : [`${path} does not match a permitted command variant`]));
    }
  }
  if (schema.type === 'object') {
    if (value === null || typeof value !== 'object' || Array.isArray(value)) return [...errors, `${path} must be an object`];
    for (const name of schema.required ?? []) {
      if (!Object.hasOwn(value, name)) errors.push(`${path}.${name} is required`);
    }
    for (const [name, field] of Object.entries(value)) {
      if (Object.hasOwn(schema.properties ?? {}, name)) errors.push(...violations(schema.properties[name], field, `${path}.${name}`));
      else if (schema.additionalProperties === false) errors.push(`${path}.${name} is not allowed`);
    }
  } else if (schema.type === 'array') {
    if (!Array.isArray(value)) return [...errors, `${path} must be an array`];
    for (let index = 0; index < value.length; index++) errors.push(...violations(schema.items, value[index], `${path}[${index}]`));
  } else if (schema.type === 'number' || schema.type === 'integer') {
    if (typeof value !== 'number' || !Number.isFinite(value) || (schema.type === 'integer' && !Number.isSafeInteger(value))) return [...errors, `${path} must be a finite ${schema.type}`];
    if (schema.minimum !== undefined && value < schema.minimum) errors.push(`${path} must be at least ${schema.minimum}`);
    if (schema.maximum !== undefined && value > schema.maximum) errors.push(`${path} must be at most ${schema.maximum}`);
  } else if (schema.type === 'string') {
    if (typeof value !== 'string') return [...errors, `${path} must be a string`];
    if (schema.minLength !== undefined && value.length < schema.minLength) errors.push(`${path} must not be empty`);
    if (schema.maxLength !== undefined && value.length > schema.maxLength) errors.push(`${path} is too long`);
    if (schema.pattern && !new RegExp(schema.pattern).test(value)) errors.push(`${path} contains invalid characters`);
  } else if (schema.type === 'boolean' && typeof value !== 'boolean') {
    errors.push(`${path} must be a boolean`);
  }
  return errors;
}

export function validateCommandArguments(value) {
  if (value === null || typeof value !== 'object' || Array.isArray(value)) return ['arguments must be an object'];
  if (typeof value.command !== 'string' || !Object.hasOwn(COMMAND_SCHEMAS, value.command)) return [`arguments.command must be one of ${COMMAND_NAMES.join(', ')}`];
  return [...new Set(violations(COMMAND_SCHEMAS[value.command], value, 'arguments'))];
}
