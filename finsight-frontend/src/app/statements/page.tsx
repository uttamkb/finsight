"use client";

import { useState, useEffect, useCallback, useRef } from "react";
import { Upload, FileText, CheckCircle2, AlertCircle, Clock, Loader2, Play } from "lucide-react";
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
  type: string;
  amount: number;
  category: Category;
  reconciled: boolean;
  referenceNumber: string;
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
  const fileInputRef = useRef<HTMLInputElement>(null);

  const fetchTransactions = useCallback(async () => {
    setIsLoading(true);
    try {
      const response = await fetch(`${API_BASE_URL}/statements/transactions?page=${page}&size=15`);
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
  }, [page, toast]);

  useEffect(() => {
    fetchTransactions();
    fetch(`${API_BASE_URL}/settings`)
      .then(res => res.json())
      .then(data => {
        if (data && data.currency) setCurrency(data.currency);
      })
      .catch(console.error);
  }, [fetchTransactions]);

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

      <div className="rounded-xl border border-primary/10 bg-card overflow-hidden shadow-lg">
        <div className="overflow-x-auto">
          <table className="w-full text-sm text-left">
            <thead className="text-xs text-muted-foreground uppercase bg-muted/50">
              <tr>
                <th scope="col" className="px-6 py-4 font-medium">Date</th>
                <th scope="col" className="px-6 py-4 font-medium">Description</th>
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
        </div>
      </div>
    </div>
  );
}
