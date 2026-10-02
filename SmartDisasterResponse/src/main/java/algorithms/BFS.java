package algorithms;

import model.Graph;
import model.Location;
import model.Road;

import java.util.*;

/**
 * Breadth-First Search (BFS) implementation.
 * 
 * BFS explores locations level by level, finding the minimum number of
 * roads/steps to reach other locations from a starting point.
 * 
 * Useful for:
 * - Finding minimum number of roads to traverse
 * - Level-wise exploration of disaster-affected areas
 * - Finding shortest path in terms of number of edges
 * 
 * Time Complexity: O(V + E)
 * Space Complexity: O(V)
 */
public class BFS {

    private Graph graph;

    public BFS(Graph graph) {
        this.graph = graph;
    }

    /**
     * Perform BFS traversal from the given starting location.
     * Returns a list of locations in the order they were visited.
     */
    public List<Location> traverse(Location start) {
        List<Location> traversalOrder = new ArrayList<>();
        Set<Location> visited = new HashSet<>();
        Queue<Location> queue = new LinkedList<>();

        if (start == null || graph.getRoads(start) == null) {
            return traversalOrder;
        }

        visited.add(start);
        queue.offer(start);

        while (!queue.isEmpty()) {
            Location current = queue.poll();
            traversalOrder.add(current);

            // Visit all traversable (non-blocked) neighbors
            for (Road road : graph.getTraversableRoads(current)) {
                Location neighbor = road.getDestination();
                if (!visited.contains(neighbor)) {
                    visited.add(neighbor);
                    queue.offer(neighbor);
                }
            }
        }

        return traversalOrder;
    }

    /**
     * Find the minimum number of roads (edges) from source to destination.
     * Returns -1 if no path exists.
     */
    public int minRoadsToDestination(Location source, Location destination) {
        if (source == null || destination == null) return -1;
        if (source.equals(destination)) return 0;

        Set<Location> visited = new HashSet<>();
        Queue<Location> queue = new LinkedList<>();
        Map<Location, Integer> distance = new HashMap<>();

        visited.add(source);
        queue.offer(source);
        distance.put(source, 0);

        while (!queue.isEmpty()) {
            Location current = queue.poll();
            int currentDist = distance.get(current);

            for (Road road : graph.getTraversableRoads(current)) {
                Location neighbor = road.getDestination();
                if (!visited.contains(neighbor)) {
                    visited.add(neighbor);
                    distance.put(neighbor, currentDist + 1);
                    queue.offer(neighbor);

                    if (neighbor.equals(destination)) {
                        return currentDist + 1;
                    }
                }
            }
        }

        return -1; // No path found
    }

    /**
     * Perform BFS from source and return level-wise grouped locations.
     * Useful for disaster evacuation planning to understand distance levels.
     */
    public Map<Integer, List<Location>> traverseByLevel(Location start) {
        Map<Integer, List<Location>> levels = new TreeMap<>();
        Set<Location> visited = new HashSet<>();
        Queue<Location> queue = new LinkedList<>();

        if (start == null) return levels;

        visited.add(start);
        queue.offer(start);
        int level = 0;

        while (!queue.isEmpty()) {
            int levelSize = queue.size();
            List<Location> currentLevel = new ArrayList<>();

            for (int i = 0; i < levelSize; i++) {
                Location current = queue.poll();
                currentLevel.add(current);

                for (Road road : graph.getTraversableRoads(current)) {
                    Location neighbor = road.getDestination();
                    if (!visited.contains(neighbor)) {
                        visited.add(neighbor);
                        queue.offer(neighbor);
                    }
                }
            }

            levels.put(level, currentLevel);
            level++;
        }

        return levels;
    }

    /**
     * Format BFS traversal result as a readable string.
     */
    public String formatTraversal(List<Location> traversal) {
        if (traversal.isEmpty()) {
            return "BFS Traversal: No locations to traverse.";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("BFS Traversal Order:\n");
        for (int i = 0; i < traversal.size(); i++) {
            sb.append(traversal.get(i).getName());
            if (i < traversal.size() - 1) {
                sb.append(" -> ");
            }
        }
        return sb.toString();
    }
}
