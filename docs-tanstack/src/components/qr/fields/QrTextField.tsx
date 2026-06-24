import type { AnyFieldApi } from "@tanstack/react-form";
import { ExternalLink } from "lucide-react";
import FormFieldInfo from "@/components/common/FormFieldInfo";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";

export default function QrTextField({
  field,
  label,
  placeholder,
  docsLink,
}: {
  field: AnyFieldApi;
  label: string;
  placeholder?: string;
  docsLink: string;
}) {
  return (
    <div className="text-left">
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
      <Input
        id={field.name}
        value={field.state.value ?? ""}
        onBlur={field.handleBlur}
        onChange={(e) => field.handleChange(e.target.value || null)}
        placeholder={placeholder}
        className="mt-2"
      />
      <FormFieldInfo field={field} />
    </div>
  );
}
