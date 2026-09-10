"use client";

import { useMemo, useState } from "react";
import { Bot, BrainCircuit, CheckCircle2, ChevronRight, Code2, FolderTree, GitBranch, Play, Search, ShieldCheck, Sparkles, Terminal, XCircle, Zap } from "lucide-react";
import styles from "./page.module.css";

type Event = { id:string; at:string; kind:string; message:string; role?:string; detail?:string };

const starter = "Build me a production-ready full-stack SaaS application with authentication, PostgreSQL data model, REST API, responsive dashboard, tests, Docker support and deployment configuration.";

export default function Home() {
  const [request,setRequest]=useState(starter);
  const [events,setEvents]=useState<Event[]>([]);
  const [running,setRunning]=useState(false);
  const [projectId,setProjectId]=useState<string|null>(null);
  const [tab,setTab]=useState<"code"|"diff"|"chat">("code");
  const [files,setFiles]=useState<string[]>([]);
  const [active,setActive]=useState("README.md");

  const status = useMemo(() => running ? "ENGINEERING" : projectId ? "READY" : "IDLE", [running,projectId]);

  async function build() {
    if (!request.trim() || running) return;
    setRunning(true); setEvents([]); setFiles([]);
    const res = await fetch("/api/agent",{method:"POST",headers:{"content-type":"application/json"},body:JSON.stringify({request})});
    if (!res.body) { setRunning(false); return; }
    const reader=res.body.getReader(), decoder=new TextDecoder(); let buf="";
    while(true){
      const {value,done}=await reader.read(); if(done) break;
      buf += decoder.decode(value,{stream:true});
      const chunks=buf.split("\n\n"); buf=chunks.pop()||"";
      for(const chunk of chunks){
        const line=chunk.split("\n").find(x=>x.startsWith("data: "));
        if(!line) continue;
        const data=JSON.parse(line.slice(6));
        if(data.type==="project"){setProjectId(data.project.id);setFiles(data.project.files||[])}
        if(data.type==="event"){setEvents(v=>[...v,data.event]);}
        if(data.type==="complete"||data.type==="error"){setRunning(false);}
      }
    }
    setRunning(false);
  }

  return <main className={styles.shell}>
    <header className={styles.top}>
      <div className={styles.brand}><div className={styles.logo}><Sparkles size={18}/></div><b>ForgeAI</b><span className={styles.badge}>AUTONOMOUS ENGINEERING</span></div>
      <div className={styles.topRight}><span className={styles.live}><span/> {status}</span><button className={styles.ghost}><GitBranch size={15}/> main</button><button className={styles.deploy} onClick={()=>window.open("https://vercel.com/new","_blank")}>Deploy ↗</button></div>
    </header>

    <section className={styles.workspace}>
      <aside className={styles.left}>
        <div className={styles.sideTitle}><span>PROJECT</span><button>＋</button></div>
        <div className={styles.projectCard}><Code2 size={16}/><div><b>New Software</b><small>{projectId ? projectId.slice(0,8) : "unsaved workspace"}</small></div></div>
        <div className={styles.sideTitle}><span>FILES</span><Search size={14}/></div>
        <div className={styles.files}>
          {(files.length?files:["README.md","app/","lib/","tests/","Dockerfile"]).map((f,i)=><button key={f} onClick={()=>setActive(f)} className={active===f?styles.fileActive:styles.file}><span>{f.endsWith("/")?<FolderTree size={14}/>:<Code2 size={14}/>}</span>{f}</button>)}
        </div>
        <div className={styles.leftBottom}><div><ShieldCheck size={15}/> Sandbox</div><small>Execution: {process.env.NEXT_PUBLIC_EXECUTION_MODE || "controlled"}</small></div>
      </aside>

      <section className={styles.center}>
        <div className={styles.tabs}><button className={tab==="code"?styles.tabActive:styles.tab} onClick={()=>setTab("code")}><Code2 size={15}/> Code</button><button className={tab==="diff"?styles.tabActive:styles.tab} onClick={()=>setTab("diff")}>⌘ Diff</button><button className={tab==="chat"?styles.tabActive:styles.tab} onClick={()=>setTab("chat")}><Bot size={15}/> Agent Chat</button><div className={styles.path}>{active}</div></div>
        {tab==="code" && <div className={styles.editor}><div className={styles.lineNums}>{Array.from({length:26},(_,i)=><span key={i}>{String(i+1).padStart(2,"0")}</span>)}</div><pre><code><span className={styles.kw}>import</span> {"{ "}createProject{" }"} <span className={styles.kw}>from</span> <span className={styles.str}>"@/lib/agent"</span>{"\n\n"}<span className={styles.kw}>export async function</span> <span className={styles.fn}>buildSoftware</span>(request) {"{"}{"\n  "}<span className={styles.kw}>const</span> project = <span className={styles.kw}>await</span> createProject(request){"\n  "}<span className={styles.kw}>return</span> project{"\n"}{"}"}</code></pre></div>}
        {tab==="diff" && <div className={styles.empty}><Zap size={28}/><h3>Change control</h3><p>Agent changes, approvals, reversions and diffs appear here during a run.</p></div>}
        {tab==="chat" && <div className={styles.chat}>{events.slice(-12).map(e=><div key={e.id} className={styles.chatItem}><b>{e.role||"agent"}</b><span>{e.message}</span></div>)}</div>}
        <div className={styles.prompt}>
          <textarea value={request} onChange={e=>setRequest(e.target.value)} placeholder="Describe what you want to build..."/>
          <div className={styles.promptBar}><div className={styles.chips}><span><BrainCircuit size={13}/> Planner</span><span><Terminal size={13}/> Tools</span><span><ShieldCheck size={13}/> Verify</span></div><button className={styles.run} disabled={running} onClick={build}>{running?<><span className={styles.spinner}/> Engineering...</>:<><Play size={15}/> Build Software</>}</button></div>
        </div>
      </section>

      <aside className={styles.right}>
        <div className={styles.panelHead}><span>AGENT CONTROL</span><span className={styles.dot}/></div>
        <div className={styles.pipeline}>
          {["Understand","Architecture","Implement","Build & Test","Debug","Verify","Ship"].map((x,i)=><div key={x} className={styles.step}><div className={styles.stepIcon}>{i<2&&!running?<CheckCircle2 size={14}/>:<span>{i+1}</span>}</div><div><b>{x}</b><small>{i===2&&running?"writing + editing":i<2?"ready":"waiting"}</small></div>{i<6&&<ChevronRight size={13}/>}</div>)}
        </div>
        <div className={styles.panelHead}>LIVE ACTIVITY</div>
        <div className={styles.activity}>{events.length===0?<div className={styles.emptySmall}><Bot size={22}/><span>Agent activity will stream here.</span></div>:events.slice(-16).reverse().map(e=><div className={styles.activityItem} key={e.id}><div className={styles.activityIcon}>{e.kind==="error"?<XCircle size={13}/>:e.kind==="success"?<CheckCircle2 size={13}/>:<Zap size={13}/>}</div><div><b>{e.message}</b>{e.detail&&<small>{e.detail.slice(0,140)}</small>}<time>{new Date(e.at).toLocaleTimeString()}</time></div></div>)}</div>
      </aside>
    </section>
  </main>
}
