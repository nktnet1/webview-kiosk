import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "#/components/ui/select";
import type { MqttQos } from "./validation";

export function MqttQosSelect({
  value,
  onChange,
  id,
  compact = false,
}: {
  value: MqttQos;
  onChange: (value: MqttQos) => void;
  id: string;
  compact?: boolean;
}) {
  return (
    <Select
      value={String(value)}
      onValueChange={(next) => onChange(Number(next) as MqttQos)}
    >
      <SelectTrigger
        id={id}
        className={compact ? "w-full min-w-0 gap-1 px-2" : "w-full min-w-0"}
      >
        <SelectValue>{compact ? String(value) : undefined}</SelectValue>
      </SelectTrigger>
      <SelectContent>
        <SelectItem value="0">QoS 0</SelectItem>
        <SelectItem value="1">QoS 1</SelectItem>
        <SelectItem value="2">QoS 2</SelectItem>
      </SelectContent>
    </Select>
  );
}
