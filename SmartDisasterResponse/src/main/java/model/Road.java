package model;

/**
 * Represents a road (edge) connecting two locations in the disaster area graph.
 * Each road has a distance, risk level, terrain, status, and traffic level.
 * Cost is dynamically calculated: cost = distance + riskPenalty + terrainPenalty + trafficPenalty
 */
public class Road {
    public enum Status { OPEN, BLOCKED, DAMAGED, HIGH_RISK }

    public enum RiskLevel {
        LOW(0), MEDIUM(5), HIGH(10), VERY_HIGH(20);
        private final int penalty;
        RiskLevel(int penalty) { this.penalty = penalty; }
        public int getPenalty() { return penalty; }
    }

    /**
     * Road terrain — influences which disasters affect this road.
     */
    public enum RoadTerrain {
        NORMAL, BRIDGE, COASTAL, MOUNTAIN, UNDERGROUND, INDUSTRIAL, FORESTED, OPEN
    }

    private Location source;
    private Location destination;
    private double distance;
    private RiskLevel riskLevel;
    private Status status;
    private int trafficLevel;
    private RoadTerrain roadTerrain;
    private double disasterPenalty; // Extra penalty added by active disaster

    /**
     * The same physical road travelled in the opposite direction.
     * A road is undirected, so its condition (status, risk, disaster penalty,
     * traffic, terrain) must be identical in both directions; the two directed
     * Road objects are linked as twins and keep that state in sync.
     */
    private Road twin;

    public Road(Location source, Location destination, double distance,
                RiskLevel riskLevel, Status status, int trafficLevel,
                RoadTerrain roadTerrain) {
        this.source = source;
        this.destination = destination;
        this.distance = distance;
        this.riskLevel = riskLevel;
        this.status = status;
        this.trafficLevel = trafficLevel;
        this.roadTerrain = roadTerrain;
        this.disasterPenalty = 0;
    }

    public Road(Location source, Location destination, double distance) {
        this(source, destination, distance, RiskLevel.LOW, Status.OPEN, 0, RoadTerrain.NORMAL);
    }

    /**
     * Links this directed road with its opposite direction so both share one
     * condition. Called when the graph stores the reverse edge of a road.
     */
    public void linkTwin(Road other) {
        this.twin = other;
        if (other != null) other.twin = this;
    }

    public Road getTwin() { return twin; }

    /**
     * Calculate the cost of traveling this road.
     * cost = distance + riskPenalty + disasterPenalty + trafficPenalty
     * Blocked roads return Double.MAX_VALUE.
     */
    public double getCost() {
        if (status == Status.BLOCKED) return Double.MAX_VALUE;
        double cost = distance + riskLevel.getPenalty() + disasterPenalty;
        if (status == Status.DAMAGED) cost += 5;
        if (status == Status.HIGH_RISK) cost += riskLevel.getPenalty();
        cost += trafficLevel * 0.3;
        return cost;
    }

    public boolean isBlocked() { return status == Status.BLOCKED; }

    // Getters and Setters
    public Location getSource() { return source; }
    public Location getDestination() { return destination; }
    public double getDistance() { return distance; }
    public RiskLevel getRiskLevel() { return riskLevel; }
    // Condition setters write through to the twin so both directions of an
    // undirected road always report the same state.
    public void setRiskLevel(RiskLevel riskLevel) {
        this.riskLevel = riskLevel;
        if (twin != null) twin.riskLevel = riskLevel;
    }
    public Status getStatus() { return status; }
    public void setStatus(Status status) {
        this.status = status;
        if (twin != null) twin.status = status;
    }
    public int getTrafficLevel() { return trafficLevel; }
    public void setTrafficLevel(int trafficLevel) {
        this.trafficLevel = trafficLevel;
        if (twin != null) twin.trafficLevel = trafficLevel;
    }
    public RoadTerrain getRoadTerrain() { return roadTerrain; }
    public void setRoadTerrain(RoadTerrain roadTerrain) {
        this.roadTerrain = roadTerrain;
        if (twin != null) twin.roadTerrain = roadTerrain;
    }
    public double getDisasterPenalty() { return disasterPenalty; }
    public void setDisasterPenalty(double disasterPenalty) {
        this.disasterPenalty = disasterPenalty;
        if (twin != null) twin.disasterPenalty = disasterPenalty;
    }

    @Override
    public String toString() {
        return source.getName() + " --(" + distance + "km, " + status + ", " + riskLevel + ")--> " + destination.getName();
    }
}
