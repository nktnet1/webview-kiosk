import { createFileRoute } from "@tanstack/react-router";
import { createSearchAPI } from "fumadocs-core/search/server";
import { legal, source } from "@/lib/source";

const pages = source.getPages().concat(legal.getPages());

const server = createSearchAPI("advanced", {
  // https://docs.orama.com/docs/orama-js/supported-languages
  language: "english",
  indexes: pages.map((page) => ({
    id: page.url,
    title: page.data.title,
    description: page.data.description,
    structuredData: page.data.structuredData,
    url: page.url,
  })),
});

export const Route = createFileRoute("/api/search")({
  server: {
    handlers: {
      GET: () => server.staticGET(),
    },
  },
});
