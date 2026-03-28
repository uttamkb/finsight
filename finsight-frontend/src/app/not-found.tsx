"use client";

import Link from "next/link";
import { MoveLeft, AlertCircle, Building2 } from "lucide-react";

export default function NotFound() {
  return (
    <div className="flex min-h-[80vh] flex-col items-center justify-center p-8 text-center animate-fade-in">
      <div className="glass-panel p-16 rounded-[4rem] shadow-2xl relative border-primary/5 max-w-2xl w-full overflow-hidden group">
        {/* Decorative background glow */}
        <div className="absolute -inset-20 bg-primary/10 blur-[100px] rounded-full opacity-20 group-hover:opacity-40 transition-opacity -z-10" />
        
        <div className="mb-12 flex justify-center">
          <div className="relative p-6 bg-primary/5 rounded-3xl glow-primary/10">
            <Building2 className="h-20 w-20 text-primary opacity-20" />
            <AlertCircle className="absolute -bottom-4 -right-4 h-12 w-12 text-error animate-pulse shadow-error" />
          </div>
        </div>

        <h1 className="mb-4 text-[12rem] font-black text-primary leading-none tracking-tighter opacity-10 select-none">404</h1>
        <div className="absolute top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2 w-full px-10">
            <h2 className="mb-8 text-4xl font-black text-base-content uppercase tracking-tight">Ledger Matrix De-Synced</h2>
            <p className="mb-12 text-[10px] font-black text-base-content/30 uppercase tracking-[0.3em] leading-relaxed">
              The requested data point or fiscal coordinate resides outside the current operational neural grid boundaries.
            </p>

            <Link 
              href="/dashboard"
              className="btn btn-primary h-14 px-12 rounded-2xl font-black uppercase text-xs tracking-[0.2em] shadow-xl shadow-primary/30 hover:scale-105 active:scale-95 transition-all flex items-center gap-4 mx-auto w-fit"
            >
              <MoveLeft className="h-5 w-5" />
              Return to Central Command
            </Link>
        </div>
      </div>

      <div className="mt-16 flex items-center gap-4 text-[9px] font-black text-base-content/20 uppercase tracking-[0.5em] animate-pulse">
        <div className="h-px w-12 bg-base-content/10"></div>
        FINSIGHT_NEURAL_ERROR_REPORT
        <div className="h-px w-12 bg-base-content/10"></div>
      </div>
    </div>
  );
}
