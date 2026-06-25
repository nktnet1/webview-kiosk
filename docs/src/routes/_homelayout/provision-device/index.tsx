import { createFileRoute } from "@tanstack/react-router";
import QRCodeForm from "@/components/qr/QrCodeForm";

export const Route = createFileRoute("/_homelayout/provision-device/")({
  component: RouteComponent,
});

function RouteComponent() {
  return (
    <main className="flex flex-col items-center text-center p-4 md:mt-8 w-full">
      <QRCodeForm />
    </main>
  );
}
