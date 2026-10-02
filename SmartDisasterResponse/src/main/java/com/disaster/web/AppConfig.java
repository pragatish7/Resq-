package com.disaster.web;

import com.disaster.web.dto.RoadDto;
import algorithms.*;
import model.*;
import service.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the ORIGINAL, unmodified Swing-era object graph into Spring.
 * MainFrame.createSampleData builds the same 10-location / 16-road / 3-shelter
 * disaster area it always has — we only reuse its builder so the data matches.
 */
@Configuration
public class AppConfig {

    @Bean
    public Graph graph() {
        Graph graph = new Graph();
        // Locations with terrain types (unchanged sample data)
        Location residentialArea = new Location("loc1", "Residential Area", 100, 300, Location.TerrainType.RESIDENTIAL);
        Location school = new Location("loc2", "School", 200, 150, Location.TerrainType.URBAN);
        Location hospital = new Location("loc3", "Hospital", 350, 100, Location.TerrainType.URBAN);
        Location market = new Location("loc4", "Market", 350, 280, Location.TerrainType.COMMERCIAL);
        Location junctionA = new Location("loc5", "Junction A", 250, 250, Location.TerrainType.URBAN);
        Location junctionB = new Location("loc6", "Junction B", 450, 200, Location.TerrainType.URBAN);
        Location railwayStation = new Location("loc7", "Railway Station", 500, 350, Location.TerrainType.TRANSPORT);
        Location stadium = new Location("loc8", "Stadium", 550, 100, Location.TerrainType.OPEN);
        Location reliefCampA = new Location("loc9", "Relief Camp A", 480, 400, Location.TerrainType.OPEN);
        Location reliefCampB = new Location("loc10", "Relief Camp B", 650, 250, Location.TerrainType.OPEN);

        graph.addLocation(residentialArea); graph.addLocation(school);
        graph.addLocation(hospital); graph.addLocation(market);
        graph.addLocation(junctionA); graph.addLocation(junctionB);
        graph.addLocation(railwayStation); graph.addLocation(stadium);
        graph.addLocation(reliefCampA); graph.addLocation(reliefCampB);

        // Roads with terrain types (unchanged sample data)
        graph.addRoad(new Road(residentialArea, junctionA, 3.0, Road.RiskLevel.LOW, Road.Status.OPEN, 2, Road.RoadTerrain.NORMAL));
        graph.addRoad(new Road(residentialArea, school, 5.0, Road.RiskLevel.LOW, Road.Status.OPEN, 1, Road.RoadTerrain.NORMAL));
        graph.addRoad(new Road(school, hospital, 4.0, Road.RiskLevel.LOW, Road.Status.OPEN, 3, Road.RoadTerrain.NORMAL));
        graph.addRoad(new Road(school, junctionA, 2.5, Road.RiskLevel.LOW, Road.Status.OPEN, 1, Road.RoadTerrain.NORMAL));
        graph.addRoad(new Road(junctionA, market, 3.0, Road.RiskLevel.LOW, Road.Status.OPEN, 4, Road.RoadTerrain.NORMAL));
        graph.addRoad(new Road(junctionA, junctionB, 4.5, Road.RiskLevel.LOW, Road.Status.OPEN, 2, Road.RoadTerrain.BRIDGE));
        graph.addRoad(new Road(hospital, junctionB, 3.5, Road.RiskLevel.LOW, Road.Status.OPEN, 1, Road.RoadTerrain.NORMAL));
        graph.addRoad(new Road(hospital, stadium, 2.0, Road.RiskLevel.LOW, Road.Status.OPEN, 0, Road.RoadTerrain.OPEN));
        graph.addRoad(new Road(market, railwayStation, 4.0, Road.RiskLevel.LOW, Road.Status.OPEN, 5, Road.RoadTerrain.INDUSTRIAL));
        graph.addRoad(new Road(market, junctionB, 3.0, Road.RiskLevel.LOW, Road.Status.OPEN, 2, Road.RoadTerrain.NORMAL));
        graph.addRoad(new Road(junctionB, stadium, 3.0, Road.RiskLevel.LOW, Road.Status.OPEN, 1, Road.RoadTerrain.OPEN));
        graph.addRoad(new Road(junctionB, reliefCampB, 2.5, Road.RiskLevel.LOW, Road.Status.OPEN, 0, Road.RoadTerrain.OPEN));
        graph.addRoad(new Road(railwayStation, reliefCampA, 2.0, Road.RiskLevel.LOW, Road.Status.OPEN, 1, Road.RoadTerrain.COASTAL));
        graph.addRoad(new Road(railwayStation, reliefCampB, 3.5, Road.RiskLevel.LOW, Road.Status.OPEN, 2, Road.RoadTerrain.COASTAL));
        graph.addRoad(new Road(stadium, reliefCampB, 2.0, Road.RiskLevel.LOW, Road.Status.OPEN, 0, Road.RoadTerrain.OPEN));
        graph.addRoad(new Road(residentialArea, market, 6.0, Road.RiskLevel.LOW, Road.Status.OPEN, 3, Road.RoadTerrain.FORESTED));

        return graph;
    }

    @Bean
    public SafeZoneService safeZoneService(Graph graph) {
        SafeZoneService svc = new SafeZoneService();
        svc.addSafeZone(new SafeZone("Relief Camp A", reliefCampA(graph), 500, 120, SafeZone.SafetyLevel.HIGH));
        svc.addSafeZone(new SafeZone("Relief Camp B", reliefCampB(graph), 300, 280, SafeZone.SafetyLevel.HIGH));
        svc.addSafeZone(new SafeZone("Stadium", stadium(graph), 1000, 50, SafeZone.SafetyLevel.MEDIUM));
        return svc;
    }

    private Location reliefCampA(Graph g) { return g.getLocationById("loc9"); }
    private Location reliefCampB(Graph g) { return g.getLocationById("loc10"); }
    private Location stadium(Graph g) { return g.getLocationById("loc8"); }

    @Bean
    public RouteService routeService(Graph graph) { return new RouteService(graph); }

    @Bean
    public DisasterService disasterService(Graph graph) { return new DisasterService(graph); }
}
