// Mirrors Android's RE2/J compilation guard. Matching is linear-time, but
// expanded counted repetitions can make compilation expensive.
const MAX_COST = 8192;
const OVER_BUDGET = MAX_COST + 1;
const MAX_GROUP_DEPTH = 64;

const cap = (value: number) => Math.min(value, OVER_BUDGET);

class Frame {
  branches = 0;
  prefix = 0;
  atom = 0;

  addAtom(cost: number): void {
    this.prefix = cap(this.prefix + this.atom);
    this.atom = cost;
  }

  repeat(count: number): void {
    this.atom = cap(this.atom * count);
  }

  optional(): void {
    if (!this.atom) {
      this.addAtom(1);
    } else {
      this.atom = cap(this.atom + 1);
    }
  }

  alternative(): void {
    this.branches = cap(this.branches + cap(this.prefix + this.atom) + 1);
    this.prefix = 0;
    this.atom = 0;
  }

  cost(): number {
    return cap(this.branches + this.prefix + this.atom);
  }
}

function repetition(pattern: string, start: number): [number, number] | null {
  let index = start + 1;
  if (!isDigit(pattern[index])) return null;

  let min = 0;
  while (isDigit(pattern[index])) {
    min = cap(min * 10 + Number(pattern[index++]));
  }
  let max = min;
  if (pattern[index] === ",") {
    index++;
    if (pattern[index] === "}") {
      max = cap(min + 1);
    } else {
      if (!isDigit(pattern[index])) return null;
      max = 0;
      while (isDigit(pattern[index])) {
        max = cap(max * 10 + Number(pattern[index++]));
      }
    }
  }
  return pattern[index] === "}" ? [index + 1, Math.max(max, 1)] : null;
}

function isDigit(char: string | undefined): boolean {
  return char !== undefined && char >= "0" && char <= "9";
}

export function estimateRe2RuleCost(pattern: string): number | null {
  const stack: Frame[] = [];
  let frame = new Frame();
  let i = 0;

  while (i < pattern.length) {
    switch (pattern[i]) {
      case "\\":
        frame.addAtom(1);
        i += i + 1 < pattern.length ? 2 : 1;
        break;
      case "[": {
        const start = i++;
        let escaped = false;
        while (i < pattern.length) {
          const char = pattern[i++];
          if (!escaped && char === "]") break;
          if (!escaped && char === "\\") {
            escaped = true;
          } else {
            escaped = false;
          }
        }
        frame.addAtom(cap(i - start));
        break;
      }
      case "(":
        if (stack.length === MAX_GROUP_DEPTH) return null;
        stack.push(frame);
        frame = new Frame();
        i++;
        break;
      case ")": {
        const parent = stack.pop();
        if (!parent) return null;
        const groupCost = cap(frame.cost() + 2);
        frame = parent;
        frame.addAtom(groupCost);
        i++;
        break;
      }
      case "|":
        frame.alternative();
        i++;
        break;
      case "*":
      case "+":
      case "?":
        frame.optional();
        i++;
        break;
      case "{": {
        const repeat = frame.atom ? repetition(pattern, i) : null;
        if (repeat) {
          frame.repeat(repeat[1]);
          i = repeat[0];
        } else {
          frame.addAtom(1);
          i++;
        }
        break;
      }
      default:
        frame.addAtom(1);
        i++;
    }
    if (frame.cost() > MAX_COST) return null;
  }
  return !stack.length && frame.cost() <= MAX_COST ? frame.cost() : null;
}

export function isRe2RuleWithinBudget(pattern: string): boolean {
  return estimateRe2RuleCost(pattern) !== null;
}

/** A conservative, anchored literal prefix usable only to skip non-matches. */
export function getAnchoredLiteralPrefix(pattern: string): string | null {
  // A top-level alternative could match without the leading ^. Reject all
  // alternatives conservatively, even those inside groups or character classes.
  if (!pattern.startsWith("^") || pattern.includes("|")) return null;
  let prefix = "";
  let i = 1;
  while (i < pattern.length) {
    const char = pattern[i];
    if (char === "\\") {
      if (
        i + 1 >= pattern.length
        || !"\\.^$|?*+()[]{}".includes(pattern[i + 1])
      ) {
        break;
      }
      prefix += pattern[i + 1];
      i += 2;
    } else if (".^$|?*+()[]{}".includes(char)) {
      break;
    } else {
      prefix += char;
      i++;
    }
  }
  return prefix || null;
}
