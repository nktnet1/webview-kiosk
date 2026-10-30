import type { AnyFieldApi } from "@tanstack/react-form";
import QrFieldLabel from "#/components/qr/fields/QrFieldLabel";
import { Checkbox } from "#/components/ui/checkbox";

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
    <div className="flex min-h-9 items-center gap-3">
      <Checkbox
        id={field.name}
        checked={field.state.value}
        onBlur={field.handleBlur}
        onCheckedChange={(state) => field.handleChange(state === true)}
      />
      <QrFieldLabel id={field.name} label={label} docsLink={docsLink} />
    </div>
  );
}
