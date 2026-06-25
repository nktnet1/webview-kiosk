import { HomeLayout } from "fumadocs-ui/layouts/home";
import { DefaultNotFound } from "fumadocs-ui/layouts/home/not-found";
import { homeBaseOptions } from "@/lib/layout.shared";

export function NotFound() {
  return (
    <HomeLayout {...homeBaseOptions()}>
      <DefaultNotFound />
    </HomeLayout>
  );
}
