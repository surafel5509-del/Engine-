export type AgentRole = "planner" | "coder" | "debugger" | "tester" | "reviewer";
export type TaskStatus = "pending" | "running" | "blocked" | "done" | "failed";
export type EventKind = "plan" | "task" | "tool" | "model" | "command" | "error" | "success" | "info";

export type Task = {
  id: string; title: string; description: string; role: AgentRole;
  status: TaskStatus; priority: number; dependsOn: string[]; retries: number; maxRetries: number;
};

export type AgentEvent = {
  id: string; at: string; kind: EventKind; message: string;
  role?: AgentRole; taskId?: string; detail?: string;
};

export type ProjectState = {
  id: string; name: string; root: string; request: string;
  stack: string[]; tasks: Task[]; events: AgentEvent[];
  files: string[]; memory: string[]; iteration: number; status: "idle" | "running" | "done" | "failed";
  createdAt: string; updatedAt: string;
};
