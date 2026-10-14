import { type ChangeEvent, useEffect, useState } from "react";
import { Input } from "#/components/ui/input";
import { Label } from "#/components/ui/label";
import { Textarea } from "#/components/ui/textarea";
import { splitTargets } from "./payload";

type Props = {
  id: string;
  label: string;
  values: string[];
  onChange: (values: string[]) => void;
  placeholder?: string;
  multiline?: boolean;
};

/** Preserve an unfinished comma/newline while syncing an array to the payload. */
export function DelimitedListInput({
  id,
  label,
  values,
  onChange,
  placeholder = "Comma-separated (optional)",
  multiline = false,
}: Props) {
  const [draft, setDraft] = useState(values.join(", "));
  const normalized = values.join("\n");
  useEffect(() => {
    if (splitTargets(draft).join("\n") !== normalized)
      setDraft(values.join(", "));
  }, [draft, normalized, values]);
  const sharedProps = {
    id,
    value: draft,
    placeholder,
    spellCheck: false,
    onChange: (event: ChangeEvent<HTMLInputElement | HTMLTextAreaElement>) => {
      setDraft(event.target.value);
      onChange(splitTargets(event.target.value));
    },
  };
  return (
    <div className="space-y-2">
      <Label htmlFor={id}>{label}</Label>
      {multiline ? (
        <Textarea {...sharedProps} rows={3} />
      ) : (
        <Input {...sharedProps} />
      )}
    </div>
  );
}
