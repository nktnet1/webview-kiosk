import { createFileRoute } from "@tanstack/react-router";
import MqttDebugger from "#/components/mqtt/MqttDebugger";

export const Route = createFileRoute("/_homelayout/debugger/mqtt/")({
  component: MqttPage,
});

function MqttPage() {
  return (
    <main className="flex w-full flex-col items-center p-4 md:mt-8">
      <MqttDebugger />
    </main>
  );
}
