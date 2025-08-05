import { APP_NAME } from "@/config/app";
import type { BaseLayoutProps } from "fumadocs-ui/layouts/shared";
import { BookIcon, FileTextIcon, ShieldIcon } from "lucide-react";
import AppIcon from "@/app/icon.png";
import Image from "next/image";

export const baseOptions: BaseLayoutProps = {
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
      icon: <BookIcon />,
      text: "Docs",
      url: "/docs",
      secondary: false,
      active: "nested-url",
    },
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
};
