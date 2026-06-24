import { pageSchema } from "fumadocs-core/source/schema";
import { defineCollections, defineConfig, defineDocs } from "fumadocs-mdx/config";

export const docs = defineDocs({
  dir: "./content/docs",
  docs: {
    async: true,
    postprocess: {
      includeProcessedMarkdown: true,
    },
  },
});

export const legal = defineCollections({
  type: "doc",
  dir: "./content/legal",
  async: true,
});


export default defineConfig();
