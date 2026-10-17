// Mirrors the portable wire format; integration tests also execute Kotlin exports.
export function routingPayload(rules, suffixes) {
    const routes = { hosts: new Map(), entities: new Map(), ancestors: new Map(),
        ancestorEntities: new Map(), regex: [], exceptions: [], usesAncestors: false, limit: 1024 };
    const labels = new Set();
    let records = '';
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
        records += JSON.stringify([type, args, excludedHosts, exception, excludedEntities,
            targets, includedHosts, includedEntities]) + '\n';
    }
    for (const key of ['hosts', 'entities', 'ancestors', 'ancestorEntities']) {
        routes[key] = [...routes[key]].sort(([a], [b]) => a < b ? -1 : a > b ? 1 : 0)
            .map(([name, values]) => name + '\t' + JSON.stringify(values) + '\n').join('');
    }
    routes.regex = JSON.stringify(routes.regex);
    routes.entityLabels = [...labels].sort().map(label => label + '\n').join('');
    routes.suffixes = suffixes ? JSON.stringify(suffixes) : null;
    return [routes, records];
}
