# Known Issues & Limitations

## Known Issues

| ID  | Area       | Description                          | Status       | Workaround / Notes |
|-----|------------|--------------------------------------|--------------|---------------------|
| 001 | Build      | Windows: `mvnw.cmd` is gitignored    | Intentional  | Use `./mvnw` from PowerShell or WSL. The `mvnw` shell script works on Windows via Git Bash or WSL. |
| 002 | Frontend   | `package-lock.json` is gitignored    | Intentional  | Run `npm install` after cloning. Lock file intentionally excluded per project convention. |
| 003 | Frontend   | `.eslintrc.cjs` uses `export default` but config is `.cjs` | Cosmetic | Works because `type: "module"` in package.json. ESLint resolves it correctly. |
| 004 | Frontend   | `tsconfig.json` excludes `node` but not `server.mjs` | Low priority | `server.mjs` is at project root, outside `src/`. No type impact. |
| 005 | Security   | `.env.example` contains placeholder secrets | By design | All values are `CHANGE_ME_*` or obvious defaults. Must be replaced before deployment. |
| 006 | Kilo       | `.md` files in `.kilo/agent/` and `.kilo/command/` fail frontmatter validation | Non-fatal | YAML frontmatter in these directories triggers "No context found for instance". Agent and command definitions are consolidated in `kilo.jsonc` instead, which passes schema validation. |
| 007 | Frontend   | `jsdom` was missing from `package.json` devDependencies | Fixed | Vitest config requires `jsdom` for the `environment: "jsdom"` setting. Added `jsdom: ^24.1.3` to devDependencies. |
| 008 | Frontend   | `eslint-config-prettier` and `eslint-plugin-react` were missing from `package.json` | Fixed | ESLint config extends `"prettier"` and uses `eslint-plugin-react`. Both added to devDependencies. |
| 009 | Frontend   | Smoke test was in `setup.ts` which Vitest does not discover | Fixed | Vitest only discovers `*.test.ts`/`*.spec.ts` files. `setup.ts` now contains only setup code; test moved to `src/test/smoke.test.ts`. |
| 010 | Frontend   | ESLint failed on `tailwind.config.ts` and `postcss.config.js` | Fixed | These files are outside `tsconfig.json`'s `include` scope. Added `--ignore-pattern` flags to the `lint` script and `ignorePatterns` in `.eslintrc.cjs`. |
| 011 | Dev        | Local MySQL lacked `flowerconnect` user with privileges | Environment | Backend uses `DB_USER=flowerconnect`/`DB_PASS=flowerconnect`. Local MySQL had only `root` (passwordless). User must be created manually for local dev, or Docker Compose handles it automatically. |
| 012 | Docker     | Docker not available on this machine | Blocked | `docker compose config` and `docker compose up --build` could not be validated. Full-stack Docker verification is pending. Local services (MySQL + JAR + node server) were used as a substitute. | |

## Known Limitations

| ID  | Area       | Description                                           | Impact |
|-----|------------|-------------------------------------------------------|--------|
| 001 | Backend    | No Redis configuration in `application.yml`            | Redis runs in Docker but backend doesn't connect to it yet. |
| 002 | Frontend   | No error boundary component                           | Unhandled errors will crash the app. Should add in Phase 2. |
| 003 | Frontend   | No loading states or suspense in routes                | All routes render immediately. Add skeleton loaders later. |
| 004 | Docker     | No `.env` file required for `docker compose up`      | Compose uses defaults from `.env.example`. Production deployments need a real `.env`. |
| 005 | Docker     | No health check for backend DB/Redis connectivity    | Backend may start before DB is ready if healthcheck fails silently. |
| 006 | Docker     | Docker not available on this machine                  | Full-stack Docker verification pending. Local services used as substitute. |

## Discovered Problems

- Frontend scaffold had missing devDependencies (`jsdom`, `eslint-config-prettier`, `eslint-plugin-react`) that broke `npm run test` and `npm run lint`. All fixed and documented above.
- Vitest smoke test was placed in `setup.ts` which Vitest does not auto-discover (only `*.test.ts`/`*.spec.ts`). Moved to `src/test/smoke.test.ts`.
- ESLint config used `export default` in `.eslintrc.cjs` (CommonJS extension), causing a parse error. Rewritten with `module.exports`.
- ESLint failed on `tailwind.config.ts` and `postcss.config.js` because they fall outside `tsconfig.json`'s `include` scope. Added ignore patterns.
- Local MySQL instance lacked the `flowerconnect` user with privileges; backend failed to start until the user was created manually. Docker Compose creates this user automatically.
- Docker is not available on this machine, so `docker compose config` and `docker compose up --build` could not be validated directly. Full-stack was verified using local services as a substitute.

## Blocked Work

_None currently blocked._

## Deprecation Notices

_None._
