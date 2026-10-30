import type { AnyFieldApi } from "@tanstack/react-form";
import { Eye, EyeOff } from "lucide-react";
import { useState } from "react";
import FormFieldInfo from "#/components/common/FormFieldInfo";
import QrFieldLabel from "#/components/qr/fields/QrFieldLabel";
import { Input } from "#/components/ui/input";
import { Textarea } from "#/components/ui/textarea";

export default function QrTextField({
  field,
  label,
  placeholder,
  docsLink,
  secret = false,
  multiline = false,
}: {
  field: AnyFieldApi;
  label: string;
  placeholder?: string;
  docsLink: string;
  secret?: boolean;
  multiline?: boolean;
}) {
  const [revealed, setRevealed] = useState(false);
  const inputProps = {
    id: field.name,
    value: field.state.value ?? "",
    onBlur: field.handleBlur,
    placeholder,
    autoComplete: "off",
  };

  return (
    <div className="min-w-0 space-y-2">
      <QrFieldLabel id={field.name} label={label} docsLink={docsLink} />
      {multiline ? (
        <Textarea
          {...inputProps}
          onChange={(e) => field.handleChange(e.target.value)}
          rows={4}
          spellCheck={false}
          className="min-h-28 resize-y font-mono text-sm"
        />
      ) : (
        <div className="relative">
          <Input
            {...inputProps}
            type={secret && !revealed ? "password" : "text"}
            onChange={(e) => field.handleChange(e.target.value)}
            className={secret ? "h-10 pr-11" : "h-10"}
          />
          {secret && (
            <button
              type="button"
              aria-label={`${revealed ? "Hide" : "Show"} ${label.toLowerCase()}`}
              aria-controls={field.name}
              aria-pressed={revealed}
              onClick={() => setRevealed(!revealed)}
              className="absolute inset-y-0 right-1 my-auto inline-flex size-8 items-center justify-center rounded text-muted-foreground hover:text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
            >
              {revealed ? (
                <EyeOff aria-hidden="true" className="size-4" />
              ) : (
                <Eye aria-hidden="true" className="size-4" />
              )}
            </button>
          )}
        </div>
      )}
      <FormFieldInfo field={field} />
    </div>
  );
}
