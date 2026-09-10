import { NextRequest } from "next/server";
import { getProject } from "@/lib/agent";

export async function GET(_req: NextRequest, ctx: { params: Promise<{id:string}> }) {
  const { id } = await ctx.params;
  const project = await getProject(id);
  if (!project) return Response.json({ error:"not found" }, {status:404});
  return Response.json(project);
}
