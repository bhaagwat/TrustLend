package com.trustlend.graph;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.Set;

/**
 * A generic, directed, weighted graph stored as adjacency maps (Unit 2: collections + generics).
 *
 * <pre>
 *   outgoing:  node → (neighbour → weight)     "who do I vouch for, and how strongly"
 *   incoming:  node → (neighbour → weight)     "who vouches for me"
 * </pre>
 *
 * Besides basic graph operations it implements:
 * <ul>
 *   <li>{@link #propagate} – personalised PageRank / EigenTrust style trust flow</li>
 *   <li>{@link #hopsFrom}  – breadth-first search (degrees of separation)</li>
 *   <li>{@link #strongestPath} – Dijkstra on −log(weight): the chain of vouches with the highest product</li>
 * </ul>
 *
 * @param <N> node type (we use vendor ids, {@code Long}); must have proper equals/hashCode
 */
public class TrustGraph<N> {

    private final Map<N, Map<N, Double>> outgoing = new HashMap<>();
    private final Map<N, Map<N, Double>> incoming = new HashMap<>();

    /** A read-only view of one edge. */
    public record Edge<N>(N from, N to, double weight) { }

    // ------------------------------------------------------------------ structure

    public void addNode(N node) {
        Objects.requireNonNull(node, "node");
        outgoing.putIfAbsent(node, new HashMap<>());
        incoming.putIfAbsent(node, new HashMap<>());
    }

    /** Adds or replaces an edge. Self-loops and non-positive weights are rejected. */
    public void addEdge(N from, N to, double weight) {
        if (Objects.equals(from, to)) {
            throw new IllegalArgumentException("Self-loops are not allowed in a trust graph");
        }
        if (weight <= 0 || Double.isNaN(weight)) {
            throw new IllegalArgumentException("Edge weight must be positive, got " + weight);
        }
        addNode(from);
        addNode(to);
        outgoing.get(from).put(to, weight);
        incoming.get(to).put(from, weight);
    }

    public boolean removeEdge(N from, N to) {
        Map<N, Double> out = outgoing.get(from);
        if (out == null || out.remove(to) == null) {
            return false;
        }
        incoming.get(to).remove(from);
        return true;
    }

    /** Removes a node and every edge touching it (explicit Iterator to remove while iterating). */
    public void removeNode(N node) {
        if (!outgoing.containsKey(node)) {
            return;
        }
        for (Iterator<Map.Entry<N, Map<N, Double>>> it = incoming.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<N, Map<N, Double>> entry = it.next();
            if (entry.getKey().equals(node)) {
                it.remove();
            } else {
                entry.getValue().remove(node);
            }
        }
        outgoing.remove(node);
        for (Map<N, Double> out : outgoing.values()) {
            out.remove(node);
        }
    }

    public boolean containsNode(N node) {
        return outgoing.containsKey(node);
    }

    public Set<N> nodes() {
        return Collections.unmodifiableSet(outgoing.keySet());
    }

    public int nodeCount() {
        return outgoing.size();
    }

    public int edgeCount() {
        int count = 0;
        for (Map<N, Double> out : outgoing.values()) {
            count += out.size();
        }
        return count;
    }

    public double weight(N from, N to) {
        return outgoing.getOrDefault(from, Map.of()).getOrDefault(to, 0.0);
    }

    public Map<N, Double> outNeighbours(N node) {
        return Collections.unmodifiableMap(outgoing.getOrDefault(node, Map.of()));
    }

    public Map<N, Double> inNeighbours(N node) {
        return Collections.unmodifiableMap(incoming.getOrDefault(node, Map.of()));
    }

    public double outWeight(N node) {
        double sum = 0;
        for (double w : outgoing.getOrDefault(node, Map.of()).values()) {
            sum += w;
        }
        return sum;
    }

    public List<Edge<N>> edges() {
        List<Edge<N>> list = new ArrayList<>();
        outgoing.forEach((from, out) -> out.forEach((to, w) -> list.add(new Edge<>(from, to, w))));
        return list;
    }

    // ------------------------------------------------------------------ algorithms

    /** Result of trust propagation. */
    public record PropagationResult<N>(Map<N, Double> trust, Map<N, Double> inflow, int iterations, boolean converged) { }

    /**
     * Personalised PageRank with weighted edges (the idea behind EigenTrust).
     *
     * <pre>
     *   t(v) = (1 − d)·p(v) + d·[ Σ_u t(u)·w(u,v)/W(u)  +  dangling·p(v) ]
     * </pre>
     *
     * where p is the prior ("local" trust from a vendor's own record), d the damping factor,
     * W(u) the total outgoing weight of u and "dangling" the trust held by nodes that vouch for nobody.
     * Trust therefore flows along vouches, and a vouch from a highly trusted vendor carries more.
     *
     * @param prior      non-negative prior per node (normalised internally)
     * @param damping    probability of following a vouch instead of teleporting to the prior (0..1)
     * @param maxIter    iteration cap
     * @param tolerance  stop when the L1 change between iterations is below this
     * @return stationary trust per node plus the inflow each node received from others in the final iteration
     */
    public PropagationResult<N> propagate(Map<N, Double> prior, double damping, int maxIter, double tolerance) {
        if (damping < 0 || damping >= 1) {
            throw new IllegalArgumentException("damping must be in [0, 1)");
        }
        Map<N, Double> p = normalise(prior);
        Map<N, Double> t = new HashMap<>(p);
        Map<N, Double> inflow = new HashMap<>();
        int iter = 0;
        boolean converged = false;

        while (iter < maxIter && !converged) {
            iter++;
            double dangling = 0;
            for (N n : outgoing.keySet()) {
                if (outgoing.get(n).isEmpty()) {
                    dangling += t.getOrDefault(n, 0.0);
                }
            }
            Map<N, Double> received = new HashMap<>();
            for (Map.Entry<N, Map<N, Double>> e : outgoing.entrySet()) {
                N from = e.getKey();
                double total = outWeight(from);
                if (total == 0) continue;
                double share = t.getOrDefault(from, 0.0);
                for (Map.Entry<N, Double> edge : e.getValue().entrySet()) {
                    received.merge(edge.getKey(), share * edge.getValue() / total, Double::sum);
                }
            }
            Map<N, Double> next = new HashMap<>();
            double delta = 0;
            for (N n : outgoing.keySet()) {
                double pn = p.getOrDefault(n, 0.0);
                double value = (1 - damping) * pn + damping * (received.getOrDefault(n, 0.0) + dangling * pn);
                next.put(n, value);
                delta += Math.abs(value - t.getOrDefault(n, 0.0));
            }
            t = next;
            inflow = received;
            converged = delta < tolerance;
        }
        return new PropagationResult<>(t, inflow, iter, converged);
    }

    private Map<N, Double> normalise(Map<N, Double> prior) {
        Map<N, Double> p = new HashMap<>();
        double sum = 0;
        for (N n : outgoing.keySet()) {
            double v = Math.max(0, prior.getOrDefault(n, 0.0));
            p.put(n, v);
            sum += v;
        }
        if (sum == 0) {
            double uniform = outgoing.isEmpty() ? 0 : 1.0 / outgoing.size();
            p.replaceAll((k, v) -> uniform);
        } else {
            final double s = sum;
            p.replaceAll((k, v) -> v / s);
        }
        return p;
    }

    /** Breadth-first search along outgoing edges: node → number of hops from {@code start}. */
    public Map<N, Integer> hopsFrom(N start) {
        Map<N, Integer> hops = new HashMap<>();
        if (!containsNode(start)) {
            return hops;
        }
        Queue<N> queue = new LinkedList<>();
        Set<N> visited = new HashSet<>();
        queue.add(start);
        visited.add(start);
        hops.put(start, 0);
        while (!queue.isEmpty()) {
            N current = queue.poll();
            for (N next : outgoing.get(current).keySet()) {
                if (visited.add(next)) {
                    hops.put(next, hops.get(current) + 1);
                    queue.add(next);
                }
            }
        }
        return hops;
    }

    /**
     * The chain of vouches from {@code from} to {@code to} whose weights have the largest product.
     * Maximising Π w  ⇔  minimising Σ −log(w), so this is Dijkstra's algorithm on −log weights.
     */
    public Optional<List<N>> strongestPath(N from, N to) {
        if (!containsNode(from) || !containsNode(to)) {
            return Optional.empty();
        }
        record Item<T>(T node, double cost) { }

        Map<N, Double> best = new HashMap<>();
        Map<N, N> previous = new HashMap<>();
        PriorityQueue<Item<N>> pq = new PriorityQueue<>((a, b) -> Double.compare(a.cost(), b.cost()));
        best.put(from, 0.0);
        pq.add(new Item<>(from, 0.0));

        while (!pq.isEmpty()) {
            Item<N> item = pq.poll();
            if (item.cost() > best.getOrDefault(item.node(), Double.MAX_VALUE)) continue;
            if (item.node().equals(to)) break;
            for (Map.Entry<N, Double> e : outgoing.get(item.node()).entrySet()) {
                double w = Math.min(1.0, e.getValue());
                double cost = item.cost() - Math.log(w);
                if (cost < best.getOrDefault(e.getKey(), Double.MAX_VALUE)) {
                    best.put(e.getKey(), cost);
                    previous.put(e.getKey(), item.node());
                    pq.add(new Item<>(e.getKey(), cost));
                }
            }
        }
        if (!best.containsKey(to)) {
            return Optional.empty();
        }
        LinkedList<N> path = new LinkedList<>();
        for (N at = to; at != null; at = previous.get(at)) {
            path.addFirst(at);
        }
        return Optional.of(path);
    }
}
