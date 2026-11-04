import { joinURL, withoutTrailingSlash } from "ufo";

export const getBasePath = () =>
  joinURL(
    "/",
    withoutTrailingSlash(import.meta.env.PUBLIC_DOCS_BASE_PATH ?? "/"),
  );
