"use client";

import { Tooltip as TooltipPrimitive } from "radix-ui";
import type * as React from "react";
import { cn } from "#/lib/cn";

function TooltipProvider(
  props: React.ComponentProps<typeof TooltipPrimitive.Provider>,
) {
  return <TooltipPrimitive.Provider {...props} />;
}

function Tooltip(props: React.ComponentProps<typeof TooltipPrimitive.Root>) {
  return <TooltipPrimitive.Root {...props} />;
}

function TooltipTrigger(
  props: React.ComponentProps<typeof TooltipPrimitive.Trigger>,
) {
  return <TooltipPrimitive.Trigger {...props} />;
}

function TooltipContent({
  className,
  sideOffset = 6,
  ...props
}: React.ComponentProps<typeof TooltipPrimitive.Content>) {
  return (
    <TooltipPrimitive.Portal>
      <TooltipPrimitive.Content
        sideOffset={sideOffset}
        className={cn(
          "bg-popover text-popover-foreground z-[60] max-w-xs rounded-md border px-3 py-2 text-xs leading-relaxed shadow-md",
          className,
        )}
        {...props}
      />
    </TooltipPrimitive.Portal>
  );
}

export { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger };
