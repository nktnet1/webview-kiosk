// app/page.tsx

import QRCodeForm from "@/components/QrCodeForm";

export default function ProvisionDevicePage() {
  return (
    <main className="flex flex-1 flex-col items-center text-center p-4 md:mt-8">
      <QRCodeForm />
    </main>
  );
}
