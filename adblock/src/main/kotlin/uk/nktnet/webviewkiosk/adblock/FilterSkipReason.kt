package uk.nktnet.webviewkiosk.adblock

/** Inspection diagnostics only; no counters are updated on request matching. */
enum class FilterSkipReason(val label: String) {
    POPUP("Popup rules"),
    CSP("Content Security Policy rules"),
    HTML("HTML response filters"),
    PROCEDURAL("Unsupported cosmetic operations"),
    SCRIPTLET_SCOPE("Unsupported scriptlet scopes"),
    SCRIPTLET_NAME("Unimplemented scriptlets"),
    SCRIPTLET_ARGUMENTS("Unsupported scriptlet arguments or patterns"),
    SCRIPTLET_TRUST("Scriptlets requiring a trusted source"),
    NETWORK("Unsupported network syntax or options"),
    LIMIT("Rules exceeding safety limits"),
    OTHER("Other unsupported rules"),
    INVALID("Invalid rules"),
}
