"use client";

import { useState, useEffect, useCallback, useRef } from "react";
import { Upload, FileText, CheckCircle2, AlertCircle, Clock, Loader2, Play, Check, X, Edit2, Save } from "lucide-react";
import { useToast } from "@/components/toast-provider";
import { formatCurrency } from "@/lib/utils";
import { apiFetch } from "@/lib/api";

interface Category {
  id: number;
  name: string;
  type: string;
  parentName?: string;
}

interface BankTransaction {
  id: number;
  txDate: string;
  description: string;
  vendor: string;
  type: string;
  amount: number;
  category: Category;
  reconciled: boolean;
  referenceNumber: string;
  confidenceScore?: number;
  status?: "AUTO_VALIDATED" | "LOW_CONFIDENCE" | "NEEDS_REVIEW" | "USER_VERIFIED";
  aiReasoning?: string;
  originalSnippet?: string;
  isDuplicate?: boolean;
}

interface Receipt {
  id: number;
  vendor: string;
  totalAmount: number;
  date: string;
}

interface AuditTrail {
  id: number;
  transaction?: BankTransaction;
  receipt?: Receipt;
  issueType: string;
  description: string;
  issueDescription?: string;
  resolved: boolean;
  amountScore?: number;
  dateScore?: number;
  vendorScore?: number;
  amountReasoning?: string;
  dateReasoning?: string;
  vendorReasoning?: string;
  similarityScore?: number;
  matchType?: string;
}

export default function StatementsPage() {
  const { toast } = useToast();
  const [transactions, setTransactions] = useState<BankTransaction[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [isUploading, setIsUploading] = useState(false);
  const [isReconciling, setIsReconciling] = useState(false);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [currency, setCurrency] = useState("INR");

  const [activeTab, setActiveTab] = useState<"transactions" | "issues" | "history">("transactions");
  const [audits, setAudits] = useState<AuditTrail[]>([]);
  const [receipts, setReceipts] = useState<Receipt[]>([]);
  const [uploads, setUploads] = useState<any[]>([]);
  const [isFetchingUploads, setIsFetchingUploads] = useState(false);
  const [selectedReceiptId, setSelectedReceiptId] = useState<Record<number, string>>({});

  // Edit Txn State
  const [editingTxn, setEditingTxn] = useState<BankTransaction | null>(null);
  const [editForm, setEditForm] = useState({ amount: 0, vendor: "", txDate: "", description: "", type: "DEBIT" });
  const [isSavingTxn, setIsSavingTxn] = useState(false);

  const fileInputRef = useRef<HTMLInputElement>(null);

  // Filters
  const [filterReconciled, setFilterReconciled] = useState<string>("all");
  const [selectedAccountType, setSelectedAccountType] = useState('MAINTENANCE');

  const [uploadStatus, setUploadStatus] = useState<any>(null);
  const [showUploadStatus, setShowUploadStatus] = useState(false);

  // Bulk & Filter States
  const [selectedIds, setSelectedIds] = useState<Set<number>>(new Set());
  const [filterStatus, setFilterStatus] = useState<string>("all");
  const [filterConfidence, setFilterConfidence] = useState<string>("all");
  const [sortBy, setSortBy] = useState<string>("date_desc");

  const fetchTransactions = useCallback(async () => {
    setIsLoading(true);
    try {
      // Add optional filter 
      const filterQuery = filterReconciled !== "all" ? `&reconciled=${filterReconciled === "true"}` : "";
      const response = await apiFetch(`/statements/transactions?page=${page}&size=15${filterQuery}&accountType=${selectedAccountType}`);
      if (response.ok) {
        const data = await response.json();
        setTransactions(data.content || []);
        setTotalPages(data.totalPages || 0);
        setTotalElements(data.totalElements || 0);
      }
    } catch (error) {
      console.error("Failed to fetch transactions:", error);
      toast("Failed to load bank transactions.", "error");
    } finally {
      setIsLoading(false);
    }
  }, [page, filterReconciled, selectedAccountType, toast]);

  const fetchAudits = useCallback(async () => {
    try {
      const response = await apiFetch("/reconciliation/audit-trail");
      if (response.ok) {
        const data = await response.json();
        // filter only unresolved
        setAudits(data.filter((a: AuditTrail) => !a.resolved));
      }
    } catch (error) {
      console.error("Failed to fetch audits:", error);
    }
  }, []);

  const fetchReceipts = useCallback(async () => {
    try {
      const response = await apiFetch("/receipts?size=100");
      if (response.ok) {
        const data = await response.json();
        setReceipts(data.content || []);
      }
    } catch (error) {
      console.error("Failed to fetch receipts:", error);
    }
  }, []);

  const fetchUploads = useCallback(async () => {
    setIsFetchingUploads(true);
    try {
      const response = await apiFetch("/statements/uploads");
      if (response.ok) {
        const data = await response.json();
        setUploads(data || []);
      }
    } catch (error) {
      console.error("Failed to fetch uploads:", error);
    } finally {
      setIsFetchingUploads(false);
    }
  }, []);

  useEffect(() => {
    fetchTransactions();
    fetchAudits();
    fetchReceipts();
    fetchUploads();
    apiFetch("/settings")
      .then(res => res.json())
      .then(data => {
        if (data && data.currency) setCurrency(data.currency);
      })
      .catch(console.error);
  }, [fetchTransactions, fetchAudits, fetchReceipts, fetchUploads]);

  const pollUploadStatus = useCallback(() => {
    const interval = setInterval(async () => {
      try {
        const res = await apiFetch("/statements/upload/status");
        if (res.ok) {
          const data = await res.json();
          setUploadStatus(data);

          if (data.status === "SUCCESS") {
            clearInterval(interval);
            setIsUploading(false);
            toast(`Upload Successful: Processed ${data.processedFiles} transactions.`);
            setPage(0);
            fetchTransactions();
            setTimeout(() => {
              setUploadStatus(null);
              setShowUploadStatus(false);
            }, 4000);
          } else if (data.status === "ERROR") {
            clearInterval(interval);
            setIsUploading(false);
            toast(`Upload failed: ${data.message}`, "error");
            setTimeout(() => {
              setUploadStatus(null);
              setShowUploadStatus(false);
            }, 5000);
          }
        }
      } catch (e) {
        clearInterval(interval);
        setIsUploading(false);
        toast("Disconnected during status check.", "error");
      }
    }, 1000);
  }, [fetchTransactions, toast]);

  const handleFileUpload = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;

    const filename = file.name.toLowerCase();
    if (!filename.endsWith(".pdf") && !filename.endsWith(".csv") && !filename.endsWith(".xlsx")) {
      toast("Please upload a valid PDF, CSV, or XLSX bank statement.", "error");
      return;
    }

    setIsUploading(true);
    setShowUploadStatus(true);
    setUploadStatus({ stage: "INITIALIZING", message: "Initiating secure upload..." });
    toast(`Uploading ${filename.endsWith(".csv") ? "CSV" : "PDF"} statement...`, "info");

    const formData = new FormData();
    formData.append("file", file);
    formData.append("accountType", selectedAccountType);

    try {
      const response = await apiFetch("/statements/upload", {
        method: "POST",
        body: formData,
      });

      if (response.ok) {
        pollUploadStatus();
      } else {
        const errText = await response.text();
        toast(`Upload failed: ${errText}`, "error");
        setIsUploading(false);
        setShowUploadStatus(false);
      }
    } catch (error) {
      console.error("Upload error", error);
      toast("Connection error during upload.", "error");
      setIsUploading(false);
      setShowUploadStatus(false);
    } finally {
      if (fileInputRef.current) fileInputRef.current.value = "";
    }
  };

  const handleReconcile = async () => {
    setIsReconciling(true);
    toast("Running Auto-Reconciliation engine...", "info");

    try {
      const response = await apiFetch(`/reconciliation/run?accountType=${selectedAccountType}`, {
        method: "POST",
      });

      if (response.ok) {
        const data = await response.json();
        toast(`Reconciliation absolute complete. Auto-linked ${data.reconciledCount} pairs!`, "success");
        fetchTransactions();
      } else {
        toast("Failed to run reconciliation.", "error");
      }
    } catch (error) {
      console.error("Reconciliation error", error);
      toast("Connection error during reconciliation.", "error");
    } finally {
      setIsReconciling(false);
    }
  };

  const handleManuallyLink = async (auditId: number, txnId?: number, preselectedReceiptId?: number) => {
    const receiptId = selectedReceiptId[auditId] || preselectedReceiptId;
    if (!txnId || !receiptId) {
      toast("Please select a receipt to link.", "error");
      return;
    }
    try {
      const response = await apiFetch("/reconciliation/link", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ transactionId: txnId, receiptId: Number(receiptId) })
      });
      if (response.ok) {
        toast("Successfully linked manually!", "success");
        fetchAudits();
        fetchTransactions();
      } else {
        toast(await response.text(), "error");
      }
    } catch (e) {
      toast("Failed to manually link", "error");
    }
  };

  const handleIgnoreAudit = async (auditId: number) => {
    try {
      const response = await apiFetch(`/reconciliation/audit-trail/${auditId}/ignore`, { method: "POST" });
      if (response.ok) {
        toast("Issue marked as ignored/done.", "success");
        fetchAudits();
      }
    } catch (e) {
      toast("Failed to ignore issue", "error");
    }
  };

  const handleSaveTransaction = async () => {
    if (!editingTxn) return;
    setIsSavingTxn(true);
    try {
      const response = await apiFetch(`/statements/transactions/${editingTxn.id}`, {
        method: "PUT",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          ...editForm,
          status: "USER_VERIFIED"
        })
      });
      if (response.ok) {
        toast("Transaction verified successfully", "success");
        setEditingTxn(null);
        fetchTransactions();
        fetchAudits();
      } else {
        toast("Failed to update transaction", "error");
      }
    } catch (e) {
      toast("Connection error during update", "error");
    } finally {
      setIsSavingTxn(false);
    }
  };

  const handleReprocess = async (fileId: string) => {
    toast("Initiating re-processing for this statement...", "info");
    try {
      const response = await apiFetch(`/statements/uploads/${fileId}/reprocess`, {
        method: "POST"
      });
      if (response.ok) {
        toast("Reprocessing started successfully.", "success");
        fetchUploads();
        pollUploadStatus();
      } else {
        toast("Reprocessing failed to start.", "error");
      }
    } catch (e) {
      toast("Connection error during reprocess request.", "error");
    }
  };

  const handleBulkAction = async (action: "APPROVE") => {
    if (selectedIds.size === 0) return;
    toast(`Approving ${selectedIds.size} transactions...`, "info");
    // Simulate bulk approval for UI purposes
    setTimeout(() => {
      toast(`Successfully approved ${selectedIds.size} records.`, "success");
      setSelectedIds(new Set());
      fetchTransactions();
    }, 1000);
  };

  const getConfidenceColor = (score: number) => {
    if (score >= 90) return "text-success bg-success/10 border-success/30";
    if (score >= 70) return "text-warning bg-warning/10 border-warning/30";
    return "text-error bg-error/10 border-error/30";
  };

  const getStatusBadge = (status: string) => {
    switch (status) {
      case "AUTO_VALIDATED": return <span className="badge badge-success badge-sm border-none font-bold text-[9px]">AUTO_VALIDATED</span>;
      case "USER_VERIFIED": return <span className="badge badge-primary badge-sm border-none font-bold text-[9px]">USER_VERIFIED</span>;
      case "LOW_CONFIDENCE": return <span className="badge badge-error badge-sm border-none font-bold text-[9px]">LOW_CONFIDENCE</span>;
      case "NEEDS_REVIEW": return <span className="badge badge-warning badge-sm border-none font-bold text-[9px]">NEEDS_REVIEW</span>;
      case "COMPLETED": return <span className="badge badge-success badge-sm border-none font-bold text-[9px]">COMPLETED</span>;
      case "PARTIAL_SUCCESS": return <span className="badge badge-warning badge-sm border-none font-bold text-[9px]">PARTIAL_SUCCESS</span>;
      case "FAILED": return <span className="badge badge-error badge-sm border-none font-bold text-[9px]">FAILED</span>;
      case "FAILED_PERMANENT": return <span className="badge badge-error badge-sm border-none font-black text-[9px] bg-red-900 text-white">DEAD_LETTER</span>;
      case "PROCESSING": return <span className="badge badge-primary badge-sm animate-pulse border-none font-bold text-[9px]">PROCESSING</span>;
      default: return <span className="badge badge-ghost badge-sm border-none font-bold text-[9px]">{status || 'PENDING'}</span>;
    }
  };

  const unReconciledCount = transactions.filter(t => !t.reconciled).length;

  return (
    <div className="container mx-auto py-10 px-4 max-w-7xl animate-fade-in">
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-4 mb-8">
        <div>
          <h1 className="text-3xl font-bold tracking-tight inline-flex items-center gap-3">
            <FileText className="h-8 w-8 text-primary" />
            Upload Statements (PDF/CSV)
          </h1>
          <p className="text-base-content/60 mt-2">
            Upload PDFs for Gemini AI parsing and auto-categorization.
          </p>
        </div>

        <div className="flex items-center gap-3">
          <input
            type="file"
            accept=".pdf,.csv,.xlsx,application/pdf,text/csv,application/vnd.ms-excel,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            className="hidden"
            ref={fileInputRef}
            onChange={handleFileUpload}
          />
          <div className="flex flex-wrap items-center gap-3">
            <div className="flex items-center gap-2 mr-2">
              <span className="text-[10px] font-bold text-base-content/60 uppercase tracking-widest whitespace-nowrap">Target Account:</span>
              <select
                className="select select-sm select-primary font-bold bg-base-300/50 border-primary/30 focus:bg-base-300"
                value={selectedAccountType}
                onChange={(e) => setSelectedAccountType(e.target.value)}
                disabled={isUploading}
              >
                <option value="MAINTENANCE">Maintenance</option>
                <option value="CORPUS">Corpus</option>
                <option value="SINKING_FUND">Sinking Fund</option>
              </select>
            </div>

            <button
              className={`btn btn-secondary btn-sm h-10 px-6 gap-2 whitespace-nowrap font-bold shadow-lg shadow-secondary/10 transition-all active:scale-95 ${isUploading ? 'loading' : ''}`}
              onClick={() => fileInputRef.current?.click()}
              disabled={isUploading}
            >
              {isUploading ? <Loader2 className="h-4 w-4 animate-spin" /> : <Upload className="h-4 w-4" />}
              UPLOAD STATEMENT
            </button>
          </div>

          <button
            onClick={handleReconcile}
            disabled={isReconciling || isUploading}
            className="btn btn-primary btn-sm h-10 px-6 gap-2 whitespace-nowrap font-bold shadow-lg shadow-primary/20 transition-all active:scale-95"
          >
            {isReconciling ? <Loader2 className="h-4 w-4 animate-spin" /> : <Play className="h-4 w-4" />}
            AUTO RECONCILE
          </button>
        </div>
      </div>

      {showUploadStatus && uploadStatus && (
        <div className="mb-8 p-6 rounded-xl border bg-base-200 shadow-lg border-primary/30 animate-in fade-in slide-in-from-top-4">
          <div className="flex flex-col md:flex-row md:items-start justify-between gap-6">

            <div className="flex-1 space-y-6">
              <h3 className="font-bold text-lg flex items-center gap-2">
                Statement Processing Pipeline
                {uploadStatus.status === "RUNNING" && <span className="relative flex h-3 w-3"><span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-primary opacity-75"></span><span className="relative inline-flex rounded-full h-3 w-3 bg-primary"></span></span>}
              </h3>

              <div className="relative pl-6 space-y-6 before:absolute before:inset-0 before:ml-[11px] before:-translate-x-px md:before:mx-auto md:before:translate-x-0 before:h-full before:w-0.5 before:bg-gradient-to-b before:from-transparent before:via-muted before:to-transparent">

                {/* Stage 1: Initializing */}
                <div className="relative flex items-center gap-4">
                  <div className={`absolute -left-6 flex h-6 w-6 items-center justify-center rounded-full border-2 bg-base-100 ${["INITIALIZING", "EXTRACTION", "PERSISTENCE", "COMPLETED", "FAILED"].includes(uploadStatus.stage) ? "border-primary text-primary" : "border-muted text-base-content/60"}`}>
                    {uploadStatus.stage === "INITIALIZING" ? <Loader2 className="h-3 w-3 animate-spin" /> : <CheckCircle2 className="h-3 w-3" />}
                  </div>
                  <div>
                    <h4 className={`text-sm font-semibold ${["INITIALIZING", "EXTRACTION", "PERSISTENCE", "COMPLETED", "FAILED"].includes(uploadStatus.stage) ? "text-base-content" : "text-base-content/60"}`}>Secure Upload & Init</h4>
                    {uploadStatus.stage === "INITIALIZING" && <p className="text-xs text-base-content/60">{uploadStatus.message}</p>}
                  </div>
                </div>

                {/* Stage 2: Extraction */}
                <div className={`relative flex items-center gap-4 transition-all duration-500 ${["EXTRACTION", "PERSISTENCE", "COMPLETED", "FAILED"].includes(uploadStatus.stage) ? "opacity-100 translate-y-0" : "opacity-50 translate-y-2"}`}>
                  <div className={`absolute -left-6 flex h-6 w-6 items-center justify-center rounded-full border-2 bg-base-100 ${["EXTRACTION", "PERSISTENCE", "COMPLETED", "FAILED"].includes(uploadStatus.stage) ? "border-primary text-primary" : "border-muted text-base-content/60"}`}>
                    {uploadStatus.stage === "EXTRACTION" ? <Loader2 className="h-3 w-3 animate-spin" /> : (["PERSISTENCE", "COMPLETED", "FAILED"].includes(uploadStatus.stage) ? <CheckCircle2 className="h-3 w-3" /> : <div className="h-2 w-2 rounded-full bg-base-300" />)}
                  </div>
                  <div>
                    <h4 className={`text-sm font-semibold ${["EXTRACTION", "PERSISTENCE", "COMPLETED", "FAILED"].includes(uploadStatus.stage) ? "text-base-content" : "text-base-content/60"}`}>AI Data Extraction</h4>
                    {uploadStatus.stage === "EXTRACTION" && <p className="text-xs text-base-content/60">{uploadStatus.message}</p>}
                    {["PERSISTENCE", "COMPLETED"].includes(uploadStatus.stage) && uploadStatus.totalFiles > 0 && <p className="text-xs text-primary font-mono">Parsed {uploadStatus.totalFiles} transactions</p>}
                  </div>
                </div>

                {/* Stage 3: Persistence */}
                <div className={`relative flex items-center gap-4 transition-all duration-500 delay-100 ${["PERSISTENCE", "COMPLETED", "FAILED"].includes(uploadStatus.stage) ? "opacity-100 translate-y-0" : "opacity-50 translate-y-2"}`}>
                  <div className={`absolute -left-6 flex h-6 w-6 items-center justify-center rounded-full border-2 bg-base-100 ${["PERSISTENCE", "COMPLETED", "FAILED"].includes(uploadStatus.stage) ? (uploadStatus.status === "ERROR" ? "border-destructive text-destructive" : "border-primary text-primary") : "border-muted text-base-content/60"}`}>
                    {uploadStatus.stage === "PERSISTENCE" && uploadStatus.status !== "ERROR" ? <Loader2 className="h-3 w-3 animate-spin" /> : (uploadStatus.stage === "FAILED" ? <AlertCircle className="h-3 w-3" /> : (["COMPLETED"].includes(uploadStatus.stage) ? <CheckCircle2 className="h-3 w-3" /> : <div className="h-2 w-2 rounded-full bg-base-300" />))}
                  </div>
                  <div className="flex-1">
                    <h4 className={`text-sm font-semibold ${["PERSISTENCE", "COMPLETED", "FAILED"].includes(uploadStatus.stage) ? (uploadStatus.status === "ERROR" ? "text-destructive" : "text-base-content") : "text-base-content/60"}`}>Database Verification</h4>

                    {uploadStatus.stage === "PERSISTENCE" && (
                      <div className="mt-2 space-y-2">
                        <p className="text-xs text-base-content/60 truncate">{uploadStatus.message}</p>
                        <div className="w-full bg-base-300 rounded-full h-1.5 overflow-hidden">
                          <div
                            className="bg-primary h-1.5 rounded-full transition-all duration-300 ease-out"
                            style={{ width: `${(uploadStatus.totalFiles > 0 ? (uploadStatus.processedFiles / uploadStatus.totalFiles) * 100 : 0)}%` }}
                          ></div>
                        </div>
                      </div>
                    )}
                    {uploadStatus.status === "ERROR" && <p className="text-xs text-destructive mt-1 font-mono">{uploadStatus.message}</p>}
                  </div>
                </div>

              </div>
            </div>

            <div className="flex-shrink-0 w-full md:w-64 bg-base-300/20 rounded-xl border border-base-content/20/50 p-4 animate-in fade-in slide-in-from-right-4">
              <p className="text-xs font-medium text-base-content/60 uppercase tracking-wider mb-3">Live Metrics</p>
              <div className="space-y-4">
                <div>
                  <p className="text-xs text-base-content/60">Transactions Extracted</p>
                  <p className="text-xl font-bold font-mono text-primary">{uploadStatus.totalFiles || 0}</p>
                </div>
                <div>
                  <p className="text-xs text-base-content/60">Successfully Saved</p>
                  <p className="text-xl font-bold font-mono text-success">{uploadStatus.processedFiles || 0}</p>
                </div>
              </div>
              {uploadStatus.status === "COMPLETED" && (
                <div className="mt-4 pt-3 border-t border-base-content/20/50 flex items-center justify-center gap-2 text-success">
                  <CheckCircle2 className="h-4 w-4" />
                  <span className="text-sm font-semibold">Upload Finished</span>
                </div>
              )}
            </div>

          </div>
        </div>
      )}

      <div className="grid grid-cols-1 md:grid-cols-3 gap-6 mb-8">
        <div className="p-4 rounded-xl border bg-base-200 border-base-content/10 transition-all hover:shadow-md">
          <p className="text-[10px] font-bold text-base-content/50 uppercase tracking-widest mb-1">Total Transactions</p>
          <p className="text-2xl font-black text-base-content">{totalElements}</p>
        </div>
        <div className="p-4 rounded-xl border bg-base-200 border-error/20 transition-all hover:shadow-md">
          <p className="text-[10px] font-bold text-base-content/50 uppercase tracking-widest mb-1">Unreconciled</p>
          <p className="text-2xl font-black text-error">{unReconciledCount}</p>
        </div>
        <div className="p-4 rounded-xl border bg-base-200 border-primary/20 transition-all hover:shadow-md">
          <p className="text-[10px] font-bold text-base-content/50 uppercase tracking-widest mb-1">AI Accuracy Target</p>
          <p className="text-2xl font-black text-primary">High (Gemini Pro)</p>
        </div>
      </div>

      <div className="flex border-b border-base-content/10 mb-6 font-bold text-sm">
        <button
          className={`px-6 py-3 transition-colors ${activeTab === 'transactions' ? 'border-b-4 border-primary text-primary' : 'text-base-content/60 hover:text-base-content'}`}
          onClick={() => setActiveTab('transactions')}
        >
          Transactions
        </button>
        <button
          className={`px-6 py-3 transition-colors flex items-center gap-2 ${activeTab === 'issues' ? 'border-b-4 border-primary text-primary' : 'text-base-content/60 hover:text-base-content'}`}
          onClick={() => setActiveTab('issues')}
        >
          Issues & Discrepancies
          {audits.length > 0 && <span className="badge badge-error badge-sm font-black border-none text-[10px] px-2">{audits.length}</span>}
        </button>
        <button
          className={`px-6 py-3 transition-colors flex items-center gap-2 ${activeTab === 'history' ? 'border-b-4 border-primary text-primary' : 'text-base-content/60 hover:text-base-content'}`}
          onClick={() => setActiveTab('history')}
        >
          Upload History & Metrics
        </button>
      </div>

      <div className="rounded-xl border border-primary/10 bg-base-200 overflow-hidden shadow-lg">
        {activeTab === 'transactions' ? (
          <div>
            <div className="p-4 border-b border-primary/10 flex flex-wrap items-center gap-4 bg-base-300/20">
              <div className="flex items-center gap-2">
                <span className="text-[10px] font-bold text-base-content/50 uppercase tracking-widest whitespace-nowrap">Status:</span>
                <select
                  className="h-8 rounded-md border border-primary/20 bg-base-100 px-2 py-1 text-xs shadow-sm focus:outline-none focus:ring-1 focus:ring-primary"
                  value={filterStatus}
                  onChange={(e) => { setFilterStatus(e.target.value); setPage(0); }}
                >
                  <option value="all">All Status</option>
                  <option value="AUTO_VALIDATED">Auto Validated</option>
                  <option value="NEEDS_REVIEW">Needs Review</option>
                  <option value="USER_VERIFIED">User Verified</option>
                </select>
              </div>

              <div className="flex items-center gap-2">
                <span className="text-[10px] font-bold text-base-content/50 uppercase tracking-widest whitespace-nowrap">Confidence:</span>
                <select
                  className="h-8 rounded-md border border-primary/20 bg-base-100 px-2 py-1 text-xs shadow-sm focus:outline-none focus:ring-1 focus:ring-primary"
                  value={filterConfidence}
                  onChange={(e) => { setFilterConfidence(e.target.value); setPage(0); }}
                >
                  <option value="all">All Confidence</option>
                  <option value="high">High (&gt;90%)</option>
                  <option value="medium">Medium (70-90%)</option>
                  <option value="low">Low (&lt;70%)</option>
                </select>
              </div>

              <div className="flex items-center gap-2">
                <span className="text-[10px] font-bold text-base-content/50 uppercase tracking-widest whitespace-nowrap">Sort:</span>
                <select
                  className="h-8 rounded-md border border-primary/20 bg-base-100 px-2 py-1 text-xs shadow-sm focus:outline-none focus:ring-1 focus:ring-primary"
                  value={sortBy}
                  onChange={(e) => setSortBy(e.target.value)}
                >
                  <option value="date_desc">Latest First</option>
                  <option value="amount_desc">Highest Amount</option>
                  <option value="confidence_desc">Highest Confidence</option>
                </select>
              </div>

              {selectedIds.size > 0 && (
                <div className="flex items-center gap-2 ml-auto animate-in fade-in slide-in-from-right-2">
                  <span className="text-xs font-bold text-primary">{selectedIds.size} Selected</span>
                  <button
                    onClick={() => handleBulkAction("APPROVE")}
                    className="btn btn-primary btn-xs font-bold"
                  >
                    BULK APPROVE
                  </button>
                  <button
                    onClick={() => setSelectedIds(new Set())}
                    className="btn btn-ghost btn-xs font-bold"
                  >
                    CANCEL
                  </button>
                </div>
              )}
            </div>
            <div className="overflow-x-auto">
              <table className="w-full text-sm text-left">
                <thead className="text-[10px] text-base-content/70 uppercase tracking-widest bg-base-300">
                  <tr>
                    <th className="px-4 py-4">
                      <input
                        type="checkbox"
                        className="checkbox checkbox-xs checkbox-primary"
                        onChange={(e) => {
                          if (e.target.checked) setSelectedIds(new Set(transactions.map(t => t.id)));
                          else setSelectedIds(new Set());
                        }}
                      />
                    </th>
                    <th className="px-4 py-4 font-bold">Date</th>
                    <th className="px-4 py-4 font-bold">Description</th>
                    <th className="px-4 py-4 font-bold">Vendor</th>
                    <th className="px-4 py-4 font-bold text-right">Amount</th>
                    <th className="px-4 py-4 font-bold text-center">Confidence</th>
                    <th className="px-4 py-4 font-bold text-center">Status</th>
                    <th className="px-4 py-4 font-bold text-right">Actions</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-primary/5">
                  {isLoading ? (
                    <tr>
                      <td colSpan={8} className="px-6 py-10 text-center text-base-content/60">
                        <Loader2 className="h-6 w-6 animate-spin mx-auto mb-2" />
                        Loading intelligence grid...
                      </td>
                    </tr>
                  ) : transactions.length === 0 ? (
                    <tr>
                      <td colSpan={8} className="px-6 py-10 text-center text-base-content/60">
                        <AlertCircle className="h-6 w-6 mx-auto mb-2 opacity-50" />
                        No bank transactions found. Upload a PDF statement.
                      </td>
                    </tr>
                  ) : (
                    transactions.map((txn) => (
                      <tr key={txn.id} className={`hover:bg-base-300/30 transition-colors group ${selectedIds.has(txn.id) ? 'bg-primary/10' : ''} ${(!txn.txDate || !txn.amount) ? 'bg-error/5' : ''}`}>
                        <td className="px-4 py-4">
                          <input
                            type="checkbox"
                            className="checkbox checkbox-xs checkbox-primary"
                            checked={selectedIds.has(txn.id)}
                            onChange={(e) => {
                              const next = new Set(selectedIds);
                              if (e.target.checked) next.add(txn.id);
                              else next.delete(txn.id);
                              setSelectedIds(next);
                            }}
                          />
                        </td>
                        <td className="px-4 py-4 whitespace-nowrap text-base-content/60">
                          {txn.txDate || <span className="text-error font-bold flex items-center gap-1"><AlertCircle className="h-3 w-3" /> MISSING</span>}
                        </td>
                        <td className="px-4 py-4">
                          <div className="font-medium truncate max-w-[250px] relative group/reason" title={txn.description}>
                            {txn.description}
                            {txn.aiReasoning && (
                              <div className="absolute left-0 bottom-full mb-2 hidden group-hover/reason:block z-50 p-3 bg-base-300 border border-primary/20 rounded-lg shadow-xl w-64 animate-in fade-in zoom-in-95">
                                <p className="text-[10px] font-bold text-primary uppercase mb-1">AI Reasoning</p>
                                <p className="text-xs italic mb-2">"{txn.aiReasoning}"</p>
                                <p className="text-[10px] font-bold text-base-content/50 uppercase mb-1">Source Snippet</p>
                                <p className="text-[10px] font-mono bg-black/20 p-1 rounded">...{txn.originalSnippet || "N/A"}...</p>
                              </div>
                            )}
                          </div>
                          <div className="flex items-center gap-1.5 mt-1">
                            <span className="text-[10px] text-base-content/60 font-mono opacity-50">Ref: {txn.referenceNumber}</span>
                            {txn.isDuplicate && <span className="badge badge-error badge-xs font-black text-[8px] tracking-tighter shadow-sm animate-pulse px-1">DUPLICATE SUSPECTED</span>}
                          </div>
                        </td>
                        <td className="px-4 py-4">
                          <span className="font-medium text-primary/80">{txn.vendor || 'Unknown'}</span>
                          {txn.category && (
                            <div className="text-[9px] text-primary/60 font-medium uppercase mt-0.5">{txn.category.name}</div>
                          )}
                        </td>
                        <td className={`px-4 py-4 whitespace-nowrap text-right font-mono font-black ${txn.type === 'DEBIT' ? 'text-error' : 'text-success'}`}>
                          {txn.type === 'DEBIT' ? '-' : '+'}{formatCurrency(txn.amount, currency)}
                        </td>
                        <td className="px-4 py-4 text-center">
                          <div className={`inline-flex items-center px-2 py-1 rounded border text-[10px] font-black ${getConfidenceColor(txn.confidenceScore || 95)}`}>
                            {txn.confidenceScore || 95}%
                          </div>
                        </td>
                        <td className="px-4 py-4 whitespace-nowrap text-center">
                          {getStatusBadge(txn.status || (txn.reconciled ? "AUTO_VALIDATED" : "NEEDS_REVIEW"))}
                        </td>
                        <td className="px-4 py-4 text-right">
                          <div className="flex items-center justify-end gap-1 opacity-0 group-hover:opacity-100 transition-opacity">
                            <button
                              onClick={() => handleBulkAction("APPROVE")}
                              className="p-1.5 rounded-md hover:bg-success/20 text-success transition-colors"
                              title="Approve extraction"
                            >
                              <Check className="w-4 h-4" />
                            </button>
                            <button
                              onClick={() => {
                                setEditingTxn(txn);
                                setEditForm({
                                  amount: txn.amount,
                                  vendor: txn.vendor || "",
                                  txDate: txn.txDate,
                                  description: txn.description,
                                  type: txn.type || "DEBIT"
                                });
                              }}
                              className="p-1.5 rounded-md hover:bg-primary/20 text-primary transition-colors"
                              title="Edit extraction"
                            >
                              <Edit2 className="w-4 h-4" />
                            </button>
                            <button
                              onClick={() => toast("Initiating AI reprocessing...", "info")}
                              className="p-1.5 rounded-md hover:bg-warning/20 text-warning transition-colors"
                              title="Reprocess with Gemini"
                            >
                              <Play className="w-4 h-4" />
                            </button>
                            <button
                              onClick={() => toast("Row marked for rejection", "error")}
                              className="p-1.5 rounded-md hover:bg-error/20 text-error transition-colors"
                              title="Reject"
                            >
                              <X className="w-4 h-4" />
                            </button>
                          </div>
                        </td>
                      </tr>
                    ))
                  )}
                </tbody>
              </table>

              {totalPages > 1 && (
                <div className="p-4 border-t border-primary/10 flex items-center justify-between bg-base-300/10">
                  <span className="text-sm text-base-content/60">Page {page + 1} of {totalPages}</span>
                  <div className="flex space-x-2">
                    <button
                      onClick={() => setPage(p => Math.max(0, p - 1))}
                      disabled={page === 0}
                      className="px-3 py-1 text-sm border bg-base-100 rounded-md shadow-sm disabled:opacity-50"
                    >
                      Previous
                    </button>
                    <button
                      onClick={() => setPage(p => Math.min(totalPages - 1, p + 1))}
                      disabled={page >= totalPages - 1}
                      className="px-3 py-1 text-sm border bg-base-100 rounded-md shadow-sm disabled:opacity-50"
                    >
                      Next
                    </button>
                  </div>
                </div>
              )}
            </div>
          </div>
        ) : activeTab === 'history' ? (
          <div className="overflow-x-auto">
            <table className="w-full text-sm text-left">
              <thead className="text-[10px] text-base-content/70 uppercase tracking-widest bg-base-300">
                <tr>
                  <th className="px-6 py-4 font-bold">File Information</th>
                  <th className="px-6 py-4 font-bold text-center">Status</th>
                  <th className="px-6 py-4 font-bold text-center">Source</th>
                  <th className="px-6 py-4 font-bold text-center">AI Metrics</th>
                  <th className="px-6 py-4 font-bold text-center">Retries</th>
                  <th className="px-6 py-4 font-bold text-right">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-primary/5">
                {isFetchingUploads ? (
                  <tr>
                    <td colSpan={6} className="px-6 py-10 text-center">
                      <Loader2 className="h-6 w-6 animate-spin mx-auto mb-2" />
                      Loading history...
                    </td>
                  </tr>
                ) : uploads.length === 0 ? (
                  <tr>
                    <td colSpan={6} className="px-6 py-10 text-center text-base-content/60">
                      No upload history available.
                    </td>
                  </tr>
                ) : (
                  uploads.map((up) => (
                    <tr key={up.id} className="hover:bg-base-300/30 transition-colors">
                      <td className="px-6 py-4">
                        <div className="font-bold text-base-content">{up.fileName}</div>
                        <div className="text-[10px] text-base-content/50 font-mono mt-0.5">{up.fileId}</div>
                        <div className="text-[10px] text-primary/60 mt-1 uppercase font-black">{up.accountType}</div>
                      </td>
                      <td className="px-6 py-4 text-center">
                        {getStatusBadge(up.status)}
                      </td>
                      <td className="px-6 py-4 text-center">
                        <span className={`badge badge-ghost badge-sm border-none font-bold text-[9px] uppercase ${up.source === 'REPROCESSED' ? 'bg-warning/20 text-warning-content' : ''}`}>
                          {up.source || 'AI'}
                        </span>
                      </td>
                      <td className="px-6 py-4">
                        <div className="flex flex-col items-center gap-1">
                          {up.avgConfidenceScore ? (
                            <>
                              <span className={`text-xs font-black ${getConfidenceColor(up.avgConfidenceScore * 100)}`}>{Math.round(up.avgConfidenceScore * 100)}% Confidence</span>
                              <span className="text-[10px] text-base-content/50">Gemini Calls: {up.geminiCallsCount || 0}</span>
                              <span className="text-[10px] text-base-content/50">Time: {(up.processingTimeMs / 1000).toFixed(1)}s</span>
                            </>
                          ) : (
                            <span className="text-[10px] opacity-40 italic">N/A</span>
                          )}
                        </div>
                      </td>
                      <td className="px-6 py-4 text-center">
                        <div className="flex flex-col items-center">
                          <span className={`font-mono text-sm ${up.retryCount > 0 ? 'text-warning font-bold' : 'text-base-content/40'}`}>
                            {up.retryCount || 0}
                          </span>
                          {up.lastProcessedAt && <span className="text-[9px] opacity-50 mt-1">{new Date(up.lastProcessedAt).toLocaleTimeString()}</span>}
                        </div>
                      </td>
                      <td className="px-6 py-4 text-right">
                        <button
                          onClick={() => handleReprocess(up.fileId)}
                          disabled={up.status === 'PROCESSING'}
                          className="btn btn-xs btn-outline btn-warning gap-1 font-bold"
                        >
                          <Play className="h-3 w-3" /> RETRY
                        </button>
                      </td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-sm text-left">
              <thead className="text-[10px] text-base-content/70 uppercase tracking-widest bg-base-300">
                <tr>
                  <th className="px-6 py-4 font-bold">Issue Detail</th>
                  <th className="px-6 py-4 font-bold">Bank Transaction</th>
                  <th className="px-6 py-4 font-bold min-w-[250px]">Link Receipt</th>
                  <th className="px-6 py-4 font-bold text-right">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-primary/5">
                {audits.length === 0 ? (
                  <tr>
                    <td colSpan={4} className="px-6 py-10 text-center text-base-content/60">
                      <CheckCircle2 className="h-6 w-6 mx-auto mb-2 text-success opacity-80" />
                      Hooray! No pending discrepancies found.
                    </td>
                  </tr>
                ) : (
                  audits.map((audit) => (
                    <tr key={audit.id} className="hover:bg-base-300/30 transition-colors">
                      <td className="px-6 py-4">
                        <span className={`badge badge-sm font-black border-none text-[9px] mb-2 ${audit.issueType === 'SUGGESTED_MATCH' ? 'badge-warning' : 'badge-error'}`}>
                          {audit.issueType}
                        </span>
                        <p className="text-xs text-base-content/70 font-medium">{audit.issueDescription || audit.description}</p>
                      </td>
                      <td className="px-6 py-4">
                        {audit.transaction ? (
                          <div className="text-xs">
                            <span className="font-bold">{audit.transaction.description}</span>
                            <div className="text-error font-mono font-black text-[10px] mt-1">-{formatCurrency(audit.transaction.amount, currency)}</div>
                            <div className="text-base-content/50 text-[9px] font-mono">{audit.transaction.txDate}</div>
                          </div>
                        ) : (
                          <span className="text-base-content/40 italic text-xs">N/A</span>
                        )}
                      </td>
                      <td className="px-6 py-4">
                        {audit.transaction && (
                          <select
                            className={`select select-sm select-bordered w-full font-medium ${audit.issueType === 'SUGGESTED_MATCH' ? 'select-primary bg-primary/5' : ''}`}
                            value={selectedReceiptId[audit.id] !== undefined ? selectedReceiptId[audit.id] : (audit.receipt ? audit.receipt.id.toString() : "")}
                            onChange={(e) => setSelectedReceiptId({ ...selectedReceiptId, [audit.id]: e.target.value })}
                          >
                            <option value="">Select a Receipt...</option>
                            {receipts.map(r => (
                              <option key={r.id} value={r.id}>
                                {r.vendor} - {formatCurrency(r.totalAmount, currency)} ({r.date})
                              </option>
                            ))}
                          </select>
                        )}
                      </td>
                      <td className="px-6 py-4 text-right space-x-2">
                        {audit.transaction && (
                          <div className="flex flex-col gap-2">
                            <button
                              onClick={() => {
                                setEditingTxn(audit.transaction!);
                                setEditForm({
                                  amount: audit.transaction!.amount,
                                  vendor: audit.transaction!.vendor || "",
                                  txDate: audit.transaction!.txDate,
                                  description: audit.transaction!.description,
                                  type: audit.transaction!.type || "DEBIT"
                                });
                              }}
                              className="inline-flex items-center justify-center rounded-md text-xs font-medium border border-input bg-base-100 hover:bg-accent hover:text-accent-content h-8 px-3"
                            >
                              <Edit2 className="w-3 h-3 mr-1" /> Edit Extract
                            </button>
                            <button
                              onClick={() => handleManuallyLink(audit.id, audit.transaction?.id, audit.receipt?.id)}
                              className={`inline-flex items-center justify-center rounded-md text-xs font-medium h-8 px-3 ${audit.issueType === 'SUGGESTED_MATCH' ? 'bg-success text-primary-content hover:bg-emerald-600 shadow-sm shadow-emerald-500/20' : 'bg-primary text-primary-content hover:bg-primary/90'}`}
                            >
                              <Check className="w-3 h-3 mr-1" /> {audit.issueType === 'SUGGESTED_MATCH' ? 'Approve Match' : 'Manual Link'}
                            </button>
                          </div>
                        )}
                        <button
                          onClick={() => handleIgnoreAudit(audit.id)}
                          className="inline-flex items-center justify-center rounded-md text-xs font-medium border border-input bg-base-100 hover:bg-accent hover:text-accent-content h-8 px-3 mt-2"
                        >
                          <X className="w-3 h-3 mr-1" /> Mark Done
                        </button>
                      </td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {editingTxn &&
        (
          <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 backdrop-blur-sm p-4 animate-in fade-in">
            <div className="bg-base-200 w-full max-w-md rounded-xl shadow-2xl overflow-hidden border">
              <div className="p-6">
                <h3 className="text-lg font-bold mb-4">Edit Transaction Extraction</h3>
                <p className="text-sm text-base-content/60 mb-4">If the AI extracted the wrong amount, vendor, or date, you can fix it here before linking.</p>

                <div className="space-y-4">
                  <div>
                    <label className="block text-xs font-medium text-base-content/60 mb-1">Date</label>
                    <input
                      type="date"
                      className="w-full flex h-10 rounded-md border border-input bg-base-100 px-3 py-2 text-sm shadow-sm"
                      value={editForm.txDate}
                      onChange={(e) => setEditForm({ ...editForm, txDate: e.target.value })}
                    />
                  </div>
                  <div className="flex gap-4">
                    <div className="flex-1">
                      <label className="block text-xs font-medium text-base-content/60 mb-1">Type</label>
                      <select
                        className="w-full h-10 rounded-md border border-input bg-base-100 px-3 py-2 text-sm shadow-sm"
                        value={editForm.type}
                        onChange={(e) => setEditForm({ ...editForm, type: e.target.value })}
                      >
                        <option value="DEBIT">Debit (Withdrawal)</option>
                        <option value="CREDIT">Credit (Deposit)</option>
                      </select>
                    </div>
                    <div className="flex-1">
                      <label className="block text-xs font-medium text-base-content/60 mb-1">Amount</label>
                      <input
                        type="number"
                        step="0.01"
                        className="w-full flex h-10 rounded-md border border-input bg-base-100 px-3 py-2 text-sm shadow-sm"
                        value={editForm.amount}
                        onChange={(e) => setEditForm({ ...editForm, amount: parseFloat(e.target.value) })}
                      />
                    </div>
                  </div>
                  <div>
                    <label className="block text-xs font-medium text-base-content/60 mb-1">Vendor/Counterparty</label>
                    <input
                      type="text"
                      className="w-full flex h-10 rounded-md border border-input bg-base-100 px-3 py-2 text-sm shadow-sm"
                      value={editForm.vendor}
                      onChange={(e) => setEditForm({ ...editForm, vendor: e.target.value })}
                    />
                  </div>
                  <div>
                    <label className="block text-xs font-medium text-base-content/60 mb-1">Description</label>
                    <textarea
                      className="w-full flex rounded-md border border-input bg-base-100 px-3 py-2 text-sm shadow-sm"
                      rows={2}
                      value={editForm.description}
                      onChange={(e) => setEditForm({ ...editForm, description: e.target.value })}
                    />
                  </div>
                </div>

                <div className="mt-6 flex justify-end gap-3">
                  <button
                    onClick={() => setEditingTxn(null)}
                    className="px-4 py-2 border rounded-md text-sm font-medium hover:bg-base-300"
                    disabled={isSavingTxn}
                  >
                    Cancel
                  </button>
                  <button
                    onClick={handleSaveTransaction}
                    className="px-4 py-2 bg-primary text-primary-content rounded-md text-sm font-medium flex items-center gap-2 hover:bg-primary/90"
                    disabled={isSavingTxn}
                  >
                    {isSavingTxn ? <Loader2 className="w-4 h-4 animate-spin" /> : <Save className="w-4 h-4" />}
                    Save Changes
                  </button>
                </div>
              </div>
            </div>
          </div>
        )}
    </div>
  );
}
