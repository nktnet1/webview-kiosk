import { createFileRoute } from "@tanstack/react-router";
import UnifiedPushDebugger from "#/components/unifiedpush/UnifiedPushDebugger";

export const Route = createFileRoute("/_homelayout/tools/unifiedpush/")({
  component: UnifiedPushPage,
});

function UnifiedPushPage() {
  return (
    <main className="min-w-0 w-full px-5 pt-3 pb-6 sm:px-8 sm:pt-4 sm:pb-8 lg:px-10 lg:pt-5 lg:pb-10">
      <UnifiedPushDebugger />
    </main>
  );
}
