"use client";

import { useState, useEffect, useCallback } from "react";
import { CopyCheck, AlertTriangle, AlertCircle, Loader2, Download, Filter, CheckCheck } from "lucide-react";
import { useToast } from "@/components/toast-provider";
import { formatCurrency } from "@/lib/utils";
import { API_BASE_URL } from "@/lib/constants";

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
}

const ISSUE_TYPES = [
  { value: "", label: "All Types" },
  { value: "BANK_NO_RECEIPT", label: "Bank No Receipt" },
  { value: "RECEIPT_NO_BANK", label: "Receipt No Bank" },
  { value: "AMOUNT_MISMATCH", label: "Amount Mismatch" },
  { value: "DATE_MISMATCH", label: "Date Mismatch" },
];

function ScoreBadge({ score }: { score: number | null }) {
  if (score === null || score === undefined) return <span className="text-muted-foreground text-xs">—</span>;
  const pct = Math.round(score);
  const color = pct >= 80 ? "text-emerald-400 bg-emerald-500/10 border-emerald-500/30"
              : pct >= 50 ? "text-amber-400 bg-amber-500/10 border-amber-500/30"
              : "text-muted-foreground bg-muted/30 border-muted/30";
  return (
    <span className={`px-2 py-0.5 rounded-full text-[10px] font-bold border ${color}`}>
      {pct}/100
    </span>
  );
}

function MatchTypeBadge({ type }: { type: string | null }) {
  if (!type) return null;
  const color = type === "EXACT" ? "text-emerald-400 border-emerald-500/30 bg-emerald-500/10"
              : type === "FUZZY" ? "text-amber-400 border-amber-500/30 bg-amber-500/10"
              : "text-muted-foreground border-muted/30 bg-muted/10";
  return (
    <span className={`px-2 py-0.5 rounded-full text-[10px] font-bold border ${color}`}>
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
      const url = `${API_BASE_URL}/reconciliation/audit-trail${filter ? `?issueType=${filter}` : ""}`;
      const res = await fetch(url);
      if (res.ok) {
        const data = await res.json();
        setAudits(Array.isArray(data) ? data : data.content || []);
      }

      const statsRes = await fetch(`${API_BASE_URL}/reconciliation/audit-trail/statistics`);
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
    fetch(`${API_BASE_URL}/settings`)
      .then(res => res.json())
      .then(data => { if (data?.currency) setCurrency(data.currency); })
      .catch(console.error);
  }, [fetchAudits, issueTypeFilter]);

  const handleResolve = async (id: number) => {
    setResolvingId(id);
    try {
      const res = await fetch(`${API_BASE_URL}/reconciliation/audit-trail/${id}/resolve`, {
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

  const handleExportCsv = () => {
    window.location.href = `${API_BASE_URL}/reconciliation/audit-trail/export`;
  };

  const unresolvedAudits = audits.filter(a => !a.resolved);

  return (
    <div className="container mx-auto py-10 px-4 max-w-7xl animate-fade-in">
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-4 mb-8">
        <div>
          <h1 className="text-3xl font-bold tracking-tight inline-flex items-center gap-3">
            <CopyCheck className="h-8 w-8 text-primary" />
            Reconciliation Engine
          </h1>
          <p className="text-muted-foreground mt-2">
            Review and resolve flagged discrepancies. Similarity scoring helps identify fuzzy matches.
          </p>
        </div>
        <button
          onClick={handleExportCsv}
          className="inline-flex items-center gap-2 px-4 py-2 rounded-lg bg-primary/10 text-primary border border-primary/20 text-sm font-medium hover:bg-primary/20 transition-colors"
        >
          <Download className="h-4 w-4" />
          Export CSV
        </button>
      </div>

      {/* Stats */}
      <div className="grid grid-cols-1 md:grid-cols-2 gap-4 mb-6">
        <div className="p-4 rounded-xl border bg-card/50 backdrop-blur-sm border-destructive/20 transition-all hover:scale-[1.02]">
          <p className="text-xs font-medium text-muted-foreground uppercase tracking-wider mb-1">Attention Required</p>
          <div className="flex items-center gap-2">
            <AlertTriangle className="h-5 w-5 text-destructive" />
            <p className="text-3xl font-bold text-destructive">{stats.unresolved}</p>
          </div>
        </div>
        <div className="p-4 rounded-xl border bg-card/50 backdrop-blur-sm border-emerald-500/20 transition-all hover:scale-[1.02]">
          <p className="text-xs font-medium text-muted-foreground uppercase tracking-wider mb-1">Manually Resolved</p>
          <p className="text-3xl font-bold text-emerald-500">{stats.resolved}</p>
        </div>
      </div>

      {/* Filter bar */}
      <div className="flex items-center gap-3 mb-4">
        <Filter className="h-4 w-4 text-muted-foreground" />
        <label className="text-sm text-muted-foreground">Filter by type:</label>
        <select
          value={issueTypeFilter}
          onChange={e => setIssueTypeFilter(e.target.value)}
          className="text-sm rounded-lg bg-card border border-border px-3 py-1.5 text-foreground focus:outline-none focus:ring-1 focus:ring-primary"
        >
          {ISSUE_TYPES.map(t => (
            <option key={t.value} value={t.value}>{t.label}</option>
          ))}
        </select>
      </div>

      {/* Table */}
      <div className="rounded-xl border border-destructive/20 bg-card overflow-hidden shadow-lg shadow-destructive/5">
        <div className="p-4 bg-destructive/10 border-b border-destructive/20">
          <h2 className="font-semibold text-destructive inline-flex items-center gap-2">
            <AlertCircle className="h-4 w-4" /> Audit Trail
          </h2>
        </div>
        <div className="overflow-x-auto">
          <table className="w-full text-sm text-left">
            <thead className="text-xs text-muted-foreground uppercase bg-muted/50">
              <tr>
                <th scope="col" className="px-6 py-4 font-medium">Issue Detail</th>
                <th scope="col" className="px-6 py-4 font-medium">Type</th>
                <th scope="col" className="px-6 py-4 font-medium">Bank Transaction</th>
                <th scope="col" className="px-6 py-4 font-medium">Receipt</th>
                <th scope="col" className="px-6 py-4 font-medium text-center">Score / Match</th>
                <th scope="col" className="px-6 py-4 font-medium text-center">Action</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-primary/5">
              {isLoading ? (
                <tr>
                  <td colSpan={6} className="px-6 py-10 text-center text-muted-foreground">
                    <Loader2 className="h-6 w-6 animate-spin mx-auto mb-2" />
                    Loading exceptions...
                  </td>
                </tr>
              ) : unresolvedAudits.length === 0 ? (
                <tr>
                  <td colSpan={6} className="px-6 py-10 text-center text-muted-foreground">
                    <CopyCheck className="h-8 w-8 mx-auto mb-3 text-emerald-500/50" />
                    <p className="text-emerald-500 font-medium text-lg">All caught up!</p>
                    <p>No unresolved reconciliation issues found.</p>
                  </td>
                </tr>
              ) : (
                unresolvedAudits.map((audit) => (
                  <tr key={audit.id} className="hover:bg-muted/30 transition-colors">
                    <td className="px-6 py-4 max-w-[200px]">
                      <span className="text-xs font-medium text-amber-500 block mb-1">
                        {new Date(audit.createdAt).toLocaleDateString()}
                      </span>
                      <span className="text-xs text-muted-foreground line-clamp-2">{audit.issueDescription}</span>
                    </td>
                    <td className="px-6 py-4 whitespace-nowrap">
                      <span className="px-2.5 py-1 rounded-full bg-destructive/10 text-destructive text-[10px] font-bold tracking-wide border border-destructive/20">
                        {audit.issueType?.replace(/_/g, ' ') || 'UNMATCHED'}
                      </span>
                    </td>
                    <td className="px-6 py-4">
                      {audit.transaction ? (
                        <div className="rounded-md border p-2 bg-muted/20 border-dotted border-muted-foreground/30">
                          <div className="font-medium text-xs truncate max-w-[150px]" title={audit.transaction.description}>{audit.transaction.description}</div>
                          <div className="text-xs mt-1 text-muted-foreground flex justify-between">
                            <span>{audit.transaction.txDate}</span>
                            <span className="font-mono text-destructive">-{formatCurrency(audit.transaction.amount, currency)}</span>
                          </div>
                        </div>
                      ) : (
                        <span className="text-muted-foreground text-xs italic opacity-50">None</span>
                      )}
                    </td>
                    <td className="px-6 py-4">
                      {audit.receipt ? (
                        <div className="rounded-md border p-2 bg-muted/20 border-dotted border-muted-foreground/30">
                          <div className="font-medium text-xs truncate max-w-[150px]" title={audit.receipt.vendor}>{audit.receipt.vendor}</div>
                          <div className="text-xs mt-1 text-muted-foreground flex justify-between">
                            <span>{audit.receipt.date}</span>
                            <span className="font-mono text-emerald-500">+{formatCurrency(audit.receipt.amount, currency)}</span>
                          </div>
                        </div>
                      ) : (
                        <span className="text-muted-foreground text-xs italic opacity-50">None</span>
                      )}
                    </td>
                    <td className="px-6 py-4 whitespace-nowrap text-center">
                      <div className="flex flex-col items-center gap-1">
                        <ScoreBadge score={audit.similarityScore} />
                        <MatchTypeBadge type={audit.matchType} />
                      </div>
                    </td>
                    <td className="px-6 py-4 whitespace-nowrap text-center">
                      <button
                        onClick={() => handleResolve(audit.id)}
                        disabled={resolvingId === audit.id}
                        className="inline-flex items-center gap-1.5 text-xs font-medium px-3 py-1.5 rounded-lg bg-emerald-500/10 text-emerald-400 border border-emerald-500/20 hover:bg-emerald-500/20 transition-colors disabled:opacity-50"
                      >
                        {resolvingId === audit.id ? (
                          <Loader2 className="h-3 w-3 animate-spin" />
                        ) : (
                          <CheckCheck className="h-3 w-3" />
                        )}
                        Resolve
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
