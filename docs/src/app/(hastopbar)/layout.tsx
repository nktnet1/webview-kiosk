import type { ReactNode } from "react";
import { HomeLayout } from "fumadocs-ui/layouts/home";
import { BookIcon } from "lucide-react";
import { BaseLayoutProps } from "fumadocs-ui/layouts/shared";
import { baseOptions } from "@/app/layout.config";

const homeBaseOptions: BaseLayoutProps = {
  ...baseOptions,
  links: [
    {
      icon: <BookIcon />,
      text: "Docs",
      url: "/docs",
      secondary: false,
    },
    ...(baseOptions?.links ?? []),
  ],
} as const;

export default function Layout({ children }: { children: ReactNode }) {
  return <HomeLayout {...homeBaseOptions}>{children}</HomeLayout>;
}
