"use client";

import { useState, useEffect } from "react";
import { apiFetch } from "@/lib/api";
import { 
  Radar, RadarChart, PolarGrid, PolarAngleAxis, PolarRadiusAxis, 
  ResponsiveContainer, LineChart, Line, XAxis, YAxis, Tooltip, CartesianGrid 
} from "recharts";
import { 
  Clipboard, QrCode, RefreshCw, Star, 
  TrendingUp, MessageSquare, AlertCircle, CheckCircle2,
  HardDrive, Zap, ShieldCheck, HeartPulse, Code2
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
}

export default function ResidentFeedbackDashboard() {
  const [survey, setSurvey] = useState<Survey | null>(null);
  const [data, setData] = useState<DashboardData | null>(null);
  const [loading, setLoading] = useState(true);
  const [syncing, setSyncing] = useState(false);

  useEffect(() => {
    fetchSurvey();
  }, []);

  const fetchSurvey = async () => {
    try {
      const res = await apiFetch("/survey/current?tenantId=local_tenant");
      const currentSurvey = await res.json();
      if (currentSurvey) {
        setSurvey(currentSurvey);
        fetchDashboard(currentSurvey.id);
      } else {
        setLoading(false);
      }
    } catch (err) {
      console.error("Failed to fetch survey:", err);
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

  if (loading) return <div className="p-10 text-center">Loading Intelligence Engine...</div>;

  if (!survey) {
    return (
      <div className="container mx-auto py-20 text-center">
        <AlertCircle className="h-12 w-12 text-muted-foreground mx-auto mb-4" />
        <h2 className="text-2xl font-bold">No Active Survey</h2>
        <p className="text-muted-foreground mt-2">Create a new survey to start collecting resident feedback.</p>
      </div>
    );
  }

  // Format data for Radar Chart
  const radarData = data ? Object.entries(data.averageRatings).map(([subject, value]) => ({
    subject: subject.split(' ').slice(0, 2).join(' '), // Shorten labels
    fullSubject: subject,
    A: value,
    fullMark: 5,
  })) : [];

  return (
    <div className="container mx-auto py-10 px-4 max-w-7xl">
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-6 mb-10">
        <div>
          <div className="flex items-center gap-2 text-primary font-bold mb-2">
            <Zap className="h-5 w-5" />
            <span>AI-POWERED INTELLIGENCE</span>
          </div>
          <h1 className="text-4xl font-extrabold tracking-tight">Resident Feedback Hub</h1>
          <p className="text-muted-foreground mt-1">Analyzing insights for {survey.quarter} {survey.year}</p>
        </div>

        <div className="flex gap-3">
          <button 
            onClick={syncNow}
            disabled={syncing}
            className="flex items-center gap-2 px-4 py-2 bg-secondary hover:bg-secondary/80 rounded-xl font-medium transition-all"
          >
            <RefreshCw className={`h-4 w-4 ${syncing ? "animate-spin" : ""}`} />
            {syncing ? "Syncing..." : "Sync Responses"}
          </button>
          <button 
            onClick={copyLink}
            className="flex items-center gap-2 px-4 py-2 bg-primary text-primary-foreground hover:opacity-90 rounded-xl font-medium transition-all shadow-lg"
          >
            <Clipboard className="h-4 w-4" />
            Share Survey URL
          </button>
        </div>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-3 gap-6 mb-10">
        <div className="p-6 rounded-2xl border bg-card/50 shadow-sm flex items-center gap-4">
          <div className="h-12 w-12 rounded-full bg-blue-500/10 flex items-center justify-center text-blue-500">
            <MessageSquare className="h-6 w-6" />
          </div>
          <div>
            <div className="text-2xl font-bold">{data?.totalResponses || 0}</div>
            <div className="text-sm text-muted-foreground">Total Responses</div>
          </div>
        </div>
        <div className="p-6 rounded-2xl border bg-card/50 shadow-sm flex items-center gap-4">
          <div className="h-12 w-12 rounded-full bg-emerald-500/10 flex items-center justify-center text-emerald-500">
            <Star className="h-6 w-6" />
          </div>
          <div>
            <div className="text-2xl font-bold">
              {radarData.length > 0 ? (radarData.reduce((acc, curr) => acc + curr.A, 0) / radarData.length).toFixed(1) : 0}
            </div>
            <div className="text-sm text-muted-foreground">Global Satisfaction</div>
          </div>
        </div>
        <div className="p-6 rounded-2xl border bg-card/50 shadow-sm flex items-center gap-4">
          <div className="h-12 w-12 rounded-full bg-amber-500/10 flex items-center justify-center text-amber-500">
            <TrendingUp className="h-6 w-6" />
          </div>
          <div>
            <div className="text-2xl font-bold">Stable</div>
            <div className="text-sm text-muted-foreground">Quarterly Trend</div>
          </div>
        </div>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-8">
        {/* Radar Chart */}
        <div className="p-8 rounded-3xl border bg-card shadow-lg relative overflow-hidden">
          <div className="absolute top-0 right-0 p-8 opacity-5">
            <HeartPulse className="h-32 w-32" />
          </div>
          <h2 className="text-xl font-bold mb-6 flex items-center gap-2">
             Facility Satisfaction Analysis
          </h2>
          <div className="h-[400px] w-full">
            <ResponsiveContainer width="100%" height="100%">
              <RadarChart cx="50%" cy="50%" outerRadius="80%" data={radarData}>
                <PolarGrid stroke="hsl(var(--muted-foreground))" strokeOpacity={0.1} />
                <PolarAngleAxis 
                  dataKey="subject" 
                  tick={{ fill: "hsl(var(--muted-foreground))", fontSize: 12 }} 
                />
                <PolarRadiusAxis 
                  angle={30} 
                  domain={[0, 5]} 
                  tick={{ fill: "hsl(var(--muted-foreground))", fontSize: 10 }} 
                />
                <Radar
                  name="Satisfaction"
                  dataKey="A"
                  stroke="hsl(var(--primary))"
                  fill="hsl(var(--primary))"
                  fillOpacity={0.6}
                />
                <Tooltip 
                   contentStyle={{ backgroundColor: "hsl(var(--card))", borderRadius: "12px", border: "1px solid hsl(var(--border))" }}
                />
              </RadarChart>
            </ResponsiveContainer>
          </div>
          <p className="text-xs text-muted-foreground text-center mt-4">Values represent average ratings from 1 (Very Dissatisfied) to 5 (Very Satisfied)</p>
        </div>

        {/* AI Recommendations */}
        <div className="flex flex-col gap-6">
          <div className="p-6 rounded-3xl border bg-indigo-500/5 border-indigo-500/10 shadow-lg relative h-full">
            <div className="flex items-center justify-between mb-6">
              <h2 className="text-xl font-bold flex items-center gap-2">
                <Code2 className="h-5 w-5 text-indigo-500" />
                Gemini Operational Insights
              </h2>
              <span className="text-[10px] font-bold bg-indigo-500/20 text-indigo-600 px-2 py-1 rounded-full border border-indigo-500/20 uppercase">Pro Engine</span>
            </div>
            
            <div className="space-y-4 max-h-[500px] overflow-y-auto pr-2 scrollbar-hide">
              {data?.insights.map((insight, idx) => (
                <div key={idx} className="p-5 rounded-2xl bg-background/50 border hover:border-indigo-500/30 transition-all group">
                  <div className="flex items-center justify-between mb-2">
                    <h3 className="font-bold flex items-center gap-2">
                      <div className={`h-2 w-2 rounded-full ${insight.sentimentScore > 0.7 ? "bg-emerald-500" : insight.sentimentScore > 0.4 ? "bg-amber-500" : "bg-rose-500"}`}></div>
                      {insight.facility}
                    </h3>
                    <span className="text-xs font-mono font-bold text-muted-foreground">{(insight.sentimentScore * 100).toFixed(0)}% Sentiment</span>
                  </div>
                  <p className="text-sm text-muted-foreground mb-3 leading-relaxed">{insight.aiSummary}</p>
                  <div className="p-3 rounded-lg bg-indigo-500/5 border border-indigo-500/10 text-xs italic text-indigo-600/80">
                    <span className="font-bold uppercase text-[9px] not-italic block mb-1">Recommendation:</span>
                    {insight.recommendations}
                  </div>
                </div>
              ))}
              
              {!data?.insights.length && (
                <div className="text-center py-20 text-muted-foreground italic">
                  Run synchronization to generate AI insights.
                </div>
              )}
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
