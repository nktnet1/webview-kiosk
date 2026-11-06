import { createFileRoute, redirect } from "@tanstack/react-router";

/** Preserve existing links to the interface tester. */
export const Route = createFileRoute("/_homelayout/tools/webview-interfaces/")({
  beforeLoad: () => {
    throw redirect({ replace: true, to: "/tools/device-apis" });
  },
});
