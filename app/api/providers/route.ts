import { providerConfig } from "@/lib/providers";export const runtime="nodejs";export async function GET(){return Response.json(providerConfig())}
