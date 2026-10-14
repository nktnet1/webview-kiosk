import { createFileRoute } from "@tanstack/react-router";
import MqttDebugger from "#/components/mqtt/MqttDebugger";

export const Route = createFileRoute("/_homelayout/debugger/mqtt/")({
  component: MqttPage,
});

function MqttPage() {
  return (
    <main className="min-w-0 w-full">
      <MqttDebugger />
    </main>
  );
}
