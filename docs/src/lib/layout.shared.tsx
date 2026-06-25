import type { BaseLayoutProps } from "fumadocs-ui/layouts/shared";
import { BookIcon, FileTextIcon, ShieldIcon } from "lucide-react";
import IconAsset from "@/assets/icon.svg";
import { APP_NAME } from "@/config/app";
import { gitConfig } from "./shared";

export function baseOptions(): BaseLayoutProps {
  return {
    nav: {
      title: (
        <>
          <img
            src={IconAsset}
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

export function homeBaseOptions(): BaseLayoutProps {
  const base = baseOptions();
  return {
    ...base,
    links: [
      {
        icon: <BookIcon />,
        text: "Docs",
        url: "/docs",
        secondary: false,
      },
      ...(base?.links ?? []),
    ],
  };
}
