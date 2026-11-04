package com.trustlend.engine;

import com.trustlend.engine.factor.FactorResult;
import com.trustlend.engine.factor.RepaymentHistoryFactor;
import com.trustlend.engine.factor.ScoringFactor;
import com.trustlend.engine.factor.TenureFactor;
import com.trustlend.engine.factor.TransactionConsistencyFactor;
import com.trustlend.graph.TrustGraph;
import com.trustlend.model.ScoreBreakdown;
import com.trustlend.model.Vouch;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Computes trust scores for every vendor in two passes.
 *
 * <h3>Pass 1 – local trust (vendor's own record)</h3>
 * Each {@link ScoringFactor} (consistency, repayment, tenure) yields a value in [0, 1].
 * Their weighted mean is the vendor's <em>local trust</em>.
 *
 * <h3>Pass 2 – network trust (the weighted vouch graph)</h3>
 * <ol>
 *   <li>Edge u→v has weight = vouch strength (0.2..1.0) × credibility(u).
 *       Credibility drops for every vendor u vouched for who later defaulted
 *       ("skin in the game"), so careless vouching costs the voucher.</li>
 *   <li>Trust is propagated with personalised PageRank using local trust as the prior.</li>
 *   <li>A vendor's network score is the trust that <em>flows into</em> them from others,
 *       scaled against the 90th percentile so one celebrity node does not flatten everyone else.</li>
 * </ol>
 *
 * <h3>Final score</h3>
 * {@code score = min + (max − min) × Σ weight_i × factor_i}, i.e. 300..900.
 */
public class TrustScoreEngine {

    private static final Logger LOG = Logger.getLogger(TrustScoreEngine.class.getName());

    /** Vendors with a defaulted loan can never score at or above this (start of the Fair band). */
    public static final int DEFAULT_CAP = 550;

    private final EngineSettings settings;
    private final List<ScoringFactor> localFactors;

    public TrustScoreEngine(EngineSettings settings) {
        this.settings = settings;
        this.localFactors = List.of(
                new TransactionConsistencyFactor(settings.consistencyWindowWeeks()),
                new RepaymentHistoryFactor(),
                new TenureFactor());
    }

    public EngineSettings getSettings() {
        return settings;
    }

    /** Builds the weighted trust graph (vendor id → vendor id) with credibility-adjusted weights. */
    public TrustGraph<Long> buildGraph(Collection<VendorProfile> profiles, Collection<Vouch> vouches) {
        Set<Long> defaulters = new HashSet<>();
        TrustGraph<Long> graph = new TrustGraph<>();
        for (VendorProfile p : profiles) {
            graph.addNode(p.vendorId());
            if (p.hasDefaulted()) defaulters.add(p.vendorId());
        }
        Map<Long, Double> credibility = credibility(vouches, defaulters);
        for (Vouch v : vouches) {
            if (graph.containsNode(v.getVoucherId()) && graph.containsNode(v.getVoucheeId())) {
                double w = v.weight() * credibility.getOrDefault(v.getVoucherId(), 1.0);
                graph.addEdge(v.getVoucherId(), v.getVoucheeId(), w);
            }
        }
        return graph;
    }

    /** credibility(u) = max(0.4, 1 − penalty × number of defaulters u vouched for). */
    private Map<Long, Double> credibility(Collection<Vouch> vouches, Set<Long> defaulters) {
        Map<Long, Integer> badVouches = new HashMap<>();
        for (Vouch v : vouches) {
            if (defaulters.contains(v.getVoucheeId())) {
                badVouches.merge(v.getVoucherId(), 1, Integer::sum);
            }
        }
        Map<Long, Double> cred = new HashMap<>();
        badVouches.forEach((id, n) -> cred.put(id, Math.max(0.4, 1 - settings.voucherPenalty() * n)));
        return cred;
    }

    /** Scores every vendor as of {@code today}. */
    public Map<Long, ScoreBreakdown> computeAll(Collection<VendorProfile> profiles,
                                                Collection<Vouch> vouches,
                                                LocalDate today) {
        long start = System.nanoTime();

        // ---- pass 1: local factors
        Map<Long, Map<String, FactorResult>> results = new HashMap<>();
        Map<Long, Double> localTrust = new HashMap<>();
        double localWeightSum = settings.wConsistency() + settings.wRepayment() + settings.wTenure();
        for (VendorProfile p : profiles) {
            Map<String, FactorResult> r = new HashMap<>();
            double local = 0;
            for (ScoringFactor f : localFactors) {
                FactorResult fr = f.evaluate(p, today);
                r.put(f.key(), fr);
                local += settings.weightFor(f.key()) * fr.value();
            }
            results.put(p.vendorId(), r);
            localTrust.put(p.vendorId(), localWeightSum == 0 ? 0 : local / localWeightSum);
        }

        // ---- pass 2: propagate through the vouch graph
        TrustGraph<Long> graph = buildGraph(profiles, vouches);
        TrustGraph.PropagationResult<Long> prop = graph.propagate(
                localTrust, settings.damping(), settings.iterations(), settings.tolerance());
        Map<Long, Double> network = scaleInflow(prop.inflow());
        Set<Long> defaulters = new HashSet<>();
        for (VendorProfile p : profiles) {
            if (p.hasDefaulted()) defaulters.add(p.vendorId());
        }

        // ---- combine
        Map<Long, ScoreBreakdown> scores = new HashMap<>();
        for (VendorProfile p : profiles) {
            long id = p.vendorId();
            Map<String, FactorResult> r = results.get(id);
            double c = r.get("consistency").value();
            double rep = r.get("repayment").value();
            double ten = r.get("tenure").value();
            double net = network.getOrDefault(id, 0.0);

            double combined = settings.wConsistency() * c + settings.wRepayment() * rep
                    + settings.wTenure() * ten + settings.wNetwork() * net;
            int score = (int) Math.round(settings.minScore() + (settings.maxScore() - settings.minScore()) * combined);

            List<String> reasons = new ArrayList<>();
            // Hard rule used by real bureaus too: a recent default caps the score in the Poor band,
            // however good the rest of the profile (or the vouches) look.
            if (defaulters.contains(id) && score >= DEFAULT_CAP) {
                score = DEFAULT_CAP - 1;
                reasons.add("Score capped below " + DEFAULT_CAP + " because of a defaulted loan");
            }
            r.get("consistency").reasons().forEach(reasons::add);
            r.get("repayment").reasons().forEach(reasons::add);
            r.get("tenure").reasons().forEach(reasons::add);
            reasons.addAll(networkReasons(graph, id, net, defaulters));

            scores.put(id, new ScoreBreakdown(id, score, c, rep, ten, net, reasons, today));
        }
        LOG.fine(() -> String.format("Scored %d vendors, %d vouches, %d iterations (converged=%s) in %.1f ms",
                profiles.size(), graph.edgeCount(), prop.iterations(), prop.converged(),
                (System.nanoTime() - start) / 1e6));
        return scores;
    }

    /** Divides inflow by its 90th percentile (among vendors who receive any) and caps at 1. */
    static Map<Long, Double> scaleInflow(Map<Long, Double> inflow) {
        double[] positive = inflow.values().stream().mapToDouble(Double::doubleValue).filter(v -> v > 0).toArray();
        Map<Long, Double> scaled = new HashMap<>();
        if (positive.length == 0) {
            return scaled;
        }
        Arrays.sort(positive);
        double p90 = positive[(int) Math.floor(0.9 * (positive.length - 1))];
        if (p90 <= 0) p90 = positive[positive.length - 1];
        final double ref = p90;
        inflow.forEach((id, v) -> scaled.put(id, Math.min(1.0, v / ref)));
        return scaled;
    }

    private List<String> networkReasons(TrustGraph<Long> graph, long id, double net, Set<Long> defaulters) {
        List<String> reasons = new ArrayList<>();
        int received = graph.inNeighbours(id).size();
        int given = graph.outNeighbours(id).size();
        if (received == 0) {
            reasons.add("No one has vouched for this vendor yet");
        } else {
            reasons.add("Vouched for by " + received + " vendor(s); network trust " + Math.round(net * 100) + "%");
        }
        long badVouches = graph.outNeighbours(id).keySet().stream().filter(defaulters::contains).count();
        if (badVouches > 0) {
            reasons.add("Vouched for " + badVouches + " vendor(s) who defaulted — vouching credibility reduced");
        } else if (given > 0) {
            reasons.add("Vouches for " + given + " other vendor(s)");
        }
        return reasons;
    }
}
