import { createFileRoute } from "@tanstack/react-router";
import RegexPlayground from "#/components/RegexPlayground";

export const Route = createFileRoute("/_homelayout/tools/regex/")({
  component: RegexToolPage,
});

function RegexToolPage() {
  return (
    <main className="mx-auto w-full max-w-5xl px-5 py-6 sm:px-8 sm:py-10">
      <header className="mb-6 space-y-2">
        <h1 className="text-3xl font-bold tracking-tight">Regex Playground</h1>
        <p className="text-muted-foreground">
          Test Web Content URL blacklist and whitelist patterns. A matching
          whitelist rule takes precedence over the blacklist.
        </p>
      </header>
      <RegexPlayground />
    </main>
  );
}
