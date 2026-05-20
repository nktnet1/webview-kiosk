import { docs, legal as legalPages } from "fumadocs-mdx:collections/server";
import { loader } from "fumadocs-core/source";
import { toFumadocsSource } from "fumadocs-mdx/runtime/server";

export const source = loader(
  {
    docs: docs.toFumadocsSource(),
  },
  {
    baseUrl: "/docs",
  },
);

export const legal = loader(toFumadocsSource(legalPages, []), {
  baseUrl: "/",
});
