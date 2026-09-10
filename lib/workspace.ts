import fs from "node:fs/promises";import path from "node:path";import { safePath } from "./security";
export async function createWorkspace(id:string){const root=path.join(process.env.FORGEAI_WORKSPACE_ROOT||"/tmp/forgeai-workspaces",id);await fs.mkdir(root,{recursive:true});return root}
export async function listFiles(root:string):Promise<string[]>{const out:string[]=[];async function walk(dir:string){for(const e of await fs.readdir(dir,{withFileTypes:true})){if(["node_modules",".git",".next","dist","build",".turbo"].includes(e.name))continue;const full=path.join(dir,e.name),rel=path.relative(root,full).replaceAll(path.sep,"/");if(e.isDirectory())await walk(full);else out.push(rel);if(out.length>=500)return}}await walk(root);return out.sort()}
export async function readFileSafe(root:string,file:string){return fs.readFile(safePath(root,file),"utf8")}
export async function writeFileSafe(root:string,file:string,content:string){const target=safePath(root,file);await fs.mkdir(path.dirname(target),{recursive:true});await fs.writeFile(target,content,"utf8")}
export async function deleteFileSafe(root:string,file:string){await fs.unlink(safePath(root,file))}
