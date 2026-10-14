import { createFileRoute } from "@tanstack/react-router";
import MqttDebugger from "#/components/mqtt/MqttDebugger";

export const Route = createFileRoute("/_homelayout/debugger/mqtt/")({
  component: MqttPage,
});

function MqttPage() {
  return (
    <main className="min-w-0 w-full px-5 py-6 sm:px-8 sm:py-8 lg:px-10 lg:py-10">
      <MqttDebugger />
    </main>
  );
}
