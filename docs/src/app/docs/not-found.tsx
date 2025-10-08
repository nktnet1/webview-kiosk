import { redirect } from "next/navigation";

export default function DocsNotFound() {
  redirect("/docs");
}
