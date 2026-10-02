package model;

/**
 * Represents a location (vertex/node) in the disaster area graph.
 * Each location has a unique ID, name, coordinates, and terrain type.
 */
public class Location {
    /**
     * Terrain types that influence disaster-specific road risk.
     */
    public enum TerrainType {
        COASTAL,    // Near sea — tsunami, cyclone risk
        MOUNTAIN,   // Sloped/hilly — landslide, avalanche risk
        LOW_LYING,  // Below sea level — flood risk
        INDUSTRIAL, // Factories — fire, chemical, gas leak risk
        URBAN,      // Dense buildings — earthquake collapse risk
        BRIDGE,     // Over water — flood, earthquake risk
        UNDERGROUND,// Below ground — flood, collapse risk
        OPEN,       // Exposed area — tornado, thunderstorm risk
        FORESTED,   // Near trees — wildfire, thunderstorm risk
        RESIDENTIAL, // Housing area — general risk
        COMMERCIAL,  // Shops/markets — general risk
        TRANSPORT    // Railways, bus stands — infrastructure risk
    }

    private String id;
    private String name;
    private int x;
    private int y;
    private TerrainType terrainType;

    public Location(String id, String name, int x, int y, TerrainType terrainType) {
        this.id = id;
        this.name = name;
        this.x = x;
        this.y = y;
        this.terrainType = terrainType;
    }

    public Location(String id, String name, int x, int y) {
        this(id, name, x, y, TerrainType.URBAN);
    }

    public Location(String id, String name) {
        this(id, name, 0, 0, TerrainType.URBAN);
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public int getX() { return x; }
    public int getY() { return y; }
    public TerrainType getTerrainType() { return terrainType; }
    public void setTerrainType(TerrainType terrainType) { this.terrainType = terrainType; }

    @Override
    public String toString() { return name; }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        Location location = (Location) obj;
        return id.equals(location.id);
    }

    @Override
    public int hashCode() { return id.hashCode(); }
}
