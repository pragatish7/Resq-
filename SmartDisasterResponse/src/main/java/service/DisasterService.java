package service;

import model.*;
import model.Location.TerrainType;
import model.Road.RoadTerrain;

import java.util.*;

/**
 * Service for managing disasters with terrain-aware and severity-aware road effects.
 * Each disaster type affects specific terrains differently.
 */
public class DisasterService {

    private Graph graph;
    private Disaster currentDisaster;

    public DisasterService(Graph graph) {
        this.graph = graph;
    }

    public Disaster getCurrentDisaster() { return currentDisaster; }

    /** Forget the active disaster (network reset). */
    public void clearCurrentDisaster() { this.currentDisaster = null; }

    /**
     * Apply a disaster with given severity to the road network.
     * Returns a Disaster object with details about affected roads.
     */
    public Disaster applyDisaster(Disaster.Type type, Disaster.Severity severity) {
        graph.resetRoadStatuses();
        Disaster disaster = new Disaster(type, severity, "", "");

        // Determine which terrains each disaster targets and at what intensity
        Map<RoadTerrain, Double> terrainTargets = getTerrainTargets(type, severity);

        // Also consider location terrain
        Map<TerrainType, Double> locationTargets = getLocationTargets(type, severity);

        for (Road road : graph.getAllRoads()) {
            double penalty = 0;
            Road.RoadTerrain rTerrain = road.getRoadTerrain();
            TerrainType srcTerrain = road.getSource().getTerrainType();

            // Check road terrain
            if (terrainTargets.containsKey(rTerrain)) {
                penalty += terrainTargets.get(rTerrain) * severity.getMultiplier();
            }
            // Check source location terrain
            if (locationTargets.containsKey(srcTerrain)) {
                penalty += locationTargets.get(srcTerrain) * severity.getMultiplier();
            }
            // Check destination terrain too
            TerrainType dstTerrain = road.getDestination().getTerrainType();
            if (locationTargets.containsKey(dstTerrain)) {
                penalty += locationTargets.get(dstTerrain) * severity.getMultiplier() * 0.5;
            }

            if (penalty > 0) {
                // Determine road status based on penalty and severity
                if (penalty >= 15 || (severity == Disaster.Severity.CRITICAL && penalty >= 8)) {
                    road.setStatus(Road.Status.BLOCKED);
                    road.setRiskLevel(Road.RiskLevel.VERY_HIGH);
                } else if (penalty >= 10) {
                    road.setStatus(Road.Status.HIGH_RISK);
                    road.setRiskLevel(Road.RiskLevel.HIGH);
                } else if (penalty >= 5) {
                    road.setStatus(Road.Status.DAMAGED);
                    road.setRiskLevel(Road.RiskLevel.MEDIUM);
                } else if (penalty >= 2) {
                    road.setRiskLevel(Road.RiskLevel.MEDIUM);
                }
                road.setDisasterPenalty(penalty);
                disaster.addAffectedRoad(road);
            }
        }

        disaster.setDescription(getDisasterDescription(type, severity, disaster));
        disaster.setRecommendedAction(getRecommendedAction(type, severity));
        this.currentDisaster = disaster;
        return disaster;
    }

    /** Convenience overload: apply disaster with default MEDIUM severity. */
    public Disaster applyDisaster(Disaster.Type type) {
        return applyDisaster(type, Disaster.Severity.MEDIUM);
    }

    /**
     * Returns which road terrains each disaster targets and the base penalty.
     */
    private Map<RoadTerrain, Double> getTerrainTargets(Disaster.Type type, Disaster.Severity severity) {
        Map<RoadTerrain, Double> targets = new HashMap<>();
        switch (type) {
            case FLOOD:
                targets.put(RoadTerrain.BRIDGE, 15.0);
                targets.put(RoadTerrain.COASTAL, 12.0);
                targets.put(RoadTerrain.UNDERGROUND, 10.0);
                targets.put(RoadTerrain.NORMAL, 3.0);
                break;
            case EARTHQUAKE:
                targets.put(RoadTerrain.BRIDGE, 18.0);
                targets.put(RoadTerrain.UNDERGROUND, 12.0);
                targets.put(RoadTerrain.INDUSTRIAL, 10.0);
                targets.put(RoadTerrain.NORMAL, 5.0);
                break;
            case CYCLONE:
                targets.put(RoadTerrain.COASTAL, 18.0);
                targets.put(RoadTerrain.OPEN, 12.0);
                targets.put(RoadTerrain.FORESTED, 8.0);
                break;
            case TSUNAMI:
                targets.put(RoadTerrain.COASTAL, 25.0);
                targets.put(RoadTerrain.BRIDGE, 15.0);
                targets.put(RoadTerrain.UNDERGROUND, 12.0);
                break;
            case LANDSLIDE:
                targets.put(RoadTerrain.MOUNTAIN, 20.0);
                targets.put(RoadTerrain.FORESTED, 8.0);
                break;
            case AVALANCHE:
                targets.put(RoadTerrain.MOUNTAIN, 22.0);
                break;
            case DROUGHT:
                targets.put(RoadTerrain.OPEN, 2.0);
                targets.put(RoadTerrain.FORESTED, 4.0);
                break;
            case THUNDERSTORM:
                targets.put(RoadTerrain.OPEN, 10.0);
                targets.put(RoadTerrain.FORESTED, 8.0);
                targets.put(RoadTerrain.INDUSTRIAL, 5.0);
                break;
            case TORNADO:
                targets.put(RoadTerrain.OPEN, 20.0);
                targets.put(RoadTerrain.NORMAL, 10.0);
                targets.put(RoadTerrain.FORESTED, 8.0);
                break;
            case INDUSTRIAL_FIRE:
                targets.put(RoadTerrain.INDUSTRIAL, 22.0);
                targets.put(RoadTerrain.FORESTED, 6.0);
                break;
            case CHEMICAL_LEAK:
                targets.put(RoadTerrain.INDUSTRIAL, 25.0);
                targets.put(RoadTerrain.UNDERGROUND, 15.0);
                targets.put(RoadTerrain.NORMAL, 5.0);
                break;
            case GAS_LEAK:
                targets.put(RoadTerrain.INDUSTRIAL, 20.0);
                targets.put(RoadTerrain.UNDERGROUND, 12.0);
                break;
            case NUCLEAR_EMERGENCY:
                targets.put(RoadTerrain.INDUSTRIAL, 30.0);
                targets.put(RoadTerrain.NORMAL, 15.0);
                targets.put(RoadTerrain.OPEN, 10.0);
                break;
            case BUILDING_COLLAPSE:
                targets.put(RoadTerrain.UNDERGROUND, 20.0);
                targets.put(RoadTerrain.NORMAL, 10.0);
                targets.put(RoadTerrain.INDUSTRIAL, 8.0);
                break;
            case DAM_FAILURE:
                targets.put(RoadTerrain.BRIDGE, 25.0);
                targets.put(RoadTerrain.UNDERGROUND, 15.0);
                targets.put(RoadTerrain.COASTAL, 12.0);
                targets.put(RoadTerrain.NORMAL, 8.0);
                break;
        }
        return targets;
    }

    /**
     * Returns which location terrains each disaster targets.
     */
    private Map<TerrainType, Double> getLocationTargets(Disaster.Type type, Disaster.Severity severity) {
        Map<TerrainType, Double> targets = new HashMap<>();
        switch (type) {
            case FLOOD:
                targets.put(TerrainType.LOW_LYING, 12.0);
                targets.put(TerrainType.COASTAL, 8.0);
                targets.put(TerrainType.BRIDGE, 10.0);
                break;
            case EARTHQUAKE:
                targets.put(TerrainType.URBAN, 10.0);
                targets.put(TerrainType.BRIDGE, 15.0);
                targets.put(TerrainType.UNDERGROUND, 12.0);
                targets.put(TerrainType.INDUSTRIAL, 8.0);
                break;
            case CYCLONE:
                targets.put(TerrainType.COASTAL, 15.0);
                targets.put(TerrainType.OPEN, 10.0);
                targets.put(TerrainType.FORESTED, 8.0);
                break;
            case TSUNAMI:
                targets.put(TerrainType.COASTAL, 25.0);
                targets.put(TerrainType.LOW_LYING, 15.0);
                break;
            case LANDSLIDE:
                targets.put(TerrainType.MOUNTAIN, 18.0);
                targets.put(TerrainType.FORESTED, 6.0);
                break;
            case AVALANCHE:
                targets.put(TerrainType.MOUNTAIN, 20.0);
                break;
            case DROUGHT:
                targets.put(TerrainType.COMMERCIAL, 3.0);
                targets.put(TerrainType.RESIDENTIAL, 2.0);
                break;
            case THUNDERSTORM:
                targets.put(TerrainType.OPEN, 8.0);
                targets.put(TerrainType.TRANSPORT, 5.0);
                break;
            case TORNADO:
                targets.put(TerrainType.OPEN, 18.0);
                targets.put(TerrainType.RESIDENTIAL, 10.0);
                break;
            case INDUSTRIAL_FIRE:
                targets.put(TerrainType.INDUSTRIAL, 20.0);
                break;
            case CHEMICAL_LEAK:
                targets.put(TerrainType.INDUSTRIAL, 22.0);
                targets.put(TerrainType.URBAN, 8.0);
                break;
            case GAS_LEAK:
                targets.put(TerrainType.INDUSTRIAL, 18.0);
                targets.put(TerrainType.RESIDENTIAL, 10.0);
                break;
            case NUCLEAR_EMERGENCY:
                targets.put(TerrainType.INDUSTRIAL, 28.0);
                targets.put(TerrainType.RESIDENTIAL, 12.0);
                targets.put(TerrainType.URBAN, 10.0);
                break;
            case BUILDING_COLLAPSE:
                targets.put(TerrainType.URBAN, 15.0);
                targets.put(TerrainType.COMMERCIAL, 10.0);
                break;
            case DAM_FAILURE:
                targets.put(TerrainType.LOW_LYING, 20.0);
                targets.put(TerrainType.COASTAL, 12.0);
                targets.put(TerrainType.BRIDGE, 18.0);
                break;
        }
        return targets;
    }

    private String getDisasterDescription(Disaster.Type type, Disaster.Severity severity, Disaster disaster) {
        return severity + " severity " + type.getDisplayName().toLowerCase()
                + " — " + disaster.getAffectedRoads().size() + " roads affected";
    }

    private String getRecommendedAction(Disaster.Type type, Disaster.Severity severity) {
        switch (type) {
            case FLOOD: return "Move to higher ground immediately. Avoid low-lying areas, bridges, and underground passages.";
            case EARTHQUAKE: return "Evacuate open areas away from buildings and bridges. Avoid underground zones.";
            case CYCLONE: return "Seek shelter in sturdy buildings. Stay away from coastal and open areas.";
            case TSUNAMI: return "EVACUATE TO HIGH GROUND IMMEDIATELY. Move away from coast and low-lying areas.";
            case LANDSLIDE: return "Evacuate mountain and sloped areas immediately. Avoid forested routes.";
            case AVALANCHE: return "Move away from mountain roads immediately. Seek low-altitude safe zones.";
            case DROUGHT: return "Conserve water. Prioritize routes to relief centers with water supply.";
            case THUNDERSTORM: return "Seek shelter indoors. Avoid open areas and roads with fallen debris.";
            case TORNADO: return "Seek shelter in lowest interior room. Avoid open roads and exposed areas.";
            case INDUSTRIAL_FIRE: return "Evacuate industrial zones. Avoid areas near factories and fuel storage.";
            case CHEMICAL_LEAK: return "Move upwind immediately. Avoid industrial zones and affected areas completely.";
            case GAS_LEAK: return "Evacuate area immediately. No sparks or flames. Avoid underground routes.";
            case NUCLEAR_EMERGENCY: return "EVACUATE EXCLUSION ZONE IMMEDIATELY. Move perpendicular to wind direction.";
            case BUILDING_COLLAPSE: return "Avoid narrow streets and damaged structures. Evacuate to open safe zones.";
            case DAM_FAILURE: return "Move to high ground immediately downstream. Avoid bridges and low-lying areas.";
            default: return "Move to safety and follow emergency instructions.";
        }
    }

    // ------------------------------------------------------------------
    //  Network state snapshot (Phase 4: simulate on a copy)
    // ------------------------------------------------------------------

    /** The mutable condition of one road at snapshot time. */
    public static class RoadState {
        public final Road.Status status;
        public final Road.RiskLevel riskLevel;
        public final double disasterPenalty;
        public final int trafficLevel;
        public final Road.RoadTerrain roadTerrain;

        RoadState(Road road) {
            this.status = road.getStatus();
            this.riskLevel = road.getRiskLevel();
            this.disasterPenalty = road.getDisasterPenalty();
            this.trafficLevel = road.getTrafficLevel();
            this.roadTerrain = road.getRoadTerrain();
        }
    }

    /** A complete, restorable copy of the network's mutable state. */
    public static class NetworkState {
        public final Map<Road, RoadState> roads;
        public final Disaster disaster;

        NetworkState(Map<Road, RoadState> roads, Disaster disaster) {
            this.roads = roads;
            this.disaster = disaster;
        }
    }

    /**
     * Copy the current road conditions + active disaster. Scenarios run their
     * road changes on the live graph and then restore this snapshot, so the
     * original network is never modified until the user applies a scenario.
     */
    public NetworkState captureNetworkState() {
        Map<Road, RoadState> roads = new HashMap<>();
        for (Road road : graph.getAllRoads()) roads.put(road, new RoadState(road));
        return new NetworkState(roads, currentDisaster);
    }

    /** Put a previously captured network state back exactly as it was. */
    public void restoreNetworkState(NetworkState state) {
        if (state == null) return;
        for (Map.Entry<Road, RoadState> entry : state.roads.entrySet()) {
            Road road = entry.getKey();
            RoadState s = entry.getValue();
            road.setRoadTerrain(s.roadTerrain);
            road.setTrafficLevel(s.trafficLevel);
            road.setRiskLevel(s.riskLevel);
            road.setStatus(s.status);
            road.setDisasterPenalty(s.disasterPenalty);
        }
        this.currentDisaster = state.disaster;
    }

    /** Manually update a road's status; both directions of the undirected road change. */
    public boolean updateRoadStatus(Location source, Location destination, Road.Status newStatus) {
        for (Road road : graph.getAllRoads()) {
            if (road.getSource().equals(source) && road.getDestination().equals(destination)) {
                road.setStatus(newStatus);
                switch (newStatus) {
                    case BLOCKED: road.setRiskLevel(Road.RiskLevel.VERY_HIGH); break;
                    case HIGH_RISK: road.setRiskLevel(Road.RiskLevel.HIGH); break;
                    case DAMAGED: road.setRiskLevel(Road.RiskLevel.MEDIUM); break;
                    default: road.setRiskLevel(Road.RiskLevel.LOW);
                }
                return true;
            }
        }
        return false;
    }
}
