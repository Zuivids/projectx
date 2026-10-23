# ProjectX frontend

React 19 + TypeScript, built with Vite. The UI is in Latvian.

## Run in development

The backend must be running on port 8080 (see the main README). Then:

```bash
npm install
npm run dev
```

Open http://localhost:5173. Vite forwards `/api` requests to the backend, so the browser sees one origin and
the session and CSRF cookies work as in production.

## Commands

| Command | What it does |
| --- | --- |
| `npm run dev` | Development server with live reload |
| `npm test` | Unit and component tests (Vitest + Testing Library) |
| `npm run lint` | Lint with oxlint |
| `npm run build` | Type-check and build to `dist/` |

## Structure

| Folder | Contents |
| --- | --- |
| `src/api` | `client.ts` (fetch + CSRF + error handling) and typed API calls |
| `src/auth` | Logged-in user state (`AuthContext`) and route guards |
| `src/components` | Shared layout and form pieces |
| `src/pages` | One file per page |

## Pages

| Path | Page | Who |
| --- | --- | --- |
| `/` | Landing page | Everyone |
| `/pieslegties` | Login, with the MFA code step | Logged out |
| `/registreties` | Registration | Logged out |
| `/sakums` | Home ("Mani kursi") | Logged in |
| `/konts/drosiba` | Security: MFA setup with QR code and recovery codes | Logged in |
| `/kursi` | Course catalog | Everyone |
| `/kursi/:slug` | Course page with table of contents | Everyone |
| `/kursi/:slug/nodarbibas/:id` | Free preview lesson | Everyone |
| `/admin` | Admin panel start | Admins who passed MFA in this session |
| `/admin/kursi` | Course list, new course | Admins with MFA |
| `/admin/kursi/:id` | Course editor: details, status, sections and lessons | Admins with MFA |

Route guards only decide what to show. The real protection is on the server, which checks every API request.

## Rebranding

The product name comes from `VITE_APP_NAME` at build time (default `ProjectX`), see `src/config.ts`.
