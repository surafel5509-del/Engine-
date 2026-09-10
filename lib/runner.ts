import { spawn } from "node:child_process";
import { validateCommand } from "./security";

export type RunResult = { code: number; stdout: string; stderr: string; timedOut: boolean };

export async function runLocalCommand(cwd: string, command: string): Promise<RunResult> {
  if (process.env.FORGEAI_EXECUTION_MODE !== "local") {
    throw new Error("Execution is disabled. Set FORGEAI_EXECUTION_MODE=local only on a trusted development machine.");
  }
  const safe = validateCommand(command);
  const timeout = Number(process.env.FORGEAI_COMMAND_TIMEOUT_MS || 120000);
  return new Promise((resolve, reject) => {
    const [bin, ...args] = safe.split(/\s+/);
    const child = spawn(bin, args, { cwd, env: { ...process.env, CI: "1" }, shell: false });
    let stdout="", stderr="", timedOut=false;
    const timer = setTimeout(() => { timedOut=true; child.kill("SIGKILL"); }, timeout);
    child.stdout.on("data", d => stdout += d.toString().slice(0, 20000));
    child.stderr.on("data", d => stderr += d.toString().slice(0, 20000));
    child.on("error", reject);
    child.on("close", code => { clearTimeout(timer); resolve({ code: code ?? 1, stdout, stderr, timedOut }); });
  });
}

/*
Production sandbox contract:
- worker receives a project snapshot
- executes in an ephemeral container/VM
- network is disabled by default
- CPU/memory/process/time limits are enforced
- workspace is mounted read/write only for that run
- secrets are never mounted
- result contains stdout/stderr/exit code/artifacts
*/
