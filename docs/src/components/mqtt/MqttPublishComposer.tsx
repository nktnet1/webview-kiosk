import { Send } from "lucide-react";
import { InfoTooltip } from "#/components/remote-control/InfoTooltip";
import { RemoteMessageEditor } from "#/components/remote-control/RemoteMessageEditor";
import { Button } from "#/components/ui/button";
import { Card } from "#/components/ui/card";
import { Checkbox } from "#/components/ui/checkbox";
import { Input } from "#/components/ui/input";
import { Label } from "#/components/ui/label";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "#/components/ui/select";
import { Textarea } from "#/components/ui/textarea";
import { MqttQosSelect } from "./MqttQosSelect";
import { RequestEditor } from "./RequestEditor";
import type { MqttQos } from "./validation";

export const PUBLISH_MODES = [
  "command",
  "settings",
  "request",
  "custom",
] as const;
export type PublishMode = (typeof PUBLISH_MODES)[number];

type Props = {
  mode: PublishMode;
  onModeChange: (mode: PublishMode) => void;
  commandPayload: string;
  onCommandPayloadChange: (value: string) => void;
  settingsPayload: string;
  onSettingsPayloadChange: (value: string) => void;
  requestPayload: string;
  onRequestPayloadChange: (value: string) => void;
  customPayload: string;
  onCustomPayloadChange: (value: string) => void;
  responseTopic: string;
  onResponseTopicChange: (value: string) => void;
  correlationData: string;
  onCorrelationDataChange: (value: string) => void;
  topic: string;
  onTopicChange: (value: string) => void;
  qos: MqttQos;
  onQosChange: (qos: MqttQos) => void;
  retain: boolean;
  onRetainChange: (value: boolean) => void;
  connected: boolean;
  busy: boolean;
  onPublish: () => void;
};

export function MqttPublishComposer({
  mode,
  onModeChange,
  commandPayload,
  onCommandPayloadChange,
  settingsPayload,
  onSettingsPayloadChange,
  requestPayload,
  onRequestPayloadChange,
  customPayload,
  onCustomPayloadChange,
  responseTopic,
  onResponseTopicChange,
  correlationData,
  onCorrelationDataChange,
  topic,
  onTopicChange,
  qos,
  onQosChange,
  retain,
  onRetainChange,
  connected,
  busy,
  onPublish,
}: Props) {
  return (
    <section aria-label="Publish messages" className="min-w-0">
      <Card className="gap-0 overflow-hidden py-0">
        <div className="border-b px-3 py-3">
          <h2 className="text-sm font-semibold">Publish</h2>
        </div>
        <div className="min-w-0 space-y-3 p-3">
          <div className="space-y-1.5">
            <Label htmlFor="mqtt-publish-mode">Message type</Label>
            <Select
              value={mode}
              onValueChange={(next) => onModeChange(next as PublishMode)}
            >
              <SelectTrigger id="mqtt-publish-mode" className="w-full">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="command">Command</SelectItem>
                <SelectItem value="settings">Settings</SelectItem>
                <SelectItem value="request">Request</SelectItem>
                <SelectItem value="custom">Custom payload</SelectItem>
              </SelectContent>
            </Select>
          </div>
          <div className="space-y-1.5">
            <Label htmlFor="mqtt-publish-topic">Publish topic</Label>
            <Input
              id="mqtt-publish-topic"
              className="font-mono text-xs"
              spellCheck={false}
              value={topic}
              onChange={(event) => onTopicChange(event.target.value)}
            />
          </div>
          <section
            id="mqtt-publish-editor"
            aria-label="Message payload editor"
            className="max-h-[min(28rem,55vh)] min-w-0 space-y-3 overflow-y-auto overscroll-contain rounded-md border p-3"
          >
            {mode === "command" || mode === "settings" ? (
              <RemoteMessageEditor
                key={mode}
                transport="mqtt"
                kind={mode}
                value={mode === "command" ? commandPayload : settingsPayload}
                onChange={
                  mode === "command"
                    ? onCommandPayloadChange
                    : onSettingsPayloadChange
                }
                onKindChange={onModeChange}
                showMessageTypeSelect={false}
              />
            ) : mode === "request" ? (
              <RequestEditor
                value={requestPayload}
                onChange={onRequestPayloadChange}
                responseTopic={responseTopic}
                onResponseTopicChange={onResponseTopicChange}
                correlationData={correlationData}
                onCorrelationDataChange={onCorrelationDataChange}
              />
            ) : (
              <div className="space-y-2">
                <Label htmlFor="mqtt-custom-message">Payload (UTF-8)</Label>
                <Textarea
                  id="mqtt-custom-message"
                  className="min-h-32 font-mono text-sm"
                  rows={5}
                  value={customPayload}
                  spellCheck={false}
                  onChange={(event) =>
                    onCustomPayloadChange(event.target.value)
                  }
                />
              </div>
            )}
          </section>
          <div className="flex flex-wrap items-end justify-between gap-2 border-t pt-3">
            <div className="space-y-1.5">
              <Label htmlFor="mqtt-publish-qos">QoS</Label>
              <MqttQosSelect
                id="mqtt-publish-qos"
                value={qos}
                onChange={onQosChange}
              />
            </div>
            <div className="flex h-9 items-center gap-2">
              <Checkbox
                id="mqtt-retain"
                checked={retain}
                onCheckedChange={(value) => onRetainChange(value === true)}
              />
              <Label htmlFor="mqtt-retain">Retain</Label>
              <InfoTooltip>
                Retained commands may run again when the kiosk reconnects.
              </InfoTooltip>
            </div>
            <Button
              type="button"
              size="sm"
              disabled={!connected || busy}
              onClick={onPublish}
            >
              <Send aria-hidden="true" />
              {busy ? "Publishing..." : "Publish"}
            </Button>
          </div>
        </div>
      </Card>
    </section>
  );
}
