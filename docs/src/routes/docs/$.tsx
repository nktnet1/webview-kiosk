import { createFileRoute, Link, notFound } from "@tanstack/react-router";
import { createServerFn } from "@tanstack/react-start";
import cn from "cnfast";
import browserCollections from "collections/browser";
import type { Separator } from "fumadocs-core/page-tree";
import { useFumadocsLoader } from "fumadocs-core/source/client";
import * as Base from "fumadocs-ui/components/sidebar/base";
import { DocsLayout } from "fumadocs-ui/layouts/docs";
import {
  DocsBody,
  DocsDescription,
  DocsPage,
  DocsTitle,
  MarkdownCopyButton,
  PageLastUpdate,
  ViewOptionsPopover,
} from "fumadocs-ui/layouts/docs/page";
import { type ComponentProps, Suspense } from "react";
import { useMDXComponents } from "@/components/fumadocs/mdx";
import { baseOptions } from "@/lib/layout.shared";
import { docsRoute, gitConfig } from "@/lib/shared";
import { slugsToMarkdownPath, source } from "@/lib/source";
import { staticFunctionMiddleware } from "@/lib/staticMiddlewareFunction";

export const Route = createFileRoute("/docs/$")({
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
    const page = source.getPage(slugs);
    if (!page) {
      throw notFound();
    }

    return {
      path: page.path,
      title: page.data.title,
      lastModified: page.data.lastModified,
      markdownUrl: slugsToMarkdownPath(page.slugs, docsRoute).url,
      pageTree: await source.serializePageTree(source.getPageTree()),
    };
  });

const clientLoader = browserCollections.docs.createClientLoader({
  component(
    { toc, frontmatter, default: MDX },
    {
      markdownUrl,
      path,
      lastModified,
    }: {
      markdownUrl: string;
      path: string;
      lastModified?: Date;
    },
  ) {
    // biome-ignore lint/correctness/useHookAtTopLevel: default fumadocs template
    const components = useMDXComponents();
    return (
      <DocsPage toc={toc}>
        <DocsTitle>{frontmatter.title}</DocsTitle>
        <DocsDescription>{frontmatter.description}</DocsDescription>
        <div className="flex flex-row gap-2 items-center border-b -mt-4 pb-6">
          <MarkdownCopyButton markdownUrl={markdownUrl} />
          <ViewOptionsPopover
            markdownUrl={markdownUrl}
            githubUrl={`https://github.com/${gitConfig.user}/${gitConfig.repo}/blob/${gitConfig.branch}/docs/content/docs/${path}`}
          />
        </div>
        <DocsBody>
          <MDX components={components} />
          {lastModified && (
            <PageLastUpdate
              className="mb-6 mt-8 pt-4 border-t-2"
              date={lastModified}
            />
          )}
        </DocsBody>
      </DocsPage>
    );
  },
});

function SidebarSeparator({
  className,
  style,
  children,
  item,
  ...props
}: ComponentProps<"p"> & { item: Separator }) {
  const depth = Base.useFolderDepth();

  return (
    <Base.SidebarSeparator
      className={cn(
        "[&_svg]:size-4 [&_svg]:shrink-0 first:mt-2 mt-4 mb-1",
        className,
      )}
      style={{
        paddingInlineStart: `calc(${2 + 3 * depth} * var(--spacing))`,
        ...style,
      }}
      {...props}
    >
      <span className="inline-flex items-center gap-x-1.5">
        {item.icon}
        {item.name}
      </span>
    </Base.SidebarSeparator>
  );
}

function Page() {
  const { pageTree, path, markdownUrl, lastModified } = useFumadocsLoader(
    Route.useLoaderData(),
  );

  return (
    <DocsLayout
      {...baseOptions()}
      tree={pageTree}
      sidebar={{
        enabled: true,
        components: { Separator: SidebarSeparator },
      }}
    >
      <Link to={markdownUrl} hidden />
      <Suspense>
        {clientLoader.useContent(path, { markdownUrl, path, lastModified })}
      </Suspense>
    </DocsLayout>
  );
}
