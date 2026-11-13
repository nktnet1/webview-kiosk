import { useState } from "react";
import { toast } from "sonner";
import { Button } from "#/components/ui/button";
import { Card, CardContent } from "#/components/ui/card";
import { Input } from "#/components/ui/input";
import { Textarea } from "#/components/ui/textarea";
import { testUrlRules } from "#/lib/regex-playground";

export default function RegexPlayground() {
  const [blacklist, setBlacklist] = useState("");
  const [whitelist, setWhitelist] = useState("");
  const [testUrl, setTestUrl] = useState("");
  const [result, setResult] = useState<"blocked" | "allowed" | null>(null);

  function isValidUrl(url: string): boolean {
    try {
      const parsed = new URL(url);
      if (parsed.protocol !== "http:" && parsed.protocol !== "https:") {
        return false;
      }
      const hostname = parsed.hostname;
      return parsed.protocol === "http:" || /\.[^.]{2,}$/.test(hostname);
    } catch {
      return false;
    }
  }

  function handleValidate() {
    if (!isValidUrl(testUrl)) {
      toast.warning("Invalid URL - a valid example is https://example.com");
      setResult(null);
      return;
    }

    const { blocked, ruleError } = testUrlRules(testUrl, blacklist, whitelist);
    setResult(blocked ? "blocked" : "allowed");

    if (blocked) {
      toast.error(
        ruleError
          ? `URL is Blocked (RE2 rule evaluation failed): ${testUrl}`
          : `URL is Blocked: ${testUrl}`,
      );
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
      <p className="text-sm text-muted-foreground">
        URL rules use RE2-based matching, as in the Android app.
      </p>
      <Card>
        <CardContent className="space-y-4">
          <div>
            <label
              htmlFor="test-url-input"
              className="block text-sm font-medium mb-1"
            >
              Test URL
            </label>
            <div className="flex space-x-2">
              <Input
                id="test-url-input"
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
            <label
              htmlFor="blacklist-textarea"
              className="block text-sm font-medium mb-1"
            >
              Blacklist
            </label>
            <Textarea
              id="blacklist-textarea"
              placeholder={`e.g.\n\t.*`}
              value={blacklist}
              onChange={(e) => setBlacklist(e.target.value)}
              className={`min-h-25 ${result === "blocked" ? "border border-red-500" : ""}`}
            />
          </div>
          <div>
            <label
              htmlFor="whitelist-textarea"
              className="block text-sm font-medium mb-1"
            >
              Whitelist
            </label>
            <Textarea
              id="whitelist-textarea"
              placeholder={`e.g.\n\t^https://trusted\\.org/?$\n\t^https://.*\\.trusted\\.org/.*`}
              value={whitelist}
              onChange={(e) => setWhitelist(e.target.value)}
              className={`min-h-25 ${
                result === "allowed" && whitelist.trim().length > 0
                  ? "bordemin-h-25n-500"
                  : ""
              }`}
            />
          </div>
        </CardContent>
      </Card>
    </div>
  );
}
