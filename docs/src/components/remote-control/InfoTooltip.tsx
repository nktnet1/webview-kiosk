"use client";

import { Info } from "lucide-react";
import type { ReactNode } from "react";
import {
  Tooltip,
  TooltipContent,
  TooltipProvider,
  TooltipTrigger,
} from "#/components/ui/tooltip";

/** Small, keyboard-accessible help affordance for shared MQTT/UnifiedPush controls. */
export function InfoTooltip({ children }: { children: ReactNode }) {
  return (
    <TooltipProvider delayDuration={200}>
      <Tooltip>
        <TooltipTrigger asChild>
          <button
            type="button"
            aria-label="More information"
            className="text-muted-foreground hover:text-foreground focus-visible:ring-ring inline-flex size-5 shrink-0 items-center justify-center rounded-full focus-visible:ring-2 focus-visible:outline-none"
          >
            <Info aria-hidden="true" className="size-3.5" />
          </button>
        </TooltipTrigger>
        <TooltipContent side="top">{children}</TooltipContent>
      </Tooltip>
    </TooltipProvider>
  );
}
