package org.bitwisemadness.tensuraquests;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * JUnit suite for the pure-logic side of the mod. The CI {@code junit} check
 * asserts a non-zero test count so it cannot pass vacuously; replace this
 * placeholder with real tests as the framework lands.
 */
class TensuraQuestsTest {

    @Test
    void modIdMatchesGradleProperty() {
        assertEquals("tensura_quests", TensuraQuests.MOD_ID);
    }
}
