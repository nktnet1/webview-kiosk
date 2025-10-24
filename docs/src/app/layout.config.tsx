import { APP_NAME } from "@/config/app";
import type { BaseLayoutProps } from "fumadocs-ui/layouts/shared";
import { FileTextIcon, ShieldIcon } from "lucide-react";
import AppIcon from "@/app/icon.svg";
import Image from "next/image";
import { GITHUB_URL } from "@/config/links";

export const baseOptions: BaseLayoutProps = {
  githubUrl: GITHUB_URL,
  nav: {
    title: (
      <>
        <Image
          src={AppIcon}
          className="w-8 h-8 md:w-9 md:h-9 rounded-full"
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
} as const;
