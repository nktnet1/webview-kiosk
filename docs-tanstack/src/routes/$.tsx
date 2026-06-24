import { createFileRoute, Link, notFound } from "@tanstack/react-router";
import { createServerFn } from "@tanstack/react-start";
import { staticFunctionMiddleware } from "@tanstack/start-static-server-functions";
import browserCollections from "collections/browser";
import { useFumadocsLoader } from "fumadocs-core/source/client";
import { InlineTOC } from "fumadocs-ui/components/inline-toc";
import {
  MarkdownCopyButton,
  PageLastUpdate,
  ViewOptionsPopover,
} from "fumadocs-ui/layouts/docs/page";
import { HomeLayout } from "fumadocs-ui/layouts/home";
import { Suspense } from "react";
import { useMDXComponents } from "@/components/mdx";
import { homeBaseOptions } from "@/lib/layout.shared";
import { gitConfig } from "@/lib/shared";
import { legal, slugsToMarkdownPath } from "@/lib/source";

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
      lastModified: page.data.lastModified,
    };
  });

const clientLoader = browserCollections.legal.createClientLoader({
  component(
    { default: MDX, toc, lastModified },
    {
      markdownUrl,
      path,
    }: {
      path: string;
      markdownUrl: string;
    },
  ) {
    // biome-ignore lint/correctness/useHookAtTopLevel: default fumadocs template
    const components = useMDXComponents();
    return (
      <main className="mx-auto w-full max-w-page px-4 py-4 md:px-8 md:py-6">
        <article className="container grid grid-cols-1 px-0 py-2 md:py-4 lg:grid-cols-[2fr_1fr] lg:px-4">
          <div className="prose">
            <div className="flex flex-row gap-2 items-center border-b pb-6">
              <MarkdownCopyButton markdownUrl={markdownUrl} />
              <ViewOptionsPopover
                markdownUrl={markdownUrl}
                githubUrl={`https://github.com/${gitConfig.user}/${gitConfig.repo}/blob/${gitConfig.branch}/docs/content/legal/${path}`}
              />
            </div>
            <InlineTOC items={toc} />
            <MDX components={components} />
          </div>
          <div className="prose p-4 flex justify-center mt-3">
            {lastModified && <PageLastUpdate date={lastModified} />}
          </div>
        </article>
      </main>
    );
  },
});

function Page() {
  const { path, markdownUrl } = useFumadocsLoader(Route.useLoaderData());

  return (
    <HomeLayout {...homeBaseOptions()}>
      <Link to={markdownUrl} hidden />
      <Suspense>
        {clientLoader.useContent(path, { markdownUrl, path })}
      </Suspense>
    </HomeLayout>
  );
}
