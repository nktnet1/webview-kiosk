import type { AnyFieldApi } from "@tanstack/react-form";
import { ExternalLink } from "lucide-react";
import { Checkbox } from "@/components/ui/checkbox";
import { Label } from "@/components/ui/label";

export default function QrCheckboxField({
  field,
  label,
  docsLink,
}: {
  field: AnyFieldApi;
  label: string;
  docsLink: string;
}) {
  return (
    <div className="flex items-center gap-2">
      <Checkbox
        id={field.name}
        checked={field.state.value}
        onCheckedChange={(state) => field.handleChange(state === true)}
      />
      <Label htmlFor={field.name} className="flex items-center gap-2">
        {label}
        <a
          href={docsLink}
          target="_blank"
          rel="noopener noreferrer"
          className="inline-flex items-center hover:opacity-75"
        >
          <ExternalLink size={18} />
        </a>
      </Label>
    </div>
  );
}
