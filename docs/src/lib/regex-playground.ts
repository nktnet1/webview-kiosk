import { RE2JS } from "re2js";

type RuleResult = "match" | "no-match" | "error";

export function isValidWebNavigationUrl(url: string): boolean {
  if (url.length > 2 * 1024 * 1024 || /%(?![\da-fA-F]{2})/.test(url)) {
    return false;
  }

  for (const char of url) {
    if (char <= " " || char === "\u007f") {
      return false;
    }
  }

  try {
    const parsed = new URL(url);
    return (
      (parsed.protocol === "http:" || parsed.protocol === "https:")
      && parsed.hostname.length > 0
    );
  } catch {
    return false;
  }
}

function parseRules(input: string): string[] {
  return input
    .split("\n")
    .map((line) => line.trim())
    .filter(Boolean);
}

function matchRule(pattern: string, url: string): RuleResult {
  try {
    return RE2JS.compile(pattern).matcher(url).find() ? "match" : "no-match";
  } catch {
    return "error";
  }
}

export function testUrlRules(
  url: string,
  blacklist: string,
  whitelist: string,
): { blocked: boolean; ruleError: boolean } {
  let blacklistResult: RuleResult | undefined;
  for (const pattern of parseRules(blacklist)) {
    const result = matchRule(pattern, url);
    if (result !== "no-match") {
      blacklistResult = result;
      break;
    }
  }

  if (!blacklistResult) {
    return { blocked: false, ruleError: false };
  }

  let ruleError = blacklistResult === "error";
  for (const pattern of parseRules(whitelist)) {
    const result = matchRule(pattern, url);
    if (result === "match") {
      return { blocked: false, ruleError: false };
    }
    if (result === "error") {
      ruleError = true;
    }
  }
  return { blocked: true, ruleError };
}
