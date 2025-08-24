import { Button } from "@/components/ui/button";
import { APP_LINK, APP_NAME } from "@/config/app";
import Link from "next/link";

export default function HomePage() {
  return (
    <main className="flex flex-1 flex-col items-center text-center px-4 pt-24 pb-12">
      <div className="bg-fd-muted p-2 md:p-16 rounded-2xl">
        <h1 className="mb-4 text-4xl font-bold tracking-tight">{APP_NAME}</h1>
        <p className="mb-6 text-lg text-muted-foreground max-w-lg">
          Turn any Android device into a dedicated, locked-down web page in
          fullscreen mode.
        </p>
        <div className="flex flex-wrap gap-2 items-center justify-center">
          <Button asChild variant="info" className="w-28">
            <Link href="/docs">Get Started</Link>
          </Button>
          <Button asChild variant="success" className="w-28">
            <Link target="_blank" href={APP_LINK}>
              Download
            </Link>
          </Button>
        </div>
      </div>
    </main>
  );
}
