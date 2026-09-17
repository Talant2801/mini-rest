# mini-rest frontend

React + TypeScript + Vite UI for the `mini-rest` notification API.

Lets you create, list, edit, and delete notifications sent through the
`console`, `email`, and `slack` channels exposed by `POST/GET/PUT/DELETE /api/message`.

## Prerequisites

Requires Node.js (18+) and npm. Neither was found on this machine when this
module was scaffolded — install Node first, e.g. via https://nodejs.org or a
version manager like `nvm`.

## Run

```bash
cd frontend
npm install
npm run dev
```

This starts Vite on http://localhost:5173 and proxies `/api/*` requests to
the Spring Boot backend on `http://localhost:8080` (see `vite.config.ts`).
Start the backend separately (`./gradlew bootRun` from the project root).

## Build

```bash
npm run build
```

Outputs a static production bundle to `frontend/dist`. Since the backend has
no CORS configuration, either serve `dist` through the same origin as the
API (e.g. copy it into the Spring Boot `src/main/resources/static` folder)
or add CORS headers to `MessageController` if you want to host the frontend
on a separate origin in production.

## Structure

- `src/api.ts` — fetch wrapper for the `/api/message` endpoints
- `src/types.ts` — shared TypeScript types mirroring `Message` / `NotificationRequest`
- `src/components/MessageForm.tsx` — create/edit form
- `src/components/MessageList.tsx` — table of notifications with edit/delete actions
- `src/App.tsx` — top-level state and data flow
