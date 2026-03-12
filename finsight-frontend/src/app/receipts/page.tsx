"use client";

import { useState, useEffect, useCallback } from "react";
import { Receipt as ReceiptIcon, RefreshCw, Search, Filter, ChevronLeft, ChevronRight, FileText, CheckCircle2, AlertCircle, Clock, Loader2, ScrollText, X } from "lucide-react";
import { useToast } from "@/components/toast-provider";
import { formatCurrency } from "@/lib/utils";
import { API_BASE_URL } from "@/lib/constants";

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
      const response = await fetch(`${API_BASE_URL}/receipts?page=${page}&size=10${searchParam}`);
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
    fetch(`${API_BASE_URL}/settings`)
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
        const res = await fetch(`${API_BASE_URL}/sync/google-drive/status`);
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
      const response = await fetch(`${API_BASE_URL}/sync/google-drive`, {
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
    PROCESSED: <CheckCircle2 className="h-4 w-4 text-emerald-500" />,
    FAILED: <AlertCircle className="h-4 w-4 text-destructive" />,
    PENDING: <Clock className="h-4 w-4 text-amber-500" />
  };

  return (
    <div className="container mx-auto py-10 px-4 max-w-7xl">
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-4 mb-8">
        <div className="flex items-center gap-3">
          <div className="p-2 bg-primary/10 rounded-lg">
            <ReceiptIcon className="h-8 w-8 text-primary" />
          </div>
          <div>
            <h1 className="text-3xl font-bold tracking-tight">Receipts</h1>
            <p className="text-muted-foreground text-sm">Manage and sync association expense receipts</p>
          </div>
        </div>
        <div className="flex items-center gap-3">
          <button 
            onClick={async () => {
              setShowLogs(true);
              try {
                const res = await fetch(`${API_BASE_URL}/sync/google-drive/status`);
                if (res.ok) {
                  const data = await res.json();
                  setSyncStatus(data);
                }
              } catch (e) {
                console.error("Failed to fetch logs history:", e);
              }
            }}
            className="flex items-center gap-2 bg-muted text-muted-foreground px-4 py-2.5 rounded-xl hover:bg-muted/80 transition-all font-semibold"
            title="View Sync Logs"
          >
            <ScrollText className="h-4 w-4" />
            <span>Logs</span>
          </button>
          <button 
            onClick={handleSync}
            disabled={isSyncing}
            className="flex items-center gap-2 bg-primary text-primary-foreground px-6 py-2.5 rounded-xl hover:bg-primary/90 transition-all font-semibold shadow-lg shadow-primary/20 disabled:opacity-50"
          >
            {isSyncing ? <RefreshCw className="h-4 w-4 animate-spin" /> : <RefreshCw className="h-4 w-4" />}
            {isSyncing ? "Syncing..." : "Sync Now"}
          </button>
        </div>
      </div>

      {syncStatus && (
        <div className="mb-8 p-6 rounded-xl border bg-card shadow-lg border-primary/20 animate-in fade-in slide-in-from-top-4">
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
                  <div className={`absolute -left-6 flex h-6 w-6 items-center justify-center rounded-full border-2 bg-background ${["INITIALIZING", "SCANNING_FOLDERS", "PROCESS_OCR", "COMPLETED", "FAILED"].includes(syncStatus.stage) ? "border-primary text-primary" : "border-muted text-muted-foreground"}`}>
                    {syncStatus.stage === "INITIALIZING" ? <Loader2 className="h-3 w-3 animate-spin" /> : <CheckCircle2 className="h-3 w-3" />}
                  </div>
                  <div>
                    <h4 className={`text-sm font-semibold ${["INITIALIZING", "SCANNING_FOLDERS", "PROCESS_OCR", "COMPLETED", "FAILED"].includes(syncStatus.stage) ? "text-foreground" : "text-muted-foreground"}`}>Secure Connection Established</h4>
                    {syncStatus.stage === "INITIALIZING" && <p className="text-xs text-muted-foreground">{syncStatus.message}</p>}
                  </div>
                </div>

                {/* Stage 2: Scanning Folders */}
                <div className={`relative flex items-center gap-4 transition-all duration-500 ${["SCANNING_FOLDERS", "PROCESS_OCR", "COMPLETED", "FAILED"].includes(syncStatus.stage) ? "opacity-100 translate-y-0" : "opacity-50 translate-y-2"}`}>
                  <div className={`absolute -left-6 flex h-6 w-6 items-center justify-center rounded-full border-2 bg-background ${["SCANNING_FOLDERS", "PROCESS_OCR", "COMPLETED", "FAILED"].includes(syncStatus.stage) ? "border-primary text-primary" : "border-muted text-muted-foreground"}`}>
                    {syncStatus.stage === "SCANNING_FOLDERS" ? <Loader2 className="h-3 w-3 animate-spin" /> : (["PROCESS_OCR", "COMPLETED", "FAILED"].includes(syncStatus.stage) ? <CheckCircle2 className="h-3 w-3" /> : <div className="h-2 w-2 rounded-full bg-muted" />)}
                  </div>
                  <div>
                    <h4 className={`text-sm font-semibold ${["SCANNING_FOLDERS", "PROCESS_OCR", "COMPLETED", "FAILED"].includes(syncStatus.stage) ? "text-foreground" : "text-muted-foreground"}`}>Recursive Folder Scan</h4>
                    {syncStatus.stage === "SCANNING_FOLDERS" && <p className="text-xs text-muted-foreground">{syncStatus.message}</p>}
                    {["PROCESS_OCR", "COMPLETED"].includes(syncStatus.stage) && syncStatus.totalFiles > 0 && <p className="text-xs text-primary font-mono">Discovered {syncStatus.totalFiles} documents</p>}
                  </div>
                </div>

                {/* Stage 3: OCR Engine */}
                <div className={`relative flex items-center gap-4 transition-all duration-500 delay-100 ${["PROCESS_OCR", "COMPLETED", "FAILED"].includes(syncStatus.stage) ? "opacity-100 translate-y-0" : "opacity-50 translate-y-2"}`}>
                  <div className={`absolute -left-6 flex h-6 w-6 items-center justify-center rounded-full border-2 bg-background ${["PROCESS_OCR", "COMPLETED", "FAILED"].includes(syncStatus.stage) ? (syncStatus.status === "ERROR" ? "border-destructive text-destructive" : "border-primary text-primary") : "border-muted text-muted-foreground"}`}>
                    {syncStatus.stage === "PROCESS_OCR" && syncStatus.status !== "ERROR" ? <Loader2 className="h-3 w-3 animate-spin" /> : (syncStatus.stage === "FAILED" ? <AlertCircle className="h-3 w-3" /> : (["COMPLETED"].includes(syncStatus.stage) ? <CheckCircle2 className="h-3 w-3" /> : <div className="h-2 w-2 rounded-full bg-muted" />))}
                  </div>
                  <div className="flex-1">
                    <h4 className={`text-sm font-semibold ${["PROCESS_OCR", "COMPLETED", "FAILED"].includes(syncStatus.stage) ? (syncStatus.status === "ERROR" ? "text-destructive" : "text-foreground") : "text-muted-foreground"}`}>AI Extraction & Parsing</h4>
                    
                    {syncStatus.stage === "PROCESS_OCR" && (
                      <div className="mt-2 space-y-2">
                        <p className="text-xs text-muted-foreground truncate">{syncStatus.message}</p>
                        <div className="w-full bg-muted rounded-full h-1.5 overflow-hidden">
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
              <div className="flex-shrink-0 w-full md:w-64 bg-muted/20 rounded-xl border border-border/50 p-4 animate-in fade-in slide-in-from-right-4">
                <p className="text-xs font-medium text-muted-foreground uppercase tracking-wider mb-3">Live Counters</p>
                <div className="grid grid-cols-2 gap-3 mb-4">
                  <div>
                    <p className="text-xs text-muted-foreground">Processed</p>
                    <p className="text-xl font-bold font-mono text-emerald-500">{syncStatus.processedFiles || 0}</p>
                  </div>
                  <div>
                    <p className="text-xs text-muted-foreground">Found</p>
                    <p className="text-xl font-bold font-mono text-foreground">{syncStatus.totalFiles || 0}</p>
                  </div>
                  <div>
                    <p className="text-xs text-muted-foreground">Skipped (Dupes)</p>
                    <p className="text-xl font-bold font-mono text-amber-500">{syncStatus.skippedFiles || 0}</p>
                  </div>
                  <div>
                    <p className="text-xs text-muted-foreground">Failed</p>
                    <p className="text-xl font-bold font-mono text-destructive">{syncStatus.failedFiles || 0}</p>
                  </div>
                </div>
                {syncStatus.status === "COMPLETED" && (
                    <div className="pt-3 border-t border-border/50 flex items-center justify-center gap-2 text-emerald-500">
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
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-background/80 backdrop-blur-sm animate-in fade-in duration-200">
          <div className="bg-card w-full max-w-2xl max-h-[80vh] rounded-2xl border shadow-2xl flex flex-col animate-in zoom-in-95 duration-200 overflow-hidden">
            <div className="p-4 border-b flex items-center justify-between bg-muted/30">
              <div className="flex items-center gap-2">
                <ScrollText className="h-5 w-5 text-primary" />
                <h2 className="font-bold text-lg">Detailed Sync Logs</h2>
              </div>
              <button 
                onClick={() => setShowLogs(false)}
                className="p-1 hover:bg-muted rounded-full transition-colors"
                id="close-logs-modal"
              >
                <X className="h-5 w-5" />
              </button>
            </div>
            <div className="flex-1 overflow-y-auto p-4 space-y-1 font-mono text-xs bg-muted/10 min-h-[300px]">
              {!syncStatus || !syncStatus.logs || syncStatus.logs.length === 0 ? (
                <div className="flex flex-col items-center justify-center py-20 text-muted-foreground opacity-50">
                  <ScrollText className="h-10 w-10 mb-2" />
                  <p>No activity logs for current session.</p>
                </div>
              ) : (
                syncStatus.logs.map((log: string, idx: number) => {
                  let textColor = "text-muted-foreground";
                  if (log.includes("SUCCESS")) textColor = "text-emerald-500";
                  if (log.includes("ERROR") || log.includes("FATAL")) textColor = "text-destructive";
                  if (log.includes("SKIP")) textColor = "text-amber-500";
                  if (log.includes("FINISH")) textColor = "text-primary font-bold";
                  
                  return (
                    <div key={idx} className={`py-0.5 border-b border-border/10 last:border-0 ${textColor}`}>
                      {log}
                    </div>
                  );
                })
              )}
            </div>
            <div className="p-4 border-t bg-muted/30 text-[10px] text-muted-foreground flex justify-between items-center">
              <span>Tenant: local_tenant</span>
              <span>Live Updates Enabled</span>
            </div>
          </div>
        </div>
      )}

      {/* Stats bar */}
      <div className="grid grid-cols-1 md:grid-cols-3 gap-4 mb-6">
        <div className="p-4 rounded-xl border bg-card/50 backdrop-blur-sm border-primary/10">
          <p className="text-xs font-medium text-muted-foreground uppercase tracking-wider mb-1">Total Processed</p>
          <p className="text-2xl font-bold">{totalElements}</p>
        </div>
        <div className="p-4 rounded-xl border bg-card/50 backdrop-blur-sm border-primary/10">
          <p className="text-xs font-medium text-muted-foreground uppercase tracking-wider mb-1">Last Sync</p>
          <p className="text-2xl font-bold">Today</p>
        </div>
        <div className="p-4 rounded-xl border bg-card/50 backdrop-blur-sm border-primary/10">
          <p className="text-xs font-medium text-muted-foreground uppercase tracking-wider mb-1">Local Mode Efficiency</p>
          <p className="text-2xl font-bold text-emerald-500">92%</p>
        </div>
      </div>

      <div className="rounded-xl border bg-card overflow-hidden shadow-sm">
        <div className="p-4 border-b bg-muted/30 flex items-center justify-between">
          <div className="flex items-center gap-2 max-w-md w-full">
            <Search className="h-4 w-4 text-muted-foreground" />
            <input 
              placeholder="Search vendors or files..." 
              className="bg-transparent border-none focus:ring-0 text-sm w-full"
              value={searchQuery}
              onChange={(e) => {
                setSearchQuery(e.target.value);
                setPage(0); // Reset to first page on search
              }}
            />
          </div>
          <button className="flex items-center gap-2 text-sm font-medium text-muted-foreground hover:text-foreground px-3 py-1 bg-muted rounded-lg transition-colors">
            <Filter className="h-3.5 w-3.5" />
            Filters
          </button>
        </div>

        <div className="overflow-x-auto">
          <table className="w-full text-sm text-left">
            <thead className="text-xs text-muted-foreground uppercase bg-muted/10 border-b">
              <tr>
                <th className="px-6 py-4 font-medium">Date</th>
                <th className="px-6 py-4 font-medium">Vendor</th>
                <th className="px-6 py-4 font-medium">Category</th>
                <th className="px-6 py-4 font-medium text-right">Amount</th>
                <th className="px-6 py-4 font-medium">Mode</th>
                <th className="px-6 py-4 font-medium text-center">Confidence</th>
                <th className="px-6 py-4 font-medium">Status</th>
                <th className="px-6 py-4 font-medium"></th>
              </tr>
            </thead>
            <tbody className="divide-y border-muted/20">
              {isLoading ? (
                [...Array(5)].map((_, i) => (
                  <tr key={i} className="animate-pulse">
                    <td colSpan={7} className="px-6 py-4 h-16 bg-muted/5"></td>
                  </tr>
                ))
              ) : receipts.length === 0 ? (
                <tr>
                  <td colSpan={7} className="px-6 py-12 text-center">
                    <div className="flex flex-col items-center gap-3 text-muted-foreground">
                      <FileText className="h-12 w-12 opacity-20" />
                      <p>No receipts found. Trigger a sync to get started.</p>
                    </div>
                  </td>
                </tr>
              ) : (
                receipts.map((r) => (
                  <tr key={r.id} className="hover:bg-primary/5 transition-colors group">
                    <td className="px-6 py-4 whitespace-nowrap font-medium">{r.date || "N/A"}</td>
                    <td className="px-6 py-4 whitespace-nowrap">
                      <div className="font-semibold">{r.vendor || "Unknown"}</div>
                      <div className="text-xs text-muted-foreground truncate max-w-[150px]">{r.fileName}</div>
                    </td>
                    <td className="px-6 py-4 whitespace-nowrap">
                      <span className="px-2 py-1 rounded-full bg-primary/10 text-primary text-[10px] font-bold">
                        {r.category || "Uncategorized"}
                      </span>
                    </td>
                    <td className="px-6 py-4 whitespace-nowrap text-right font-mono font-bold text-primary">
                      {formatCurrency(r.amount, currency)}
                    </td>
                    <td className="px-6 py-4 whitespace-nowrap">
                      <span className="px-2 py-1 rounded-md bg-muted text-[10px] font-bold tracking-tight">
                        {r.ocrModeUsed}
                      </span>
                    </td>
                    <td className="px-6 py-4 whitespace-nowrap text-center">
                      <div className="flex items-center justify-center gap-1.5">
                        <div className="w-12 h-1.5 bg-muted rounded-full overflow-hidden">
                          <div 
                            className={`h-full rounded-full ${r.ocrConfidence > 0.8 ? "bg-emerald-500" : "bg-amber-500"}`}
                            style={{ width: `${r.ocrConfidence * 100}%` }}
                          />
                        </div>
                        <span className="text-[10px] font-mono">{(r.ocrConfidence * 100).toFixed(0)}%</span>
                      </div>
                    </td>
                    <td className="px-6 py-4 whitespace-nowrap capitalize">
                      <div className="flex items-center gap-2">
                        {statusIcons[r.status as keyof typeof statusIcons] || statusIcons.PENDING}
                        <span>{r.status.toLowerCase()}</span>
                      </div>
                    </td>
                    <td className="px-6 py-4 whitespace-nowrap text-right">
                      <button 
                        className="p-1 px-2 border rounded hover:bg-background transition-colors text-xs"
                        onClick={() => window.open(`https://drive.google.com/file/d/${r.driveFileId}/view`, '_blank')}
                      >
                        View
                      </button>
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>

        {totalPages > 1 && (
          <div className="p-4 border-t bg-muted/10 flex items-center justify-between">
            <p className="text-xs text-muted-foreground">Page {page + 1} of {totalPages}</p>
            <div className="flex items-center gap-2">
              <button 
                onClick={() => setPage(p => Math.max(0, p - 1))}
                disabled={page === 0}
                className="p-1.5 rounded-lg border hover:bg-background disabled:opacity-50"
              >
                <ChevronLeft className="h-4 w-4" />
              </button>
              <button 
                onClick={() => setPage(p => Math.min(totalPages - 1, p + 1))}
                disabled={page === totalPages - 1}
                className="p-1.5 rounded-lg border hover:bg-background disabled:opacity-50"
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
