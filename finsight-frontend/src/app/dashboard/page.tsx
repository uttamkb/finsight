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
  Building2
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
import { API_BASE_URL } from "@/lib/constants";
import Link from "next/link";

interface DashboardStats {
  totalReceipts: number;
  totalBankTransactions: number;
  unreconciledItems: number;
  currentMonthBurnRate: number;
  totalIncome: number;
  totalExpense: number;
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

const COLORS = ['#0ea5e9', '#3b82f6', '#6366f1', '#8b5cf6', '#a855f7', '#d946ef', '#ec4899', '#f43f5e'];

export default function DashboardPage() {
  const { toast } = useToast();
  const [stats, setStats] = useState<DashboardStats | null>(null);
  const [history, setHistory] = useState<MonthlySummary[]>([]);
  const [projections, setProjections] = useState<Projection[]>([]);
  const [recentSpent, setRecentSpent] = useState<RecentSpent[]>([]);
  const [ocrStats, setOcrStats] = useState<Record<string, number>>({});
  const [categories, setCategories] = useState<any[]>([]);
  
  const [isLoading, setIsLoading] = useState(true);
  const [currency, setCurrency] = useState("INR");
  const [unresolvedAnomalies, setUnresolvedAnomalies] = useState<number>(0);

  const fetchData = useCallback(async () => {
    setIsLoading(true);
    try {
      const [statsRes, historyRes, projRes, recentRes, settingsRes, auditStatsRes, ocrStatsRes, catRes] = await Promise.all([
        fetch(`${API_BASE_URL}/dashboard/stats`),
        fetch(`${API_BASE_URL}/dashboard/history`),
        fetch(`${API_BASE_URL}/dashboard/projections`),
        fetch(`${API_BASE_URL}/statements/transactions?page=0&size=5`),
        fetch(`${API_BASE_URL}/settings`),
        fetch(`${API_BASE_URL}/reconciliation/audit-trail/statistics`),
        fetch(`${API_BASE_URL}/insights/ocr-stats`),
        fetch(`${API_BASE_URL}/insights/categories/spend`)
      ]);

      if (statsRes.ok) setStats(await statsRes.json());
      if (historyRes.ok) setHistory((await historyRes.json()).reverse());
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
  }, [toast]);

  useEffect(() => {
    fetchData();
  }, [fetchData]);

  if (isLoading || !stats) {
    return (
      <div className="flex flex-col items-center justify-center min-h-[60vh]">
        <Loader2 className="h-10 w-10 animate-spin text-primary mb-4" />
        <p className="text-muted-foreground animate-pulse">Initializing Financial Watchdog Dashboard...</p>
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
    <div className="container mx-auto py-10 px-4 max-w-7xl animate-fade-in">
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-4 mb-8">
        <div>
          <h1 className="text-3xl font-bold tracking-tight inline-flex items-center gap-3">
            <LayoutDashboard className="h-8 w-8 text-primary" />
            Financial Dashboard
          </h1>
          <p className="text-muted-foreground mt-2">
            Real-time oversight of association inflows, outflows, and predictive projections.
          </p>
        </div>
        <button 
          onClick={() => {
            toast("Generating system report...", "info");
            window.print();
          }}
          className="inline-flex items-center justify-center gap-2 whitespace-nowrap rounded-md text-sm font-medium ring-offset-background transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 disabled:pointer-events-none disabled:opacity-50 bg-primary text-primary-foreground hover:bg-primary/90 h-10 px-4 py-2 shadow-lg shadow-primary/20"
        >
          <DownloadCloud className="h-4 w-4" />
          Export Ledger
        </button>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6 mb-8">
        <div className="p-6 rounded-xl border bg-card/50 backdrop-blur-sm border-emerald-500/20 shadow-lg shadow-emerald-500/5">
           <div className="flex items-center justify-between mb-2">
              <p className="text-xs font-medium text-muted-foreground uppercase tracking-wider">Monthly Inflow</p>
              <TrendingUp className="h-4 w-4 text-emerald-500" />
           </div>
           <p className="text-2xl font-bold text-emerald-500 font-mono">{formatCurrency(stats.totalIncome, currency)}</p>
           <p className="text-[10px] text-muted-foreground mt-1 underline underline-offset-2">Current Month Active</p>
        </div>

        <div className="p-6 rounded-xl border bg-card/50 backdrop-blur-sm border-destructive/20 shadow-lg shadow-destructive/5">
           <div className="flex items-center justify-between mb-2">
              <p className="text-xs font-medium text-muted-foreground uppercase tracking-wider">Monthly Outflow</p>
              <TrendingDown className="h-4 w-4 text-destructive" />
           </div>
           <p className="text-2xl font-bold text-destructive font-mono">{formatCurrency(stats.totalExpense, currency)}</p>
           <p className="text-[10px] text-muted-foreground mt-1 underline underline-offset-2">Receipts + Bank Debits</p>
        </div>

        <div className="p-6 rounded-xl border bg-card/50 backdrop-blur-sm border-primary/20 shadow-lg shadow-primary/5">
           <div className="flex items-center justify-between mb-2">
              <p className="text-xs font-medium text-muted-foreground uppercase tracking-wider">Daily Burn Rate</p>
              <Flame className="h-4 w-4 text-primary" />
           </div>
           <p className="text-2xl font-bold font-mono">{formatCurrency(stats.currentMonthBurnRate, currency)}</p>
           <p className="text-[10px] text-muted-foreground mt-1">Avg per day this month</p>
        </div>

        <Link href="/vendors" className="p-6 rounded-xl border bg-card/50 backdrop-blur-sm border-rose-500/20 shadow-lg shadow-rose-500/5 hover:border-rose-500/40 transition-all">
           <div className="flex items-center justify-between mb-2">
              <p className="text-xs font-medium text-muted-foreground uppercase tracking-wider">AI Anomalies</p>
              <ShieldAlert className="h-4 w-4 text-rose-500" />
           </div>
           <p className="text-2xl font-bold text-rose-500">{unresolvedAnomalies}</p>
           <p className="text-[10px] text-muted-foreground mt-1">Unresolved audit items</p>
        </Link>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-8 mb-8">
        <div className="lg:col-span-2 p-6 rounded-xl border bg-card/30 backdrop-blur-sm border-primary/10 shadow-xl">
           <h3 className="text-lg font-semibold mb-6 flex items-center gap-2">
              <History className="h-5 w-5 text-primary" /> Income vs Expense History
           </h3>
           <div className="h-[350px] w-full">
              <ResponsiveContainer width="100%" height="100%">
                 <BarChart data={chartHistory}>
                    <CartesianGrid strokeDasharray="3 3" vertical={false} stroke="rgba(255,255,255,0.05)" />
                    <XAxis dataKey="name" tick={{fill: '#888', fontSize: 12}} axisLine={false} tickLine={false} />
                    <YAxis tick={{fill: '#888', fontSize: 12}} axisLine={false} tickLine={false} tickFormatter={(value) => `₹${value/1000}k`} />
                    <Tooltip 
                      contentStyle={{ backgroundColor: 'rgba(15, 23, 42, 0.9)', border: '1px solid rgba(14, 165, 233, 0.2)', borderRadius: '8px' }}
                      itemStyle={{ fontSize: '12px' }}
                    />
                    <Legend iconType="circle" />
                    <Bar dataKey="income" name="Inflow" fill="#10b981" radius={[4, 4, 0, 0]} barSize={24} />
                    <Bar dataKey="expense" name="Outflow" fill="#ef4444" radius={[4, 4, 0, 0]} barSize={24} />
                 </BarChart>
              </ResponsiveContainer>
           </div>
        </div>

        <div className="p-6 rounded-xl border bg-card/30 backdrop-blur-sm border-primary/10 shadow-xl">
           <h3 className="text-lg font-semibold mb-6 flex items-center gap-2 text-amber-500">
              <TrendingUp className="h-5 w-5" /> 3-Month Projection
           </h3>
           <div className="h-[350px] w-full">
              <ResponsiveContainer width="100%" height="100%">
                 <LineChart data={projectionData}>
                    <CartesianGrid strokeDasharray="3 3" vertical={false} stroke="rgba(255,255,255,0.05)" />
                    <XAxis dataKey="name" tick={{fill: '#888', fontSize: 10}} axisLine={false} tickLine={false} />
                    <YAxis hide />
                    <Tooltip 
                      contentStyle={{ backgroundColor: 'rgba(15, 23, 42, 0.9)', border: '1px solid rgba(14, 165, 233, 0.2)', borderRadius: '8px' }}
                    />
                    <Line type="monotone" dataKey="projected" stroke="#f59e0b" strokeWidth={3} dot={{ r: 4, fill: '#f59e0b' }} activeDot={{ r: 6 }} />
                 </LineChart>
              </ResponsiveContainer>
           </div>
           <div className="mt-4 p-4 bg-amber-500/5 rounded-lg border border-amber-500/10 text-xs text-muted-foreground">
              Based on rolling average of last 6 months.
           </div>
        </div>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-8 mb-8">
        {/* OCR breakdown */}
        <div className="p-6 rounded-xl border bg-card/30 backdrop-blur-sm border-primary/10 shadow-xl">
           <h3 className="text-lg font-semibold mb-6 flex items-center gap-2">
              <Sparkles className="h-5 w-5 text-primary" /> AI Ingestion Insights
           </h3>
           <div className="h-[250px] w-full">
              <ResponsiveContainer width="100%" height="100%">
                 <PieChart>
                    <Pie
                       data={Object.entries(ocrStats).map(([name, value]) => ({ name: name.replace('MODE_', '').replace('_', ' '), value }))}
                       innerRadius={60}
                       outerRadius={80}
                       paddingAngle={5}
                       dataKey="value"
                    >
                       {Object.entries(ocrStats).map((entry, index) => (
                          <Cell key={`cell-${index}`} fill={COLORS[index % COLORS.length]} />
                       ))}
                    </Pie>
                    <Tooltip 
                       contentStyle={{ backgroundColor: 'rgba(15, 23, 42, 0.9)', border: '1px solid rgba(14, 165, 233, 0.2)', borderRadius: '8px' }}
                    />
                    <Legend iconType="circle" />
                 </PieChart>
              </ResponsiveContainer>
           </div>
        </div>

        {/* Category Spend breakdown */}
        <div className="p-6 rounded-xl border bg-card/30 backdrop-blur-sm border-primary/10 shadow-xl">
           <h3 className="text-lg font-semibold mb-6 flex items-center gap-2">
              <Building2 className="h-5 w-5 text-primary" /> Expenditure by Category
           </h3>
           <div className="h-[250px] w-full">
              <ResponsiveContainer width="100%" height="100%">
                 <BarChart data={categories.slice(0, 6)} layout="vertical">
                    <XAxis type="number" hide />
                    <YAxis dataKey="categoryName" type="category" width={100} tick={{fill: '#888', fontSize: 11}} axisLine={false} tickLine={false} />
                    <Tooltip 
                       contentStyle={{ backgroundColor: 'rgba(15, 23, 42, 0.9)', border: '1px solid rgba(14, 165, 233, 0.2)', borderRadius: '8px' }}
                       formatter={(val: any) => formatCurrency(Number(val || 0), currency)}
                    />
                    <Bar dataKey="totalSpent" fill="var(--primary)" radius={[0, 4, 4, 0]} barSize={15}>
                       {categories.map((entry, index) => (
                          <Cell key={`cell-${index}`} fill={COLORS[index % COLORS.length]} />
                       ))}
                    </Bar>
                 </BarChart>
              </ResponsiveContainer>
           </div>
        </div>
      </div>

      <div className="rounded-xl border border-primary/10 bg-card overflow-hidden shadow-xl">
         <div className="p-6 border-b border-primary/10 flex items-center justify-between">
            <h2 className="text-xl font-bold flex items-center gap-2">
               <Wallet className="h-5 w-5 text-primary" />
               Latest Spending Watchdog
            </h2>
            <Link 
               href="/statements"
               className="text-xs font-semibold text-primary hover:underline underline-offset-4 flex items-center gap-1"
            >
               View All <ChevronRight className="h-3 w-3" />
            </Link>
         </div>
         <div className="overflow-x-auto">
            <table className="w-full text-sm text-left">
               <thead className="text-xs text-muted-foreground uppercase bg-muted/50">
                  <tr>
                     <th scope="col" className="px-6 py-4 font-medium">Date</th>
                     <th scope="col" className="px-6 py-4 font-medium">Description</th>
                     <th scope="col" className="px-6 py-4 font-medium text-right">Amount</th>
                     <th scope="col" className="px-6 py-4 font-medium text-center">Status</th>
                  </tr>
               </thead>
               <tbody className="divide-y divide-primary/5">
                  {recentSpent.length === 0 ? (
                     <tr>
                        <td colSpan={4} className="px-6 py-10 text-center text-muted-foreground italic">
                           No transactions found. Synchronize your accounts to start monitoring.
                        </td>
                     </tr>
                  ) : (
                    recentSpent.map((txn) => (
                      <tr key={txn.id} className="hover:bg-muted/30 transition-colors">
                         <td className="px-6 py-4 whitespace-nowrap text-muted-foreground font-mono text-xs">
                            {txn.txDate}
                         </td>
                         <td className="px-6 py-4 font-medium max-w-[300px] truncate">
                            {txn.description}
                         </td>
                         <td className={`px-6 py-4 whitespace-nowrap text-right font-mono font-bold ${txn.type === 'DEBIT' ? 'text-destructive' : 'text-emerald-500'}`}>
                            {txn.type === 'DEBIT' ? '-' : '+'}{formatCurrency(txn.amount, currency)}
                         </td>
                         <td className="px-6 py-4 whitespace-nowrap text-center">
                            {txn.reconciled ? (
                               <span className="text-[10px] bg-emerald-500/10 text-emerald-500 px-2 py-1 rounded-full border border-emerald-500/20 font-bold tracking-tight">VERIFIED</span>
                            ) : (
                               <span className="text-[10px] bg-amber-500/10 text-amber-500 px-2 py-1 rounded-full border border-amber-500/20 font-bold tracking-tight">UNAUDITED</span>
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
  );
}
