import { Button } from "@/components/ui/button";
import { APP_NAME } from "@/config/app";
import { PLAYSTORE_URL } from "@/config/links";
import Link from "next/link";

export default function HomePage() {
  return (
    <main className="flex flex-1 flex-col items-center text-center p-4 md:mt-8">
      <div className="bg-fd-muted rounded-2xl p-6 md:p-16">
        <h1 className="mb-8 text-4xl font-bold tracking-tight">{APP_NAME}</h1>
        <p className="mb-6 text-lg text-muted-foreground max-w-lg">
          Turn your Android device into a locked-down web page in fullscreen
          mode
        </p>
        <div className="flex flex-wrap gap-2 items-center justify-center">
          <Button asChild variant="info" className="w-28">
            <Link href="/docs">Get Started</Link>
          </Button>
          <Button asChild variant="success" className="w-28">
            <Link target="_blank" href={PLAYSTORE_URL}>
              Download
            </Link>
          </Button>
        </div>
      </div>
    </main>
  );
}
