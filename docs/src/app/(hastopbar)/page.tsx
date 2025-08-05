import { Button } from "@/components/ui/button";
import { APP_NAME } from "@/config/app";
import Link from "next/link";

export default function HomePage() {
  return (
    <main className="flex flex-1 flex-col items-center justify-center text-center px-4 py-12">
      <h1 className="mb-4 text-4xl font-bold tracking-tight">
        Welcome to {APP_NAME}
      </h1>
      <p className="mb-6 text-lg text-muted-foreground max-w-md">
        A minimal and secure webview wrapper. Get started by exploring the usage
        documentation.
      </p>
      <Button asChild>
        <Link href="/docs">Get Started</Link>
      </Button>
    </main>
  );
}
