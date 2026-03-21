"use client";

import React, { useState, useEffect } from "react";
import { apiFetch } from "@/lib/api";
import { 
  Radar, RadarChart, PolarGrid, PolarAngleAxis, PolarRadiusAxis, 
  ResponsiveContainer, LineChart, Line, XAxis, YAxis, Tooltip, CartesianGrid 
} from "recharts";
import { 
  Clipboard, QrCode, RefreshCw, Star, 
  TrendingUp, MessageSquare, AlertCircle, CheckCircle2,
  HardDrive, Zap, ShieldCheck, HeartPulse, Code2, Layers
} from "lucide-react";

interface Survey {
  id: number;
  formUrl: string;
  quarter: string;
  year: number;
  status: string;
}

interface Insight {
  id: number;
  facility: string;
  sentimentScore: number;
  aiSummary: string;
  recommendations: string;
}

interface DashboardData {
  totalResponses: number;
  averageRatings: Record<string, number>;
  insights: Insight[];
  executiveSummary?: string;
}

export default function ResidentFeedbackDashboard() {
  const [activeTab, setActiveTab] = useState("insights");
  const [survey, setSurvey] = useState<Survey | null>(null);
  const [data, setData] = useState<DashboardData | null>(null);
  const [loading, setLoading] = useState(true);
  const [syncing, setSyncing] = useState(false);
  
  // Create Survey Form State
  const [quarter, setQuarter] = useState("Q1");
  const [year, setYear] = useState(new Date().getFullYear());
  const [creating, setCreating] = useState(false);

  // General Feedback Form State
  const [feedbackType, setFeedbackType] = useState("GENERAL");
  const [feedbackMsg, setFeedbackMsg] = useState("");
  const [userEmail, setUserEmail] = useState("");
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    fetchSurvey();
  }, []);

  const fetchSurvey = async () => {
    try {
      const res = await apiFetch("/survey/current?tenantId=local_tenant");
      const currentSurvey = await res.json();
      if (currentSurvey && currentSurvey.id) {
        setSurvey(currentSurvey);
        fetchDashboard(currentSurvey.id);
      } else {
        setSurvey(null);
        setLoading(false);
      }
    } catch (err) {
      console.error("Failed to fetch survey:", err);
      setSurvey(null);
      setLoading(false);
    }
  };

  const fetchDashboard = async (surveyId: number) => {
    try {
      const res = await apiFetch(`/survey/dashboard?surveyId=${surveyId}`);
      const dashboardData = await res.json();
      setData(dashboardData);
    } catch (err) {
      console.error("Failed to fetch dashboard:", err);
    } finally {
      setLoading(false);
    }
  };

  const handleCreateSurvey = async (e: React.FormEvent) => {
    e.preventDefault();
    setCreating(true);
    try {
      const res = await apiFetch(`/survey/create?tenantId=local_tenant&quarter=${quarter}&year=${year}`, {
        method: "POST"
      });
      if (res.ok) {
        const newSurvey = await res.json();
        setSurvey(newSurvey);
        fetchDashboard(newSurvey.id);
        setActiveTab("insights");
        alert("Survey created successfully and connected to Google Forms!");
      }
    } catch (err) {
      console.error("Survey creation failed:", err);
    } finally {
      setCreating(false);
    }
  };

  const handleSubmitFeedback = async (e: React.FormEvent) => {
    e.preventDefault();
    setSubmitting(true);
    try {
      const res = await apiFetch("/feedback/submit", {
        method: "POST",
        body: JSON.stringify({
          type: feedbackType,
          message: feedbackMsg,
          userEmail: userEmail,
          tenantId: "local_tenant"
        })
      });
      if (res.ok) {
        setFeedbackMsg("");
        setUserEmail("");
        alert("Feedback submitted. Thank you for helping us improve!");
      }
    } catch (err) {
      console.error("Feedback submission failed:", err);
    } finally {
      setSubmitting(false);
    }
  };

  const syncNow = async () => {
    if (!survey) return;
    setSyncing(true);
    try {
      await apiFetch(`/survey/sync?surveyId=${survey.id}`, { method: "POST" });
      await fetchDashboard(survey.id);
    } catch (err) {
      console.error("Sync failed:", err);
    } finally {
      setSyncing(false);
    }
  };

  const copyLink = () => {
    if (survey?.formUrl) {
      navigator.clipboard.writeText(survey.formUrl);
      alert("Survey link copied to clipboard!");
    }
  };

  const handlePrint = () => {
    window.print();
  };

  if (loading) return (
    <div className="flex flex-col items-center justify-center min-h-[60vh]">
      <RefreshCw className="h-10 w-10 animate-spin text-primary mb-4" />
      <p className="text-base-content/60 animate-pulse">Initializing Financial Watchdog Dashboard...</p>
    </div>
  );

  // Format data for Radar Chart
  const radarData = data ? Object.entries(data.averageRatings).map(([subject, value]) => ({
    subject: subject.split(' ').slice(0, 2).join(' '), // Shorten labels
    fullSubject: subject,
    A: value,
    fullMark: 5,
  })) : [];

  return (
    <div className="container mx-auto py-10 px-4 max-w-7xl print:bg-base-100 print:p-0">
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-6 mb-8 print:hidden">
        <div>
          <div className="flex items-center gap-2 text-primary font-bold mb-2">
            <Zap className="h-5 w-5" />
            <span>AI-POWERED INTELLIGENCE</span>
          </div>
          <h1 className="text-4xl font-extrabold tracking-tight">Resident Feedback Hub</h1>
          <p className="text-base-content/60 mt-1">Management, Insights & Community Satisfaction</p>
        </div>

        <div className="flex p-1 bg-base-300 rounded-xl gap-1">
          {[
            { id: "insights", label: "Insights", icon: TrendingUp },
            { id: "management", label: "Management", icon: Clipboard },
            { id: "bug", label: "Report Issue", icon: AlertCircle },
          ].map((tab) => (
            <button
              key={tab.id}
              onClick={() => setActiveTab(tab.id)}
              className={`flex items-center gap-2 px-4 py-2 rounded-lg text-sm font-medium transition-all ${
                activeTab === tab.id ? "bg-base-100 shadow-sm text-base-content" : "text-base-content/60 hover:text-base-content"
              }`}
            >
              <tab.icon className="h-4 w-4" />
              {tab.label}
            </button>
          ))}
        </div>
      </div>

      {/* Print Header (Only visible when printing) */}
      <div className="hidden print:block mb-8 border-b pb-4">
        <h1 className="text-3xl font-bold">Resident Satisfaction Report</h1>
        <p className="text-base-content/60">{survey?.quarter} {survey?.year} - FinSight Operational Audit</p>
      </div>

      {activeTab === "insights" && (
        <div className="animate-in fade-in slide-in-from-bottom-4 duration-500">
          {!survey ? (
             <div className="text-center py-20 border-2 border-dashed rounded-3xl bg-base-300/20 print:hidden">
                <AlertCircle className="h-12 w-12 text-base-content/60 mx-auto mb-4" />
                <h2 className="text-2xl font-bold">No Active Survey</h2>
                <p className="text-base-content/60 mt-2 mb-6">Create a new survey in the Management tab to start collecting feedback.</p>
                <button 
                  onClick={() => setActiveTab("management")}
                  className="px-6 py-2.5 bg-primary text-primary-content rounded-xl font-bold shadow-lg shadow-primary/20"
                >
                  Create Survey Now
                </button>
             </div>
          ) : (
            <>
              <div className="flex justify-between items-center mb-10 bg-primary/5 p-6 rounded-2xl border border-primary/10 print:bg-base-100 print:border-none print:px-0">
                <div>
                   <h2 className="text-xl font-bold">Audit Period: {survey.quarter} {survey.year}</h2>
                   <p className="text-sm text-base-content/60">Generated by Gemini AI on {new Date().toLocaleDateString()}</p>
                </div>
                <div className="flex gap-3 print:hidden">
                  <button onClick={syncNow} disabled={syncing} className="flex items-center gap-2 px-4 py-2 bg-secondary rounded-xl font-medium">
                    <RefreshCw className={`h-4 w-4 ${syncing ? "animate-spin" : ""}`} />
                    Sync
                  </button>
                  <button onClick={handlePrint} className="flex items-center gap-2 px-4 py-2 bg-primary text-primary-content rounded-xl font-medium shadow-lg shadow-primary/20">
                    <HardDrive className="h-4 w-4" />
                    Save Report
                  </button>
                </div>
              </div>

              {/* New Executive Summary Section */}
              {data?.executiveSummary && (
                <div className="mb-10 p-8 rounded-3xl border-2 border-primary/30 bg-primary/5 relative overflow-hidden">
                  <div className="absolute top-0 right-0 p-8 opacity-10"><ShieldCheck className="h-24 w-24" /></div>
                  <h2 className="text-2xl font-bold mb-4 flex items-center gap-2">
                    <Star className="h-6 w-6 text-primary fill-primary" /> Executive Board Summary
                  </h2>
                  <div className="prose prose-sm max-w-none text-base-content leading-relaxed">
                    {data.executiveSummary.split('\n').map((para: string, i: number) => (
                      <p key={i} className="mb-4">{para}</p>
                    ))}
                  </div>
                </div>
              )}

              <div className="grid grid-cols-1 md:grid-cols-3 gap-6 mb-10 print:grid-cols-3">
                <div className="p-6 rounded-2xl border bg-base-200/50 shadow-sm flex items-center gap-4">
                  <div className="h-12 w-12 rounded-full bg-info/10 flex items-center justify-center text-info">
                    <MessageSquare className="h-6 w-6" />
                  </div>
                  <div>
                    <div className="text-2xl font-bold">{data?.totalResponses || 0}</div>
                    <div className="text-sm text-base-content/60">Total Responses</div>
                  </div>
                </div>
                <div className="p-6 rounded-2xl border bg-base-200/50 shadow-sm flex items-center gap-4">
                  <div className="h-12 w-12 rounded-full bg-success/10 flex items-center justify-center text-success">
                    <Star className="h-6 w-6" />
                  </div>
                  <div>
                    <div className="text-2xl font-bold">
                      {radarData.length > 0 ? (radarData.reduce((acc: number, curr: any) => acc + curr.A, 0) / radarData.length).toFixed(1) : "0.0"}
                    </div>
                    <div className="text-sm text-base-content/60">Satisfaction Score</div>
                  </div>
                </div>
                <div className="p-6 rounded-2xl border bg-base-200/50 shadow-sm flex items-center gap-4">
                  <div className="h-12 w-12 rounded-full bg-warning/10 flex items-center justify-center text-warning">
                    <TrendingUp className="h-6 w-6" />
                  </div>
                  <div>
                    <div className="text-2xl font-bold">AI Audited</div>
                    <div className="text-sm text-base-content/60">Verification Status</div>
                  </div>
                </div>
              </div>

              <div className="grid grid-cols-1 lg:grid-cols-2 gap-8 print:block">
                <div className="p-8 rounded-3xl border bg-base-200 shadow-lg relative overflow-hidden print:mb-8 print:shadow-none">
                  <div className="absolute top-0 right-0 p-8 opacity-5"><HeartPulse className="h-32 w-32" /></div>
                  <h2 className="text-xl font-bold mb-6 flex items-center gap-2">Facility Satisfaction Matrix</h2>
                  <div className="h-[400px] w-full">
                    <ResponsiveContainer width="100%" height="100%">
                      <RadarChart cx="50%" cy="50%" outerRadius="80%" data={radarData}>
                        <PolarGrid stroke="hsl(var(--muted-foreground))" strokeOpacity={0.1} />
                        <PolarAngleAxis dataKey="subject" tick={{ fill: "hsl(var(--muted-foreground))", fontSize: 12 }} />
                        <PolarRadiusAxis angle={30} domain={[0, 5]} tick={{ fill: "hsl(var(--muted-foreground))", fontSize: 10 }} />
                        <Radar name="Satisfaction" dataKey="A" stroke="hsl(var(--primary))" fill="hsl(var(--primary))" fillOpacity={0.6} />
                        <Tooltip contentStyle={{ backgroundColor: "hsl(var(--card))", borderRadius: "12px", border: "1px solid hsl(var(--border))" }} />
                      </RadarChart>
                    </ResponsiveContainer>
                  </div>
                </div>

                <div className="p-6 rounded-3xl border bg-primary/5 border-primary/10 shadow-lg relative h-full print:shadow-none print:border-indigo-100">
                  <div className="flex items-center justify-between mb-6">
                    <h2 className="text-xl font-bold flex items-center gap-2">
                       <CheckCircle2 className="h-5 w-5 text-primary" /> Operational Action Items
                    </h2>
                    <span className="text-[10px] font-bold bg-primary/20 text-indigo-600 px-2 py-1 rounded-full uppercase print:hidden">Gemini Insight Engine</span>
                  </div>
                  <div className="space-y-4 max-h-[600px] overflow-y-auto pr-2 scrollbar-hide print:max-h-none print:overflow-visible">
                    {data?.insights.map((insight, idx) => (
                      <div key={idx} className="p-5 rounded-2xl bg-base-100/50 border hover:border-primary/30 transition-all group print:mb-4 print:break-inside-avoid">
                        <div className="flex items-center justify-between mb-2">
                          <h3 className="font-bold flex items-center gap-2">
                            <div className={`h-2 w-2 rounded-full ${insight.sentimentScore > 0.7 ? "bg-success" : insight.sentimentScore > 0.4 ? "bg-warning" : "bg-rose-500"}`}></div>
                            {insight.facility}
                          </h3>
                          <span className="text-xs font-mono font-bold text-base-content/60">{(insight.sentimentScore * 100).toFixed(0)}% Mood Score</span>
                        </div>
                        <p className="text-sm text-base-content/60 mb-3 leading-relaxed">{insight.aiSummary}</p>
                        <div className="p-4 rounded-xl bg-primary/5 border border-primary/10 text-sm text-indigo-700 font-medium">
                          <div className="flex items-center gap-2 mb-2 text-[10px] font-bold uppercase tracking-wider text-primary">
                            <Zap className="h-3 w-3" /> Recommended Action
                          </div>
                          {insight.recommendations}
                        </div>
                      </div>
                    ))}
                  </div>
                </div>
              </div>
            </>
          )}
        </div>
      )}

      {activeTab === "management" && (
        <div className="max-w-2xl mx-auto animate-in slide-in-from-bottom-4 duration-500">
           <div className="p-8 rounded-3xl border bg-base-200 shadow-xl">
             <h2 className="text-2xl font-bold mb-6 flex items-center gap-2">
               <QrCode className="h-6 w-6 text-primary" /> Create Quarterly Survey
             </h2>
             <p className="text-base-content/60 mb-8">This will automatically generate a new Google Form and archive any existing active survey for this association.</p>
             
             <form onSubmit={handleCreateSurvey} className="space-y-6">
               <div className="grid grid-cols-2 gap-4">
                 <div className="space-y-2">
                   <label className="text-sm font-medium">Quarter</label>
                   <select 
                     value={quarter}
                     onChange={(e) => setQuarter(e.target.value)}
                     className="w-full p-3 rounded-xl bg-base-300 border-none focus:ring-2 ring-primary/20 transition-all font-medium"
                   >
                     <option>Q1</option>
                     <option>Q2</option>
                     <option>Q3</option>
                     <option>Q4</option>
                   </select>
                 </div>
                 <div className="space-y-2">
                   <label className="text-sm font-medium">Year</label>
                   <input 
                     type="number"
                     value={year}
                     onChange={(e) => setYear(Number(e.target.value))}
                     className="w-full p-3 rounded-xl bg-base-300 border-none focus:ring-2 ring-primary/20 transition-all font-medium"
                   />
                 </div>
               </div>
               
               <div className="p-6 rounded-2xl bg-warning/5 border border-amber-500/10 flex gap-4">
                  <ShieldCheck className="h-6 w-6 text-warning shrink-0" />
                  <p className="text-xs text-amber-700/80 leading-relaxed">
                    <strong>Note:</strong> Creating a survey requires the Google Workspace Service Account JSON to be configured in Settings. The form will include automated questions for all primary facilities.
                  </p>
               </div>

               <button 
                 type="submit" 
                 disabled={creating}
                 className="w-full py-4 bg-primary text-primary-content rounded-xl font-bold shadow-lg shadow-primary/20 hover:opacity-90 transition-all flex items-center justify-center gap-2"
               >
                 {creating ? <RefreshCw className="h-5 w-5 animate-spin" /> : <Layers className="h-5 w-5" />}
                 {creating ? "Generating Form..." : "Generate Google Form & Connect"}
               </button>
             </form>
           </div>
        </div>
      )}

      {activeTab === "bug" && (
        <div className="max-w-2xl mx-auto animate-in slide-in-from-bottom-4 duration-500">
           <div className="p-8 rounded-3xl border bg-base-200 shadow-xl">
             <h2 className="text-2xl font-bold mb-6 flex items-center gap-2">
               <AlertCircle className="h-6 w-6 text-destructive" /> Issue Tracking & Suggestions
             </h2>
             <p className="text-base-content/60 mb-8">Submit bug reports or suggest new features for the FinSight platform. Your feedback is stored directly and reviewed by the audit team.</p>
             
             <form onSubmit={handleSubmitFeedback} className="space-y-6">
               <div className="space-y-2">
                 <label className="text-sm font-medium">Email Address (Optional)</label>
                 <input 
                   type="email"
                   placeholder="resident@complex.com"
                   value={userEmail}
                   onChange={(e) => setUserEmail(e.target.value)}
                   className="w-full p-3 rounded-xl bg-base-300 border-none focus:ring-2 ring-primary/20 transition-all"
                 />
               </div>
               
               <div className="space-y-2">
                 <label className="text-sm font-medium">Feedback Type</label>
                 <div className="flex gap-2">
                   {["BUG", "FEATURE", "GENERAL"].map((t) => (
                     <button
                       key={t}
                       type="button"
                       onClick={() => setFeedbackType(t)}
                       className={`flex-1 py-2 rounded-lg text-xs font-bold transition-all border ${
                         feedbackType === t ? "bg-primary border-primary text-primary-content" : "bg-base-300 border-transparent text-base-content/60"
                       }`}
                     >
                       {t}
                     </button>
                   ))}
                 </div>
               </div>

               <div className="space-y-2">
                 <label className="text-sm font-medium">Message</label>
                 <textarea 
                   rows={5}
                   required
                   value={feedbackMsg}
                   onChange={(e) => setFeedbackMsg(e.target.value)}
                   placeholder="Describe the issue or your suggestion in detail..."
                   className="w-full p-4 rounded-xl bg-base-300 border-none focus:ring-2 ring-primary/20 transition-all resize-none"
                 />
               </div>

               <button 
                 type="submit" 
                 disabled={submitting}
                 className="w-full py-4 bg-foreground text-background rounded-xl font-bold hover:opacity-90 transition-all flex items-center justify-center gap-2"
               >
                 {submitting ? <RefreshCw className="h-5 w-5 animate-spin" /> : <MessageSquare className="h-5 w-5" />}
                 {submitting ? "Submitting..." : "Send Feedback"}
               </button>
             </form>
           </div>
        </div>
      )}
    </div>
  );
}
