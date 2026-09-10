# ForgeAI

ForgeAI is an autonomous software-engineering workspace: **Idea → Plan → Code → Run → Observe → Fix → Verify → Ship**.

## Included in this build

- IDE-style workspace UI
- Project/session state model
- Task graph with dependencies, priorities, retries and progress
- Real Mistral/Groq/Cerebras provider abstraction using server-side API keys
- Planner → Coder → Debugger → Tester → Reviewer role orchestration
- JSON action protocol for model-to-tool execution
- Safe filesystem tools with path validation
- Command policy and bounded execution
- Build/test/error recovery loop
- SSE real-time agent event stream
- Project memory / architecture notes
- Git command adapter (opt-in)
- Deployment adapter interface
- Docker sandbox runner specification for isolated worker deployments
- Existing-project inspection model
- Change/diff/approval concepts
- Graceful fallback when no model key is configured

## Important production boundary

`FORGEAI_EXECUTION_MODE=disabled` is the safe default. The local command runner is intended for a trusted development machine. For production, generated code must execute in an isolated worker/container/VM, not inside the public Next.js request process. The repository includes the sandbox interface and Docker runner design so that worker can be attached without exposing arbitrary shell execution to the web app.

## Run

```bash
npm install
cp .env.example .env.local
npm run typecheck
npm run dev
```

Open http://localhost:3000.

## Provider routing

The server chooses a configured provider. Set `FORGEAI_PROVIDER` if you want a fixed provider, otherwise it scores the request for complexity and chooses among configured providers.

Model names are environment variables so they can be updated without changing application code.
