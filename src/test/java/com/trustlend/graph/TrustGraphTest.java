package com.trustlend.graph;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrustGraphTest {

    private TrustGraph<String> g;

    @BeforeEach
    void setUp() {
        g = new TrustGraph<>();
    }

    @Test
    @DisplayName("Self-vouching and non-positive weights are rejected")
    void rejectsInvalidEdges() {
        assertThrows(IllegalArgumentException.class, () -> g.addEdge("a", "a", 1));
        assertThrows(IllegalArgumentException.class, () -> g.addEdge("a", "b", 0));
        assertThrows(IllegalArgumentException.class, () -> g.addEdge("a", "b", -0.5));
    }

    @Test
    @DisplayName("Removing a node removes every edge that touches it")
    void removeNodeCleansEdges() {
        g.addEdge("a", "b", 1);
        g.addEdge("b", "c", 1);
        g.addEdge("c", "a", 1);
        g.removeNode("b");
        assertEquals(2, g.nodeCount());
        assertEquals(1, g.edgeCount());
        assertTrue(g.inNeighbours("c").isEmpty());
        assertFalse(g.outNeighbours("a").containsKey("b"));
    }

    @Test
    @DisplayName("Propagated trust sums to 1 and converges")
    void propagationIsAProbabilityDistribution() {
        g.addEdge("a", "b", 1);
        g.addEdge("b", "c", 0.5);
        g.addEdge("c", "a", 0.8);
        g.addNode("d");
        TrustGraph.PropagationResult<String> r = g.propagate(Map.of("a", 1.0, "b", 1.0, "c", 1.0, "d", 1.0), 0.7, 200, 1e-9);
        double sum = r.trust().values().stream().mapToDouble(Double::doubleValue).sum();
        assertEquals(1.0, sum, 1e-6);
        assertTrue(r.converged());
    }

    @Test
    @DisplayName("A vouch from a trusted vendor is worth more than one from an untrusted vendor")
    void trustedVouchersPassOnMoreTrust() {
        g.addEdge("trusted", "x", 1.0);
        g.addEdge("unknown", "y", 1.0);
        Map<String, Double> prior = Map.of("trusted", 0.9, "unknown", 0.1, "x", 0.3, "y", 0.3);
        TrustGraph.PropagationResult<String> r = g.propagate(prior, 0.7, 100, 1e-9);
        assertTrue(r.inflow().get("x") > r.inflow().get("y"));
        assertTrue(r.trust().get("x") > r.trust().get("y"));
    }

    @Test
    @DisplayName("Strongest path maximises the product of weights, not the number of hops")
    void strongestPathPrefersStrongChains() {
        g.addEdge("a", "d", 0.2);            // direct but weak: 0.2
        g.addEdge("a", "b", 0.9);
        g.addEdge("b", "c", 0.9);
        g.addEdge("c", "d", 0.9);            // three hops: 0.729
        Optional<List<String>> path = g.strongestPath("a", "d");
        assertTrue(path.isPresent());
        assertEquals(List.of("a", "b", "c", "d"), path.get());
        assertTrue(g.strongestPath("d", "a").isEmpty());
    }

    @Test
    @DisplayName("BFS counts degrees of separation")
    void hopsFrom() {
        g.addEdge("a", "b", 1);
        g.addEdge("b", "c", 1);
        g.addEdge("a", "c", 1);
        g.addEdge("c", "d", 1);
        Map<String, Integer> hops = g.hopsFrom("a");
        assertEquals(0, hops.get("a"));
        assertEquals(1, hops.get("c"));
        assertEquals(2, hops.get("d"));
    }
}
