import { loader, multiple } from "fumadocs-core/source";
import { docs, legal as legalPages } from "fumadocs-mdx:collections/server";
import { toFumadocsSource } from "fumadocs-mdx/runtime/server";

export const source = loader(
  multiple({
    docs: docs.toFumadocsSource(),
  }),
  {
    baseUrl: "/docs",
  }
);

export const legal = loader(toFumadocsSource(legalPages, []), {
  baseUrl: "/",
});
