import { createFileRoute, notFound } from "@tanstack/react-router";
import { getLLMTextLegal, legal, markdownPathToSlugs } from "@/lib/source";

export const Route = createFileRoute("/{$}.md")({
  server: {
    handlers: {
      GET: async ({ params }) => {
        const slugs = markdownPathToSlugs(params._splat?.split("/") ?? []);
        const page = legal.getPage(slugs);
        if (!page) {
          throw notFound();
        }

        return new Response(await getLLMTextLegal(page), {
          headers: {
            "Content-Type": "text/markdown",
          },
        });
      },
    },
  },
});
