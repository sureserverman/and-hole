package org.pihole.android.core.filter.trie

class SuffixTrie {
    private val root = Node()

    private class Node(
        val children: MutableMap<String, Node> = mutableMapOf(),
        var terminal: Boolean = false,
    )

    fun insertReversedLabels(reversedLabels: List<String>) {
        var n = root
        for (label in reversedLabels) {
            n = n.children.getOrPut(label) { Node() }
        }
        n.terminal = true
    }

    fun containsSuffix(reversedLabels: List<String>): Boolean {
        var n = root
        for (label in reversedLabels) {
            n = n.children[label] ?: return false
            if (n.terminal) return true
        }
        return n.terminal
    }
}
