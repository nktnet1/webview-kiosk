import { ChevronDown, Search } from "lucide-react";
import { Popover } from "radix-ui";
import { useId, useRef, useState } from "react";
import { cn } from "#/lib/cn";
import { Input } from "./input";

export type ComboboxOption = {
  value: string;
  label: string;
  description?: string;
};

/** A compact, keyboard-accessible autocomplete using the project's Radix primitives. */
export function filterComboboxOptions(
  options: readonly ComboboxOption[],
  query: string,
): ComboboxOption[] {
  const terms = query.trim().toLocaleLowerCase().split(/\s+/).filter(Boolean);
  if (!terms.length) return [...options];
  return options.filter((option) => {
    const haystack =
      `${option.label} ${option.value} ${option.description ?? ""}`.toLocaleLowerCase();
    return terms.every((term) => haystack.includes(term));
  });
}

export function Combobox({
  options,
  onSelect,
  placeholder = "Choose an option…",
  emptyMessage = "No matching options",
  disabled = false,
  id,
  className,
}: {
  options: readonly ComboboxOption[];
  onSelect: (value: string) => void;
  placeholder?: string;
  emptyMessage?: string;
  disabled?: boolean;
  id?: string;
  className?: string;
}) {
  const generatedId = useId();
  const listId = `combobox-list-${generatedId}`;
  const inputRef = useRef<HTMLInputElement>(null);
  const [open, setOpen] = useState(false);
  const [query, setQuery] = useState("");
  const [active, setActive] = useState(0);
  const results = filterComboboxOptions(options, query);
  const selectedIndex = Math.min(active, Math.max(0, results.length - 1));

  function choose(value: string) {
    onSelect(value);
    setQuery("");
    setActive(0);
    setOpen(false);
    inputRef.current?.focus();
  }

  return (
    <Popover.Root open={open && !disabled} onOpenChange={setOpen} modal={false}>
      <Popover.Anchor asChild>
        <div className={cn("relative", className)}>
          <Search
            aria-hidden="true"
            className="pointer-events-none absolute top-1/2 left-3 size-4 -translate-y-1/2 text-muted-foreground"
          />
          <Input
            ref={inputRef}
            id={id}
            role="combobox"
            aria-autocomplete="list"
            aria-expanded={open && !disabled}
            aria-controls={open && !disabled ? listId : undefined}
            aria-activedescendant={
              open && results.length > 0
                ? `${listId}-option-${selectedIndex}`
                : undefined
            }
            className="w-full pr-9 pl-9"
            autoComplete="off"
            placeholder={placeholder}
            disabled={disabled}
            value={query}
            onFocus={() => setOpen(true)}
            onClick={() => setOpen(true)}
            onChange={(event) => {
              setQuery(event.target.value);
              setActive(0);
              setOpen(true);
            }}
            onKeyDown={(event) => {
              if (event.key === "Escape") {
                setOpen(false);
                return;
              }
              if (event.key === "ArrowDown" || event.key === "ArrowUp") {
                event.preventDefault();
                setOpen(true);
                if (results.length > 0)
                  setActive((index) =>
                    event.key === "ArrowDown"
                      ? (index + 1) % results.length
                      : (index + results.length - 1) % results.length,
                  );
              }
              if (event.key === "Enter" && open && results.length > 0) {
                event.preventDefault();
                choose(results[selectedIndex].value);
              }
            }}
          />
          <ChevronDown
            aria-hidden="true"
            className="pointer-events-none absolute top-1/2 right-3 size-4 -translate-y-1/2 text-muted-foreground"
          />
        </div>
      </Popover.Anchor>
      <Popover.Portal>
        <Popover.Content
          align="start"
          side="bottom"
          sideOffset={5}
          avoidCollisions
          collisionPadding={8}
          onOpenAutoFocus={(event) => event.preventDefault()}
          onCloseAutoFocus={(event) => event.preventDefault()}
          className="z-50 w-(--radix-popover-trigger-width) min-w-48 overflow-hidden rounded-md border bg-popover p-1 text-popover-foreground shadow-md outline-none"
        >
          <div
            id={listId}
            role="listbox"
            aria-label="Suggestions"
            className="max-h-64 overflow-y-auto overscroll-contain"
          >
            {results.length === 0 ? (
              <div className="px-3 py-4 text-center text-sm text-muted-foreground">
                {emptyMessage}
              </div>
            ) : (
              results.map((option, index) => (
                <button
                  key={option.value}
                  id={`${listId}-option-${index}`}
                  role="option"
                  aria-selected={index === selectedIndex}
                  tabIndex={-1}
                  type="button"
                  className={cn(
                    "flex w-full cursor-pointer flex-col rounded-sm px-3 py-2 text-left text-sm outline-none hover:bg-accent",
                    index === selectedIndex && "bg-accent",
                  )}
                  onPointerEnter={() => setActive(index)}
                  onMouseDown={(event) => event.preventDefault()}
                  onClick={() => choose(option.value)}
                >
                  <span>{option.label}</span>
                  <span className="wrap-anywhere text-xs text-muted-foreground">
                    {option.description ?? option.value}
                  </span>
                </button>
              ))
            )}
          </div>
        </Popover.Content>
      </Popover.Portal>
    </Popover.Root>
  );
}
