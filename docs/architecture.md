# ForgeAI Architecture

## Control plane
Next.js handles authentication, projects, sessions, UI streaming and orchestration metadata.

## Agent plane
The orchestrator loops over role-specific reasoning:
Planner → Coder → Tester → Debugger → Reviewer.

The model emits small JSON actions. The control plane validates each action before dispatch.

## Tool plane
Filesystem tools are path-scoped. Commands are blocked unless explicitly allowed. Production execution belongs in an isolated worker.

## State
ProjectState records:
- request and stack decisions
- task graph
- iterations and retries
- events
- file inventory
- project memory

The default file store is a local development adapter. For production, replace it with Postgres/Redis/object storage behind the same interface.

## Deployment
The UI can send the user to Vercel. Actual deployment should happen only after:
1. build
2. tests
3. security checks
4. preview verification
5. user approval or configured policy

Vercel is appropriate for the control plane/web applications, while arbitrary generated code should execute in isolated workers.
