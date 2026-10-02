package algorithms;

import model.Graph;
import model.Location;
import model.Road;

import java.util.*;

/**
 * Dijkstra's Algorithm implementation for finding the safest/shortest route.
 * 
 * The algorithm finds the minimum-cost path from source to destination,
 * where cost = distance + riskPenalty + trafficPenalty.
 * Blocked roads have infinite cost and are naturally excluded.
 * 
 * Time Complexity: O((V + E) log V) using priority queue
 * Space Complexity: O(V)
 */
public class DijkstraAlgorithm {

    /**
     * Result of Dijkstra's algorithm containing the route and cost information.
     */
    public static class DijkstraResult {
        private List<Location> route;
        private double totalCost;
        private double totalDistance;
        private boolean routeFound;
        private String message;

        public DijkstraResult(List<Location> route, double totalCost,
                              double totalDistance, boolean routeFound, String message) {
            this.route = route;
            this.totalCost = totalCost;
            this.totalDistance = totalDistance;
            this.routeFound = routeFound;
            this.message = message;
        }

        public List<Location> getRoute() { return route; }
        public double getTotalCost() { return totalCost; }
        public double getTotalDistance() { return totalDistance; }
        public boolean isRouteFound() { return routeFound; }
        public String getMessage() { return message; }

        @Override
        public String toString() {
            if (!routeFound) {
                return "NO ROUTE FOUND: " + message;
            }
            StringBuilder sb = new StringBuilder();
            sb.append("Route found (Total distance: ").append(String.format("%.1f", totalDistance))
              .append(" km, Cost: ").append(String.format("%.1f", totalCost)).append(")\n");
            for (int i = 0; i < route.size(); i++) {
                sb.append(route.get(i).getName());
                if (i < route.size() - 1) sb.append(" -> ");
            }
            return sb.toString();
        }
    }

    private Graph graph;

    public DijkstraAlgorithm(Graph graph) {
        this.graph = graph;
    }

    /**
     * Find the safest/shortest route from source to destination using Dijkstra's algorithm.
     * Minimises the real cost model (distance + risk + disaster + traffic penalties).
     * 
     * @param source Starting location
     * @param destination Target location
     * @return DijkstraResult containing route and cost information
     */
    public DijkstraResult findShortestRoute(Location source, Location destination) {
        return findRoute(source, destination, false);
    }

    /**
     * Distance-only variant: minimises the summed road distance in kilometres.
     * Used for the "shortest vs safest" comparison; blocked roads are still
     * excluded (a blocked road is not a drivable road). The returned
     * totalCost is still the real weighted cost of that distance-minimal route,
     * so both measures can be shown for the same path.
     */
    public DijkstraResult findShortestDistanceRoute(Location source, Location destination) {
        return findRoute(source, destination, true);
    }

    /**
     * Shared Dijkstra search. {@code byDistance} picks the edge weight:
     * road distance in km, or the full cost model. All other behaviour
     * (blocked roads excluded, path reconstruction, result fields) is identical.
     */
    private DijkstraResult findRoute(Location source, Location destination, boolean byDistance) {
        if (source == null || destination == null) {
            return new DijkstraResult(null, 0, 0, false, "Invalid source or destination");
        }

        if (source.equals(destination)) {
            List<Location> route = new ArrayList<>();
            route.add(source);
            return new DijkstraResult(route, 0, 0, true, "Source and destination are the same");
        }

        // Distance map: location -> minimum weight to reach (cost or km)
        Map<Location, Double> distances = new HashMap<>();
        // Previous location map for path reconstruction
        Map<Location, Location> previous = new HashMap<>();
        // Track actual distances (not cost)
        Map<Location, Double> actualDistances = new HashMap<>();
        // Track the real weighted cost alongside the distance-minimising weight
        Map<Location, Double> actualCosts = new HashMap<>();
        // Priority queue: (weight, location)
        PriorityQueue<Map.Entry<Double, Location>> pq = new PriorityQueue<>(
            Comparator.comparingDouble(Map.Entry::getKey)
        );
        // Set of visited locations
        Set<Location> visited = new HashSet<>();

        // Initialize all distances to infinity
        for (Location loc : graph.getAllLocations()) {
            distances.put(loc, Double.MAX_VALUE);
            actualDistances.put(loc, 0.0);
            actualCosts.put(loc, 0.0);
        }

        // Source distance is 0
        distances.put(source, 0.0);
        actualDistances.put(source, 0.0);
        actualCosts.put(source, 0.0);
        pq.offer(new AbstractMap.SimpleEntry<>(0.0, source));

        while (!pq.isEmpty()) {
            Map.Entry<Double, Location> current = pq.poll();
            double currentCost = current.getKey();
            Location currentLoc = current.getValue();

            // Skip if already visited
            if (visited.contains(currentLoc)) continue;
            visited.add(currentLoc);

            // If we reached the destination, we're done
            if (currentLoc.equals(destination)) {
                break;
            }

            // Explore neighbors
            for (Road road : graph.getTraversableRoads(currentLoc)) {
                Location neighbor = road.getDestination();

                if (visited.contains(neighbor)) continue;

                // Calculate new weight to reach neighbor
                double step = byDistance ? road.getDistance() : road.getCost();
                double newCost = currentCost + step;
                double newDist = actualDistances.get(currentLoc) + road.getDistance();
                double newRealCost = actualCosts.get(currentLoc) + road.getCost();

                // If found a better path, update
                if (newCost < distances.get(neighbor)) {
                    distances.put(neighbor, newCost);
                    actualDistances.put(neighbor, newDist);
                    actualCosts.put(neighbor, newRealCost);
                    previous.put(neighbor, currentLoc);
                    pq.offer(new AbstractMap.SimpleEntry<>(newCost, neighbor));
                }
            }
        }

        // Reconstruct path
        if (distances.get(destination) == Double.MAX_VALUE) {
            return new DijkstraResult(null, 0, 0, false,
                    "NO SAFE ROUTE AVAILABLE from " + source.getName() + " to " + destination.getName());
        }

        List<Location> route = new ArrayList<>();
        Location current = destination;
        while (current != null) {
            route.add(0, current);
            current = previous.get(current);
        }

        return new DijkstraResult(route, actualCosts.get(destination),
                actualDistances.get(destination), true, "Route found successfully");
    }

    /**
     * Find the nearest available safe zone from a given location.
     */
    public DijkstraResult findNearestSafeZone(Location source, List<Location> safeZoneLocations) {
        DijkstraResult bestResult = null;

        for (Location safeZone : safeZoneLocations) {
            DijkstraResult result = findShortestRoute(source, safeZone);
            if (result.isRouteFound()) {
                if (bestResult == null || result.getTotalCost() < bestResult.getTotalCost()) {
                    bestResult = result;
                }
            }
        }

        if (bestResult == null) {
            return new DijkstraResult(null, 0, 0, false,
                    "NO SAFE ROUTE AVAILABLE to any safe zone from " + source.getName());
        }

        return bestResult;
    }
}
