/** Properties allowed on MQTT 5 PUBLISH packets (not CONNECT or PUBACK). */
export type MqttUserProperty = { name: string; value: string };

export type MqttPublishProperties = {
  payloadFormatIndicator?: number;
  messageExpiryInterval?: number;
  contentType?: string;
  responseTopic?: string;
  /** Binary correlation data in hexadecimal, to avoid lossy UTF-8 decoding. */
  correlationData?: string;
  subscriptionIdentifiers?: number[];
  topicAlias?: number;
  userProperties?: MqttUserProperty[];
};

const utf8 = new TextDecoder("utf-8", { fatal: true });

export function mqttHex(bytes: Uint8Array): string {
  return Array.from(bytes, (byte) => byte.toString(16).padStart(2, "0")).join(
    "",
  );
}

/** Strictly decode PUBLISH properties without reading beyond the property block. */
export function parseMqttPublishProperties(
  data: Uint8Array,
): MqttPublishProperties {
  const properties: MqttPublishProperties = {};
  const seen = new Set<number>();
  let offset = 0;

  function requireBytes(count: number) {
    if (offset + count > data.length)
      throw new Error("Truncated MQTT 5 PUBLISH property.");
  }
  function byte(): number {
    requireBytes(1);
    return data[offset++];
  }
  function word(): number {
    return byte() * 256 + byte();
  }
  function uint32(): number {
    return byte() * 16_777_216 + byte() * 65_536 + byte() * 256 + byte();
  }
  function binary(): Uint8Array {
    const size = word();
    requireBytes(size);
    const result = data.subarray(offset, offset + size);
    offset += size;
    return result;
  }
  function string(): string {
    try {
      return utf8.decode(binary());
    } catch {
      throw new Error("Invalid MQTT 5 UTF-8 property.");
    }
  }
  function variable(): number {
    let value = 0;
    let multiplier = 1;
    for (let i = 0; i < 4; i++) {
      const next = byte();
      value += (next & 0x7f) * multiplier;
      if ((next & 0x80) === 0) return value;
      multiplier *= 128;
    }
    throw new Error("Invalid MQTT 5 subscription identifier.");
  }

  while (offset < data.length) {
    const id = byte();
    if (id !== 0x0b && id !== 0x26) {
      if (seen.has(id)) throw new Error("Repeated MQTT 5 PUBLISH property.");
      seen.add(id);
    }
    switch (id) {
      case 0x01: {
        const format = byte();
        if (format > 1)
          throw new Error("Invalid MQTT payload format indicator.");
        properties.payloadFormatIndicator = format;
        break;
      }
      case 0x02:
        properties.messageExpiryInterval = uint32();
        break;
      case 0x03:
        properties.contentType = string();
        break;
      case 0x08:
        properties.responseTopic = string();
        break;
      case 0x09:
        properties.correlationData = mqttHex(binary());
        break;
      case 0x0b: {
        const identifier = variable();
        if (identifier === 0)
          throw new Error("Invalid MQTT subscription identifier.");
        properties.subscriptionIdentifiers = [
          ...(properties.subscriptionIdentifiers ?? []),
          identifier,
        ];
        break;
      }
      case 0x23: {
        const alias = word();
        if (alias === 0) throw new Error("Invalid MQTT topic alias.");
        properties.topicAlias = alias;
        break;
      }
      case 0x26:
        properties.userProperties = [
          ...(properties.userProperties ?? []),
          { name: string(), value: string() },
        ];
        break;
      default:
        throw new Error(
          `Unsupported MQTT 5 PUBLISH property 0x${id.toString(16)}.`,
        );
    }
  }
  return properties;
}

/** MQTT does not disclose the publisher's broker login to subscribers. */
export function reportedMqttSender(
  properties: MqttPublishProperties | undefined,
): { username?: string; appInstanceId?: string } | null {
  const userProperties = properties?.userProperties ?? [];
  const username = userProperties.find(
    ({ name, value }) => name === "username" && value.trim() !== "",
  )?.value;
  const appInstanceId = userProperties.find(
    ({ name, value }) => name === "appInstanceId" && value.trim() !== "",
  )?.value;
  return username || appInstanceId ? { username, appInstanceId } : null;
}
