"use client";

import { X } from "lucide-react";
import { Dialog as DialogPrimitive } from "radix-ui";
import type * as React from "react";
import { cn } from "#/lib/cn";

function Dialog(props: React.ComponentProps<typeof DialogPrimitive.Root>) {
  return <DialogPrimitive.Root {...props} />;
}

function DialogContent({
  className,
  children,
  ...props
}: React.ComponentProps<typeof DialogPrimitive.Content>) {
  return (
    <DialogPrimitive.Portal>
      <DialogPrimitive.Overlay className="fixed inset-0 z-50 bg-black/50 data-[state=open]:animate-in data-[state=closed]:animate-out data-[state=closed]:fade-out-0 data-[state=open]:fade-in-0" />
      <DialogPrimitive.Content
        data-slot="dialog-content"
        className={cn(
          "bg-background text-foreground fixed top-1/2 left-1/2 z-50 grid max-h-[calc(100dvh-2rem)] w-[calc(100%-2rem)] max-w-xl -translate-x-1/2 -translate-y-1/2 gap-4 overflow-y-auto rounded-lg border p-6 shadow-lg",
          className,
        )}
        {...props}
      >
        {children}
        <DialogPrimitive.Close className="text-muted-foreground hover:text-foreground focus-visible:ring-ring absolute top-4 right-4 rounded-sm opacity-80 hover:opacity-100 focus-visible:ring-2 focus-visible:outline-none">
          <X aria-hidden="true" className="size-4" />
          <span className="sr-only">Close dialog</span>
        </DialogPrimitive.Close>
      </DialogPrimitive.Content>
    </DialogPrimitive.Portal>
  );
}

function DialogHeader({ className, ...props }: React.ComponentProps<"div">) {
  return (
    <div
      className={cn("flex flex-col gap-2 pr-6 text-left", className)}
      {...props}
    />
  );
}

function DialogFooter({ className, ...props }: React.ComponentProps<"div">) {
  return (
    <div
      className={cn(
        "flex flex-col-reverse gap-2 sm:flex-row sm:justify-end",
        className,
      )}
      {...props}
    />
  );
}

function DialogTitle(
  props: React.ComponentProps<typeof DialogPrimitive.Title>,
) {
  return <DialogPrimitive.Title className="text-lg font-semibold" {...props} />;
}

function DialogDescription(
  props: React.ComponentProps<typeof DialogPrimitive.Description>,
) {
  return (
    <DialogPrimitive.Description
      className="text-muted-foreground text-sm"
      {...props}
    />
  );
}

export {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
};
