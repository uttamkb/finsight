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
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-4 mb-8 print:hidden">
        <div>
          <h1 className="text-3xl font-bold tracking-tight inline-flex items-center gap-3">
            <FileBarChart className="h-8 w-8 text-primary" />
            Financial Auditor & GBM Report
          </h1>
          <p className="text-base-content/60 mt-2">
            Review the monthly association ledger and generate the General Body Meeting report.
          </p>
        </div>
        <button
          onClick={handleDownload}
          className="inline-flex items-center justify-center gap-2 whitespace-nowrap rounded-md text-sm font-medium ring-offset-background transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 bg-primary text-primary-content hover:bg-primary/90 h-10 px-4 py-2 shadow-lg shadow-primary/20"
        >
          <Download className="h-4 w-4" />
          Download PDF
        </button>
      </div>

      {/* Report Container */}
      <div ref={reportRef} className="bg-base-200 rounded-2xl border border-primary/10 shadow-xl overflow-hidden print:shadow-none print:border-none print:bg-base-100 print:text-base-content">
         {/* Report Header */}
         <div className="p-8 border-b border-primary/10 bg-base-300/30 print:bg-base-200 flex justify-between items-end">
            <div>
               <h2 className="text-3xl font-extrabold tracking-tight mb-2">General Body Meeting Report</h2>
               <p className="text-base-content/60 print:text-base-content/70 flex items-center gap-2">
                  <CalendarDays className="h-4 w-4" /> Statement Period: {new Date().toLocaleDateString('default', { month: 'long', year: 'numeric'})}
               </p>
            </div>
            <div className="text-right">
               <p className="text-xs uppercase tracking-widest text-base-content/60 print:text-base-content/60 mb-1">Generated By</p>
               <p className="font-semibold text-primary print:text-base-content flex items-center gap-1.5 justify-end">
                  <span className="w-2 h-2 rounded-full bg-success animate-pulse print:hidden"></span>
                  FinSight AI Auditor
               </p>
            </div>
         </div>

         {/* Executive Summary Cards */}
         <div className="grid grid-cols-1 md:grid-cols-3 gap-6 p-8 bg-base-200 print:bg-base-100 print:gap-4 print:p-4">
            <div className="p-6 rounded-xl border bg-base-300/10 print:bg-base-200 print:border-base-content/20">
               <p className="text-sm font-medium text-base-content/60 print:text-base-content/60 mb-2 flex items-center gap-2">
                  <TrendingUp className="h-4 w-4 text-success" /> Total Inflow
               </p>
               <p className="text-3xl font-bold font-mono text-success">{formatCurrency(report.totalIncome, currency)}</p>
            </div>
            <div className="p-6 rounded-xl border bg-base-300/10 print:bg-base-200 print:border-base-content/20">
               <p className="text-sm font-medium text-base-content/60 print:text-base-content/60 mb-2 flex items-center gap-2">
                  <TrendingDown className="h-4 w-4 text-destructive" /> Total Outflow
               </p>
               <p className="text-3xl font-bold font-mono text-destructive">{formatCurrency(report.totalExpense, currency)}</p>
            </div>
            <div className="p-6 rounded-xl border bg-base-300/10 print:bg-base-200 print:border-base-content/20">
               <p className="text-sm font-medium text-base-content/60 print:text-base-content/60 mb-2 flex items-center gap-2">
                  <Wallet className="h-4 w-4 text-primary" /> Net Balance
               </p>
               <p className="text-3xl font-bold font-mono text-primary">{formatCurrency(report.netBalance, currency)}</p>
            </div>
         </div>

         <div className="px-8 pb-8 grid grid-cols-1 md:grid-cols-2 gap-8 print:px-4 print:pb-4">
            {/* Left Col: Charts */}
            <div className="space-y-8">
               <div>
                  <h3 className="text-lg font-bold mb-4 border-b border-primary/10 pb-2 print:border-base-content/20">Highest Expenditures (Vendors)</h3>
                  <div className="h-[250px] w-full">
                     <ResponsiveContainer width="100%" height="100%">
                        <BarChart data={report.topVendors} layout="vertical" margin={{ top: 0, right: 0, left: -20, bottom: 0 }}>
                           <XAxis type="number" hide />
                           <YAxis dataKey="vendorName" type="category" tick={{fill: '#888', fontSize: 11}} axisLine={false} tickLine={false} />
                           <Tooltip content={<CustomTooltip />} />
                           <Bar dataKey="totalSpent" fill="var(--primary)" barSize={16} radius={[0, 4, 4, 0]}>
                              {report.topVendors.map((entry, index) => (
                                 <Cell key={`cell-${index}`} fill={COLORS[index % COLORS.length]} />
                              ))}
                           </Bar>
                        </BarChart>
                     </ResponsiveContainer>
                  </div>
               </div>
            </div>

            {/* Right Col: Ledger / Projection */}
            <div className="space-y-8">
               <div>
                  <h3 className="text-lg font-bold mb-4 border-b border-primary/10 pb-2 print:border-base-content/20">AI Financial Projections</h3>
                  <div className="space-y-4 p-4 rounded-lg bg-primary/5 border border-primary/10 print:bg-base-200 print:border-base-content/20">
                     <div className="flex justify-between items-center">
                        <span className="text-sm text-base-content/60 print:text-base-content/70">Avg. Daily Burn Rate</span>
                        <span className="font-mono font-bold text-destructive">{formatCurrency(report.burnRate, currency)}/day</span>
                     </div>
                     <div className="flex justify-between items-center">
                        <span className="text-sm text-base-content/60 print:text-base-content/70">Projected Exp. (Next Month)</span>
                        <span className="font-mono font-bold text-warning">{formatCurrency(report.projectedExpense, currency)}</span>
                     </div>
                  </div>
               </div>

                <div>
                  <h3 className="text-lg font-bold mb-4 border-b border-primary/10 pb-2 print:border-base-content/20">Category Breakdown</h3>
                  <div className="space-y-3">
                     {report.categoryBreakdown.slice(0, 5).map((cat, idx) => (
                        <div key={idx} className="flex justify-between items-center text-sm">
                           <span className="text-base-content/60 print:text-base-content/80">{cat.categoryName}</span>
                           <span className="font-mono font-medium">{formatCurrency(cat.totalSpent, currency)}</span>
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
