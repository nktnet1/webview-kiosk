import { pageSchema } from "fumadocs-core/source/schema";
import {
  defineCollections,
  defineConfig,
  defineDocs,
} from "fumadocs-mdx/config";
import lastModified from "fumadocs-mdx/plugins/last-modified";
import type { Root } from "mdast";
import { visit } from "unist-util-visit";
import { APP_NAME } from "./src/config/app";

function remarkReplaceConstants(options: {
  constants: Record<string, string>;
}) {
  return (tree: Root) => {
    visit(tree, (node, index, parent) => {
      if (
        !(
          node.type === "mdxFlowExpression" || node.type === "mdxTextExpression"
        )
      ) {
        return;
      }

      const replacementValue = options.constants[node.value];
      if (
        replacementValue !== undefined &&
        parent &&
        typeof index === "number"
      ) {
        parent.children[index] = {
          type: "text",
          value: replacementValue,
        };
      }
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
