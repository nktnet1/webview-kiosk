import { Generator, getConfig } from "@tanstack/router-generator";

const root = process.cwd();
const config = getConfig({}, root);
await new Generator({ config, root }).run();
