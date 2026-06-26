import {
  createRootRoute,
  HeadContent,
  Outlet,
  Scripts,
} from "@tanstack/react-router";
import { TanStackRouterDevtools } from "@tanstack/react-router-devtools";
import { RootProvider } from "fumadocs-ui/provider/tanstack";
import urlJoin from "url-join";
import IconAsset from "@/assets/icon.svg";
import SearchDialog from "@/components/search";
import { Toaster } from "@/components/ui/sonner";
import { APP_NAME } from "@/config/app";
import { getBasePath } from "@/lib/basePath";
import appCss from "@/styles/app.css?url";

export const Route = createRootRoute({
  head: () => ({
    meta: [
      {
        charSet: "utf-8",
      },
      {
        name: "viewport",
        content: "width=device-width, initial-scale=1",
      },
      {
        title: APP_NAME,
      },
    ],
    links: [
      { rel: "stylesheet", href: appCss },
      { rel: "icon", href: IconAsset },
    ],
  }),
  component: RootComponent,
});

function RootComponent() {
  return (
    <html suppressHydrationWarning lang="en">
      <head>
        <HeadContent />
      </head>
      <body className="flex flex-col min-h-screen">
        <RootProvider
          search={{
            SearchDialog,
            options: {
              api: urlJoin(getBasePath(), "/api/search"),
            },
          }}
        >
          <Outlet />
          <Toaster />
          <TanStackRouterDevtools position="bottom-right" />
        </RootProvider>
        <Scripts />
      </body>
    </html>
  );
}
