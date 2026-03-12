"use client";

import { useState, useEffect, useCallback, useRef } from "react";
import { BarChart, Bar, XAxis, YAxis, Tooltip, ResponsiveContainer, PieChart, Pie, Cell } from "recharts";
import { 
  Building2, 
  PieChart as PieChartIcon, 
  AlertTriangle, 
  Loader2, 
  Sparkles, 
  AlertCircle,
  RefreshCcw,
  History,
  Clock
} from "lucide-react";
import { useToast } from "@/components/toast-provider";
import { formatCurrency } from "@/lib/utils";
import { API_BASE_URL } from "@/lib/constants";

interface VendorInsight {
  vendorName: string;
  totalSpent: number;
  transactionCount: number;
}

interface CategoryInsight {
  categoryName: string;
  totalSpent: number;
}

interface Anomaly {
  description: string;
  reason: string;
  amount: number;
  date?: string;
  txDate?: string;
  detectedAt?: string;
}

const COLORS = ['#0ea5e9', '#3b82f6', '#6366f1', '#8b5cf6', '#a855f7', '#d946ef', '#ec4899', '#f43f5e'];

export default function VendorsPage() {
  const { toast } = useToast();
  const [vendors, setVendors] = useState<VendorInsight[]>([]);
  const [categories, setCategories] = useState<CategoryInsight[]>([]);
  const [anomalies, setAnomalies] = useState<Anomaly[]>([]);
  
  const [isLoading, setIsLoading] = useState(true);
  const [isDetecting, setIsDetecting] = useState(false);
  const [hasRunDetection, setHasRunDetection] = useState(false);
  const [currency, setCurrency] = useState("INR");
  
  const [autoRefresh, setAutoRefresh] = useState(false);
  const refreshIntervalRef = useRef<NodeJS.Timeout | null>(null);

  const fetchInsights = useCallback(async () => {
    setIsLoading(true);
    try {
      const vendRes = await fetch(`${API_BASE_URL}/insights/vendors/top?limit=10`);
      if (vendRes.ok) setVendors(await vendRes.json());

      const catRes = await fetch(`${API_BASE_URL}/insights/categories/spend`);
      if (catRes.ok) setCategories(await catRes.json());

      // Fetch forensic history
      const historyRes = await fetch(`${API_BASE_URL}/insights/anomalies/history`);
      if (historyRes.ok) {
        const historyData = await historyRes.json();
        if (historyData.length > 0) {
          setAnomalies(historyData);
          setHasRunDetection(true);
        }
      }

    } catch (error) {
      console.error("Failed to fetch insights:", error);
      toast("Error loading vendor insights.", "error");
    } finally {
      setIsLoading(false);
    }
  }, [toast]);

  useEffect(() => {
    fetchInsights();
    fetch(`${API_BASE_URL}/settings`)
      .then(res => res.json())
      .then(data => {
        if (data && data.currency) setCurrency(data.currency);
      })
      .catch(console.error);
  }, [fetchInsights]);

  const runAnomalyDetection = useCallback(async (isSilent = false) => {
    if (isDetecting) return;
    setIsDetecting(true);
    if (!isSilent) toast("Auditing recent transactions with Gemini 1.5 Pro...", "info");

    try {
      const res = await fetch(`${API_BASE_URL}/insights/anomalies/detect`);
      if (res.ok) {
        const data = await res.json();
        // Since detect endpoint now persists to DB, we should refresh from history to stay consistent
        const historyRes = await fetch(`${API_BASE_URL}/insights/anomalies/history`);
        if (historyRes.ok) {
          setAnomalies(await historyRes.json());
        } else {
          setAnomalies(data.anomalies || []);
        }
        
        setHasRunDetection(true);
        if (!isSilent) {
          if (data.count === 0) {
            toast("Audit complete. No anomalies detected.", "success");
          } else {
            toast(`Audit complete. Found ${data.count} suspicious records.`, "error");
          }
        }
      } else if (!isSilent) {
        const errData = await res.json();
        toast(errData.error || "Failed to run anomaly detection", "error");
      }
    } catch (error) {
       if (!isSilent) toast("Connection error during anomaly detection.", "error");
    } finally {
      setIsDetecting(false);
    }
  }, [isDetecting, toast]);

  useEffect(() => {
    if (autoRefresh) {
      toast("Auto-audit enabled (5 min interval).", "info");
      refreshIntervalRef.current = setInterval(() => {
        runAnomalyDetection(true);
      }, 5 * 60 * 1000);
    } else {
      if (refreshIntervalRef.current) {
        clearInterval(refreshIntervalRef.current);
        refreshIntervalRef.current = null;
      }
    }
    return () => {
      if (refreshIntervalRef.current) clearInterval(refreshIntervalRef.current);
    };
  }, [autoRefresh, runAnomalyDetection, toast]);

  const CustomTooltip = ({ active, payload, label }: any) => {
    if (active && payload && payload.length) {
      return (
        <div className="bg-card/90 backdrop-blur-md border border-primary/20 p-3 rounded-lg shadow-xl">
          <p className="font-semibold">{label || payload[0].name}</p>
          <p className="text-primary font-mono">{formatCurrency(payload[0].value, currency)}</p>
        </div>
      );
    }
    return null;
  };

  return (
    <div className="container mx-auto py-10 px-4 max-w-7xl animate-fade-in">
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-4 mb-8">
        <div>
          <h1 className="text-3xl font-bold tracking-tight inline-flex items-center gap-3">
            <Building2 className="h-8 w-8 text-primary" />
            Vendor Insights & Audit
          </h1>
          <p className="text-muted-foreground mt-2">
            Analyze expenditure flows and audit transactions using AI Anomaly Detection.
          </p>
        </div>
      </div>

      {isLoading ? (
        <div className="flex items-center justify-center p-20">
           <Loader2 className="h-8 w-8 animate-spin text-primary" />
        </div>
      ) : (
        <>
          <div className="grid grid-cols-1 lg:grid-cols-2 gap-6 mb-8">
            <div className="p-0 flex flex-col rounded-xl border bg-card/50 backdrop-blur-sm border-primary/10 shadow-lg overflow-hidden">
               <div className="p-6 pb-0">
                  <h3 className="text-lg font-semibold flex items-center gap-2">
                    <Building2 className="h-5 w-5 text-primary" /> Top 10 Vendors
                  </h3>
               </div>
               <div className="p-6 h-[240px] w-full">
                 <ResponsiveContainer width="100%" height="100%">
                   <BarChart data={vendors} layout="vertical" margin={{ top: 5, right: 30, left: 20, bottom: 5 }}>
                     <XAxis type="number" hide />
                     <YAxis dataKey="vendorName" type="category" width={100} tick={{fill: '#888', fontSize: 11}} />
                     <Tooltip content={<CustomTooltip />} />
                     <Bar dataKey="totalSpent" fill="var(--primary)" radius={[0, 4, 4, 0]} barSize={15}>
                       {vendors.map((entry, index) => (
                         <Cell key={`cell-${index}`} fill={COLORS[index % COLORS.length]} />
                       ))}
                     </Bar>
                   </BarChart>
                 </ResponsiveContainer>
               </div>
               <div className="border-t border-primary/5">
                  <table className="w-full text-xs text-left">
                    <thead className="bg-muted/30 text-muted-foreground uppercase font-bold">
                      <tr>
                        <th className="px-6 py-3">Vendor</th>
                        <th className="px-6 py-3 text-right">Transactions</th>
                        <th className="px-6 py-3 text-right">Total Spent</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-primary/5">
                      {vendors.slice(0, 5).map((v, i) => (
                        <tr key={i} className="hover:bg-primary/5">
                          <td className="px-6 py-2.5 font-medium">{v.vendorName}</td>
                          <td className="px-6 py-2.5 text-right font-mono">{v.transactionCount}</td>
                          <td className="px-6 py-2.5 text-right font-mono text-primary">{formatCurrency(v.totalSpent, currency)}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
               </div>
            </div>

            <div className="p-6 rounded-xl border bg-card/50 backdrop-blur-sm border-primary/10 shadow-lg">
               <h3 className="text-lg font-semibold mb-6 flex items-center gap-2">
                 <PieChartIcon className="h-5 w-5 text-primary" /> Expenditure by Category
               </h3>
               <div className="h-[300px] w-full">
                 <ResponsiveContainer width="100%" height="100%">
                   <PieChart>
                     <Pie
                       data={categories}
                       cx="50%"
                       cy="50%"
                       innerRadius={80}
                       outerRadius={120}
                       paddingAngle={5}
                       dataKey="totalSpent"
                       nameKey="categoryName"
                     >
                       {categories.map((entry, index) => (
                         <Cell key={`cell-${index}`} fill={COLORS[index % COLORS.length]} />
                       ))}
                     </Pie>
                     <Tooltip content={<CustomTooltip />} />
                   </PieChart>
                 </ResponsiveContainer>
               </div>
            </div>
          </div>

          <div className="rounded-xl border border-primary/10 bg-card overflow-hidden shadow-lg relative">
             <div className="absolute inset-x-0 top-0 h-1 bg-gradient-to-r from-transparent via-primary/50 to-transparent"></div>
             
             <div className="p-6 flex flex-col md:flex-row items-start md:items-center justify-between border-b border-primary/10 gap-4">
                <div>
                  <h2 className="text-xl font-bold flex items-center gap-2">
                     <AlertTriangle className="h-5 w-5 text-amber-500" />
                     Forensic Audit Watchdog
                  </h2>
                  <p className="text-sm text-muted-foreground mt-1">
                    AI-powered anomaly detection for suspicious patterns, duplicates, and inflated costs.
                  </p>
                </div>
                <div className="flex items-center gap-3">
                  <div className="flex items-center gap-2 bg-muted/50 px-3 py-1.5 rounded-lg border border-primary/5">
                    <span className="text-[10px] uppercase font-bold text-muted-foreground tracking-wider">Auto-Audit</span>
                    <button 
                      onClick={() => setAutoRefresh(!autoRefresh)}
                      className={`relative inline-flex h-5 w-9 shrink-0 cursor-pointer items-center rounded-full border-2 border-transparent transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 bg-muted ${autoRefresh ? 'bg-primary' : 'bg-muted'}`}
                    >
                      <span
                        className={`pointer-events-none block h-4 w-4 rounded-full bg-white shadow-lg ring-0 transition-transform ${autoRefresh ? 'translate-x-4' : 'translate-x-0'}`}
                      />
                    </button>
                  </div>
                  <button
                    onClick={() => runAnomalyDetection()}
                    disabled={isDetecting}
                    className="inline-flex items-center justify-center gap-2 whitespace-nowrap rounded-md text-sm font-medium ring-offset-background transition-all focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 disabled:pointer-events-none disabled:opacity-50 bg-primary text-primary-foreground hover:bg-primary/90 h-10 px-4 py-2 shadow-lg shadow-primary/20 hover:scale-105"
                  >
                    {isDetecting ? <Loader2 className="h-4 w-4 animate-spin" /> : <Sparkles className="h-4 w-4" />}
                    Invoke Analyst
                  </button>
                </div>
             </div>

             <div className="p-0">
               {!hasRunDetection && !isDetecting ? (
                  <div className="px-6 py-16 text-center text-muted-foreground">
                     <AlertCircle className="h-8 w-8 mx-auto mb-3 opacity-20" />
                     <p>No prior audit results found. Run a security audit to begin forensic analysis.</p>
                  </div>
               ) : isDetecting && anomalies.length === 0 ? (
                  <div className="px-6 py-16 text-center text-primary flex flex-col items-center justify-center min-h-[200px]">
                     <Loader2 className="h-10 w-10 animate-spin mb-4" />
                     <p className="font-medium animate-pulse">Consulting Gemini 1.5 Pro...</p>
                  </div>
               ) : anomalies.length === 0 ? (
                  <div className="px-6 py-16 text-center">
                     <div className="inline-flex items-center justify-center w-16 h-16 rounded-full bg-emerald-500/10 mb-4">
                        <Sparkles className="h-8 w-8 text-emerald-500" />
                     </div>
                     <p className="text-xl font-semibold text-emerald-500 mb-1">Clean Audit!</p>
                     <p className="text-muted-foreground">No suspicious transactions found in the forensic buffer.</p>
                  </div>
               ) : (
                  <div className="overflow-x-auto">
                    <table className="w-full text-sm text-left">
                      <thead className="text-xs text-muted-foreground uppercase bg-muted/50">
                        <tr>
                          <th scope="col" className="px-6 py-4 font-medium">Flagged Entity</th>
                          <th scope="col" className="px-6 py-4 font-medium">Amount</th>
                          <th scope="col" className="px-6 py-4 font-medium">AI Forensic Reasoning</th>
                          <th scope="col" className="px-6 py-4 font-medium text-right">Detection Date</th>
                        </tr>
                      </thead>
                      <tbody className="divide-y divide-primary/5">
                        {anomalies.map((anomaly, idx) => (
                           <tr key={idx} className="hover:bg-destructive/5 transition-colors group">
                             <td className="px-6 py-4">
                               <div className="font-medium">{anomaly.description}</div>
                               <div className="text-xs text-muted-foreground mt-0.5">{anomaly.date || anomaly.txDate}</div>
                             </td>
                             <td className="px-6 py-4 font-mono font-bold text-destructive">
                                {formatCurrency(anomaly.amount, currency)}
                             </td>
                             <td className="px-6 py-4">
                               <div className="flex items-start gap-2">
                                  <AlertTriangle className="h-4 w-4 text-amber-500 shrink-0 mt-0.5" />
                                  <span className="text-muted-foreground text-xs leading-relaxed">{anomaly.reason}</span>
                               </div>
                             </td>
                             <td className="px-6 py-4 text-right text-[10px] text-muted-foreground font-mono">
                                {anomaly.detectedAt ? new Date(anomaly.detectedAt).toLocaleString() : 'Live'}
                             </td>
                           </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
               )}
             </div>
          </div>
        </>
      )}
    </div>
  );
}
