import { expect, test } from "@playwright/test";

test("sidebar navigation loads prerendered docs without cross-origin requests", async ({
  page,
}) => {
  const staticCacheRequests: string[] = [];
  page.on("request", (request) => {
    if (request.url().includes("/staticServerFnCache/")) {
      staticCacheRequests.push(request.url());
    }
  });

  await page.goto("docs/");
  await expect(
    page.getByRole("heading", { name: "Overview", exact: true }),
  ).toBeVisible();
  await page
    .getByRole("link", { name: "Installation", exact: true })
    .first()
    .click();
  await expect(
    page.getByRole("heading", { name: "Installation", exact: true }),
  ).toBeVisible();

  expect(staticCacheRequests.length).toBeGreaterThan(0);
  const siteOrigin = new URL(page.url()).origin;
  for (const url of staticCacheRequests) {
    expect(new URL(url).origin).toBe(siteOrigin);
  }
});
