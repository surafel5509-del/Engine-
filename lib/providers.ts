import { z } from "zod";

const ActionSchema = z.object({
  type: z.enum(["write_file","read_file","run_command","finish","note"]),
  path: z.string().optional(),
  content: z.string().optional(),
  command: z.string().optional(),
  message: z.string().optional()
});
export type ModelAction = z.infer<typeof ActionSchema>;

export type Provider = { name: string; model: string; complete: (system: string, user: string) => Promise<string> };

function envProvider(name: string, key: string | undefined, endpoint: string, model: string): Provider | null {
  if (!key) return null;
  return {
    name, model,
    async complete(system, user) {
      const res = await fetch(endpoint, {
        method: "POST",
        headers: { "content-type": "application/json", authorization: `Bearer ${key}` },
        body: JSON.stringify({
          model,
          temperature: 0.15,
          messages: [{ role: "system", content: system }, { role: "user", content: user }]
        })
      });
      if (!res.ok) throw new Error(`${name} API ${res.status}: ${(await res.text()).slice(0,500)}`);
      const data = await res.json();
      return data?.choices?.[0]?.message?.content ?? "";
    }
  };
}

export function getProvider(request: string): Provider | null {
  const forced = process.env.FORGEAI_PROVIDER?.toLowerCase();
  const candidates = [
    forced === "mistral" || !forced ? envProvider("Mistral", process.env.MISTRAL_API_KEY, "https://api.mistral.ai/v1/chat/completions", process.env.MISTRAL_MODEL || "mistral-large-latest") : null,
    forced === "groq" || !forced ? envProvider("Groq", process.env.GROQ_API_KEY, "https://api.groq.com/openai/v1/chat/completions", process.env.GROQ_MODEL || "llama-3.3-70b-versatile") : null,
    forced === "cerebras" || !forced ? envProvider("Cerebras", process.env.CEREBRAS_API_KEY, "https://api.cerebras.ai/v1/chat/completions", process.env.CEREBRAS_MODEL || "llama-3.3-70b") : null
  ].filter(Boolean) as Provider[];
  if (!candidates.length) return null;
  const complexity = (request.match(/\b(database|auth|payment|mobile|desktop|microservice|realtime|security|migration|production)\b/gi) || []).length;
  return candidates[Math.min(complexity > 2 ? 0 : candidates.length - 1, candidates.length - 1)];
}

export function parseActions(text: string): ModelAction[] {
  const cleaned = text.replace(/```json|```/g, "").trim();
  try {
    const value = JSON.parse(cleaned);
    const list = Array.isArray(value) ? value : value.actions;
    if (!Array.isArray(list)) return [];
    return list.map(x => ActionSchema.parse(x));
  } catch { return []; }
}
