"use client";

import { useState } from "react";
import { Info, ShieldCheck, Database, Server, Smartphone, Layers, Workflow, CheckCircle2, Zap, LayoutDashboard, Cloud, Code2, Network, Cpu, HardDrive } from "lucide-react";

export default function SystemInformationPage() {
  const [activeTab, setActiveTab] = useState("overview");

  return (
    <div className="container mx-auto py-10 px-4 max-w-7xl animate-in fade-in duration-500">
      <div className="flex items-center gap-3 mb-8">
        <div className="p-3 bg-primary/10 rounded-xl text-primary">
          <Info className="h-8 w-8" />
        </div>
        <div>
          <h1 className="text-3xl font-bold tracking-tight">System Information & Architecture</h1>
          <p className="text-muted-foreground">Comprehensive documentation of the FinSight live system and components.</p>
        </div>
      </div>

      <div className="flex space-x-2 overflow-x-auto pb-4 mb-6 scrollbar-hide">
        {[
          { id: "overview", label: "Overview & Features", icon: ShieldCheck },
          { id: "architecture", label: "System Architecture", icon: Server },
          { id: "data-flow", label: "Data Flow Pipelines", icon: Workflow },
          { id: "schema", label: "Database Schema", icon: Database },
          { id: "tech-stack", label: "Technology Stack", icon: Layers },
          { id: "integrations", label: "Integrations", icon: Network },
        ].map((tab) => (
          <button
            key={tab.id}
            onClick={() => setActiveTab(tab.id)}
            className={`flex items-center gap-2 px-4 py-2.5 rounded-xl font-medium text-sm transition-all whitespace-nowrap ${
              activeTab === tab.id
                ? "bg-primary text-primary-foreground shadow-md"
                : "bg-muted hover:bg-muted/80 text-muted-foreground hover:text-foreground"
            }`}
          >
            <tab.icon className="h-4 w-4" />
            {tab.label}
          </button>
        ))}
      </div>

      <div className="grid gap-6">
        {activeTab === "overview" && (
          <div className="grid gap-6 animate-in slide-in-from-bottom-4 duration-500">
            <div className="p-6 rounded-2xl border bg-card shadow-sm">
              <h2 className="text-xl font-bold mb-4 flex items-center gap-2">
                <ShieldCheck className="h-5 w-5 text-primary" />
                Product Mission
              </h2>
              <p className="text-muted-foreground leading-relaxed">
                FinSight is an intelligent financial management platform engineered specifically for Apartment and Housing Associations. It automates the ingestion, parsing, and reconciliation of association expenses by combining optical character recognition (OCR) with robust reconciliation algorithms. The system aims to replace manual ledger tracking with an automated pipeline that traces every spent cent from bank statements directly to physical/digital receipts, ensuring 100% financial transparency and fraud prevention.
              </p>
            </div>

            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
              {[
                { title: "Automated Ingestion", desc: "Watch Google Drive folders for new expense receipts and invoices automatically.", icon: Cloud },
                { title: "AI-Powered Parsing (2.0)", desc: "Extract vendor names, dates, amounts, and metadata from receipts using Gemini 2.0 Flash with robust raw-text fallback.", icon: Zap },
                { title: "Bank Reconciliation", desc: "Correlate and verify bank statement line items against parsed receipt documents seamlessly.", icon: CheckCircle2 },
                { title: "Vendor Intelligence", desc: "Aggregate transactions by vendor to identify spending patterns and top payees.", icon: LayoutDashboard },
                { title: "Conflict Resolution", desc: "Audit trails automatically flag and queue mismatched amounts or missing records for manual review.", icon: ShieldCheck },
                { title: "Multi-Tenancy", desc: "Data is segregated by `tenant_id` at the database level, allowing for secure multi-association management.", icon: Database },
              ].map((feature, i) => (
                <div key={i} className="p-5 rounded-xl border bg-card/50 hover:bg-card transition-colors">
                  <feature.icon className="h-6 w-6 text-primary mb-3" />
                  <h3 className="font-semibold text-lg mb-2">{feature.title}</h3>
                  <p className="text-sm text-muted-foreground">{feature.desc}</p>
                </div>
              ))}
            </div>
          </div>
        )}

        {activeTab === "architecture" && (
          <div className="grid gap-6 animate-in slide-in-from-bottom-4 duration-500">
            <div className="p-6 rounded-2xl border bg-card shadow-sm">
              <h2 className="text-xl font-bold mb-6 flex items-center gap-2">
                <Server className="h-5 w-5 text-primary" />
                High-Level System Design
              </h2>
              
              <div className="flex flex-col md:flex-row items-center justify-center gap-4 py-8">
                {/* Client */}
                <div className="flex flex-col items-center w-full md:w-1/4 p-6 rounded-xl border-2 border-primary/20 bg-primary/5 text-center relative z-10">
                  <Smartphone className="h-10 w-10 text-primary mb-3" />
                  <h3 className="font-bold text-lg">Next.js Frontend</h3>
                  <p className="text-xs text-muted-foreground mt-2">React 19, Tailwind v4, API Centralization (constants.ts)</p>
                </div>

                {/* Arrow */}
                <div className="flex flex-col items-center text-muted-foreground">
                  <span className="text-xs font-mono mb-1">REST API (JSON)</span>
                  <div className="h-0.5 w-16 bg-muted-foreground/30 hidden md:block relative">
                    <div className="absolute right-0 top-1/2 -translate-y-1/2 w-2 h-2 border-t-2 border-r-2 border-muted-foreground/30 rotate-45"></div>
                  </div>
                  <div className="w-0.5 h-8 bg-muted-foreground/30 md:hidden relative"></div>
                </div>

                {/* Server */}
                <div className="flex flex-col items-center w-full md:w-1/3 p-6 rounded-xl border-2 border-blue-500/20 bg-blue-500/5 text-center relative z-10">
                  <Server className="h-10 w-10 text-blue-500 mb-3" />
                  <h3 className="font-bold text-lg">Spring Boot API Layer</h3>
                  <p className="text-xs text-muted-foreground mt-2">Java 21, Spring Web, Controllers, CORS enabled</p>
                  
                  <div className="w-full h-px bg-border my-4"></div>
                  
                  <div className="grid grid-cols-2 gap-2 w-full text-xs">
                    <div className="bg-background p-2 rounded border">DriveSyncService</div>
                    <div className="bg-background p-2 rounded border">OcrService</div>
                    <div className="bg-background p-2 rounded border text-primary font-bold">API_BASE_URL</div>
                    <div className="bg-background p-2 rounded border">Gemini 2.0 Parser</div>
                    <div className="bg-background p-2 rounded border text-indigo-500 font-bold underline decoration-indigo-500/30">SurveyEngine</div>
                  </div>
                </div>

                {/* Arrow */}
                <div className="flex flex-col items-center text-muted-foreground">
                  <span className="text-xs font-mono mb-1">JPA / Hibernate</span>
                  <div className="h-0.5 w-16 bg-muted-foreground/30 hidden md:block relative">
                    <div className="absolute right-0 top-1/2 -translate-y-1/2 w-2 h-2 border-t-2 border-r-2 border-muted-foreground/30 rotate-45"></div>
                  </div>
                  <div className="w-0.5 h-8 bg-muted-foreground/30 md:hidden relative"></div>
                </div>

                {/* DB */}
                <div className="flex flex-col items-center w-full md:w-1/4 p-6 rounded-xl border-2 border-amber-500/20 bg-amber-500/5 text-center relative z-10">
                  <Database className="h-10 w-10 text-amber-500 mb-3" />
                  <h3 className="font-bold text-lg">SQLite / Turso DB</h3>
                  <p className="text-xs text-muted-foreground mt-2">finsight.db | LibSQL Embedded mode, WAL Mode</p>
                </div>
              </div>
            </div>
          </div>
        )}

        {/* ... Include remaining tabs ... */}
        {activeTab === "schema" && (
           <div className="grid gap-6 animate-in slide-in-from-bottom-4 duration-500">
             <div className="p-6 rounded-2xl border bg-card shadow-sm">
                <h2 className="text-xl font-bold mb-4 flex items-center gap-2">
                  <Database className="h-5 w-5 text-primary" />
                  Embedded Database Schema (SQLite + Turso)
                </h2>
                <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
                  
                  <div className="border rounded-lg overflow-hidden border-indigo-500/30 ring-1 ring-indigo-500/20">
                    <div className="bg-indigo-500/10 p-3 border-b border-indigo-500/30 font-mono font-bold text-sm flex items-center justify-between">
                      <span className="text-indigo-600">surveys & responses (Turso)</span>
                      <span className="text-[10px] font-sans text-indigo-500 bg-background px-2 py-0.5 rounded border border-indigo-500/30 font-bold uppercase tracking-wider">Active Planning</span>
                    </div>
                    <div className="p-4 space-y-2 text-sm font-mono text-muted-foreground">
                      <div className="flex justify-between"><span>survey_id, form_url</span> <span className="text-indigo-500/70">METADATA</span></div>
                      <div className="flex justify-between"><span>responses</span> <span className="text-indigo-500/70">RAW DATA</span></div>
                      <div className="flex justify-between"><span>ai_insights</span> <span className="text-indigo-500/70">AGGREGATED</span></div>
                    </div>
                  </div>
                  
                  <div className="border rounded-lg overflow-hidden">
                    <div className="bg-muted p-3 border-b font-mono font-bold text-sm flex items-center justify-between">
                      <span>receipts</span>
                      <span className="text-xs font-sans text-muted-foreground bg-background px-2 py-0.5 rounded border">Primary Data</span>
                    </div>
                    <div className="p-4 space-y-2 text-sm font-mono text-muted-foreground">
                      <div className="flex justify-between"><span>id</span> <span className="text-primary/70">PK INTEGER</span></div>
                      <div className="flex justify-between"><span>tenant_id</span> <span>TEXT NOT NULL</span></div>
                      <div className="flex justify-between"><span>drive_file_id</span> <span>TEXT UNIQUE</span></div>
                      <div className="flex justify-between"><span>file_name, vendor</span> <span>TEXT</span></div>
                      <div className="flex justify-between"><span>amount, ocr_conf</span> <span>REAL</span></div>
                      <div className="flex justify-between"><span>content_hash</span> <span className="text-emerald-500/70">TEXT (MD5)</span></div>
                      <div className="flex justify-between"><span>status</span> <span>TEXT (PENDING|PROCESSED..)</span></div>
                    </div>
                  </div>

                  <div className="border rounded-lg overflow-hidden">
                    <div className="bg-muted p-3 border-b font-mono font-bold text-sm flex items-center justify-between">
                      <span>bank_transactions</span>
                      <span className="text-xs font-sans text-muted-foreground bg-background px-2 py-0.5 rounded border">Reconciliation Data</span>
                    </div>
                    <div className="p-4 space-y-2 text-sm font-mono text-muted-foreground">
                      <div className="flex justify-between"><span>id</span> <span className="text-primary/70">PK INTEGER</span></div>
                      <div className="flex justify-between"><span>tx_date</span> <span>DATE NOT NULL</span></div>
                      <div className="flex justify-between"><span>description</span> <span>TEXT NOT NULL</span></div>
                      <div className="flex justify-between"><span>amount</span> <span>REAL NOT NULL</span></div>
                      <div className="flex justify-between"><span>category_id</span> <span className="text-amber-500/70">FK INTEGER</span></div>
                      <div className="flex justify-between"><span>receipt_id</span> <span className="text-amber-500/70">FK INTEGER</span></div>
                    </div>
                  </div>

                  <div className="border rounded-lg overflow-hidden">
                    <div className="bg-muted p-3 border-b font-mono font-bold text-sm">audit_trail</div>
                    <div className="p-4 space-y-2 text-sm font-mono text-muted-foreground">
                      <div className="flex justify-between"><span>id</span> <span className="text-primary/70">PK INTEGER</span></div>
                      <div className="flex justify-between"><span>transaction_id</span> <span className="text-amber-500/70">FK INTEGER</span></div>
                      <div className="flex justify-between"><span>receipt_id</span> <span className="text-amber-500/70">FK INTEGER</span></div>
                      <div className="flex justify-between"><span>issue_type</span> <span>TEXT (MISMATCH)</span></div>
                      <div className="flex justify-between"><span>resolved</span> <span>INTEGER (0/1)</span></div>
                    </div>
                  </div>

                  <div className="border rounded-lg overflow-hidden">
                    <div className="bg-muted p-3 border-b font-mono font-bold text-sm flex items-center justify-between">
                      <span>app_config</span>
                      <span className="text-xs font-sans text-muted-foreground bg-background px-2 py-0.5 rounded border">Global Settings</span>
                    </div>
                    <div className="p-4 space-y-2 text-sm font-mono text-muted-foreground">
                      <div className="flex justify-between"><span>apartment_name</span> <span>TEXT</span></div>
                      <div className="flex justify-between"><span>service_account_json</span> <span className="text-blue-500">TEXT (Long)</span></div>
                      <div className="flex justify-between"><span>gemini_api_key</span> <span>TEXT</span></div>
                      <div className="flex justify-between"><span>synced_at</span> <span className="text-emerald-500">TIMESTAMP</span></div>
                      <div className="mt-2 pt-2 border-t text-[10px] uppercase tracking-tighter opacity-70">
                        Implements Priority Logic: Saved UI Settings &gt; Env Fallbacks.
                      </div>
                    </div>
                  </div>

                </div>
             </div>
           </div>
        )}

        {activeTab === "tech-stack" && (
           <div className="grid gap-6 animate-in slide-in-from-bottom-4 duration-500">
             <div className="p-6 rounded-2xl border bg-card shadow-sm">
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
                      <li className="flex justify-between items-center border p-3 rounded-lg bg-background text-sm">
                        <span className="font-medium">Framework</span> <span className="font-mono text-muted-foreground">Next.js 16 (React 19)</span>
                      </li>
                      <li className="flex justify-between items-center border p-3 rounded-lg bg-background text-sm">
                        <span className="font-medium">Language</span> <span className="font-mono text-muted-foreground">TypeScript</span>
                      </li>
                      <li className="flex justify-between items-center border p-3 rounded-lg bg-background text-sm">
                        <span className="font-medium">Styling</span> <span className="font-mono text-muted-foreground">Tailwind CSS v4</span>
                      </li>
                      <li className="flex justify-between items-center border p-3 rounded-lg bg-background text-sm">
                        <span className="font-medium">Icons & Visuals</span> <span className="font-mono text-muted-foreground">Lucide, Recharts</span>
                      </li>
                    </ul>
                  </div>

                  <div>
                    <h3 className="font-semibold text-lg border-b pb-2 mb-4 flex items-center gap-2">
                       <Cpu className="h-4 w-4" /> Backend API (Server)
                    </h3>
                    <ul className="space-y-3">
                      <li className="flex justify-between items-center border p-3 rounded-lg bg-background text-sm">
                        <span className="font-medium">Framework</span> <span className="font-mono text-muted-foreground">Spring Boot 3.4.3</span>
                      </li>
                      <li className="flex justify-between items-center border p-3 rounded-lg bg-background text-sm">
                        <span className="font-medium">Language</span> <span className="font-mono text-muted-foreground">Java 21</span>
                      </li>
                      <li className="flex justify-between items-center border p-3 rounded-lg bg-background text-sm">
                        <span className="font-medium">Build Tool</span> <span className="font-mono text-muted-foreground">Maven</span>
                      </li>
                      <li className="flex justify-between items-center border p-3 rounded-lg bg-background text-sm">
                        <span className="font-medium">Database Layer</span> <span className="font-mono text-muted-foreground">Spring Data JPA, Hibernate</span>
                      </li>
                      <li className="flex justify-between items-center border p-3 rounded-lg bg-background text-sm">
                        <span className="font-medium">Embedded DB</span> <span className="font-mono text-muted-foreground">SQLite / Turso (LibSQL)</span>
                      </li>
                    </ul>
                  </div>
                </div>
             </div>
           </div>
        )}

        {activeTab === "data-flow" && (
           <div className="grid gap-6 animate-in slide-in-from-bottom-4 duration-500">
             <div className="p-6 rounded-2xl border bg-card shadow-sm">
                <h2 className="text-xl font-bold mb-4 flex items-center gap-2">
                  <Workflow className="h-5 w-5 text-primary" />
                  Core Data Processing Pipeline
                </h2>
                <div className="space-y-6 pt-4">
                  {[
                    { step: "1", title: "Drive & Sheets Ingestion", desc: "DriveSyncService and GoogleSheetsSyncService poll Google Workspace for new expense receipts and resident survey responses." },
                    { step: "2", title: "OCR & Form Mapping", desc: "OcrService extracts receipt data, while the Survey Engine maps Sheet columns to structured Turso database responses." },
                    { step: "3", title: "Gemini 2.0 Analysis & Insights", desc: "Bank statement categorization and Resident sentiment/recommendation generation via Gemini 2.0 Flash (latest)." },
                    { step: "4", title: "Financial Reconciliation", desc: "ReconciliationService correlates transactions against receipts with automatic tie-ins and MD5-based duplicate detection." }
                  ].map((pipe, i) => (
                    <div key={i} className="flex gap-4 p-4 border rounded-xl bg-background relative overflow-hidden group hover:border-primary/50 transition-colors">
                      <div className="absolute left-0 top-0 bottom-0 w-1 bg-primary/20 group-hover:bg-primary transition-colors"></div>
                      <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-primary text-primary-foreground font-bold font-mono shadow-md">
                        {pipe.step}
                      </div>
                      <div>
                        <h3 className="font-bold mb-1 group-hover:text-primary transition-colors">{pipe.title}</h3>
                        <p className="text-muted-foreground text-sm leading-relaxed">{pipe.desc}</p>
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
                <div className="p-6 rounded-2xl border bg-card shadow-sm relative overflow-hidden">
                  <div className="absolute right-0 top-0 w-32 h-32 bg-emerald-500/5 rounded-full blur-3xl"></div>
                  <HardDrive className="h-8 w-8 text-emerald-500 mb-4 relative z-10" />
                  <h3 className="text-lg font-bold mb-2">Google Drive API v3</h3>
                  <p className="text-sm text-muted-foreground mb-4">Leveraged for a continuous ingestion pipeline. Features **Robust Regex Validation** for Service Account JSONs to handle varied formatting and ensure configuration reliability.</p>
                  <div className="text-xs font-mono bg-muted p-2 rounded">com.google.apis:google-api-services-drive</div>
                </div>

                <div className="p-6 rounded-2xl border bg-card shadow-sm relative overflow-hidden">
                  <div className="absolute right-0 top-0 w-32 h-32 bg-blue-500/5 rounded-full blur-3xl"></div>
                  <Code2 className="h-8 w-8 text-blue-500 mb-4 relative z-10" />
                  <h3 className="text-lg font-bold mb-2">Google Gemini 2.0 Flash</h3>
                  <p className="text-sm text-muted-foreground mb-4">Employed for unstructured data classification and robust receipt parsing. Converts malformed response chunks into structured financial records via a Java-side Regex fallback engine.</p>
                  <div className="text-xs font-mono bg-muted p-2 rounded">API Model: gemini-2.0-flash</div>
                </div>
             </div>
           </div>
        )}

      </div>
    </div>
  );
}
