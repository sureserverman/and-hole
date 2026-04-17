package org.pihole.android.core.filter.rules

sealed class FilterDecision {
    data object Allow : FilterDecision()
    data class Block(val reason: RuleKind) : FilterDecision()
    data object Pass : FilterDecision()
}
