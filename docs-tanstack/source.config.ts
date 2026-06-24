import { pageSchema } from "fumadocs-core/source/schema";
import {
  defineCollections,
  defineConfig,
  defineDocs,
} from "fumadocs-mdx/config";
import lastModified from "fumadocs-mdx/plugins/last-modified";

export const docs = defineDocs({
  dir: "./content/docs",
  docs: {
    schema: pageSchema,
    postprocess: {
      includeProcessedMarkdown: true,
    },
  },
});

export const legal = defineCollections({
  type: "doc",
  dir: "./content/legal",
  schema: pageSchema,
  postprocess: {
    includeProcessedMarkdown: true,
  },
});

export default defineConfig({
  plugins: [lastModified()],
});
