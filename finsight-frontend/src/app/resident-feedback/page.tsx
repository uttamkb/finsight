"use client";

import React, { useState, useEffect } from "react";
import { apiFetch } from "@/lib/api";
import {
  Radar, RadarChart, PolarGrid, PolarAngleAxis, PolarRadiusAxis,
  ResponsiveContainer, Tooltip,
} from "recharts";
import {
  RefreshCw, Star, MessageSquare, AlertCircle, CheckCircle2,
  Zap, ShieldCheck, Link2, HardDrive, Settings,
} from "lucide-react";

// ─── Types ────────────────────────────────────────────────────────────────────

interface Survey {
  id: number;
  formUrl: string;
  label: string;
  status: string;
}

interface McActionItem {
  id: number;
  priority: "HIGH" | "MEDIUM" | "LOW";
  facility: string;
  action: string;
  timeline: string;
  expectedOutcome: string;
  status: "TODO" | "IN_PROGRESS" | "DONE" | "NOT_FEASIBLE";
}

interface DashboardData {
  totalResponses: number;
  averageRatings: Record<string, number>;
  mcActionPlan: McActionItem[];
  label?: string;
  formUrl?: string;
}

// ─── Helpers ──────────────────────────────────────────────────────────────────

const priorityStyle: Record<string, string> = {
  HIGH:   "bg-error/10 text-error border-error/20",
  MEDIUM: "bg-warning/10 text-warning border-warning/20",
  LOW:    "bg-success/10 text-success border-success/20",
};

const statusStyle: Record<string, string> = {
  TODO:        "bg-base-content/5 text-base-content/40 border-base-content/10",
  IN_PROGRESS: "bg-info/10 text-info border-info/20",
  DONE:        "bg-success/10 text-success border-success/20",
  NOT_FEASIBLE: "bg-error/10 text-error border-error/20",
};

// ─── Main Component ───────────────────────────────────────────────────────────

export default function ResidentFeedbackDashboard() {
  const [activeTab, setActiveTab] = useState<"insights" | "connect" | "config">("insights");
  const [survey, setSurvey] = useState<Survey | null>(null);
  const [data, setData] = useState<DashboardData | null>(null);
  const [loading, setLoading] = useState(true);
  const [syncing, setSyncing] = useState(false);
  const [syncMsg, setSyncMsg] = useState<string | null>(null);

  // Connect form state
  const [formId, setFormId] = useState("");
  const [label, setLabel]   = useState("");
  const [connecting, setConnecting] = useState(false);
  const [connectError, setConnectError] = useState<string | null>(null);
  const [connectSuccess, setConnectSuccess] = useState<string | null>(null);

  useEffect(() => { loadSurvey(); }, []);

  const loadSurvey = async () => {
    setLoading(true);
    try {
      const res = await apiFetch("/survey/current?tenantId=local_tenant");
      const s = await res.json();
      if (s?.id) {
        setSurvey(s);
        await loadDashboard(s.id);
      } else {
        setSurvey(null);
      }
    } catch { setSurvey(null); }
    finally { setLoading(false); }
  };

  const loadDashboard = async (surveyId: number) => {
    const res = await apiFetch(`/survey/dashboard?surveyId=${surveyId}`);
    const d = await res.json();
    setData(d);
  };

  const handleConnect = async (e: React.FormEvent) => {
    e.preventDefault();
    setConnecting(true);
    setConnectError(null);
    setConnectSuccess(null);
    try {
      const params = new URLSearchParams({ tenantId: "local_tenant", formId });
      if (label) params.set("label", label);
      const res = await apiFetch(`/survey/connect?${params}`, { method: "POST" });
      if (res.ok) {
        const newSurvey = await res.json();
        setSurvey(newSurvey);
        await loadDashboard(newSurvey.id);
        setConnectSuccess("Google Form connected successfully! Click 'Sync & Analyse' to generate the MC Action Plan.");
        setActiveTab("insights");
        setFormId("");
        setLabel("");
      } else {
        const txt = await res.text();
        let msg = "Connection failed.";
        try { msg = JSON.parse(txt).message || msg; } catch { msg = txt || msg; }
        setConnectError(msg);
      }
    } catch { setConnectError("Network error — is the backend running?"); }
    finally { setConnecting(false); }
  };

  const syncNow = async () => {
    if (!survey) return;
    setSyncing(true);
    setSyncMsg(null);
    try {
      const res = await apiFetch(`/survey/sync?surveyId=${survey.id}`, { method: "POST" });
      const result = await res.json();
      await loadDashboard(survey.id);
      setSyncMsg(result.message || "Sync complete.");
    } catch { setSyncMsg("Sync failed."); }
    finally { setSyncing(false); }
  };

  const updateActionStatus = async (itemId: number, newStatus: string) => {
    try {
      const res = await apiFetch(`/survey/action-items/${itemId}?status=${newStatus}`, {
        method: "PATCH",
      });
      if (res.ok) {
        // Optimistic UI or just reload
        if (survey) loadDashboard(survey.id);
      }
    } catch (err) {
      console.error("Failed to update status", err);
    }
  };

  // ── Radar data ──
  const radarData = data
    ? Object.entries(data.averageRatings).map(([subject, value]) => ({
        subject: subject.split(" ").slice(0, 2).join(" "),
        fullSubject: subject,
        A: value,
        fullMark: 5,
      }))
    : [];

  const avgRating = radarData.length
    ? (radarData.reduce((acc, cur) => acc + cur.A, 0) / radarData.length).toFixed(1)
    : "–";

  if (loading) return (
    <div className="flex flex-col items-center justify-center min-h-[60vh]">
      <RefreshCw className="h-10 w-10 animate-spin text-primary mb-4" />
      <p className="text-base-content/60 animate-pulse">Loading Feedback Hub…</p>
    </div>
  );

  return (
    <div className="container mx-auto py-10 px-4 max-w-7xl animate-fade-in">

      {/* ── Header ── */}
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-8 mb-12">
        <div className="flex items-center gap-6">
          <div className="p-4 bg-primary/10 rounded-3xl">
            <MessageSquare className="h-12 w-12 text-primary" />
          </div>
          <div>
            <h1 className="text-4xl font-black tracking-tight">Resident Feedback Hub</h1>
            <p className="text-base-content/40 font-bold uppercase tracking-[0.2em] text-[10px] mt-1 flex items-center gap-2">
              <Zap className="h-3 w-3 text-primary animate-pulse" /> Gemini AI · MC Action Plan Generator
            </p>
          </div>
        </div>

        {/* ── Tabs ── */}
        <div className="flex p-1.5 bg-base-300/50 backdrop-blur-xl rounded-[2rem] border border-base-content/5 shadow-inner">
          {([
            { id: "insights", label: "Dashboard",     icon: Star     },
            { id: "connect",  label: "Connect Form",  icon: Link2    },
            { id: "config",   label: "Configuration", icon: Settings },
          ] as const).map((tab) => (
            <button
              key={tab.id}
              onClick={() => setActiveTab(tab.id)}
              className={`flex items-center gap-3 px-6 py-3 rounded-[1.5rem] text-xs font-black uppercase tracking-widest transition-all ${
                activeTab === tab.id
                  ? "bg-base-100 shadow-xl text-primary"
                  : "text-base-content/40 hover:text-base-content hover:bg-base-100/30"
              }`}
            >
              <tab.icon className="h-4 w-4" />
              {tab.label}
            </button>
          ))}
        </div>
      </div>

      {/* ══════════════════════════════════════════════════════════════════════
          TAB: DASHBOARD
      ══════════════════════════════════════════════════════════════════════ */}
      {activeTab === "insights" && (
        <div className="animate-in fade-in slide-in-from-bottom-4 duration-500">

          {connectSuccess && (
            <div className="mb-6 p-4 rounded-2xl bg-success/10 border border-success/20 flex gap-3 items-center">
              <CheckCircle2 className="h-5 w-5 text-success shrink-0" />
              <p className="text-sm font-bold text-success">{connectSuccess}</p>
            </div>
          )}

          {!survey ? (
            <div className="text-center py-20 border-2 border-dashed rounded-3xl bg-base-300/20">
              <AlertCircle className="h-12 w-12 text-base-content/40 mx-auto mb-4" />
              <h2 className="text-2xl font-bold">No Connected Survey</h2>
              <p className="text-base-content/60 mt-2 mb-6">
                Go to the <strong>Connect Form</strong> tab to link your Google Form.
              </p>
              <button
                onClick={() => setActiveTab("connect")}
                className="px-6 py-2.5 bg-primary text-primary-content rounded-xl font-bold shadow-lg shadow-primary/20"
              >
                Connect a Google Form
              </button>
            </div>
          ) : (
            <>
              {/* Survey header bar */}
              <div className="flex flex-col md:flex-row justify-between items-start md:items-center mb-10 glass-panel p-8 rounded-[2.5rem] shadow-xl border-primary/10 gap-6">
                <div>
                  <h2 className="text-2xl font-black uppercase tracking-tighter">
                    {survey.label || "Resident Survey"}
                  </h2>
                  <a href={survey.formUrl} target="_blank" rel="noopener noreferrer"
                    className="text-xs text-primary underline mt-1 block">
                    View Google Form ↗
                  </a>
                </div>
                <div className="flex flex-col gap-3 items-end">
                  <div className="flex gap-4">
                    <button
                      onClick={() => setActiveTab("connect")}
                      className="btn btn-ghost h-12 px-6 rounded-2xl font-black uppercase text-[10px] tracking-widest border border-base-content/10 flex items-center gap-2 hover:bg-base-content/5"
                    >
                      <Link2 className="h-4 w-4" />
                      Connect Different Survey
                    </button>
                    <button
                      onClick={syncNow}
                      disabled={syncing}
                      className="btn btn-primary h-12 px-8 rounded-2xl font-black uppercase text-[10px] tracking-widest shadow-xl shadow-primary/20 flex items-center gap-2"
                    >
                      <RefreshCw className={`h-4 w-4 ${syncing ? "animate-spin" : ""}`} />
                      {syncing ? "Syncing…" : "Sync & Analyse"}
                    </button>
                  </div>
                  {syncMsg && (
                    <p className="text-xs text-success font-bold animate-pulse">{syncMsg}</p>
                  )}
                </div>
              </div>

              {/* Stats */}
              <div className="grid grid-cols-1 md:grid-cols-3 gap-8 mb-12">
                <StatCard icon={MessageSquare} value={data?.totalResponses ?? 0}
                  label="Responses Collected" colorClass="text-primary" bgClass="bg-primary/10" />
                <StatCard icon={Star} value={avgRating}
                  label="Avg Satisfaction Rating" colorClass="text-success" bgClass="bg-success/10" />
                <StatCard icon={ShieldCheck}
                  value={data?.mcActionPlan?.length ?? 0}
                  label="MC Action Items" colorClass="text-warning" bgClass="bg-warning/10" />
              </div>

              {/* Radar + Action Plan */}
              <div className="grid grid-cols-1 lg:grid-cols-2 gap-12">

                {/* Radar chart */}
                <div className="glass-panel p-10 rounded-[3rem] shadow-2xl relative overflow-hidden">
                  <h2 className="text-2xl font-black mb-10 uppercase tracking-tight">
                    Satisfaction Radar
                  </h2>
                  <div className="h-[400px] w-full">
                    <ResponsiveContainer width="100%" height="100%">
                      <RadarChart cx="50%" cy="50%" outerRadius="75%" data={radarData}>
                        <PolarGrid stroke="rgba(255,255,255,0.05)" />
                        <PolarAngleAxis dataKey="subject"
                          tick={{ fill: "currentColor", fontSize: 10, fontWeight: 900 }} />
                        <PolarRadiusAxis angle={30} domain={[0, 5]}
                          tick={{ fill: "currentColor", fontSize: 10, opacity: 0.3 }} />
                        <Radar name="Satisfaction" dataKey="A"
                          stroke="var(--color-primary)" fill="var(--color-primary)" fillOpacity={0.4} />
                        <Tooltip
                          contentStyle={{
                            backgroundColor: "rgba(0,0,0,0.8)", borderRadius: "16px",
                            border: "1px solid rgba(255,255,255,0.1)", backdropFilter: "blur(10px)"
                          }}
                          formatter={(v: any) => [`${Number(v).toFixed(1)} / 5`, "Avg Rating"]}
                        />
                      </RadarChart>
                    </ResponsiveContainer>
                  </div>
                </div>

                {/* MC Action Plan */}
                <div className="glass-panel p-10 rounded-[3rem] shadow-2xl overflow-hidden">
                  <div className="flex items-center justify-between mb-8 pb-6 border-b border-base-content/5">
                    <h2 className="text-2xl font-black flex items-center gap-3 uppercase tracking-tight">
                      <CheckCircle2 className="h-7 w-7 text-primary" /> MC Action Plan
                    </h2>
                    <span className="text-[10px] font-black bg-primary/10 text-primary px-4 py-2 rounded-full uppercase tracking-widest border border-primary/10">
                      Gemini AI
                    </span>
                  </div>

                  {(!data?.mcActionPlan || data.mcActionPlan.length === 0) ? (
                    <div className="text-center py-12 text-base-content/40">
                      <AlertCircle className="h-8 w-8 mx-auto mb-3" />
                      <p className="text-sm font-bold">No Action Plan yet.</p>
                      <p className="text-xs mt-1">Click <strong>Sync & Analyse</strong> to generate.</p>
                    </div>
                  ) : (
                    <div className="space-y-4 max-h-[550px] overflow-y-auto pr-2 custom-scrollbar">
                      {data.mcActionPlan.map((item, idx) => (
                        <div key={idx}
                          className={`p-6 rounded-3xl border transition-all ${
                            item.status === 'DONE' ? 'bg-success/5 border-success/20 opacity-70' : 'bg-base-100/30 border-base-content/5 hover:border-primary/20'
                          }`}>
                          <div className="flex items-start justify-between gap-4 mb-4">
                            <div>
                              <h3 className={`font-black text-sm tracking-tight ${item.status === 'DONE' ? 'line-through' : ''}`}>
                                {item.facility}
                              </h3>
                              <div className="flex gap-2 mt-2">
                                <span className={`text-[9px] font-black px-2 py-0.5 rounded-md border uppercase ${priorityStyle[item.priority]}`}>
                                  {item.priority}
                                </span>
                                <span className={`text-[9px] font-black px-2 py-0.5 rounded-md border uppercase ${statusStyle[item.status]}`}>
                                  {item.status.replace('_', ' ')}
                                </span>
                              </div>
                            </div>
                            
                            {/* Status Toggle Actions */}
                            <div className="flex gap-1">
                              {item.status !== 'DONE' && (
                                <button 
                                  onClick={() => updateActionStatus(item.id, 'DONE')}
                                  className="p-2 hover:bg-success/20 rounded-xl text-success transition-colors"
                                  title="Mark as Done"
                                >
                                  <CheckCircle2 className="h-4 w-4" />
                                </button>
                              )}
                              {item.status === 'TODO' && (
                                <button 
                                  onClick={() => updateActionStatus(item.id, 'IN_PROGRESS')}
                                  className="p-2 hover:bg-info/20 rounded-xl text-info transition-colors"
                                  title="Start Working"
                                >
                                  <RefreshCw className="h-4 w-4" />
                                </button>
                              )}
                              {item.status !== 'NOT_FEASIBLE' && item.status !== 'DONE' && (
                                <button 
                                  onClick={() => updateActionStatus(item.id, 'NOT_FEASIBLE')}
                                  className="p-2 hover:bg-error/20 rounded-xl text-error transition-colors"
                                  title="Mark Not Feasible"
                                >
                                  <AlertCircle className="h-4 w-4" />
                                </button>
                              )}
                              {item.status !== 'TODO' && (
                                <button 
                                  onClick={() => updateActionStatus(item.id, 'TODO')}
                                  className="p-2 hover:bg-base-content/10 rounded-xl text-base-content/40 transition-colors"
                                  title="Reset to Todo"
                                >
                                  <RefreshCw className="h-4 w-4 rotate-180" />
                                </button>
                              )}
                            </div>
                          </div>
                          
                          <p className={`text-sm leading-relaxed mb-4 ${item.status === 'DONE' ? 'text-base-content/40' : 'text-base-content/70'}`}>
                            {item.action}
                          </p>
                          
                          <div className="flex flex-wrap gap-4 text-[10px] text-base-content/40 font-bold border-t border-base-content/5 pt-4">
                            <span className="flex items-center gap-1.5"><RefreshCw className="h-3 w-3" /> {item.timeline}</span>
                            <span className="flex-1 flex items-center gap-1.5"><ShieldCheck className="h-3 w-3" /> {item.expectedOutcome}</span>
                          </div>
                        </div>
                      ))}
                    </div>
                  )}
                </div>
              </div>
            </>
          )}
        </div>
      )}

      {/* ══════════════════════════════════════════════════════════════════════
          TAB: CONNECT FORM
      ══════════════════════════════════════════════════════════════════════ */}
      {activeTab === "connect" && (
        <div className="max-w-2xl mx-auto animate-in slide-in-from-bottom-4 duration-500">
          <div className="p-8 rounded-3xl border bg-base-200 shadow-xl">
            <h2 className="text-2xl font-bold mb-2 flex items-center gap-2">
              <Link2 className="h-6 w-6 text-primary" /> Connect Google Form
            </h2>
            <p className="text-base-content/60 mb-8 text-sm leading-relaxed">
              Create your survey on <strong>Google Forms</strong> and paste the Form ID here.
              The platform will fetch responses and generate an AI-powered MC Action Plan.
            </p>

            <form onSubmit={handleConnect} className="space-y-5">
              <div className="space-y-2">
                <label className="text-sm font-bold">Google Form ID <span className="text-error">*</span></label>
                <input
                  id="form-id-input"
                  type="text"
                  required
                  value={formId}
                  onChange={(e) => setFormId(e.target.value.trim())}
                  placeholder="e.g. 1BxiMVs0XRA5nFMdKvBdBZjgmUUqptlbs74OgVE2upms"
                  className="w-full p-3 rounded-xl bg-base-300 border-none focus:ring-2 ring-primary/20 transition-all font-mono text-sm"
                />
                <p className="text-[11px] text-base-content/40">
                  Find in the form URL: <code>docs.google.com/forms/d/<strong>[FORM_ID]</strong>/edit</code>
                </p>
              </div>

              <div className="space-y-2">
                <label className="text-sm font-bold">Survey Label <span className="text-base-content/40">(optional)</span></label>
                <input
                  id="survey-label-input"
                  type="text"
                  value={label}
                  onChange={(e) => setLabel(e.target.value)}
                  placeholder="e.g. Q1 2026 Resident Feedback"
                  className="w-full p-3 rounded-xl bg-base-300 border-none focus:ring-2 ring-primary/20 transition-all font-medium"
                />
              </div>

              <div className="p-5 rounded-2xl bg-warning/5 border border-amber-500/10 flex gap-4">
                <ShieldCheck className="h-5 w-5 text-warning shrink-0 mt-0.5" />
                <p className="text-xs text-amber-700/80 leading-relaxed">
                  Ensure the <strong>Service Account</strong> (configured in Settings → Connectivity) has
                  <strong> View access</strong> to the Google Form before connecting.
                </p>
              </div>

              {connectError && (
                <div className="p-4 rounded-2xl bg-error/10 border border-error/20 flex gap-3">
                  <AlertCircle className="h-5 w-5 text-error shrink-0" />
                  <div>
                    <p className="text-sm font-bold text-error">Connection Failed</p>
                    <p className="text-xs text-error/70 mt-1">{connectError}</p>
                  </div>
                </div>
              )}

              <button
                type="submit"
                disabled={connecting}
                className="w-full py-4 bg-primary text-primary-content rounded-xl font-bold shadow-lg shadow-primary/20 hover:opacity-90 transition-all flex items-center justify-center gap-2"
              >
                {connecting
                  ? <><RefreshCw className="h-5 w-5 animate-spin" /> Connecting…</>
                  : <><Link2 className="h-5 w-5" /> Connect &amp; Activate Survey</>}
              </button>
            </form>

            {survey && (
              <div className="mt-6 p-4 rounded-2xl bg-success/10 border border-success/20">
                <p className="text-sm font-bold text-success flex items-center gap-2">
                  <CheckCircle2 className="h-4 w-4" /> Currently connected: {survey.label || "Active Survey"}
                </p>
                <a href={survey.formUrl} target="_blank" rel="noopener noreferrer"
                  className="text-xs text-primary underline mt-1 block">View live form ↗</a>
              </div>
            )}
          </div>
        </div>
      )}

      {/* ══════════════════════════════════════════════════════════════════════
          TAB: CONFIGURATION
      ══════════════════════════════════════════════════════════════════════ */}
      {activeTab === "config" && (
        <div className="max-w-2xl mx-auto animate-in slide-in-from-bottom-4 duration-500 space-y-6">
          <div className="p-8 rounded-3xl border bg-base-200 shadow-xl">
            <h2 className="text-2xl font-bold mb-6 flex items-center gap-2">
              <Settings className="h-6 w-6 text-primary" /> System Configuration
            </h2>
            <div className="space-y-4">
              {[
                { icon: HardDrive, label: "Google Forms API (Response Ingestion)", status: "CONNECTED",  color: "success" },
                { icon: Zap,       label: "Gemini AI — MC Action Plan",            status: "ENABLED",    color: "success" },
                { icon: RefreshCw, label: "Auto Sync Scheduler",                   status: "HOURLY",     color: "success" },
              ].map((item, i) => (
                <div key={i} className="flex items-center justify-between p-4 rounded-2xl bg-base-300/50 border border-base-content/5">
                  <div className="flex items-center gap-3">
                    <item.icon className="h-5 w-5 text-primary" />
                    <span className="text-sm font-bold">{item.label}</span>
                  </div>
                  <span className={`text-xs font-black px-3 py-1 rounded-full bg-${item.color}/10 text-${item.color} border border-${item.color}/20`}>
                    {item.status}
                  </span>
                </div>
              ))}
            </div>
            <div className="mt-6 p-5 rounded-2xl bg-warning/5 border border-amber-500/10">
              <p className="text-xs text-amber-700/80 leading-relaxed">
                <strong>Service Account JSON</strong> and <strong>Gemini API Key</strong> must be configured in{" "}
                <a href="/settings" className="text-primary underline font-bold">Settings → Connectivity</a>.
              </p>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

// ─── Sub-Components ───────────────────────────────────────────────────────────

function StatCard({ icon: Icon, value, label, colorClass, bgClass }: {
  icon: React.ElementType; value: number | string;
  label: string; colorClass: string; bgClass: string;
}) {
  return (
    <div className="glass-panel p-8 rounded-[2rem] shadow-lg flex items-center gap-6 group hover:glow-primary transition-all">
      <div className={`h-16 w-16 rounded-2xl ${bgClass} flex items-center justify-center ${colorClass} shadow-inner`}>
        <Icon className="h-8 w-8" />
      </div>
      <div>
        <div className="text-4xl font-black font-mono tracking-tighter">{value}</div>
        <div className="text-[10px] font-black uppercase tracking-[0.2em] text-base-content/30 mt-1">{label}</div>
      </div>
    </div>
  );
}
