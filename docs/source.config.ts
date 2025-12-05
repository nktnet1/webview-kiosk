import {
  defineCollections,
  defineConfig,
  defineDocs,
  frontmatterSchema,
  metaSchema,
} from "fumadocs-mdx/config";

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
  mdxOptions: {},
});
