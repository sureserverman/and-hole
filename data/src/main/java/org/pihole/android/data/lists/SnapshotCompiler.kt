package org.pihole.android.data.lists

import org.pihole.android.core.filter.rules.CompiledMatcher

object SnapshotCompiler {

    fun compile(domains: Collection<String>): CompiledMatcher =
        MatcherAssembler.fromSubscribedOnly(domains)
}
