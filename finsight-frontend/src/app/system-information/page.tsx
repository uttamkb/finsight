"use client";

import { useState } from "react";
import { Info, ShieldCheck, Database, Server, Smartphone, Layers, Workflow, CheckCircle2, Zap, LayoutDashboard, Cloud, Code2, Network, Cpu, HardDrive, BrainCircuit, ChevronRight } from "lucide-react";

export default function SystemInformationPage() {
  const [activeTab, setActiveTab] = useState("overview");

  return (
    <div className="container mx-auto py-10 px-4 max-w-7xl animate-fade-in">
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-8 mb-12">
        <div className="flex items-center gap-6">
          <div className="p-4 bg-primary/10 rounded-3xl glow-primary">
            <Info className="h-12 w-12 text-primary" />
          </div>
          <div>
            <h1 className="text-4xl font-black tracking-tight leading-tight uppercase">System Blueprint</h1>
            <p className="text-base-content/40 font-bold uppercase tracking-[0.2em] text-[10px] mt-1 flex items-center gap-2">
              <Server className="h-3.5 w-3.5 text-primary" /> Architecture & Operational Matrix
            </p>
          </div>
        </div>
      </div>

      <div className="flex space-x-3 overflow-x-auto pb-6 mb-10 custom-scrollbar">
        {[
          { id: "overview", label: "Mission & Intel", icon: ShieldCheck },
          { id: "architecture", label: "Neural Topology", icon: Server },
          { id: "data-flow", label: "Logic Pipelines", icon: Workflow },
          { id: "schema", label: "Data DNA", icon: Database },
          { id: "tech-stack", label: "Core Stack", icon: Layers },
          { id: "integrations", label: "External Vectors", icon: Network },
          { id: "training", label: "OCR Synth", icon: BrainCircuit },
        ].map((tab) => (
          <button
            key={tab.id}
            onClick={() => setActiveTab(tab.id)}
            className={`flex items-center gap-3 px-6 py-3.5 rounded-2xl font-black text-[10px] uppercase tracking-[0.2em] transition-all whitespace-nowrap shadow-sm border ${
              activeTab === tab.id
                ? "bg-primary text-primary-content border-primary shadow-xl shadow-primary/20 scale-105"
                : "bg-base-300/50 border-transparent text-base-content/30 hover:text-base-content hover:bg-base-300 transition-colors"
            }`}
          >
            <tab.icon className="h-4 w-4" />
            {tab.label}
          </button>
        ))}
      </div>

      <div className="grid gap-6">
        {activeTab === "overview" && (
          <div className="grid gap-10 animate-fade-in">
            <div className="glass-panel p-10 rounded-[3rem] shadow-2xl relative overflow-hidden group hover:glow-primary transition-all duration-700">
              <div className="absolute top-0 right-0 p-12 opacity-5 scale-150 group-hover:rotate-12 transition-transform duration-1000"><ShieldCheck className="h-32 w-32" /></div>
              <h2 className="text-3xl font-black mb-8 flex items-center gap-4 tracking-tighter uppercase">
                <ShieldCheck className="h-8 w-8 text-primary group-hover:animate-pulse" />
                Strategic Configuration
              </h2>
              <p className="text-lg text-base-content/70 leading-relaxed font-bold italic">
                FinSight is an autonomous financial intelligence matrix engineered for high-fidelity association auditing. By synthesizing deep neural parsing with multi-vector reconciliation, the system enforces absolute fiscal transparency. Every operational transaction is traced from bank origin to its digital receipt signature, neutralizing financial variance through real-time AI surveillance.
              </p>
            </div>

            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-8">
              {[
                { title: "AUTONOMOUS INGESTION", desc: "Digital vacuuming of Google Workspace repositories for live financial records.", icon: Cloud, color: "primary" },
                { title: "NEURAL PARSING 3.0", desc: "Gemini 1.5 Flash sequence for high-confidence data extraction & normalization.", icon: Zap, color: "warning" },
                { title: "FORGE RECONCILIATION", desc: "Triple-threat 60:30:10 correlation matrix with tiered precision tolerance.", icon: CheckCircle2, color: "success" },
                { title: "ENTITY INTELLIGENCE", desc: "Behavioral grouping by vendor vector to map expenditure heatmaps.", icon: LayoutDashboard, color: "info" },
                { title: "AUDIT SURVEILLANCE", desc: "Persistent monitoring of operational buffer for mismatched records.", icon: ShieldCheck, color: "error" },
                { title: "TENANT ISOLATION", desc: "Hard-linked multi-associative data segregation at the kernel level.", icon: Database, color: "primary" },
              ].map((feature, i) => (
                <div key={i} className="glass-panel p-8 rounded-[2rem] hover:glow-primary transition-all group border-base-content/5">
                  <div className={`p-4 bg-${feature.color}/10 rounded-2xl w-fit mb-6 transition-all group-hover:scale-110`}>
                    <feature.icon className={`h-8 w-8 text-${feature.color}`} />
                  </div>
                  <h3 className="font-black text-lg mb-4 tracking-tight uppercase group-hover:text-primary transition-colors">{feature.title}</h3>
                  <p className="text-sm text-base-content/40 font-bold leading-relaxed">{feature.desc}</p>
                </div>
              ))}
            </div>
          </div>
        )}

        {activeTab === "architecture" && (
          <div className="grid gap-10 animate-fade-in">
            <div className="glass-panel p-10 rounded-[3rem] shadow-2xl relative border-primary/5">
              <h2 className="text-3xl font-black mb-12 flex items-center gap-4 tracking-tighter uppercase">
                <Server className="h-8 w-8 text-primary shadow-primary" />
                Operational Topology
              </h2>
              
              <div className="flex flex-col md:flex-row items-stretch justify-center gap-8 py-12 relative">
                <div className="glass-panel w-full md:w-1/4 p-8 rounded-[2rem] border border-primary/20 bg-primary/5 text-center relative z-10 transition-all hover:glow-primary hover:scale-105">
                  <Smartphone className="h-12 w-12 text-primary mx-auto mb-6 drop-shadow-[0_0_10px_rgba(59,130,246,0.5)]" />
                  <h3 className="font-black text-xl mb-4 tracking-tight uppercase">Interface Node</h3>
                  <div className="space-y-3">
                    <div className="text-[10px] font-black uppercase tracking-widest bg-primary/20 text-primary py-1.5 rounded-lg border border-primary/20">Next.js 19</div>
                    <p className="text-[10px] font-bold text-base-content/30 uppercase leading-relaxed tracking-wider">React 19, Tailwind v4, API Constants</p>
                  </div>
                </div>

                <div className="flex flex-col items-center justify-center text-primary/20 group">
                  <div className="h-px w-20 bg-primary/20 hidden md:block relative animate-pulse">
                    <div className="absolute right-0 top-1/2 -translate-y-1/2 w-3 h-3 border-t-2 border-r-2 border-primary rotate-45 shadow-primary"></div>
                  </div>
                  <span className="text-[10px] font-mono font-black mt-4 uppercase tracking-[0.2em] group-hover:text-primary transition-colors">REST/XHR</span>
                </div>

                <div className="glass-panel w-full md:w-1/3 p-8 rounded-[2rem] border border-info/20 bg-info/5 text-center relative z-10 transition-all hover:glow-info hover:scale-105">
                  <Cpu className="h-12 w-12 text-info mx-auto mb-6 drop-shadow-[0_0_10px_rgba(6,182,212,0.5)]" />
                  <h3 className="font-black text-xl mb-4 tracking-tight uppercase">Logic Core</h3>
                  <div className="space-y-3">
                    <div className="text-[10px] font-black uppercase tracking-widest bg-info/20 text-info py-1.5 rounded-lg border border-info/20">Spring Boot 3.4</div>
                    <div className="grid grid-cols-2 gap-3 mt-6">
                      {["DriveSync", "OcrParser", "GeminiEngine", "AuditFlow"].map((s) => (
                        <div key={s} className="bg-base-100/50 p-3 rounded-xl border border-white/5 text-[9px] font-black uppercase tracking-widest text-base-content/40">{s}</div>
                      ))}
                    </div>
                  </div>
                </div>

                <div className="flex flex-col items-center justify-center text-warning/20 group">
                  <div className="h-px w-20 bg-warning/30 hidden md:block relative animate-pulse">
                    <div className="absolute right-0 top-1/2 -translate-y-1/2 w-3 h-3 border-t-2 border-r-2 border-warning rotate-45 shadow-warning"></div>
                  </div>
                  <span className="text-[10px] font-mono font-black mt-4 uppercase tracking-[0.2em] group-hover:text-warning transition-colors">JPA/LibSQL</span>
                </div>

                <div className="glass-panel w-full md:w-1/4 p-8 rounded-[2rem] border border-warning/20 bg-warning/5 text-center relative z-10 transition-all hover:glow-warning hover:scale-105">
                  <Database className="h-12 w-12 text-warning mx-auto mb-6 drop-shadow-[0_0_10px_rgba(234,179,8,0.5)]" />
                  <h3 className="font-black text-xl mb-4 tracking-tight uppercase">Relational Buffer</h3>
                  <div className="space-y-4">
                    <div className="text-[10px] font-black uppercase tracking-widest bg-warning/20 text-warning py-1.5 rounded-lg border border-warning/20">Turso / LibSQL</div>
                    <p className="text-[10px] font-bold text-base-content/30 uppercase leading-relaxed tracking-widest">finsight.db | WAL Persistence</p>
                  </div>
                </div>
              </div>
            </div>
          </div>
        )}

        {/* ... Include remaining tabs ... */}
        {activeTab === "schema" && (
           <div className="grid gap-10 animate-fade-in">
             <div className="glass-panel p-10 rounded-[3rem] shadow-2xl relative border-primary/5">
                <h2 className="text-3xl font-black mb-10 flex items-center gap-4 tracking-tighter uppercase">
                  <Database className="h-8 w-8 text-primary shadow-primary" />
                  Relational Synapse Schema
                </h2>
                <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-8">
                  
                  <div className="glass-panel rounded-3xl overflow-hidden border-primary/20 hover:scale-[1.02] transition-transform">
                    <div className="bg-primary/20 p-5 border-b border-primary/20 flex items-center justify-between">
                      <span className="font-black font-mono text-xs uppercase tracking-widest text-primary">surveys & intel</span>
                      <span className="text-[9px] font-black text-white bg-primary px-2 py-1 rounded-lg uppercase tracking-wider">Turso Cloud</span>
                    </div>
                    <div className="p-6 space-y-3 text-[10px] font-mono font-bold text-base-content/50 uppercase tracking-widest">
                      <div className="flex justify-between items-center bg-base-100/30 p-2 rounded-lg"><span>survey_id</span> <span className="text-primary">UID_STRING</span></div>
                      <div className="flex justify-between items-center bg-base-100/30 p-2 rounded-lg"><span>responses</span> <span className="text-info">BLOB_JSON</span></div>
                      <div className="flex justify-between items-center bg-base-100/30 p-2 rounded-lg"><span>sentiment</span> <span className="text-success">FLOAT_32</span></div>
                    </div>
                  </div>
                  
                  {[
                    { name: 'receipts', type: 'Primary Data', schema: [
                      ['tenant_id', 'TEXT NOT NULL'],
                      ['drive_id', 'TEXT UNIQUE'],
                      ['vendor', 'TEXT INDEXED'],
                      ['amount', 'DECIMAL(15,2)'],
                      ['hash', 'SHA256_HASH'],
                    ]},
                    { name: 'bank_txns', type: 'Financial Data', schema: [
                      ['tx_date', 'DATE NOT NULL'],
                      ['desc', 'TEXT INDEXED'],
                      ['amount', 'DECIMAL(15,2)'],
                      ['reconciled', 'BOOL_INT'],
                      ['receipt_id', 'FK_INT'],
                    ]},
                    { name: 'audit_trail', type: 'Forensic Logic', schema: [
                      ['txn_id', 'FK_INT INDEXED'],
                      ['issue', 'ENUM_MISMATCH'],
                      ['score', 'FLOAT (60:30:10)'],
                      ['resolved', 'BOOL_INT'],
                    ]},
                    { name: 'anomalies', type: 'Risk Data', schema: [
                      ['reason', 'AI_DEDUCTION'],
                      ['amount', 'DECIMAL(15,2)'],
                      ['vector', 'SENSOR_TAG'],
                    ]},
                    { name: 'app_config', type: 'System Core', schema: [
                      ['service_acc', 'SECURE_TEXT'],
                      ['gemini_key', 'SECURE_TEXT'],
                      ['synced_at', 'EPOCH_MS'],
                    ]}
                  ].map((table) => (
                    <div key={table.name} className="glass-panel rounded-3xl overflow-hidden border-base-content/5 hover:scale-[1.02] transition-transform">
                      <div className="bg-base-300/40 p-5 border-b border-base-content/5 flex items-center justify-between">
                        <span className="font-black font-mono text-xs uppercase tracking-widest text-base-content/80">{table.name}</span>
                        <span className="text-[9px] font-black text-base-content/40 bg-base-100/50 px-2 py-1 rounded-lg uppercase tracking-wider">{table.type}</span>
                      </div>
                      <div className="p-6 space-y-2.5 text-[10px] font-mono font-bold text-base-content/30 uppercase tracking-widest">
                        {table.schema.map(([col, typ]) => (
                          <div key={col} className="flex justify-between items-center group/row">
                            <span className="group-hover/row:text-primary transition-colors">{col}</span>
                            <span className="opacity-40">{typ}</span>
                          </div>
                        ))}
                      </div>
                    </div>
                  ))}
                </div>
             </div>
           </div>
        )}

        {activeTab === "tech-stack" && (
           <div className="grid gap-6 animate-in slide-in-from-bottom-4 duration-500">
             <div className="p-6 rounded-2xl border bg-base-200 shadow-sm">
                <h2 className="text-xl font-bold mb-6 flex items-center gap-2">
                  <Layers className="h-5 w-5 text-primary" />
                  Engineering Technology Stack
                </h2>
                
                <div className="grid grid-cols-1 md:grid-cols-2 gap-8">
                  <div>
                    <h3 className="font-semibold text-lg border-b pb-2 mb-4 flex items-center gap-2">
                       <LayoutDashboard className="h-4 w-4" /> Frontend (Web Client)
                    </h3>
                    <ul className="space-y-3">
                      <li className="flex justify-between items-center border p-3 rounded-lg bg-base-100 text-sm">
                        <span className="font-medium">Framework</span> <span className="font-mono text-base-content/60">Next.js 16 (React 19)</span>
                      </li>
                      <li className="flex justify-between items-center border p-3 rounded-lg bg-base-100 text-sm">
                        <span className="font-medium">Language</span> <span className="font-mono text-base-content/60">TypeScript</span>
                      </li>
                      <li className="flex justify-between items-center border p-3 rounded-lg bg-base-100 text-sm">
                        <span className="font-medium">Styling</span> <span className="font-mono text-base-content/60">Tailwind CSS v4</span>
                      </li>
                      <li className="flex justify-between items-center border p-3 rounded-lg bg-base-100 text-sm">
                        <span className="font-medium">Icons & Visuals</span> <span className="font-mono text-base-content/60">Lucide, Recharts</span>
                      </li>
                    </ul>
                  </div>

                  <div>
                    <h3 className="font-semibold text-lg border-b pb-2 mb-4 flex items-center gap-2">
                       <Cpu className="h-4 w-4" /> Backend API (Server)
                    </h3>
                    <ul className="space-y-3">
                      <li className="flex justify-between items-center border p-3 rounded-lg bg-base-100 text-sm">
                        <span className="font-medium">Framework</span> <span className="font-mono text-base-content/60">Spring Boot 3.4.3</span>
                      </li>
                      <li className="flex justify-between items-center border p-3 rounded-lg bg-base-100 text-sm">
                        <span className="font-medium">Language</span> <span className="font-mono text-base-content/60">Java 21</span>
                      </li>
                      <li className="flex justify-between items-center border p-3 rounded-lg bg-base-100 text-sm">
                        <span className="font-medium">Build Tool</span> <span className="font-mono text-base-content/60">Maven</span>
                      </li>
                      <li className="flex justify-between items-center border p-3 rounded-lg bg-base-100 text-sm">
                        <span className="font-medium">Database Layer</span> <span className="font-mono text-base-content/60">Spring Data JPA, Hibernate</span>
                      </li>
                      <li className="flex justify-between items-center border p-3 rounded-lg bg-base-100 text-sm">
                        <span className="font-medium">Embedded DB</span> <span className="font-mono text-base-content/60">SQLite / Turso (LibSQL)</span>
                      </li>
                    </ul>
                  </div>
                </div>
             </div>
           </div>
        )}

        {activeTab === "data-flow" && (
           <div className="grid gap-6 animate-in slide-in-from-bottom-4 duration-500">
             <div className="p-6 rounded-2xl border bg-base-200 shadow-sm">
                <h2 className="text-xl font-bold mb-4 flex items-center gap-2">
                  <Workflow className="h-5 w-5 text-primary" />
                  Core Data Processing Pipeline
                </h2>
                <div className="space-y-6 pt-4">
                  {[
                    { step: "1", title: "Drive & Sheets Ingestion", desc: "DriveSyncService and GoogleSheetsSyncService poll Google Workspace for new expense receipts and resident survey responses." },
                    { step: "2", title: "OCR & Hybrid Parsing", desc: "OcrService extracts receipt data. Bank statements follow a Hybrid Pipeline (Table Extraction -> Fallback -> Gemini) with Dynamic Column Detection." },
                    { step: "3", title: "Gemini 3 & Resident Insights", desc: "Bank statement categorization and Resident sentiment generation. Gemini acts as an intelligent fallback for malformed table data." },
                    { step: "4", title: "Financial Reconciliation", desc: "ReconciliationService uses a 60-30-10 scoring logic with tiered BigDecimal precision and SHA-256 deduplication." }
                  ].map((pipe, i) => (
                    <div key={i} className="flex gap-4 p-4 border rounded-xl bg-base-100 relative overflow-hidden group hover:border-primary/50 transition-colors">
                      <div className="absolute left-0 top-0 bottom-0 w-1 bg-primary/20 group-hover:bg-primary transition-colors"></div>
                      <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-primary text-primary-content font-bold font-mono shadow-md">
                        {pipe.step}
                      </div>
                      <div>
                        <h3 className="font-bold mb-1 group-hover:text-primary transition-colors">{pipe.title}</h3>
                        <p className="text-base-content/60 text-sm leading-relaxed">{pipe.desc}</p>
                      </div>
                    </div>
                  ))}
                </div>
             </div>
           </div>
        )}

        {activeTab === "integrations" && (
           <div className="grid gap-6 animate-in slide-in-from-bottom-4 duration-500">
             <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
                <div className="p-6 rounded-2xl border bg-base-200 shadow-sm relative overflow-hidden">
                  <div className="absolute right-0 top-0 w-32 h-32 bg-success/5 rounded-full blur-3xl"></div>
                  <HardDrive className="h-8 w-8 text-success mb-4 relative z-10" />
                  <h3 className="text-lg font-bold mb-2">Google Drive API v3</h3>
                  <p className="text-sm text-base-content/60 mb-4">Leveraged for a continuous ingestion pipeline. Features **Robust Regex Validation** for Service Account JSONs to handle varied formatting and ensure configuration reliability.</p>
                  <div className="text-xs font-mono bg-base-300 p-2 rounded">com.google.apis:google-api-services-drive</div>
                </div>

                <div className="p-6 rounded-2xl border bg-base-200 shadow-sm relative overflow-hidden">
                  <div className="absolute right-0 top-0 w-32 h-32 bg-info/5 rounded-full blur-3xl"></div>
                  <Code2 className="h-8 w-8 text-info mb-4 relative z-10" />
                   <h3 className="text-lg font-bold mb-2">Google Gemini 3 Series</h3>
                  <p className="text-sm text-base-content/60 mb-4">Master categorization engine and anomaly detection. Acts as a high-confidence fallback in the Bank Statement Hybrid Parser when table extraction yield is low (&lt; 80%).</p>
                  <div className="text-xs font-mono bg-base-300 p-2 rounded">API Models: gemini-3-flash-preview, gemini-3.1-pro-preview</div>
                </div>
             </div>
           </div>
        )}

        {activeTab === "training" && (
          <div className="grid gap-6 animate-in slide-in-from-bottom-4 duration-500">
            <div className="p-6 rounded-2xl border bg-base-200 shadow-sm">
               <h2 className="text-xl font-bold mb-4 flex items-center gap-2">
                 <BrainCircuit className="h-5 w-5 text-primary" />
                 OCR Training & Fine-tuning
               </h2>
               <p className="text-base-content/60 mb-6">
                 FinSight allows you to improve the local OCR model's accuracy by training it on your specific receipt patterns. 
                 This "Human-in-the-loop" system converts your manual corrections into training data.
               </p>
               
               <div className="bg-primary/5 border-2 border-dashed border-primary/20 rounded-2xl p-10 text-center">
                  <Cpu className="h-12 w-12 text-primary/50 mx-auto mb-4" />
                  <h3 className="text-lg font-bold">Launch Training Center</h3>
                  <p className="text-sm text-base-content/60 mb-6 max-w-md mx-auto">
                    Manage harvested golden samples, prepare datasets, and monitor the fine-tuning pipeline.
                  </p>
                  <a 
                    href="/system-information/training"
                    className="inline-flex items-center gap-2 px-8 py-3 bg-indigo-600 text-primary-content rounded-xl font-bold hover:bg-indigo-700 transition-all shadow-lg shadow-indigo-500/20"
                  >
                    Go to Training Dashboard <ChevronRight className="h-4 w-4" />
                  </a>
               </div>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
