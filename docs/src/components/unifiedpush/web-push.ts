/**
 * Web Push (RFC 8291) and VAPID (RFC 8292), implemented with Web Crypto so
 * private keys never have to leave the browser for this debugging tool.
 */
import * as v from "valibot";
import {
  isJsonObject,
  parseJsonObject,
} from "../remote-control/json-validation";

export const MAX_PUSH_PAYLOAD_BYTES = 3993;
export const MAX_PUSH_TTL = 2_419_200;

const encoder = new TextEncoder();
const Base64UrlSchema = v.pipe(v.string(), v.trim(), v.regex(/^[\w-]+={0,2}$/));
const UrlSchema = v.pipe(v.string(), v.trim(), v.url());
const SecureEndpointSchema = v.pipe(
  UrlSchema,
  v.check((value) => {
    const url = new URL(value);
    return (
      url.protocol === "https:"
      && !!url.hostname
      && !url.username
      && !url.password
      && !url.hash
    );
  }),
);
const PushKindSchema = v.picklist(["command", "settings"]);
const NonEmptyCommandSchema = v.pipe(v.string(), v.nonEmpty());
const VapidSubjectSchema = v.pipe(
  v.string(),
  v.trim(),
  v.check((subject) => {
    if (!subject) return true;
    if (subject.startsWith("mailto:")) {
      return /^mailto:[^\s@]+@[^\s@]+$/.test(subject);
    }
    const url = v.safeParse(UrlSchema, subject);
    if (!url.success) return false;
    const parsed = new URL(url.output);
    return parsed.protocol === "https:" && !parsed.username && !parsed.password;
  }),
);

export const PushCredentialsSchema = v.object({
  endpoint: v.string(),
  p256dh: v.string(),
  auth: v.string(),
  vapidPublicKey: v.string(),
  vapidPrivateKey: v.string(),
  vapidSubject: v.string(),
});
export type PushCredentials = v.InferOutput<typeof PushCredentialsSchema>;

export const PushTtlSchema = v.pipe(
  v.number(),
  v.safeInteger(),
  v.minValue(0),
  v.maxValue(MAX_PUSH_TTL),
);

const NonBlankInputSchema = v.pipe(v.string(), v.trim(), v.nonEmpty());

/** Validate TanStack Form's string input with the same bounds as Web Push. */
export function isValidPushTtlInput(value: string): boolean {
  const result = v.safeParse(NonBlankInputSchema, value);
  return result.success && v.is(PushTtlSchema, Number(result.output));
}

export type PreparedPush = {
  endpoint: string;
  headers: Record<string, string>;
  body: Uint8Array<ArrayBuffer>;
};

function encodeBase64Url(bytes: Uint8Array): string {
  return btoa(String.fromCharCode(...bytes))
    .replaceAll("+", "-")
    .replaceAll("/", "_")
    .replaceAll("=", "");
}

function decodeBase64Url(value: string, expectedLength: number, name: string) {
  const result = v.safeParse(Base64UrlSchema, value);
  if (!result.success) throw new Error(`${name} must be base64url-encoded.`);

  let bytes: Uint8Array<ArrayBuffer>;
  try {
    bytes = Uint8Array.from(
      atob(result.output.replaceAll("-", "+").replaceAll("_", "/")),
      (character) => character.charCodeAt(0),
    );
  } catch {
    throw new Error(`${name} must be base64url-encoded.`);
  }

  if (bytes.length !== expectedLength) {
    throw new Error(`${name} must decode to ${expectedLength} bytes.`);
  }
  return bytes;
}

function concat(...parts: Uint8Array[]): Uint8Array<ArrayBuffer> {
  const combined = new Uint8Array(
    parts.reduce((sum, part) => sum + part.length, 0),
  );
  let offset = 0;
  for (const part of parts) {
    combined.set(part, offset);
    offset += part.length;
  }
  return combined;
}

function utf8(value: string): Uint8Array<ArrayBuffer> {
  return encoder.encode(value);
}

export function parsePushEndpoint(value: string): URL {
  const result = v.safeParse(UrlSchema, value);
  if (!result.success)
    throw new Error("Enter a valid HTTPS push endpoint URL.");
  if (!v.is(SecureEndpointSchema, result.output)) {
    throw new Error(
      "The push endpoint must be an HTTPS URL without embedded credentials or a fragment.",
    );
  }
  return new URL(result.output);
}

export function validatePushPayload(value: string): string {
  const message = parseJsonObject(
    value,
    "Enter a valid JSON payload.",
    "The payload must be a JSON object.",
  );
  if (!v.is(PushKindSchema, message.type)) {
    throw new Error('The payload type must be "command" or "settings".');
  }
  if (
    message.type === "command"
    && !v.is(NonEmptyCommandSchema, message.command)
  ) {
    throw new Error('A command payload needs a non-empty "command" string.');
  }
  if (message.type === "settings" && !isJsonObject(message.data)) {
    throw new Error('A settings payload needs a "data" object.');
  }
  return JSON.stringify(message);
}

async function hkdf(
  secret: Uint8Array<ArrayBuffer>,
  salt: Uint8Array<ArrayBuffer>,
  info: Uint8Array<ArrayBuffer>,
  lengthBits: number,
): Promise<Uint8Array<ArrayBuffer>> {
  const key = await crypto.subtle.importKey("raw", secret, "HKDF", false, [
    "deriveBits",
  ]);
  return new Uint8Array(
    await crypto.subtle.deriveBits(
      { name: "HKDF", hash: "SHA-256", salt, info },
      key,
      lengthBits,
    ),
  );
}

async function encryptPayload(
  plaintext: Uint8Array<ArrayBuffer>,
  recipientPublicKey: Uint8Array<ArrayBuffer>,
  authSecret: Uint8Array<ArrayBuffer>,
): Promise<Uint8Array<ArrayBuffer>> {
  const recipient = await crypto.subtle.importKey(
    "raw",
    recipientPublicKey,
    { name: "ECDH", namedCurve: "P-256" },
    false,
    [],
  );
  const sender = await crypto.subtle.generateKey(
    { name: "ECDH", namedCurve: "P-256" },
    true,
    ["deriveBits"],
  );
  const senderPublicKey = new Uint8Array(
    await crypto.subtle.exportKey("raw", sender.publicKey),
  );
  const sharedSecret = new Uint8Array(
    await crypto.subtle.deriveBits(
      { name: "ECDH", public: recipient },
      sender.privateKey,
      256,
    ),
  );
  const salt = crypto.getRandomValues(new Uint8Array(16));
  const ikm = await hkdf(
    sharedSecret,
    authSecret,
    concat(utf8("WebPush: info\0"), recipientPublicKey, senderPublicKey),
    256,
  );
  const cek = await hkdf(ikm, salt, utf8("Content-Encoding: aes128gcm\0"), 128);
  const nonce = await hkdf(ikm, salt, utf8("Content-Encoding: nonce\0"), 96);

  // RFC 8188: one final record, with the 0x02 delimiter and a 16-byte GCM tag.
  const ciphertext = new Uint8Array(
    await crypto.subtle.encrypt(
      { name: "AES-GCM", iv: nonce },
      await crypto.subtle.importKey("raw", cek, "AES-GCM", false, ["encrypt"]),
      concat(plaintext, Uint8Array.of(2)),
    ),
  );

  // RFC 8188 aes128gcm header: salt (16), rs (4, big-endian), idlen (1), keyid (65).
  const header = new Uint8Array(21);
  header.set(salt);
  new DataView(header.buffer).setUint32(16, 4096);
  header[20] = senderPublicKey.length;
  return concat(header, senderPublicKey, ciphertext);
}

function vapidSubjectClaim(value: string): string | undefined {
  const result = v.safeParse(VapidSubjectSchema, value);
  if (!result.success) {
    throw new Error(
      "VAPID contact must be a mailto: email address or an HTTPS URL.",
    );
  }
  return result.output || undefined;
}

async function createVapidAuthorization(
  endpoint: URL,
  publicKey: Uint8Array<ArrayBuffer>,
  privateKey: Uint8Array<ArrayBuffer>,
  subject: string,
): Promise<string> {
  const jwk = {
    kty: "EC",
    crv: "P-256",
    x: encodeBase64Url(publicKey.subarray(1, 33)),
    y: encodeBase64Url(publicKey.subarray(33, 65)),
    d: encodeBase64Url(privateKey),
  };
  let key: CryptoKey;
  try {
    key = await crypto.subtle.importKey(
      "jwk",
      jwk,
      { name: "ECDSA", namedCurve: "P-256" },
      false,
      ["sign"],
    );
  } catch {
    throw new Error(
      "The VAPID public and private keys are invalid or do not match.",
    );
  }
  const contact = vapidSubjectClaim(subject);
  const claims = {
    aud: endpoint.origin,
    exp: Math.floor(Date.now() / 1000) + 12 * 60 * 60,
    ...(contact ? { sub: contact } : {}),
  };
  const unsigned = `${encodeBase64Url(utf8(JSON.stringify({ typ: "JWT", alg: "ES256" })))}.${encodeBase64Url(utf8(JSON.stringify(claims)))}`;
  const signature = new Uint8Array(
    await crypto.subtle.sign(
      { name: "ECDSA", hash: "SHA-256" },
      key,
      utf8(unsigned),
    ),
  );
  const verificationKey = await crypto.subtle.importKey(
    "raw",
    publicKey,
    { name: "ECDSA", namedCurve: "P-256" },
    false,
    ["verify"],
  );
  if (
    !(await crypto.subtle.verify(
      { name: "ECDSA", hash: "SHA-256" },
      verificationKey,
      signature,
      utf8(unsigned),
    ))
  ) {
    throw new Error("The VAPID public and private keys do not match.");
  }
  return `vapid t=${unsigned}.${encodeBase64Url(signature)}, k=${encodeBase64Url(publicKey)}`;
}

export async function generateVapidKeys(): Promise<{
  publicKey: string;
  privateKey: string;
}> {
  if (!globalThis.crypto?.subtle) {
    throw new Error("Web Push encryption requires HTTPS or localhost.");
  }
  const pair = await crypto.subtle.generateKey(
    { name: "ECDSA", namedCurve: "P-256" },
    true,
    ["sign", "verify"],
  );
  const publicKey = new Uint8Array(
    await crypto.subtle.exportKey("raw", pair.publicKey),
  );
  const privateKey = await crypto.subtle.exportKey("jwk", pair.privateKey);
  if (!privateKey.d) throw new Error("Unable to export the VAPID private key.");
  return { publicKey: encodeBase64Url(publicKey), privateKey: privateKey.d };
}

export async function prepareWebPush(
  credentials: PushCredentials,
  payload: string,
  ttl: number,
): Promise<PreparedPush> {
  if (!globalThis.crypto?.subtle) {
    throw new Error("Web Push encryption requires HTTPS or localhost.");
  }
  const parsedCredentials = v.safeParse(PushCredentialsSchema, credentials);
  if (!parsedCredentials.success) {
    throw new Error("Push credential fields must be strings.");
  }
  const endpoint = parsePushEndpoint(parsedCredentials.output.endpoint);
  if (!v.is(PushTtlSchema, ttl)) {
    throw new Error(`TTL must be an integer between 0 and ${MAX_PUSH_TTL}.`);
  }

  const body = utf8(validatePushPayload(payload));
  if (body.length > MAX_PUSH_PAYLOAD_BYTES) {
    throw new Error(
      `Payload must be at most ${MAX_PUSH_PAYLOAD_BYTES} UTF-8 bytes.`,
    );
  }

  const p256dh = decodeBase64Url(
    parsedCredentials.output.p256dh,
    65,
    "Endpoint public key",
  );
  if (p256dh[0] !== 4) {
    throw new Error("Endpoint public key must be an uncompressed P-256 point.");
  }
  const auth = decodeBase64Url(
    parsedCredentials.output.auth,
    16,
    "Endpoint auth secret",
  );
  const vapidPublicKey = decodeBase64Url(
    parsedCredentials.output.vapidPublicKey,
    65,
    "VAPID public key",
  );
  if (vapidPublicKey[0] !== 4) {
    throw new Error("VAPID public key must be an uncompressed P-256 point.");
  }
  const vapidPrivateKey = decodeBase64Url(
    parsedCredentials.output.vapidPrivateKey,
    32,
    "VAPID private key",
  );
  const authorization = await createVapidAuthorization(
    endpoint,
    vapidPublicKey,
    vapidPrivateKey,
    parsedCredentials.output.vapidSubject,
  );
  const encrypted = await encryptPayload(body, p256dh, auth);
  return {
    endpoint: endpoint.toString(),
    headers: {
      TTL: String(ttl),
      "Content-Encoding": "aes128gcm",
      Authorization: authorization,
    },
    body: encrypted,
  };
}
