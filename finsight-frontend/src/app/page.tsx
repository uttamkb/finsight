"use client";

import { useState } from "react";
import Link from "next/link";
import { ArrowRight, BarChart3, Receipt, CheckCircle2, X } from "lucide-react";

export default function Home() {
  const [selectedFeature, setSelectedFeature] = useState<null | {
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
    <div className="flex flex-col items-center justify-center min-h-[calc(100vh-3.5rem)] text-center px-4 relative">
      <div className="space-y-6 max-w-3xl">
        <h1 className="text-4xl font-extrabold tracking-tight sm:text-5xl md:text-6xl lg:text-7xl">
          Automate your <span className="text-primary block">Apartment Finances</span>
        </h1>
        <p className="mx-auto max-w-[700px] text-base-content/60 md:text-xl">
          FinSight intelligently ingests receipts via Google Drive, parses Bank Statements, and reconciles everything down to the penny.
        </p>
        <div className="flex justify-center gap-4 mt-8">
          <Link
            href="/dashboard"
            className="inline-flex h-11 items-center justify-center rounded-md bg-primary px-8 text-sm font-medium text-primary-content transition-colors hover:bg-primary/90"
          >
            Go to Dashboard <ArrowRight className="ml-2 h-4 w-4" />
          </Link>
          <Link
            href="/settings"
            className="inline-flex h-11 items-center justify-center rounded-md border border-input bg-base-100 px-8 text-sm font-medium transition-colors hover:bg-accent hover:text-accent-content"
          >
            Configure System
          </Link>
        </div>
      </div>

      <div className="mt-20 grid grid-cols-1 md:grid-cols-3 gap-8 text-left max-w-5xl w-full">
        {features.map((feature) => (
          <div 
            key={feature.id}
            onClick={() => setSelectedFeature(feature)}
            className="group flex flex-col space-y-2 p-6 rounded-xl border bg-base-200 text-card-foreground shadow-sm hover:border-primary/50 hover:shadow-lg hover:shadow-primary/10 transition-all cursor-pointer relative overflow-hidden"
          >
            <div className="absolute top-0 right-0 p-4 opacity-0 group-hover:opacity-100 transition-opacity">
              <ArrowRight className="h-4 w-4 text-primary" />
            </div>
            <feature.icon className="h-10 w-10 text-primary mb-2" />
            <h3 className="font-semibold tracking-tight text-xl">{feature.title}</h3>
            <p className="text-sm text-base-content/60">{feature.description}</p>
          </div>
        ))}
      </div>

      {/* Feature Detail Modal */}
      {selectedFeature && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-base-100/80 backdrop-blur-sm animate-in fade-in duration-200">
          <div className="relative max-w-2xl w-full bg-base-200 border rounded-2xl shadow-2xl p-8 md:p-10 animate-in zoom-in-95 duration-200">
            <button 
              onClick={() => setSelectedFeature(null)}
              className="absolute top-4 right-4 p-2 rounded-full hover:bg-base-300 transition-colors"
            >
              <X className="h-5 w-5" />
            </button>
            <div className="flex flex-col items-center text-center space-y-6">
              <div className="p-4 bg-primary/20 rounded-2xl">
                <selectedFeature.icon className="h-16 w-16 text-primary" />
              </div>
              <div className="space-y-2">
                <h2 className="text-3xl font-bold tracking-tight">{selectedFeature.title}</h2>
                <p className="text-primary font-medium">{selectedFeature.description}</p>
              </div>
              <div className="bg-base-300/50 p-6 rounded-xl border text-left">
                <p className="text-lg leading-relaxed text-base-content/60">
                  {selectedFeature.explanation}
                </p>
              </div>
              <button 
                onClick={() => setSelectedFeature(null)}
                className="bg-primary text-primary-content px-8 py-3 rounded-xl font-bold hover:bg-primary/90 transition-all"
              >
                Got it, thanks!
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
