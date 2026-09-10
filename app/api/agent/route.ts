import { NextRequest } from "next/server";
import { createProject, runAgent } from "@/lib/agent";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";
export const maxDuration = 300;

export async function POST(req: NextRequest) {
  const body = await req.json().catch(() => ({}));
  const request = String(body.request || "").trim();
  if (!request) return Response.json({ error: "request is required" }, { status: 400 });

  const project = await createProject(request, String(body.name || "ForgeAI Project"));
  const encoder = new TextEncoder();
  const stream = new ReadableStream({
    async start(controller) {
      const send = (data: unknown) => controller.enqueue(encoder.encode(`data: ${JSON.stringify(data)}\n\n`));
      send({ type:"project", project });
      try {
        await runAgent(project, async e => send({ type:"event", event:e }));
        send({ type:"complete" });
      } catch (error) {
        send({ type:"error", error:String(error) });
      } finally { controller.close(); }
    }
  });
  return new Response(stream, { headers: { "Content-Type":"text/event-stream", "Cache-Control":"no-cache, no-transform", "Connection":"keep-alive" }});
}
