"use client";

import { useState } from "react";
import Link from "next/link";
import { ArrowRight, BarChart3, Receipt, CheckCircle2, X, Sparkles } from "lucide-react";

export default function Home() {
  const [selectedFeature, setSelectedFeature] = useState<null | {
    id: string;
    title: string;
    description: string;
    explanation: string;
    icon: any;
  }>(null);

  const features = [
    {
      id: "ocr",
      title: "Smart OCR",
      description: "Automatically turn your paper receipts and digital photos into accurate expense records without manual typing.",
      explanation: "The system watches your Google Drive folder. When you drop a photo of a receipt or a PDF invoice, our AI 'reads' it just like a human would. It identifies the vendor, the date, and the total amount. Even if the receipt is handwritten or slightly crumpled, the system cross-references multiple AI models to ensure the data is 100% correct before saving it.",
      icon: Receipt,
    },
    {
      id: "recon",
      title: "Reconciliation",
      description: "Verify that every dollar spent matches a legitimate receipt, ensuring your association's money is always accounted for.",
      explanation: "This is the 'truth checker' of the system. We compare your bank statement transactions against the receipts you've scanned. If a bank payment matches a receipt (by date and amount), the system automatically marks it as 'Verified.' If there's a payment without a receipt, or a receipt without a payment, it highlights it for you to review. This prevents fraud and clarifies exactly where the money went.",
      icon: CheckCircle2,
    },
    {
      id: "gbm",
      title: "GBM Ready",
      description: "Generate professional financial reports and audit trails instantly for your next Association meeting.",
      explanation: "Preparing for the annual General Body Meeting (GBM) used to take weeks of manual spreadsheet work. With FinSight, it happens in seconds. The system organizes all spending into categories (like utilities, repairs, or maintenance) and generates clear charts. You get a fully transparent view of the association's financial health, making it easy to answer any member's questions and pass audits with zero stress.",
      icon: BarChart3,
    }
  ];

  return (
    <div className="flex flex-col items-center justify-center min-h-[calc(100vh-4rem)] text-center px-4 relative overflow-hidden">
      {/* Dynamic Backdrop */}
      <div className="absolute top-0 left-1/2 -translate-x-1/2 w-full h-[500px] bg-gradient-to-b from-primary/10 to-transparent pointer-events-none -z-10" />
      <div className="absolute -top-24 -left-24 w-96 h-96 bg-primary/20 rounded-full blur-3xl pointer-events-none -z-10" />
      <div className="absolute top-1/2 -right-24 w-80 h-80 bg-secondary/10 rounded-full blur-3xl pointer-events-none -z-10" />

      <div className="space-y-8 max-w-4xl animate-fade-in">
        <div className="inline-flex items-center gap-2 px-3 py-1 rounded-full border border-primary/20 bg-primary/5 text-primary text-xs font-bold uppercase tracking-widest mb-4">
          <Sparkles className="h-3 w-3" />
          Powered by Gemini AI
        </div>
        <h1 className="text-5xl font-black tracking-tight sm:text-6xl md:text-7xl lg:text-8xl leading-[1.1]">
          Automate your <span className="text-primary drop-shadow-[0_0_15px_rgba(59,130,246,0.5)]">Finance</span>
        </h1>
        <p className="mx-auto max-w-[750px] text-base-content/70 text-lg md:text-xl font-medium leading-relaxed">
          FinSight is the next-gen financial layer for your Apartment Association. 
          Smart OCR, AI reconciliation, and GBM-ready reports—all in one place.
        </p>
        <div className="flex flex-wrap justify-center gap-6 mt-10">
          <Link
            href="/dashboard"
            className="inline-flex h-14 items-center justify-center rounded-2xl bg-primary px-10 text-sm font-black text-primary-content transition-all hover:bg-primary/90 hover:scale-105 active:scale-95 shadow-xl shadow-primary/30"
          >
            LAUNCH DASHBOARD <ArrowRight className="ml-2 h-5 w-5" />
          </Link>
          <Link
            href="/settings"
            className="inline-flex h-14 items-center justify-center rounded-2xl border border-base-content/20 bg-base-100/50 backdrop-blur-md px-10 text-sm font-black transition-all hover:bg-base-300 hover:scale-105 active:scale-95 shadow-lg"
          >
            CONFIGURE ENGINE
          </Link>
        </div>
      </div>

      <div className="mt-24 grid grid-cols-1 md:grid-cols-3 gap-8 text-left max-w-6xl w-full animate-fade-in [animation-delay:200ms]">
        {features.map((feature) => (
          <div 
            key={feature.id}
            onClick={() => setSelectedFeature(feature)}
            className="glass-panel group flex flex-col space-y-4 p-8 rounded-3xl cursor-pointer relative overflow-hidden transition-all hover:-translate-y-2 hover:border-primary/40 hover:glow-primary"
          >
            <div className="absolute top-0 right-0 p-6 opacity-0 group-hover:opacity-100 transition-all group-hover:translate-x-0 translate-x-4">
              <ArrowRight className="h-5 w-5 text-primary" />
            </div>
            <div className="p-3 bg-primary/10 rounded-2xl w-fit group-hover:bg-primary/20 transition-colors">
              <feature.icon className="h-8 w-8 text-primary" />
            </div>
            <div>
              <h3 className="font-extrabold tracking-tight text-2xl mb-2">{feature.title}</h3>
              <p className="text-base text-base-content/60 font-medium leading-relaxed">{feature.description}</p>
            </div>
          </div>
        ))}
      </div>

      {/* Feature Detail Modal */}
      {selectedFeature && (
        <div className="fixed inset-0 z-[100] flex items-center justify-center p-4 bg-base-100/40 backdrop-blur-xl animate-in fade-in duration-300">
          <div className="relative max-w-2xl w-full bg-base-100 border border-base-content/10 rounded-[2.5rem] shadow-[0_0_100px_rgba(0,0,0,0.5)] p-10 md:p-14 animate-in zoom-in-95 duration-300">
            <button 
              onClick={() => setSelectedFeature(null)}
              className="absolute top-8 right-8 p-3 rounded-full hover:bg-base-300 transition-all active:scale-90"
            >
              <X className="h-6 w-6" />
            </button>
            <div className="flex flex-col items-center text-center space-y-8">
              <div className="p-6 bg-primary/10 rounded-[2rem] glow-primary">
                <selectedFeature.icon className="h-20 w-20 text-primary" />
              </div>
              <div className="space-y-3">
                <h2 className="text-4xl font-black tracking-tighter">{selectedFeature.title}</h2>
                <p className="text-primary font-bold text-lg uppercase tracking-wide">{selectedFeature.description}</p>
              </div>
              <div className="bg-base-200 p-8 rounded-[1.5rem] border border-base-content/5 text-left shadow-inner">
                <p className="text-xl leading-relaxed text-base-content/70 italic font-medium">
                  {selectedFeature.explanation}
                </p>
              </div>
              <button 
                onClick={() => setSelectedFeature(null)}
                className="w-full bg-primary text-primary-content h-16 rounded-[1.25rem] font-black text-lg hover:bg-primary/90 transition-all hover:shadow-lg hover:shadow-primary/30 active:scale-95"
              >
                PROCEED
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
