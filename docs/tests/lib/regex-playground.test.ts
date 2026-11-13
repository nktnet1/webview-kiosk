import { describe, expect, it } from "vitest";
import { testUrlRules } from "#/lib/regex-playground";

describe("RE2 URL playground", () => {
  const url = "https://example.com/allowed";

  it("uses partial RE2 matches and whitelist precedence", () => {
    expect(testUrlRules(url, "example\\.com", "/allowed")).toEqual({
      blocked: false,
      ruleError: false,
    });
    expect(testUrlRules(url, "example\\.com", "/private")).toEqual({
      blocked: true,
      ruleError: false,
    });
  });

  it("fails closed on an unsupported blacklist rule", () => {
    expect(testUrlRules(url, "(?<=https://)example", "")).toEqual({
      blocked: true,
      ruleError: true,
    });
  });

  it("fails closed when an evaluated whitelist rule is invalid", () => {
    expect(testUrlRules(url, "example", "(")).toEqual({
      blocked: true,
      ruleError: true,
    });
  });

  it("still honours a valid whitelist even if another rule is invalid", () => {
    expect(testUrlRules(url, "(", "/allowed")).toEqual({
      blocked: false,
      ruleError: false,
    });
  });

  it("does not evaluate a whitelist when no blacklist matches", () => {
    expect(testUrlRules(url, "other\\.com", "(")).toEqual({
      blocked: false,
      ruleError: false,
    });
  });

  it("accepts valid counted repetitions", () => {
    expect(testUrlRules("https://example.com/aaaa", "a{4}", "")).toEqual({
      blocked: true,
      ruleError: false,
    });
  });

  it("does not reject long URLs or valid configured patterns by an estimate", () => {
    const literal = "a".repeat(4200);
    expect(testUrlRules(`https://example.com/${literal}`, literal, "")).toEqual(
      {
        blocked: true,
        ruleError: false,
      },
    );
  });

  it("evaluates large lists of harmless rules without a shared budget", () => {
    const patterns = Array.from(
      { length: 257 },
      (_, index) => `^https://denied\\.example/${index}$`,
    ).join("\n");
    const url = `https://denied.example/allowed?${"x".repeat(4000)}`;
    expect(testUrlRules(url, patterns, "")).toEqual({
      blocked: false,
      ruleError: false,
    });
    expect(testUrlRules("https://denied.example/256", patterns, "")).toEqual({
      blocked: true,
      ruleError: false,
    });
    expect(testUrlRules("allowed", "^blocked|allowed", "")).toEqual({
      blocked: true,
      ruleError: false,
    });
  });

  it("handles the nested-quantifier input without catastrophic backtracking", () => {
    const expensive = `https://example.com/${"a".repeat(6000)}!`;
    expect(testUrlRules(expensive, "(a+)+$", "")).toEqual({
      blocked: false,
      ruleError: false,
    });
  });
});
