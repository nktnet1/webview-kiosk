// Mirrors the portable wire format; integration tests also execute Kotlin exports.
const core = new Set(['SET', 'ABORT_READ', 'ABORT_WRITE', 'PREVENT_WINDOW_OPEN',
    'TRUSTED_PRUNE_INBOUND_OBJECT', 'TRUSTED_PRUNE_OUTBOUND_OBJECT',
    'PREVENT_ADD_EVENT_LISTENER', 'ABORT_CURRENT_SCRIPT', 'JSON_PRUNE', 'JSON_EDIT',
    'TRUSTED_JSON_EDIT',
    'PREVENT_FETCH', 'TRUSTED_PREVENT_FETCH', 'TRUSTED_SET']);
const network = new Set(['TRUSTED_JSON_EDIT_FETCH', 'TRUSTED_JSON_EDIT_XHR',
    'TRUSTED_JSONL_EDIT_FETCH', 'TRUSTED_JSONL_EDIT_XHR', 'JSON_EDIT_FETCH_REQUEST',
    'JSON_EDIT_XHR_REQUEST', 'TRUSTED_JSON_EDIT_FETCH_REQUEST', 'TRUSTED_JSON_EDIT_XHR_REQUEST',
    'PREVENT_XHR', 'TRUSTED_PREVENT_XHR', 'JSON_PRUNE_FETCH', 'JSON_PRUNE_XHR',
    'JSON_EDIT_FETCH', 'JSON_EDIT_XHR', 'JSONL_EDIT_FETCH', 'JSONL_EDIT_XHR', 'XML_PRUNE',
    'M3U_PRUNE', 'MPEGDASH_PRUNE', 'TRUSTED_REPLACE_FETCH', 'TRUSTED_REPLACE_XHR']);
const advanced = new Set(['TRUSTED_REPLACE_ARGUMENT', 'TRUSTED_REPLACE_OUTBOUND_TEXT',
    'TRUSTED_SUPPRESS_NATIVE_METHOD', 'TRUSTED_OVERRIDE_ELEMENT_METHOD',
    'TRUSTED_PREVENT_DOM_BYPASS', 'PREVENT_CLIPBOARD_WRITE', 'TRUSTED_REPLACE_NODE_TEXT',
    'TRUSTED_CLICK_ELEMENT', 'TRUSTED_CREATE_HTML']);

export function routingPayload(rules, suffixes) {
    const routes = { hosts: new Map(), entities: new Map(), ancestors: new Map(),
        ancestorEntities: new Map(), regex: [], exceptions: [], usesAncestors: false, limit: 1024 };
    const labels = new Set();
    let records = '';
    let instructions = '';
    const offsets = new Map();
    const add = (table, name, offset) => {
        if (!routes[table].has(name)) { routes[table].set(name, []); }
        routes[table].get(name).push(offset);
    };
    const target = (raw, excluded) => {
        if (raw.startsWith('/')) {
            const expr = raw.slice(1, -1), split = expr.indexOf('\\/');
            return [split < 0 ? expr : expr.slice(0, split) + '$',
                split < 0 ? null : '^' + expr.slice(split), true, false, false, excluded];
        }
        const ancestor = raw.endsWith('>>');
        if (ancestor) { raw = raw.slice(0, -2); }
        const slash = raw.indexOf('/');
        if (!ancestor && slash < 0) { return null; }
        let host = slash < 0 ? raw : raw.slice(0, slash);
        const entity = host.endsWith('.*');
        if (entity) { host = host.slice(0, -2); labels.add(host.split('.').at(-1)); }
        routes.usesAncestors ||= ancestor;
        return [host, slash < 0 ? null : raw.slice(slash), false, entity, ancestor, excluded];
    };
    for (const [type, args, domain = 'site.example', excluded = [], exception = false] of rules) {
        const instruction = JSON.stringify([type, args]);
        if (type !== null && !offsets.has(instruction)) {
            offsets.set(instruction, instructions.length);
            const object = type.includes('EDIT_') && type.includes('_OBJECT') &&
                !type.endsWith('EDIT_ELEMENT_OBJECT') || type === 'TRUSTED_EDIT_INBOUND_ELEMENT';
            const module = core.has(type) || object ? 0 : network.has(type) ? 2 :
                advanced.has(type) ? 3 : 1;
            instructions += JSON.stringify([type, args, module]) + '\n';
        }
        const offset = records.length;
        const targets = [], includedHosts = [], includedEntities = [], excludedHosts = [],
            excludedEntities = [];
        for (const [name, negative] of [[domain, false], ...excluded.map(name => [name, true])]) {
            if (name === '') { continue; }
            const value = target(name, negative);
            if (value) {
                targets.push(value);
                if (!negative) {
                    const [host, path, regex, entity, ancestor] = value;
                    if (regex) { routes.regex.push([host, path, offset]); }
                    else { add(ancestor ? entity ? 'ancestorEntities' : 'ancestors'
                        : entity ? 'entities' : 'hosts', host, offset); }
                }
            } else if (name.endsWith('.*')) {
                const entity = name.slice(0, -2);
                labels.add(entity.split('.').at(-1));
                (negative ? excludedEntities : includedEntities).push(entity);
                if (!negative) { add('entities', entity, offset); }
            } else {
                (negative ? excludedHosts : includedHosts).push(name);
                if (!negative) { add('hosts', name, offset); }
            }
        }
        if (!includedHosts.length && !includedEntities.length &&
            targets.every(value => value[5])) { routes.exceptions.push(offset); }
        records += JSON.stringify([type === null ? -1 : offsets.get(instruction), null,
            excludedHosts, exception, excludedEntities,
            targets, includedHosts, includedEntities]) + '\n';
    }
    for (const key of ['hosts', 'entities', 'ancestors', 'ancestorEntities']) {
        routes[key] = [...routes[key]].sort(([a], [b]) => a < b ? -1 : a > b ? 1 : 0)
            .map(([name, values]) => name + '\t' + JSON.stringify(values) + '\n').join('');
    }
    routes.regex = JSON.stringify(routes.regex);
    routes.entityLabels = [...labels].sort().map(label => label + '\n').join('');
    routes.suffixes = suffixes ? JSON.stringify(suffixes) : null;
    return [routes, records, instructions];
}
