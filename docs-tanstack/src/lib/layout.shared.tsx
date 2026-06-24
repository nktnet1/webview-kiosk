import type { BaseLayoutProps } from "fumadocs-ui/layouts/shared";
import { gitConfig } from "./shared";
import { APP_NAME } from "@/config/app";
import { FileTextIcon, ShieldIcon } from "lucide-react";

export function baseOptions(): BaseLayoutProps {
  return {
  nav: {
    title: (
      <>
        <img
          src="/icon.svg"
          className="w-8 h-8 md:w-9 md:h-9 rounded-full"
          loading="eager"
          alt="WL"
        />
        {APP_NAME}
      </>
    ),
  },
  links: [
    {
      icon: <ShieldIcon />,
      text: "Privacy",
      url: "/privacy",
      secondary: false,
    },
    {
      icon: <FileTextIcon />,
      text: "Terms",
      url: "/terms",
      secondary: false,
    },
  ],
    githubUrl: `https://github.com/${gitConfig.user}/${gitConfig.repo}`,
  };
}
