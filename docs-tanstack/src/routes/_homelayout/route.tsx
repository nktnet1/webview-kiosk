import { createFileRoute, Outlet } from "@tanstack/react-router";
import { HomeLayout } from "fumadocs-ui/layouts/home";
import { homeBaseOptions } from "@/lib/layout.shared";

export const Route = createFileRoute("/_homelayout")({
  component: RouteComponent,
});

function RouteComponent() {
  return (
    <HomeLayout {...homeBaseOptions()}>
      <Outlet />
    </HomeLayout>
  );
}
