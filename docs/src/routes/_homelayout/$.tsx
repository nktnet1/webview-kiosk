import { createFileRoute, Link, notFound } from "@tanstack/react-router";
import { createServerFn } from "@tanstack/react-start";
import browserCollections from "collections/browser";
import { useFumadocsLoader } from "fumadocs-core/source/client";
import { InlineTOC } from "fumadocs-ui/components/inline-toc";
import {
  MarkdownCopyButton,
  PageLastUpdate,
  ViewOptionsPopover,
} from "fumadocs-ui/layouts/docs/page";
import { Suspense } from "react";
import { useMDXComponents } from "@/components/fumadocs/mdx";
import { gitConfig } from "@/lib/shared";
import { legal, slugsToMarkdownPath } from "@/lib/source";
import { staticFunctionMiddleware } from "@/lib/staticMiddlewareFunction";

export const Route = createFileRoute("/_homelayout/$")({
  component: Page,
  loader: async ({ params }) => {
    const slugs = params._splat?.split("/") ?? [];
    const data = await loader({ data: slugs });
    await clientLoader.preload(data.path);
    return data;
  },
  head: ({ loaderData }) => {
    return {
      meta: [
        {
          title: loaderData?.title,
        },
      ],
    };
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
      title: page.data.title,
      description: page.data.description,
      markdownUrl: slugsToMarkdownPath(page.slugs, "").url,
      pageTree: await legal.serializePageTree(legal.getPageTree()),
      lastModified: page.data.lastModified,
    };
  });

const clientLoader = browserCollections.legal.createClientLoader({
  component(
    { default: MDX, toc, lastModified },
    {
      title,
      description,
      markdownUrl,
      path,
    }: {
      title: string;
      description?: string;
      path: string;
      markdownUrl: string;
    },
  ) {
    // biome-ignore lint/correctness/useHookAtTopLevel: default fumadocs template
    const components = useMDXComponents();
    return (
      <main className="mx-auto w-full max-w-page px-4 py-4 md:px-8 md:py-6">
        <div
          className="container rounded-xl border py-4 md:py-12 px-4 md:px-8"
          style={{
            backgroundColor: "black",
            backgroundImage: [
              "linear-gradient(140deg, hsla(224,34%,84%,0.3), transparent 50%)",
              "linear-gradient(to left top, hsla(200,90%,50%,0.8), transparent 50%)",
              "radial-gradient(circle at 100% 100%, hsla(100,100%,40%,1), hsla(240,40%,40%,1) 17%, hsla(240,40%,40%,0.5) 20%, transparent)",
            ].join(", "),
            backgroundBlendMode: "difference, difference, normal",
          }}
        >
          <h1 className="mb-2 text-3xl font-bold text-white">{title}</h1>
          <p className="mb-4 text-white/80">{description}</p>
        </div>

        <article className="container grid grid-cols-1 px-0 py-2 md:py-4 lg:grid-cols-[2fr_1fr] lg:px-4 gap-y-6">
          <div className="prose">
            <InlineTOC className="mt-6 mb-0" items={toc} />
            <MDX components={components} />
          </div>
          <div className="prose p-4 flex flex-col items-center mt-3">
            <div className="flex flex-row gap-2 items-center justify-center border-b pb-6">
              <MarkdownCopyButton markdownUrl={markdownUrl} />
              <ViewOptionsPopover
                markdownUrl={markdownUrl}
                githubUrl={`https://github.com/${gitConfig.user}/${gitConfig.repo}/blob/${gitConfig.branch}/docs/content/legal/${path}`}
              />
            </div>
            {lastModified && <PageLastUpdate date={lastModified} />}
          </div>
        </article>
      </main>
    );
  },
});

function Page() {
  const { title, description, markdownUrl, path } = useFumadocsLoader(
    Route.useLoaderData(),
  );

  return (
    <main>
      <Link to={markdownUrl} hidden />
      <Suspense>
        {clientLoader.useContent(path, {
          title,
          description,
          markdownUrl,
          path,
        })}
      </Suspense>
    </main>
  );
}
