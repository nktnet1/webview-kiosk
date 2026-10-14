import { useState } from "react";
import { DelimitedListInput } from "#/components/remote-control/DelimitedListInput";
import { InfoTooltip } from "#/components/remote-control/InfoTooltip";
import { isJsonObject } from "#/components/remote-control/json-validation";
import {
  changeTopLevelField,
  stringifyPayload,
} from "#/components/remote-control/payload";
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
import { Tabs, TabsContent, TabsList, TabsTrigger } from "#/components/ui/tabs";
import { Textarea } from "#/components/ui/textarea";
import {
  assertEditableRequest,
  changeRequestType,
  REQUEST_TYPES,
  setRequestSettingKeys,
} from "./request";

type Props = {
  value: string;
  onChange: (value: string) => void;
  responseTopic: string;
  onResponseTopicChange: (value: string) => void;
  correlationData: string;
  onCorrelationDataChange: (value: string) => void;
};

export function RequestEditor({
  value,
  onChange,
  responseTopic,
  onResponseTopicChange,
  correlationData,
  onCorrelationDataChange,
}: Props) {
  const [tab, setTab] = useState("form");
  const [formError, setFormError] = useState<string | null>(null);
  let message: ReturnType<typeof assertEditableRequest> | null = null;
  try {
    message = assertEditableRequest(value);
  } catch {
    /* preserve freeform JSON */
  }

  function update(next: NonNullable<typeof message>) {
    onChange(stringifyPayload(next));
    setFormError(null);
  }

  function changeTab(next: string) {
    if (next === "form") {
      try {
        assertEditableRequest(value);
      } catch (error) {
        setFormError(
          error instanceof Error ? error.message : "Invalid request payload.",
        );
        return;
      }
    }
    setFormError(null);
    setTab(next);
  }

  return (
    <div className="space-y-4">
      <Tabs value={tab} onValueChange={changeTab}>
        <TabsList>
          <TabsTrigger value="form">Form</TabsTrigger>
          <TabsTrigger value="json">JSON</TabsTrigger>
        </TabsList>
        <TabsContent value="form" className="space-y-4 pt-4">
          {message && (
            <>
              <div className="space-y-2">
                <Label htmlFor="mqtt-request-type">Request</Label>
                <Select
                  value={String(message.requestType)}
                  onValueChange={(next) =>
                    update(changeRequestType(message, next))
                  }
                >
                  <SelectTrigger id="mqtt-request-type" className="w-full">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    {REQUEST_TYPES.map((item) => (
                      <SelectItem key={item.name} value={item.name}>
                        {item.label}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
              {message.requestType === "get_settings" && (
                <DelimitedListInput
                  id="mqtt-request-settings"
                  label="Setting keys (blank for all)"
                  multiline
                  values={
                    isJsonObject(message.data)
                    && Array.isArray(message.data.settings)
                      ? message.data.settings.filter(
                          (item): item is string => typeof item === "string",
                        )
                      : []
                  }
                  onChange={(values) =>
                    update(setRequestSettingKeys(message, values.join(", ")))
                  }
                />
              )}
              {message.requestType === "get_launchable_packages" && (
                <div className="flex items-center gap-2">
                  <Checkbox
                    id="mqtt-permitted"
                    checked={
                      isJsonObject(message.data)
                      && message.data.filterLockTaskPermitted === true
                    }
                    onCheckedChange={(checked) => {
                      const data = isJsonObject(message.data)
                        ? message.data
                        : {};
                      update({
                        ...message,
                        data: {
                          ...data,
                          filterLockTaskPermitted: checked === true,
                        },
                      });
                    }}
                  />
                  <Label htmlFor="mqtt-permitted">
                    Lock-task permitted only
                  </Label>
                </div>
              )}
              <details className="rounded-md border p-3">
                <summary className="cursor-pointer text-sm font-medium">
                  Advanced request options
                </summary>
                <div className="space-y-3 pt-4">
                  <div className="space-y-2">
                    <Label htmlFor="mqtt-request-message-id">Message ID</Label>
                    <Input
                      id="mqtt-request-message-id"
                      value={
                        typeof message.messageId === "string"
                          ? message.messageId
                          : ""
                      }
                      onChange={(event) =>
                        update(
                          changeTopLevelField(
                            message,
                            "messageId",
                            event.target.value || undefined,
                          ),
                        )
                      }
                    />
                  </div>
                  {(["targetInstances", "targetUsernames"] as const).map(
                    (key) => (
                      <DelimitedListInput
                        key={key}
                        id={`mqtt-${key}`}
                        label={
                          key === "targetInstances"
                            ? "Target instance IDs"
                            : "Target usernames"
                        }
                        values={
                          Array.isArray(message[key])
                            ? (message[key] as string[])
                            : []
                        }
                        onChange={(targets) =>
                          update(
                            changeTopLevelField(
                              message,
                              key,
                              targets.length ? targets : undefined,
                            ),
                          )
                        }
                      />
                    ),
                  )}
                </div>
              </details>
            </>
          )}
        </TabsContent>
        <TabsContent value="json" className="space-y-2 pt-4">
          <Label htmlFor="mqtt-request-json">Request JSON</Label>
          <Textarea
            id="mqtt-request-json"
            value={value}
            onChange={(event) => onChange(event.target.value)}
            rows={12}
            spellCheck={false}
            className="min-h-60 font-mono text-sm"
          />
        </TabsContent>
      </Tabs>
      {formError && (
        <p role="alert" className="text-sm text-destructive">
          {formError}
        </p>
      )}
      <details className="rounded-md border p-3">
        <summary className="cursor-pointer text-sm font-medium">
          MQTT 5 response metadata
        </summary>
        <div className="space-y-3 pt-4">
          <div className="space-y-2">
            <div className="flex items-center gap-1">
              <Label htmlFor="mqtt-response-topic">Response topic</Label>
              <InfoTooltip>
                Optional MQTT 5 response-topic property. Subscribe to this topic
                to receive the reply.
              </InfoTooltip>
            </div>
            <Input
              id="mqtt-response-topic"
              value={responseTopic}
              onChange={(event) => onResponseTopicChange(event.target.value)}
              placeholder="wk/debugger/reply"
            />
          </div>
          <div className="space-y-2">
            <Label htmlFor="mqtt-correlation">Correlation data</Label>
            <Input
              id="mqtt-correlation"
              value={correlationData}
              onChange={(event) => onCorrelationDataChange(event.target.value)}
            />
          </div>
        </div>
      </details>
    </div>
  );
}
