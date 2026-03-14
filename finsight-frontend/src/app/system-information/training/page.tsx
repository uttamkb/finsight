"use client";

import { useState, useEffect } from "react";
import { 
  BrainCircuit, 
  Cpu, 
  Database, 
  RefreshCw, 
  CheckCircle2, 
  AlertCircle, 
  FileJson, 
  Image as ImageIcon,
  Play,
  Loader2,
  ChevronRight,
  History,
  Info,
  Edit2,
  Trash2,
  X,
  HelpCircle,
  Clock,
  CheckCircle,
  Download
} from "lucide-react";
import { apiFetch } from "@/lib/api";
import { useToast } from "@/components/toast-provider";

interface HarvestedSample {
  sampleId: string;
  vendor: string;
  amount: number;
  date: string;
  category: string;
  original_filename: string;
  verified?: boolean;
}

export default function TrainingDashboardPage() {
  const { toast } = useToast();
  const [samples, setSamples] = useState<HarvestedSample[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [isPreparing, setIsPreparing] = useState(false);
  const [isHarvesting, setIsHarvesting] = useState(false);
  const [preparationOutput, setPreparationOutput] = useState<string | null>(null);
  const [showHelp, setShowHelp] = useState(false);
  const [editingSample, setEditingSample] = useState<HarvestedSample | null>(null);
  const [editData, setEditData] = useState<Partial<HarvestedSample>>({});

  const [dictionaryStats, setDictionaryStats] = useState<{count: number}>({ count: 0 });

  useEffect(() => {
    fetchSamples();
    fetchDictionaryStats();
  }, []);

  const fetchDictionaryStats = async () => {
    try {
      const response = await apiFetch("/training/dictionary/stats");
      if (response.ok) {
        const data = await response.json();
        setDictionaryStats({ count: data.count });
      }
    } catch (error) {
      console.error("Failed to fetch dictionary stats:", error);
    }
  };

  const fetchSamples = async () => {
    setIsLoading(true);
    try {
      const response = await apiFetch("/training/samples");
      if (response.ok) {
        const data = await response.json();
        setSamples(data);
      }
    } catch (error) {
      console.error("Failed to fetch samples:", error);
    } finally {
      setIsLoading(false);
    }
  };

  const handleHarvestManual = async () => {
    setIsHarvesting(true);
    try {
      const response = await apiFetch("/training/harvest", { method: "POST" });
      const data = await response.json();
      if (response.ok) {
        toast(`Harvest complete: ${data.count} new samples found.`);
        fetchSamples();
      } else {
        toast(data.message || "Harvest failed.", "error");
      }
    } catch (error) {
      toast("Connection error during harvest.", "error");
    } finally {
      setIsHarvesting(false);
    }
  };

  const handleUpdateSample = async () => {
    if (!editingSample) return;
    try {
      const response = await apiFetch(`/training/samples/${editingSample.sampleId}`, {
        method: "PUT",
        body: JSON.stringify(editData)
      });
      if (response.ok) {
        toast("Sample updated and verified!");
        setEditingSample(null);
        fetchSamples();
        fetchDictionaryStats();
      } else {
        toast("Failed to update sample.", "error");
      }
    } catch (error) {
      toast("Connection error.", "error");
    }
  };

  const handleDeleteSample = async (id: string) => {
    if (!confirm("Are you sure you want to delete this training sample?")) return;
    try {
      const response = await apiFetch(`/training/samples/${id}`, { method: "DELETE" });
      if (response.ok) {
        toast("Sample deleted.");
        fetchSamples();
      } else {
        toast("Deletion failed.", "error");
      }
    } catch (error) {
      toast("Connection error.", "error");
    }
  };

  const handlePrepareDataset = async () => {
    if (samples.length === 0) {
      toast("No harvested samples available to prepare.", "error");
      return;
    }

    setIsPreparing(true);
    setPreparationOutput(null);
    try {
      const response = await apiFetch("/training/prepare", { method: "POST" });
      const data = await response.json();
      
      if (response.ok) {
        toast("Dataset preparation successful!");
        setPreparationOutput(data.output);
      } else {
        toast(data.message || "Failed to prepare dataset.", "error");
      }
    } catch (error) {
      toast("Connection error during dataset preparation.", "error");
    } finally {
      setIsPreparing(false);
    }
  };

  const handleRunTraining = async () => {
    setIsPreparing(true); // Reuse preparation loading state or add isTraining
    setPreparationOutput(null);
    try {
      const response = await apiFetch("/training/train", { method: "POST" });
      const data = await response.json();
      if (response.ok) {
        toast("Training completed successfully!");
        setPreparationOutput(data.output);
      } else {
        toast(data.message || "Training failed.", "error");
      }
    } catch (error) {
      toast("Connection error during training.", "error");
    } finally {
      setIsPreparing(false);
    }
  };

  const handleDeployModel = async () => {
    try {
      const response = await apiFetch("/training/deploy", { method: "POST" });
      const data = await response.json();
      if (response.ok) {
        toast("Model deployed successfully!");
      } else {
        toast(data.message || "Deployment failed.", "error");
      }
    } catch (error) {
      toast("Connection error during deployment.", "error");
    }
  };

  return (
    <div className="container mx-auto py-10 px-4 max-w-6xl animate-in fade-in duration-500">
      {/* Header */}
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-6 mb-8">
        <div className="flex items-center gap-3">
          <div className="p-3 bg-indigo-500/10 rounded-xl text-indigo-500">
            <BrainCircuit className="h-8 w-8" />
          </div>
          <div>
            <h1 className="text-3xl font-bold tracking-tight">OCR Rule Refinement</h1>
            <p className="text-muted-foreground">Improve extraction accuracy by managing verified vendors and regex rules.</p>
          </div>
        </div>
        
        <div className="flex gap-3">
          <button 
            onClick={() => setShowHelp(true)}
            className="inline-flex items-center gap-2 px-3 py-2 border rounded-lg hover:bg-muted transition-colors text-sm font-medium text-muted-foreground"
          >
            <HelpCircle className="h-4 w-4" />
            Help
          </button>
          <button 
            onClick={handleHarvestManual}
            disabled={isHarvesting}
            className="inline-flex items-center gap-2 px-4 py-2 border rounded-lg hover:bg-muted transition-colors text-sm font-medium"
          >
            {isHarvesting ? <Loader2 className="h-4 w-4 animate-spin text-indigo-500" /> : <Download className="h-4 w-4" />}
            Harvest Now
          </button>
          <button 
            onClick={fetchSamples}
            className="inline-flex items-center gap-2 px-4 py-2 border rounded-lg hover:bg-muted transition-colors text-sm font-medium"
          >
            <RefreshCw className={`h-4 w-4 ${isLoading ? 'animate-spin' : ''}`} />
            Refresh
          </button>
          <button 
            onClick={handlePrepareDataset}
            disabled={isPreparing || samples.length === 0}
            className="inline-flex items-center gap-2 px-6 py-2 bg-indigo-600 text-white rounded-lg font-bold shadow-lg shadow-indigo-500/20 hover:bg-indigo-700 transition-all disabled:opacity-50"
          >
            {isPreparing ? <Loader2 className="h-4 w-4 animate-spin" /> : <Play className="h-4 w-4" />}
            {isPreparing ? "Processing..." : "Update Extraction Rules"}
          </button>
        </div>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-8">
        {/* Statistics & Guides */}
        <div className="lg:col-span-1 space-y-6">
          <div className="p-6 rounded-2xl border bg-card shadow-sm">
            <h2 className="text-lg font-bold mb-4 flex items-center gap-2">
              <Cpu className="h-5 w-5 text-indigo-500" />
              Model Insights
            </h2>
            <div className="space-y-4">
              <div className="flex justify-between items-center p-3 rounded-lg bg-muted/50 border">
                <span className="text-sm font-medium">Harvested Samples</span>
                <span className="text-xl font-bold text-indigo-500">{samples.length}</span>
              </div>
              <div className="flex justify-between items-center p-3 rounded-lg bg-muted/50 border">
                <span className="text-sm font-medium">Verified Vendors</span>
                <span className="text-xl font-bold text-emerald-500">{dictionaryStats.count}</span>
              </div>
              <div className="flex justify-between items-center p-3 rounded-lg bg-muted/50 border">
                <span className="text-sm font-medium">Model Status</span>
                <span className="text-xs bg-emerald-500/10 text-emerald-500 px-2 py-0.5 rounded border border-emerald-500/20 font-bold uppercase">Optimized</span>
              </div>
            </div>
            
            <div className="mt-6 p-4 bg-amber-500/5 border border-amber-500/20 rounded-xl">
              <div className="flex gap-2 text-amber-600 mb-2">
                <AlertCircle className="h-4 w-4 shrink-0" />
                <span className="text-xs font-bold uppercase tracking-wider">Note</span>
              </div>
              <p className="text-xs text-muted-foreground leading-relaxed">
                Aim for at least **50-100 samples** from the same associations to significantly improve vendor and amount recognition accuracy.
              </p>
            </div>
          </div>

          <div className="p-6 rounded-2xl border bg-card shadow-sm">
            <h2 className="text-lg font-bold mb-4 flex items-center gap-2">
              <History className="h-5 w-5 text-indigo-500" />
              Next Steps
            </h2>
            <div className="space-y-4">
              {[
                { 
                  title: "Run Dataset Prep", 
                  desc: "Convert harvested JSON/Images to PaddleOCR format.", 
                  done: samples.length > 0,
                  action: handlePrepareDataset,
                  label: "Prepare"
                },
                { 
                  title: "Update Rules", 
                  desc: "Commit verified vendors to the ground-truth dictionary.", 
                  done: dictionaryStats.count > 0,
                  action: handleRunTraining,
                  label: "Apply Rules"
                },
                { 
                  title: "Deploy Improvements", 
                  desc: "Sync refined rules with the OCR processing engine.", 
                  done: false,
                  action: handleDeployModel,
                  label: "Deploy"
                }
              ].map((step, i) => (
                <div key={i} className="flex flex-col gap-3 p-3 rounded-lg hover:bg-muted/30 transition-colors">
                  <div className="flex gap-3">
                    <div className={`mt-0.5 h-5 w-5 rounded-full flex items-center justify-center shrink-0 ${step.done ? 'bg-indigo-500 text-white' : 'border border-muted-foreground/30 text-muted-foreground/50'}`}>
                      {step.done ? <CheckCircle2 className="h-3 w-3" /> : <span className="text-[10px] font-bold">{i+1}</span>}
                    </div>
                    <div className="flex-1">
                      <h3 className="text-sm font-bold">{step.title}</h3>
                      <p className="text-xs text-muted-foreground mb-3">{step.desc}</p>
                      <button 
                        onClick={step.action}
                        disabled={isPreparing}
                        className="w-full py-1.5 px-3 bg-muted hover:bg-indigo-500 hover:text-white rounded-md text-[10px] font-bold uppercase tracking-wider transition-all disabled:opacity-50"
                      >
                        {step.label}
                      </button>
                    </div>
                  </div>
                </div>
              ))}
            </div>
          </div>
        </div>

        {/* Harvested Samples List */}
        <div className="lg:col-span-2 space-y-6">
          <div className="p-6 rounded-2xl border bg-card shadow-sm min-h-[400px]">
            <h2 className="text-lg font-bold mb-4 flex items-center gap-2">
              <Database className="h-5 w-5 text-indigo-500" />
              Harvested Golden Samples
            </h2>

            {isLoading ? (
              <div className="flex flex-col items-center justify-center py-20 gap-4 opacity-50">
                <Loader2 className="h-10 w-10 animate-spin text-indigo-500" />
                <p className="text-sm font-medium">Scanning workspace for samples...</p>
              </div>
            ) : samples.length === 0 ? (
              <div className="flex flex-col items-center justify-center py-20 text-center space-y-4 border-2 border-dashed rounded-xl bg-muted/10">
                <div className="p-4 bg-muted rounded-full">
                  <ImageIcon className="h-12 w-12 text-muted-foreground/40" />
                </div>
                <div>
                  <h3 className="text-lg font-bold">No samples harvested yet</h3>
                  <p className="text-sm text-muted-foreground max-w-xs mx-auto">
                    Golden samples are created automatically when you verify transactions with attached receipts.
                  </p>
                </div>
              </div>
            ) : (
              <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                {samples.map((sample) => (
                  <div key={sample.sampleId} className="group p-4 rounded-xl border bg-background/50 hover:border-indigo-500/50 hover:bg-background transition-all relative overflow-hidden">
                    {sample.verified && (
                      <div className="absolute top-0 right-0 px-2 py-0.5 bg-emerald-500 text-white text-[9px] font-bold uppercase rounded-bl-lg flex items-center gap-1 shadow-sm">
                        <CheckCircle className="h-3 w-3" /> Verified
                      </div>
                    )}
                    {!sample.verified && (
                      <div className="absolute top-0 right-0 px-2 py-0.5 bg-amber-500 text-white text-[9px] font-bold uppercase rounded-bl-lg flex items-center gap-1 shadow-sm">
                        <Clock className="h-3 w-3" /> Raw
                      </div>
                    )}
                    
                    <div className="flex items-start justify-between mb-2">
                      <div className="p-2 bg-indigo-500/10 rounded-lg text-indigo-500 group-hover:bg-indigo-500 group-hover:text-white transition-colors">
                        <FileJson className="h-4 w-4" />
                      </div>
                      <div className="flex gap-1 opacity-0 group-hover:opacity-100 transition-opacity">
                        <button 
                          onClick={() => {
                            setEditingSample(sample);
                            setEditData({
                              vendor: sample.vendor,
                              amount: sample.amount,
                              date: sample.date,
                              category: sample.category
                            });
                          }}
                          className="p-1.5 hover:bg-indigo-50 text-indigo-600 rounded"
                          title="Verify / Edit"
                        >
                          <Edit2 className="h-3.5 w-3.5" />
                        </button>
                        <button 
                          onClick={() => handleDeleteSample(sample.sampleId)}
                          className="p-1.5 hover:bg-rose-50 text-rose-600 rounded"
                          title="Delete"
                        >
                          <Trash2 className="h-3.5 w-3.5" />
                        </button>
                      </div>
                    </div>
                    
                    <h3 className="font-bold text-sm truncate mb-1">{sample.vendor || "Unknown Vendor"}</h3>
                    <div className="flex items-center gap-2 text-xs text-muted-foreground mb-3">
                      <span className="bg-muted px-1.5 py-0.5 rounded italic">{sample.category || "Uncategorized"}</span>
                      <span>•</span>
                      <span className="font-bold text-indigo-500">
                        {new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR' }).format(sample.amount)}
                      </span>
                    </div>
                    <div className="flex items-center justify-between pt-2 border-t border-muted/50">
                      <span className="text-[10px] text-muted-foreground font-mono">
                        {sample.date || "No Date"}
                      </span>
                      <span className="text-[10px] text-muted-foreground italic truncate max-w-[100px]" title={sample.original_filename}>
                        {sample.original_filename}
                      </span>
                    </div>
                  </div>
                ))}
              </div>
            )}
          </div>

          {preparationOutput && (
            <div className="p-6 rounded-2xl border-2 border-indigo-500/20 bg-indigo-500/5 shadow-inner animate-in slide-in-from-top-4 duration-500">
              <h2 className="text-sm font-bold mb-3 flex items-center gap-2 text-indigo-600 uppercase tracking-widest">
                <Play className="h-3 w-3 fill-current" />
                Preparation Logs
              </h2>
              <pre className="text-[10px] font-mono p-4 bg-black/80 text-emerald-400 rounded-lg overflow-x-auto whitespace-pre-wrap max-h-48 scrollbar-thin scrollbar-thumb-emerald-900">
                {preparationOutput}
              </pre>
            </div>
          )}
        </div>
      </div>

      {/* Help Modal */}
      {showHelp && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/60 backdrop-blur-sm animate-in fade-in duration-300">
          <div className="bg-card border rounded-2xl shadow-2xl max-w-2xl w-full p-8 relative animate-in zoom-in-95 duration-300">
            <button 
              onClick={() => setShowHelp(false)}
              className="absolute top-4 right-4 p-2 hover:bg-muted rounded-full text-muted-foreground transition-colors"
            >
              <X className="h-5 w-5" />
            </button>
            <div className="flex items-center gap-4 mb-6">
              <div className="p-3 bg-indigo-500/10 rounded-xl text-indigo-500">
                <HelpCircle className="h-8 w-8" />
              </div>
              <h2 className="text-2xl font-bold">Rule Refinement Guide</h2>
            </div>
            <div className="space-y-6 text-sm leading-relaxed">
              <section>
                <h3 className="font-bold text-indigo-500 mb-2 uppercase tracking-tight text-xs">How it Works</h3>
                <p className="text-muted-foreground">Instead of slow model retraining, we use **Rule-Based Refinement**. By verifying vendors, you build a "Verified Dictionary" that the OCR engine uses to guarantee 100% matching accuracy for your frequent businesses.</p>
              </section>
              <section>
                <h3 className="font-bold text-indigo-500 mb-2 uppercase tracking-tight text-xs">The Human-in-the-Loop Process</h3>
                <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                  <div className="p-4 bg-muted/40 rounded-xl border border-dashed">
                    <div className="font-bold mb-1">1. Harvest</div>
                    <p className="text-xs text-muted-foreground">Receipts synced from Drive with high confidence are automatically captured. Use "Harvest Now" to scan existing receipts.</p>
                  </div>
                  <div className="p-4 bg-muted/40 rounded-xl border border-dashed">
                    <div className="font-bold mb-1">2. Verify</div>
                    <p className="text-xs text-muted-foreground text-emerald-600 font-medium">This is the critical step. Use the edit icon on any "Raw" sample to correct typos in Vendor name or Amount.</p>
                  </div>
                  <div className="p-4 bg-muted/40 rounded-xl border border-dashed">
                    <div className="font-bold mb-1">3. Rule Export</div>
                    <p className="text-xs text-muted-foreground">Verified vendors are added to a local dictionary. Click "Update Extraction Rules" to export this for the OCR engine.</p>
                  </div>
                  <div className="p-4 bg-muted/40 rounded-xl border border-dashed">
                    <div className="font-bold mb-1">4. Regex Accuracy</div>
                    <p className="text-xs text-muted-foreground text-indigo-600 font-medium font-bold">High Accuracy mode (Gemini) automatically updates this dictionary when it successfully identifies a vendor.</p>
                  </div>
                </div>
              </section>
            </div>
            <button 
              onClick={() => setShowHelp(false)}
              className="mt-8 w-full py-3 bg-indigo-600 text-white font-bold rounded-xl hover:bg-indigo-700 transition-colors shadow-lg shadow-indigo-500/20"
            >
              Got it, let's train!
            </button>
          </div>
        </div>
      )}

      {/* Edit/Verify Modal */}
      {editingSample && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/60 backdrop-blur-sm animate-in fade-in duration-300">
          <div className="bg-card border rounded-2xl shadow-2xl max-w-md w-full p-8 relative animate-in zoom-in-95 duration-300">
            <button 
              onClick={() => setEditingSample(null)}
              className="absolute top-4 right-4 p-2 hover:bg-muted rounded-full text-muted-foreground transition-colors"
            >
              <X className="h-5 w-5" />
            </button>
            <h2 className="text-xl font-bold mb-1">Verify Ground Truth</h2>
            <p className="text-sm text-muted-foreground mb-6">Correct any OCR errors to improve model accuracy.</p>
            
            <div className="space-y-4">
              <div>
                <label className="text-[10px] font-bold uppercase text-muted-foreground mb-1 block">Vendor Name</label>
                <input 
                  type="text" 
                  value={editData.vendor} 
                  onChange={e => setEditData({...editData, vendor: e.target.value})}
                  className="w-full p-3 rounded-lg border bg-background focus:ring-2 focus:ring-indigo-500 outline-none transition-all font-medium"
                />
              </div>
              <div className="grid grid-cols-2 gap-4">
                <div>
                  <label className="text-[10px] font-bold uppercase text-muted-foreground mb-1 block">Amount (INR)</label>
                  <input 
                    type="number" 
                    value={editData.amount} 
                    onChange={e => setEditData({...editData, amount: parseFloat(e.target.value ?? '0')})}
                    className="w-full p-3 rounded-lg border bg-background focus:ring-2 focus:ring-indigo-500 outline-none transition-all font-medium"
                  />
                </div>
                <div>
                  <label className="text-[10px] font-bold uppercase text-muted-foreground mb-1 block">Date</label>
                  <input 
                    type="date" 
                    value={editData.date} 
                    onChange={e => setEditData({...editData, date: e.target.value})}
                    className="w-full p-3 rounded-lg border bg-background focus:ring-2 focus:ring-indigo-500 outline-none transition-all font-medium"
                  />
                </div>
              </div>
              <div>
                <label className="text-[10px] font-bold uppercase text-muted-foreground mb-1 block">Category</label>
                <input 
                  type="text" 
                  value={editData.category} 
                  onChange={e => setEditData({...editData, category: e.target.value})}
                  className="w-full p-3 rounded-lg border bg-background focus:ring-2 focus:ring-indigo-500 outline-none transition-all font-medium"
                />
              </div>
            </div>

            <div className="flex gap-3 mt-8">
              <button 
                onClick={() => setEditingSample(null)}
                className="flex-1 py-3 border rounded-xl font-bold hover:bg-muted transition-colors"
              >
                Cancel
              </button>
              <button 
                onClick={handleUpdateSample}
                className="flex-1 py-3 bg-indigo-600 text-white font-bold rounded-xl hover:bg-indigo-700 transition-colors shadow-lg shadow-indigo-500/20"
              >
                Verify Sample
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
