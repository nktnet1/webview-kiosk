import {
  defineCollections,
  defineConfig,
  defineDocs,
  frontmatterSchema,
  metaSchema,
} from "fumadocs-mdx/config";
import lastModified from "fumadocs-mdx/plugins/last-modified";

export const docs = defineDocs({
  docs: {
    schema: frontmatterSchema,
    async: true,
  },
  meta: {
    schema: metaSchema,
  },
});

export const legal = defineCollections({
  type: "doc",
  dir: "./content/legal",
  schema: frontmatterSchema.extend({}),
  async: true,
});

export default defineConfig({
  plugins: [lastModified()],
  mdxOptions: {},
});
