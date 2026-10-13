import { Eye, EyeOff } from "lucide-react";
import type { ComponentProps } from "react";
import { Input } from "#/components/ui/input";
import { cn } from "#/lib/cn";

type SecretInputProps = Omit<ComponentProps<typeof Input>, "type"> & {
  revealed: boolean;
  onRevealChange: (revealed: boolean) => void;
  revealLabel: string;
};

/** Controlled visibility keeps individual reveal buttons in sync with a show-all toggle. */
export function SecretInput({
  revealed,
  onRevealChange,
  revealLabel,
  className,
  disabled,
  id,
  ...props
}: SecretInputProps) {
  return (
    <div className="relative">
      <Input
        {...props}
        id={id}
        disabled={disabled}
        type={revealed ? "text" : "password"}
        className={cn("pr-11", className)}
      />
      <button
        type="button"
        disabled={disabled}
        aria-label={`${revealed ? "Hide" : "Show"} ${revealLabel}`}
        aria-controls={id}
        onClick={() => onRevealChange(!revealed)}
        className="text-muted-foreground hover:text-foreground focus-visible:ring-ring absolute inset-y-0 right-1.5 my-auto inline-flex size-7 items-center justify-center rounded-md focus-visible:ring-2 focus-visible:outline-none disabled:pointer-events-none disabled:opacity-50"
      >
        {revealed ? (
          <EyeOff aria-hidden="true" className="size-4" />
        ) : (
          <Eye aria-hidden="true" className="size-4" />
        )}
      </button>
    </div>
  );
}
