import { metaSchema, pageSchema } from "fumadocs-core/source/schema";
import {
  defineCollections,
  defineConfig,
  defineDocs,
} from "fumadocs-mdx/config";
import lastModified from "fumadocs-mdx/plugins/last-modified";

export const docs = defineDocs({
  docs: {
    schema: pageSchema,
    async: true,
  },
  meta: {
    schema: metaSchema,
  },
});

export const legal = defineCollections({
  type: "doc",
  async: true,
  schema: pageSchema,
  dir: "./content/legal",
});

export default defineConfig({
  plugins: [lastModified()],
  mdxOptions: {},
});
