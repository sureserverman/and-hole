package org.pihole.android.core.filter.rules

enum class RuleKind {
    EXACT_ALLOW,
    REGEX_ALLOW,
    EXACT_DENY,
    SUBSCRIBED_DENY,
    REGEX_DENY,
}
