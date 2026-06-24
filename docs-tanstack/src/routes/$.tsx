import { createFileRoute, Link, notFound } from "@tanstack/react-router";
import { createServerFn } from "@tanstack/react-start";
import { legal, slugsToMarkdownPath } from "@/lib/source";
import browserCollections from "collections/browser";
import {
  DocsBody,
  MarkdownCopyButton,
  ViewOptionsPopover,
} from "fumadocs-ui/layouts/docs/page";
import { baseOptions } from "@/lib/layout.shared";
import { gitConfig } from "@/lib/shared";
import { staticFunctionMiddleware } from "@tanstack/start-static-server-functions";
import { useFumadocsLoader } from "fumadocs-core/source/client";
import { Suspense } from "react";
import { useMDXComponents } from "@/components/mdx";
import { HomeLayout } from "fumadocs-ui/layouts/home";

export const Route = createFileRoute("/$")({
  component: Page,
  loader: async ({ params }) => {
    const slugs = params._splat?.split("/") ?? [];
    const data = await loader({ data: slugs });
    await clientLoader.preload(data.path);
    return data;
  },
});

const loader = createServerFn({
  method: "GET",
})
  .validator((slugs: string[]) => slugs)
  .middleware([staticFunctionMiddleware])
  .handler(async ({ data: slugs }) => {
    const page = legal.getPage(slugs);
    if (!page) {
      throw notFound();
    }

    return {
      path: page.path,
      markdownUrl: slugsToMarkdownPath(page.slugs).url,
      pageTree: await legal.serializePageTree(legal.getPageTree()),
    };
  });

const clientLoader = browserCollections.legal.createClientLoader({
  component(
    { default: MDX },
    {
      markdownUrl,
      path,
    }: {
      path: string;
      markdownUrl: string;
    },
  ) {

    return (
      <main className="mx-auto w-full max-w-page px-4 py-4 md:px-8 md:py-6">
        <div className="flex flex-row gap-2 items-center border-b -mt-4 pb-6">
          <MarkdownCopyButton markdownUrl={markdownUrl} />
          <ViewOptionsPopover
            markdownUrl={markdownUrl}
            githubUrl={`https://github.com/${gitConfig.user}/${gitConfig.repo}/blob/${gitConfig.branch}/docs/content/legal/${path}`}
          />
        </div>
        <DocsBody>
          <MDX components={useMDXComponents()} />
        </DocsBody>
      </main>
    );
  },
});

function Page() {
  const { path, markdownUrl } = useFumadocsLoader(
    Route.useLoaderData(),
  );

  return (
    <HomeLayout {...baseOptions()}>
      <Link to={markdownUrl} hidden />
      <Suspense>
        {clientLoader.useContent(path, { markdownUrl, path })}
      </Suspense>
    </HomeLayout>
  );
}
