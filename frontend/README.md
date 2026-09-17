# QueueWise Frontend Shell

This first frontend slice establishes the application foundation only. It does not call backend endpoints or implement appointment/queue business logic.

## Entry point: `src/main.tsx`

- Imports React strict mode, which helps surface unsafe side effects during development.
- Creates one `QueryClient` for React Query server-state management.
- Sets `retry: 1` so a failed query does not retry indefinitely.
- Sets `staleTime: 30_000` so stable data is not refetched on every render.
- Wraps the application in `QueryClientProvider`, making the query client available to future feature hooks.
- Wraps the application in `BrowserRouter`, enabling client-side routes without full page reloads.
- Imports `styles.css`, which contains the Tailwind directives and the QueueWise visual system.

## Root component: `src/App.tsx`

- Defines the route table with React Router.
- Keeps `/login` outside the authenticated application shell.
- Redirects `/` to `/customer` so the first screen is a usable dashboard surface.
- Defines route placeholders for customer, appointment, token, queue, staff, counter, admin, and analytics workflows.
- Uses `ShellRoute` to apply the shared layout without repeating it for every route.
- Keeps the dashboard data static in this first slice; the next feature will replace that surface with React Query data.
- Uses `Link` for internal navigation so route changes do not reload the browser.

## Shared navigation: `src/components/AppShell.tsx`

- `navigationGroups` is the single navigation definition for customer, operations, and administration areas.
- `NavLink` applies active styling based on the current route.
- `useLocation` selects the current page title for the top bar.
- `useState` controls the mobile sidebar without introducing a global UI store.
- The workspace switcher, status pill, profile chip, and navigation are presentational shell elements; they do not claim backend state yet.
- The mobile scrim closes the sidebar when the user taps outside it.
- `children` makes the shell reusable for every future page.

## Placeholder pages: `src/components/PagePlaceholder.tsx`

- Defines a typed `PagePlaceholderProps` contract.
- Accepts a Lucide icon component rather than a string or manually-drawn SVG.
- Gives every unfinished route a consistent heading, description, and visual state.
- Keeps unfinished features explicit instead of showing a misleading empty dashboard.

## HTTP boundary: `src/api/client.ts`

- Creates one Axios instance with the environment-configured API base URL.
- Falls back to `/api/v1` for same-origin deployments.
- Adds JSON content headers for future API calls.
- Reads the access token from session storage and attaches it as a bearer token.
- Does not make a request during shell startup; feature APIs will be added with typed query/mutation modules later.

## Configuration

- `package.json` contains React, React Router, React Query, Axios, Lucide, Vite, TypeScript, Tailwind, PostCSS, and Autoprefixer.
- `vite.config.ts` binds the dev server to `0.0.0.0` so Docker and local network testing can reach it.
- `tailwind.config.js` scans the HTML and `src` tree for utility classes.
- `postcss.config.js` runs Tailwind and Autoprefixer during the Vite build.
- `tsconfig.app.json` enables strict TypeScript checking for application code.
- `tsconfig.node.json` type-checks Vite configuration.
- `.nvmrc` pins Node.js 22 for local development and CI.

## Styling: `src/styles.css`

The stylesheet defines the QueueWise visual language: deep green navigation, warm paper background, coral actions, serif display headings, compact operational metadata, responsive breakpoints, and mobile navigation behavior. Tailwind is available for incremental utility styling; the shell uses named CSS classes so the first visual system is readable and easy to tune.

## Current route map

| Route | Surface |
|---|---|
| `/login` | Authentication shell |
| `/customer` | Customer dashboard |
| `/services` | Service selection |
| `/appointments/book` | Appointment booking |
| `/tokens/:tokenId` | Token details |
| `/queue` | Live queue |
| `/history` | Appointment history |
| `/staff` | Staff dashboard |
| `/staff/counter` | Counter dashboard |
| `/admin` | Admin dashboard |
| `/admin/analytics` | Analytics |

## Validation

```bash
npm install
npm run build
npm run dev -- --host 0.0.0.0
```

The shell builds with TypeScript and Vite before feature pages are implemented.
