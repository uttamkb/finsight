"use client";

import { useState, useEffect } from "react";
import { Settings as SettingsIcon, Save, Key, Globe, Layout, Check, AlertCircle, Loader2, Database, Download, Upload, Trash2 } from "lucide-react";
import { useTheme } from "next-themes";
import { useToast } from "@/components/toast-provider";
import { useRef } from "react";
import { apiFetch } from "@/lib/api";

interface AppConfig {
  apartmentName: string;
  geminiApiKey: string;
  driveFolderUrl: string;
  serviceAccountJson: string;
  currency: string;
  ocrMode: string;
  themePreference: string;
}

export default function SettingsPage() {
  const { setTheme } = useTheme();
  const { toast } = useToast();
  const [config, setConfig] = useState<AppConfig>({
    apartmentName: "",
    geminiApiKey: "",
    driveFolderUrl: "",
    serviceAccountJson: "",
    currency: "INR",
    ocrMode: "MODE_LOW_COST",
    themePreference: "DARK",
  });

  const [isLoading, setIsLoading] = useState(true);
  const [isSaving, setIsSaving] = useState(false);
  const [isExporting, setIsExporting] = useState(false);
  const [isImporting, setIsImporting] = useState(false);
  const [isResetting, setIsResetting] = useState(false);
  const [lastSync, setLastSync] = useState<string | null>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    apiFetch("/settings")
      .then((res) => res.json())
      .then((data) => {
        // Ensure currency has a default value if missing from the DB
        const configData = {
           ...data,
           currency: data.currency || "INR"
        };
        setConfig(configData);
        if (configData.themePreference) {
          setTheme(configData.themePreference.toLowerCase());
        }
        setIsLoading(false);
      })
      .catch((err) => {
        console.error("Failed to fetch settings:", err);
        setIsLoading(false);
      });

    // Fetch last sync history
    apiFetch("/sync/history")
      .then(res => res.json())
      .then(data => {
        if (data.lastSync) {
          setLastSync(data.lastSync);
        }
      })
      .catch(err => console.error("Failed to fetch sync history:", err));
  }, [setTheme]);

  const handleSave = async () => {
    setIsSaving(true);
    try {
      const response = await apiFetch("/settings", {
        method: "PUT",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(config),
      });

      if (response.ok) {
        toast("Configuration updated successfully!");
        setTheme(config.themePreference.toLowerCase());
      } else {
        toast("Failed to save configuration.", "error");
      }
    } catch (error) {
      toast("Connection error. Is the backend running?", "error");
    } finally {
      setIsSaving(false);
    }
  };

  const handleExport = async () => {
    setIsExporting(true);
    try {
      const response = await apiFetch("/backup/export");
      if (response.ok) {
        const blob = await response.blob();
        const url = window.URL.createObjectURL(blob);
        const a = document.createElement("a");
        a.href = url;
        const timestamp = new Date().toISOString().replace(/[:.]/g, "-").slice(0, 19);
        a.download = `finsight_backup_${timestamp}.json`;
        document.body.appendChild(a);
        a.click();
        a.remove();
        toast("Backup exported successfully!");
      } else {
        toast("Failed to export backup.", "error");
      }
    } catch (error) {
      toast("Export failed. Backend might be unreachable.", "error");
    } finally {
      setIsExporting(false);
    }
  };

  const handleImport = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;

    if (!confirm("Are you sure? This will OVERWRITE your current database with the backup data. This action cannot be undone.")) {
      return;
    }

    setIsImporting(true);
    try {
      const text = await file.text();
      const backupData = JSON.parse(text);

      const response = await apiFetch("/backup/import", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(backupData),
      });

      if (response.ok) {
        toast("Backup restored successfully! Refreshing settings...");
        window.location.reload();
      } else {
        toast("Failed to restore backup.", "error");
      }
    } catch (error) {
        console.error(error);
      toast("Import failed. Ensure the file is a valid FinSight backup.", "error");
    } finally {
      setIsImporting(false);
      if (fileInputRef.current) fileInputRef.current.value = "";
    }
  };

  const handleReset = async () => {
    if (!confirm("CRITICAL WARNING: This will PERMANENTLY DELETE all data (Receipts, Transactions, Vendors). Your settings will also be reset. Continue?")) {
      return;
    }

    setIsResetting(true);
    try {
      const response = await apiFetch("/backup/reset", { method: "POST" });
      if (response.ok) {
        toast("Database reset successfully. Starting fresh.");
        window.location.reload();
      } else {
        toast("Failed to reset database.", "error");
      }
    } catch (error) {
      toast("Reset failed. Backend might be unreachable.", "error");
    } finally {
      setIsResetting(false);
    }
  };

  if (isLoading) {
    return (
      <div className="flex items-center justify-center min-h-[60vh]">
        <Loader2 className="h-8 w-8 text-primary animate-spin" />
      </div>
    );
  }

  return (
    <div className="container mx-auto py-10 px-4 max-w-5xl animate-fade-in">
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-8 mb-12">
        <div className="flex items-center gap-6">
          <div className="p-4 bg-primary/10 rounded-3xl glow-primary">
            <SettingsIcon className="h-10 w-10 text-primary" />
          </div>
          <div>
            <h1 className="text-4xl font-black tracking-tight leading-tight uppercase">Base Configuration</h1>
            <p className="text-base-content/40 font-bold uppercase tracking-[0.2em] text-[10px] mt-1">Neural Core Configuration</p>
          </div>
        </div>
        <button
          onClick={handleSave}
          disabled={isSaving}
          className="btn btn-primary h-14 px-10 rounded-2xl font-black uppercase text-xs tracking-widest shadow-xl shadow-primary/20 hover:scale-105 active:scale-95 transition-all flex items-center gap-3"
        >
          {isSaving ? <Loader2 className="h-5 w-5 animate-spin" /> : <Save className="h-5 w-5" />}
          {isSaving ? "Syncing..." : "Sync Config"}
        </button>
      </div>

      <div className="grid gap-8">
        {/* Apartment Branding */}
        <div className="glass-panel p-10 rounded-[2.5rem] shadow-2xl border-primary/5 transition-all group overflow-hidden relative">
          <div className="absolute top-0 right-0 p-12 opacity-5 scale-150 rotate-12 group-hover:rotate-0 transition-all duration-1000"><Layout className="h-32 w-32" /></div>
          <div className="flex items-center gap-4 mb-10 border-b border-base-content/5 pb-6">
            <Layout className="h-6 w-6 text-primary shadow-primary" />
            <h2 className="text-xl font-black uppercase tracking-tighter">Entity Identity</h2>
          </div>
          <div className="space-y-8 relative">
            <div className="grid gap-3">
              <label className="text-[10px] font-black text-primary uppercase tracking-[0.2em] pl-1">Association Alpha Name</label>
              <input
                type="text"
                value={config.apartmentName}
                onChange={(e) => setConfig({ ...config, apartmentName: e.target.value })}
                className="w-full h-14 px-6 rounded-2xl bg-base-100/50 border border-base-content/5 focus:ring-2 focus:ring-primary outline-none transition-all font-black text-lg shadow-inner"
                placeholder="SKYLINE_PRIMARY_RE_NODE"
              />
            </div>
            <div className="grid gap-3">
              <label className="text-[10px] font-black text-primary uppercase tracking-[0.2em] pl-1">Fiscal Unit (Currency)</label>
              <select
                value={config.currency}
                onChange={(e) => setConfig({ ...config, currency: e.target.value })}
                className="w-full h-14 px-6 rounded-2xl bg-base-100/50 border border-base-content/5 focus:ring-2 focus:ring-primary outline-none transition-all font-black text-xs uppercase tracking-widest shadow-inner appearance-none cursor-pointer"
              >
                <option value="INR">₹ INR - RUPEE_UNIT</option>
                <option value="USD">$ USD - DOLLAR_UNIT</option>
                <option value="EUR">€ EUR - EURO_UNIT</option>
                <option value="GBP">£ GBP - POUND_UNIT</option>
              </select>
            </div>
          </div>
        </div>

        {/* AI & OCR Configuration */}
        <div className="glass-panel p-10 rounded-[2.5rem] shadow-2xl border-primary/5 transition-all group overflow-hidden relative">
          <div className="absolute top-0 right-0 p-12 opacity-5 scale-150 -rotate-12 group-hover:rotate-0 transition-all duration-1000"><Key className="h-32 w-32" /></div>
          <div className="flex items-center gap-4 mb-10 border-b border-base-content/5 pb-6">
            <Key className="h-6 w-6 text-primary shadow-primary" />
            <h2 className="text-xl font-black uppercase tracking-tighter">Neural Ops</h2>
          </div>
          <div className="space-y-10 relative">
            <div className="grid gap-3">
              <label className="text-[10px] font-black text-primary uppercase tracking-[0.2em] pl-1">Gemini API Access Key</label>
              <input
                type="password"
                value={config.geminiApiKey}
                onChange={(e) => setConfig({ ...config, geminiApiKey: e.target.value })}
                className="w-full h-14 px-6 rounded-2xl bg-base-100/50 border border-base-content/5 focus:ring-2 focus:ring-primary outline-none transition-all font-mono text-sm tracking-widest shadow-inner group-hover:border-primary/20"
                placeholder="••••••••••••••••••••••••••••"
              />
              <p className="text-[10px] font-bold text-base-content/30 uppercase tracking-widest pl-1 mt-1 italic">Authorized for deep-layer categorization & neural fallback.</p>
            </div>

            <div className="grid gap-3">
              <label className="text-[10px] font-black text-primary uppercase tracking-[0.2em] pl-1">OCR Processing Vector</label>
              <select
                value={config.ocrMode}
                onChange={(e) => setConfig({ ...config, ocrMode: e.target.value })}
                className="w-full h-14 px-6 rounded-2xl bg-base-100/50 border border-base-content/5 focus:ring-2 focus:ring-primary outline-none transition-all font-black text-xs uppercase tracking-widest shadow-inner appearance-none cursor-pointer"
              >
                <option value="MODE_LOW_COST">ECO_MODE (Local TrOCR Only)</option>
                <option value="MODE_HYBRID">SYNAPSE_MODE (Local + Gemini Fallback)</option>
                <option value="MODE_HIGH_ACCURACY">MAX_INTELLIGENCE (Gemini Only)</option>
              </select>
            </div>

            <div className="grid gap-3">
              <label className="text-[10px] font-black text-primary uppercase tracking-[0.2em] pl-1">Interface Spectrum (Theme)</label>
              <select
                value={config.themePreference}
                onChange={(e) => setConfig({ ...config, themePreference: e.target.value })}
                className="w-full h-14 px-6 rounded-2xl bg-base-100/50 border border-base-content/5 focus:ring-2 focus:ring-primary outline-none transition-all font-black text-xs uppercase tracking-widest shadow-inner appearance-none cursor-pointer"
              >
                <option value="DARK">CYBER_DARK (Neon Precision)</option>
                <option value="LIGHT">STARK_LIGHT (Corporate Logic)</option>
              </select>
            </div>
          </div>
        </div>

        {/* Connectivity */}
        <div className="glass-panel p-10 rounded-[2.5rem] shadow-2xl border-primary/5 transition-all group overflow-hidden relative">
          <div className="absolute top-0 right-0 p-12 opacity-5 scale-150 rotate-45 group-hover:rotate-0 transition-all duration-1000"><Globe className="h-32 w-32" /></div>
          <div className="flex items-center gap-4 mb-10 border-b border-base-content/5 pb-6">
            <Globe className="h-6 w-6 text-primary shadow-primary" />
            <h2 className="text-xl font-black uppercase tracking-tighter">Remote Link</h2>
          </div>
          <div className="space-y-8 relative">
            {lastSync && (
              <div className="flex items-center gap-4 p-6 bg-primary/5 rounded-2xl border border-primary/10 mb-4 glow-primary/5">
                <div className="h-3 w-3 rounded-full bg-primary animate-pulse"></div>
                <span className="text-[11px] font-black uppercase tracking-widest text-primary">
                  Last Uplink Synchronicity: <span className="font-mono text-base-content/40 ml-2 italic">{new Date(lastSync).toLocaleString()}</span>
                </span>
              </div>
            )}
            <div className="grid gap-3">
              <label className="text-[10px] font-black text-primary uppercase tracking-[0.2em] pl-1">Drive Vector URL (Receipts)</label>
              <input
                type="text"
                value={config.driveFolderUrl}
                onChange={(e) => setConfig({ ...config, driveFolderUrl: e.target.value })}
                className="w-full h-14 px-6 rounded-2xl bg-base-100/50 border border-base-content/5 focus:ring-2 focus:ring-primary outline-none transition-all font-black text-[11px] tracking-tight shadow-inner"
                placeholder="https://drive.google.com/drive/folders/TARGET_DIRECTORY"
              />
            </div>

            <div className="grid gap-3">
              <label className="text-[10px] font-black text-primary uppercase tracking-[0.2em] pl-1">Service Account Authentication Config (JSON)</label>
              <textarea
                value={config.serviceAccountJson}
                onChange={(e) => setConfig({ ...config, serviceAccountJson: e.target.value })}
                className="textarea w-full min-h-[160px] px-6 py-4 rounded-2xl bg-base-100/50 border border-base-content/5 focus:ring-2 focus:ring-primary outline-none transition-all font-mono text-[10px] leading-relaxed shadow-inner"
                placeholder='{ "type": "service_account", ... }'
              />
              <p className="text-[10px] font-bold text-base-content/30 uppercase tracking-widest pl-1 mt-1 italic">
                Authorized credentials for programmatic Drive extraction. Target folder must grant 'EDITOR' access to the Service Identity.
              </p>
            </div>
          </div>
        </div>

        {/* Data Management */}
        <div className="glass-panel p-10 rounded-[2.5rem] shadow-2xl border-primary/5 transition-all overflow-hidden relative">
          <div className="flex items-center gap-4 mb-10 border-b border-base-content/5 pb-6">
            <Database className="h-6 w-6 text-primary shadow-primary" />
            <h2 className="text-xl font-black uppercase tracking-tighter">Ledger Management</h2>
          </div>
          <div className="grid grid-cols-1 md:grid-cols-3 gap-12 relative">
            <div className="flex flex-col gap-5 p-6 rounded-2xl bg-base-100/30 border border-base-content/5 hover:bg-base-100/50 transition-all text-center">
              <h3 className="text-[10px] font-black text-primary uppercase tracking-[0.2em]">Archive Sync</h3>
              <p className="text-[10px] font-bold text-base-content/30 uppercase tracking-[0.15em] mb-4 leading-relaxed">Synthesize portable JSON backup of core neural ledger.</p>
              <button
                onClick={handleExport}
                disabled={isExporting}
                className="btn btn-outline h-12 rounded-xl font-black uppercase text-[10px] tracking-widest border-primary/20 hover:bg-primary hover:text-white transition-all"
              >
                {isExporting ? <Loader2 className="h-4 w-4 animate-spin" /> : <Download className="h-4 w-4" />}
                {isExporting ? "Compiling..." : "Export"}
              </button>
            </div>

            <div className="flex flex-col gap-5 p-6 rounded-2xl bg-base-100/30 border border-base-content/5 hover:bg-base-100/50 transition-all text-center">
              <h3 className="text-[10px] font-black text-info uppercase tracking-[0.2em]">Matrix Restore</h3>
              <p className="text-[10px] font-bold text-base-content/30 uppercase tracking-[0.15em] mb-4 leading-relaxed">Restore ledger integrity from archived transactional signatures.</p>
              <input
                type="file"
                ref={fileInputRef}
                onChange={handleImport}
                className="hidden"
                accept=".json"
              />
              <button
                onClick={() => fileInputRef.current?.click()}
                disabled={isImporting}
                className="btn btn-outline h-12 rounded-xl font-black uppercase text-[10px] tracking-widest border-info/20 hover:bg-info hover:text-white transition-all"
              >
                {isImporting ? <Loader2 className="h-4 w-4 animate-spin" /> : <Upload className="h-4 w-4" />}
                {isImporting ? "Injecting..." : "Restore"}
              </button>
            </div>

            <div className="flex flex-col gap-5 p-6 rounded-2xl bg-error/5 border border-error/5 hover:bg-error/10 transition-all text-center">
              <h3 className="text-[10px] font-black text-error uppercase tracking-[0.2em]">Zero Phase</h3>
              <p className="text-[10px] font-bold text-error/30 uppercase tracking-[0.15em] mb-4 leading-relaxed">Permanently purge all neural data & configuration settings.</p>
              <button
                onClick={handleReset}
                disabled={isResetting}
                className="btn btn-error h-12 rounded-xl font-black uppercase text-[10px] tracking-widest text-white shadow-xl shadow-error/20 hover:scale-105 active:scale-95"
              >
                {isResetting ? <Loader2 className="h-4 w-4 animate-spin" /> : <Trash2 className="h-4 w-4" />}
                {isResetting ? "Purging..." : "Reset"}
              </button>
            </div>
          </div>
        </div>

        <div className="glass-panel p-8 rounded-[2rem] border border-dashed border-base-content/10 text-center opacity-40 hover:opacity-100 transition-opacity">
            <span className="text-[9px] font-black text-base-content/40 uppercase tracking-[0.4em] italic">Multi-Tenancy Neural Uplink scheduled for Phase 06 implementation.</span>
        </div>
      </div>
    </div>
  );
}
