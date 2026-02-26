import { AnyFieldApi } from "@tanstack/react-form";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import FormFieldInfo from "@/components/common/FormFieldInfo";

export default function QrTextField({
  field,
  label,
  placeholder,
}: {
  field: AnyFieldApi;
  label: string;
  placeholder?: string;
}) {
  return (
    <div className="text-left">
      <Label htmlFor={field.name}>{label}</Label>
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
