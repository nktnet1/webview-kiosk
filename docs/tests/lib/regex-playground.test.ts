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

  it("fails closed on expensive counted repetitions", () => {
    expect(testUrlRules(url, "(a{1000}){1000}", "")).toEqual({
      blocked: true,
      ruleError: true,
    });
    expect(testUrlRules(url, "example", "(a{1000}|b{1000}){1000}")).toEqual({
      blocked: true,
      ruleError: true,
    });
    expect(testUrlRules(url, "example", "/allowed")).toEqual({
      blocked: false,
      ruleError: false,
    });
  });

  it("allows long URLs when the rule is safe", () => {
    const longUrl = `https://example.com/${"a".repeat(70_000)}/allowed`;
    expect(testUrlRules(longUrl, "/denied$", "")).toEqual({
      blocked: false,
      ruleError: false,
    });
    expect(testUrlRules(longUrl, "/allowed$", "")).toEqual({
      blocked: true,
      ruleError: false,
    });
  });

  it("accepts safe patterns longer than the previous character limit", () => {
    const literal = "a".repeat(4200);
    expect(testUrlRules(`https://example.com/${literal}`, literal, "")).toEqual(
      {
        blocked: true,
        ruleError: false,
      },
    );
  });

  it("rejects large RE2 programs on long URLs before matching", () => {
    const longUrl = `https://example.com/${"a".repeat(70_000)}`;
    for (const pattern of [`${"a?".repeat(4000)}b`, `${"a".repeat(4200)}b`]) {
      expect(testUrlRules(longUrl, pattern, "")).toEqual({
        blocked: true,
        ruleError: true,
      });
    }
  });

  it("shares a match work budget across the blacklist", () => {
    const mediumUrl = `https://example.com/${"a".repeat(4200)}`;
    const blacklist = ["b".repeat(4200), "c".repeat(4200)].join("\n");
    expect(testUrlRules(mediumUrl, blacklist, "")).toEqual({
      blocked: true,
      ruleError: true,
    });
  });

  it("rejects excessive grouping depth", () => {
    expect(
      testUrlRules(url, `${"(".repeat(65)}a${")".repeat(65)}`, ""),
    ).toEqual({
      blocked: true,
      ruleError: true,
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
