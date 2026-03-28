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
    <div className="container mx-auto py-10 px-4 max-w-7xl animate-fade-in">
      {/* Header */}
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-8 mb-12">
        <div className="flex items-center gap-6">
          <div className="p-4 bg-primary/10 rounded-3xl glow-primary">
            <BrainCircuit className="h-12 w-12 text-primary" />
          </div>
          <div>
            <h1 className="text-4xl font-black tracking-tight leading-tight uppercase">Neural Forge</h1>
            <p className="text-base-content/40 font-bold uppercase tracking-[0.2em] text-[10px] mt-1 flex items-center gap-2">
              <Cpu className="h-3.5 w-3.5 text-primary animate-pulse" /> OCR Logic Refinement Matrix
            </p>
          </div>
        </div>
        
        <div className="flex flex-wrap gap-4 print:hidden">
          <button 
            onClick={() => setShowHelp(true)}
            className="btn btn-ghost h-12 px-6 rounded-2xl border border-base-content/5 font-black uppercase text-[10px] tracking-widest hover:bg-primary/5"
          >
            <HelpCircle className="h-4 w-4" />
            Intel Guide
          </button>
          <button 
            onClick={handleHarvestManual}
            disabled={isHarvesting}
            className="btn btn-ghost h-12 px-6 rounded-2xl border border-base-content/5 font-black uppercase text-[10px] tracking-widest hover:bg-primary/5"
          >
            {isHarvesting ? <Loader2 className="h-4 w-4 animate-spin text-primary" /> : <Download className="h-4 w-4" />}
            Pull Samples
          </button>
          <button 
            onClick={fetchSamples}
            className="btn btn-ghost h-12 w-12 rounded-2xl border border-base-content/5 flex items-center justify-center hover:bg-primary/5"
          >
            <RefreshCw className={`h-4 w-4 ${isLoading ? 'animate-spin' : ''}`} />
          </button>
          <button 
            onClick={handlePrepareDataset}
            disabled={isPreparing || samples.length === 0}
            className="btn btn-primary h-12 px-10 rounded-2xl font-black uppercase text-[10px] tracking-widest shadow-xl shadow-primary/20 hover:scale-105 active:scale-95"
          >
            {isPreparing ? <Loader2 className="h-4 w-4 animate-spin" /> : <Play className="h-4 w-4" />}
            {isPreparing ? "Processing..." : "Commit Logic"}
          </button>
        </div>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-8">
        {/* Statistics & Guides */}
        <div className="lg:col-span-1 space-y-8">
          <div className="glass-panel p-8 rounded-[2.5rem] shadow-xl relative border-primary/5">
            <h2 className="text-xl font-black mb-8 flex items-center gap-4 uppercase tracking-tighter">
              <Cpu className="h-6 w-6 text-primary" />
              Machine Intel
            </h2>
            <div className="space-y-6">
              <div className="flex justify-between items-center p-6 rounded-2xl bg-base-100/30 border border-base-content/5 shadow-inner">
                <span className="text-[10px] font-black uppercase tracking-widest text-base-content/40">Samples</span>
                <span className="text-3xl font-black font-mono tracking-tighter text-primary">{samples.length}</span>
              </div>
              <div className="flex justify-between items-center p-6 rounded-2xl bg-base-100/30 border border-base-content/5 shadow-inner">
                <span className="text-[10px] font-black uppercase tracking-widest text-base-content/40">Verified</span>
                <span className="text-3xl font-black font-mono tracking-tighter text-success">{dictionaryStats.count}</span>
              </div>
              <div className="flex justify-between items-center p-6 rounded-2xl bg-base-100/30 border border-base-content/5 shadow-inner">
                <span className="text-[10px] font-black uppercase tracking-widest text-base-content/40">Status</span>
                <span className="text-[10px] font-black uppercase tracking-[0.2em] bg-success/10 text-success px-3 py-1.5 rounded-lg border border-success/20">Optimized</span>
              </div>
            </div>
            
            <div className="mt-8 p-6 bg-warning/5 border border-warning/10 rounded-[2rem] group hover:glow-warning transition-all">
              <div className="flex gap-3 text-warning mb-4">
                <AlertCircle className="h-5 w-5 shrink-0" />
                <span className="text-[10px] font-black uppercase tracking-widest">Advisory</span>
              </div>
              <p className="text-[10px] text-base-content/40 font-bold uppercase tracking-widest leading-loose">
                Target 50-100 samples per association vector for peak neural recognition accuracy.
              </p>
            </div>
          </div>

          <div className="glass-panel p-8 rounded-[2.5rem] shadow-xl relative border-primary/5">
            <h2 className="text-xl font-black mb-8 flex items-center gap-4 uppercase tracking-tighter">
              <History className="h-6 w-6 text-primary" />
              Directives
            </h2>
            <div className="space-y-6">
              {[
                { 
                  title: "DATASET PREP", 
                  desc: "Normalize harvested samples to neural format.", 
                  done: samples.length > 0,
                  action: handlePrepareDataset,
                  label: "Prepare"
                },
                { 
                  title: "UPDATE LOGIC", 
                  desc: "Commit entities to ground-truth dictionary.", 
                  done: dictionaryStats.count > 0,
                  action: handleRunTraining,
                  label: "Apply"
                },
                { 
                  title: "DEPLOY ENGINE", 
                  desc: "Sync refined matrix with OCR kernel.", 
                  done: false,
                  action: handleDeployModel,
                  label: "Deploy"
                }
              ].map((step, i) => (
                <div key={i} className="flex flex-col gap-4 p-6 rounded-2xl bg-base-100/30 border border-base-content/5 group hover:bg-base-100/50 transition-all">
                  <div className="flex gap-4">
                    <div className={`mt-0.5 h-6 w-6 rounded-full flex items-center justify-center shrink-0 ${step.done ? 'bg-primary text-primary-content shadow-lg shadow-primary/20' : 'border-2 border-base-content/10 text-base-content/20'}`}>
                      {step.done ? <CheckCircle2 className="h-3.5 w-3.5" /> : <span className="text-[10px] font-black">{i+1}</span>}
                    </div>
                    <div className="flex-1">
                      <h3 className="text-xs font-black uppercase tracking-[0.1em] transition-colors group-hover:text-primary">{step.title}</h3>
                      <p className="text-[10px] font-bold text-base-content/30 uppercase tracking-widest mt-1 mb-6 leading-relaxed">{step.desc}</p>
                      <button 
                        onClick={step.action}
                        disabled={isPreparing}
                        className="w-full py-2.5 px-4 bg-primary text-primary-content rounded-xl text-[10px] font-black uppercase tracking-widest shadow-lg shadow-primary/10 hover:scale-105 active:scale-95 transition-all disabled:opacity-50"
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
        <div className="lg:col-span-2 space-y-8">
          <div className="glass-panel p-10 rounded-[3rem] shadow-2xl min-h-[600px] border-primary/5">
            <h2 className="text-3xl font-black mb-10 flex items-center gap-4 uppercase tracking-tighter">
              <Database className="h-8 w-8 text-primary shadow-primary" />
              Neural Matrix Buffer
            </h2>

            {isLoading ? (
              <div className="flex flex-col items-center justify-center py-32 gap-6 opacity-30">
                <Loader2 className="h-16 w-16 animate-spin text-primary" />
                <p className="text-xs font-black uppercase tracking-[0.3em]">Synapsing Workspace Samples...</p>
              </div>
            ) : samples.length === 0 ? (
              <div className="flex flex-col items-center justify-center py-32 text-center space-y-8 border-2 border-dashed rounded-[3rem] border-base-content/5 bg-base-100/30">
                <div className="p-8 bg-base-300/50 rounded-full glow-primary opacity-20">
                  <ImageIcon className="h-16 w-16 text-primary" />
                </div>
                <div>
                  <h3 className="text-xl font-black uppercase tracking-tight">Zero State Detected</h3>
                  <p className="text-xs font-bold text-base-content/30 uppercase tracking-widest mt-4 max-w-sm mx-auto leading-loose">
                    Golden samples accumulate through verified transactional signatures.
                  </p>
                </div>
              </div>
            ) : (
              <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
                {samples.map((sample) => (
                  <div key={sample.sampleId} className="group p-8 rounded-[2rem] border border-base-content/5 bg-base-100/30 hover:glow-primary hover:scale-[1.02] transition-all relative overflow-hidden shadow-inner">
                    {sample.verified && (
                      <div className="absolute top-0 right-0 px-4 py-2 bg-success text-white text-[9px] font-black uppercase tracking-widest rounded-bl-2xl flex items-center gap-2 shadow-xl">
                        <CheckCircle className="h-3 w-3" /> Integrity Verified
                      </div>
                    )}
                    {!sample.verified && (
                      <div className="absolute top-0 right-0 px-4 py-2 bg-warning text-white text-[9px] font-black uppercase tracking-widest rounded-bl-2xl flex items-center gap-2 shadow-xl">
                        <Clock className="h-3 w-3" /> Raw Buffer
                      </div>
                    )}
                    
                    <div className="flex items-start justify-between mb-6">
                      <div className="p-4 bg-primary/10 rounded-2xl text-primary group-hover:bg-primary group-hover:text-primary-content transition-all shadow-inner">
                        <FileJson className="h-6 w-6" />
                      </div>
                      <div className="flex gap-2 opacity-0 group-hover:opacity-100 transition-all scale-90 group-hover:scale-100">
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
                          className="p-2.5 bg-primary/10 text-primary rounded-xl hover:bg-primary hover:text-white shadow-sm"
                          title="Verify / Edit"
                        >
                          <Edit2 className="h-4 w-4" />
                        </button>
                        <button 
                          onClick={() => handleDeleteSample(sample.sampleId)}
                          className="p-2.5 bg-error/10 text-error rounded-xl hover:bg-error hover:text-white shadow-sm"
                          title="Delete"
                        >
                          <Trash2 className="h-4 w-4" />
                        </button>
                      </div>
                    </div>
                    
                    <h3 className="text-lg font-black tracking-tight group-hover:text-primary transition-colors truncate">{sample.vendor || "NULL_ENTITY"}</h3>
                    <div className="flex items-center gap-4 text-[10px] font-bold uppercase tracking-widest text-base-content/30 mt-2 mb-8">
                      <span className="bg-base-300 px-2 py-1 rounded-lg border border-base-content/5">{sample.category || "UNIDENTIFIED"}</span>
                      <span>•</span>
                      <span className="text-primary font-black font-mono">
                        {new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR' }).format(sample.amount)}
                      </span>
                    </div>
                    
                    <div className="flex items-center justify-between pt-6 border-t border-base-content/5">
                      <span className="text-[10px] font-black font-mono text-base-content/20 tracking-tighter">
                        {sample.date || "DATE_MISSING"}
                      </span>
                      <span className="text-[10px] font-black font-mono text-primary/30 truncate max-w-[120px]" title={sample.original_filename}>
                        {sample.original_filename}
                      </span>
                    </div>
                  </div>
                ))}
              </div>
            )}
          </div>

          {preparationOutput && (
            <div className="glass-panel p-10 rounded-[3rem] shadow-2xl border-primary/10 animate-fade-in relative overflow-hidden group">
              <div className="absolute top-0 right-0 p-12 opacity-5 scale-150 rotate-12 group-hover:rotate-0 transition-all duration-1000"><Cpu className="h-32 w-32" /></div>
              <h2 className="text-[10px] font-black mb-6 flex items-center gap-3 text-primary uppercase tracking-[0.3em]">
                <Play className="h-3 w-3 fill-current animate-pulse" />
                Data Stream Output
              </h2>
              <pre className="text-[11px] font-mono p-8 bg-black/95 text-primary rounded-2xl overflow-x-auto whitespace-pre-wrap max-h-72 custom-scrollbar shadow-inner border border-primary/20 leading-relaxed">
                {preparationOutput}
              </pre>
            </div>
          )}
        </div>
      </div>

      {/* Help Modal */}
      {showHelp && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/80 backdrop-blur-xl animate-fade-in">
          <div className="glass-panel border-primary/20 rounded-[3rem] shadow-2xl max-w-2xl w-full p-12 relative animate-in zoom-in-95 duration-500 overflow-hidden">
            <div className="absolute top-0 right-0 p-12 opacity-5 scale-150 rotate-12"><HelpCircle className="h-32 w-32" /></div>
            <button 
              onClick={() => setShowHelp(false)}
              className="absolute top-8 right-8 p-3 hover:bg-base-300 rounded-2xl text-base-content/60 transition-colors"
            >
              <X className="h-6 w-6" />
            </button>
            <div className="flex items-center gap-6 mb-10">
              <div className="p-4 bg-primary/10 rounded-2xl text-primary">
                <HelpCircle className="h-8 w-8" />
              </div>
              <h2 className="text-3xl font-black uppercase tracking-tight">Neural Configuration</h2>
            </div>
            <div className="space-y-8 text-sm leading-relaxed">
              <section>
                <h3 className="font-black text-primary mb-3 uppercase tracking-[0.2em] text-[10px]">Matrix Logic</h3>
                <p className="text-base-content/60 font-medium italic">Instead of basic retraining, we implement Rule-Based Refinement. Verified entities are synthesized into a Ground-Truth Dictionary, guaranteeing 100% recognition accuracy for recurring financial vectors.</p>
              </section>
              <section>
                <h3 className="font-black text-primary mb-4 uppercase tracking-[0.2em] text-[10px]">Processing Sequence</h3>
                <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
                  {[
                    { step: "01", t: "HARVEST", d: "Vacuuming Drive repositories for high-confidence samples." },
                    { step: "02", t: "VERIFY", d: "Human-in-the-loop correction of neural extraction errors." },
                    { step: "03", t: "SYNTHESIS", d: "Exporting verified entities to the core ground-truth dictionary." },
                    { step: "04", t: "VECTOR ACCURACY", d: "Gemini 1.5 High-Accuracy mode updates this buffer autonomously." }
                  ].map((s) => (
                    <div key={s.step} className="p-6 bg-base-100/30 rounded-2xl border border-base-content/5 group hover:border-primary/20 transition-all">
                      <div className="text-[10px] font-black text-primary mb-2 opacity-40">{s.step}</div>
                      <div className="font-black uppercase tracking-widest text-xs mb-2">{s.t}</div>
                      <p className="text-[10px] text-base-content/40 font-bold uppercase leading-relaxed">{s.d}</p>
                    </div>
                  ))}
                </div>
              </section>
            </div>
            <button 
              onClick={() => setShowHelp(false)}
              className="btn btn-primary mt-12 w-full h-14 rounded-2xl font-black uppercase text-xs tracking-widest shadow-xl shadow-primary/20 hover:scale-105 active:scale-95 transition-all"
            >
              Initialize Neural Forge
            </button>
          </div>
        </div>
      )}      {/* Edit/Verify Modal */}
      {editingSample && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/80 backdrop-blur-xl animate-fade-in">
          <div className="glass-panel border-primary/20 rounded-[3rem] shadow-2xl max-w-lg w-full p-12 relative animate-in zoom-in-95 duration-500 overflow-hidden">
            <div className="absolute top-0 right-0 p-12 opacity-5 scale-150 group-hover:rotate-12 transition-transform duration-1000"><Edit2 className="h-32 w-32" /></div>
            <button 
              onClick={() => setEditingSample(null)}
              className="absolute top-8 right-8 p-3 hover:bg-base-300 rounded-full text-base-content/60 transition-colors"
            >
              <X className="h-6 w-6" />
            </button>
            <h2 className="text-2xl font-black uppercase tracking-tight mb-2">Ground Truth Sync</h2>
            <p className="text-xs font-bold text-base-content/30 uppercase tracking-widest mb-10">Neural Calibration Logic Sequence</p>
            
            <div className="space-y-6">
              <div className="group">
                <label className="text-[10px] font-black uppercase tracking-[0.2em] text-primary mb-3 block">Entity Signature</label>
                <input 
                  type="text" 
                  value={editData.vendor} 
                  onChange={e => setEditData({...editData, vendor: e.target.value})}
                  className="w-full h-14 px-6 rounded-2xl bg-base-100/50 border border-base-content/5 focus:ring-2 focus:ring-primary outline-none transition-all font-black text-lg group-hover:border-primary/20 shadow-inner"
                  placeholder="VENDOR_ID"
                />
              </div>
              <div className="grid grid-cols-2 gap-6">
                <div className="group">
                  <label className="text-[10px] font-black uppercase tracking-[0.2em] text-primary mb-3 block">Fiscal Magnitude (INR)</label>
                  <input 
                    type="number" 
                    value={editData.amount} 
                    onChange={e => setEditData({...editData, amount: parseFloat(e.target.value ?? '0')})}
                    className="w-full h-14 px-6 rounded-2xl bg-base-100/50 border border-base-content/5 focus:ring-2 focus:ring-primary outline-none transition-all font-black text-lg group-hover:border-primary/20 shadow-inner"
                  />
                </div>
                <div className="group">
                  <label className="text-[10px] font-black uppercase tracking-[0.2em] text-primary mb-3 block">Temporal Coordinate</label>
                  <input 
                    type="date" 
                    value={editData.date} 
                    onChange={e => setEditData({...editData, date: e.target.value})}
                    className="w-full h-14 px-6 rounded-2xl bg-base-100/50 border border-base-content/5 focus:ring-2 focus:ring-primary outline-none transition-all font-bold group-hover:border-primary/20 shadow-inner"
                  />
                </div>
              </div>
              <div className="group">
                <label className="text-[10px] font-black uppercase tracking-[0.2em] text-primary mb-3 block">Categorical Vector</label>
                <input 
                  type="text" 
                  value={editData.category} 
                  onChange={e => setEditData({...editData, category: e.target.value})}
                  className="w-full h-14 px-6 rounded-2xl bg-base-100/50 border border-base-content/5 focus:ring-2 focus:ring-primary outline-none transition-all font-black text-lg group-hover:border-primary/20 shadow-inner"
                  placeholder="CATEGORY_TYPE"
                />
              </div>
            </div>

            <div className="flex gap-4 mt-12">
              <button 
                onClick={() => setEditingSample(null)}
                className="flex-1 h-14 rounded-2xl font-black uppercase text-xs tracking-widest border border-base-content/10 hover:bg-base-200 transition-all"
              >
                Abort
              </button>
              <button 
                onClick={handleUpdateSample}
                className="flex-1 h-14 bg-primary text-white rounded-2xl font-black uppercase text-xs tracking-widest shadow-xl shadow-primary/20 hover:scale-105 active:scale-95 transition-all"
              >
                Verify Vector
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
