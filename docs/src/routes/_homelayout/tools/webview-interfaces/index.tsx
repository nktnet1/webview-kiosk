import { createFileRoute } from "@tanstack/react-router";
import InterfaceTester from "#/components/interfaces/InterfaceTester";

export const Route = createFileRoute("/_homelayout/tools/webview-interfaces/")({
  component: WebviewInterfacesPage,
});

function WebviewInterfacesPage() {
  return (
    <main className="mx-auto w-full max-w-6xl px-5 py-8 sm:px-8 sm:py-12 lg:px-10">
      <header className="mb-7 space-y-3">
        <h1 className="text-3xl font-bold tracking-tight sm:text-4xl">
          WebView interfaces
        </h1>
        <p className="max-w-3xl text-muted-foreground">
          Test the device APIs exposed to pages by WebView Kiosk. Open this page
          inside the app's WebView with the relevant settings enabled; a regular
          desktop browser cannot access the Android bridges. The Blob download
          test also requires Allow File Download in Web Engine settings.
        </p>
      </header>
      <InterfaceTester />
    </main>
  );
}
