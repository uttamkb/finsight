"use client";

import { useState, useEffect } from "react";
import { Settings as SettingsIcon, Save, Key, Globe, Layout, Check, AlertCircle, Loader2 } from "lucide-react";
import { useTheme } from "next-themes";
import { useToast } from "@/components/toast-provider";
import { API_BASE_URL } from "@/lib/constants";

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
  const [lastSync, setLastSync] = useState<string | null>(null);

  useEffect(() => {
    fetch(`${API_BASE_URL}/settings`)
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
    fetch(`${API_BASE_URL}/sync/history`)
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
      const response = await fetch(`${API_BASE_URL}/settings`, {
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

  if (isLoading) {
    return (
      <div className="flex items-center justify-center min-h-[60vh]">
        <Loader2 className="h-8 w-8 text-primary animate-spin" />
      </div>
    );
  }

  return (
    <div className="container mx-auto py-10 px-4 max-w-4xl">
      <div className="flex items-center justify-between mb-8">
        <div className="flex items-center gap-2">
          <SettingsIcon className="h-8 w-8 text-primary" />
          <h1 className="text-3xl font-bold tracking-tight">System Settings</h1>
        </div>
        <button
          onClick={handleSave}
          disabled={isSaving}
          className="inline-flex items-center gap-2 px-6 py-2 bg-primary text-primary-foreground rounded-md font-medium hover:bg-primary/90 transition-all disabled:opacity-50"
        >
          {isSaving ? <Loader2 className="h-4 w-4 animate-spin" /> : <Save className="h-4 w-4" />}
          {isSaving ? "Saving..." : "Save Configuration"}
        </button>
      </div>

      <div className="grid gap-6">
        {/* Apartment Branding */}
        <div className="rounded-xl border bg-card p-6 shadow-sm border-primary/10">
          <div className="flex items-center gap-2 mb-6 border-b border-primary/10 pb-4">
            <Layout className="h-5 w-5 text-primary" />
            <h2 className="text-xl font-semibold">Apartment Branding</h2>
          </div>
          <div className="space-y-4">
            <div className="grid gap-2">
              <label className="text-sm font-medium text-muted-foreground">Apartment / Association Name</label>
              <input
                type="text"
                value={config.apartmentName}
                onChange={(e) => setConfig({ ...config, apartmentName: e.target.value })}
                className="flex h-12 w-full rounded-lg border border-input bg-background/50 px-4 py-2 text-sm focus:ring-2 focus:ring-primary focus:outline-none transition-all"
                placeholder="e.g., Skyline Residents Association"
              />
            </div>
            <div className="grid gap-2">
              <label className="text-sm font-medium text-muted-foreground">Default Currency</label>
              <select
                value={config.currency}
                onChange={(e) => setConfig({ ...config, currency: e.target.value })}
                className="flex h-12 w-full rounded-lg border border-input bg-background/50 px-4 py-2 text-sm focus:ring-2 focus:ring-primary focus:outline-none transition-all cursor-pointer"
              >
                <option value="INR">₹ INR - Indian Rupee</option>
                <option value="USD">$ USD - US Dollar</option>
                <option value="EUR">€ EUR - Euro</option>
                <option value="GBP">£ GBP - British Pound</option>
              </select>
            </div>
          </div>
        </div>

        {/* AI & OCR Configuration */}
        <div className="rounded-xl border bg-card p-6 shadow-sm border-primary/10">
          <div className="flex items-center gap-2 mb-6 border-b border-primary/10 pb-4">
            <Key className="h-5 w-5 text-primary" />
            <h2 className="text-xl font-semibold">AI & OCR Configuration</h2>
          </div>
          <div className="space-y-6">
            <div className="grid gap-2">
              <label className="text-sm font-medium text-muted-foreground">Gemini API Key</label>
              <input
                type="password"
                value={config.geminiApiKey}
                onChange={(e) => setConfig({ ...config, geminiApiKey: e.target.value })}
                className="flex h-12 w-full rounded-lg border border-input bg-background/50 px-4 py-2 text-sm focus:ring-2 focus:ring-primary focus:outline-none transition-all"
                placeholder="••••••••••••••••••••••••••••"
              />
              <p className="text-xs text-muted-foreground">Used for high-accuracy fallback and intelligent categorization.</p>
            </div>

            <div className="grid gap-2">
              <label className="text-sm font-medium text-muted-foreground">OCR Processing Mode</label>
              <select
                value={config.ocrMode}
                onChange={(e) => setConfig({ ...config, ocrMode: e.target.value })}
                className="flex h-12 w-full rounded-lg border border-input bg-background/50 px-4 py-2 text-sm focus:ring-2 focus:ring-primary focus:outline-none transition-all cursor-pointer"
              >
                <option value="MODE_LOW_COST">Low Cost (Local TrOCR Only)</option>
                <option value="MODE_HYBRID">Hybrid (Local + Gemini Fallback)</option>
                <option value="MODE_HIGH_ACCURACY">Max Accuracy (Gemini Only)</option>
              </select>
            </div>

            <div className="grid gap-2">
              <label className="text-sm font-medium text-muted-foreground">UI Theme Preference</label>
              <select
                value={config.themePreference}
                onChange={(e) => setConfig({ ...config, themePreference: e.target.value })}
                className="flex h-12 w-full rounded-lg border border-input bg-background/50 px-4 py-2 text-sm focus:ring-2 focus:ring-primary focus:outline-none transition-all cursor-pointer"
              >
                <option value="DARK">Dark (Neon Accents)</option>
                <option value="LIGHT">Light (Clean Corporate)</option>
              </select>
            </div>
          </div>
        </div>

        {/* Connectivity */}
        <div className="rounded-xl border bg-card p-6 shadow-sm border-primary/10">
          <div className="flex items-center gap-2 mb-6 border-b border-primary/10 pb-4">
            <Globe className="h-5 w-5 text-primary" />
            <h2 className="text-xl font-semibold">Connectivity</h2>
          </div>
          <div className="space-y-4">
            {lastSync && (
              <div className="flex items-center gap-2 p-3 bg-primary/5 rounded-lg border border-primary/10 mb-2">
                <AlertCircle className="h-4 w-4 text-primary" />
                <span className="text-sm font-medium">
                  Last successfully synchronized: <span className="text-foreground">{new Date(lastSync).toLocaleString()}</span>
                </span>
              </div>
            )}
            <div className="grid gap-2">
              <label className="text-sm font-medium text-muted-foreground">Google Drive Receipts Folder URL</label>
              <input
                type="text"
                value={config.driveFolderUrl}
                onChange={(e) => setConfig({ ...config, driveFolderUrl: e.target.value })}
                className="flex h-12 w-full rounded-lg border border-input bg-background/50 px-4 py-2 text-sm focus:ring-2 focus:ring-primary focus:outline-none transition-all"
                placeholder="https://drive.google.com/drive/folders/..."
              />
            </div>

            <div className="grid gap-2">
              <label className="text-sm font-medium text-muted-foreground">Google Service Account JSON</label>
              <textarea
                value={config.serviceAccountJson}
                onChange={(e) => setConfig({ ...config, serviceAccountJson: e.target.value })}
                className="flex min-h-[120px] w-full rounded-lg border border-input bg-background/50 px-4 py-2 text-sm focus:ring-2 focus:ring-primary focus:outline-none transition-all font-mono"
                placeholder='{
  "type": "service_account",
  "project_id": "your-project",
  "private_key_id": "...",
  "private_key": "-----BEGIN PRIVATE KEY-----\n...\n-----END PRIVATE KEY-----\n",
  "client_email": "..."
}'
              />
              <p className="text-xs text-muted-foreground">
                Required to programmatically access your Google Drive folder. Ensure the Service Account Email is shared as an "Editor" on the Drive Folder.
              </p>
            </div>
          </div>
        </div>

        <div className="rounded-xl border bg-card p-6 border-dashed border-muted-foreground/25 text-center">
            <span className="text-sm text-muted-foreground italic">User management and multi-tenancy controls are coming in Phase 6.</span>
        </div>
      </div>
    </div>
  );
}
