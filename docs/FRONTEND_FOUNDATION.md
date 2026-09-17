# QueueWise Frontend Foundation

## Architecture

- **React + Vite:** Vite provides a fast TypeScript development/build loop; React owns composition and local UI state.
- **React Router:** `src/app/Router.tsx` is the single route registry. Routes are protected at the shell boundary and currently render placeholders; business workflows are intentionally deferred.
- **React Query:** `src/app/Providers.tsx` owns one `QueryClient` with conservative query retries and no mutation retries. Feature modules will use it for server state and cache invalidation.
- **Axios:** `src/api/client.ts` owns the API base URL, bearer-token request interceptor, and response error normalization. Components do not parse Axios errors directly.
- **Authentication state:** `src/auth/AuthProvider.tsx` stores only the access token and safe user projection in `sessionStorage`. It exposes login/logout/loading/error state through `useAuth`; password input never enters persistent storage.
- **Loading and errors:** `src/components/AsyncState.tsx` provides shared loading and normalized error states so future features have consistent failure behavior.
- **Layout:** `AppShell` is the authenticated application layout. It owns navigation, responsive sidebar state, page chrome, and session controls; feature pages own their content.

## Request Flow

1. A route renders inside `Providers`.
2. A feature calls `apiClient` or React Query.
3. Axios adds the access token when present.
4. Backend `ApiErrorResponse` values become `ApiRequestError` instances.
5. The feature renders loading, data, or error state without duplicating transport logic.

## Security Decisions

- The frontend never decodes or trusts JWT claims for authorization; the backend remains authoritative.
- Session storage is used for the current MVP because the backend issues bearer access tokens and no refresh-token endpoint exists yet.
- Protected routes redirect to `/login`; logout clears both token and user projection.
- Business pages are placeholders until their corresponding backend APIs exist, preventing the UI from inventing contracts.

## Customer Dashboard

`features/customer/CustomerDashboard.tsx` is the one implemented business view. It uses
the authenticated user projection for the greeting, presents an honest empty state when no
token is active, and links to the future service, booking, queue, and history routes. It does
not fabricate appointment or queue data because the current backend exposes no customer
dashboard aggregation endpoint. Those cards can later become React Query-backed features
without changing the shell, router, auth context, or API client.
