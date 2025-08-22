import { Button } from "@/components/ui/button";
import { APP_NAME } from "@/config/app";
import Link from "next/link";

export default function HomePage() {
  return (
    <main className="flex flex-1 flex-col items-center text-center px-4 pt-24 pb-12">
      <h1 className="mb-4 text-4xl font-bold tracking-tight">{APP_NAME}</h1>
      <p className="mb-6 text-lg text-muted-foreground max-w-lg">
        Turn any Android device into a dedicated, locked-down web page in
        fullscreen mode.
      </p>
      <Button asChild>
        <Link href="/docs">Get Started</Link>
      </Button>
    </main>
  );
}
