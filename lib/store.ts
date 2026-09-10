import fs from "node:fs/promises";
import path from "node:path";
import { ProjectState } from "./types";

const ROOT = process.env.FORGEAI_WORKSPACE_ROOT || "/tmp/forgeai-workspaces";

export async function ensureRoot() { await fs.mkdir(ROOT, { recursive: true }); }
export function projectDir(id: string) { return path.join(ROOT, id); }
export async function saveState(state: ProjectState) {
  await ensureRoot();
  await fs.mkdir(projectDir(state.id), { recursive: true });
  await fs.writeFile(path.join(projectDir(state.id), "state.json"), JSON.stringify(state, null, 2));
}
export async function loadState(id: string): Promise<ProjectState | null> {
  try { return JSON.parse(await fs.readFile(path.join(projectDir(id), "state.json"), "utf8")); }
  catch { return null; }
}
