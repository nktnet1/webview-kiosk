import { createFileRoute } from "@tanstack/react-router";
import UnifiedPushDebugger from "#/components/unifiedpush/UnifiedPushDebugger";

export const Route = createFileRoute("/_homelayout/debugger/unifiedpush/")({
  component: UnifiedPushPage,
});

function UnifiedPushPage() {
  return (
    <main className="flex flex-col items-center p-4 md:mt-4 w-full">
      <UnifiedPushDebugger />
    </main>
  );
}
