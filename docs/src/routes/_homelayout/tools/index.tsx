import { createFileRoute, Link } from "@tanstack/react-router";
import { ArrowUpRight, BellRing, Braces, QrCode, Radio } from "lucide-react";
import { toolDirectory } from "#/lib/tool-directory";

export const Route = createFileRoute("/_homelayout/tools/")({
  component: ToolsPage,
});

const icons = {
  mqtt: Radio,
  unifiedpush: BellRing,
  regex: Braces,
  qr: QrCode,
} as const;

function ToolsPage() {
  return (
    <main className="mx-auto w-full max-w-6xl px-5 py-8 sm:px-8 sm:py-12 lg:px-10">
      <header className="mb-8 max-w-2xl space-y-3">
        <h1 className="text-3xl font-bold tracking-tight sm:text-4xl">Tools</h1>
        <p className="text-muted-foreground">
          Test integrations, validate URL rules, and prepare device installation
          from your browser.
        </p>
      </header>

      <div className="grid gap-4 sm:grid-cols-2">
        {toolDirectory.map(({ id, title, description, href }) => {
          const Icon = icons[id];
          return (
            <Link
              key={id}
              to={href}
              className="group flex min-h-48 flex-col rounded-xl border border-border bg-card p-6 text-card-foreground transition-colors hover:border-primary/50 hover:bg-accent/40 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
            >
              <div className="mb-5 flex items-start justify-between">
                <span className="flex size-11 items-center justify-center rounded-lg bg-muted text-foreground">
                  <Icon aria-hidden="true" className="size-5" />
                </span>
                <ArrowUpRight
                  aria-hidden="true"
                  className="size-5 text-muted-foreground transition-colors group-hover:text-foreground"
                />
              </div>
              <h2 className="mb-2 text-lg font-semibold">{title}</h2>
              <p className="text-sm leading-relaxed text-muted-foreground">
                {description}
              </p>
            </Link>
          );
        })}
      </div>
    </main>
  );
}
