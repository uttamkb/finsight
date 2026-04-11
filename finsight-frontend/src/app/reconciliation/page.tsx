"use client";

import { useState, useEffect, useCallback } from "react";
import { CopyCheck, AlertTriangle, AlertCircle, Loader2, Download, Filter, CheckCheck } from "lucide-react";
import { useToast } from "@/components/toast-provider";
import { formatCurrency } from "@/lib/utils";
import { apiFetch } from "@/lib/api";

interface AuditTrail {
  id: number;
  issueDescription: string;
  issueType: string;
  matchType: string | null;
  similarityScore: number | null;
  resolved: boolean;
  createdAt: string;
  transaction: {
    description: string;
    amount: number;
    txDate: string;
  } | null;
  receipt: {
    vendor: string;
    amount: number;
    date: string;
  } | null;
  amountScore: number;
  dateScore: number;
  vendorScore: number;
  amountReasoning: string;
  dateReasoning: string;
  vendorReasoning: string;
}

const ISSUE_TYPES = [
  { value: "", label: "All Types" },
  { value: "BANK_NO_RECEIPT", label: "Bank No Receipt" },
  { value: "RECEIPT_NO_BANK", label: "Receipt No Bank" },
  { value: "AMOUNT_MISMATCH", label: "Amount Mismatch" },
  { value: "DATE_MISMATCH", label: "Date Mismatch" },
];

function ScoreBadge({ score, amountScore, dateScore, vendorScore, amountReasoning, dateReasoning, vendorReasoning }: { score: number | null, amountScore?: number, dateScore?: number, vendorScore?: number, amountReasoning?: string, dateReasoning?: string, vendorReasoning?: string }) {
  if (score === null || score === undefined) return <span className="text-base-content/60 text-xs">—</span>;
  const pct = Math.round(score);
  const color = pct >= 80 ? "badge-success"
    : pct >= 50 ? "badge-warning"
      : "badge-ghost";
  return (
    <div className="group relative">
      <span className={`badge badge-sm font-bold ${color}`}>
        {pct}/100
      </span>

      {/* Detailed Breakdown Tooltip */}
      {(amountScore !== undefined || dateScore !== undefined || vendorScore !== undefined) && (
        <div className="absolute z-50 invisible group-hover:visible opacity-0 group-hover:opacity-100 transition-all bottom-full left-1/2 -translate-x-1/2 mb-2 w-64 p-4 bg-base-100 border border-base-content/10 shadow-xl rounded-2xl text-left">
          <div className="text-[10px] font-black uppercase tracking-widest text-base-content/40 mb-3 border-b border-base-content/5 pb-2">Confidence Breakdown</div>
          <div className="space-y-3">
            {amountScore !== undefined && amountScore !== null && (
              <div className="flex justify-between items-start gap-4">
                <div>
                  <div className="text-[11px] font-bold">Amount Score</div>
                  <div className="text-[9px] text-base-content/60 leading-tight mt-0.5">{amountReasoning || "Analyzed"}</div>
                </div>
                <div className={`text-xs font-black font-mono whitespace-nowrap ${amountScore >= 40 ? 'text-success' : amountScore > 0 ? 'text-warning' : 'text-error'}`}>{amountScore}/50</div>
              </div>
            )}
            {dateScore !== undefined && dateScore !== null && (
              <div className="flex justify-between items-start gap-4">
                <div>
                  <div className="text-[11px] font-bold">Date Score</div>
                  <div className="text-[9px] text-base-content/60 leading-tight mt-0.5">{dateReasoning || "Analyzed"}</div>
                </div>
                <div className={`text-xs font-black font-mono whitespace-nowrap ${dateScore >= 20 ? 'text-success' : dateScore > 0 ? 'text-warning' : 'text-error'}`}>{dateScore}/30</div>
              </div>
            )}
            {vendorScore !== undefined && vendorScore !== null && (
              <div className="flex justify-between items-start gap-4">
                <div>
                  <div className="text-[11px] font-bold">Vendor Score</div>
                  <div className="text-[9px] text-base-content/60 leading-tight mt-0.5">{vendorReasoning || "Analyzed"}</div>
                </div>
                <div className={`text-xs font-black font-mono whitespace-nowrap ${vendorScore >= 15 ? 'text-success' : vendorScore > 0 ? 'text-warning' : 'text-error'}`}>{vendorScore.toFixed(0)}/20</div>
              </div>
            )}
          </div>
        </div>
      )}
    </div>
  );
}

function MatchTypeBadge({ type }: { type: string | null }) {
  if (!type) return null;
  const color = type === "EXACT" ? "badge-success"
    : type === "FUZZY" ? "badge-warning"
      : "badge-ghost";
  return (
    <span className={`badge badge-sm font-bold badge-outline ${color}`}>
      {type}
    </span>
  );
}

export default function ReconciliationPage() {
  const { toast } = useToast();
  const [audits, setAudits] = useState<AuditTrail[]>([]);
  const [stats, setStats] = useState({ unresolved: 0, resolved: 0 });
  const [isLoading, setIsLoading] = useState(true);
  const [currency, setCurrency] = useState("INR");
  const [issueTypeFilter, setIssueTypeFilter] = useState("");
  const [resolvingId, setResolvingId] = useState<number | null>(null);

  const fetchAudits = useCallback(async (filter = "") => {
    setIsLoading(true);
    try {
      const url = `/reconciliation/audit-trail${filter ? `?issueType=${filter}` : ""}`;
      const res = await apiFetch(url);
      if (res.ok) {
        const data = await res.json();
        setAudits(Array.isArray(data) ? data : data.content || []);
      }

      const statsRes = await apiFetch("/reconciliation/audit-trail/statistics");
      if (statsRes.ok) {
        const statsData = await statsRes.json();
        setStats({
          unresolved: statsData.unresolvedCount,
          resolved: statsData.resolved
        });
      }
    } catch (error) {
      console.error("Failed to fetch audit trails:", error);
      toast("Error loading reconciliation data.", "error");
    } finally {
      setIsLoading(false);
    }
  }, [toast]);

  useEffect(() => {
    fetchAudits(issueTypeFilter);
    apiFetch("/settings")
      .then(res => res.json())
      .then(data => { if (data?.currency) setCurrency(data.currency); })
      .catch(console.error);
  }, [fetchAudits, issueTypeFilter]);

  const handleResolve = async (id: number) => {
    setResolvingId(id);
    try {
      const res = await apiFetch(`/reconciliation/audit-trail/${id}/resolve`, {
        method: "POST",
      });
      if (res.ok) {
        toast("Item marked as resolved.", "success");
        fetchAudits(issueTypeFilter);
      } else {
        toast("Failed to resolve. Please try again.", "error");
      }
    } catch {
      toast("Network error. Please try again.", "error");
    } finally {
      setResolvingId(null);
    }
  };

  const handleExportCsv = async () => {
    try {
      const response = await apiFetch("/reconciliation/audit-trail/export");
      if (response.ok) {
        const blob = await response.blob();
        const url = window.URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = `audit_trail_export_${new Date().toISOString().split('T')[0]}.csv`;
        document.body.appendChild(a);
        a.click();
        window.URL.revokeObjectURL(url);
        document.body.removeChild(a);
      } else {
        toast("Failed to export. Unauthorized or server error.", "error");
      }
    } catch (e) {
      toast("Connection error during export.", "error");
    }
  };

  const unresolvedAudits = audits.filter(a => !a.resolved);

  return (
    <div className="container mx-auto py-10 px-4 max-w-7xl animate-fade-in">
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-6 mb-12">
        <div className="flex items-center gap-5">
          <div className="p-3 bg-primary/10 rounded-2xl glow-primary">
            <CopyCheck className="h-10 w-10 text-primary" />
          </div>
          <div>
            <h1 className="text-4xl font-black tracking-tight leading-tight">Reconciliation Engine</h1>
            <p className="text-base-content/60 font-medium text-lg uppercase tracking-wider text-[11px] mt-1">Cross-Validation & Forensic Audit</p>
          </div>
        </div>
        <button
          onClick={handleExportCsv}
          className="btn btn-ghost border-base-content/10 bg-base-100/50 backdrop-blur-md h-12 px-8 rounded-2xl font-black text-xs uppercase tracking-widest transition-all shadow-sm hover:glow-primary hover:text-primary active:scale-95 flex items-center gap-3"
        >
          <Download className="h-5 w-5" />
          Export Audit Trail
        </button>
      </div>

      {/* Stats */}
      <div className="grid grid-cols-1 md:grid-cols-2 gap-8 mb-10 [animation-delay:100ms]">
        <div className="glass-panel p-8 rounded-[2.5rem] border-error/10 shadow-xl transition-all hover:glow-accent hover:-translate-y-1">
          <p className="text-[10px] font-black text-base-content/50 uppercase tracking-[0.2em] mb-4">Anomalies Detected</p>
          <div className="flex items-center justify-between">
            <p className="text-5xl font-black text-error font-mono tracking-tighter leading-none">{stats.unresolved}</p>
            <div className="p-4 bg-error/10 rounded-2xl animate-pulse">
              <AlertTriangle className="h-8 w-8 text-error " />
            </div>
          </div>
          <p className="text-[11px] text-base-content/40 mt-6 font-bold uppercase tracking-widest">Awaiting Human Verification</p>
        </div>
        <div className="glass-panel p-8 rounded-[2.5rem] border-success/10 shadow-xl transition-all hover:glow-secondary hover:-translate-y-1">
          <p className="text-[10px] font-black text-base-content/50 uppercase tracking-[0.2em] mb-4">Accuracy Verified</p>
          <div className="flex items-center justify-between">
            <p className="text-5xl font-black text-success font-mono tracking-tighter leading-none">{stats.resolved}</p>
            <div className="p-4 bg-success/10 rounded-2xl">
              <CheckCheck className="h-8 w-8 text-success" />
            </div>
          </div>
          <p className="text-[11px] text-base-content/40 mt-6 font-bold uppercase tracking-widest">Audit Trail Synchronized</p>
        </div>
      </div>

      {/* Filter bar */}
      <div className="flex items-center gap-3 mb-4">
        <Filter className="h-4 w-4 text-base-content/60" />
        <label className="text-sm text-base-content/60 font-medium">Filter by type:</label>
        <select
          value={issueTypeFilter}
          onChange={e => setIssueTypeFilter(e.target.value)}
          className="select select-sm select-bordered bg-base-200 text-base-content focus:select-primary"
        >
          {ISSUE_TYPES.map(t => (
            <option key={t.value} value={t.value}>{t.label}</option>
          ))}
        </select>
      </div>

      {/* Table */}
      <div className="glass-panel rounded-[2.5rem] overflow-hidden shadow-2xl [animation-delay:200ms] border-primary/5">
        <div className="p-6 border-b border-base-content/5 bg-base-200/30 backdrop-blur flex items-center justify-between">
          <div className="flex items-center gap-3">
            <div className="p-2 bg-primary/10 rounded-xl">
              <AlertCircle className="h-5 w-5 text-primary" />
            </div>
            <h2 className="text-2xl font-black uppercase tracking-tighter">Forensic Audit Trail</h2>
          </div>
          <div className="flex items-center gap-4 bg-base-100/50 p-2 px-4 rounded-2xl border border-base-content/5 shadow-inner">
            <Filter className="h-4 w-4 text-base-content/40" />
            <span className="text-[10px] font-black uppercase tracking-widest text-base-content/40">Vector Filter:</span>
            <select
              value={issueTypeFilter}
              onChange={e => setIssueTypeFilter(e.target.value)}
              className="bg-transparent border-none focus:ring-0 text-xs font-black uppercase tracking-widest text-primary cursor-pointer"
            >
              {ISSUE_TYPES.map(t => (
                <option key={t.value} value={t.value}>{t.label}</option>
              ))}
            </select>
          </div>
        </div>
        <div className="overflow-x-auto">
          <table className="table w-full">
            <thead className="bg-base-300/40 text-base-content/50 border-b border-base-content/5">
              <tr>
                <th className="px-8 py-5 text-[10px] font-black uppercase tracking-[0.2em]">Audit Timestamp</th>
                <th className="px-8 py-5 text-[10px] font-black uppercase tracking-[0.2em]">Variance Type</th>
                <th className="px-8 py-5 text-[10px] font-black uppercase tracking-[0.2em]">Financial Inflow/Outflow</th>
                <th className="px-8 py-5 text-[10px] font-black uppercase tracking-[0.2em]">Validation Records</th>
                <th className="px-8 py-5 text-[10px] font-black uppercase tracking-[0.2em] text-center">Confidence Matrix</th>
                <th className="px-8 py-5 text-[10px] font-black uppercase tracking-[0.2em] text-center">Intervention</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-base-content/5">
              {isLoading ? (
                <tr>
                  <td colSpan={6} className="px-6 py-10 text-center text-base-content/60">
                    <Loader2 className="h-6 w-6 animate-spin mx-auto mb-2" />
                    Loading exceptions...
                  </td>
                </tr>
              ) : unresolvedAudits.length === 0 ? (
                <tr>
                  <td colSpan={6} className="px-6 py-10 text-center text-base-content/60">
                    <CopyCheck className="h-8 w-8 mx-auto mb-3 text-success/50" />
                    <p className="text-success font-medium text-lg">All caught up!</p>
                    <p>No unresolved reconciliation issues found.</p>
                  </td>
                </tr>
              ) : (
                unresolvedAudits.map((audit) => (
                  <tr key={audit.id} className="hover:bg-primary/5 transition-all group">
                    <td className="px-8 py-6 max-w-[220px]">
                      <span className="text-[10px] font-black text-primary block mb-1.5 uppercase tracking-widest">
                        {new Date(audit.createdAt).toLocaleDateString()}
                      </span>
                      <span className="text-sm text-base-content/80 font-bold leading-relaxed line-clamp-2 italic">{audit.issueDescription}</span>
                    </td>
                    <td className="px-8 py-6 whitespace-nowrap">
                      <span className="px-3 py-1.5 rounded-xl bg-error/10 text-error text-[10px] font-black uppercase tracking-widest border border-error/10">
                        {audit.issueType?.replace(/_/g, ' ') || 'UNMATCHED'}
                      </span>
                    </td>
                    <td className="px-8 py-6">
                      {audit.transaction ? (
                        <div className="rounded-2xl border border-dashed p-3 bg-base-100/50 border-base-content/10 shadow-sm group-hover:border-primary/30 transition-colors">
                          <div className="font-black text-[12px] truncate max-w-[180px] uppercase tracking-tight" title={audit.transaction.description}>{audit.transaction.description}</div>
                          <div className="text-[10px] mt-2 text-base-content/50 flex justify-between items-center gap-3">
                            <span className="font-bold">{audit.transaction.txDate}</span>
                            <span className="font-mono font-black text-base text-error">-{formatCurrency(audit.transaction.amount, currency)}</span>
                          </div>
                        </div>
                      ) : (
                        <div className="h-14 flex items-center justify-center border border-dashed border-base-content/10 rounded-2xl opacity-30 text-[10px] font-black uppercase tracking-widest">MISSING RECORD</div>
                      )}
                    </td>
                    <td className="px-8 py-6">
                      {audit.receipt ? (
                        <div className="rounded-2xl border border-dashed p-3 bg-base-100/50 border-base-content/10 shadow-sm group-hover:border-success/30 transition-colors">
                          <div className="font-black text-[12px] truncate max-w-[180px] uppercase tracking-tight" title={audit.receipt.vendor}>{audit.receipt.vendor}</div>
                          <div className="text-[10px] mt-2 text-base-content/50 flex justify-between items-center gap-3">
                            <span className="font-bold">{audit.receipt.date}</span>
                            <span className="font-mono font-black text-base text-success">+{formatCurrency(audit.receipt.amount, currency)}</span>
                          </div>
                        </div>
                      ) : (
                        <div className="h-14 flex items-center justify-center border border-dashed border-base-content/10 rounded-2xl opacity-30 text-[10px] font-black uppercase tracking-widest">MISSING RECEIPT</div>
                      )}
                    </td>
                    <td className="px-8 py-6 whitespace-nowrap text-center">
                      <div className="flex flex-col items-center gap-2">
                        <ScoreBadge score={audit.similarityScore} amountScore={audit.amountScore} dateScore={audit.dateScore} vendorScore={audit.vendorScore} amountReasoning={audit.amountReasoning} dateReasoning={audit.dateReasoning} vendorReasoning={audit.vendorReasoning} />
                        <MatchTypeBadge type={audit.matchType} />
                      </div>
                    </td>
                    <td className="px-8 py-6 whitespace-nowrap text-center">
                      <button
                        onClick={() => handleResolve(audit.id)}
                        disabled={resolvingId === audit.id}
                        className="btn btn-ghost btn-sm h-10 px-6 rounded-xl border border-base-content/5 font-black uppercase text-[10px] tracking-widest hover:bg-success hover:text-success-content hover:border-success transition-all active:scale-95"
                      >
                        {resolvingId === audit.id ? (
                          <Loader2 className="h-4 w-4 animate-spin" />
                        ) : (
                          <CheckCheck className="h-4 w-4" />
                        )}
                        Authorize
                      </button>
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
