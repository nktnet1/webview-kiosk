import "@/app/global.css";
import { RootProvider } from "fumadocs-ui/provider/next";
import { Inter } from "next/font/google";
import type { ReactNode } from "react";
import { Toaster } from "@/components/ui/sonner";
import DefaultSearchDialog from "@/components/fumadocs/search";

const inter = Inter({
  subsets: ["latin"],
});

export default function Layout({ children }: { children: ReactNode }) {
  return (
    <html lang="en" className={inter.className} suppressHydrationWarning>
      <link rel="icon" href="/icon.svg" sizes="any" />
      <body className="flex flex-col min-h-screen">
        <RootProvider
          search={{
            SearchDialog: DefaultSearchDialog,
          }}
        >
          {children}
          <Toaster />
        </RootProvider>
      </body>
    </html>
  );
}
