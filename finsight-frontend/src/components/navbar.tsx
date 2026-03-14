"use client";

import Link from "next/link"
import { Building2 } from "lucide-react"
import { ThemeToggle } from "./theme-toggle"
import { useEffect, useState } from "react"
import { ShieldAlert } from "lucide-react"
import { usePathname } from "next/navigation"
import { apiFetch } from "@/lib/api";

export function Navbar() {
  const pathname = usePathname();
  const [apartmentName, setApartmentName] = useState("FinSight");
  const [anomalyCount, setAnomalyCount] = useState(0);

  useEffect(() => {
    // Fetch Apartment Name
    apiFetch("/settings")
      .then(res => res.json())
      .then(data => {
        if (data && data.apartmentName) {
          setApartmentName(data.apartmentName);
        }
      })
      .catch(err => console.error("Navbar failed to fetch config:", err));

    // Fetch unresolved anomalies count
    const fetchAnomalies = () => {
      apiFetch("/reconciliation/audit-trail/statistics")
        .then(res => res.json())
        .then(data => {
          setAnomalyCount(data.unresolvedCount || 0);
        })
        .catch(err => console.error("Navbar failed to fetch anomaly count:", err));
    };

    fetchAnomalies();
    const interval = setInterval(fetchAnomalies, 30000); // Refresh every 30s
    return () => clearInterval(interval);
  }, []);

  const navLinks = [
    { href: "/dashboard", label: "Dashboard" },
    { href: "/receipts", label: "Receipts" },
    { href: "/statements", label: "Statements" },
    { href: "/reconciliation", label: "Reconciliation" },
    { href: "/resident-feedback", label: "Feedback" },
    { href: "/system-information", label: "System Info" },
  ];

  return (
    <header className="sticky top-0 z-50 w-full border-b border-border/40 bg-background/95 backdrop-blur supports-[backdrop-filter]:bg-background/60">
      <div className="container mx-auto flex h-14 max-w-screen-2xl items-center px-4">
        <div className="mr-4 hidden md:flex">
          <Link href="/" className="mr-6 flex items-center space-x-2">
            <Building2 className="h-6 w-6 text-primary" />
            <span className="hidden font-bold sm:inline-block">
              {apartmentName}
            </span>
          </Link>
          <nav className="flex items-center space-x-6 text-sm font-medium">
            {navLinks.map((link) => (
              <Link
                key={link.href}
                href={link.href}
                className={`transition-colors hover:text-foreground/80 ${
                  pathname === link.href ? "text-foreground font-bold" : "text-foreground/60"
                }`}
              >
                {link.label}
              </Link>
            ))}
            <Link
              href="/vendors"
              className={`transition-colors hover:text-foreground/80 flex items-center gap-1 ${
                pathname === "/vendors" ? "text-foreground font-bold" : "text-foreground/60"
              }`}
            >
              Vendors
              {anomalyCount > 0 && (
                <span className="flex h-4 w-4 items-center justify-center rounded-full bg-destructive text-[10px] font-bold text-destructive-foreground animate-pulse">
                  {anomalyCount}
                </span>
              )}
            </Link>
          </nav>
        </div>
        <div className="flex flex-1 items-center justify-between space-x-2 md:justify-end">
          <div className="w-full flex-1 md:w-auto md:flex-none">
             {/* Space for global search later */}
          </div>
          <nav className="flex items-center space-x-2">
            <Link
              href="/settings"
              className="inline-flex h-9 w-9 items-center justify-center rounded-md transition-colors hover:bg-muted dark:hover:bg-muted"
            >
               <span className="sr-only">Settings</span>
               <svg
                  xmlns="http://www.w3.org/2000/svg"
                  width="24"
                  height="24"
                  viewBox="0 0 24 24"
                  fill="none"
                  stroke="currentColor"
                  strokeWidth="2"
                  strokeLinecap="round"
                  strokeLinejoin="round"
                  className="h-4 w-4"
                >
                  <path d="M12.22 2h-.44a2 2 0 0 0-2 2v.18a2 2 0 0 1-1 1.73l-.43.25a2 2 0 0 1-2 0l-.15-.08a2 2 0 0 0-2.73.73l-.22.38a2 2 0 0 0 .73 2.73l.15.1a2 2 0 0 1 1 1.72v.51a2 2 0 0 1-1 1.74l-.15.09a2 2 0 0 0-.73 2.73l.22.38a2 2 0 0 0 2.73.73l.15-.08a2 2 0 0 1 2 0l.43.25a2 2 0 0 1 1 1.73V20a2 2 0 0 0 2 2h.44a2 2 0 0 0 2-2v-.18a2 2 0 0 1 1-1.73l.43-.25a2 2 0 0 1 2 0l.15.08a2 2 0 0 0 2.73-.73l.22-.39a2 2 0 0 0-.73-2.73l-.15-.08a2 2 0 0 1-1-1.74v-.5a2 2 0 0 1 1-1.74l.15-.09a2 2 0 0 0 .73-2.73l-.22-.38a2 2 0 0 0-2.73-.73l-.15.08a2 2 0 0 1-2 0l-.43-.25a2 2 0 0 1-1-1.73V4a2 2 0 0 0-2-2z" />
                  <circle cx="12" cy="12" r="3" />
                </svg>
            </Link>
            <ThemeToggle />
          </nav>
        </div>
      </div>
    </header>
  )
}
