import type { AnyFieldApi } from "@tanstack/react-form";
import QrFieldLabel from "#/components/qr/fields/QrFieldLabel";
import {
  Select,
  SelectContent,
  SelectGroup,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "#/components/ui/select";

export default function QrSelectField({
  field,
  label,
  options,
  docsLink,
}: {
  field: AnyFieldApi;
  label: string;
  options: string[];
  docsLink: string;
}) {
  return (
    <div className="min-w-0 space-y-2">
      <QrFieldLabel id={field.name} label={label} docsLink={docsLink} />
      <Select
        value={field.state.value}
        onValueChange={(value) => field.handleChange(value)}
      >
        <SelectTrigger
          id={field.name}
          onBlur={field.handleBlur}
          className="w-full data-[size=default]:h-10"
        >
          <SelectValue placeholder={`Select ${label.toLowerCase()}`} />
        </SelectTrigger>
        <SelectContent>
          <SelectGroup>
            {options.map((o) => (
              <SelectItem key={o} value={o}>
                {o}
              </SelectItem>
            ))}
          </SelectGroup>
        </SelectContent>
      </Select>
    </div>
  );
}
