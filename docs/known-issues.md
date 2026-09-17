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
| 012 | Docker     | Docker not available on this machine | Resolved | Docker is now available. Full-stack verified with `docker compose up --build`. All services pass healthchecks. |
| 013 | Docker     | `docker-compose.yml` DB_URL used `${DB_HOST:-mysql}:${DB_PORT:-3306}` which resolved from root `.env` (host-side: `localhost:3307`) instead of internal Docker defaults | Fixed in `docker-compose.yml` | `DB_URL` now hardcodes `mysql:3306` (internal Docker network). Compose-level `${DB_HOST}` substitution is not used for the JDBC URL since the backend always connects to MySQL via the internal network. |
| 015 | DB / Auth  | `User.role` was `FetchType.LAZY` but `UserRepository.findByEmail` and `findById` lacked `JOIN FETCH`, causing `LazyInitializationException` during login and token refresh | Fixed | Added `@Query` with `JOIN FETCH u.role` to `UserRepository.findByEmail` and a new `findByIdWithRole` method. Added `@Query` with `JOIN FETCH rt.user u JOIN FETCH u.role` to `RefreshTokenRepository.findByTokenHash`. Updated `AuthService.login` to use `findByIdWithRole`. Updated `AuthServiceTest` mocks accordingly. |

## Known Limitations

| ID  | Area       | Description                                           | Impact |
|-----|------------|-------------------------------------------------------|--------|
| 001 | Backend    | No Redis configuration in `application.yml`            | Redis runs in Docker but backend doesn't connect to it yet. |
| 002 | Frontend   | No error boundary component                           | Unhandled errors will crash the app. Should add in Phase 2. |
| 003 | Frontend   | No loading states or suspense in routes                | All routes render immediately. Add skeleton loaders later. |
| 004 | Docker     | No `.env` file required for `docker compose up`      | Compose uses defaults from `.env.example`. Production deployments need a real `.env`. |
| 005 | Docker     | No health check for backend DB/Redis connectivity    | Backend may start before DB is ready if healthcheck fails silently. |
| 006 | Docker     | Docker now available                                  | Full-stack Docker verified. All healthchecks pass. |

## Discovered Problems

- Frontend scaffold had missing devDependencies (`jsdom`, `eslint-config-prettier`, `eslint-plugin-react`) that broke `npm run test` and `npm run lint`. All fixed and documented above.
- Vitest smoke test was placed in `setup.ts` which Vitest does not auto-discover (only `*.test.ts`/`*.spec.ts`). Moved to `src/test/smoke.test.ts`.
- ESLint config used `export default` in `.eslintrc.cjs` (CommonJS extension), causing a parse error. Rewritten with `module.exports`.
- ESLint failed on `tailwind.config.ts` and `postcss.config.js` because they fall outside `tsconfig.json`'s `include` scope. Added ignore patterns.
- Local MySQL instance lacked the `flowerconnect` user with privileges; backend failed to start until the user was created manually. Docker Compose creates this user automatically.
- Docker was unavailable in prior sessions; `docker compose up --build` could not be validated directly and local services were used as a substitute. Docker is now available and full-stack has been verified.
- `docker-compose.yml` DB_URL line used `${DB_HOST:-mysql}:${DB_PORT:-3306}` template substitution, which resolved to `localhost:3307` (from root `.env` file intended for host-side tooling) instead of the internal Docker hostname `mysql:3306`. Fixed by hardcoding `mysql:3306` in the DB_URL env var.
- V1__baseline.sql `roles` table omitted the `created_at` column that the `Role` entity maps via `@CreationTimestamp`. Hibernate `ddl-auto: validate` rejected the schema at startup. Fixed with V4 migration (`ALTER TABLE roles ADD COLUMN created_at ...`) rather than editing the applied V1 baseline.
- `User.role` (FetchType.LAZY) was not eagerly fetched by `UserRepository.findByEmail` and `findById`, causing `LazyInitializationException` when `UserDetailsImpl.fromUser()` or `AuthService.createAuthResponse()` accessed `role.getName()` outside the Hibernate session. Fixed by adding `@Query` with `JOIN FETCH` to `findByEmail`, adding `findByIdWithRole`, and eager-fetching Role in `RefreshTokenRepository.findByTokenHash`.

## Blocked Work

_None currently blocked._

## Deprecation Notices

_None._
