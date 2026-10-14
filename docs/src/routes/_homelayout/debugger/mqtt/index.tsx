import { createFileRoute } from "@tanstack/react-router";
import MqttDebugger from "#/components/mqtt/MqttDebugger";

export const Route = createFileRoute("/_homelayout/debugger/mqtt/")({
  component: MqttPage,
});

function MqttPage() {
  return (
    <main className="min-w-0 w-full px-3 pt-3 sm:px-5 sm:pt-5 lg:px-6">
      <MqttDebugger />
    </main>
  );
}
