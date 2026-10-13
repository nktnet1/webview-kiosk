"use client";

import { useEffect, useState } from "react";
import { Button } from "#/components/ui/button";
import { Checkbox } from "#/components/ui/checkbox";
import { Input } from "#/components/ui/input";
import { Label } from "#/components/ui/label";
import {
  Select,
  SelectContent,
  SelectGroup,
  SelectItem,
  SelectLabel,
  SelectTrigger,
  SelectValue,
} from "#/components/ui/select";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "#/components/ui/tabs";
import { Textarea } from "#/components/ui/textarea";
import {
  addDefaultSetting,
  assertEditablePayload,
  COMMANDS,
  changeCommand,
  changeDataField,
  changeSetting,
  changeTopLevelField,
  commandDefinition,
  createRemotePayload,
  isJsonObject,
  type JsonObject,
  type MessageKind,
  parseRemotePayload,
  type RemoteTransport,
  splitTargets,
  stringifyPayload,
} from "./payload";
import {
  formatSettingLabel,
  SETTINGS_BY_KEY,
  SETTINGS_CATALOG,
  type SettingDefinition,
} from "./settings-catalog";

type Props = {
  transport: RemoteTransport;
  kind: MessageKind;
  value: string;
  onChange: (value: string) => void;
  onKindChange: (kind: MessageKind) => void;
};

function ToggleField({
  id,
  label,
  checked,
  onChange,
}: {
  id: string;
  label: string;
  checked: boolean;
  onChange: (value: boolean) => void;
}) {
  return (
    <div className="flex items-center gap-3 py-1">
      <Checkbox
        id={id}
        checked={checked}
        onCheckedChange={(checked) => onChange(checked === true)}
      />
      <Label htmlFor={id}>{label}</Label>
    </div>
  );
}

function TargetField({
  id,
  label,
  targets,
  onChange,
}: {
  id: string;
  label: string;
  targets: string[];
  onChange: (values: string[]) => void;
}) {
  // Keep the uncommitted delimiter while typing multiple targets.
  const [draft, setDraft] = useState(targets.join(", "));
  const targetValues = targets.join("\n");
  useEffect(() => {
    if (splitTargets(draft).join("\n") !== targetValues) {
      setDraft(targets.join(", "));
    }
  }, [draft, targets, targetValues]);
  return (
    <div className="space-y-2">
      <Label htmlFor={id}>{label}</Label>
      <Input
        id={id}
        value={draft}
        placeholder="Comma-separated (optional)"
        spellCheck={false}
        onChange={(event) => {
          setDraft(event.target.value);
          onChange(splitTargets(event.target.value));
        }}
      />
    </div>
  );
}

function CommandControls({
  message,
  onChange,
}: {
  message: JsonObject;
  onChange: (message: JsonObject) => void;
}) {
  const commandName = message.command as string;
  const definition = commandDefinition(commandName);
  const data = isJsonObject(message.data) ? message.data : {};
  if (!definition) return null;

  return (
    <div className="space-y-4">
      <div className="space-y-2">
        <Label htmlFor="remote-command">Command</Label>
        <Select
          value={commandName}
          onValueChange={(value) => onChange(changeCommand(message, value))}
        >
          <SelectTrigger id="remote-command" className="w-full">
            <SelectValue placeholder="Choose a command" />
          </SelectTrigger>
          <SelectContent>
            {COMMANDS.map((command) => (
              <SelectItem key={command.name} value={command.name}>
                {command.label}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
        <p className="text-xs text-muted-foreground">
          {definition.description}
        </p>
      </div>
      {(definition.fields ?? []).map((field) => {
        const id = `remote-data-${field.key}`;
        const fieldValue = data[field.key];
        const setField = (value: unknown) =>
          onChange(changeDataField(message, field.key, value));
        if (field.kind === "boolean") {
          return (
            <ToggleField
              key={id}
              id={id}
              label={field.label}
              checked={fieldValue === true}
              onChange={setField}
            />
          );
        }
        if (field.kind === "choice") {
          return (
            <div className="space-y-2" key={id}>
              <Label htmlFor={id}>{field.label}</Label>
              <Select
                value={
                  typeof fieldValue === "string"
                    ? fieldValue
                    : (field.options?.[0] ?? "")
                }
                onValueChange={setField}
              >
                <SelectTrigger id={id} className="w-full">
                  <SelectValue placeholder="Choose an option" />
                </SelectTrigger>
                <SelectContent>
                  {field.options?.map((option) => (
                    <SelectItem key={option} value={option}>
                      {formatSettingLabel(option)}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
          );
        }
        if (field.kind === "integer") {
          return (
            <div className="space-y-2" key={id}>
              <Label htmlFor={id}>{field.label}</Label>
              <Input
                id={id}
                type="number"
                min={0}
                step={1}
                value={
                  typeof fieldValue === "number"
                  || typeof fieldValue === "string"
                    ? fieldValue
                    : ""
                }
                onChange={(event) =>
                  setField(
                    event.target.value === "" ? "" : Number(event.target.value),
                  )
                }
              />
            </div>
          );
        }
        return (
          <div className="space-y-2" key={id}>
            <Label htmlFor={id}>{field.label}</Label>
            {field.kind === "textarea" ? (
              <Textarea
                id={id}
                rows={3}
                value={typeof fieldValue === "string" ? fieldValue : ""}
                placeholder={field.placeholder}
                onChange={(event) => setField(event.target.value)}
              />
            ) : (
              <Input
                id={id}
                value={typeof fieldValue === "string" ? fieldValue : ""}
                placeholder={field.placeholder}
                required={field.required}
                onChange={(event) => setField(event.target.value)}
              />
            )}
          </div>
        );
      })}
    </div>
  );
}

function SettingControl({
  setting,
  value,
  onChange,
}: {
  setting: SettingDefinition;
  value: unknown;
  onChange: (value: unknown) => void;
}) {
  const id = `remote-setting-${setting.key.replaceAll(".", "-")}`;
  if (setting.kind === "boolean") {
    return (
      <ToggleField
        id={id}
        label="Enabled"
        checked={value === true}
        onChange={onChange}
      />
    );
  }
  if (setting.kind === "choice") {
    return (
      <Select
        value={typeof value === "string" ? value : ""}
        onValueChange={onChange}
      >
        <SelectTrigger
          id={id}
          aria-label={`${setting.key} value`}
          className="w-full"
        >
          <SelectValue placeholder="Choose a value" />
        </SelectTrigger>
        <SelectContent>
          {setting.options?.map((option) => (
            <SelectItem key={option} value={option}>
              {formatSettingLabel(option)}
            </SelectItem>
          ))}
        </SelectContent>
      </Select>
    );
  }
  if (setting.kind === "multi-select") {
    const current = Array.isArray(value) ? (value as string[]) : [];
    return (
      <div className="grid grid-cols-2 gap-2 sm:grid-cols-3">
        {setting.options?.map((option) => (
          <ToggleField
            key={option}
            id={`${id}-${option}`}
            label={formatSettingLabel(option)}
            checked={current.includes(option)}
            onChange={(checked) =>
              onChange(
                checked
                  ? [...current, option]
                  : current.filter((item) => item !== option),
              )
            }
          />
        ))}
      </div>
    );
  }
  return (
    <Input
      id={id}
      aria-label={`${setting.key} value`}
      type={setting.kind === "integer" ? "number" : "text"}
      step={setting.kind === "integer" ? 1 : undefined}
      value={
        typeof value === "string" || typeof value === "number" ? value : ""
      }
      onChange={(event) =>
        onChange(
          setting.kind === "integer" && event.target.value !== ""
            ? Number(event.target.value)
            : event.target.value,
        )
      }
    />
  );
}

function SettingsControls({
  message,
  onChange,
}: {
  message: JsonObject;
  onChange: (message: JsonObject) => void;
}) {
  const data = isJsonObject(message.data) ? message.data : {};
  const settings = isJsonObject(data.settings) ? data.settings : {};
  const [filter, setFilter] = useState("");
  const [newKey, setNewKey] = useState("");
  const available = SETTINGS_CATALOG.filter(
    (setting) =>
      !(setting.key in settings)
      && `${setting.key} ${setting.label}`
        .toLowerCase()
        .includes(filter.trim().toLowerCase()),
  );
  const groups = new Map<string, SettingDefinition[]>();
  for (const setting of available) {
    const group = groups.get(setting.group) ?? [];
    group.push(setting);
    groups.set(setting.group, group);
  }

  return (
    <div className="space-y-5">
      <div className="space-y-3">
        <p className="text-sm font-medium">Settings to update</p>
        {Object.entries(settings).length === 0 && (
          <p className="text-sm text-muted-foreground">
            No settings added yet.
          </p>
        )}
        {Object.entries(settings).map(([key, value]) => {
          const setting = SETTINGS_BY_KEY.get(key);
          return (
            <div
              key={key}
              className="rounded-md border bg-background p-3 space-y-3"
            >
              <div className="flex items-start justify-between gap-3">
                <div className="min-w-0">
                  <p className="text-sm font-medium">{setting?.label ?? key}</p>
                  <p className="text-xs text-muted-foreground break-all">
                    {key}
                  </p>
                </div>
                <Button
                  type="button"
                  variant="ghost"
                  size="sm"
                  aria-label={`Remove ${key}`}
                  onClick={() =>
                    onChange(changeSetting(message, key, undefined))
                  }
                >
                  Remove
                </Button>
              </div>
              {setting ? (
                <SettingControl
                  setting={setting}
                  value={value}
                  onChange={(next) =>
                    onChange(changeSetting(message, key, next))
                  }
                />
              ) : (
                <p className="text-xs text-muted-foreground">
                  Unrecognised key. Edit its value in the JSON tab.
                </p>
              )}
            </div>
          );
        })}
      </div>
      <div className="space-y-2 border-t pt-4">
        <Label htmlFor="remote-setting-filter">Find a setting</Label>
        <Input
          id="remote-setting-filter"
          value={filter}
          placeholder="Search by key or name"
          onChange={(event) => {
            setFilter(event.target.value);
            setNewKey("");
          }}
        />
        <Select value={newKey} onValueChange={setNewKey}>
          <SelectTrigger
            id="remote-setting-select"
            aria-label="Setting to add"
            className="w-full"
            disabled={available.length === 0}
          >
            <SelectValue
              placeholder={
                available.length ? "Choose a setting" : "No matching settings"
              }
            />
          </SelectTrigger>
          <SelectContent>
            {[...groups].map(([group, entries]) => (
              <SelectGroup key={group}>
                <SelectLabel>{group}</SelectLabel>
                {entries.map((setting) => (
                  <SelectItem key={setting.key} value={setting.key}>
                    {setting.label} ({setting.key})
                  </SelectItem>
                ))}
              </SelectGroup>
            ))}
          </SelectContent>
        </Select>
        <Button
          type="button"
          variant="outline"
          disabled={!newKey}
          onClick={() => {
            onChange(addDefaultSetting(message, newKey));
            setNewKey("");
          }}
        >
          Add setting
        </Button>
      </div>
    </div>
  );
}

function AdvancedControls({
  kind,
  message,
  onChange,
}: {
  kind: MessageKind;
  message: JsonObject;
  onChange: (message: JsonObject) => void;
}) {
  const setField = (key: string, value: unknown) =>
    onChange(changeTopLevelField(message, key, value));
  return (
    <details className="rounded-md border p-3">
      <summary className="cursor-pointer text-sm font-medium">
        Advanced message options
      </summary>
      <div className="space-y-4 pt-4">
        <div className="space-y-2">
          <Label htmlFor="remote-message-id">Message ID (optional)</Label>
          <Input
            id="remote-message-id"
            value={
              typeof message.messageId === "string" ? message.messageId : ""
            }
            onChange={(event) =>
              setField("messageId", event.target.value || undefined)
            }
          />
        </div>
        <TargetField
          id="remote-target-instances"
          label="Target instance IDs"
          targets={
            Array.isArray(message.targetInstances)
              ? (message.targetInstances as string[])
              : []
          }
          onChange={(values) =>
            setField("targetInstances", values.length ? values : undefined)
          }
        />
        <TargetField
          id="remote-target-users"
          label="Target usernames"
          targets={
            Array.isArray(message.targetUsernames)
              ? (message.targetUsernames as string[])
              : []
          }
          onChange={(values) =>
            setField("targetUsernames", values.length ? values : undefined)
          }
        />
        {kind === "command" ? (
          <>
            <ToggleField
              id="remote-interact"
              label="Trigger user interaction"
              checked={message.interact !== false}
              onChange={(value) => setField("interact", value)}
            />
            <ToggleField
              id="remote-wake-screen"
              label="Wake screen"
              checked={message.wakeScreen === true}
              onChange={(value) => setField("wakeScreen", value)}
            />
          </>
        ) : (
          <>
            <ToggleField
              id="remote-show-toast"
              label="Show confirmation toast"
              checked={message.showToast !== false}
              onChange={(value) => setField("showToast", value)}
            />
            <ToggleField
              id="remote-reload"
              label="Reload activity after update"
              checked={message.reloadActivity !== false}
              onChange={(value) => setField("reloadActivity", value)}
            />
          </>
        )}
      </div>
    </details>
  );
}

/** A controlled editor: the caller owns the JSON string for MQTT or UnifiedPush transport. */
export function RemoteMessageEditor({
  transport,
  kind,
  value,
  onChange,
  onKindChange,
}: Props) {
  const [tab, setTab] = useState("form");
  const [formError, setFormError] = useState<string | null>(null);
  let message: JsonObject | null = null;
  try {
    message = parseRemotePayload(value, transport, kind);
    if (kind === "command" && !commandDefinition(message.command as string)) {
      message = null;
    }
  } catch {
    // Invalid JSON stays editable in the JSON tab.
  }
  const formMessage = message;

  function updateMessage(next: JsonObject) {
    onChange(stringifyPayload(next));
    setFormError(null);
  }

  function updateRaw(next: string) {
    onChange(next);
    // UnifiedPush embeds the kind; MQTT uses the selected publish topic instead.
    if (transport === "unifiedpush") {
      try {
        const parsed: unknown = JSON.parse(next);
        if (
          isJsonObject(parsed)
          && (parsed.type === "command" || parsed.type === "settings")
        ) {
          onKindChange(parsed.type);
        }
      } catch {
        // Preserve invalid/incomplete text while the user edits it.
      }
    }
    setFormError(null);
  }

  function changeTab(next: string) {
    if (next === "form") {
      try {
        assertEditablePayload(value, transport, kind);
      } catch (error) {
        setFormError(
          error instanceof Error
            ? error.message
            : "Cannot edit this payload in the form.",
        );
        return;
      }
    }
    setFormError(null);
    setTab(next);
  }

  return (
    <div className="space-y-3">
      <Tabs value={tab} onValueChange={changeTab}>
        <TabsList>
          <TabsTrigger value="form">Form</TabsTrigger>
          <TabsTrigger value="json">JSON</TabsTrigger>
        </TabsList>
        <TabsContent value="form" className="pt-4 space-y-5">
          {formMessage && (
            <>
              <div className="space-y-2">
                <Label htmlFor="remote-message-kind">Message type</Label>
                <Select
                  value={kind}
                  onValueChange={(next) => {
                    const nextKind = next as MessageKind;
                    onKindChange(nextKind);
                    updateMessage(createRemotePayload(transport, nextKind));
                  }}
                >
                  <SelectTrigger id="remote-message-kind" className="w-full">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="command">Command</SelectItem>
                    <SelectItem value="settings">Settings update</SelectItem>
                  </SelectContent>
                </Select>
              </div>
              {kind === "command" ? (
                <CommandControls
                  message={formMessage}
                  onChange={updateMessage}
                />
              ) : (
                <SettingsControls
                  message={formMessage}
                  onChange={updateMessage}
                />
              )}
              <AdvancedControls
                kind={kind}
                message={formMessage}
                onChange={updateMessage}
              />
            </>
          )}
        </TabsContent>
        <TabsContent value="json" className="pt-4 space-y-2">
          <Label htmlFor="push-payload">JSON payload</Label>
          <Textarea
            id="push-payload"
            className="font-mono text-sm min-h-64"
            rows={13}
            spellCheck={false}
            value={value}
            onChange={(event) => updateRaw(event.target.value)}
          />
          <p className="text-xs text-muted-foreground">
            Edit the exact message body. Invalid JSON is kept as you type and
            cannot be sent.
          </p>
        </TabsContent>
      </Tabs>
      {formError && (
        <p role="alert" className="text-destructive text-sm">
          {formError}
        </p>
      )}
      <p className="text-xs text-muted-foreground">
        The same commands and settings are supported by MQTT and UnifiedPush.
        See the{" "}
        <a className="underline" href="/docs/settings/mqtt/topics/command">
          command reference
        </a>{" "}
        and{" "}
        <a className="underline" href="/docs/settings/mqtt/topics/settings">
          settings reference
        </a>
        .
      </p>
    </div>
  );
}
