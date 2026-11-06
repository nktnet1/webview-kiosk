import { createElement } from "react";
import { renderToStaticMarkup } from "react-dom/server";
import { describe, expect, it } from "vitest";
import { Button } from "#/components/ui/button";

describe("button cursor", () => {
  it("shows the pointer cursor when clickable", () => {
    const markup = renderToStaticMarkup(
      createElement(Button, { children: "Run test" }),
    );

    expect(markup).toContain("cursor-pointer");
  });

  it("does not use the pointer cursor when disabled", () => {
    const markup = renderToStaticMarkup(
      createElement(Button, { disabled: true, children: "Run test" }),
    );

    expect(markup).toContain("disabled:cursor-default");
    expect(markup).toContain('disabled=""');
  });
});
