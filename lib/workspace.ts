import fs from "node:fs/promises";
import path from "node:path";
import { projectDir } from "./store";
import { safeJoin } from "./security";

export async function createWorkspace(id: string) {
  const root = projectDir(id);
  await fs.mkdir(root, { recursive: true });
  return root;
}

export async function writeFileSafe(root: string, relativePath: string, content: string) {
  const target = safeJoin(root, relativePath);
  await fs.mkdir(path.dirname(target), { recursive: true });
  await fs.writeFile(target, content, "utf8");
  return relativePath;
}

export async function readFileSafe(root: string, relativePath: string) {
  return fs.readFile(safeJoin(root, relativePath), "utf8");
}

export async function listFiles(root: string, current = root): Promise<string[]> {
  const entries = await fs.readdir(current, { withFileTypes: true });
  const result: string[] = [];
  for (const e of entries) {
    if (["node_modules",".git",".next","dist","build"].includes(e.name)) continue;
    const p = path.join(current, e.name);
    if (e.isDirectory()) result.push(...await listFiles(root, p));
    else result.push(path.relative(root, p));
  }
  return result;
}
