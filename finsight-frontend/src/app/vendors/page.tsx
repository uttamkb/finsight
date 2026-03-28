"use client";

import { useState, useEffect, useCallback, useRef } from "react";
import { BarChart, Bar, XAxis, YAxis, Tooltip, ResponsiveContainer, PieChart, Pie, Cell, Legend } from "recharts";
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
import { apiFetch } from "@/lib/api";

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

const COLORS = ['var(--color-primary)', 'var(--color-info)', 'var(--color-secondary)', 'var(--color-accent)', 'var(--color-neutral)', '#d946ef', '#ec4899', '#f43f5e'];

export default function VendorsPage() {
  const { toast } = useToast();
  const [vendors, setVendors] = useState<VendorInsight[]>([]);
  const [categories, setCategories] = useState<CategoryInsight[]>([]);
  const [anomalies, setAnomalies] = useState<Anomaly[]>([]);
  
  const [isLoading, setIsLoading] = useState(true);
  const [selectedCategory, setSelectedCategory] = useState<string | null>(null);
  const [drilldownData, setDrilldownData] = useState<any | null>(null);
  const [isDrillingDown, setIsDrillingDown] = useState(false);
  const [isDetecting, setIsDetecting] = useState(false);
  const [hasRunDetection, setHasRunDetection] = useState(false);
  const [currency, setCurrency] = useState("INR");
  
  const [autoRefresh, setAutoRefresh] = useState(false);
  const refreshIntervalRef = useRef<NodeJS.Timeout | null>(null);

  const fetchInsights = useCallback(async () => {
    setIsLoading(true);
    try {
      const vendRes = await apiFetch("/insights/vendors/top?limit=10");
      if (vendRes.ok) setVendors(await vendRes.json());

      const catRes = await apiFetch("/insights/categories/spend");
      if (catRes.ok) setCategories(await catRes.json());

      // Fetch forensic history
      const historyRes = await apiFetch("/insights/anomalies/history");
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
    apiFetch("/settings")
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
      const res = await apiFetch("/insights/anomalies/detect");
      if (res.ok) {
        const data = await res.json();
        // Since detect endpoint now persists to DB, we should refresh from history to stay consistent
        const historyRes = await apiFetch("/insights/anomalies/history");
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

  const handlePieClick = async (clickedData: any) => {
    // Recharts 2.x often passes the data in clickedData.payload
    // Depending on the event source, it might be the direct object or wrapped
    const entry = clickedData?.payload || clickedData;
    
    if (!entry || !entry.categoryName) {
      console.warn("Could not extract categoryName from pie click:", clickedData);
      return;
    }
    
    const categoryName = entry.categoryName;
    setSelectedCategory(categoryName);
    setIsDrillingDown(true);
    
    try {
      const res = await apiFetch(`/insights/categories/${encodeURIComponent(categoryName)}/drilldown`);
      if (res.ok) {
        setDrilldownData(await res.json());
        // Scroll to drilldown section
        setTimeout(() => {
          const drilldownEl = document.getElementById('category-drilldown');
          if (drilldownEl) drilldownEl.scrollIntoView({ behavior: 'smooth' });
        }, 100);
      } else {
        toast("Failed to load category details.", "error");
      }
    } catch (error) {
      console.error("Drilldown fetch error:", error);
      toast("Failed to load category details.", "error");
    } finally {
      setIsDrillingDown(false);
    }
  };

  const CustomTooltip = ({ active, payload, label }: any) => {
    if (active && payload && payload.length) {
      return (
        <div className="bg-base-200/90 backdrop-blur-md border border-primary/30 p-3 rounded-lg shadow-xl">
          <p className="font-semibold">{label || payload[0].name}</p>
          <p className="text-primary font-mono">{formatCurrency(payload[0].value, currency)}</p>
        </div>
      );
    }
    return null;
  };

  return (
    <div className="container mx-auto py-10 px-4 max-w-7xl animate-fade-in">
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-6 mb-12">
        <div className="flex items-center gap-5">
          <div className="p-3 bg-primary/10 rounded-2xl glow-primary">
            <Building2 className="h-10 w-10 text-primary" />
          </div>
          <div>
            <h1 className="text-4xl font-black tracking-tight leading-tight">Vendor Intel & Audit</h1>
            <p className="text-base-content/60 font-medium text-lg uppercase tracking-wider text-[11px] mt-1">Behavioral Analysis & Forensic Watchdog</p>
          </div>
        </div>
      </div>

      {isLoading ? (
        <div className="flex items-center justify-center p-20">
           <Loader2 className="h-8 w-8 animate-spin text-primary" />
        </div>
      ) : (
        <>
          <div className="grid grid-cols-1 lg:grid-cols-2 gap-8 mb-10 [animation-delay:100ms]">
            <div className="glass-panel p-0 flex flex-col rounded-[2.5rem] shadow-xl overflow-hidden transition-all hover:glow-primary">
               <div className="p-8 pb-4">
                  <h3 className="text-xl font-black flex items-center gap-3">
                    <Building2 className="h-6 w-6 text-primary" /> Prime Vector Vendors
                  </h3>
               </div>
               <div className="p-8 h-[260px] w-full">
                 <ResponsiveContainer width="100%" height="100%">
                   <BarChart data={vendors} layout="vertical">
                     <XAxis type="number" hide />
                     <YAxis dataKey="vendorName" type="category" width={110} tick={{fill: 'currentColor', fontSize: 10, opacity: 0.6, fontWeight: 700}} axisLine={false} tickLine={false} />
                     <Tooltip content={<CustomTooltip />} />
                     <Bar dataKey="totalSpent" fill="var(--color-primary)" radius={[0, 8, 8, 0]} barSize={16}>
                       {vendors.map((entry, index) => (
                         <Cell key={`cell-${index}`} fill={COLORS[index % COLORS.length]} />
                       ))}
                     </Bar>
                   </BarChart>
                 </ResponsiveContainer>
               </div>
               <div className="border-t border-base-content/5 mt-auto bg-base-300/20">
                  <table className="table w-full">
                    <thead>
                      <tr className="bg-base-300/40 text-base-content/50">
                        <th className="px-8 py-4 text-[10px] font-black uppercase tracking-[0.2em]">Entity Name</th>
                        <th className="px-8 py-4 text-right text-[10px] font-black uppercase tracking-[0.2em]">Txns</th>
                        <th className="px-8 py-4 text-right text-[10px] font-black uppercase tracking-[0.2em]">Inflow Magnitude</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-base-content/5 font-medium">
                      {vendors.slice(0, 5).map((v, i) => (
                        <tr key={i} className="hover:bg-primary/5 transition-all group">
                          <td className="px-8 py-3.5 font-bold text-sm group-hover:text-primary">{v.vendorName}</td>
                          <td className="px-8 py-3.5 text-right font-mono font-bold text-xs opacity-60">{v.transactionCount}</td>
                          <td className="px-8 py-3.5 text-right font-mono font-black text-sm text-primary">{formatCurrency(v.totalSpent, currency)}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
               </div>
            </div>

            <div className="glass-panel p-8 rounded-[2.5rem] shadow-xl transition-all hover:glow-secondary">
               <h3 className="text-xl font-black mb-8 flex items-center gap-3">
                 <PieChartIcon className="h-6 w-6 text-primary" /> Resource Distribution
               </h3>
               <div className="h-[340px] w-full">
                 <ResponsiveContainer width="100%" height="100%">
                   <PieChart>
                     <Pie
                       data={categories}
                       innerRadius={90}
                       outerRadius={130}
                       paddingAngle={8}
                       dataKey="totalSpent"
                       nameKey="categoryName"
                      onClick={handlePieClick}
                      className="cursor-pointer"
                     >
                       {categories.map((entry, index) => (
                         <Cell key={`cell-${index}`} fill={COLORS[index % COLORS.length]} stroke="rgba(255,255,255,0.05)" strokeWidth={2} />
                       ))}
                     </Pie>
                     <Tooltip content={<CustomTooltip />} />
                     <Legend iconType="circle" wrapperStyle={{ fontSize: '11px', fontWeight: 'bold', textTransform: 'uppercase' }} />
                   </PieChart>
                 </ResponsiveContainer>
               </div>
            </div>
          </div>

          {/* Category Drilldown Section */}
          {(selectedCategory || isDrillingDown) && (
            <div id="category-drilldown" className="glass-panel p-8 rounded-[2.5rem] shadow-2xl mb-10 border-primary/20 animate-in slide-in-from-bottom-5 duration-500">
              <div className="flex items-center justify-between mb-10">
                <div className="flex items-center gap-4">
                  <div className="p-3 bg-primary/10 rounded-2xl">
                    <PieChartIcon className="h-8 w-8 text-primary" />
                  </div>
                  <div>
                    <h2 className="text-3xl font-black tracking-tight">{selectedCategory}</h2>
                    <p className="text-sm font-bold text-base-content/40 uppercase tracking-widest">Resource Allocation Deep-Dive</p>
                  </div>
                </div>
                <button 
                  onClick={() => {
                    setSelectedCategory(null);
                    setDrilldownData(null);
                  }}
                  className="btn btn-ghost btn-circle"
                >
                  <AlertCircle className="h-6 w-6 rotate-45" />
                </button>
              </div>

              {isDrillingDown ? (
                <div className="flex flex-col items-center justify-center p-20 gap-4">
                  <Loader2 className="h-10 w-10 animate-spin text-primary" />
                  <p className="font-bold text-primary animate-pulse uppercase tracking-widest text-xs">Analyzing Category Composition...</p>
                </div>
              ) : drilldownData ? (
                <div className="grid grid-cols-1 lg:grid-cols-2 gap-10">
                  {/* Top Vendors in Category */}
                  <div className="flex flex-col">
                    <h4 className="text-sm font-black uppercase tracking-widest text-base-content/50 mb-6 flex items-center gap-2">
                      <Building2 className="h-4 w-4" /> Leading Entities
                    </h4>
                    <div className="bg-base-200/50 rounded-3xl overflow-hidden border border-base-content/5">
                      <table className="table w-full">
                        <thead className="bg-base-300/50">
                          <tr>
                            <th className="px-6 py-4 text-[10px] uppercase font-black tracking-widest opacity-50">Vendor</th>
                            <th className="px-6 py-4 text-right text-[10px] uppercase font-black tracking-widest opacity-50">Count</th>
                            <th className="px-6 py-4 text-right text-[10px] uppercase font-black tracking-widest opacity-50">Magnitude</th>
                          </tr>
                        </thead>
                        <tbody className="divide-y divide-base-content/5">
                          {drilldownData.topVendors.map((v: any, i: number) => (
                            <tr key={i} className="hover:bg-primary/5 transition-all">
                              <td className="px-6 py-4 font-bold text-sm">{v.vendorName}</td>
                              <td className="px-6 py-4 text-right font-mono text-xs opacity-60">{v.transactionCount}</td>
                              <td className="px-6 py-4 text-right font-mono font-black text-sm text-primary">{formatCurrency(v.totalSpent, currency)}</td>
                            </tr>
                          ))}
                        </tbody>
                      </table>
                    </div>
                  </div>

                  {/* Recent Transactions in Category */}
                  <div className="flex flex-col">
                    <h4 className="text-sm font-black uppercase tracking-widest text-base-content/50 mb-6 flex items-center gap-2">
                      <Clock className="h-4 w-4" /> Operational History
                    </h4>
                    <div className="space-y-3">
                      {drilldownData.recentTransactions.map((tx: any, i: number) => (
                        <div key={i} className="bg-base-100 p-5 rounded-2xl border border-base-content/5 hover:border-primary/20 transition-all flex items-center justify-between group shadow-sm">
                          <div className="flex flex-col gap-1">
                            <span className="text-[10px] font-black opacity-30 uppercase tracking-tighter">{tx.txDate}</span>
                            <span className="font-bold text-sm line-clamp-1 group-hover:text-primary transition-colors">{tx.description}</span>
                          </div>
                          <div className="text-right">
                            <div className="font-black text-base text-primary/80">{formatCurrency(tx.amount, currency)}</div>
                            <div className="text-[10px] font-black opacity-30 uppercase tracking-widest">{tx.vendor || 'UNSPECIFIED'}</div>
                          </div>
                        </div>
                      ))}
                    </div>
                  </div>
                </div>
              ) : (
                <div className="text-center p-10 opacity-30 italic">No historical data available for this category.</div>
              )}
            </div>
          )}

          <div className="glass-panel rounded-[2.5rem] overflow-hidden shadow-2xl relative border-warning/10 [animation-delay:200ms] animate-fade-in hover:glow-accent">
             <div className="p-8 flex flex-col md:flex-row items-start md:items-center justify-between border-b border-warning/10 gap-6 bg-warning/5 backdrop-blur-xl">
                <div>
                  <h2 className="text-2xl font-black flex items-center gap-3 text-warning uppercase tracking-tighter">
                     <AlertTriangle className="h-6 w-6 animate-pulse" />
                     Forensic Audit Watchdog
                  </h2>
                  <p className="text-sm font-bold text-base-content/40 mt-1 uppercase tracking-widest">
                    Gemini 1.5 Pro AI Analytic Sequence
                  </p>
                </div>
                <div className="flex items-center gap-4">
                  <div className="flex items-center gap-3 bg-base-300/50 px-5 py-3 rounded-2xl border border-base-content/10 shadow-inner">
                    <span className="text-[10px] uppercase font-black text-base-content/40 tracking-[0.2em]">Live Monitoring</span>
                    <input 
                      type="checkbox" 
                      className="toggle toggle-primary toggle-sm" 
                      checked={autoRefresh}
                      onChange={() => setAutoRefresh(!autoRefresh)}
                    />
                  </div>
                  <button
                    onClick={() => runAnomalyDetection()}
                    disabled={isDetecting}
                    className="btn btn-warning h-14 px-8 rounded-2xl font-black text-xs uppercase tracking-[0.2em] shadow-xl shadow-warning/20 transition-all hover:scale-105 active:scale-95 flex items-center gap-3"
                  >
                    {isDetecting ? <Loader2 className="h-5 w-5 animate-spin" /> : <Sparkles className="h-5 w-5" />}
                    Invoke Security Configuration
                  </button>
                </div>
             </div>

             <div className="p-0">
               {!hasRunDetection && !isDetecting ? (
                  <div className="px-8 py-24 text-center text-base-content/40 italic font-black text-lg uppercase tracking-widest opacity-40">
                     <AlertCircle className="h-12 w-12 mx-auto mb-4" />
                     <p>Operational Buffer Empty. Initiate Security Configuration.</p>
                  </div>
               ) : isDetecting && anomalies.length === 0 ? (
                  <div className="px-8 py-24 text-center text-primary flex flex-col items-center justify-center min-h-[300px] bg-primary/5">
                     <Loader2 className="h-12 w-12 animate-spin mb-6" />
                     <p className="font-black animate-pulse uppercase tracking-[0.3em] text-sm">Synchronizing with Gemini 1.5 Forensic Neural Engine...</p>
                  </div>
               ) : anomalies.length === 0 ? (
                  <div className="px-8 py-24 text-center bg-success/5">
                     <div className="inline-flex items-center justify-center w-20 h-20 rounded-full bg-success/10 mb-6 glow-secondary">
                        <Sparkles className="h-10 w-10 text-success" />
                     </div>
                     <p className="text-3xl font-black text-success mb-2 uppercase tracking-tight">Zero Variance Detected</p>
                     <p className="text-base-content/40 font-bold uppercase tracking-widest text-[11px]">System Integrity: Optimal</p>
                  </div>
               ) : (
                  <div className="overflow-x-auto">
                    <table className="table w-full">
                      <thead>
                        <tr className="bg-error/10 text-error">
                          <th className="px-8 py-5 text-[10px] font-black uppercase tracking-[0.2em]">Flagged Operational Record</th>
                          <th className="px-8 py-5 text-[10px] font-black uppercase tracking-[0.2em]">Variance Magnitude</th>
                          <th className="px-8 py-5 text-[10px] font-black uppercase tracking-[0.2em]">AI Forensic Deduction</th>
                          <th className="px-8 py-5 text-right text-[10px] font-black uppercase tracking-[0.2em]">Detection Vector</th>
                        </tr>
                      </thead>
                      <tbody className="divide-y divide-base-content/5 font-medium">
                        {anomalies.map((anomaly, idx) => (
                           <tr key={idx} className="hover:bg-error/5 transition-all group">
                             <td className="px-8 py-6">
                               <div className="font-black text-base text-base-content/80 group-hover:text-error transition-colors uppercase tracking-tight">{anomaly.description}</div>
                               <div className="flex items-center gap-2 mt-2">
                                  <Clock className="h-3 w-3 text-base-content/20" />
                                  <span className="text-[10px] font-black font-mono text-base-content/30 uppercase tracking-widest">{anomaly.date || anomaly.txDate}</span>
                               </div>
                             </td>
                             <td className="px-8 py-6 font-mono font-black text-error text-xl scale-110 origin-left">
                                {formatCurrency(anomaly.amount, currency)}
                             </td>
                             <td className="px-8 py-6">
                               <div className="flex items-start gap-3 bg-error/5 p-4 rounded-2xl border border-error/5 shadow-inner transition-colors group-hover:bg-error/10">
                                  <Sparkles className="h-5 w-5 text-error shrink-0 mt-0.5 animate-pulse" />
                                  <span className="text-base-content/70 text-xs leading-relaxed font-bold italic">{anomaly.reason}</span>
                                </div>
                             </td>
                             <td className="px-8 py-6 text-right">
                                <span className="text-[10px] font-black font-mono text-base-content/30 uppercase tracking-[0.1em] block">Sensor Tag</span>
                                <span className="text-[11px] font-bold text-base-content/60">{anomaly.detectedAt ? new Date(anomaly.detectedAt).toLocaleString() : 'LIVE RECORD'}</span>
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
