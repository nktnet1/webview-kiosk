import { expect, type Page, test } from "@playwright/test";

type LocationHost = typeof window & {
  __locationRequests: {
    success: PositionCallback;
    error: PositionErrorCallback;
    options: PositionOptions;
    watch: boolean;
  }[];
  __clearedWatches: number[];
};

test.beforeEach(async ({ page }) => {
  await page.addInitScript(() => {
    const host = window as LocationHost;
    host.__locationRequests = [];
    host.__clearedWatches = [];
    Object.defineProperty(navigator, "geolocation", {
      configurable: true,
      value: {
        getCurrentPosition(
          success: PositionCallback,
          error: PositionErrorCallback,
          options: PositionOptions,
        ) {
          host.__locationRequests.push({
            success,
            error,
            options,
            watch: false,
          });
        },
        watchPosition(
          success: PositionCallback,
          error: PositionErrorCallback,
          options: PositionOptions,
        ) {
          host.__locationRequests.push({
            success,
            error,
            options,
            watch: true,
          });
          return 0;
        },
        clearWatch(id: number) {
          host.__clearedWatches.push(id);
        },
      },
    });
  });
  await page.goto("tools/device-apis/");
});

async function emitPosition(
  page: Page,
  index: number,
  latitude: number,
  timestamp: number,
) {
  await page.evaluate(
    ({ index, latitude, timestamp }) => {
      (window as LocationHost).__locationRequests[index].success({
        coords: {
          latitude,
          longitude: 151.2,
          accuracy: 12,
          altitude: null,
          altitudeAccuracy: null,
          heading: null,
          speed: null,
        },
        timestamp,
      } as GeolocationPosition);
    },
    { index, latitude, timestamp },
  );
}

async function emitError(page: Page, index: number, code: number) {
  await page.evaluate(
    ({ index, code }) => {
      (window as LocationHost).__locationRequests[index].error({
        code,
        message: "Test location error",
      } as GeolocationPositionError);
    },
    { index, code },
  );
}

test("location requests are explicit and display coordinates, accuracy and fix time", async ({
  page,
}) => {
  const panel = page.getByRole("region", { name: "Location" });
  await expect(panel.getByText("Detected", { exact: true })).toBeVisible();
  expect(
    await page.evaluate(
      () => (window as LocationHost).__locationRequests.length,
    ),
  ).toBe(0);
  await expect(
    panel.getByRole("link", { name: "Use Direct GPS" }),
  ).toHaveAttribute("href", /\/docs\/settings\/device#use-direct-gps$/);
  await panel.getByRole("button", { name: "Get location" }).click();
  await expect(
    panel.getByRole("button", { name: "Start watch" }),
  ).toBeDisabled();
  expect(
    await page.evaluate(() => {
      const { options, watch } = (window as LocationHost).__locationRequests[0];
      return { options, watch };
    }),
  ).toEqual({
    options: { enableHighAccuracy: true, maximumAge: 0, timeout: 30_000 },
    watch: false,
  });
  await emitPosition(page, 0, -33.8, Date.UTC(2026, 10, 25));
  await expect(panel.getByRole("status")).toHaveText("Location received");
  await expect(panel.getByText("-33.8", { exact: true })).toBeVisible();
  await expect(panel.getByText("151.2", { exact: true })).toBeVisible();
  await expect(panel.getByText("12 m", { exact: true })).toBeVisible();
  await expect(panel.locator("time")).toHaveAttribute(
    "datetime",
    "2026-11-25T00:00:00.000Z",
  );
  await expect(
    panel.getByRole("button", { name: "Get location" }),
  ).toBeEnabled();
});

for (const code of [1, 2, 3]) {
  test(`one-shot location error ${code} permits retry`, async ({ page }) => {
    const panel = page.getByRole("region", { name: "Location" });
    await panel.getByRole("button", { name: "Get location" }).click();
    await emitError(page, 0, code);
    await expect(panel.getByRole("status")).toHaveText(
      `Location error (${code}): Test location error`,
    );
    await panel.getByRole("button", { name: "Get location" }).click();
    await emitPosition(page, 1, -33.9, Date.now());
    await expect(panel.getByRole("status")).toHaveText("Location received");
  });
}

test("watches recover after transient errors and ignore callbacks after stopping", async ({
  page,
}) => {
  const panel = page.getByRole("region", { name: "Location" });
  await panel.getByRole("button", { name: "Start watch" }).click();
  for (const code of [2, 3]) {
    await emitError(page, 0, code);
    await expect(panel.getByRole("status")).toContainText(
      `Location error (${code})`,
    );
    await expect(
      panel.getByRole("button", { name: "Stop watch" }),
    ).toBeEnabled();
  }
  await emitPosition(page, 0, -33.8, Date.UTC(2026, 10, 25));
  await expect(panel.getByRole("status")).toHaveText("Watching location");
  await emitPosition(page, 0, -33.9, Date.UTC(2026, 10, 25, 1));
  await expect(panel.getByText("-33.9", { exact: true })).toBeVisible();
  await expect(panel.locator("time")).toHaveAttribute(
    "datetime",
    "2026-11-25T01:00:00.000Z",
  );
  await panel.getByRole("button", { name: "Stop watch" }).click();
  expect(
    await page.evaluate(() => (window as LocationHost).__clearedWatches),
  ).toEqual([0]);
  await emitPosition(page, 0, 99, Date.now());
  await emitError(page, 0, 1);
  await expect(panel.getByRole("status")).toHaveText("Location watch stopped");
  await expect(panel.getByText("-33.9", { exact: true })).toBeVisible();
});

test("permission denial ends a watch and navigation clears the next watch", async ({
  page,
}) => {
  const panel = page.getByRole("region", { name: "Location" });
  await panel.getByRole("button", { name: "Start watch" }).click();
  await emitError(page, 0, 1);
  await expect(
    panel.getByRole("button", { name: "Start watch" }),
  ).toBeEnabled();
  expect(
    await page.evaluate(() => (window as LocationHost).__clearedWatches),
  ).toEqual([0]);
  await panel.getByRole("button", { name: "Start watch" }).click();
  await page.getByRole("link", { name: "Tools", exact: true }).first().click();
  await expect(
    page.getByRole("heading", { name: "Tools", exact: true }),
  ).toBeVisible();
  expect(
    await page.evaluate(() => (window as LocationHost).__clearedWatches),
  ).toEqual([0, 0]);
});

test("synchronous API errors leave location controls usable", async ({
  page,
}) => {
  const panel = page.getByRole("region", { name: "Location" });
  await page.evaluate(() => {
    navigator.geolocation.getCurrentPosition = () => {
      throw new Error("Location blocked");
    };
  });
  await panel.getByRole("button", { name: "Get location" }).click();
  await expect(panel.getByRole("status")).toHaveText("Location blocked");
  await expect(
    panel.getByRole("button", { name: "Get location" }),
  ).toBeEnabled();
  await panel.getByRole("button", { name: "Start watch" }).click();
  await expect(panel.getByRole("button", { name: "Stop watch" })).toBeEnabled();
});
