package com.disaster.web;

import com.disaster.web.dto.*;
import algorithms.BFS;
import algorithms.DFS;
import algorithms.DijkstraAlgorithm;
import algorithms.KruskalMST;
import model.*;
import service.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * REST API over the existing disaster-response logic.
 * All algorithm/service classes are reused exactly as they were — this layer
 * only translates between JSON and the existing Java objects.
 * (Scanner/System.out patterns never existed in the service layer; the console
 * printing lived in the Swing entry point, which stays untouched.)
 */
@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api")
public class DisasterController {

    private final Graph graph;
    private final RouteService routeService;
    private final DisasterService disasterService;
    private final SafeZoneService safeZoneService;
    private final DijkstraAlgorithm dijkstra;
    private final BFS bfs;
    private final DFS dfs;
    private final KruskalMST kruskal;

    public DisasterController(Graph graph, RouteService routeService,
                              DisasterService disasterService, SafeZoneService safeZoneService) {
        this.graph = graph;
        this.routeService = routeService;
        this.disasterService = disasterService;
        this.safeZoneService = safeZoneService;
        this.dijkstra = routeService.getDijkstra();
        this.bfs = routeService.getBfs();
        this.dfs = routeService.getDfs();
        this.kruskal = routeService.getKruskalMST();
    }

    // ------------------------------------------------------------------
    //  Reference data
    // ------------------------------------------------------------------

    /**
     * All locations with coordinates + terrain (drives the interactive map).
     * Also carries a deterministic geographic projection (lat/lng) so real
     * basemaps can place the same locations: the graph's abstract x/y are
     * mapped into a compact region and offset per location id, so the projected
     * map is a faithful, stable layout of the REAL road graph.
     */
    @GetMapping("/locations")
    public Map<String, Object> locations() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (Location loc : graph.getAllLocations()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", loc.getId());
            m.put("name", loc.getName());
            m.put("x", loc.getX());
            m.put("y", loc.getY());
            m.put("terrain", loc.getTerrainType().name());
            double[] ll = project(loc);
            m.put("lat", ll[0]);
            m.put("lng", ll[1]);
            list.add(m);
        }
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("locations", list);
        resp.put("count", list.size());
        return resp;
    }

    /**
     * Deterministic geographic projection of the graph coordinates.
     * Base region: a mostly rural area near Bilaspur, Chhattisgarh, India.
     * The x/y plane is scaled into a compact box and offset per location id so
     * distinct locations never collapse onto one another.
     */
    private double[] project(Location loc) {
        double baseLat = 22.0790, baseLng = 82.1500;
        double lng = baseLng + loc.getX() * 0.00012 + (Math.floor(loc.getId().hashCode() % 7)) * 0.0004;
        double lat = baseLat + loc.getY() * 0.00009 + (Math.floor(loc.getId().hashCode() % 5)) * 0.0003;
        return new double[]{ Math.round(lat * 1e6) / 1e6, Math.round(lng * 1e6) / 1e6 };
    }

    /** All directed road entries (both directions of each undirected road). */
    @GetMapping("/roads")
    public Map<String, Object> roads() {
        List<RoadDto> list = new ArrayList<>();
        for (Location loc : graph.getAllLocations()) {
            for (Road road : graph.getRoads(loc)) {
                list.add(RoadDto.from(road));
            }
        }
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("roads", list);
        resp.put("count", list.size());
        return resp;
    }

    // ------------------------------------------------------------------
    //  Evacuation routing (Dijkstra)
    // ------------------------------------------------------------------

    /** Safest route between two locations using the real cost model. */
    @PostMapping("/route/safest")
    public Map<String, Object> safestRoute(@RequestBody RouteRequest req) {
        Location s = requireLocation(req.from());
        Location d = requireLocation(req.to());

        DijkstraAlgorithm.DijkstraResult r = routeService.findSafestRoute(s, d);
        // Watch this plan: every later road change is re-assessed against it.
        // Background lookups (watch=false) must not replace the user's route.
        if (req.watch() == null || req.watch()) routeService.rememberActiveRoute(s, d, r.getRoute());
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("found", r.isRouteFound());
        resp.put("message", r.getMessage());
        if (r.isRouteFound()) {
            List<String> path = new ArrayList<>();
            for (Location loc : r.getRoute()) path.add(loc.getName());
            resp.put("path", path);
            resp.put("totalDistanceKm", round(r.getTotalDistance()));
            resp.put("totalCost", round(r.getTotalCost()));
            resp.put("riskLevel", routeService.getRouteRiskLevel(r.getRoute()));
            resp.put("warnings", routeService.getRouteWarnings(r.getRoute()));
            resp.put("alertLevel", routeService.getAlertLevel(routeService.getRouteRiskLevel(r.getRoute())));
        }
        return resp;
    }

    /** Nearest available (not full) safe zone from a source location. */
    @PostMapping("/route/nearest-safe-zone")
    public Map<String, Object> nearestSafeZone(@RequestBody RouteRequest req) {
        Location s = requireLocation(req.from());
        DijkstraAlgorithm.DijkstraResult r = routeService.findNearestSafeZone(s, safeZoneService.getAllSafeZones());
        if (r.isRouteFound()) {
            Location target = r.getRoute().get(r.getRoute().size() - 1);
            routeService.rememberActiveRoute(s, target, r.getRoute());
        }
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("found", r.isRouteFound());
        resp.put("message", r.getMessage());
        if (r.isRouteFound()) {
            List<String> path = new ArrayList<>();
            for (Location loc : r.getRoute()) path.add(loc.getName());
            Location target = r.getRoute().get(r.getRoute().size() - 1);
            SafeZone zone = safeZoneService.findByLocation(target);
            resp.put("path", path);
            resp.put("totalDistanceKm", round(r.getTotalDistance()));
            resp.put("totalCost", round(r.getTotalCost()));
            resp.put("safeZone", zone != null ? zone.getName() : target.getName());
            resp.put("safeZoneCapacity", zone != null ? zone.getCapacity() : null);
            resp.put("safeZoneAvailable", zone != null ? zone.getAvailableCapacity() : null);
            resp.put("riskLevel", routeService.getRouteRiskLevel(r.getRoute()));
            resp.put("warnings", routeService.getRouteWarnings(r.getRoute()));
        }
        return resp;
    }

    // ------------------------------------------------------------------
    //  Route comparison (shortest vs safest) + explanation
    // ------------------------------------------------------------------

    /**
     * Shortest route vs safest route side by side, with an explanation built
     * from the two real results and the live cost formula.
     */
    @PostMapping("/route/compare")
    public Map<String, Object> compareRoutes(@RequestBody RouteRequest req) {
        Location s = requireLocation(req.from());
        Location d = requireLocation(req.to());
        RouteService.RouteComparison cmp = routeService.compareRoutes(s, d);

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("from", cmp.from);
        resp.put("to", cmp.to);
        resp.put("identical", cmp.identical);
        resp.put("shortest", optionToMap(cmp.shortest));
        resp.put("safest", optionToMap(cmp.safest));
        resp.put("explanation", cmp.explanation);
        resp.put("formula", cmp.formula);
        resp.put("formulaTerms", cmp.formulaTerms);
        return resp;
    }

    private Map<String, Object> optionToMap(RouteService.RouteOption opt) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("kind", opt.kind);
        m.put("found", opt.found);
        m.put("message", opt.message);
        if (!opt.found) return m;
        m.put("path", opt.path);
        m.put("distanceKm", opt.distanceKm);
        m.put("cost", opt.cost);
        m.put("costs", opt.legs);                   // per-road cost breakdown
        m.put("estMinutes", opt.estMinutes);
        m.put("roadCount", opt.roadCount);
        m.put("riskLevel", opt.riskLevel);
        m.put("alertLevel", opt.alertLevel);
        m.put("warnings", opt.warnings);
        m.put("highRiskCount", opt.highRiskCount);
        m.put("damagedCount", opt.damagedCount);
        m.put("blockedCount", opt.blockedCount);
        return m;
    }

    // ------------------------------------------------------------------
    //  Network reset (restore the original state)
    // ------------------------------------------------------------------

    /** Restore every road to OPEN and forget the watched route. */
    @PostMapping("/network/reset")
    public Map<String, Object> resetNetwork() {
        graph.resetRoadStatuses();
        disasterService.clearCurrentDisaster();
        routeService.clearActiveRoute();
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("reset", true);
        resp.put("message", "All " + graph.getRoadCount() + " roads restored to OPEN");
        resp.put("blocked", graph.getBlockedRoadCount());
        resp.put("highRisk", graph.getHighRiskRoadCount());
        resp.put("damaged", graph.getDamagedRoadCount());
        resp.put("reroute", rerouteMap());
        return resp;
    }

    /** The watched route re-checked against the current network (Phase 1 status). */
    private Map<String, Object> rerouteMap() {
        RouteService.RerouteReport r = routeService.reassessActiveRoute();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("hadRoute", r.hadRoute);
        m.put("status", r.status);
        m.put("statusLabel", r.statusLabel);
        m.put("message", r.message);
        m.put("from", r.from);
        m.put("to", r.to);
        m.put("found", r.found);
        m.put("routeChanged", r.routeChanged);
        m.put("previousPath", r.previousPath);
        m.put("previousRisk", r.previousRisk);
        m.put("previousDistanceKm", r.previousDistanceKm);
        m.put("blockedSegments", r.blockedSegments);
        m.put("degradedSegments", r.degradedSegments);
        m.put("newPath", r.newPath);
        m.put("newDistanceKm", r.newDistanceKm);
        m.put("newCost", r.newCost);
        m.put("newRisk", r.newRisk);
        m.put("alertLevel", r.alertLevel);
        m.put("warnings", r.warnings);
        m.put("affectedSegments", r.affectedSegments);
        return m;
    }

    // ------------------------------------------------------------------
    //  Graph traversal + network analytics
    // ------------------------------------------------------------------

    @PostMapping("/traversal/bfs")
    public Map<String, Object> bfs(@RequestBody Map<String, String> body) {
        Location start = requireLocation(body.get("start"));
        List<String> order = new ArrayList<>();
        for (Location loc : bfs.traverse(start)) order.add(loc.getName());
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("start", start.getName());
        resp.put("order", order);
        resp.put("visitedCount", order.size());
        return resp;
    }

    @PostMapping("/traversal/dfs")
    public Map<String, Object> dfs(@RequestBody Map<String, String> body) {
        Location start = requireLocation(body.get("start"));
        List<String> order = new ArrayList<>();
        for (Location loc : dfs.traverse(start)) order.add(loc.getName());
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("start", start.getName());
        resp.put("order", order);
        resp.put("visitedCount", order.size());
        return resp;
    }

    /** Minimum number of roads between two locations (edge count, ignores distance). */
    @PostMapping("/traversal/min-roads")
    public Map<String, Object> minRoads(@RequestBody RouteRequest req) {
        Location s = requireLocation(req.from());
        Location d = requireLocation(req.to());
        int min = bfs.minRoadsToDestination(s, d);
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("from", s.getName());
        resp.put("to", d.getName());
        resp.put("minRoads", min);
        resp.put("reachable", min >= 0);
        return resp;
    }

    @PostMapping("/traversal/connectivity")
    public Map<String, Object> connectivity(@RequestBody RouteRequest req) {
        Location a = requireLocation(req.from());
        Location b = requireLocation(req.to());
        boolean connected = dfs.isConnected(a, b);
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("from", a.getName());
        resp.put("to", b.getName());
        resp.put("connected", connected);
        return resp;
    }

    /** All connected components of the traversable road network. */
    @GetMapping("/network/components")
    public Map<String, Object> components() {
        List<List<String>> comps = new ArrayList<>();
        for (List<Location> comp : dfs.findConnectedComponents()) {
            List<String> names = new ArrayList<>();
            for (Location loc : comp) names.add(loc.getName());
            comps.add(names);
        }
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("components", comps);
        resp.put("count", comps.size());
        return resp;
    }

    /** Kruskal minimum spanning tree over road distances. */
    @GetMapping("/network/mst")
    public Map<String, Object> mst() {
        KruskalMST.MSTResult r = kruskal.computeMST();
        List<Map<String, Object>> edges = new ArrayList<>();
        for (KruskalMST.MSTEdge e : r.getEdges()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("from", e.getSource().getName());
            m.put("to", e.getDestination().getName());
            m.put("distanceKm", round(e.getDistance()));
            edges.add(m);
        }
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("edges", edges);
        resp.put("totalCostKm", round(r.getTotalCost()));
        resp.put("complete", r.isComplete());
        return resp;
    }

    // ------------------------------------------------------------------
    //  Safe zones (shelters)
    // ------------------------------------------------------------------

    @GetMapping("/safezones")
    public Map<String, Object> safeZones() {
        List<Map<String, Object>> list = new ArrayList<>();
        int totalCap = 0, totalOcc = 0;
        for (SafeZone z : safeZoneService.getAllSafeZones()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", z.getName());
            m.put("location", z.getLocation().getName());
            m.put("capacity", z.getCapacity());
            m.put("occupied", z.getCurrentOccupants());
            m.put("available", z.getAvailableCapacity());
            m.put("occupancyPercent", round(z.getOccupancyPercent()));
            m.put("safetyLevel", z.getSafetyLevel().name());
            m.put("status", z.isAvailable() ? "AVAILABLE" : "FULL");
            list.add(m);
            totalCap += z.getCapacity();
            totalOcc += z.getCurrentOccupants();
        }
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("safeZones", list);
        resp.put("totalCapacity", totalCap);
        resp.put("totalOccupied", totalOcc);
        resp.put("totalAvailable", totalCap - totalOcc);
        return resp;
    }

    /**
     * Check evacuees in to a shelter (real occupancy mutation).
     * A shelter can never be pushed past its capacity: a check-in that needs
     * more places than are free is refused, so the caller can roll over to the
     * next recommended shelter.
     */
    @PostMapping("/safezones/checkin")
    public Map<String, Object> checkIn(@RequestBody Map<String, Object> body) {
        String zoneName = String.valueOf(body.get("zone"));
        int count = parseInt(body.get("count"), 0);
        if (count <= 0) throw new BadRequestException("count must be a positive number");
        SafeZone target = safeZoneService.findByName(zoneName);
        if (target != null && count > target.getAvailableCapacity()) {
            throw new BadRequestException(target.getName() + " can only take "
                    + target.getAvailableCapacity() + " more evacuees (capacity "
                    + target.getCapacity() + ", occupied " + target.getCurrentOccupants() + ")");
        }
        boolean ok = safeZoneService.addOccupants(zoneName, count);
        SafeZone z = safeZoneService.findByName(zoneName);
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("success", ok);
        if (!ok) resp.put("error", z != null
                ? "Shelter " + zoneName + " is full or cannot accept " + count + " evacuees"
                : "Unknown shelter: " + zoneName);
        if (z != null) {
            resp.put("zone", z.getName());
            resp.put("occupied", z.getCurrentOccupants());
            resp.put("capacity", z.getCapacity());
            resp.put("available", z.getAvailableCapacity());
            resp.put("status", z.isAvailable() ? "AVAILABLE" : "FULL");
        }
        return resp;
    }

    /**
     * Phase 2 — smart shelter recommendation for an evacuation point.
     * Ranks every shelter by the real Dijkstra route to it: route cost and
     * simulated travel time, route risk, reachability (blocked roads) and the
     * shelter's own capacity, occupancy and safety level. Full or unreachable
     * shelters stay listed but lose the recommendation, so the next best
     * shelter rolls up automatically.
     */
    @PostMapping("/shelters/recommend")
    public Map<String, Object> recommendShelters(@RequestBody Map<String, Object> body) {
        Location from = requireLocation(String.valueOf(body.get("from")));
        int evacuees = Math.max(1, parseInt(body.getOrDefault("evacuees", 50), 50));
        List<RouteService.ShelterRecommendation> recs =
                routeService.recommendShelters(from, safeZoneService.getAllSafeZones(), evacuees);

        List<Map<String, Object>> list = new ArrayList<>();
        int reachable = 0, freeReachable = 0, rank = 0;
        String recommendedName = null;
        for (RouteService.ShelterRecommendation r : recs) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("rank", ++rank);
            m.put("name", r.name);
            m.put("location", r.location);
            m.put("safetyLevel", r.safetyLevel);
            m.put("status", r.status);
            m.put("capacity", r.capacity);
            m.put("occupied", r.occupied);
            m.put("available", r.available);
            m.put("occupancyPercent", r.occupancyPercent);
            m.put("reachable", r.reachable);
            m.put("fitsEvacuees", r.fitsEvacuees);
            m.put("recommended", r.recommended);
            m.put("score", r.reachable ? r.score : null);
            m.put("reason", r.reason);
            if (r.reachable) {
                m.put("path", r.path);
                m.put("distanceKm", r.distanceKm);
                m.put("cost", r.cost);
                m.put("estMinutes", r.estMinutes);
                m.put("riskLevel", r.riskLevel);
                m.put("alertLevel", r.alertLevel);
                m.put("warnings", r.warnings);
                m.put("highRiskCount", r.highRiskCount);
                m.put("damagedCount", r.damagedCount);
                reachable++;
                freeReachable += r.available;
            }
            if (r.recommended && recommendedName == null) recommendedName = r.name;
            list.add(m);
        }

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("from", from.getName());
        resp.put("evacuees", evacuees);
        resp.put("recommendations", list);
        resp.put("totalShelters", recs.size());
        resp.put("reachableCount", reachable);
        resp.put("freePlacesReachable", freeReachable);
        resp.put("recommendedName", recommendedName);
        return resp;
    }

    // ------------------------------------------------------------------
    //  Disaster simulation
    // ------------------------------------------------------------------

    /** Apply a disaster at a severity to the whole road network. */
    @PostMapping("/disaster/apply")
    public Map<String, Object> applyDisaster(@RequestBody Map<String, String> body) {
        String typeStr = body.get("type");
        String sevStr = body.getOrDefault("severity", "MEDIUM");
        Disaster.Type type = parseEnum(Disaster.Type.class, typeStr, "disaster type");
        Disaster.Severity severity = parseEnum(Disaster.Severity.class, sevStr, "severity");

        Disaster d = disasterService.applyDisaster(type, severity);

        int blocked = 0, highRisk = 0, damaged = 0;
        for (Road road : d.getAffectedRoads()) {
            switch (road.getStatus()) {
                case BLOCKED: blocked++; break;
                case HIGH_RISK: highRisk++; break;
                case DAMAGED: damaged++; break;
                default: break;
            }
        }
        List<Map<String, Object>> affected = affectedRoadsList();

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("type", d.getType().name());
        resp.put("typeDisplay", d.getType().getDisplayName());
        resp.put("severity", d.getSeverity().name());
        resp.put("description", d.getDescription());
        resp.put("recommendedAction", d.getRecommendedAction());
        resp.put("roadsAffected", affected.size());
        resp.put("blocked", blocked);
        resp.put("highRisk", highRisk);
        resp.put("damaged", damaged);
        resp.put("affectedRoads", affected);
        // Phase 1: how the change hit the route the user last planned.
        resp.put("reroute", rerouteMap());
        return resp;
    }

    /**
     * The incident layer's data source: the active disaster plus every road that
     * carries a disaster penalty. One entry per undirected road, so the map
     * never has to guess where to put a blockage or incident symbol.
     */
    @GetMapping("/network/incidents")
    public Map<String, Object> incidents() {
        Map<String, Object> resp = new LinkedHashMap<>();
        Disaster current = disasterService.getCurrentDisaster();
        if (current == null) {
            resp.put("disaster", null);
        } else {
            Map<String, Object> d = new LinkedHashMap<>();
            d.put("type", current.getType().name());
            d.put("typeDisplay", current.getType().getDisplayName());
            d.put("severity", current.getSeverity().name());
            d.put("description", current.getDescription());
            d.put("recommendedAction", current.getRecommendedAction());
            resp.put("disaster", d);
        }
        resp.put("affectedRoads", affectedRoadsList());
        resp.put("blocked", graph.getBlockedRoadCount());
        resp.put("highRisk", graph.getHighRiskRoadCount());
        resp.put("damaged", graph.getDamagedRoadCount());
        resp.put("reroute", rerouteMap());
        return resp;
    }

    /** Every undirected road carrying a disaster penalty, with its live status. */
    private List<Map<String, Object>> affectedRoadsList() {
        List<Map<String, Object>> affected = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Location loc : graph.getAllLocations()) {
            for (Road road : graph.getRoads(loc)) {
                if (road.getDisasterPenalty() <= 0) continue;
                Location a = road.getSource(), b = road.getDestination();
                String key = a.getId().compareTo(b.getId()) < 0 ? a.getId() + "|" + b.getId() : b.getId() + "|" + a.getId();
                if (!seen.add(key)) continue;
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("from", a.getName());
                m.put("to", b.getName());
                m.put("status", road.getStatus().name());
                m.put("penalty", round(road.getDisasterPenalty()));
                m.put("distanceKm", round(road.getDistance()));
                affected.add(m);
            }
        }
        return affected;
    }

    /** Clear the current disaster and restore every road. */
    @PostMapping("/disaster/reset")
    public Map<String, Object> resetDisaster() {
        graph.resetRoadStatuses();
        disasterService.clearCurrentDisaster();
        routeService.clearActiveRoute();
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("reset", true);
        resp.put("message", "All roads restored to OPEN");
        resp.put("reroute", rerouteMap());
        return resp;
    }

    /** Manually set a road's status (a road is undirected, so both directions change). */
    @PostMapping("/roads/status")
    public Map<String, Object> setRoadStatus(@RequestBody RoadUpdateRequest req) {
        Location s = requireLocation(req.from());
        Location d = requireLocation(req.to());
        Road.Status status = parseEnum(Road.Status.class, req.status(), "road status");
        boolean ok = disasterService.updateRoadStatus(s, d, status);
        if (!ok) throw new BadRequestException("No directed road from " + s.getName() + " to " + d.getName());
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("updated", true);
        resp.put("from", s.getName());
        resp.put("to", d.getName());
        resp.put("status", status.name());
        resp.put("reroute", rerouteMap());
        return resp;
    }

    /** Current status of every road — the road table's data source. */
    @GetMapping("/roads/status")
    public Map<String, Object> roadStatuses() {
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("roads", currentRoads());
        resp.put("blocked", graph.getBlockedRoadCount());
        resp.put("highRisk", graph.getHighRiskRoadCount());
        resp.put("damaged", graph.getDamagedRoadCount());
        return resp;
    }

    // ------------------------------------------------------------------
    //  Phase 4 · disaster scenario comparison
    // ------------------------------------------------------------------

    /**
     * Compare candidate scenarios side by side. Each scenario runs the Phase 1
     * road changes on a COPY of the network state (snapshot → mutate → measure
     * → restore), so the live network is never modified here.
     */
    @PostMapping("/scenarios/compare")
    public Map<String, Object> compareScenarios(@RequestBody Map<String, Object> body) {
        Location from = requireLocation(String.valueOf(body.get("from")));
        String toName = body.get("to") == null ? null : String.valueOf(body.get("to"));
        Location to = (toName == null || toName.isBlank() || "NONE".equalsIgnoreCase(toName))
                ? null : requireLocation(toName);
        int evacuees = Math.max(1, parseInt(body.getOrDefault("evacuees", 50), 50));
        List<Map<String, Object>> specs = readScenarios(body.get("scenarios"));
        if (specs.isEmpty()) throw new BadRequestException("At least one scenario is required");

        Map<String, Object> baseline = scenarioMetrics(from, to, evacuees);
        List<Map<String, Object>> results = new ArrayList<>();
        for (Map<String, Object> spec : specs) {
            results.add(runScenario(spec, from, to, evacuees));
        }

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("from", from.getName());
        resp.put("to", to == null ? null : to.getName());
        resp.put("evacuees", evacuees);
        resp.put("baseline", baseline);
        resp.put("scenarios", results);
        resp.put("summary", scenarioSummary(results, baseline));
        resp.put("applied", false);
        return resp;
    }

    /** Apply one scenario to the live network using the same Phase 1 logic. */
    @PostMapping("/scenarios/apply")
    public Map<String, Object> applyScenario(@RequestBody Map<String, Object> body) {
        String typeStr = body.get("type") == null ? "" : String.valueOf(body.get("type"));
        String label = body.get("label") == null ? "Scenario" : String.valueOf(body.get("label"));
        List<Map<String, String>> blocks = readBlocks(body.get("blocks"));

        Disaster d = null;
        if (!typeStr.isBlank() && !"NONE".equalsIgnoreCase(typeStr)) {
            Disaster.Type type = parseEnum(Disaster.Type.class, typeStr, "disaster type");
            String sevStr = body.get("severity") == null ? "MEDIUM" : String.valueOf(body.get("severity"));
            Disaster.Severity severity = parseEnum(Disaster.Severity.class, sevStr, "severity");
            d = disasterService.applyDisaster(type, severity);
        }
        int manualBlocks = 0;
        for (Map<String, String> block : blocks) {
            Location bs = requireLocation(block.get("from"));
            Location bd = requireLocation(block.get("to"));
            if (disasterService.updateRoadStatus(bs, bd, Road.Status.BLOCKED)) manualBlocks++;
        }

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("applied", true);
        resp.put("label", label);
        resp.put("type", d == null ? null : d.getType().name());
        resp.put("typeDisplay", d == null ? "Manual road blocks" : d.getType().getDisplayName());
        resp.put("severity", d == null ? null : d.getSeverity().name());
        resp.put("description", d == null
                ? manualBlocks + " road(s) closed manually — the rest of the network is unchanged"
                : d.getDescription());
        resp.put("recommendedAction", d == null
                ? "Avoid the closed roads; routing already excludes them."
                : d.getRecommendedAction());
        resp.put("manualBlocks", manualBlocks);
        resp.put("blocked", graph.getBlockedRoadCount());
        resp.put("highRisk", graph.getHighRiskRoadCount());
        resp.put("damaged", graph.getDamagedRoadCount());
        resp.put("affectedRoads", affectedRoadsList());
        resp.put("reroute", rerouteMap());
        return resp;
    }

    /**
     * Run one scenario on a snapshot copy and return its metrics.
     * Every scenario starts from the clean sample network and then applies its
     * own Phase 1 changes, so the rows are directly comparable even when one of
     * them is already live; the restore in the finally block leaves the real
     * network untouched.
     */
    private Map<String, Object> runScenario(Map<String, Object> spec, Location from, Location to, int evacuees) {
        String label = spec.get("label") == null ? "Scenario" : String.valueOf(spec.get("label"));
        String typeStr = spec.get("type") == null ? "" : String.valueOf(spec.get("type"));
        String sevStr = spec.get("severity") == null ? "MEDIUM" : String.valueOf(spec.get("severity"));
        List<Map<String, String>> blocks = readBlocks(spec.get("blocks"));

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("label", label);
        row.put("type", typeStr.isBlank() || "NONE".equalsIgnoreCase(typeStr) ? null : typeStr);
        row.put("severity", sevStr);
        List<String> blockNames = new ArrayList<>();
        for (Map<String, String> b : blocks) blockNames.add(b.get("from") + " \u2192 " + b.get("to"));
        row.put("blockedRoadNames", blockNames);
        row.put("applied", false);

        DisasterService.NetworkState snapshot = disasterService.captureNetworkState();
        try {
            graph.resetRoadStatuses();
            disasterService.clearCurrentDisaster();
            if (!typeStr.isBlank() && !"NONE".equalsIgnoreCase(typeStr)) {
                Disaster.Type type = parseEnum(Disaster.Type.class, typeStr, "disaster type");
                Disaster.Severity severity = parseEnum(Disaster.Severity.class, sevStr, "severity");
                disasterService.applyDisaster(type, severity);
            }
            int manualBlocks = 0;
            for (Map<String, String> b : blocks) {
                Location bs = requireLocation(b.get("from"));
                Location bd = requireLocation(b.get("to"));
                if (disasterService.updateRoadStatus(bs, bd, Road.Status.BLOCKED)) manualBlocks++;
            }
            row.put("manualBlocks", manualBlocks);
            row.putAll(scenarioMetrics(from, to, evacuees));
        } finally {
            disasterService.restoreNetworkState(snapshot);
        }
        return row;
    }

    /** Measure the live network exactly as it stands (used per scenario + as baseline). */
    private Map<String, Object> scenarioMetrics(Location from, Location to, int evacuees) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("blocked", graph.getBlockedRoadCount());
        m.put("highRisk", graph.getHighRiskRoadCount());
        m.put("damaged", graph.getDamagedRoadCount());

        int affected = 0;
        Set<String> seen = new HashSet<>();
        for (Location loc : graph.getAllLocations()) {
            for (Road road : graph.getRoads(loc)) {
                if (road.getDisasterPenalty() <= 0) continue;
                Location a = road.getSource(), b = road.getDestination();
                String key = a.getId().compareTo(b.getId()) < 0 ? a.getId() + "|" + b.getId() : b.getId() + "|" + a.getId();
                if (seen.add(key)) affected++;
            }
        }
        m.put("affectedRoads", affected);

        // Areas cut off: locations no longer reachable from the evacuation origin (DFS).
        List<String> isolated = new ArrayList<>();
        for (Location loc : graph.getAllLocations()) {
            if (!loc.equals(from) && !routeService.checkConnectivity(from, loc)) isolated.add(loc.getName());
        }
        m.put("isolatedCount", isolated.size());
        m.put("isolatedLocations", isolated);
        // Road-level detail so a scenario can be previewed on the map without
        // ever touching the live network (the caller restores the snapshot).
        m.put("affectedRoads", affectedRoadsList());

        Map<String, Object> route = new LinkedHashMap<>();
        if (to != null) {
            DijkstraAlgorithm.DijkstraResult r = routeService.findSafestRoute(from, to);
            route.put("found", r.isRouteFound());
            if (r.isRouteFound()) {
                List<String> path = new ArrayList<>();
                for (Location loc : r.getRoute()) path.add(loc.getName());
                route.put("path", path);
                route.put("distanceKm", round(r.getTotalDistance()));
                route.put("cost", round(r.getTotalCost()));
                route.put("estMinutes", routeService.estimateMinutes(r.getRoute()));
                route.put("riskLevel", routeService.getRouteRiskLevel(r.getRoute()));
                route.put("roadCount", Math.max(0, r.getRoute().size() - 1));
            } else {
                route.put("message", r.getMessage());
            }
        }
        m.put("route", route);

        // Shelters that can still be reached in this scenario (Phase 2 logic).
        int reachableShelters = 0, freeReachable = 0;
        String bestShelter = null, bestRisk = null;
        double bestMinutes = 0;
        int bestFree = 0;
        for (RouteService.ShelterRecommendation rec : routeService.recommendShelters(
                from, safeZoneService.getAllSafeZones(), evacuees)) {
            if (!rec.reachable) continue;
            reachableShelters++;
            freeReachable += rec.available;
            if (bestShelter == null) {
                bestShelter = rec.name;
                bestRisk = rec.riskLevel;
                bestMinutes = rec.estMinutes;
                bestFree = rec.available;
            }
        }
        m.put("reachableShelters", reachableShelters);
        m.put("freePlacesReachable", freeReachable);
        m.put("bestShelter", bestShelter);
        m.put("bestShelterRisk", bestRisk);
        m.put("bestShelterMinutes", bestMinutes);
        m.put("bestShelterFree", bestFree);
        return m;
    }

    /** Small, data-derived summary of the comparison (worst case, best case, shelter gaps). */
    private List<String> scenarioSummary(List<Map<String, Object>> results, Map<String, Object> baseline) {
        List<String> lines = new ArrayList<>();
        if (results.isEmpty()) return lines;

        Map<String, Object> worst = results.get(0);
        for (Map<String, Object> r : results) {
            if (intOf(r.get("blocked")) > intOf(worst.get("blocked"))) worst = r;
        }
        double baseKm = routeKm(baseline);
        double worstKm = routeKm(worst);
        StringBuilder sb = new StringBuilder();
        sb.append("Most disruptive: ").append(worst.get("label"))
          .append(" — ").append(worst.get("blocked")).append(" road(s) blocked, ")
          .append(worst.get("isolatedCount")).append(" location(s) cut off");
        if (worstKm > 0 && baseKm > 0 && Math.abs(worstKm - baseKm) > 0.05) {
            sb.append(", evacuation route ").append(fmt(worstKm)).append(" km vs ").append(fmt(baseKm)).append(" km now");
        } else if (worstKm > 0) {
            sb.append(", evacuation route unchanged at ").append(fmt(baseKm)).append(" km");
        }
        sb.append(".");
        lines.add(sb.toString());

        for (Map<String, Object> r : results) {
            if (intOf(r.get("reachableShelters")) == 0) {
                lines.add("No shelter can be reached from the evacuation point in " + r.get("label") + ".");
            } else if (intOf(r.get("reachableShelters")) < intOf(baseline.get("reachableShelters"))) {
                lines.add(r.get("label") + " leaves " + r.get("reachableShelters")
                        + " of " + baseline.get("reachableShelters") + " reachable shelters ("
                        + r.get("freePlacesReachable") + " free places vs " + baseline.get("freePlacesReachable") + " now).");
            }
        }
        if (lines.size() == 1) {
            lines.add("No scenario drops the reachable-shelter count below the "
                    + baseline.get("reachableShelters") + " reachable right now.");
        }
        return lines;
    }

    private double routeKm(Map<String, Object> metrics) {
        Object route = metrics.get("route");
        if (route instanceof Map<?, ?> map) {
            Object km = ((Map<?, ?>) map).get("distanceKm");
            if (km instanceof Number n) return n.doubleValue();
        }
        return 0;
    }

    private int intOf(Object o) {
        return o instanceof Number n ? n.intValue() : 0;
    }

    private String fmt(double v) {
        return v == Math.rint(v) ? String.valueOf((long) v) : String.valueOf(Math.round(v * 10.0) / 10.0);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> readScenarios(Object raw) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (!(raw instanceof List)) return out;
        for (Object item : (List<Object>) raw) {
            if (item instanceof Map) out.add((Map<String, Object>) item);
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, String>> readBlocks(Object raw) {
        List<Map<String, String>> out = new ArrayList<>();
        if (!(raw instanceof List)) return out;
        for (Object item : (List<Object>) raw) {
            if (item instanceof Map) {
                Map<String, String> m = (Map<String, String>) item;
                if (m.get("from") != null && m.get("to") != null) out.add(m);
            }
        }
        return out;
    }

    // ------------------------------------------------------------------
    //  Dashboard aggregate
    // ------------------------------------------------------------------

    /** Everything the dashboard needs in one call. */
    @GetMapping("/dashboard")
    public Map<String, Object> dashboard() {
        Disaster current = disasterService.getCurrentDisaster();
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("locations", graph.getLocationCount());
        resp.put("roads", graph.getRoadCount());
        resp.put("safeZones", safeZoneService.getAllSafeZones().size());
        resp.put("blockedRoads", graph.getBlockedRoadCount());
        resp.put("highRiskRoads", graph.getHighRiskRoadCount());
        resp.put("damagedRoads", graph.getDamagedRoadCount());
        resp.put("availableShelters", safeZoneService.getAvailableSafeZones().size());

        List<Map<String, Object>> roads = currentRoads();
        resp.put("recentRoads", roads.subList(0, Math.min(6, roads.size())));
        resp.put("roadsAll", roads);

        Map<String, Object> disaster = new LinkedHashMap<>();
        if (current != null) {
            disaster.put("type", current.getType().getDisplayName());
            disaster.put("severity", current.getSeverity().name());
            disaster.put("description", current.getDescription());
            disaster.put("recommendedAction", current.getRecommendedAction());
            disaster.put("affectedRoadCount", current.getAffectedRoads().size());
        }
        resp.put("currentDisaster", disaster);
        return resp;
    }

    // ------------------------------------------------------------------
    //  Helpers
    // ------------------------------------------------------------------

    private List<Map<String, Object>> currentRoads() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (Road road : graph.getAllRoads()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("from", road.getSource().getName());
            m.put("to", road.getDestination().getName());
            m.put("fromId", road.getSource().getId());
            m.put("toId", road.getDestination().getId());
            m.put("distanceKm", road.getDistance());
            m.put("trafficLevel", road.getTrafficLevel());
            m.put("terrain", road.getRoadTerrain().name());
            m.put("riskLevel", road.getRiskLevel().name());
            m.put("status", road.getStatus().name());
            // Exact cost-model inputs so map popups match the routing engine.
            // Blocked roads cost infinity, which has no JSON form — send null.
            m.put("cost", road.isBlocked() ? null : round(road.getCost()));
            m.put("riskPenalty", road.getRiskLevel().getPenalty());
            m.put("disasterPenalty", round(road.getDisasterPenalty()));
            list.add(m);
        }
        return list;
    }

    private Location requireLocation(String name) {
        if (name == null || name.isBlank()) throw new BadRequestException("Location name is required");
        Location loc = graph.getLocationByName(name.trim());
        if (loc == null) throw new BadRequestException("Unknown location: " + name);
        return loc;
    }

    private <T extends Enum<T>> T parseEnum(Class<T> type, String value, String what) {
        if (value == null || value.isBlank()) throw new BadRequestException(what + " is required");
        try {
            return Enum.valueOf(type, value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("Unknown " + what + ": " + value);
        }
    }

    private int parseInt(Object o, int def) {
        try { return Integer.parseInt(String.valueOf(o)); } catch (Exception e) { return def; }
    }

    private double round(double v) { return Math.round(v * 10.0) / 10.0; }

    public static class BadRequestException extends RuntimeException {
        public BadRequestException(String msg) { super(msg); }
    }

    /** JSON error shape for validation failures. */
    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<Map<String, Object>> handleBad(BadRequestException ex) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", ex.getMessage());
        return ResponseEntity.badRequest().body(body);
    }
}
