package com.coverageproject.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class PatternTest {

    @Test
    void levelCountsDeterministicCells() {
        assertEquals(0, Pattern.of(null, null, null).level());
        assertEquals(1, Pattern.of("0", null, null).level());
        assertEquals(3, Pattern.of("0", "1", "0").level());
    }

    @Test
    void asStringRendersWildcardAsX() {
        assertEquals("0XX", Pattern.of("0", null, null).asString());
        assertEquals("XXX", Pattern.allX(3).asString());
        assertEquals("123", Pattern.of("1", "2", "3").asString());
    }

    @Test
    void equalityIsStructural() {
        assertEquals(Pattern.of("0", null, "1"), Pattern.of("0", null, "1"));
        assertEquals(Pattern.of("0", null, "1").hashCode(), Pattern.of("0", null, "1").hashCode());
        assertFalse(Pattern.of("0", null, "1").equals(Pattern.of("0", "0", "1")));
    }

    @Test
    void matchesRowChecksDeterministicCellsOnly() {
        assertTrue(Pattern.of("0", null, "1").matchesRow(List.of("0", "0", "1")));
        assertTrue(Pattern.of("0", null, "1").matchesRow(List.of("0", "1", "1")));
        assertFalse(Pattern.of("0", null, "1").matchesRow(List.of("1", "0", "1")));
        assertFalse(Pattern.of("0", null, "1").matchesRow(List.of("0", "0", "0")));
    }

    @Test
    void parentsReplaceOneDeterministicCellAtATime() {
        Set<String> got = Patterns.parents(Pattern.of("0", "1", "0")).stream()
                .map(Pattern::asString)
                .collect(Collectors.toSet());
        assertEquals(Set.of("X10", "0X0", "01X"), got);
    }

    @Test
    void rootHasNoParents() {
        assertTrue(Patterns.parents(Pattern.allX(3)).isEmpty());
    }

    @Test
    void dominanceMatchesPaperDefinition() {
        Pattern general = Pattern.of("0", null, null);
        Pattern specific = Pattern.of("0", "1", "0");
        assertTrue(Patterns.dominates(general, specific));
        assertFalse(Patterns.dominates(specific, general));
        assertFalse(Patterns.dominates(Pattern.of("1", null, null), specific));
    }

    @Test
    void allPatternsAtLevelEnumeratesEveryPattern() {
        List<List<String>> binary3 = List.of(List.of("0", "1"), List.of("0", "1"), List.of("0", "1"));
        // Number of level-l patterns over d binary attributes: C(d, l) * 2^l.
        assertEquals(1, Patterns.allPatternsAtLevel(binary3, 0).size()); // just XXX
        assertEquals(6, Patterns.allPatternsAtLevel(binary3, 1).size()); // C(3,1)*2
        assertEquals(12, Patterns.allPatternsAtLevel(binary3, 2).size()); // C(3,2)*4
        assertEquals(8, Patterns.allPatternsAtLevel(binary3, 3).size()); // C(3,3)*8
    }
}
