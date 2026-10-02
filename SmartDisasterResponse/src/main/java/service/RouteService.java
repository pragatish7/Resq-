package service;

import algorithms.*;
import model.*;

import java.util.*;

/**
 * Service for calculating evacuation routes.
 * Integrates Dijkstra, BFS, DFS, and MST with severity-aware cost.
 */
public class RouteService {

    private Graph graph;
    private DijkstraAlgorithm dijkstra;
    private BFS bfs;
    private DFS dfs;
    private KruskalMST kruskalMST;

    /** The route the user last planned — watched for automatic rerouting. */
    private Location activeFrom;
    private Location activeTo;
    private List<Location> activeRoute;

    public RouteService(Graph graph) {
        this.graph = graph;
        this.dijkstra = new DijkstraAlgorithm(graph);
        this.bfs = new BFS(graph);
        this.dfs = new DFS(graph);
        this.kruskalMST = new KruskalMST(graph);
    }

    public DijkstraAlgorithm.DijkstraResult findSafestRoute(Location source, Location destination) {
        return dijkstra.findShortestRoute(source, destination);
    }

    /** Distance-minimal route (km), still excluding blocked roads. */
    public DijkstraAlgorithm.DijkstraResult findShortestDistanceRoute(Location source, Location destination) {
        return dijkstra.findShortestDistanceRoute(source, destination);
    }

    /**
     * The directed road object between two adjacent locations, or null.
     * Blocked roads are included here — callers inspect the status themselves.
     */
    public Road roadBetween(Location from, Location to) {
        if (from == null || to == null) return null;
        for (Road road : graph.getRoads(from)) {
            if (road.getDestination().equals(to)) return road;
        }
        return null;
    }

    public DijkstraAlgorithm.DijkstraResult findNearestSafeZone(Location source, List<SafeZone> safeZones) {
        List<Location> available = new ArrayList<>();
        for (SafeZone zone : safeZones) {
            if (zone.isAvailable()) available.add(zone.getLocation());
        }
        return dijkstra.findNearestSafeZone(source, available);
    }

    public List<Location> runBFSTraversal(Location start) { return bfs.traverse(start); }
    public Map<Integer, List<Location>> runBFSByLevel(Location start) { return bfs.traverseByLevel(start); }
    public int findMinRoads(Location source, Location dest) { return bfs.minRoadsToDestination(source, dest); }
    public List<Location> runDFSTraversal(Location start) { return dfs.traverse(start); }
    public boolean checkConnectivity(Location a, Location b) { return dfs.isConnected(a, b); }
    public List<List<Location>> findConnectedComponents() { return dfs.findConnectedComponents(); }
    public KruskalMST.MSTResult generateMST() { return kruskalMST.computeMST(); }
    public DijkstraAlgorithm getDijkstra() { return dijkstra; }
    public BFS getBfs() { return bfs; }
    public DFS getDfs() { return dfs; }
    public KruskalMST getKruskalMST() { return kruskalMST; }

    /**
     * Assess the risk level of a computed route.
     * Returns "LOW", "MEDIUM", "HIGH", or "CRITICAL".
     */
    public String getRouteRiskLevel(List<Location> route) {
        if (route == null || route.size() < 2) return "UNKNOWN";
        int highRisk = 0, mediumRisk = 0, blocked = 0;
        for (int i = 0; i < route.size() - 1; i++) {
            for (Road road : graph.getRoads(route.get(i))) {
                if (road.getDestination().equals(route.get(i + 1))) {
                    switch (road.getStatus()) {
                        case BLOCKED: blocked++; break;
                        case HIGH_RISK: highRisk++; break;
                        case DAMAGED: mediumRisk++; break;
                    }
                    break;
                }
            }
        }
        if (blocked > 0) return "CRITICAL";
        if (highRisk > 0) return "HIGH";
        if (mediumRisk > 0) return "MEDIUM";
        return "LOW";
    }

    /**
     * Check if a route has any warnings (high-risk or damaged segments).
     */
    public List<String> getRouteWarnings(List<Location> route) {
        List<String> warnings = new ArrayList<>();
        if (route == null || route.size() < 2) return warnings;
        for (int i = 0; i < route.size() - 1; i++) {
            for (Road road : graph.getRoads(route.get(i))) {
                if (road.getDestination().equals(route.get(i + 1))) {
                    if (road.getStatus() == Road.Status.HIGH_RISK) {
                        warnings.add(route.get(i).getName() + " → " + route.get(i+1).getName() + " is HIGH RISK");
                    } else if (road.getStatus() == Road.Status.DAMAGED) {
                        warnings.add(route.get(i).getName() + " → " + route.get(i+1).getName() + " is DAMAGED");
                    }
                    break;
                }
            }
        }
        return warnings;
    }

    /**
     * Get the emergency alert level based on route risk.
     * Returns color name: GREEN, YELLOW, ORANGE, RED.
     */
    public String getAlertLevel(String riskLevel) {
        switch (riskLevel) {
            case "CRITICAL": return "RED";
            case "HIGH": return "ORANGE";
            case "MEDIUM": return "YELLOW";
            default: return "GREEN";
        }
    }

    // ==================================================================
    //  Phase 1 · active route watch (dynamic rerouting status)
    // ==================================================================

    /** Remember the route the user planned so road changes can be re-assessed against it. */
    public void rememberActiveRoute(Location from, Location to, List<Location> route) {
        this.activeFrom = from;
        this.activeTo = to;
        this.activeRoute = route == null ? null : new ArrayList<>(route);
    }

    public boolean hasActiveRoute() { return activeFrom != null && activeTo != null; }

    /** Forget the watched route (network reset / explicit clear). */
    public void clearActiveRoute() {
        activeFrom = null;
        activeTo = null;
        activeRoute = null;
    }

    public Location getActiveFrom() { return activeFrom; }
    public Location getActiveTo() { return activeTo; }

    /**
     * Re-run the watched route against the current network and report what
     * happened to it. Status values:
     * UNAFFECTED, ROUTE_AFFECTED, ALTERNATIVE_ROUTE_FOUND, NO_SAFE_ROUTE, NONE.
     */
    public RerouteReport reassessActiveRoute() {
        RerouteReport report = new RerouteReport();
        report.hadRoute = hasActiveRoute();
        if (!report.hadRoute) {
            report.status = "NONE";
            report.statusLabel = "No route planned";
            report.message = "Plan a route and it will be re-checked automatically when roads change.";
            return report;
        }

        report.from = activeFrom.getName();
        report.to = activeTo.getName();
        List<String> previousPath = new ArrayList<>();
        if (activeRoute != null) {
            for (Location loc : activeRoute) previousPath.add(loc.getName());
        }
        report.previousPath = previousPath;
        report.previousRisk = getRouteRiskLevel(activeRoute);

        // Segments of the previously planned route that are no longer usable.
        List<Map<String, String>> broken = new ArrayList<>();
        int blockedSegments = 0, degradedSegments = 0;
        boolean previousStillClear = activeRoute != null && activeRoute.size() >= 2;
        if (activeRoute != null && activeRoute.size() >= 2) {
            for (int i = 0; i < activeRoute.size() - 1; i++) {
                Road r = roadBetween(activeRoute.get(i), activeRoute.get(i + 1));
                if (r != null) report.previousDistanceKm += r.getDistance();
                Map<String, String> seg = new LinkedHashMap<>();
                seg.put("from", activeRoute.get(i).getName());
                seg.put("to", activeRoute.get(i + 1).getName());
                if (r == null || r.isBlocked()) {
                    previousStillClear = false;
                    blockedSegments++;
                    seg.put("status", r == null ? "MISSING" : r.getStatus().name());
                    broken.add(seg);
                } else if (r.getStatus() == Road.Status.HIGH_RISK || r.getStatus() == Road.Status.DAMAGED) {
                    degradedSegments++;
                    seg.put("status", r.getStatus().name());
                    broken.add(seg);
                }
            }
        } else {
            previousStillClear = false;
        }
        report.previousDistanceKm = round(report.previousDistanceKm);
        report.affectedSegments = broken;
        report.blockedSegments = blockedSegments;
        report.degradedSegments = degradedSegments;

        DijkstraAlgorithm.DijkstraResult fresh = findSafestRoute(activeFrom, activeTo);
        report.found = fresh.isRouteFound();
        if (fresh.isRouteFound()) {
            List<String> newPath = new ArrayList<>();
            for (Location loc : fresh.getRoute()) newPath.add(loc.getName());
            report.newPath = newPath;
            report.newDistanceKm = round(fresh.getTotalDistance());
            report.newCost = round(fresh.getTotalCost());
            report.newRisk = getRouteRiskLevel(fresh.getRoute());
            report.warnings = getRouteWarnings(fresh.getRoute());
            report.alertLevel = getAlertLevel(report.newRisk);
            report.routeChanged = !newPath.equals(previousPath);
        }

        if (!fresh.isRouteFound()) {
            report.status = "NO_SAFE_ROUTE";
            report.statusLabel = "No safe route available";
            report.message = "Every route from " + report.from + " to " + report.to
                    + " is blocked on the current network.";
        } else if (!previousStillClear) {
            report.status = "ALTERNATIVE_ROUTE_FOUND";
            report.statusLabel = "Alternative route found";
            report.message = blockedSegments + " road segment(s) on the planned route are "
                    + "blocked — a new route was computed around them.";
        } else if (report.routeChanged) {
            report.status = "ROUTE_AFFECTED";
            report.statusLabel = "Route affected";
            report.message = "The planned route is still open but the cheapest path has changed.";
        } else if (degradedSegments > 0) {
            report.status = "ROUTE_AFFECTED";
            report.statusLabel = "Route affected";
            report.message = "Still usable, but the route now crosses " + degradedSegments
                    + " degraded road segment(s) with a higher penalty.";
        } else {
            report.status = "UNAFFECTED";
            report.statusLabel = "Route unaffected";
            report.message = "The planned route is unchanged and still the cheapest path.";
        }
        return report;
    }

    /** What happened to the watched route after a network change. */
    public static class RerouteReport {
        public boolean hadRoute;
        public String status = "NONE";
        public String statusLabel = "No route planned";
        public String message = "";
        public String from, to;
        public String previousRisk;
        public List<String> previousPath = new ArrayList<>();
        public List<String> newPath = new ArrayList<>();
        public double newDistanceKm, newCost;
        public String newRisk;
        public String alertLevel;
        public boolean routeChanged;
        public boolean found;
        public double previousDistanceKm;
        public int blockedSegments, degradedSegments;
        public List<String> warnings = new ArrayList<>();
        public List<Map<String, String>> affectedSegments = new ArrayList<>();
    }

    // ==================================================================
    //  Phase 3 · shortest vs safest comparison + explanation
    // ==================================================================

    /** One candidate route, fully described from real road data. */
    public static class RouteOption {
        public String kind;              // "SHORTEST" | "SAFEST"
        public boolean found;
        public String message = "";
        public List<String> path = new ArrayList<>();
        public double distanceKm, cost, estMinutes;
        public String riskLevel = "UNKNOWN", alertLevel = "GREEN";
        public List<String> warnings = new ArrayList<>();
        public List<Map<String, Object>> legs = new ArrayList<>();
        public int roadCount;
        public int highRiskCount, damagedCount, blockedCount;
    }

    /** Side-by-side comparison plus a data-generated explanation and the cost model used. */
    public static class RouteComparison {
        public String from, to;
        public RouteOption shortest;
        public RouteOption safest;
        public boolean identical;
        public List<String> explanation = new ArrayList<>();
        public String formula = "";
        public List<Map<String, String>> formulaTerms = new ArrayList<>();
    }

    /**
     * Compare the distance-minimal route with the cost-minimal ("safest") route.
     * Every number in the returned explanation is read from the two real results.
     */
    public RouteComparison compareRoutes(Location from, Location to) {
        RouteComparison cmp = new RouteComparison();
        cmp.from = from.getName();
        cmp.to = to.getName();
        cmp.shortest = buildOption("SHORTEST", findShortestDistanceRoute(from, to));
        cmp.safest = buildOption("SAFEST", findSafestRoute(from, to));

        if (cmp.shortest.found && cmp.safest.found) {
            cmp.identical = cmp.shortest.path.equals(cmp.safest.path);
        }

        String risk = "RISK: LOW 0 · MEDIUM 5 · HIGH 10 · VERY_HIGH 20";
        cmp.formula = "cost = distanceKm + riskPenalty + disasterPenalty + trafficLevel × 0.3"
                + ", plus +5 when a road is DAMAGED and a second + riskPenalty when it is HIGH_RISK."
                + " BLOCKED roads return infinite cost, so Dijkstra can never use them.";
        cmp.formulaTerms.add(term("distance", "road length in km", "straight from the road record"));
        cmp.formulaTerms.add(term("risk penalty", "crosses risk level of the road", risk));
        cmp.formulaTerms.add(term("disaster penalty", "terrain × severity multiplier", "set by the active disaster, 0 when the network is clean"));
        cmp.formulaTerms.add(term("traffic", "trafficLevel × 0.3", "current traffic level on the road (0–5)"));
        cmp.formulaTerms.add(term("status", "DAMAGED +5 · HIGH_RISK + risk penalty again · BLOCKED excluded", "same status the map colours use"));

        List<String> ex = cmp.explanation;
        if (!cmp.safest.found && !cmp.shortest.found) {
            ex.add("No route exists between " + cmp.from + " and " + cmp.to + " on the current network — every path is blocked.");
        } else if (!cmp.shortest.found) {
            ex.add("No distance-minimal route exists; only the safest route is available.");
        } else if (!cmp.safest.found) {
            ex.add("No safest route found, but a distance-minimal route exists.");
        } else if (cmp.identical) {
            ex.add("Shortest and safest routes are identical — the cost-minimal path is also the distance-minimal one.");
            ex.add("Both run " + fmt(cmp.safest.distanceKm) + " km over " + cmp.safest.roadCount
                    + " road" + (cmp.safest.roadCount == 1 ? "" : "s") + " with " + cmp.safest.riskLevel + " risk.");
        } else {
            int avoidedHigh = cmp.shortest.highRiskCount - cmp.safest.highRiskCount;
            int avoidedDamaged = cmp.shortest.damagedCount - cmp.safest.damagedCount;
            double extraKm = cmp.safest.distanceKm - cmp.shortest.distanceKm;
            double costGap = cmp.shortest.cost - cmp.safest.cost;
            List<String> avoided = new ArrayList<>();
            if (avoidedHigh > 0) avoided.add(avoidedHigh + " high-risk road" + (avoidedHigh == 1 ? "" : "s"));
            if (avoidedDamaged > 0) avoided.add(avoidedDamaged + " damaged road" + (avoidedDamaged == 1 ? "" : "s"));
            if (!avoided.isEmpty()) {
                String head = "Safest route avoids " + String.join(" and ", avoided);
                if (extraKm > 0.05) {
                    ex.add(head + " but is " + fmt(extraKm) + " km longer.");
                } else if (extraKm < -0.05) {
                    ex.add(head + " and is " + fmt(-extraKm) + " km shorter.");
                } else {
                    ex.add(head + " at the same distance.");
                }
            } else {
                ex.add("Both routes cross the same road risk profile, so Dijkstra picks the one with the lower weighted cost.");
            }
            if (costGap > 0.05) {
                ex.add("Weighted cost: safest " + fmt(cmp.safest.cost) + " vs shortest " + fmt(cmp.shortest.cost)
                        + " — saving " + fmt(costGap) + " penalty points for " + fmt(Math.max(0, extraKm)) + " extra km.");
            } else if (costGap < -0.05) {
                ex.add("Weighted cost: safest " + fmt(cmp.safest.cost) + " vs shortest " + fmt(cmp.shortest.cost)
                        + " — the shortest route is also the cheaper one here.");
            } else {
                ex.add("Both routes carry the same weighted cost (" + fmt(cmp.safest.cost) + ").");
            }
            ex.add("Roads used: shortest " + cmp.shortest.roadCount + ", safest " + cmp.safest.roadCount
                    + ". Estimated travel time: shortest " + fmt(cmp.shortest.estMinutes) + " min, safest "
                    + fmt(cmp.safest.estMinutes) + " min.");
        }
        return cmp;
    }

    private RouteOption buildOption(String kind, DijkstraAlgorithm.DijkstraResult result) {
        RouteOption opt = new RouteOption();
        opt.kind = kind;
        opt.found = result.isRouteFound();
        opt.message = result.getMessage();
        if (!opt.found) return opt;

        List<Location> route = result.getRoute();
        opt.path = new ArrayList<>();
        for (Location loc : route) opt.path.add(loc.getName());
        opt.distanceKm = round(result.getTotalDistance());
        opt.cost = round(result.getTotalCost());
        opt.riskLevel = getRouteRiskLevel(route);
        opt.alertLevel = getAlertLevel(opt.riskLevel);
        opt.warnings = getRouteWarnings(route);
        opt.roadCount = Math.max(0, route.size() - 1);

        double minutes = 0;
        for (int i = 0; i < route.size() - 1; i++) {
            Road road = roadBetween(route.get(i), route.get(i + 1));
            if (road == null) continue;
            switch (road.getStatus()) {
                case HIGH_RISK: opt.highRiskCount++; break;
                case DAMAGED: opt.damagedCount++; break;
                case BLOCKED: opt.blockedCount++; break;
                default: break;
            }
            // Simulated travel speed by road condition; traffic adds a small delay.
            double speed = road.getStatus() == Road.Status.HIGH_RISK ? 15
                    : road.getStatus() == Road.Status.DAMAGED ? 18 : 28;
            double legMinutes = road.getDistance() / speed * 60 + road.getTrafficLevel() * 0.4;
            minutes += legMinutes;

            Map<String, Object> leg = new LinkedHashMap<>();
            leg.put("from", road.getSource().getName());
            leg.put("to", road.getDestination().getName());
            leg.put("distanceKm", round(road.getDistance()));
            leg.put("status", road.getStatus().name());
            leg.put("riskLevel", road.getRiskLevel().name());
            leg.put("terrain", road.getRoadTerrain().name());
            leg.put("trafficLevel", road.getTrafficLevel());
            leg.put("riskPenalty", road.getRiskLevel().getPenalty());
            leg.put("disasterPenalty", round(road.getDisasterPenalty()));
            leg.put("trafficPenalty", round(road.getTrafficLevel() * 0.3));
            leg.put("statusPenalty", road.getStatus() == Road.Status.DAMAGED ? 5
                    : road.getStatus() == Road.Status.HIGH_RISK ? road.getRiskLevel().getPenalty() : 0);
            leg.put("legCost", round(road.getCost()));
            leg.put("legMinutes", round(legMinutes));
            opt.legs.add(leg);
        }
        opt.estMinutes = round(minutes);
        return opt;
    }

    private Map<String, String> term(String name, String detail, String note) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("term", name);
        m.put("detail", detail);
        m.put("note", note);
        return m;
    }

    // ==================================================================
    //  Phase 2 · smart shelter recommendation
    // ==================================================================

    /** One ranked shelter suggestion with the real route that reaches it. */
    public static class ShelterRecommendation {
        public String name, location, safetyLevel, status, riskLevel = "UNKNOWN", alertLevel;
        public int capacity, occupied, available;
        public int occupancyPercent;
        public boolean reachable, fitsEvacuees, recommended, routeFound;
        public double distanceKm, cost, estMinutes, score;
        public int highRiskCount, damagedCount;
        public List<String> path = new ArrayList<>();
        public List<String> warnings = new ArrayList<>();
        public String reason = "";
    }

    /**
     * Rank every safe zone for an evacuation from {@code source} using the real
     * Dijkstra route to each one. Score = route cost + route risk + shelter
     * safety level + occupancy pressure + capacity shortfall; unreachable
     * shelters are ranked last. Full shelters stay listed but lose the
     * recommendation, so the next best shelter rolls up automatically.
     */
    public List<ShelterRecommendation> recommendShelters(Location source, List<SafeZone> zones, int evacuees) {
        int needed = Math.max(1, evacuees);
        List<ShelterRecommendation> list = new ArrayList<>();
        for (SafeZone zone : zones) {
            ShelterRecommendation rec = new ShelterRecommendation();
            rec.name = zone.getName();
            rec.location = zone.getLocation().getName();
            rec.safetyLevel = zone.getSafetyLevel().name();
            rec.capacity = zone.getCapacity();
            rec.occupied = zone.getCurrentOccupants();
            rec.available = zone.getAvailableCapacity();
            rec.occupancyPercent = (int) Math.round(zone.getOccupancyPercent());
            rec.status = zone.isAvailable() ? "AVAILABLE" : "FULL";

            DijkstraAlgorithm.DijkstraResult r = findSafestRoute(source, zone.getLocation());
            rec.routeFound = r.isRouteFound();
            rec.reachable = r.isRouteFound();
            if (r.isRouteFound()) {
                for (Location loc : r.getRoute()) rec.path.add(loc.getName());
                rec.distanceKm = round(r.getTotalDistance());
                rec.cost = round(r.getTotalCost());
                rec.riskLevel = getRouteRiskLevel(r.getRoute());
                rec.alertLevel = getAlertLevel(rec.riskLevel);
                rec.warnings = getRouteWarnings(r.getRoute());
                rec.estMinutes = estimateMinutes(r.getRoute());
                for (int i = 0; i < r.getRoute().size() - 1; i++) {
                    Road road = roadBetween(r.getRoute().get(i), r.getRoute().get(i + 1));
                    if (road == null) continue;
                    if (road.getStatus() == Road.Status.HIGH_RISK) rec.highRiskCount++;
                    else if (road.getStatus() == Road.Status.DAMAGED) rec.damagedCount++;
                }
            }
            rec.fitsEvacuees = rec.available >= needed;

            if (!rec.reachable) {
                rec.score = Double.MAX_VALUE;
                rec.reason = "Unreachable — every route is blocked on the current network";
            } else {
                double riskPenalty = "CRITICAL".equals(rec.riskLevel) ? 40
                        : "HIGH".equals(rec.riskLevel) ? 22 : "MEDIUM".equals(rec.riskLevel) ? 10 : 0;
                double safetyPenalty = "LOW".equals(rec.safetyLevel) ? 15 : "MEDIUM".equals(rec.safetyLevel) ? 6 : 0;
                double occupancyPenalty = rec.occupancyPercent * 0.12;   // spread the load
                double capacityPenalty = rec.fitsEvacuees ? 0 : 40 + (needed - rec.available) * 0.2;
                rec.score = round(rec.cost + riskPenalty + safetyPenalty + occupancyPenalty + capacityPenalty);
                StringBuilder why = new StringBuilder();
                why.append(rec.cost).append(" weighted cost · ").append(rec.riskLevel).append(" risk route");
                why.append(" · ").append(rec.available).append(" free of ").append(rec.capacity);
                why.append(" · ").append(rec.safetyLevel).append(" safety");
                if (!rec.fitsEvacuees) why.append(" · only fits ").append(rec.available).append(" of ").append(needed);
                rec.reason = why.toString();
            }
            list.add(rec);
        }

        // rank: reachable first, then by score, then by more free capacity
        list.sort((a, b) -> {
            if (a.reachable != b.reachable) return a.reachable ? -1 : 1;
            int byScore = Double.compare(a.score, b.score);
            if (byScore != 0) return byScore;
            return Integer.compare(b.available, a.available);
        });

        // mark the best shelter that can actually take the evacuees
        for (ShelterRecommendation rec : list) {
            if (rec.reachable && rec.fitsEvacuees) {
                rec.recommended = true;
                break;
            }
        }
        return list;
    }

    /** Simulated travel time for a route, from the same per-road speed model used in comparisons. */
    public double estimateMinutes(List<Location> route) {
        if (route == null || route.size() < 2) return 0;
        double minutes = 0;
        for (int i = 0; i < route.size() - 1; i++) {
            Road road = roadBetween(route.get(i), route.get(i + 1));
            if (road == null) continue;
            double speed = road.getStatus() == Road.Status.HIGH_RISK ? 15
                    : road.getStatus() == Road.Status.DAMAGED ? 18 : 28;
            minutes += road.getDistance() / speed * 60 + road.getTrafficLevel() * 0.4;
        }
        return round(minutes);
    }

    private double round(double v) { return Math.round(v * 10.0) / 10.0; }

    private static String fmt(double v) {
        if (v == Math.rint(v)) return String.valueOf((long) v);
        return String.valueOf(Math.round(v * 10.0) / 10.0);
    }
}
