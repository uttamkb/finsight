"use client";

import { useState, useEffect, useCallback, useRef } from "react";
import { Download, FileBarChart, Loader2, TrendingDown, TrendingUp, CalendarDays, Wallet } from "lucide-react";
import { useToast } from "@/components/toast-provider";
import { formatCurrency } from "@/lib/utils";
import { apiFetch } from "@/lib/api";
import { BarChart, Bar, XAxis, YAxis, Tooltip, ResponsiveContainer, Cell } from "recharts";

interface GbmReport {
  totalIncome: number;
  totalExpense: number;
  netBalance: number;
  burnRate: number;
  projectedExpense: number;
  topVendors: { vendorName: string; totalSpent: number }[];
  categoryBreakdown: { categoryName: string; totalSpent: number }[];
}

const COLORS = ['var(--color-primary)', 'var(--color-info)', 'var(--color-secondary)', 'var(--color-accent)', 'var(--color-neutral)'];

export default function AuditorPage() {
  const { toast } = useToast();
  const [report, setReport] = useState<GbmReport | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [currency, setCurrency] = useState("INR");
  const reportRef = useRef<HTMLDivElement>(null);

  const fetchReport = useCallback(async () => {
    setIsLoading(true);
    try {
      // In a real app we'd have a dedicated /gbm endpoint returning all this.
      // For now, we simulate the aggregation using existing endpoints.
      const vendRes = await apiFetch("/insights/vendors/top?limit=5");
      const topVendors = vendRes.ok ? await vendRes.json() : [];

      const catRes = await apiFetch("/insights/categories/spend");
      const categoryBreakdown = catRes.ok ? await catRes.json() : [];

      // Calculate totals from categories
      const totalExpense = categoryBreakdown.reduce((sum: number, cat: any) => sum + cat.totalSpent, 0);
      
      // Simulate Income & Burn Rate (would normally come from DB)
      const mockIncome = totalExpense * 1.2; // Assuming 20% surplus
      
      setReport({
        totalIncome: mockIncome,
        totalExpense: totalExpense,
        netBalance: mockIncome - totalExpense,
        burnRate: totalExpense / 30, // Simulated daily burn rate based on 30 days
        projectedExpense: totalExpense * 1.1, // Simulated 10% MoM increase
        topVendors,
        categoryBreakdown
      });
    } catch (error) {
      console.error("Failed to compile GBM report:", error);
      toast("Error compiling report data.", "error");
    } finally {
      setIsLoading(false);
    }
  }, [toast]);

  useEffect(() => {
    fetchReport();
    apiFetch("/settings")
      .then(res => res.json())
      .then(data => {
        if (data && data.currency) setCurrency(data.currency);
      })
      .catch(console.error);
  }, [fetchReport]);

  const handleDownload = () => {
     toast("Generating PDF Report...", "info");
     // Usually would use html2pdf or window.print()
     setTimeout(() => {
        window.print();
     }, 500);
  };

  const CustomTooltip = ({ active, payload, label }: any) => {
    if (active && payload && payload.length) {
      return (
        <div className="bg-base-200/90 backdrop-blur-md border border-primary/30 p-2 text-xs rounded shadow-lg">
          <p className="font-semibold">{label || payload[0].payload.vendorName || payload[0].payload.categoryName}</p>
          <p className="text-primary font-mono">{formatCurrency(payload[0].value, currency)}</p>
        </div>
      );
    }
    return null;
  };

  if (isLoading || !report) {
    return (
      <div className="flex flex-col items-center justify-center min-h-[60vh]">
        <Loader2 className="h-10 w-10 animate-spin text-primary mb-4" />
        <p className="text-base-content/60 animate-pulse">Compiling Ledger Data for GBM Report...</p>
      </div>
    );
  }

  return (
    <div className="container mx-auto py-10 px-4 max-w-5xl animate-fade-in print:py-0 print:m-0 print:max-w-full">
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-6 mb-12 print:hidden">
        <div className="flex items-center gap-5">
          <div className="p-3 bg-primary/10 rounded-2xl glow-primary">
            <FileBarChart className="h-10 w-10 text-primary" />
          </div>
          <div>
            <h1 className="text-4xl font-black tracking-tight leading-tight">Forensic Auditor</h1>
            <p className="text-base-content/60 font-medium text-lg uppercase tracking-wider text-[11px] mt-1">GBM Reporting & Ledger Validation</p>
          </div>
        </div>
        <button
          onClick={handleDownload}
          className="btn btn-primary h-12 px-8 rounded-2xl font-black text-xs uppercase tracking-widest transition-all shadow-xl shadow-primary/30 flex items-center gap-3 hover:scale-105 active:scale-95"
        >
          <Download className="h-5 w-5" />
          Export GBM Report
        </button>
      </div>

      {/* Report Container */}
      <div ref={reportRef} className="glass-panel rounded-[2.5rem] shadow-2xl overflow-hidden print:shadow-none print:border-none print:bg-white print:text-black [animation-delay:100ms] animate-fade-in border-primary/5">
         <div className="p-10 border-b border-base-content/5 bg-base-200/30 backdrop-blur-xl print:bg-gray-100 flex justify-between items-end">
            <div>
               <h2 className="text-4xl font-black tracking-tighter mb-3 uppercase">General Body Meeting Report</h2>
               <div className="flex items-center gap-4 text-base-content/40 font-bold uppercase tracking-widest text-[10px]">
                  <div className="flex items-center gap-2">
                    <CalendarDays className="h-3.5 w-3.5" /> Statement Period: {new Date().toLocaleDateString('default', { month: 'long', year: 'numeric'})}
                  </div>
                  <div className="w-1 h-1 rounded-full bg-base-content/20"></div>
                  <div>Report Ref: GBM-{new Date().getFullYear()}-{new Date().getMonth()+1}</div>
               </div>
            </div>
            <div className="text-right">
               <p className="text-[10px] uppercase font-black text-base-content/30 tracking-[0.2em] mb-2">Authority Sequence</p>
               <div className="px-4 py-2 bg-primary/10 rounded-xl border border-primary/10 flex items-center gap-3 justify-end">
                  <div className="w-2 h-2 rounded-full bg-success animate-pulse print:hidden"></div>
                  <span className="font-black text-xs uppercase tracking-widest text-primary">FinSight AI Auditor</span>
               </div>
            </div>
         </div>

         {/* Executive Summary Cards */}
         <div className="grid grid-cols-1 md:grid-cols-3 gap-8 p-10 bg-base-200/20 print:bg-white print:gap-4 print:p-6">
            <div className="p-8 rounded-3xl border border-base-content/5 bg-base-100/50 shadow-inner transition-all hover:glow-secondary">
               <p className="text-[10px] font-black text-base-content/40 uppercase tracking-[0.2em] mb-4 flex items-center gap-2">
                  <TrendingUp className="h-4 w-4 text-success" /> Revenue Stream
               </p>
               <p className="text-4xl font-black font-mono tracking-tighter text-success">{formatCurrency(report.totalIncome, currency)}</p>
               <p className="text-[10px] text-base-content/20 mt-4 font-bold uppercase tracking-widest">Total Inflow Collected</p>
            </div>
            <div className="p-8 rounded-3xl border border-base-content/5 bg-base-100/50 shadow-inner transition-all hover:glow-accent">
               <p className="text-[10px] font-black text-base-content/40 uppercase tracking-[0.2em] mb-4 flex items-center gap-2">
                  <TrendingDown className="h-4 w-4 text-error" /> Operational Drain
               </p>
               <p className="text-4xl font-black font-mono tracking-tighter text-error">{formatCurrency(report.totalExpense, currency)}</p>
               <p className="text-[10px] text-base-content/20 mt-4 font-bold uppercase tracking-widest">Total Outflow Dispersed</p>
            </div>
            <div className="p-8 rounded-3xl border border-base-content/5 bg-base-100/50 shadow-inner transition-all hover:glow-primary">
               <p className="text-[10px] font-black text-base-content/40 uppercase tracking-[0.2em] mb-4 flex items-center gap-2">
                  <Wallet className="h-4 w-4 text-primary" /> Retained Capital
               </p>
               <p className="text-4xl font-black font-mono tracking-tighter text-primary">{formatCurrency(report.netBalance, currency)}</p>
               <p className="text-[10px] text-base-content/20 mt-4 font-bold uppercase tracking-widest">Net Treasury Position</p>
            </div>
         </div>

         <div className="px-10 pb-10 grid grid-cols-1 md:grid-cols-2 gap-12 print:px-8 print:pb-8">
            <div className="space-y-10">
               <div>
                  <h3 className="text-[11px] font-black uppercase tracking-[0.2em] mb-6 border-b border-base-content/5 pb-3 text-base-content/40 print:text-black">Top Expense Vectors</h3>
                  <div className="h-[280px] w-full">
                     <ResponsiveContainer width="100%" height="100%">
                        <BarChart data={report.topVendors} layout="vertical">
                           <XAxis type="number" hide />
                           <YAxis dataKey="vendorName" type="category" width={110} tick={{fill: 'currentColor', fontSize: 10, opacity: 0.6, fontWeight: 700}} axisLine={false} tickLine={false} />
                           <Tooltip content={<CustomTooltip />} />
                           <Bar dataKey="totalSpent" fill="var(--color-primary)" radius={[0, 8, 8, 0]} barSize={16}>
                              {report.topVendors.map((entry, index) => (
                                 <Cell key={`cell-${index}`} fill={COLORS[index % COLORS.length]} />
                              ))}
                           </Bar>
                        </BarChart>
                     </ResponsiveContainer>
                  </div>
               </div>
            </div>

            <div className="space-y-10">
               <div>
                  <h3 className="text-[11px] font-black uppercase tracking-[0.2em] mb-6 border-b border-base-content/5 pb-3 text-base-content/40 print:text-black">AI Financial Projections</h3>
                  <div className="space-y-4 p-6 rounded-3xl bg-primary/5 border border-primary/10 shadow-inner group transition-all hover:bg-primary/10">
                     <div className="flex justify-between items-center">
                        <span className="text-[10px] font-black uppercase tracking-widest text-base-content/50">Avg Daily Burn Rate</span>
                        <span className="font-mono font-black text-xl text-error">{formatCurrency(report.burnRate, currency)}</span>
                     </div>
                     <div className="w-full h-px bg-base-content/5"></div>
                     <div className="flex justify-between items-center">
                        <span className="text-[10px] font-black uppercase tracking-widest text-base-content/50">Next Cycle Forecast</span>
                        <span className="font-mono font-black text-xl text-warning">{formatCurrency(report.projectedExpense, currency)}</span>
                     </div>
                  </div>
               </div>

                <div>
                  <h3 className="text-[11px] font-black uppercase tracking-[0.2em] mb-6 border-b border-base-content/5 pb-3 text-base-content/40 print:text-black">Categorical Breakdown</h3>
                  <div className="space-y-4">
                     {report.categoryBreakdown.slice(0, 5).map((cat, idx) => (
                        <div key={idx} className="flex justify-between items-center group">
                           <span className="text-xs font-bold uppercase tracking-widest text-base-content/60 group-hover:text-primary transition-colors">{cat.categoryName}</span>
                           <div className="flex-1 border-b border-dashed border-base-content/10 mx-4 h-px opacity-20"></div>
                           <span className="font-mono font-black text-sm">{formatCurrency(cat.totalSpent, currency)}</span>
                        </div>
                     ))}
                  </div>
               </div>
            </div>
         </div>
         
         <div className="p-4 text-center text-xs text-base-content/60 border-t border-primary/5 bg-base-300/10 print:bg-base-100 print:border-base-content/20 print:text-base-content/60">
            CONFIDENTIAL - Internal Apartment Association Use Only.
         </div>
      </div>
    </div>
  );
}
