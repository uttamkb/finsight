"use client";

import { useState, useEffect, useCallback, useRef } from "react";
import { Upload, FileText, CheckCircle2, AlertCircle, Clock, Loader2, Play, Check, X } from "lucide-react";
import { useToast } from "@/components/toast-provider";
import { formatCurrency } from "@/lib/utils";
import { API_BASE_URL } from "@/lib/constants";

interface Category {
  id: number;
  name: string;
  type: string;
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
  
  const [activeTab, setActiveTab] = useState<"transactions" | "issues">("transactions");
  const [audits, setAudits] = useState<AuditTrail[]>([]);
  const [receipts, setReceipts] = useState<Receipt[]>([]);
  const [selectedReceiptId, setSelectedReceiptId] = useState<Record<number, string>>({});
  
  const fileInputRef = useRef<HTMLInputElement>(null);

  // Filters
  const [filterReconciled, setFilterReconciled] = useState<string>("all");

  const fetchTransactions = useCallback(async () => {
    setIsLoading(true);
    try {
      // Add optional filter 
      const filterQuery = filterReconciled !== "all" ? `&reconciled=${filterReconciled === "true"}` : "";
      const response = await fetch(`${API_BASE_URL}/statements/transactions?page=${page}&size=15${filterQuery}`);
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
  }, [page, filterReconciled, toast]);

  const fetchAudits = useCallback(async () => {
    try {
      const response = await fetch(`${API_BASE_URL}/reconciliation/audit-trail`);
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
      const response = await fetch(`${API_BASE_URL}/receipts?size=100`);
      if (response.ok) {
        const data = await response.json();
        setReceipts(data.content || []);
      }
    } catch (error) {
      console.error("Failed to fetch receipts:", error);
    }
  }, []);

  useEffect(() => {
    fetchTransactions();
    fetchAudits();
    fetchReceipts();
    fetch(`${API_BASE_URL}/settings`)
      .then(res => res.json())
      .then(data => {
        if (data && data.currency) setCurrency(data.currency);
      })
      .catch(console.error);
  }, [fetchTransactions, fetchAudits, fetchReceipts]);

  const handleFileUpload = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;

    const filename = file.name.toLowerCase();
    if (!filename.endsWith(".pdf") && !filename.endsWith(".csv")) {
      toast("Please upload a valid PDF or CSV bank statement.", "error");
      return;
    }

    setIsUploading(true);
    toast(`Uploading and parsing ${filename.endsWith(".csv") ? "CSV" : "PDF"} statement...`, "info");

    const formData = new FormData();
    formData.append("file", file);

    try {
      const response = await fetch(`${API_BASE_URL}/statements/upload`, {
        method: "POST",
        body: formData,
      });

      if (response.ok) {
        const data = await response.json();
        toast(`Success! Extracted ${data.transactionsSaved} valid transactions.`, "success");
        setPage(0);
        fetchTransactions();
      } else {
        const errText = await response.text();
        toast(`Failed to process PDF: ${errText}`, "error");
      }
    } catch (error) {
      console.error("Upload error", error);
      toast("Connection error during upload.", "error");
    } finally {
      setIsUploading(false);
      if (fileInputRef.current) fileInputRef.current.value = "";
    }
  };

  const handleReconcile = async () => {
    setIsReconciling(true);
    toast("Running Auto-Reconciliation engine...", "info");

    try {
      const response = await fetch(`${API_BASE_URL}/statements/reconcile`, {
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
      const response = await fetch(`${API_BASE_URL}/reconciliation/link`, {
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
      const response = await fetch(`${API_BASE_URL}/reconciliation/audit-trail/${auditId}/ignore`, { method: "POST" });
      if (response.ok) {
        toast("Issue marked as ignored/done.", "success");
        fetchAudits();
      }
    } catch (e) {
      toast("Failed to ignore issue", "error");
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
          <p className="text-muted-foreground mt-2">
            Upload PDFs for Gemini AI parsing and auto-categorization.
          </p>
        </div>

        <div className="flex items-center gap-3">
          <input
            type="file"
            accept=".pdf,.csv"
            className="hidden"
            ref={fileInputRef}
            onChange={handleFileUpload}
          />
          <button
            onClick={() => fileInputRef.current?.click()}
            disabled={isUploading}
            className="inline-flex items-center justify-center gap-2 whitespace-nowrap rounded-md text-sm font-medium ring-offset-background transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 disabled:pointer-events-none disabled:opacity-50 bg-secondary text-secondary-foreground hover:bg-secondary/80 h-10 px-4 py-2"
          >
            {isUploading ? <Loader2 className="h-4 w-4 animate-spin" /> : <Upload className="h-4 w-4" />}
            Upload Statement
          </button>
          
          <button
            onClick={handleReconcile}
            disabled={isReconciling || isUploading}
            className="inline-flex items-center justify-center gap-2 whitespace-nowrap rounded-md text-sm font-medium ring-offset-background transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 disabled:pointer-events-none disabled:opacity-50 bg-primary text-primary-foreground hover:bg-primary/90 h-10 px-4 py-2 shadow-lg shadow-primary/20"
          >
            {isReconciling ? <Loader2 className="h-4 w-4 animate-spin" /> : <Play className="h-4 w-4" />}
            Auto Reconcile
          </button>
        </div>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-3 gap-4 mb-6">
        <div className="p-4 rounded-xl border bg-card/50 backdrop-blur-sm border-primary/10 transition-all hover:scale-[1.02]">
          <p className="text-xs font-medium text-muted-foreground uppercase tracking-wider mb-1">Total Transactions</p>
          <p className="text-2xl font-bold">{totalElements}</p>
        </div>
        <div className="p-4 rounded-xl border bg-card/50 backdrop-blur-sm border-primary/10 transition-all hover:scale-[1.02]">
          <p className="text-xs font-medium text-muted-foreground uppercase tracking-wider mb-1">Unreconciled</p>
          <p className="text-2xl font-bold text-destructive">{unReconciledCount}</p>
        </div>
        <div className="p-4 rounded-xl border bg-card/50 backdrop-blur-sm border-primary/10 transition-all hover:scale-[1.02]">
          <p className="text-xs font-medium text-muted-foreground uppercase tracking-wider mb-1">AI Accuracy Target</p>
          <p className="text-2xl font-bold text-primary">High (Gemini Pro)</p>
        </div>
      </div>

      <div className="flex border-b border-primary/10 mb-6 font-medium text-sm">
        <button 
          className={`px-6 py-3 transition-colors ${activeTab === 'transactions' ? 'border-b-2 border-primary text-primary' : 'text-muted-foreground hover:text-foreground'}`}
          onClick={() => setActiveTab('transactions')}
        >
          Transactions
        </button>
        <button 
          className={`px-6 py-3 transition-colors flex items-center gap-2 ${activeTab === 'issues' ? 'border-b-2 border-primary text-primary' : 'text-muted-foreground hover:text-foreground'}`}
          onClick={() => setActiveTab('issues')}
        >
          Issues & Discrepancies
          {audits.length > 0 && <span className="bg-destructive text-destructive-foreground text-[10px] px-2 py-0.5 rounded-full font-bold">{audits.length}</span>}
        </button>
      </div>

      <div className="rounded-xl border border-primary/10 bg-card overflow-hidden shadow-lg">
        {activeTab === 'transactions' ? (
          <div>
            <div className="p-4 border-b border-primary/10 flex justify-between items-center bg-muted/20">
               <select 
                  className="h-9 rounded-md border border-input bg-background px-3 py-1 text-sm shadow-sm"
                  value={filterReconciled}
                  onChange={(e) => { setFilterReconciled(e.target.value); setPage(0); }}
               >
                 <option value="all">All Transactions</option>
                 <option value="true">Linked Only</option>
                 <option value="false">Pending Only</option>
               </select>
            </div>
            <div className="overflow-x-auto">
          <table className="w-full text-sm text-left">
            <thead className="text-xs text-muted-foreground uppercase bg-muted/50">
              <tr>
                <th scope="col" className="px-6 py-4 font-medium">Date</th>
                <th scope="col" className="px-6 py-4 font-medium">Description</th>
                <th scope="col" className="px-6 py-4 font-medium">Vendor</th>
                <th scope="col" className="px-6 py-4 font-medium text-right">Amount</th>
                <th scope="col" className="px-6 py-4 font-medium">Category</th>
                <th scope="col" className="px-6 py-4 font-medium text-center">Status</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-primary/5">
              {isLoading ? (
                <tr>
                  <td colSpan={5} className="px-6 py-10 text-center text-muted-foreground">
                    <Loader2 className="h-6 w-6 animate-spin mx-auto mb-2" />
                    Loading transactions...
                  </td>
                </tr>
              ) : transactions.length === 0 ? (
                <tr>
                  <td colSpan={5} className="px-6 py-10 text-center text-muted-foreground">
                    <AlertCircle className="h-6 w-6 mx-auto mb-2 opacity-50" />
                    No bank transactions found. Upload a PDF statement.
                  </td>
                </tr>
              ) : (
                transactions.map((txn) => (
                  <tr key={txn.id} className="hover:bg-muted/30 transition-colors group">
                    <td className="px-6 py-4 whitespace-nowrap text-muted-foreground">
                      {txn.txDate}
                    </td>
                    <td className="px-6 py-4">
                      <div className="font-medium truncate max-w-[300px]" title={txn.description}>
                        {txn.description}
                      </div>
                      <div className="text-xs text-muted-foreground font-mono mt-1 opacity-50">Ref: {txn.referenceNumber}</div>
                    </td>
                    <td className="px-6 py-4">
                      <span className="font-medium text-primary/80">{txn.vendor || 'Unknown'}</span>
                    </td>
                    <td className={`px-6 py-4 whitespace-nowrap text-right font-mono font-bold ${txn.type === 'DEBIT' ? 'text-destructive' : 'text-emerald-500'}`}>
                       {txn.type === 'DEBIT' ? '-' : '+'}{formatCurrency(txn.amount, currency)}
                    </td>
                    <td className="px-6 py-4 whitespace-nowrap">
                      {txn.category ? (
                        <span className="px-2.5 py-1 rounded-full bg-primary/10 text-primary text-[10px] font-bold tracking-wide border border-primary/20">
                          {txn.category.name}
                        </span>
                      ) : (
                        <span className="text-muted-foreground italic text-xs">Uncategorized</span>
                      )}
                    </td>
                    <td className="px-6 py-4 whitespace-nowrap text-center">
                      {txn.reconciled ? (
                        <span className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full bg-emerald-500/10 text-emerald-500 text-[10px] font-bold tracking-wide border border-emerald-500/20">
                          <CheckCircle2 className="h-3 w-3" /> Linked
                        </span>
                      ) : (
                        <span className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full bg-amber-500/10 text-amber-500 text-[10px] font-bold tracking-wide border border-amber-500/20">
                          <Clock className="h-3 w-3" /> Pending
                        </span>
                      )}
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
          
          {totalPages > 1 && (
            <div className="p-4 border-t border-primary/10 flex items-center justify-between bg-muted/10">
              <span className="text-sm text-muted-foreground">Page {page + 1} of {totalPages}</span>
              <div className="flex space-x-2">
                <button 
                  onClick={() => setPage(p => Math.max(0, p - 1))}
                  disabled={page === 0}
                  className="px-3 py-1 text-sm border bg-background rounded-md shadow-sm disabled:opacity-50"
                >
                  Previous
                </button>
                <button 
                  onClick={() => setPage(p => Math.min(totalPages - 1, p + 1))}
                  disabled={page >= totalPages - 1}
                  className="px-3 py-1 text-sm border bg-background rounded-md shadow-sm disabled:opacity-50"
                >
                  Next
                </button>
              </div>
            </div>
          )}
        </div>
        </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-sm text-left">
              <thead className="text-xs text-muted-foreground uppercase bg-muted/50">
                <tr>
                  <th scope="col" className="px-6 py-4 font-medium">Issue Detail</th>
                  <th scope="col" className="px-6 py-4 font-medium">Bank Transaction</th>
                  <th scope="col" className="px-6 py-4 font-medium min-w-[250px]">Link Receipt</th>
                  <th scope="col" className="px-6 py-4 font-medium text-right">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-primary/5">
                {audits.length === 0 ? (
                   <tr>
                    <td colSpan={4} className="px-6 py-10 text-center text-muted-foreground">
                      <CheckCircle2 className="h-6 w-6 mx-auto mb-2 text-emerald-500 opacity-80" />
                      Hooray! No pending discrepancies found.
                    </td>
                  </tr>
                ) : (
                  audits.map((audit) => (
                    <tr key={audit.id} className="hover:bg-muted/30 transition-colors">
                      <td className="px-6 py-4">
                        <span className={`inline-flex px-2 py-1 rounded-md text-xs font-bold mb-2 ${audit.issueType === 'SUGGESTED_MATCH' ? 'bg-amber-500/10 text-amber-500' : 'bg-destructive/10 text-destructive'}`}>
                          {audit.issueType}
                        </span>
                        <p className="text-xs text-muted-foreground">{audit.issueDescription || audit.description}</p>
                      </td>
                      <td className="px-6 py-4">
                        {audit.transaction ? (
                          <div className="text-sm">
                            <span className="font-medium">{audit.transaction.description}</span>
                            <div className="text-destructive font-mono text-xs mt-1">-{formatCurrency(audit.transaction.amount, currency)}</div>
                            <div className="text-muted-foreground text-[10px]">{audit.transaction.txDate}</div>
                          </div>
                        ) : (
                          <span className="text-muted-foreground italic">N/A</span>
                        )}
                      </td>
                      <td className="px-6 py-4">
                        {audit.transaction && (
                          <select 
                            className={`w-full flex h-9 w-full rounded-md border bg-background px-3 py-1 text-sm shadow-sm transition-colors focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring disabled:cursor-not-allowed disabled:opacity-50 ${audit.issueType === 'SUGGESTED_MATCH' ? 'border-primary ring-1 ring-primary/20 bg-primary/5' : 'border-input'}`}
                            value={selectedReceiptId[audit.id] !== undefined ? selectedReceiptId[audit.id] : (audit.receipt ? audit.receipt.id.toString() : "")}
                            onChange={(e) => setSelectedReceiptId({...selectedReceiptId, [audit.id]: e.target.value})}
                          >
                            <option value="">Select a Receipt to link...</option>
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
                          <button
                           onClick={() => handleManuallyLink(audit.id, audit.transaction?.id, audit.receipt?.id)}
                           className={`inline-flex items-center justify-center rounded-md text-xs font-medium h-8 px-3 ${audit.issueType === 'SUGGESTED_MATCH' ? 'bg-emerald-500 text-white hover:bg-emerald-600 shadow-sm shadow-emerald-500/20' : 'bg-primary text-primary-foreground hover:bg-primary/90'}`}
                          >
                            <Check className="w-3 h-3 mr-1" /> {audit.issueType === 'SUGGESTED_MATCH' ? 'Approve Match' : 'Manual Link'}
                          </button>
                        )}
                        <button
                         onClick={() => handleIgnoreAudit(audit.id)}
                         className="inline-flex items-center justify-center rounded-md text-xs font-medium border border-input bg-background hover:bg-accent hover:text-accent-foreground h-8 px-3"
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
    </div>
  );
}
