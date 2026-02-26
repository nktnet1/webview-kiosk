import { AnyFieldApi } from "@tanstack/react-form";
import { Label } from "@/components/ui/label";
import {
  Select,
  SelectTrigger,
  SelectValue,
  SelectContent,
  SelectGroup,
  SelectItem,
} from "@/components/ui/select";
import { ExternalLink } from "lucide-react";

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
      <Select
        value={field.state.value}
        onValueChange={(value) => field.handleChange(value)}
      >
        <SelectTrigger className="mt-2 w-full">
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
