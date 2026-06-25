import Link from "next/link";
import { Button } from "@/components/ui/button";
import { APP_DESCRIPTION, APP_NAME } from "@/config/app";
import { Metadata } from "next";

export const metadata: Metadata = {
  title: APP_NAME,
  description: APP_DESCRIPTION,
};

export default function HomePage() {
  return (
    <main className="flex flex-1 flex-col items-center text-center p-4 md:mt-8">
      <div className="bg-fd-muted rounded-2xl p-6 md:p-16">
        <h1 className="mb-8 text-4xl font-bold tracking-tight">{APP_NAME}</h1>
        <p className="mb-6 text-lg text-muted-foreground max-w-lg">
          {APP_DESCRIPTION}
        </p>
        <div className="flex flex-wrap gap-2 items-center justify-center">
          <Button asChild variant="info" className="w-28">
            <Link href="/docs">Get Started</Link>
          </Button>
          <Button asChild variant="success" className="w-28">
            <Link href="/docs/installation">Download</Link>
          </Button>
        </div>
      </div>
    </main>
  );
}
