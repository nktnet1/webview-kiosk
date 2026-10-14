import { createFileRoute } from "@tanstack/react-router";
import QRCodeForm from "#/components/qr/QrCodeForm";

export const Route = createFileRoute("/_homelayout/tools/qr-installation/")({
  component: QrInstallationToolPage,
});

function QrInstallationToolPage() {
  return (
    <main className="flex w-full flex-col items-center px-5 py-6 sm:px-8 sm:py-10">
      <QRCodeForm />
    </main>
  );
}
