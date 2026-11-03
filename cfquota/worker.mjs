import { handle } from './lib.mjs';
export default { fetch: (req, env) => handle(req, env, Date.now()) };
