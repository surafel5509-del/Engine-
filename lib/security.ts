import path from "node:path";

const ALLOWED = new Set([
  "pwd","ls","find","cat","head","tail","git","node","npm","pnpm","yarn","python","python3",
  "pytest","go","cargo","rustc","java","mvn","gradle","dotnet","docker"
]);

export function safeJoin(root: string, requested: string) {
  const base = path.resolve(root);
  const target = path.resolve(base, requested);
  if (target !== base && !target.startsWith(base + path.sep)) throw new Error("Path escapes workspace");
  return target;
}

export function validateCommand(command: string) {
  const trimmed = command.trim();
  if (!trimmed) throw new Error("Empty command");
  if (/[;&|`$><]/.test(trimmed)) throw new Error("Shell operators are blocked");
  const binary = trimmed.split(/\s+/)[0].replace(/^.*\//, "");
  if (!ALLOWED.has(binary)) throw new Error(`Command not allowed: ${binary}`);
  return trimmed;
}
