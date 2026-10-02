package algorithms;

import java.util.HashMap;
import java.util.Map;

/**
 * Union-Find (Disjoint Set) data structure used by Kruskal's MST algorithm.
 * 
 * Supports two operations:
 * - find: Find the root/representative of a set
 * - union: Merge two sets into one
 * 
 * Uses path compression and union by rank for efficiency.
 * Time Complexity: Nearly O(1) amortized per operation
 */
public class UnionFind<T> {
    private Map<T, T> parent;
    private Map<T, Integer> rank;

    public UnionFind() {
        parent = new HashMap<>();
        rank = new HashMap<>();
    }

    /**
     * Add an element as its own set.
     */
    public void makeSet(T element) {
        if (!parent.containsKey(element)) {
            parent.put(element, element);
            rank.put(element, 0);
        }
    }

    /**
     * Find the root/representative of the set containing the element.
     * Uses path compression for efficiency.
     */
    public T find(T element) {
        if (!parent.containsKey(element)) {
            makeSet(element);
        }
        if (!parent.get(element).equals(element)) {
            parent.put(element, find(parent.get(element))); // Path compression
        }
        return parent.get(element);
    }

    /**
     * Union two sets by their rank.
     * Returns true if the union was performed (elements were in different sets).
     */
    public boolean union(T element1, T element2) {
        T root1 = find(element1);
        T root2 = find(element2);

        if (root1.equals(root2)) {
            return false; // Already in the same set
        }

        // Union by rank
        int rank1 = rank.get(root1);
        int rank2 = rank.get(root2);

        if (rank1 < rank2) {
            parent.put(root1, root2);
        } else if (rank1 > rank2) {
            parent.put(root2, root1);
        } else {
            parent.put(root2, root1);
            rank.put(root1, rank1 + 1);
        }

        return true;
    }

    /**
     * Check if two elements are in the same set.
     */
    public boolean sameSet(T element1, T element2) {
        return find(element1).equals(find(element2));
    }
}
