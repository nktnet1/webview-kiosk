// app/page.tsx

import QRCodeForm from "@/components/QrCodeForm";

export default function ProvisionDevicePage() {
  return (
    <main className="flex flex-col items-center text-center p-4 md:mt-8 w-full">
      <QRCodeForm />
    </main>
  );
}
