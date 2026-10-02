package algorithms;

import model.Graph;
import model.Location;
import model.Road;

import java.util.*;

/**
 * Kruskal's Minimum Spanning Tree algorithm implementation.
 * 
 * Finds the minimum-cost network that connects all locations using the
 * least total road distance. Useful for emergency planning to establish
 * minimum communication/transportation infrastructure.
 * 
 * Algorithm:
 * 1. Sort all edges by distance (weight)
 * 2. Process edges from shortest to longest
 * 3. Add edge to MST if it doesn't create a cycle (using Union-Find)
 * 4. Repeat until V-1 edges are added or all edges are processed
 * 
 * Time Complexity: O(E log E) where E is number of edges
 * Space Complexity: O(V + E)
 */
public class KruskalMST {

    private Graph graph;

    public KruskalMST(Graph graph) {
        this.graph = graph;
    }

    /**
     * Represents an edge in the MST.
     */
    public static class MSTEdge {
        private Location source;
        private Location destination;
        private double distance;

        public MSTEdge(Location source, Location destination, double distance) {
            this.source = source;
            this.destination = destination;
            this.distance = distance;
        }

        public Location getSource() { return source; }
        public Location getDestination() { return destination; }
        public double getDistance() { return distance; }

        @Override
        public String toString() {
            return source.getName() + " -- " + String.format("%.1f", distance)
                    + " km -- " + destination.getName();
        }
    }

    /**
     * Result of Kruskal's MST algorithm.
     */
    public static class MSTResult {
        private List<MSTEdge> edges;
        private double totalCost;
        private boolean complete;

        public MSTResult(List<MSTEdge> edges, double totalCost, boolean complete) {
            this.edges = edges;
            this.totalCost = totalCost;
            this.complete = complete;
        }

        public List<MSTEdge> getEdges() { return edges; }
        public double getTotalCost() { return totalCost; }
        public boolean isComplete() { return complete; }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("=== Minimum Spanning Tree (Kruskal's Algorithm) ===\n");
            if (!complete) {
                sb.append("WARNING: MST is not complete (graph may be disconnected)\n");
            }
            sb.append("Selected Edges:\n");
            for (int i = 0; i < edges.size(); i++) {
                sb.append(String.format("%d. %s\n", i + 1, edges.get(i)));
            }
            sb.append(String.format("Total MST Cost: %.1f km\n", totalCost));
            sb.append(String.format("Number of edges: %d\n", edges.size()));
            return sb.toString();
        }
    }

    /**
     * Compute the Minimum Spanning Tree using Kruskal's algorithm.
     */
    public MSTResult computeMST() {
        // Step 1: Collect all unique edges (only from our road list to avoid duplicates)
        List<Road> allRoads = graph.getAllRoads();
        List<Road> sortedRoads = new ArrayList<>(allRoads);

        // Sort edges by distance
        sortedRoads.sort(Comparator.comparingDouble(Road::getDistance));

        // Step 2: Initialize Union-Find for all locations
        UnionFind<Location> unionFind = new UnionFind<>();
        for (Location loc : graph.getAllLocations()) {
            unionFind.makeSet(loc);
        }

        // Step 3: Process edges
        List<MSTEdge> mstEdges = new ArrayList<>();
        double totalCost = 0;
        int verticesCount = graph.getLocationCount();

        for (Road road : sortedRoads) {
            Location source = road.getSource();
            Location destination = road.getDestination();

            // If adding this edge doesn't create a cycle
            if (unionFind.union(source, destination)) {
                mstEdges.add(new MSTEdge(source, destination, road.getDistance()));
                totalCost += road.getDistance();

                // MST has V-1 edges when complete
                if (mstEdges.size() == verticesCount - 1) {
                    break;
                }
            }
        }

        // Check if MST spans all vertices (graph is connected)
        boolean complete = mstEdges.size() == verticesCount - 1;

        return new MSTResult(mstEdges, totalCost, complete);
    }
}
