import model.*;
import algorithms.*;
import service.*;

import java.util.*;

/**
 * Comprehensive test suite for the Smart Disaster Response System.
 * Tests Dijkstra, BFS, DFS, and Kruskal's MST algorithms.
 * 
 * Run: javac -d bin src/model/*.java src/algorithms/*.java src/service/*.java tests/AlgorithmTests.java && java -cp bin AlgorithmTests
 */
public class AlgorithmTests {

    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) {
        System.out.println("╔══════════════════════════════════════════════════════╗");
        System.out.println("║  ALGORITHM TEST SUITE                               ║");
        System.out.println("║  Smart Disaster Response System                     ║");
        System.out.println("╚══════════════════════════════════════════════════════╝\n");

        testDijkstraNormalRoute();
        testDijkstraBlockedRoad();
        testDijkstraHighRiskRoad();
        testDijkstraNoRoute();
        testDijkstraSameSourceAndDestination();
        testBFSTraversal();
        testBFSMinimumRoads();
        testDFSTraversal();
        testDFSConnectivity();
        testDFSConnectedComponents();
        testMSTBasic();
        testMSTConnectivity();
        testSafeZoneAvailability();
        testDisasterEffects();
        testUndirectedRoadSymmetry();

        System.out.println("\n══════════════════════════════════════════════════════");
        System.out.printf("TEST RESULTS: %d passed, %d failed, %d total%n",
                passed, failed, passed + failed);
        System.out.println("══════════════════════════════════════════════════════");

        if (failed > 0) {
            System.exit(1);
        }
    }

    // =================== DIJKSTRA TESTS ===================

    private static void testDijkstraNormalRoute() {
        System.out.println("--- Test: Dijkstra Normal Route ---");
        Graph graph = createTestGraph();
        DijkstraAlgorithm dijkstra = new DijkstraAlgorithm(graph);

        Location a = graph.getLocationByName("A");
        Location d = graph.getLocationByName("D");

        DijkstraAlgorithm.DijkstraResult result = dijkstra.findShortestRoute(a, d);

        assertTrue("Route should be found", result.isRouteFound());
        assertTrue("Route should have locations", result.getRoute() != null && result.getRoute().size() > 0);
        assertTrue("Total distance should be positive", result.getTotalDistance() > 0);
        assertTrue("Route starts at source", result.getRoute().get(0).equals(a));
        assertTrue("Route ends at destination", result.getRoute().get(result.getRoute().size() - 1).equals(d));

        System.out.println("  Route: " + result);
        System.out.println();
    }

    private static void testDijkstraBlockedRoad() {
        System.out.println("--- Test: Dijkstra Blocked Road ---");
        Graph graph = createTestGraph();
        DijkstraAlgorithm dijkstra = new DijkstraAlgorithm(graph);

        Location a = graph.getLocationByName("A");
        Location d = graph.getLocationByName("D");

        // First, find route without blocking
        DijkstraAlgorithm.DijkstraResult result1 = dijkstra.findShortestRoute(a, d);
        double costBefore = result1.getTotalCost();
        System.out.println("  Cost before blocking: " + costBefore);

        // Block a road
        for (Road road : graph.getAllRoads()) {
            if (road.getSource().getName().equals("A") && road.getDestination().getName().equals("B")) {
                road.setStatus(Road.Status.BLOCKED);
                break;
            }
        }

        // Find route after blocking
        DijkstraAlgorithm.DijkstraResult result2 = dijkstra.findShortestRoute(a, d);
        System.out.println("  Cost after blocking A->B: " + result2.getTotalCost());

        assertTrue("Route should still be found", result2.isRouteFound());
        assertTrue("Route should differ after blocking", result2.getTotalCost() != costBefore || result2.getRoute().size() != result1.getRoute().size());

        // Reset
        graph.resetRoadStatuses();
        System.out.println();
    }

    private static void testDijkstraHighRiskRoad() {
        System.out.println("--- Test: Dijkstra High Risk Road ---");
        Graph graph = createTestGraph();
        DijkstraAlgorithm dijkstra = new DijkstraAlgorithm(graph);

        Location a = graph.getLocationByName("A");
        Location d = graph.getLocationByName("D");

        // Set a road to high risk
        for (Road road : graph.getAllRoads()) {
            if (road.getSource().getName().equals("B") && road.getDestination().getName().equals("D")) {
                road.setRiskLevel(Road.RiskLevel.VERY_HIGH);
                road.setStatus(Road.Status.HIGH_RISK);
                break;
            }
        }

        DijkstraAlgorithm.DijkstraResult result = dijkstra.findShortestRoute(a, d);
        assertTrue("Route should be found with high risk roads", result.isRouteFound());
        assertTrue("Total cost should account for risk", result.getTotalCost() > 0);

        System.out.println("  Route with high risk: " + result);

        // Reset
        graph.resetRoadStatuses();
        System.out.println();
    }

    private static void testDijkstraNoRoute() {
        System.out.println("--- Test: Dijkstra No Route ---");
        Graph graph = createTestGraph();
        DijkstraAlgorithm dijkstra = new DijkstraAlgorithm(graph);

        Location a = graph.getLocationByName("A");
        Location d = graph.getLocationByName("D");

        // Block all roads from A
        for (Road road : graph.getAllRoads()) {
            if (road.getSource().getName().equals("A")) {
                road.setStatus(Road.Status.BLOCKED);
            }
        }

        DijkstraAlgorithm.DijkstraResult result = dijkstra.findShortestRoute(a, d);
        assertFalse("No route should be found", result.isRouteFound());
        assertTrue("Message should indicate no route", result.getMessage().contains("NO SAFE ROUTE"));

        System.out.println("  Result: " + result.getMessage());

        // Reset
        graph.resetRoadStatuses();
        System.out.println();
    }

    private static void testDijkstraSameSourceAndDestination() {
        System.out.println("--- Test: Dijkstra Same Source and Destination ---");
        Graph graph = createTestGraph();
        DijkstraAlgorithm dijkstra = new DijkstraAlgorithm(graph);

        Location a = graph.getLocationByName("A");
        DijkstraAlgorithm.DijkstraResult result = dijkstra.findShortestRoute(a, a);

        assertTrue("Should find route when source equals destination", result.isRouteFound());
        assertEquals("Route should contain only source", 1, result.getRoute().size());
        assertEquals("Cost should be 0", 0.0, result.getTotalCost(), 0.001);

        System.out.println("  Result: " + result);
        System.out.println();
    }

    // =================== BFS TESTS ===================

    private static void testBFSTraversal() {
        System.out.println("--- Test: BFS Traversal ---");
        Graph graph = createTestGraph();
        BFS bfs = new BFS(graph);

        Location a = graph.getLocationByName("A");
        List<Location> traversal = bfs.traverse(a);

        assertTrue("BFS should visit locations", traversal.size() > 0);
        assertTrue("BFS should start at source", traversal.get(0).equals(a));

        System.out.println("  BFS Traversal: " + bfs.formatTraversal(traversal));
        System.out.println();
    }

    private static void testBFSMinimumRoads() {
        System.out.println("--- Test: BFS Minimum Roads ---");
        Graph graph = createTestGraph();
        BFS bfs = new BFS(graph);

        Location a = graph.getLocationByName("A");
        Location d = graph.getLocationByName("D");

        int minRoads = bfs.minRoadsToDestination(a, d);
        assertTrue("Minimum roads should be positive", minRoads > 0);
        System.out.println("  Minimum roads from A to D: " + minRoads);

        // Test same location
        int sameLoc = bfs.minRoadsToDestination(a, a);
        assertEquals("Minimum roads to same location should be 0", 0, sameLoc);

        System.out.println();
    }

    // =================== DFS TESTS ===================

    private static void testDFSTraversal() {
        System.out.println("--- Test: DFS Traversal ---");
        Graph graph = createTestGraph();
        DFS dfs = new DFS(graph);

        Location a = graph.getLocationByName("A");
        List<Location> traversal = dfs.traverse(a);

        assertTrue("DFS should visit locations", traversal.size() > 0);
        assertTrue("DFS should start at source", traversal.get(0).equals(a));

        System.out.println("  DFS Traversal: " + dfs.formatTraversal(traversal));
        System.out.println();
    }

    private static void testDFSConnectivity() {
        System.out.println("--- Test: DFS Connectivity ---");
        Graph graph = createTestGraph();
        DFS dfs = new DFS(graph);

        Location a = graph.getLocationByName("A");
        Location d = graph.getLocationByName("D");

        boolean connected = dfs.isConnected(a, d);
        assertTrue("A and D should be connected in test graph", connected);

        // Block all roads from A
        for (Road road : graph.getAllRoads()) {
            if (road.getSource().getName().equals("A")) {
                road.setStatus(Road.Status.BLOCKED);
            }
        }

        boolean connectedAfterBlock = dfs.isConnected(a, d);
        assertFalse("A and D should not be connected after blocking all roads from A", connectedAfterBlock);

        System.out.println("  Connected before block: " + connected);
        System.out.println("  Connected after block: " + connectedAfterBlock);

        // Reset
        graph.resetRoadStatuses();
        System.out.println();
    }

    private static void testDFSConnectedComponents() {
        System.out.println("--- Test: DFS Connected Components ---");
        Graph graph = createTestGraph();
        DFS dfs = new DFS(graph);

        List<List<Location>> components = dfs.findConnectedComponents();
        assertTrue("Should find at least one connected component", components.size() >= 1);
        System.out.println("  Number of connected components: " + components.size());

        for (int i = 0; i < components.size(); i++) {
            List<String> names = new ArrayList<>();
            for (Location loc : components.get(i)) {
                names.add(loc.getName());
            }
            System.out.println("  Component " + (i + 1) + ": " + String.join(", ", names));
        }
        System.out.println();
    }

    // =================== MST TESTS ===================

    private static void testMSTBasic() {
        System.out.println("--- Test: MST Basic ---");
        Graph graph = createTestGraph();
        KruskalMST kruskal = new KruskalMST(graph);

        KruskalMST.MSTResult result = kruskal.computeMST();

        assertTrue("MST should have edges", result.getEdges().size() > 0);
        assertTrue("MST cost should be positive", result.getTotalCost() > 0);

        // MST should have V-1 edges for a connected graph
        int expectedEdges = graph.getLocationCount() - 1;
        assertEquals("MST should have V-1 edges for connected graph",
                expectedEdges, result.getEdges().size());

        System.out.println("  " + result);
        System.out.println();
    }

    private static void testMSTConnectivity() {
        System.out.println("--- Test: MST Connectivity ---");
        Graph graph = createTestGraph();
        KruskalMST kruskal = new KruskalMST(graph);

        KruskalMST.MSTResult result = kruskal.computeMST();
        assertTrue("MST should be complete for connected graph", result.isComplete());
        System.out.println("  MST Complete: " + result.isComplete());
        System.out.println();
    }

    // =================== SAFE ZONE TESTS ===================

    private static void testSafeZoneAvailability() {
        System.out.println("--- Test: Safe Zone Availability ---");
        SafeZoneService service = new SafeZoneService();
        Location loc1 = new Location("test1", "Test1", 0, 0);
        Location loc2 = new Location("test2", "Test2", 100, 100);

        SafeZone zone1 = new SafeZone("Zone A", loc1, 100, 100, SafeZone.SafetyLevel.HIGH);
        SafeZone zone2 = new SafeZone("Zone B", loc2, 200, 50, SafeZone.SafetyLevel.MEDIUM);

        service.addSafeZone(zone1);
        service.addSafeZone(zone2);

        assertFalse("Zone A should be full", zone1.isAvailable());
        assertTrue("Zone B should be available", zone2.isAvailable());
        assertEquals("Available zones count should be 1", 1, service.getAvailableSafeZones().size());

        System.out.println("  " + zone1);
        System.out.println("  " + zone2);
        System.out.println("  Available: " + service.getAvailableSafeZones().size());
        System.out.println();
    }

    // =================== DISASTER EFFECTS TESTS ===================

    private static void testDisasterEffects() {
        System.out.println("--- Test: Disaster Effects ---");
        Graph graph = createTestGraph();
        DisasterService disasterService = new DisasterService(graph);

        // Apply flood at CRITICAL severity to ensure some roads are blocked
        Disaster flood = disasterService.applyDisaster(Disaster.Type.FLOOD, Disaster.Severity.CRITICAL);
        assertTrue("Flood should affect roads", flood.getAffectedRoads().size() > 0);
        System.out.println("  Flood affected " + flood.getAffectedRoads().size() + " roads");

        // Check some roads are blocked
        boolean hasBlocked = false;
        for (Road road : graph.getAllRoads()) {
            if (road.isBlocked()) {
                hasBlocked = true;
                break;
            }
        }
        assertTrue("Flood should block some roads at CRITICAL severity", hasBlocked);

        // Reset and apply earthquake
        graph.resetRoadStatuses();
        Disaster earthquake = disasterService.applyDisaster(Disaster.Type.EARTHQUAKE);
        assertTrue("Earthquake should affect roads", earthquake.getAffectedRoads().size() > 0);
        System.out.println("  Earthquake affected " + earthquake.getAffectedRoads().size() + " roads");

        // Reset
        graph.resetRoadStatuses();
        System.out.println();
    }

    // =================== UNDIRECTED ROAD SYMMETRY ===================

    /**
     * A road is one physical, undirected road: both travel directions must share
     * status/risk/penalty, so a disaster or manual status change can never leave
     * one direction open while the other is impassable or differently priced.
     */
    private static void testUndirectedRoadSymmetry() {
        System.out.println("--- Test: Undirected Road Symmetry ---");
        Graph graph = createTestGraph();
        DisasterService disasterService = new DisasterService(graph);
        DijkstraAlgorithm dijkstra = new DijkstraAlgorithm(graph);
        RouteService routeService = new RouteService(graph);

        // MEDIUM flood damages every road in the test graph (all NORMAL terrain).
        disasterService.applyDisaster(Disaster.Type.FLOOD, Disaster.Severity.MEDIUM);

        int checked = 0, asymmetric = 0;
        for (Road road : graph.getAllRoads()) {
            Road reverse = reverseOf(graph, road);
            checked++;
            if (reverse == null
                    || road.getStatus() != reverse.getStatus()
                    || road.getRiskLevel() != reverse.getRiskLevel()
                    || Math.abs(road.getDisasterPenalty() - reverse.getDisasterPenalty()) > 0.001
                    || Double.compare(road.getCost(), reverse.getCost()) != 0) {
                asymmetric++;
            }
        }
        assertEquals("Every road should have a reverse direction", graph.getRoadCount(), checked);
        assertEquals("Both directions should share condition after a disaster", 0, asymmetric);

        Location a = graph.getLocationByName("A");
        Location b = graph.getLocationByName("B");
        Location d = graph.getLocationByName("D");
        DijkstraAlgorithm.DijkstraResult forward = dijkstra.findShortestRoute(a, d);
        DijkstraAlgorithm.DijkstraResult backward = dijkstra.findShortestRoute(d, a);
        assertTrue("Forward route should be found", forward.isRouteFound());
        assertTrue("Backward route should also be found", backward.isRouteFound());
        assertEquals("Both directions should cost the same",
                forward.getTotalCost(), backward.getTotalCost(), 0.001);
        String forwardRisk = routeService.getRouteRiskLevel(forward.getRoute());
        String backwardRisk = routeService.getRouteRiskLevel(backward.getRoute());
        assertTrue("Both directions should report the same risk (" + forwardRisk + " vs " + backwardRisk + ")",
                forwardRisk.equals(backwardRisk));

        // Manual status changes must apply to the whole undirected road too.
        Road ab = null;
        for (Road road : graph.getAllRoads()) {
            if (road.getSource().equals(a) && road.getDestination().equals(b)) ab = road;
        }
        assertTrue("Test graph should contain road A-B", ab != null);
        disasterService.updateRoadStatus(a, b, Road.Status.BLOCKED);
        assertTrue("Blocking a road blocks the reverse direction as well",
                ab.isBlocked() && reverseOf(graph, ab).isBlocked());
        graph.resetRoadStatuses();
        assertTrue("Reset restores the reverse direction too",
                !reverseOf(graph, ab).isBlocked()
                        && reverseOf(graph, ab).getStatus() == Road.Status.OPEN
                        && reverseOf(graph, ab).getDisasterPenalty() == 0);

        System.out.println();
    }

    /** The road in the opposite direction between the same two locations. */
    private static Road reverseOf(Graph graph, Road road) {
        if (road == null) return null;
        for (Road candidate : graph.getRoads(road.getDestination())) {
            if (candidate.getDestination().equals(road.getSource())) return candidate;
        }
        return null;
    }

    // =================== HELPER METHODS ===================

    /**
     * Create a test graph for algorithm testing.
     * 
     * Graph structure:
     *   A --2-- B --3-- D
     *   |       |       |
     *   4       1       2
     *   |       |       |
     *   C --5-- E --1-- F
     */
    private static Graph createTestGraph() {
        Graph graph = new Graph();

        Location a = new Location("a", "A", 0, 0);
        Location b = new Location("b", "B", 100, 0);
        Location c = new Location("c", "C", 0, 100);
        Location d = new Location("d", "D", 200, 0);
        Location e = new Location("e", "E", 100, 100);
        Location f = new Location("f", "F", 200, 100);

        graph.addLocation(a);
        graph.addLocation(b);
        graph.addLocation(c);
        graph.addLocation(d);
        graph.addLocation(e);
        graph.addLocation(f);

        graph.addRoad(new Road(a, b, 2.0));
        graph.addRoad(new Road(a, c, 4.0));
        graph.addRoad(new Road(b, d, 3.0));
        graph.addRoad(new Road(b, e, 1.0));
        graph.addRoad(new Road(c, e, 5.0));
        graph.addRoad(new Road(d, f, 2.0));
        graph.addRoad(new Road(e, f, 1.0));

        return graph;
    }

    // =================== ASSERTION HELPERS ===================

    private static void assertTrue(String message, boolean condition) {
        if (condition) {
            System.out.println("  ✅ PASS: " + message);
            passed++;
        } else {
            System.out.println("  ❌ FAIL: " + message);
            failed++;
        }
    }

    private static void assertFalse(String message, boolean condition) {
        assertTrue(message, !condition);
    }

    private static void assertEquals(String message, int expected, int actual) {
        if (expected == actual) {
            System.out.println("  ✅ PASS: " + message + " (" + actual + ")");
            passed++;
        } else {
            System.out.println("  ❌ FAIL: " + message + " (expected=" + expected + ", actual=" + actual + ")");
            failed++;
        }
    }

    private static void assertEquals(String message, double expected, double actual, double delta) {
        if (Math.abs(expected - actual) <= delta) {
            System.out.println("  ✅ PASS: " + message + " (" + actual + ")");
            passed++;
        } else {
            System.out.println("  ❌ FAIL: " + message + " (expected=" + expected + ", actual=" + actual + ")");
            failed++;
        }
    }
}
