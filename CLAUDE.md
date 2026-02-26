# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## About

REST API for PUC COMP, a junior software company at PUC-MG. Manages members, projects, technologies, roles, CV applications, project proposals, and image memories.

## Development Commands

The development environment runs entirely inside Docker Compose (app + PostgreSQL).

```bash
# First-time setup
cp .env.example .env          # fill in env vars
docker-compose up -d          # start containers
docker-compose exec app npx prisma migrate dev   # run migrations
docker-compose exec app npm run prisma:seed      # seed initial data

# Daily development
docker-compose up -d          # start (hot reload enabled via bind mounts on ./src and ./prisma)
docker-compose logs app -f    # follow logs
docker-compose down           # stop
docker-compose down -v        # stop and wipe DB volume

# Inside the container
docker-compose exec app npm run format     # run prettier
docker-compose exec app npm run build      # compile TypeScript to ./dist
docker-compose exec app npx prisma migrate dev   # apply new migrations
docker-compose exec app npx prisma studio        # open Prisma Studio GUI
```

There are no tests (`npm test` exits 1).

## Architecture

**Stack:** Express 5, TypeScript (ESM/NodeNext), Prisma ORM, PostgreSQL 16, AWS S3, Nodemailer, JWT, bcryptjs, multer.

**Entry point:** `src/index.ts` — configures Express, CORS (origins from `FRONTEND_URLS` env var, comma-separated), static files from `public/`, and mounts all routes under `/api/`.

### Project Layout

```
src/
  index.ts                 # app entry, exports BASE_URL
  controllers/             # business logic objects (memberController, projectController)
  routes/                  # one file per resource, registers Express Router
  middlewares/
    isAuth.ts              # JWT verification; attaches req.user = {id, is_active, is_admin}
    isAdmin.ts             # checks req.user.is_admin (must run after isAuth)
    fileMiddleware.ts      # ensures req.file is present after multer
    errorHandlers.ts       # multer error handler middleware
  utils/
    prisma.ts              # singleton PrismaClient export
    s3.ts                  # AWS S3 helpers: upload, delete, getS3URL, getSignedS3URL
    uploads.ts             # multer memory storage config (memUpload) + sanitizeFileName
    email.ts               # nodemailer transporter + sendEmail()
    formats.ts             # formatDate() → YYYY-MM-DD, keysToSnakeCase(), toSnakeCase(), toCamelCase()
prisma/
  schema.prisma            # data models
  seed.ts                  # creates default admin member from env vars
  migrations/              # Prisma migration SQL files
```

### Key Patterns

**Auth middleware chain:** Routes requiring login use `isAuth`, admin-only routes chain `isAuth, isAdmin`. Example: `router.post('/', isAuth, isAdmin, handler)`.

**Response format:** Prisma returns camelCase fields. Controllers convert to snake_case for responses using `keysToSnakeCase()` from `src/utils/formats.ts`. Dates are always serialized to `YYYY-MM-DD` via `formatDate()`. Passwords are never included in responses (`sanitizeMemberForResponse` strips them).

**File uploads:** All file uploads use multer memory storage (`memUpload` from `uploads.ts`). Files are held in `req.file.buffer` then streamed to S3. S3 uploads are rolled back if the subsequent DB insert fails.

**Project lookup middleware:** `projectsRoutes.ts` defines a `findProjectByName` middleware that resolves `:project_name` to a `Project` and attaches it to `req.project`. This is reused across sub-routes (`/contributors`, `/technologies`).

**Route-level logic:** Simple routes (technologies, roles, memories, project proposals) keep their handler logic directly in the route file rather than separate controller files.

**ESM imports:** TypeScript is compiled with `module: NodeNext`. All local imports must use `.js` extensions (e.g., `import foo from './foo.js'`), even though the source files are `.ts`.

**Prisma error codes used:** `P2002` (unique constraint), `P2003`/`P2025` (foreign key / record not found).

### Data Model Summary

- `Role` → has many `Member`s (with `level` for hierarchy)
- `Member` → belongs to `Role`, participates in `Project`s via `Contributor` join table
- `Project` → has `Contributor`s and `ProjectTechnology`s; image stored in S3 (`imageKey`)
- `Technology` → has `TechnologyType` enum and links to projects via `ProjectTechnology` with `TechnologyUsageLevel`
- `CvApplication` → stores applicant info + PDF key in S3; `cvKey` is the primary key
- `ProjectProposal` → stores client project requests; triggers email notification
- `ImageMemory` → photo gallery; image stored in S3

### Environment Variables

See `.env.example` for all required variables. Key groups:
- `DATABASE_URL` — PostgreSQL connection string
- `JWT_SECRET_KEY` — used to sign/verify tokens (1h expiry)
- `AWS_*` — S3 bucket credentials and region
- `EMAIL_*` / `TARGET_EMAIL` — SMTP config and notification recipient
- `FRONTEND_URLS` — comma-separated allowed CORS origins
- `DEFAULT_ADMIN_*` — credentials for the seeded admin member

## Code Style

Prettier config: no semicolons, single quotes, trailing commas (ES5), 2-space indent, 80 char print width. Run `npm run format` to apply.

TypeScript is strict with `noImplicitAny`, `noUnusedLocals`, `noUnusedParameters`, and `noImplicitReturns` enabled.
