import assert from "node:assert/strict";
import { readFileSync, existsSync } from "node:fs";
import { join } from "node:path";
import { test } from "node:test";
import { page } from "./scriptlet-fixture.mjs";
import { xhrFixture } from "./xhr-fixture.mjs";

const path = (...names) => names.map((name) => [false, [name], null]);
const program = (steps, mutation = null) => JSON.stringify([true, steps, [], mutation]);
const jsonArgs = (steps, mutation = null, props = "") => [
  "",
  program(steps, mutation),
  props,
  "",
  "",
];
const objectArgs = (steps, mutation = null, position = "", order = "") => [
  "app.call",
  position,
  "",
  order,
  program(steps, mutation),
];

test("object hooks follow late owners and functions and preserve receivers and exceptions", () => {
  const p = page([["EDIT_OUTBOUND_OBJECT", objectArgs(path("ads"))]]);
  p.install();
  assert.equal(
    p.run(`(() => {
        app = { id: 7, call() { return { ads: true, id: this.id }; } };
        const first = JSON.stringify(app.call());
        app.call = function() { return { ads: false, id: this.id + 1 }; };
        return first + '|' + JSON.stringify(app.call());
    })()`),
    '{"id":7}|{"id":8}',
  );
  assert.throws(
    () => p.run(`app.call = () => { throw new TypeError('native'); }; app.call()`),
    { name: "TypeError", message: "native" },
  );
});

test("inbound editing clones the selected argument and supports negative positions", () => {
  const p = page([["EDIT_INBOUND_OBJECT", objectArgs(path("ads"), null, "-1")]], {
    before: "app = { call(...args) { return args; } };",
  });
  p.install();
  assert.equal(
    p.run(`(() => {
        const original = { ads: true, keep: 1 };
        const result = app.call('first', original);
        return JSON.stringify([original, result, result[1] === original]);
    })()`),
    '[{"ads":true,"keep":1},["first",{"keep":1}],false]',
  );
  assert.equal(p.run("app.call().length"), 0);
});

test("receiver editing preserves order and getter/setter identity", () => {
  for (const order of ["", "after"]) {
    const p = page([["EDIT_THIS_OBJECT", objectArgs(path("ads"), null, "", order)]], {
      before: "app = { ads: true, call() { return this.ads; } };",
    });
    p.install();
    assert.equal(p.run("app.call()"), order === "after" ? true : undefined);
    assert.equal(p.run('Object.hasOwn(app, "ads")'), false);
  }
  for (const type of ["EDIT_OBJECT_ON_GETTER", "EDIT_OBJECT_ON_SETTER"]) {
    const args = objectArgs(path("ads"));
    args[0] = "app.value";
    const p = page([[type, args]], { before: "app = {};" });
    p.install();
    assert.equal(
      p.run(`(() => {
            const value = { ads: true, keep: 1 }; app.value = value;
            const same = app.value === value;
            return same && !Object.hasOwn(value, 'ads') && value.keep === 1;
        })()`),
      true,
    );
  }
});

test("trusted JSON edits resolve, merge, replace, call, and replace roots", () => {
  const cases = [
    [path("ads"), ["=", false], '{"ads":true}', '{"ads":false}'],
    [path("missing"), ["=", null], "{}", "{}"],
    [
      path("settings"),
      ["+", { enabled: false }],
      '{"settings":{"keep":1}}',
      '{"settings":{"keep":1,"enabled":false}}',
    ],
    [
      path("label"),
      ["repl", { regex: "ad", flags: "g", replacement: "ok" }],
      '{"label":"ad ad"}',
      '{"label":"ok ok"}',
    ],
    [[], ["=", { replaced: true }], '{"ads":1}', '{"replaced":true}'],
    [[], null, '{"ads":1}', "null"],
  ];
  for (const [steps, mutation, input, expected] of cases) {
    const p = page([["TRUSTED_JSON_EDIT", jsonArgs(steps, mutation)]]);
    p.install();
    assert.equal(p.run(`JSON.stringify(JSON.parse(${JSON.stringify(input)}))`), expected);
  }
  const p = page([
    [
      "TRUSTED_JSON_EDIT",
      jsonArgs(path("ads"), ["call", ["${obj}", "mark", "${key}", "${val}"]]),
    ],
  ]);
  p.install();
  assert.equal(
    p.run(`(() => {
        const value = { ads: 1, mark(key, old) { this.marked = key + old; } };
        return JSON.parse('{}', () => value).marked;
    })()`),
    "ads1",
  );
});

test("Fetch/Request editors preserve options, inherited fields, headers, and bodies", async () => {
  const calls = [];
  const p = page([["JSON_EDIT_FETCH_REQUEST", jsonArgs(path("ads"), null, "method:POST")]], {
    globals: {
      fetch(...args) {
        calls.push(args);
        return Promise.resolve(new Response("{}"));
      },
    },
  });
  p.install();
  p.run(`init = Object.assign(Object.create({ method: 'POST', credentials: 'include' }), {
        body: '{"ads":true,"keep":1}', headers: { 'x-session': 'kept' }
    }); fetch('https://site.example/api', init);`);
  assert.equal(calls[0][1].body, '{"keep":1}');
  assert.equal(calls[0][1].credentials, "include");
  assert.equal(calls[0][1].headers["x-session"], "kept");
  assert.equal(p.run("init.body"), '{"ads":true,"keep":1}');
  const request = p.run(`new Request('https://site.example/api', init)`);
  assert.equal(request.method, "POST");
  assert.equal(await request.text(), '{"keep":1}');
  const original = new Request("https://site.example/api", {
    method: "POST",
    body: '{"ads":1}',
  });
  p.context.original = original;
  await p.run("fetch(original)");
  assert.equal(calls[1][0], original);
  assert.equal(original.bodyUsed, false);
  await p.run('fetch("https://site.example/api", { method: "PUT", body: "{\\"ads\\":1}" })');
  assert.equal(calls[2][1].body, '{"ads":1}');
});

test("XHR request editing preserves native send errors and reusable request state", () => {
  const native = xhrFixture();
  const p = page(
    [["TRUSTED_JSON_EDIT_XHR_REQUEST", jsonArgs(path("ads"), ["=", false], "method:POST")]],
    { globals: native.globals },
  );
  p.install();
  const xhr = new native.XHR();
  xhr.open("POST", "/api");
  xhr.send('{"ads":true}');
  assert.equal(native.sends[0][1], '{"ads":false}');
  xhr.open("GET", "/other");
  xhr.send('{"ads":true}');
  assert.equal(native.sends[1][1], '{"ads":true}');
  assert.throws(() => xhr.send("{}"), { name: "InvalidStateError" });
});

test("trusted JSON and JSONL response editors share Fetch/XHR lifecycle handling", async () => {
  for (const prefix of ["JSON_EDIT", "JSONL_EDIT"]) {
    const text = prefix === "JSONL_EDIT" ? '{"ads":1}\n{"ads":2}\n' : '{"ads":1}';
    const expected =
      prefix === "JSONL_EDIT" ? '{"ads":false}\n{"ads":false}\n' : '{"ads":false}';
    const args = jsonArgs(path("ads"), ["=", false]);
    const p = page([["TRUSTED_" + prefix + "_FETCH", args]], {
      globals: { TextDecoder, fetch: () => Promise.resolve(new Response(text)) },
    });
    p.install();
    assert.equal(await (await p.run('fetch("/api")')).text(), expected);
    const native = xhrFixture();
    const x = page([["TRUSTED_" + prefix + "_XHR", args]], { globals: native.globals });
    x.install();
    const xhr = new native.XHR();
    xhr.open("GET", "/api");
    xhr.send();
    xhr.complete(text);
    assert.equal(xhr.responseText, expected);
  }
});

test("trusted legacy pruning keeps one-based arguments, guards, and copy semantics", () => {
  for (const copy of ["", "1"]) {
    const p = page(
      [["TRUSTED_PRUNE_INBOUND_OBJECT", ["app.call", "1", "ads", "keep", copy, ""]]],
      {
        before: "app = { call(value) { return value; } };",
      },
    );
    p.install();
    assert.equal(
      p.run(`(() => {
            const value = { ads: true, keep: 1 }; const result = app.call(value);
            return JSON.stringify([result, value, result === value]);
        })()`),
      copy ? '[{"keep":1},{"ads":true,"keep":1},false]' : '[{"keep":1},{"keep":1},true]',
    );
    assert.equal(p.run("value = { keep: 1 }; app.call(value) === value"), true);
  }
  const p = page([["TRUSTED_PRUNE_OUTBOUND_OBJECT", ["app.call", "", "ads", "", "", ""]]], {
    before: "app = { call() { return { ads: 1, keep: 2 }; } };",
  });
  p.install();
  assert.equal(p.run("JSON.stringify(app.call())"), '{"keep":2}');
});

test("legacy entry pruning preserves array order across multiple paths", () => {
  const p = page([["JSON_PRUNE", ["items.[-].second items.[-].first objects.{-}.ads", ""]]]);
  p.install();
  assert.equal(
    p.run(
      `JSON.stringify(JSON.parse('{
        "items":[{"first":1},{"keep":2},{"second":3}],
        "objects":{"a":{"ads":1},"b":{"keep":2}}
    }'))`.replace(/\n/g, ""),
    ),
    '{"items":[{"keep":2}],"objects":{"b":{"keep":2}}}',
  );
});

test("trusted custom method replacements follow late properties and method reassignment", () => {
  const p = page([["TRUSTED_REPLACE_ARGUMENT", ["app.call", "0", "false"]]]);
  p.install();
  assert.equal(p.run("app = { call(value) { return value; } }; app.call(true)"), false);
  assert.equal(p.run("app.call = value => !value; app.call(true)"), true);
});

test(
  "Kotlin-produced object instructions execute through the production dispatch table",
  {
    skip:
      !process.env.ROADMAP_OUTPUT_DIR ||
      !existsSync(join(process.env.ROADMAP_OUTPUT_DIR, "objects.js")),
  },
  () => {
    const p = page([], {
      before: `app = {
        read() { return { ads: 1, keep: 2 }; }, write(value) { return value; },
        next() { return this.ads; }, state: { ads: 1 }
    };`,
    });
    p.run(readFileSync(join(process.env.ROADMAP_OUTPUT_DIR, "objects.js"), "utf8"));
    assert.equal(p.run("JSON.stringify(app.read())"), '{"keep":2}');
  },
);

test(
  "Kotlin-produced trusted mutations and legacy selectors agree with pinned upstream JSONPath",
  {
    skip:
      !process.env.ROADMAP_OUTPUT_DIR ||
      !process.env.UBLOCK_JSONPATH_SOURCE ||
      !existsSync(join(process.env.ROADMAP_OUTPUT_DIR, "json-mutations.json")),
  },
  async () => {
    const code = readFileSync(process.env.UBLOCK_JSONPATH_SOURCE, "utf8");
    const { JSONPath } = await import("data:text/javascript," + encodeURIComponent(code));
    const cases = JSON.parse(
      readFileSync(join(process.env.ROADMAP_OUTPUT_DIR, "json-mutations.json"), "utf8"),
    );
    const data = {
      ads: 1,
      settings: { keep: 2 },
      label: "ad ad",
      items: [{ advert: true, ad: 1 }, { keep: 2 }, { ad: 3 }],
    };
    for (const [query, source] of cases) {
      const expected = structuredClone(data);
      expected.mark = function (key, value) {
        this.marked = key + ":" + value;
      };
      const program = JSONPath.create(query);
      assert.equal(program.valid, true, query);
      const edited = program.apply(expected);
      const wanted = edited === undefined ? expected : edited;
      const input = structuredClone(data);
      input.mark = function (key, value) {
        this.marked = key + ":" + value;
      };
      const p = page([]);
      p.context.input = input;
      p.run(source);
      const actual = p.run('JSON.stringify(JSON.parse("{}", () => input))');
      assert.deepEqual(JSON.parse(actual), JSON.parse(JSON.stringify(wanted)), query);
    }
  },
);

test('request edits retain getter receivers and delegate accessor bodies', async () => {
  const p = page([['JSON_EDIT_FETCH_REQUEST', jsonArgs(path('ads'))]], {
    globals: { fetch(url, init) { return Promise.resolve(new Request(url, init)); } },
  }); p.install();
  p.run(`options = { method: 'POST', body: '{"ads":1,"keep":2}',
    get credentials() {
      if (this !== options) throw new TypeError('Changed receiver');
      return 'include';
    }
  };`);
  const edited = await p.run('fetch("https://site.example/api", options)');
  assert.equal(edited.credentials, 'include');
  assert.equal(await edited.text(), '{"keep":2}');
  p.run(`reads = 0; options = { method: 'POST', get body() {
    reads++; return '{"ads":1,"keep":2}';
  } };`);
  const original = await p.run('fetch("https://site.example/api", options)');
  assert.equal(await original.text(), '{"ads":1,"keep":2}');
  assert.equal(p.run('reads'), 1);
});
