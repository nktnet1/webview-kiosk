import { RE2JS } from "re2js";
import { estimateRe2RuleCost } from "#/lib/re2-complexity";

type RuleResult = "match" | "no-match" | "error";

const MAX_MATCH_WORK = 24_000_000;
type MatchBudget = { remaining: number };

// Mirror Android's RE2/J unanchored find() semantics and complexity guard.

function parseRules(input: string): string[] {
  return input
    .split("\n")
    .map((line) => line.trim())
    .filter(Boolean);
}

function matchRule(
  pattern: string,
  url: string,
  budget: MatchBudget,
): RuleResult {
  const cost = estimateRe2RuleCost(pattern);
  if (cost === null) return "error";

  const work = Math.max(cost, 1) * Math.max(url.length, 1);
  if (work > budget.remaining) return "error";
  budget.remaining -= work;

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
  const budget: MatchBudget = { remaining: MAX_MATCH_WORK };
  for (const pattern of parseRules(blacklist)) {
    const result = matchRule(pattern, url, budget);
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
    const result = matchRule(pattern, url, budget);
    if (result === "match") {
      return { blocked: false, ruleError: false };
    }
    if (result === "error") {
      ruleError = true;
    }
  }
  return { blocked: true, ruleError };
}
