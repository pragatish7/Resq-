package algorithms;

import model.Graph;
import model.Location;
import model.Road;

import java.util.*;

/**
 * Depth-First Search (DFS) implementation.
 * 
 * DFS explores as far as possible along each branch before backtracking.
 * Useful for:
 * - Exploring all connected disaster-affected regions
 * - Checking connectivity between locations
 * - Discovering all reachable areas from a starting point
 * 
 * Time Complexity: O(V + E)
 * Space Complexity: O(V)
 */
public class DFS {

    private Graph graph;

    public DFS(Graph graph) {
        this.graph = graph;
    }

    /**
     * Perform DFS traversal from the given starting location (iterative).
     * Returns a list of locations in the order they were visited.
     */
    public List<Location> traverse(Location start) {
        List<Location> traversalOrder = new ArrayList<>();
        Set<Location> visited = new HashSet<>();

        if (start == null) return traversalOrder;

        Deque<Location> stack = new ArrayDeque<>();
        stack.push(start);

        while (!stack.isEmpty()) {
            Location current = stack.pop();

            if (visited.contains(current)) continue;
            visited.add(current);
            traversalOrder.add(current);

            // Add all traversable neighbors to the stack
            // Reverse order to maintain left-to-right visit order
            List<Road> roads = graph.getTraversableRoads(current);
            for (int i = roads.size() - 1; i >= 0; i--) {
                Location neighbor = roads.get(i).getDestination();
                if (!visited.contains(neighbor)) {
                    stack.push(neighbor);
                }
            }
        }

        return traversalOrder;
    }

    /**
     * Perform recursive DFS traversal from the given starting location.
     */
    public List<Location> traverseRecursive(Location start) {
        List<Location> traversalOrder = new ArrayList<>();
        Set<Location> visited = new HashSet<>();

        if (start != null) {
            dfsRecursive(start, visited, traversalOrder);
        }

        return traversalOrder;
    }

    private void dfsRecursive(Location current, Set<Location> visited,
                               List<Location> traversalOrder) {
        visited.add(current);
        traversalOrder.add(current);

        for (Road road : graph.getTraversableRoads(current)) {
            Location neighbor = road.getDestination();
            if (!visited.contains(neighbor)) {
                dfsRecursive(neighbor, visited, traversalOrder);
            }
        }
    }

    /**
     * Check if there is a path between two locations using DFS.
     */
    public boolean isConnected(Location source, Location destination) {
        if (source == null || destination == null) return false;
        if (source.equals(destination)) return true;

        Set<Location> visited = new HashSet<>();
        Deque<Location> stack = new ArrayDeque<>();
        stack.push(source);

        while (!stack.isEmpty()) {
            Location current = stack.pop();
            if (visited.contains(current)) continue;
            visited.add(current);

            if (current.equals(destination)) return true;

            for (Road road : graph.getTraversableRoads(current)) {
                if (!visited.contains(road.getDestination())) {
                    stack.push(road.getDestination());
                }
            }
        }

        return false;
    }

    /**
     * Find all connected components in the graph using DFS.
     * Useful for identifying isolated disaster-affected areas.
     */
    public List<List<Location>> findConnectedComponents() {
        List<List<Location>> components = new ArrayList<>();
        Set<Location> visited = new HashSet<>();

        for (Location loc : graph.getAllLocations()) {
            if (!visited.contains(loc)) {
                List<Location> component = new ArrayList<>();
                Deque<Location> stack = new ArrayDeque<>();
                stack.push(loc);

                while (!stack.isEmpty()) {
                    Location current = stack.pop();
                    if (visited.contains(current)) continue;
                    visited.add(current);
                    component.add(current);

                    for (Road road : graph.getTraversableRoads(current)) {
                        if (!visited.contains(road.getDestination())) {
                            stack.push(road.getDestination());
                        }
                    }
                }

                components.add(component);
            }
        }

        return components;
    }

    /**
     * Format DFS traversal result as a readable string.
     */
    public String formatTraversal(List<Location> traversal) {
        if (traversal.isEmpty()) {
            return "DFS Traversal: No locations to traverse.";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("DFS Traversal Order:\n");
        for (int i = 0; i < traversal.size(); i++) {
            sb.append(traversal.get(i).getName());
            if (i < traversal.size() - 1) {
                sb.append(" -> ");
            }
        }
        return sb.toString();
    }
}
