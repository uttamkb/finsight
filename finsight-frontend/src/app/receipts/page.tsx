"use client";

import { useState, useEffect, useCallback } from "react";
import { Receipt as ReceiptIcon, RefreshCw, Search, Filter, ChevronLeft, ChevronRight, FileText, CheckCircle2, AlertCircle, Clock, Loader2, ScrollText, X } from "lucide-react";
import { useToast } from "@/components/toast-provider";
import { formatCurrency } from "@/lib/utils";
import { apiFetch } from "@/lib/api";

interface Receipt {
  id: number;
  fileName: string;
  vendor: string;
  amount: number;
  date: string;
  driveFileId: string;
  ocrConfidence: number;
  ocrModeUsed: string;
  category: string;
  status: string;
  createdAt: string;
}

export default function ReceiptsPage() {
  const { toast } = useToast();
  const [receipts, setReceipts] = useState<Receipt[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [isSyncing, setIsSyncing] = useState(false);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [searchQuery, setSearchQuery] = useState("");
  const [currency, setCurrency] = useState("INR");
  const [showLogs, setShowLogs] = useState(false);

  const fetchReceipts = useCallback(async () => {
    setIsLoading(true);
    try {
      const searchParam = searchQuery ? `&search=${encodeURIComponent(searchQuery)}` : "";
      const response = await apiFetch(`/receipts?page=${page}&size=10${searchParam}`);
      if (response.ok) {
        const data = await response.json();
        setReceipts(data.content || []);
        setTotalPages(data.totalPages || 0);
        setTotalElements(data.totalElements || 0);
      }
    } catch (error) {
      console.error("Failed to fetch receipts:", error);
    } finally {
      setIsLoading(false);
    }
  }, [page, searchQuery]);

  useEffect(() => {
    fetchReceipts();
    // Fetch global currency preference
    apiFetch("/settings")
      .then(res => res.json())
      .then(data => {
        if (data && data.currency) {
          setCurrency(data.currency);
        }
      })
      .catch(console.error);
  }, [fetchReceipts]);

  const [syncStatus, setSyncStatus] = useState<any>(null);

  const pollSyncStatus = useCallback(() => {
    const interval = setInterval(async () => {
      try {
        const res = await apiFetch("/sync/google-drive/status");
        if (res.ok) {
          const data = await res.json();
          setSyncStatus(data);
          
          if (data.status === "SUCCESS") {
            clearInterval(interval);
            setIsSyncing(false);
            toast(`Sync Completed: ${data.processedFiles} new receipts found.`);
            fetchReceipts();
            setTimeout(() => setSyncStatus(null), 4000);
          } else if (data.status === "ERROR") {
            clearInterval(interval);
            setIsSyncing(false);
            toast(`Sync failed: ${data.message}`, "error");
            setTimeout(() => setSyncStatus(null), 5000);
          }
        }
      } catch (e) {
        clearInterval(interval);
        setIsSyncing(false);
        toast("Disconnected during sync status check.", "error");
      }
    }, 1000);
  }, [fetchReceipts, toast]);

  const handleSync = async () => {
    setIsSyncing(true);
    setSyncStatus({ stage: "INITIALIZING", message: "Starting sync request..." });
    toast("Sync sequence initiated...", "info");
    
    try {
      const response = await apiFetch("/sync/google-drive", {
        method: "POST"
      });
      if (response.ok) {
        pollSyncStatus();
      } else {
        setIsSyncing(false);
        setSyncStatus(null);
        toast("Failed to initiate sync.", "error");
      }
    } catch (error) {
      setIsSyncing(false);
      setSyncStatus(null);
      toast("Sync connection error.", "error");
    }
  };

  const statusIcons = {
    PROCESSED: <CheckCircle2 className="h-4 w-4 text-success" />,
    FAILED: <AlertCircle className="h-4 w-4 text-destructive" />,
    PENDING: <Clock className="h-4 w-4 text-warning" />
  };

  return (
    <div className="container mx-auto py-10 px-4 max-w-7xl animate-fade-in">
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-6 mb-12">
        <div className="flex items-center gap-5">
          <div className="p-3 bg-primary/10 rounded-2xl glow-primary">
            <ReceiptIcon className="h-10 w-10 text-primary" />
          </div>
          <div>
            <h1 className="text-4xl font-black tracking-tight leading-tight">Receipt Repository</h1>
            <p className="text-base-content/60 font-medium text-lg uppercase tracking-wider text-[11px] mt-1">AI-Powered Ingestion Pipeline</p>
          </div>
        </div>
        <div className="flex items-center gap-4">
          <button 
            onClick={async () => {
              setShowLogs(true);
              try {
                const res = await apiFetch("/sync/google-drive/status");
                if (res.ok) {
                  const data = await res.json();
                  setSyncStatus(data);
                }
              } catch (e) {
                console.error("Failed to fetch logs history:", e);
              }
            }}
            className="btn btn-ghost border-base-content/10 bg-base-100/50 backdrop-blur-md h-12 px-6 rounded-2xl hover:bg-base-200 transition-all font-black text-xs uppercase tracking-widest flex items-center gap-3 shadow-sm"
          >
            <ScrollText className="h-5 w-5 text-primary" />
            <span>Audit Logs</span>
          </button>
          <button 
            onClick={handleSync}
            disabled={isSyncing}
            className="btn btn-primary h-12 px-8 rounded-2xl font-black text-xs uppercase tracking-widest transition-all shadow-xl shadow-primary/30 disabled:opacity-50 flex items-center gap-3 hover:scale-105 active:scale-95"
          >
            {isSyncing ? <RefreshCw className="h-5 w-5 animate-spin" /> : <RefreshCw className="h-5 w-5" />}
            {isSyncing ? "Syncing..." : "Initiate Sync"}
          </button>
        </div>
      </div>

      {syncStatus && (
        <div className="mb-8 p-6 rounded-xl border bg-base-200 shadow-lg border-primary/30 animate-in fade-in slide-in-from-top-4">
          <div className="flex flex-col md:flex-row md:items-start justify-between gap-6">
            
            {/* Left Column: Timeline */}
            <div className="flex-1 space-y-6">
              <h3 className="font-bold text-lg flex items-center gap-2">
                Live Sync Pipeline
                {syncStatus.status === "RUNNING" && <span className="relative flex h-3 w-3"><span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-primary opacity-75"></span><span className="relative inline-flex rounded-full h-3 w-3 bg-primary"></span></span>}
              </h3>

              <div className="relative pl-6 space-y-6 before:absolute before:inset-0 before:ml-[11px] before:-translate-x-px md:before:mx-auto md:before:translate-x-0 before:h-full before:w-0.5 before:bg-gradient-to-b before:from-transparent before:via-muted before:to-transparent">
                
                {/* Stage 1: Initializing */}
                <div className="relative flex items-center gap-4">
                  <div className={`absolute -left-6 flex h-6 w-6 items-center justify-center rounded-full border-2 bg-base-100 ${["INITIALIZING", "SCANNING_FOLDERS", "PROCESS_OCR", "COMPLETED", "FAILED"].includes(syncStatus.stage) ? "border-primary text-primary" : "border-muted text-base-content/60"}`}>
                    {syncStatus.stage === "INITIALIZING" ? <Loader2 className="h-3 w-3 animate-spin" /> : <CheckCircle2 className="h-3 w-3" />}
                  </div>
                  <div>
                    <h4 className={`text-sm font-semibold ${["INITIALIZING", "SCANNING_FOLDERS", "PROCESS_OCR", "COMPLETED", "FAILED"].includes(syncStatus.stage) ? "text-base-content" : "text-base-content/60"}`}>Secure Connection Established</h4>
                    {syncStatus.stage === "INITIALIZING" && <p className="text-xs text-base-content/60">{syncStatus.message}</p>}
                  </div>
                </div>

                {/* Stage 2: Scanning Folders */}
                <div className={`relative flex items-center gap-4 transition-all duration-500 ${["SCANNING_FOLDERS", "PROCESS_OCR", "COMPLETED", "FAILED"].includes(syncStatus.stage) ? "opacity-100 translate-y-0" : "opacity-50 translate-y-2"}`}>
                  <div className={`absolute -left-6 flex h-6 w-6 items-center justify-center rounded-full border-2 bg-base-100 ${["SCANNING_FOLDERS", "PROCESS_OCR", "COMPLETED", "FAILED"].includes(syncStatus.stage) ? "border-primary text-primary" : "border-muted text-base-content/60"}`}>
                    {syncStatus.stage === "SCANNING_FOLDERS" ? <Loader2 className="h-3 w-3 animate-spin" /> : (["PROCESS_OCR", "COMPLETED", "FAILED"].includes(syncStatus.stage) ? <CheckCircle2 className="h-3 w-3" /> : <div className="h-2 w-2 rounded-full bg-base-300" />)}
                  </div>
                  <div>
                    <h4 className={`text-sm font-semibold ${["SCANNING_FOLDERS", "PROCESS_OCR", "COMPLETED", "FAILED"].includes(syncStatus.stage) ? "text-base-content" : "text-base-content/60"}`}>Recursive Folder Scan</h4>
                    {syncStatus.stage === "SCANNING_FOLDERS" && <p className="text-xs text-base-content/60">{syncStatus.message}</p>}
                    {["PROCESS_OCR", "COMPLETED"].includes(syncStatus.stage) && syncStatus.totalFiles > 0 && <p className="text-xs text-primary font-mono">Discovered {syncStatus.totalFiles} documents</p>}
                  </div>
                </div>

                {/* Stage 3: OCR Engine */}
                <div className={`relative flex items-center gap-4 transition-all duration-500 delay-100 ${["PROCESS_OCR", "COMPLETED", "FAILED"].includes(syncStatus.stage) ? "opacity-100 translate-y-0" : "opacity-50 translate-y-2"}`}>
                  <div className={`absolute -left-6 flex h-6 w-6 items-center justify-center rounded-full border-2 bg-base-100 ${["PROCESS_OCR", "COMPLETED", "FAILED"].includes(syncStatus.stage) ? (syncStatus.status === "ERROR" ? "border-destructive text-destructive" : "border-primary text-primary") : "border-muted text-base-content/60"}`}>
                    {syncStatus.stage === "PROCESS_OCR" && syncStatus.status !== "ERROR" ? <Loader2 className="h-3 w-3 animate-spin" /> : (syncStatus.stage === "FAILED" ? <AlertCircle className="h-3 w-3" /> : (["COMPLETED"].includes(syncStatus.stage) ? <CheckCircle2 className="h-3 w-3" /> : <div className="h-2 w-2 rounded-full bg-base-300" />))}
                  </div>
                  <div className="flex-1">
                    <h4 className={`text-sm font-semibold ${["PROCESS_OCR", "COMPLETED", "FAILED"].includes(syncStatus.stage) ? (syncStatus.status === "ERROR" ? "text-destructive" : "text-base-content") : "text-base-content/60"}`}>AI Extraction & Parsing</h4>
                    
                    {syncStatus.stage === "PROCESS_OCR" && (
                      <div className="mt-2 space-y-2">
                        <p className="text-xs text-base-content/60 truncate">{syncStatus.message}</p>
                        <div className="w-full bg-base-300 rounded-full h-1.5 overflow-hidden">
                          <div 
                            className="bg-primary h-1.5 rounded-full transition-all duration-300 ease-out" 
                            style={{ width: `${((syncStatus.processedFiles + syncStatus.skippedFiles + syncStatus.failedFiles) / Math.max(1, syncStatus.totalFiles)) * 100}%` }}
                          ></div>
                        </div>
                      </div>
                    )}
                    {syncStatus.status === "ERROR" && <p className="text-xs text-destructive mt-1 font-mono">{syncStatus.message}</p>}
                  </div>
                </div>

              </div>
            </div>

            {/* Right Column: Key Stats / Counters */}
            {(syncStatus.totalFiles > 0 || syncStatus.status === "COMPLETED") && (
              <div className="flex-shrink-0 w-full md:w-64 bg-base-300/20 rounded-xl border border-base-content/20/50 p-4 animate-in fade-in slide-in-from-right-4">
                <p className="text-xs font-medium text-base-content/60 uppercase tracking-wider mb-3">Live Counters</p>
                <div className="grid grid-cols-2 gap-3 mb-4">
                  <div>
                    <p className="text-xs text-base-content/60">Processed</p>
                    <p className="text-xl font-bold font-mono text-success">{syncStatus.processedFiles || 0}</p>
                  </div>
                  <div>
                    <p className="text-xs text-base-content/60">Found</p>
                    <p className="text-xl font-bold font-mono text-base-content">{syncStatus.totalFiles || 0}</p>
                  </div>
                  <div>
                    <p className="text-xs text-base-content/60">Skipped (Dupes)</p>
                    <p className="text-xl font-bold font-mono text-warning">{syncStatus.skippedFiles || 0}</p>
                  </div>
                  <div>
                    <p className="text-xs text-base-content/60">Failed</p>
                    <p className="text-xl font-bold font-mono text-destructive">{syncStatus.failedFiles || 0}</p>
                  </div>
                </div>
                {syncStatus.status === "COMPLETED" && (
                    <div className="pt-3 border-t border-base-content/20/50 flex items-center justify-center gap-2 text-success">
                        <CheckCircle2 className="h-4 w-4" />
                        <span className="text-sm font-semibold">Sync Successful</span>
                    </div>
                )}
              </div>
            )}
            
          </div>
        </div>
      )}

      {/* Sync Logs Modal */}
      {showLogs && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-base-100/80 backdrop-blur-sm animate-in fade-in duration-200">
          <div className="bg-base-200 w-full max-w-2xl max-h-[80vh] rounded-2xl border shadow-2xl flex flex-col animate-in zoom-in-95 duration-200 overflow-hidden">
            <div className="p-4 border-b flex items-center justify-between bg-base-300/30">
              <div className="flex items-center gap-2">
                <ScrollText className="h-5 w-5 text-primary" />
                <h2 className="font-bold text-lg">Detailed Sync Logs</h2>
              </div>
              <button 
                onClick={() => setShowLogs(false)}
                className="p-1 hover:bg-base-300 rounded-full transition-colors"
                id="close-logs-modal"
              >
                <X className="h-5 w-5" />
              </button>
            </div>
            <div className="flex-1 overflow-y-auto p-4 space-y-1 font-mono text-xs bg-base-300/10 min-h-[300px]">
              {!syncStatus || !syncStatus.logs || syncStatus.logs.length === 0 ? (
                <div className="flex flex-col items-center justify-center py-20 text-base-content/60 opacity-50">
                  <ScrollText className="h-10 w-10 mb-2" />
                  <p>No activity logs for current session.</p>
                </div>
              ) : (
                syncStatus.logs.map((log: string, idx: number) => {
                  let textColor = "text-base-content/60";
                  if (log.includes("SUCCESS")) textColor = "text-success";
                  if (log.includes("ERROR") || log.includes("FATAL")) textColor = "text-destructive";
                  if (log.includes("SKIP")) textColor = "text-warning";
                  if (log.includes("FINISH")) textColor = "text-primary font-bold";
                  
                  return (
                    <div key={idx} className={`py-0.5 border-b border-base-content/20/10 last:border-0 ${textColor}`}>
                      {log}
                    </div>
                  );
                })
              )}
            </div>
            <div className="p-4 border-t bg-base-300/30 text-[10px] text-base-content/60 flex justify-between items-center">
              <span>Tenant: local_tenant</span>
              <span>Live Updates Enabled</span>
            </div>
          </div>
        </div>
      )}

      {/* Stats bar */}
      <div className="grid grid-cols-1 md:grid-cols-3 gap-6 mb-10 [animation-delay:100ms]">
        <div className="glass-panel p-6 rounded-[2rem] border-primary/10 shadow-xl transition-all hover:glow-primary hover:-translate-y-1">
          <p className="text-[10px] font-black text-base-content/50 uppercase tracking-[0.2em] mb-3">Total Ingested</p>
          <p className="text-4xl font-black font-mono tracking-tighter text-primary leading-none">{totalElements}</p>
          <p className="text-[11px] text-base-content/30 mt-3 font-bold uppercase">Archived Receipts</p>
        </div>
        <div className="glass-panel p-6 rounded-[2rem] border-primary/10 shadow-xl transition-all hover:glow-secondary hover:-translate-y-1">
          <p className="text-[10px] font-black text-base-content/50 uppercase tracking-[0.2em] mb-3">Pipeline Health</p>
          <p className="text-4xl font-black font-mono tracking-tighter text-success leading-none">Healthy</p>
          <p className="text-[11px] text-base-content/30 mt-3 font-bold uppercase">Ready for Inflow</p>
        </div>
        <div className="glass-panel p-6 rounded-[2rem] border-primary/10 shadow-xl transition-all hover:glow-accent hover:-translate-y-1">
          <p className="text-[10px] font-black text-base-content/50 uppercase tracking-[0.2em] mb-3">AI Accuracy</p>
          <p className="text-4xl font-black font-mono tracking-tighter text-base-content leading-none">92%</p>
          <p className="text-[11px] text-base-content/30 mt-3 font-bold uppercase">OCR Confidence Avg</p>
        </div>
      </div>

      <div className="glass-panel rounded-[2.5rem] overflow-hidden shadow-2xl [animation-delay:200ms] border-primary/5">
        <div className="p-6 border-b border-base-content/5 bg-base-200/30 backdrop-blur flex items-center justify-between gap-6 overflow-x-auto">
          <div className="flex items-center gap-4 max-w-xl w-full bg-base-100/50 rounded-2xl px-5 h-12 border border-base-content/5 shadow-inner transition-focus-within focus-within:border-primary/40 focus-within:bg-base-100">
            <Search className="h-5 w-5 text-base-content/30" />
            <input 
              placeholder="Search vendor records, filenames, or amounts..." 
              className="bg-transparent border-none focus:ring-0 text-sm w-full font-bold placeholder:text-base-content/20"
              value={searchQuery}
              onChange={(e) => {
                setSearchQuery(e.target.value);
                setPage(0);
              }}
            />
          </div>
          <button className="btn btn-ghost h-12 rounded-2xl px-6 bg-base-100/50 border-base-content/5 flex items-center gap-3 font-black text-[10px] uppercase tracking-widest hover:bg-primary/10 hover:text-primary transition-all">
            <Filter className="h-4 w-4" />
            Advanced Filter
          </button>
        </div>

        <div className="overflow-x-auto">
          <table className="table w-full">
            <thead className="bg-base-300/40 text-base-content/50">
              <tr>
                <th className="px-8 py-5 text-[10px] font-black uppercase tracking-[0.2em]">Transaction Date</th>
                <th className="px-8 py-5 text-[10px] font-black uppercase tracking-[0.2em]">Vendor Verification</th>
                <th className="px-8 py-5 text-[10px] font-black uppercase tracking-[0.2em]">Journal Category</th>
                <th className="px-8 py-5 text-[10px] font-black uppercase tracking-[0.2em] text-right">Amount</th>
                <th className="px-8 py-5 text-[10px] font-black uppercase tracking-[0.2em]">Extraction Intelligence</th>
                <th className="px-8 py-5 text-[10px] font-black uppercase tracking-[0.2em]">Status</th>
                <th className="px-8 py-5"></th>
              </tr>
            </thead>
            <tbody className="divide-y divide-base-content/5 font-medium">
              {isLoading ? (
                [...Array(5)].map((_, i) => (
                  <tr key={i} className="animate-pulse">
                    <td colSpan={7} className="px-6 py-4 h-16 bg-base-300/5"></td>
                  </tr>
                ))
              ) : receipts.length === 0 ? (
                <tr>
                  <td colSpan={7} className="px-6 py-12 text-center">
                    <div className="flex flex-col items-center gap-3 text-base-content/60">
                      <FileText className="h-12 w-12 opacity-20" />
                      <p>No receipts found. Trigger a sync to get started.</p>
                    </div>
                  </td>
                </tr>
              ) : (
                receipts.map((r) => (
                  <tr key={r.id} className="hover:bg-primary/5 transition-all group">
                    <td className="px-8 py-6 whitespace-nowrap font-mono text-xs font-bold text-base-content/60 group-hover:text-primary transition-colors">{r.date || "N/A"}</td>
                    <td className="px-8 py-6 whitespace-nowrap">
                      <div className="font-bold text-base group-hover:text-base-content transition-colors">{r.vendor || "Unknown"}</div>
                      <div className="text-[10px] text-base-content/30 font-bold uppercase tracking-widest mt-0.5 truncate max-w-[180px]">{r.fileName}</div>
                    </td>
                    <td className="px-8 py-6 whitespace-nowrap">
                      <span className="px-3 py-1.5 rounded-xl bg-primary/10 text-primary text-[10px] font-black uppercase tracking-wider border border-primary/10">
                        {r.category || "General Expense"}
                      </span>
                    </td>
                    <td className="px-8 py-6 whitespace-nowrap text-right font-mono font-black text-lg text-primary">
                      {formatCurrency(r.amount, currency)}
                    </td>
                    <td className="px-8 py-6 whitespace-nowrap">
                      <div className="flex flex-col gap-2">
                        <span className="text-[9px] font-black text-base-content/40 uppercase tracking-[0.1em]">
                          {r.ocrModeUsed} Intelligence
                        </span>
                        <div className="flex items-center gap-2">
                          <div className="w-20 h-1.5 bg-base-300 rounded-full overflow-hidden shadow-inner">
                            <div 
                              className={`h-full rounded-full transition-all duration-500 ${r.ocrConfidence > 0.85 ? "bg-success" : r.ocrConfidence > 0.6 ? "bg-warning" : "bg-error"}`}
                              style={{ width: `${r.ocrConfidence * 100}%` }}
                            />
                          </div>
                          <span className="text-[10px] font-black font-mono">{(r.ocrConfidence * 100).toFixed(0)}%</span>
                        </div>
                      </div>
                    </td>
                    <td className="px-8 py-6 whitespace-nowrap capitalize">
                      <div className="flex items-center gap-2">
                        {statusIcons[r.status as keyof typeof statusIcons] || statusIcons.PENDING}
                        <span className="font-bold text-xs uppercase tracking-widest">{r.status.toLowerCase()}</span>
                      </div>
                    </td>
                    <td className="px-8 py-6 whitespace-nowrap text-right">
                      <button 
                        className="btn btn-ghost btn-xs h-8 px-4 border border-base-content/5 rounded-xl font-bold uppercase text-[9px] tracking-widest hover:bg-primary hover:text-primary-content hover:border-primary transition-all active:scale-95"
                        onClick={() => window.open(`https://drive.google.com/file/d/${r.driveFileId}/view`, '_blank')}
                      >
                        Source
                      </button>
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>

        {totalPages > 1 && (
          <div className="p-4 border-t bg-base-300/10 flex items-center justify-between">
            <p className="text-xs text-base-content/60">Page {page + 1} of {totalPages}</p>
            <div className="flex items-center gap-2">
              <button 
                onClick={() => setPage(p => Math.max(0, p - 1))}
                disabled={page === 0}
                className="p-1.5 rounded-lg border hover:bg-base-100 disabled:opacity-50"
              >
                <ChevronLeft className="h-4 w-4" />
              </button>
              <button 
                onClick={() => setPage(p => Math.min(totalPages - 1, p + 1))}
                disabled={page === totalPages - 1}
                className="p-1.5 rounded-lg border hover:bg-base-100 disabled:opacity-50"
              >
                <ChevronRight className="h-4 w-4" />
              </button>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
