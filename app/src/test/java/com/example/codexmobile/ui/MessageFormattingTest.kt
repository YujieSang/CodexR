package com.example.codexmobile.ui

import com.example.codexmobile.api.ChatMessage
import org.junit.Assert.*
import org.junit.Test

class MessageFormattingTest {
    @Test fun `normalizes inline and display math`() {
        assertEquals("Value \$\$x^2\$\$", MathMarkdown.normalize("Value \$x^2\$"))
        assertEquals("Value \$\$x^2\$\$", MathMarkdown.normalize("Value \\(x^2\\)"))
        assertEquals("\n\$\$\nx^2\n\$\$\n", MathMarkdown.normalize("\\[x^2\\]"))
    }
    @Test fun `leaves code fences inline code double dollar and currency alone`() {
        val text = "```sh\necho \$PATH\n```\n`\$x\$` and \$\$x^2\$\$ costs \$5 and \$10."
        assertEquals(text, MathMarkdown.normalize(text))
    }
    @Test fun `edit truncates only from selected human prompt`() {
        val first = ChatMessage("user", "First")
        val second = ChatMessage("user", "Second")
        val messages = listOf(first, ChatMessage("assistant", "Answer"), second, ChatMessage("user", "Output", kind = "tool"))
        val edited = ConversationEdits.edit(messages, second.id, "Changed", emptyList())!!
        assertEquals(listOf("First", "Answer", "Changed"), edited.map { it.content })
        assertNull(ConversationEdits.edit(messages, messages.last().id, "Bad edit", emptyList()))
    }
}
