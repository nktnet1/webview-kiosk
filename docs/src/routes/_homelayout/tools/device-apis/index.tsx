import { createFileRoute } from "@tanstack/react-router";
import InterfaceTester from "#/components/interfaces/InterfaceTester";

export const Route = createFileRoute("/_homelayout/tools/device-apis/")({
  component: DeviceApisPage,
});

function DeviceApisPage() {
  return (
    <main className="mx-auto w-full max-w-6xl px-5 py-8 sm:px-8 sm:py-12 lg:px-10">
      <header className="mb-7 space-y-3">
        <h1 className="text-3xl font-bold tracking-tight sm:text-4xl">
          Device APIs
        </h1>
        <p className="max-w-3xl text-muted-foreground">
          Test device APIs inside WebView Kiosk.
        </p>
      </header>
      <InterfaceTester />
    </main>
  );
}
