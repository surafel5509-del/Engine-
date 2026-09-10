# ForgeAI

ForgeAI is an autonomous software-engineering workspace: **Idea → Plan → Code → Run → Observe → Fix → Verify → Ship**.

## Functional build
- Streaming autonomous agent loop
- Mistral, Groq and Cerebras provider adapters with retries
- Settings UI for provider and browser-local API key entry
- Real project workspace, file tree, editor and save API
- Planner, architect, coder, tester, debugger and reviewer roles
- Incremental file inspection and safe file mutations
- Command allowlist and traversal protection
- Local execution mode plus a production sandbox-worker contract
- Project state, events and memory APIs
- Responsive IDE-style interface

## Run
```bash
npm install
cp .env.example .env.local
npm run typecheck
npm run dev
```

Open `http://localhost:3000`.

## API keys
Use Settings for a browser-local key, or set provider environment variables on the server. Never commit keys.

## Production execution
The safe default is `FORGEAI_EXECUTION_MODE=disabled`. Vercel/serverless is not a secure general-purpose sandbox. Connect a separate isolated container/microVM worker for generated-code execution; see `lib/worker/README.md` and `docs/security.md`.
