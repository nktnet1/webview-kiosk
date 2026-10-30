import { createFileRoute } from "@tanstack/react-router";
import QRCodeForm from "#/components/qr/QrCodeForm";

export const Route = createFileRoute("/_homelayout/provision-device/")({
  component: RouteComponent,
});

function RouteComponent() {
  return (
    <main className="w-full px-5 py-8 sm:px-8 sm:py-12 lg:px-10">
      <QRCodeForm />
    </main>
  );
}
