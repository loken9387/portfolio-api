# WebSocket + xterm.js Terminal (Design & Security Notes)

This document describes the approach for option 1: a browser-based terminal backed by a WebSocket server that attaches to container shells (e.g., via `docker exec -it`). It focuses on security protocols and future-proofing so implementation can be added safely later.

## Goals
- Provide an in-browser terminal with ANSI/pty fidelity using xterm.js.
- Keep security controls first-class: strong authn/authz, container scoping, and auditable activity.
- Make the implementation modular so future features (e.g., read-only mode) are easy to add.

## High-Level Flow
1. **Client** loads a page that bootstraps xterm.js (with fit/addon if desired).
2. **Client** opens a secure WebSocket connection (wss) to the backend endpoint (e.g., `/term`).
3. **Backend** authenticates the upgrade request (JWT/session/mTLS) and authorizes access to the requested container/session profile.
4. **Backend** creates a `docker exec` session (or equivalent runtime exec) with `Tty=true`, attaching stdin/stdout/stderr.
5. **Backend** hijacks the exec stream and bridges bytes to/from the WebSocket.
6. **Client** writes incoming WebSocket data to xterm.js and forwards keystrokes; resize events call `/exec/{id}/resize`.
7. **Session lifecycle** is controlled server-side (timeouts, idle kicks, audit logging).

## Security Protocols to Bake In
- **Transport security:** Require HTTPS/WSS for all traffic. Optionally enforce mTLS between the backend and Docker daemon.
- **Authentication:** Validate WebSocket upgrades with JWT or session cookies. Use short-lived tokens with refresh separate from terminal data channel.
- **Authorization:** Maintain an allowlist/ACL mapping user -> permissible container IDs or labels. Deny by default. Consider role-based scopes (read-only vs. interactive).
- **Command surface:** Default to launching a login shell only. For stricter control, whitelist commands or enable read-only view (no stdin wired).
- **Session limits:** Enforce per-user and per-tenant limits on concurrent sessions, wall-clock time, and idle timeouts. Clean up exec sessions on disconnect.
- **Resource controls:** Use container runtime features to cap CPU/memory for exec sessions where applicable. Avoid attaching to privileged containers unless explicitly allowed.
- **Audit logging:** Log connection attempts, auth decisions, exec command, container target, duration, and exit codes. Store logs centrally for review.
- **Input/output filtering:** Sanitize environment variables, and consider escaping/validating resize and control messages to avoid backend crashes.
- **Rate limiting:** Apply rate limits on WebSocket upgrades and message throughput to mitigate abuse.
- **Secrets handling:** Do not inject secrets into shell environments; use ephemeral credentials where required.

## Implementation Sketch (to keep code simple later)
- **Backend layer:**
  - A WebSocket route `/term` that expects a token and a container identifier in the query or headers.
  - A session manager that owns exec lifecycle and enforces limits (timeouts, max sessions).
  - A bridge component that maps WebSocket messages -> exec stdin, and exec stdout/stderr -> WebSocket frames.
  - A resize handler: client sends `{type: "resize", cols, rows}`; backend calls Docker exec resize endpoint.
- **Client layer:**
  - xterm.js wired to the WebSocket; writes inbound data and forwards keystrokes.
  - Sends resize messages (debounced) on window/terminal size changes.
  - Shows connection state and gracefully handles reconnect/expired token flows.

## Security-First Defaults to Implement
- Require auth before creating any exec session; reject unauthenticated upgrades early.
- Deny access to containers not explicitly allowed for the user.
- Limit session lifetime and clean up execs when the socket closes.
- Disable stdin for read-only viewers; make this a configurable mode per session token.
- Centralize logging around session start/stop and errors.

## Operational Considerations
- **Deployment:** Place backend behind a reverse proxy that terminates TLS and applies WebSocket-aware routing.
- **Monitoring:** Track auth failures, session counts, and error rates; alert on spikes.
- **Testing:** Use integration tests that spin up a disposable container and assert basic shell I/O, resize behavior, and auth rejection paths.

## Future Extensions (kept easy by the above)
- **Recorded sessions:** Stream exec output to object storage for later playback; ensure consent and retention policies.
- **Sudo/role elevation:** If needed, gate via additional approval steps and short-lived elevated tokens.
- **Multi-tenancy:** Namespaced ACLs and per-tenant resource quotas.
- **Alternate backends:** Swap Docker exec with Kubernetes exec or SSH while keeping the WebSocket bridge and client unchanged.

By following this outline, the eventual implementation will have security baked in from the start while preserving the “real terminal” experience via xterm.js and WebSocket-backed pty sessions.
