import crypto from "node:crypto";
import { createWorkspace, listFiles, readFileSafe, writeFileSafe } from "./workspace";
import { loadState, saveState } from "./store";
import { getProvider, parseActions } from "./providers";
import { runLocalCommand } from "./runner";
import { AgentEvent, ProjectState, Task } from "./types";

const id = () => crypto.randomUUID();
const now = () => new Date().toISOString();

function event(state: ProjectState, kind: AgentEvent["kind"], message: string, role?: AgentEvent["role"], detail?: string) {
  state.events.push({ id:id(), at:now(), kind, message, role, detail });
  state.updatedAt = now();
}

function initialTasks(): Task[] {
  return [
    { id:id(), title:"Understand request", description:"Extract requirements and constraints.", role:"planner", status:"pending", priority:1, dependsOn:[], retries:0, maxRetries:2 },
    { id:id(), title:"Design architecture", description:"Choose stack, boundaries, data model and verification strategy.", role:"planner", status:"pending", priority:2, dependsOn:[], retries:0, maxRetries:2 },
    { id:id(), title:"Implement software", description:"Create or modify files to satisfy the request.", role:"coder", status:"pending", priority:3, dependsOn:[], retries:0, maxRetries:3 },
    { id:id(), title:"Build and test", description:"Run appropriate checks and capture failures.", role:"tester", status:"pending", priority:4, dependsOn:[], retries:0, maxRetries:3 },
    { id:id(), title:"Debug and verify", description:"Fix failures and review the final result.", role:"debugger", status:"pending", priority:5, dependsOn:[], retries:0, maxRetries:3 }
  ];
}

export async function createProject(request: string, name = "ForgeAI Project") {
  const projectId = id();
  const root = await createWorkspace(projectId);
  const state: ProjectState = {
    id:projectId, name, root, request, stack:[], tasks:initialTasks(), events:[], files:[],
    memory:["User intent: " + request], iteration:0, status:"running", createdAt:now(), updatedAt:now()
  };
  await saveState(state);
  event(state, "info", "Workspace created");
  await saveState(state);
  return state;
}

const system = `You are ForgeAI, an autonomous software engineer.
You operate inside a bounded workspace. Return ONLY JSON: {"actions":[...]}.
Available actions:
write_file {path,content}
read_file {path}
run_command {command}
note {message}
finish {message}
Rules:
- Prefer inspecting before changing.
- Never use absolute paths.
- Never use shell operators.
- Choose a practical production stack from the user's requirements.
- Keep actions small and verifiable.
- After coding, run build/test commands appropriate to the detected stack.
- If a command fails, diagnose it and issue a minimal fix.
- Do not claim success without verification.`;

export async function runAgent(state: ProjectState, emit: (e: AgentEvent)=>Promise<void>) {
  const provider = getProvider(state.request);
  if (!provider) {
    event(state, "error", "No model provider configured", "planner", "Add MISTRAL_API_KEY, GROQ_API_KEY or CEREBRAS_API_KEY.");
    state.status = "failed"; await saveState(state); return;
  }
  event(state, "model", `Using ${provider.name} / ${provider.model}`, "planner"); await saveState(state);

  const max = Number(process.env.FORGEAI_MAX_ITERATIONS || 8);
  for (let i=0; i<max; i++) {
    state.iteration = i + 1;
    const files = await listFiles(state.root);
    state.files = files;
    const context = [
      `REQUEST:\n${state.request}`,
      `ITERATION: ${state.iteration}/${max}`,
      `FILES:\n${files.slice(0,120).join("\n") || "(empty)"}`,
      `MEMORY:\n${state.memory.slice(-12).join("\n")}`,
      `RECENT EVENTS:\n${state.events.slice(-12).map(e=>e.message).join("\n")}`
    ].join("\n\n");
    const text = await provider.complete(system, context);
    event(state, "model", "Model produced next engineering actions", "coder", text.slice(0,1800));
    const actions = parseActions(text);
    if (!actions.length) {
      event(state, "error", "Model output was not valid action JSON", "debugger");
      await saveState(state); await emit(state.events.at(-1)!); continue;
    }
    let finished = false;
    for (const action of actions.slice(0,8)) {
      if (action.type === "write_file" && action.path && action.content !== undefined) {
        await writeFileSafe(state.root, action.path, action.content);
        event(state, "tool", `Wrote ${action.path}`, "coder"); await emit(state.events.at(-1)!);
      } else if (action.type === "read_file" && action.path) {
        try {
          const content = await readFileSafe(state.root, action.path);
          event(state, "tool", `Read ${action.path}`, "coder", content.slice(0,1400));
        } catch (e) { event(state, "error", `Read failed: ${action.path}`, "debugger", String(e)); }
        await emit(state.events.at(-1)!);
      } else if (action.type === "run_command" && action.command) {
        try {
          const result = await runLocalCommand(state.root, action.command);
          event(state, "command", `${action.command} → exit ${result.code}`, "tester", (result.stdout + "\n" + result.stderr).slice(0,2000));
        } catch (e) { event(state, "error", `Command blocked/failed: ${action.command}`, "debugger", String(e)); }
        await emit(state.events.at(-1)!);
      } else if (action.type === "note" && action.message) {
        state.memory.push(action.message); event(state, "info", action.message, "reviewer"); await emit(state.events.at(-1)!);
      } else if (action.type === "finish") {
        event(state, "success", action.message || "Verified complete", "reviewer"); state.status="done"; finished=true; await emit(state.events.at(-1)!);
      }
    }
    await saveState(state);
    if (finished) break;
  }
  if (state.status === "running") {
    state.status = "failed";
    event(state, "error", "Iteration limit reached before verified completion", "debugger");
    await saveState(state); await emit(state.events.at(-1)!);
  }
  return state;
}

export async function getProject(id: string) { return loadState(id); }
