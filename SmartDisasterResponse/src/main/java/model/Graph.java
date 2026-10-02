package model;

import java.util.*;

/**
 * Represents the disaster area as an adjacency list graph.
 * Roads are undirected: each one is stored as two directed {@link Road}
 * objects (one per direction) that are linked as twins and share condition
 * state, so a disaster or status change always applies to both directions.
 */
public class Graph {
    private Map<Location, List<Road>> adjacencyList;
    private Map<String, Location> locationMap;
    private List<Road> allRoads;

    public Graph() {
        this.adjacencyList = new LinkedHashMap<>();
        this.locationMap = new HashMap<>();
        this.allRoads = new ArrayList<>();
    }

    public void addLocation(Location location) {
        adjacencyList.putIfAbsent(location, new ArrayList<>());
        locationMap.put(location.getId(), location);
    }

    public void addRoad(Road road) {
        addLocation(road.getSource());
        addLocation(road.getDestination());
        adjacencyList.get(road.getSource()).add(road);
        Road reverseRoad = new Road(road.getDestination(), road.getSource(),
                road.getDistance(), road.getRiskLevel(), road.getStatus(),
                road.getTrafficLevel(), road.getRoadTerrain());
        // One physical road: both directions must share status/risk/penalty.
        road.linkTwin(reverseRoad);
        adjacencyList.get(road.getDestination()).add(reverseRoad);
        allRoads.add(road);
    }

    public Location getLocationById(String id) { return locationMap.get(id); }

    public Location getLocationByName(String name) {
        for (Location loc : locationMap.values()) {
            if (loc.getName().equalsIgnoreCase(name)) return loc;
        }
        return null;
    }

    public List<Road> getRoads(Location location) {
        return adjacencyList.getOrDefault(location, new ArrayList<>());
    }

    public Set<Location> getAllLocations() { return adjacencyList.keySet(); }
    public List<Road> getAllRoads() { return allRoads; }
    public int getLocationCount() { return adjacencyList.size(); }
    public int getRoadCount() { return allRoads.size(); }

    public void resetRoadStatuses() {
        // allRoads holds one direction per road; the twin write-through clears
        // the reverse direction too.
        for (Road road : allRoads) {
            road.setStatus(Road.Status.OPEN);
            road.setRiskLevel(Road.RiskLevel.LOW);
            road.setDisasterPenalty(0);
        }
    }

    public List<Road> getTraversableRoads(Location location) {
        List<Road> traversable = new ArrayList<>();
        for (Road road : getRoads(location)) {
            if (!road.isBlocked()) traversable.add(road);
        }
        return traversable;
    }

    public int getBlockedRoadCount() {
        Set<String> drawn = new HashSet<>();
        int count = 0;
        for (Road road : allRoads) {
            String key = road.getSource().getId() + "-" + road.getDestination().getId();
            if (!drawn.add(key)) continue;
            if (road.isBlocked()) count++;
        }
        return count;
    }

    public int getHighRiskRoadCount() {
        Set<String> drawn = new HashSet<>();
        int count = 0;
        for (Road road : allRoads) {
            String key = road.getSource().getId() + "-" + road.getDestination().getId();
            if (!drawn.add(key)) continue;
            if (road.getStatus() == Road.Status.HIGH_RISK) count++;
        }
        return count;
    }

    public int getDamagedRoadCount() {
        Set<String> drawn = new HashSet<>();
        int count = 0;
        for (Road road : allRoads) {
            String key = road.getSource().getId() + "-" + road.getDestination().getId();
            if (!drawn.add(key)) continue;
            if (road.getStatus() == Road.Status.DAMAGED) count++;
        }
        return count;
    }

    public void printGraph() {
        System.out.println("=== Graph Adjacency List ===");
        for (Map.Entry<Location, List<Road>> entry : adjacencyList.entrySet()) {
            System.out.print(entry.getKey().getName() + " -> ");
            List<String> neighbors = new ArrayList<>();
            for (Road road : entry.getValue()) {
                neighbors.add(road.getDestination().getName() + " (" + road.getDistance() + "km)");
            }
            System.out.println(String.join(", ", neighbors));
        }
        System.out.println("============================");
    }
}
