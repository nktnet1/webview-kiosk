import { loader } from "fumadocs-core/source";
import { createMDXSource } from "fumadocs-mdx";
import { docs, legal as legalPages } from "@/.source";

export const source = loader({
  baseUrl: "/docs",
  source: docs.toFumadocsSource(),
});

export const legal = loader({
  baseUrl: "/",
  source: createMDXSource(legalPages, []),
});
