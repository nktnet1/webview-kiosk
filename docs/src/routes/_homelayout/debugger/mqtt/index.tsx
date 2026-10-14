import { createFileRoute, redirect } from "@tanstack/react-router";

/** Keep previously shared debugger links usable. */
export const Route = createFileRoute("/_homelayout/debugger/mqtt/")({
  beforeLoad: () => {
    throw redirect({ replace: true, to: "/tools/mqtt" });
  },
});
