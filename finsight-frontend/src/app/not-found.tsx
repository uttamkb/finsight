"use client";

import Link from "next/link";
import { MoveLeft, AlertCircle, Building2 } from "lucide-react";

export default function NotFound() {
  return (
    <div className="flex min-h-screen flex-col items-center justify-center bg-base-100 p-4 text-center">
      <div className="animate-fade-in relative">
        {/* Decorative background glow */}
        <div className="absolute -inset-10 bg-primary/20 blur-3xl rounded-full opacity-50 -z-10 animate-pulse" />
        
        <div className="mb-8 flex justify-center">
          <div className="relative">
            <Building2 className="h-24 w-24 text-primary opacity-20" />
            <AlertCircle className="absolute -bottom-2 -right-2 h-12 w-12 text-destructive animate-bounce" />
          </div>
        </div>

        <h1 className="mb-2 text-8xl font-black text-base-content tracking-tighter">404</h1>
        <h2 className="mb-6 text-2xl font-semibold text-base-content/60">Digital Ledger Entry Not Found</h2>
        
        <p className="mb-10 max-w-md text-base-content/60 leading-relaxed">
          The financial record or page you are looking for has been moved, 
          archived, or never existed in our current fiscal year.
        </p>

        <Link 
          href="/dashboard"
          className="inline-flex h-12 items-center gap-2 rounded-full bg-primary px-8 font-medium text-primary-content transition-all hover:scale-105 hover:shadow-lg hover:shadow-primary/30 active:scale-95"
        >
          <MoveLeft className="h-4 w-4" />
          Back to Dashboard
        </Link>
      </div>

      <div className="mt-12 flex items-center gap-2 text-xs text-base-content/60 uppercase tracking-widest opacity-50">
        <Building2 className="h-3 w-3" />
        FinSight Financial Management System
      </div>
    </div>
  );
}
