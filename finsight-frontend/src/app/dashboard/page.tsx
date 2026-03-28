"use client";

import { useState, useEffect, useCallback } from "react";
import {
   LayoutDashboard,
   DownloadCloud,
   TrendingUp,
   TrendingDown,
   Wallet,
   Flame,
   History,
   AlertCircle,
   Loader2,
   ChevronRight,
   ShieldAlert,
   Sparkles,
   Building2,
   Info
} from "lucide-react";
import {
   BarChart,
   Bar,
   LineChart,
   Line,
   XAxis,
   YAxis,
   CartesianGrid,
   Tooltip,
   ResponsiveContainer,
   Legend,
   Cell,
   PieChart,
   Pie
} from "recharts";
import { useToast } from "@/components/toast-provider";
import { formatCurrency } from "@/lib/utils";
import { apiFetch } from "@/lib/api";
import Link from "next/link";

interface DashboardStats {
   totalReceipts: number;
   totalBankTransactions: number;
   unreconciledItems: number;
   currentMonthBurnRate: number;
   totalIncome: number;
   totalExpense: number;
   expenseByCategory?: { name: string; value: number }[];
   last30DaysDailySpend?: { date: string; spend: number }[];
}

interface MonthlySummary {
   month: string;
   income: number;
   expense: number;
}

interface Projection {
   month: string;
   projectedExpense: number;
}

interface RecentSpent {
   id: number;
   txDate: string;
   description: string;
   amount: number;
   type: string;
   reconciled: boolean;
}

const COLORS = ['var(--color-primary)', 'var(--color-info)', 'var(--color-secondary)', 'var(--color-accent)', 'var(--color-neutral)', '#d946ef', '#ec4899', '#f43f5e'];

export default function DashboardPage() {
   const { toast } = useToast();
   const [stats, setStats] = useState<DashboardStats | null>(null);
   const [history, setHistory] = useState<MonthlySummary[]>([]);
   const [projections, setProjections] = useState<Projection[]>([]);
   const [recentSpent, setRecentSpent] = useState<RecentSpent[]>([]);
   const [ocrStats, setOcrStats] = useState<Record<string, number>>({});
   const [categories, setCategories] = useState<any[]>([]);

   const [isLoading, setIsLoading] = useState(true);
   const [selectedAccountType, setSelectedAccountType] = useState('MAINTENANCE');
   const [currency, setCurrency] = useState("INR");
   const [unresolvedAnomalies, setUnresolvedAnomalies] = useState<number>(0);
   const [infoModal, setInfoModal] = useState<{title: string, content: string} | null>(null);

   const fetchData = useCallback(async () => {
      setIsLoading(true);
      try {
         const now = new Date();
         const firstDay = new Date(now.getFullYear(), now.getMonth(), 1).toISOString().split('T')[0];
         const lastDay = new Date(now.getFullYear(), now.getMonth() + 1, 0).toISOString().split('T')[0];

         const [statsRes, historyRes, projRes, recentRes, settingsRes, auditStatsRes, ocrStatsRes, catRes] = await Promise.all([
            apiFetch(`/dashboard/stats?accountType=${selectedAccountType}`),
            apiFetch(`/dashboard/history?accountType=${selectedAccountType}`),
            apiFetch("/dashboard/projections"),
            apiFetch(`/statements/transactions?page=0&size=5&accountType=${selectedAccountType}&type=DEBIT&startDate=${firstDay}&endDate=${lastDay}`),
            apiFetch("/settings"),
            apiFetch("/reconciliation/audit-trail/statistics"),
            apiFetch("/insights/ocr-stats"),
            apiFetch(`/insights/category-spending?accountType=${selectedAccountType}`)
         ]);

         if (statsRes.ok) setStats(await statsRes.json());
         if (historyRes.ok) setHistory(await historyRes.json());
         if (projRes.ok) setProjections(await projRes.json());
         if (recentRes.ok) {
            const data = await recentRes.json();
            setRecentSpent(data.content || []);
         }
         if (settingsRes.ok) {
            const data = await settingsRes.json();
            if (data.currency) setCurrency(data.currency);
         }
         if (auditStatsRes.ok) {
            const auditData = await auditStatsRes.json();
            setUnresolvedAnomalies(auditData.unresolvedCount || 0);
         }
         if (ocrStatsRes.ok) setOcrStats(await ocrStatsRes.json());
         if (catRes.ok) setCategories(await catRes.json());

      } catch (error) {
         console.error("Dashboard fetch error:", error);
         toast("Failed to refresh dashboard metrics.", "error");
      } finally {
         setIsLoading(false);
      }
   }, [selectedAccountType, toast]);

   useEffect(() => {
      fetchData();
   }, [fetchData]);

   if (isLoading || !stats) {
      return (
         <div className="flex flex-col items-center justify-center min-h-[60vh]">
            <Loader2 className="h-10 w-10 animate-spin text-primary mb-4" />
            <p className="text-base-content/60 animate-pulse">Initializing Financial Watchdog Dashboard...</p>
         </div>
      );
   }

   const chartHistory = history.map(h => ({
      name: h.month,
      income: h.income,
      expense: h.expense
   }));

   const projectionData = [
      ...(history.length > 0 ? [{ name: history[history.length - 1].month, projected: history[history.length - 1].expense }] : []),
      ...projections.map(p => ({
         name: p.month,
         projected: p.projectedExpense
      }))
   ];

   return (
      <>
         <div className="container mx-auto py-10 px-4 max-w-7xl animate-fade-in">
            <div className="flex flex-col md:flex-row md:items-center justify-between gap-4 mb-8">
            <div>
               <h1 className="text-3xl font-bold tracking-tight inline-flex items-center gap-3">
                  <LayoutDashboard className="h-8 w-8 text-primary" />
                  Financial Dashboard
               </h1>
               <p className="text-base-content/60 mt-2">
                  Real-time oversight of association inflows, outflows, and predictive projections.
               </p>
            </div>
            <button
               onClick={() => {
                  toast("Generating system report...", "info");
                  window.print();
               }}
               className="btn btn-primary btn-sm h-10 px-4 gap-2 whitespace-nowrap font-medium transition-colors shadow-lg shadow-primary/20"
            >
               <DownloadCloud className="h-4 w-4" />
               Export Ledger
            </button>
         </div>

         <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6 mb-10 [animation-delay:100ms] animate-fade-in">
            <div className="glass-panel p-6 rounded-[2rem] border-success/20 shadow-xl transition-all hover:glow-secondary hover:-translate-y-1">
               <div className="flex items-center justify-between mb-4">
                  <div className="flex items-center gap-2">
                     <p className="text-[10px] font-black text-base-content/60 uppercase tracking-widest">Monthly Inflow</p>
                     <button type="button" onClick={(e) => { e.preventDefault(); e.stopPropagation(); setInfoModal({ title: 'Monthly Inflow', content: 'Sum of all confirmed CREDIT bank transactions recorded in the current calendar month.' }); }} className="hover:text-primary transition-colors focus:outline-none z-10 relative">
                        <Info className="h-4 w-4 text-base-content/40 hover:text-primary transition-colors cursor-pointer" />
                     </button>
                  </div>
                  <div className="p-2 bg-success/10 rounded-xl">
                     <TrendingUp className="h-5 w-5 text-success" />
                  </div>
               </div>
               <p className="text-3xl font-black text-success font-mono tracking-tighter leading-none">{formatCurrency(stats.totalIncome, currency)}</p>
               <p className="text-[11px] text-base-content/40 mt-3 font-bold uppercase tracking-wide">Current Month Active</p>
            </div>

            <div className="glass-panel p-6 rounded-[2rem] border-error/20 shadow-xl transition-all hover:glow-accent hover:-translate-y-1">
               <div className="flex items-center justify-between mb-4">
                  <div className="flex items-center gap-2">
                     <p className="text-[10px] font-black text-base-content/60 uppercase tracking-widest">Monthly Outflow</p>
                     <button type="button" onClick={(e) => { e.preventDefault(); e.stopPropagation(); setInfoModal({ title: 'Monthly Outflow', content: 'Sum of all confirmed DEBIT bank transactions recorded in the current calendar month.' }); }} className="hover:text-primary transition-colors focus:outline-none z-10 relative">
                        <Info className="h-4 w-4 text-base-content/40 hover:text-primary transition-colors cursor-pointer" />
                     </button>
                  </div>
                  <div className="p-2 bg-error/10 rounded-xl">
                     <TrendingDown className="h-5 w-5 text-error" />
                  </div>
               </div>
               <p className="text-3xl font-black text-error font-mono tracking-tighter leading-none">{formatCurrency(stats.totalExpense, currency)}</p>
               <p className="text-[11px] text-base-content/40 mt-3 font-bold uppercase tracking-wide">Receipts + Bank Debits</p>
            </div>

            <div className="glass-panel p-6 rounded-[2rem] border-primary/20 shadow-xl transition-all hover:glow-primary hover:-translate-y-1">
               <div className="flex items-center justify-between mb-4">
                  <div className="flex items-center gap-2">
                     <p className="text-[10px] font-black text-base-content/60 uppercase tracking-widest">Daily Burn Rate</p>
                     <button type="button" onClick={(e) => { e.preventDefault(); e.stopPropagation(); setInfoModal({ title: 'Daily Burn Rate', content: 'Calculated as the average monthly debit total over the last 3 completed months.' }); }} className="hover:text-primary transition-colors focus:outline-none z-10 relative">
                        <Info className="h-4 w-4 text-base-content/40 hover:text-primary transition-colors cursor-pointer" />
                     </button>
                  </div>
                  <div className="p-2 bg-primary/10 rounded-xl">
                     <Flame className="h-5 w-5 text-primary" />
                  </div>
               </div>
               <p className="text-3xl font-black text-base-content font-mono tracking-tighter leading-none">{formatCurrency(stats.currentMonthBurnRate, currency)}</p>
               <p className="text-[11px] text-base-content/40 mt-3 font-bold uppercase tracking-wide">Avg per day this month</p>
            </div>

            <Link href="/vendors" className="glass-panel p-6 rounded-[2rem] border-rose-500/20 shadow-xl hover:glow-accent hover:-translate-y-1 group relative">
               <div className="flex items-center justify-between mb-4">
                  <div className="flex items-center gap-2">
                     <p className="text-[10px] font-black text-base-content/60 uppercase tracking-widest">AI Anomalies</p>
                     <button type="button" onClick={(e) => { e.preventDefault(); e.stopPropagation(); setInfoModal({ title: 'AI Anomalies', content: 'Total count of unresolved forensic audit items flagged by the AI engine.' }); }} className="hover:text-rose-500 transition-colors focus:outline-none z-10 relative">
                        <Info className="h-4 w-4 text-base-content/40 hover:text-rose-500 transition-colors cursor-pointer" />
                     </button>
                  </div>
                  <div className="p-2 bg-rose-500/10 rounded-xl group-hover:bg-rose-500/20 transition-colors">
                     <ShieldAlert className="h-5 w-5 text-rose-500" />
                  </div>
               </div>
               <p className="text-3xl font-black text-rose-500 font-mono tracking-tighter leading-none">{unresolvedAnomalies}</p>
               <p className="text-[11px] text-base-content/40 mt-3 font-bold uppercase tracking-wide">Unresolved audit items</p>
            </Link>
         </div>

         <div className="grid grid-cols-1 lg:grid-cols-3 gap-8 mb-10 [animation-delay:200ms] animate-fade-in">
            <div className="lg:col-span-2 glass-panel p-8 rounded-[2rem] shadow-xl">
               <h3 className="text-xl font-black mb-10 flex items-center gap-3">
                  <div className="p-2 bg-primary/10 rounded-xl">
                     <History className="h-6 w-6 text-primary" />
                  </div>
                  Capital Monitoring Ledger
               </h3>
               <div className="h-[380px] w-full">
                  <ResponsiveContainer width="100%" height="100%">
                     <BarChart data={chartHistory}>
                        <defs>
                           <linearGradient id="colorInflow" x1="0" y1="0" x2="0" y2="1">
                              <stop offset="5%" stopColor="#3b82f6" stopOpacity={0.8} />
                              <stop offset="95%" stopColor="#3b82f6" stopOpacity={0.2} />
                           </linearGradient>
                           <linearGradient id="colorOutflow" x1="0" y1="0" x2="0" y2="1">
                              <stop offset="5%" stopColor="#ef4444" stopOpacity={0.8} />
                              <stop offset="95%" stopColor="#ef4444" stopOpacity={0.2} />
                           </linearGradient>
                        </defs>
                        <CartesianGrid strokeDasharray="3 3" vertical={false} stroke="rgba(255,255,255,0.05)" />
                        <XAxis dataKey="name" tick={{ fill: 'currentColor', fontSize: 11, opacity: 0.9, fontWeight: 900 }} axisLine={false} tickLine={false} />
                        <YAxis tick={{ fill: 'currentColor', fontSize: 11, opacity: 0.9, fontWeight: 900 }} axisLine={false} tickLine={false} tickFormatter={(value) => `₹${value / 1000}k`} />
                        <Tooltip
                           contentStyle={{ backgroundColor: 'var(--color-base-100)', border: '1px solid var(--color-base-content/10)', borderRadius: '16px', backdropFilter: 'blur(10px)', boxShadow: '0 10px 30px rgba(0,0,0,0.5)' }}
                           itemStyle={{ fontSize: '12px', fontWeight: 'bold' }}
                        />
                        <Legend iconType="rect" wrapperStyle={{ paddingTop: '20px', fontSize: '11px', fontWeight: '900', textTransform: 'uppercase', letterSpacing: '1px' }} />
                        <Bar dataKey="income" name="Inflow" fill="url(#colorInflow)" radius={[6, 6, 0, 0]} barSize={26} />
                        <Bar dataKey="expense" name="Outflow" fill="url(#colorOutflow)" radius={[6, 6, 0, 0]} barSize={26} />
                     </BarChart>
                  </ResponsiveContainer>
               </div>
            </div>

            <div className="glass-panel p-8 rounded-[2rem] shadow-xl border-rose-500/10">
               <h3 className="text-xl font-black mb-10 flex items-center gap-3 text-rose-500">
                  <div className="p-2 bg-rose-500/10 rounded-xl">
                     <Flame className="h-6 w-6 text-rose-500" />
                  </div>
                  Real-time Burn Rate
               </h3>
               <div className="h-[380px] w-full">
                  <ResponsiveContainer width="100%" height="100%">
                     <LineChart data={stats.last30DaysDailySpend || []}>
                        <CartesianGrid strokeDasharray="3 3" vertical={false} stroke="rgba(255,255,255,0.05)" />
                        <XAxis
                           dataKey="date"
                           tick={{ fill: 'currentColor', fontSize: 10, opacity: 0.8, fontWeight: 600 }}
                           axisLine={false}
                           tickLine={false}
                           tickFormatter={(val) => val.split('-').slice(1).join('/')}
                        />
                        <YAxis tick={{ fill: 'currentColor', fontSize: 10, opacity: 0.8, fontWeight: 600 }} axisLine={false} tickLine={false} tickFormatter={(val) => `₹${val}`} />
                        <Tooltip
                           contentStyle={{ backgroundColor: 'var(--color-base-100)', border: '1px solid var(--color-base-content/10)', borderRadius: '16px' }}
                           labelFormatter={(label) => `Journal Entry Date: ${label}`}
                           formatter={(val: any) => formatCurrency(Number(val), currency)}
                        />
                        <Line type="monotone" dataKey="spend" stroke="#f43f5e" strokeWidth={4} dot={false} activeDot={{ r: 6, fill: '#f43f5e', stroke: '#fff', strokeWidth: 2 }} />
                     </LineChart>
                  </ResponsiveContainer>
               </div>
               <div className="mt-6 p-4 bg-rose-500/5 rounded-2xl border border-rose-500/10 text-xs font-bold text-base-content/50 flex flex-col items-center gap-2">
                  <span className="flex items-center gap-2 uppercase tracking-widest"><Sparkles className="h-4 w-4 text-rose-500" /> Predictive Burn Analysis</span>
                  <span className="badge badge-error badge-sm font-black border-none text-[10px] py-2 px-3">WATCHDOG ACTIVE</span>
               </div>
            </div>
         </div>

         <div className="grid grid-cols-1 lg:grid-cols-2 gap-8 mb-10 [animation-delay:300ms] animate-fade-in">
            <div className="glass-panel p-8 rounded-[2rem] shadow-xl">
               <h3 className="text-xl font-black mb-8 flex items-center gap-3">
                  <div className="p-2 bg-primary/10 rounded-xl">
                     <Sparkles className="h-6 w-6 text-primary" />
                  </div>
                  Inflow Vector Analysis
               </h3>
               <div className="h-[280px] w-full">
                  <ResponsiveContainer width="100%" height="100%">
                     <PieChart>
                        <Pie
                           data={Object.entries(ocrStats).map(([name, value]) => ({ name: name.replace('MODE_', '').replace('_', ' '), value }))}
                           innerRadius={70}
                           outerRadius={100}
                           paddingAngle={8}
                           dataKey="value"
                        >
                           {Object.entries(ocrStats).map((entry, index) => (
                              <Cell key={`cell-${index}`} fill={COLORS[index % COLORS.length]} stroke="rgba(255,255,255,0.1)" strokeWidth={2} />
                           ))}
                        </Pie>
                        <Tooltip
                           contentStyle={{ backgroundColor: 'var(--color-base-100)', border: '1px solid var(--color-base-content/10)', borderRadius: '16px' }}
                        />
                        <Legend iconType="circle" wrapperStyle={{ fontSize: '11px', fontWeight: 'bold', textTransform: 'uppercase' }} />
                     </PieChart>
                  </ResponsiveContainer>
               </div>
            </div>

            <div className="glass-panel p-8 rounded-[2rem] shadow-xl">
               <h3 className="text-xl font-black mb-8 flex items-center gap-3">
                  <div className="p-2 bg-primary/10 rounded-xl">
                     <Building2 className="h-6 w-6 text-primary" />
                  </div>
                  Expense Categorization
               </h3>
               <div className="h-[280px] w-full">
                  <ResponsiveContainer width="100%" height="100%">
                     <BarChart data={categories.slice(0, 6)} layout="vertical">
                        <XAxis type="number" hide />
                        <YAxis dataKey="categoryName" type="category" width={110} tick={{ fill: 'currentColor', fontSize: 10, opacity: 0.6, fontWeight: 700 }} axisLine={false} tickLine={false} />
                        <Tooltip
                           contentStyle={{ backgroundColor: 'var(--color-base-100)', border: '1px solid var(--color-base-content/10)', borderRadius: '16px' }}
                           formatter={(val: any) => formatCurrency(Number(val || 0), currency)}
                        />
                        <Bar dataKey="totalSpent" fill="var(--color-primary)" radius={[0, 8, 8, 0]} barSize={16}>
                           {categories.map((entry, index) => (
                              <Cell key={`cell-${index}`} fill={COLORS[index % COLORS.length]} />
                           ))}
                        </Bar>
                     </BarChart>
                  </ResponsiveContainer>
               </div>
            </div>
         </div>

         <div className="glass-panel rounded-[2.5rem] overflow-hidden shadow-2xl [animation-delay:400ms] animate-fade-in border-primary/5">
            <div className="p-8 border-b border-base-content/5 flex items-center justify-between bg-base-200/30">
               <h2 className="text-2xl font-black flex items-center gap-4">
                  <div className="p-2 bg-primary/10 rounded-xl">
                     <Wallet className="h-6 w-6 text-primary" />
                  </div>
                  Recent Expenses
               </h2>
               <Link
                  href="/statements"
                  className="btn btn-primary btn-sm rounded-xl px-6 gap-2 font-bold uppercase tracking-widest text-[10px]"
               >
                  View All Activity <ChevronRight className="h-4 w-4" />
               </Link>
            </div>
            <div className="overflow-x-auto">
               <table className="table w-full">
                  <thead>
                     <tr className="bg-base-300/40 text-base-content/50">
                        <th className="px-8 py-5 text-[10px] font-black uppercase tracking-[0.2em]">Transaction Date</th>
                        <th className="px-8 py-5 text-[10px] font-black uppercase tracking-[0.2em]">Narration</th>
                        <th className="px-8 py-5 text-[10px] font-black uppercase tracking-[0.2em] text-right">Amount</th>
                        <th className="px-8 py-5 text-[10px] font-black uppercase tracking-[0.2em] text-center">Status</th>
                     </tr>
                  </thead>
                  <tbody className="divide-y divide-base-content/5 font-medium">
                     {recentSpent.length === 0 ? (
                        <tr>
                           <td colSpan={4} className="px-8 py-16 text-center text-base-content/40 italic font-bold text-lg">
                              System idle. Synchronize bank statements to populate watchdog.
                           </td>
                        </tr>
                     ) : (
                        recentSpent.map((txn) => (
                           <tr key={txn.id} className="hover:bg-primary/5 transition-all group">
                              <td className="px-8 py-6 whitespace-nowrap text-base-content/60 font-mono text-sm font-bold group-hover:text-primary">
                                 {txn.txDate}
                              </td>
                              <td className="px-8 py-6 font-bold max-w-[400px] truncate text-base group-hover:text-base-content">
                                 {txn.description}
                              </td>
                              <td className={`px-8 py-6 whitespace-nowrap text-right font-mono font-black text-lg ${txn.type?.toUpperCase() === 'DEBIT' ? 'text-error' : 'text-success'}`}>
                                 {txn.type?.toUpperCase() === 'DEBIT' ? '-' : '+'}{formatCurrency(txn.amount, currency)}
                              </td>
                              <td className="px-8 py-6 whitespace-nowrap text-center">
                                 {txn.reconciled ? (
                                    <span className="badge badge-success px-4 py-3 font-black text-[10px] tracking-widest shadow-lg shadow-success/20 animate-fade-in">VERIFIED</span>
                                 ) : (
                                    <span className="badge badge-warning px-4 py-3 font-black text-[10px] tracking-widest shadow-lg shadow-warning/20 opacity-70">PENDING AUDIT</span>
                                 )}
                              </td>
                           </tr>
                        ))
                     )}
                  </tbody>
               </table>
            </div>
         </div>

         </div>

         {infoModal && (
            <div className="fixed inset-0 z-[100] flex items-center justify-center bg-base-300/60 backdrop-blur-sm px-4">
               <div className="absolute inset-0 z-[100]" onClick={() => setInfoModal(null)}></div>
               <div className="bg-base-100 p-8 rounded-[2rem] shadow-2xl max-w-md border border-primary/20 animate-in zoom-in-95 duration-200 relative z-[101]">
                  <div className="flex items-center justify-between mb-6">
                     <h3 className="font-black text-2xl flex items-center gap-3">
                        <div className="p-2 bg-primary/10 rounded-xl">
                           <Info className="h-6 w-6 text-primary" />
                        </div>
                        {infoModal.title}
                     </h3>
                  </div>
                  <div className="text-base-content/70 text-base leading-relaxed font-medium">
                     {infoModal.content}
                  </div>
                  <div className="mt-8">
                     <button 
                        type="button"
                        className="btn btn-primary w-full rounded-xl font-bold tracking-widest uppercase text-xs shadow-lg shadow-primary/20 hover:-translate-y-0.5 transition-all" 
                        onClick={() => setInfoModal(null)}
                     >
                        Got it
                     </button>
                  </div>
               </div>
            </div>
         )}
      </>
   );
}
