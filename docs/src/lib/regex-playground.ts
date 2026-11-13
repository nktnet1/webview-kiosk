import { RE2JS } from "re2js";

type RuleResult = "match" | "no-match" | "error";

// Mirror Android's URL rule limits and RE2/J's unanchored find() semantics.
const MAX_PATTERN_LENGTH = 4096;
const MAX_URL_LENGTH = 65536;

function parseRules(input: string): string[] {
  return input
    .split("\n")
    .map((line) => line.trim())
    .filter(Boolean);
}

function matchRule(pattern: string, url: string): RuleResult {
  if (pattern.length > MAX_PATTERN_LENGTH || url.length > MAX_URL_LENGTH) {
    return "error";
  }

  try {
    return RE2JS.compile(pattern).matcher(url).find() ? "match" : "no-match";
  } catch {
    return "error";
  }
}

/** Apply the same blacklist-first, whitelist-override and fail-closed policy as Android. */
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
