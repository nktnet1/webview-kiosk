import { readdirSync, readFileSync } from "node:fs";
import { join, posix, relative, sep } from "node:path";
import { fileURLToPath } from "node:url";
import { describe, expect, it } from "vitest";

const contentDirectory = fileURLToPath(
  new URL("../../content/", import.meta.url),
);

function* markdownFiles(directory: string): Generator<string> {
  for (const entry of readdirSync(directory, { withFileTypes: true })) {
    const filename = join(directory, entry.name);
    if (entry.isDirectory()) {
      yield* markdownFiles(filename);
    } else if (/\.mdx?$/.test(entry.name)) {
      yield filename;
    }
  }
}

const pages = [...markdownFiles(contentDirectory)].map((filename) => {
  const path = relative(contentDirectory, filename).split(sep).join("/");
  return {
    route: posix.normalize(
      `/${path.replace(/\.mdx?$/, "").replace(/\/index$/, "")}`,
    ),
    content: readFileSync(filename, "utf8"),
  };
});

const anchorsByRoute = new Map(
  pages.map(({ route, content }) => [
    route,
    [...content.matchAll(/^#{1,6}\s+.+\s+\[#([a-z0-9-]+)\]\s*$/gm)].map(
      (match) => match[1],
    ),
  ]),
);

describe("documentation heading anchors", () => {
  it("uses unique, number-independent IDs for every numbered heading", () => {
    let numberedHeadings = 0;

    for (const { route, content } of pages) {
      for (const match of content.matchAll(
        /^#{1,6}\s+(\d+(?:\.\d+)*\.)\s+(.+)$/gm,
      )) {
        numberedHeadings++;
        const sectionNumber = match[1].replaceAll(".", "");
        const anchor = match[2].match(/\[#([a-z0-9-]+)\]$/)?.[1];
        expect(anchor, `${route}: ${match[0]}`).toBeDefined();
        expect(anchor, `${route}: ${match[0]}`).not.toMatch(
          new RegExp(`^${sectionNumber}-`),
        );
      }

      const anchors = anchorsByRoute.get(route) ?? [];
      expect(new Set(anchors).size, route).toBe(anchors.length);
    }

    expect(numberedHeadings).toBeGreaterThan(0);
  });

  it("keeps internal documentation fragment links pointed at existing anchors", () => {
    for (const { route, content } of pages) {
      for (const match of content.matchAll(
        /(?:\]\(|\bhref=["'])(\/docs[^\s)"']*#[a-z0-9-]+|#[a-z0-9-]+)/g,
      )) {
        const [pathname, anchor] = match[1].split("#");
        const target = pathname
          ? posix.normalize(pathname).replace(/\/$/, "")
          : route;
        expect(anchorsByRoute.get(target), `${route}: ${match[1]}`).toContain(
          anchor,
        );
      }
    }
  });
});
