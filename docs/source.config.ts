import { pageSchema } from "fumadocs-core/source/schema";
import {
  defineCollections,
  defineConfig,
  defineDocs,
} from "fumadocs-mdx/config";
import lastModified from "fumadocs-mdx/plugins/last-modified";
import type { Root, Text } from "mdast";
import { visit } from "unist-util-visit";
import { APP_NAME } from "./src/config/app";

function remarkReplaceConstants(options: {
  constants: Record<string, string>;
  pattern?: RegExp;
}) {
  const pattern = options.pattern ?? /\[\[([A-Z0-9_]+)\]\]/g;
  return (tree: Root) => {
    visit(tree, "text", (node: Text) => {
      if (typeof node.value !== "string") {
        return;
      }

      node.value = node.value.replace(pattern, (_match, key) => {
        return options.constants[key] ?? _match;
      });
    });
  };
}

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
  mdxOptions: {
    remarkPlugins: [
      [
        remarkReplaceConstants,
        {
          constants: {
            APP_NAME,
          },
        },
      ],
    ],
  },
});
