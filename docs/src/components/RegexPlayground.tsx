"use client";

import { useState } from "react";
import { Textarea } from "@/components/ui/textarea";
import { Input } from "@/components/ui/input";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { toast } from "sonner";

export default function RegexPlayground() {
  const [blacklist, setBlacklist] = useState("");
  const [whitelist, setWhitelist] = useState("");
  const [testUrl, setTestUrl] = useState("");
  const [result, setResult] = useState<"blocked" | "allowed" | null>(null);

  function parseRegexList(input: string): RegExp[] {
    return input
      .split("\n")
      .map((line) => line.trim())
      .filter((line) => line.length > 0)
      .map((line) => {
        try {
          return new RegExp(line);
        } catch {
          return null;
        }
      })
      .filter((r): r is RegExp => r !== null);
  }

  function isValidUrl(url: string): boolean {
    try {
      const parsed = new URL(url);
      if (parsed.protocol !== "http:" && parsed.protocol !== "https:") {
        return false;
      }
      const hostname = parsed.hostname;
      if (hostname === "localhost") return true;
      return hostname.includes(".");
    } catch {
      return false;
    }
  }

  function isBlockedUrl(
    url: string,
    blacklistRegexes: RegExp[],
    whitelistRegexes: RegExp[]
  ): boolean {
    if (whitelistRegexes.some((re) => re.test(url))) {
      return false;
    }
    return blacklistRegexes.some((re) => re.test(url));
  }

  function handleValidate() {
    if (!isValidUrl(testUrl)) {
      toast.warning("Invalid URL - a valid example is https://example.com");
      setResult(null);
      return;
    }

    const blacklistRegexes = parseRegexList(blacklist);
    const whitelistRegexes = parseRegexList(whitelist);

    const blocked = isBlockedUrl(testUrl, blacklistRegexes, whitelistRegexes);
    setResult(blocked ? "blocked" : "allowed");

    if (blocked) {
      toast.error(`URL is Blocked: ${testUrl}`);
    } else {
      toast.success(`URL is Allowed: ${testUrl}`);
    }
  }

  function handleKeyDown(e: React.KeyboardEvent<HTMLInputElement>) {
    if (e.key === "Enter" && isValidUrl(testUrl)) {
      e.preventDefault();
      handleValidate();
    }
  }

  function handleInputChange(value: string) {
    setTestUrl(value);
    setResult(null);
  }

  const validUrl = isValidUrl(testUrl);

  return (
    <div className="w-full mx-auto space-y-4">
      <Card>
        <CardContent className="space-y-4">
          <div>
            <label className="block text-sm font-medium mb-1">Test URL</label>
            <div className="flex space-x-2">
              <Input
                placeholder="https://example.com"
                value={testUrl}
                onChange={(e) => handleInputChange(e.target.value)}
                onKeyDown={handleKeyDown}
                className={
                  !validUrl && testUrl
                    ? "border-fd-warning focus-visible:ring-fd-warning"
                    : ""
                }
              />
              <Button onClick={handleValidate}>Validate</Button>
            </div>
          </div>
          <div>
            <label className="block text-sm font-medium mb-1">Blacklist</label>
            <Textarea
              placeholder="One regex per line"
              value={blacklist}
              onChange={(e) => setBlacklist(e.target.value)}
              className={`min-h-[100px] ${
                result === "blocked" ? "border border-red-500" : ""
              }`}
            />
          </div>
          <div>
            <label className="block text-sm font-medium mb-1">Whitelist</label>
            <Textarea
              placeholder="One regex per line"
              value={whitelist}
              onChange={(e) => setWhitelist(e.target.value)}
              className={`min-h-[100px] ${
                result === "allowed" && whitelist.trim().length > 0
                  ? "border border-green-500"
                  : ""
              }`}
            />
          </div>
        </CardContent>
      </Card>
    </div>
  );
}
