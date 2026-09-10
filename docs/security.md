# Security Model

1. Never expose arbitrary shell execution from a public route.
2. Keep provider keys server-side.
3. Validate every workspace path against its root.
4. Reject shell metacharacters and commands outside the allowlist.
5. Enforce command timeout.
6. Production sandbox: ephemeral container/VM, no network by default, resource limits, non-root user, read-only base filesystem.
7. Never mount production secrets into generated-code execution.
8. Persist audit events and tool inputs/outputs with redaction.
9. Require explicit deployment approval unless an organization policy says otherwise.
10. Apply authentication, rate limits, CSRF protections where applicable, and project-level authorization before exposing project APIs.
