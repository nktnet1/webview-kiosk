import type { AnyFieldApi } from "@tanstack/react-form";

export default function FormFieldInfo({ field }: { field: AnyFieldApi }) {
  return (
    <div className="mt-2 text-sm">
      {field.state.meta.isTouched && field.state.meta.errors.length ? (
        <em className="text-red-500">
          {field.state.meta.errors.map((e) => e.message).join(", ")}
        </em>
      ) : null}
      {field.state.meta.isValidating ? "Validating..." : null}
    </div>
  );
}
