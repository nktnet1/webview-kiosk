import { AnyFieldApi } from "@tanstack/react-form";
import { Label } from "@/components/ui/label";
import { Checkbox } from "@/components/ui/checkbox";

export default function QrCheckboxField({
  field,
  label,
}: {
  field: AnyFieldApi;
  label: string;
}) {
  return (
    <div className="flex items-center gap-2">
      <Checkbox
        id={field.name}
        checked={field.state.value}
        onCheckedChange={(state) => field.handleChange(state === true)}
      />
      <Label htmlFor={field.name}>{label}</Label>
    </div>
  );
}
