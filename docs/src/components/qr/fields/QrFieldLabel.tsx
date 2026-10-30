import { ExternalLink } from "lucide-react";
import { Label } from "#/components/ui/label";

export default function QrFieldLabel({
  id,
  label,
  docsLink,
}: {
  id: string;
  label: string;
  docsLink: string;
}) {
  return (
    <div className="flex min-w-0 items-center gap-2">
      <Label htmlFor={id} className="leading-5">
        {label}
      </Label>
      <a
        href={docsLink}
        target="_blank"
        rel="noopener noreferrer"
        aria-label={`Android documentation for ${label} (opens in a new tab)`}
        title="Android documentation"
        className="inline-flex size-6 shrink-0 items-center justify-center rounded text-muted-foreground transition-colors hover:text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
      >
        <ExternalLink aria-hidden="true" className="size-3.5" />
      </a>
    </div>
  );
}
