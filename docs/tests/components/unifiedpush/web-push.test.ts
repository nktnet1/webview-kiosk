import {
  createDecipheriv,
  createECDH,
  createHmac,
  randomBytes,
} from "node:crypto";
import { describe, expect, it } from "vitest";
import {
  createCurlCommand,
  generateVapidKeys,
  MAX_PUSH_PAYLOAD_BYTES,
  type PushCredentials,
  prepareWebPush,
  validatePushPayload,
} from "#/components/unifiedpush/web-push";

const decode = (value: string) => Buffer.from(value, "base64url");
const message = JSON.stringify({
  type: "command",
  command: "toast",
  data: { message: "hello 🌍" },
});

async function createCredentials() {
  const recipient = createECDH("prime256v1");
  recipient.generateKeys();
  const vapid = await generateVapidKeys();
  const auth = randomBytes(16);
  const credentials: PushCredentials = {
    endpoint: "https://push.example.org/api/long-token?up=1",
    p256dh: recipient.getPublicKey().toString("base64url"),
    auth: auth.toString("base64url"),
    vapidPublicKey: vapid.publicKey,
    vapidPrivateKey: vapid.privateKey,
    vapidSubject: "mailto:admin@example.org",
  };
  return { credentials, recipient, auth };
}

const hmac = (key: Uint8Array, bytes: Uint8Array) =>
  createHmac("sha256", key).update(bytes).digest();
const expand = (key: Uint8Array, info: string, length: number) =>
  hmac(key, Buffer.concat([Buffer.from(`${info}\0`), Buffer.of(1)])).subarray(
    0,
    length,
  );

describe("Web Push builder", () => {
  it("creates an RFC 8291 aes128gcm record that independently decrypts", async () => {
    const { credentials, recipient, auth } = await createCredentials();
    const request = await prepareWebPush(credentials, message, 90);

    expect(request.endpoint).toBe(credentials.endpoint);
    expect(request.headers.TTL).toBe("90");
    expect(request.headers["Content-Encoding"]).toBe("aes128gcm");
    expect(request.body.length).toBeLessThanOrEqual(4096);

    const record = Buffer.from(request.body);
    const salt = record.subarray(0, 16);
    expect(record.readUInt32BE(16)).toBe(4096);
    expect(record[20]).toBe(65);
    const senderPublic = record.subarray(21, 86);
    const ciphertext = record.subarray(86);
    const ecdhSecret = recipient.computeSecret(senderPublic);
    const prk = hmac(auth, ecdhSecret);
    const ikm = hmac(
      prk,
      Buffer.concat([
        Buffer.from("WebPush: info\0"),
        recipient.getPublicKey(),
        senderPublic,
        Buffer.of(1),
      ]),
    );
    const intermediate = hmac(salt, ikm);
    const key = expand(intermediate, "Content-Encoding: aes128gcm", 16);
    const nonce = expand(intermediate, "Content-Encoding: nonce", 12);
    const cipher = createDecipheriv("aes-128-gcm", key, nonce);
    cipher.setAuthTag(ciphertext.subarray(-16));
    const decrypted = Buffer.concat([
      cipher.update(ciphertext.subarray(0, -16)),
      cipher.final(),
    ]);
    expect(decrypted.at(-1)).toBe(2);
    expect(decrypted.subarray(0, -1).toString()).toBe(message);
  });

  it("signs a verifiable VAPID JWT for the endpoint origin", async () => {
    const { credentials } = await createCredentials();
    const request = await prepareWebPush(credentials, message, 0);
    const [token, key] = request.headers.Authorization.replace(
      "vapid t=",
      "",
    ).split(", k=");
    expect(key).toBe(credentials.vapidPublicKey);
    const [header, claims, signature] = token.split(".");
    expect(JSON.parse(decode(header).toString())).toEqual({
      typ: "JWT",
      alg: "ES256",
    });
    expect(JSON.parse(decode(claims).toString())).toMatchObject({
      aud: "https://push.example.org",
      sub: "mailto:admin@example.org",
    });
    const publicKey = await crypto.subtle.importKey(
      "raw",
      decode(key),
      { name: "ECDSA", namedCurve: "P-256" },
      false,
      ["verify"],
    );
    expect(
      await crypto.subtle.verify(
        { name: "ECDSA", hash: "SHA-256" },
        publicKey,
        decode(signature),
        new TextEncoder().encode(`${header}.${claims}`),
      ),
    ).toBe(true);
  });

  it("rejects mismatched VAPID keys", async () => {
    const { credentials } = await createCredentials();
    credentials.vapidPrivateKey = (await generateVapidKeys()).privateKey;
    await expect(prepareWebPush(credentials, message, 60)).rejects.toThrow(
      /do not match/,
    );
  });

  it("validates URL, keys, payload and ttl before sending", async () => {
    const { credentials } = await createCredentials();
    await expect(
      prepareWebPush(
        { ...credentials, endpoint: "http://127.0.0.1/push" },
        message,
        60,
      ),
    ).rejects.toThrow(/HTTPS/);
    await expect(
      prepareWebPush({ ...credentials, p256dh: "bad" }, message, 60),
    ).rejects.toThrow(/65 bytes/);
    await expect(
      prepareWebPush({ ...credentials, auth: "bad" }, message, 60),
    ).rejects.toThrow(/16 bytes/);
    await expect(prepareWebPush(credentials, message, -1)).rejects.toThrow(
      /TTL/,
    );
    await expect(prepareWebPush(credentials, message, 1.5)).rejects.toThrow(
      /TTL/,
    );
    await expect(
      prepareWebPush(credentials, message, 2_419_201),
    ).rejects.toThrow(/TTL/);
    await expect(
      prepareWebPush(
        { ...credentials, vapidSubject: "http://untrusted.example" },
        message,
        60,
      ),
    ).rejects.toThrow(/contact/);
    await expect(prepareWebPush(credentials, "not JSON", 60)).rejects.toThrow(
      /valid JSON/,
    );
    await expect(prepareWebPush(credentials, "{}", 60)).rejects.toThrow(/type/);
    await expect(
      prepareWebPush(
        credentials,
        JSON.stringify({
          type: "command",
          command: "toast",
          data: { message: "x".repeat(MAX_PUSH_PAYLOAD_BYTES) },
        }),
        60,
      ),
    ).rejects.toThrow(/3993/);
  });

  it("minifies JSON while keeping custom command fields", () => {
    expect(
      validatePushPayload(
        `{ "type":"command", "command":"toast", "wakeScreen":true }`,
      ),
    ).toBe('{"type":"command","command":"toast","wakeScreen":true}');
  });

  it("shell-quotes the destination without disclosing private keys in a cURL command", async () => {
    const { credentials } = await createCredentials();
    credentials.endpoint = "https://push.example.org/token'quoted";
    const request = await prepareWebPush(credentials, message, 60);
    const command = createCurlCommand(request);
    expect(command).toContain("--data-binary @unifiedpush-message.bin");
    expect(command).toContain("Content-Encoding: aes128gcm");
    expect(command).toContain("'\\''");
    expect(command).not.toContain(credentials.vapidPrivateKey);
    expect(command).not.toContain(credentials.auth);
  });
});
