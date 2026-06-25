import urlJoin from "url-join";

export const getBasePath = () => {
  return urlJoin("/", import.meta.env.PUBLIC_DOCS_BASE_PATH ?? "/");
};
