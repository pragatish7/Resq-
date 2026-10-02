# Smart Disaster Response and Evacuation Management System

## 📋 Problem Statement

During natural disasters like floods, fires, and earthquakes, people need to quickly find the **safest and shortest route** to a safe location. Roads may be blocked, damaged, or dangerous. This system helps evacuate people by calculating optimal routes considering road conditions, distances, and risk levels.

## 🎯 Objective

Create a Java-based disaster evacuation system that uses **Data Structures and Algorithms** to:
- Find the safest evacuation route using **Dijkstra's Algorithm**
- Explore disaster-affected areas using **BFS** and **DFS**
- Plan minimum-cost infrastructure using **Kruskal's MST**
- Manage safe zones and road conditions dynamically

## ✨ Features

1. **Safest Route Calculation** - Uses Dijkstra's Algorithm with cost = distance + riskPenalty
2. **BFS Exploration** - Level-wise exploration to find minimum roads between locations
3. **DFS Exploration** - Deep exploration to check connectivity and discover all reachable areas
4. **Minimum Spanning Tree** - Kruskal's algorithm to find minimum-cost network connecting all locations
5. **Disaster Simulation** - Apply floods, fires, or earthquakes to affect road conditions
6. **Dynamic Road Status** - Update road conditions (OPEN, BLOCKED, DAMAGED, HIGH_RISK)
7. **Safe Zone Management** - Track capacity, occupancy, and safety levels
8. **Find Nearest Safe Zone** - Automatically find the closest available safe zone
9. **Visual Graph Display** - Swing GUI with graph visualization
10. **Input Validation & Error Handling** - Graceful handling of edge cases

## 🛠 Technologies Used

- **Java 17+**
- **Java Swing** (GUI)
- **Java Collections Framework** (HashMap, PriorityQueue, Queue, Stack, etc.)
- **Object-Oriented Programming** (Encapsulation, Inheritance, Polymorphism)

## 📊 Data Structures Used

| Data Structure | Where Used | Purpose |
|----------------|-----------|---------|
| **Adjacency List** | Graph.java | Store locations and roads efficiently |
| **Priority Queue** | DijkstraAlgorithm.java | Select minimum-cost location to visit next |
| **HashMap** | Multiple classes | Fast lookup of locations, distances, parents |
| **Queue (LinkedList)** | BFS.java | FIFO processing for level-wise traversal |
| **Stack (ArrayDeque)** | DFS.java | LIFO processing for deep exploration |
| **Union-Find** | KruskalMST.java | Detect cycles while building MST |
| **Set (HashSet)** | Multiple classes | Track visited locations |

## 🔧 Algorithms Used

### 1. Dijkstra's Algorithm
**Purpose:** Find the safest/shortest evacuation route

**How it works in this project:**
- Each road has a **cost** calculated as: `cost = distance + riskPenalty + trafficPenalty`
- Blocked roads have **infinite cost** (excluded from consideration)
- High-risk roads have **higher penalty** so Dijkstra naturally avoids them
- Uses a **Priority Queue** to always process the cheapest unvisited location first
- Maintains a **distance map** and **previous-location map** for path reconstruction

**Time Complexity:** O((V + E) log V)

### 2. Breadth-First Search (BFS)
**Purpose:** Find minimum number of roads (edges) between locations

**How it works:**
- Starts from a source location
- Explores all neighbors at the current level before moving to the next level
- Uses a **Queue** for FIFO processing
- Useful for finding the minimum number of roads to reach a destination
- Also provides level-wise grouped exploration results

**Time Complexity:** O(V + E)

### 3. Depth-First Search (DFS)
**Purpose:** Explore all connected disaster-affected regions

**How it works:**
- Starts from a source location
- Explores as deep as possible along each branch before backtracking
- Uses a **Stack** (or recursion) for LIFO processing
- Checks **connectivity** between any two locations
- Finds **connected components** (isolated areas after roads are blocked)

**Time Complexity:** O(V + E)

### 4. Kruskal's Minimum Spanning Tree (MST)
**Purpose:** Connect all important locations with minimum total road distance

**How it works:**
1. Sort all edges (roads) by distance
2. Process edges from shortest to longest
3. Add edge to MST if it doesn't create a cycle (using Union-Find)
4. Stop when V-1 edges are added (MST complete)

**Uses Union-Find** (Disjoint Set) data structure with:
- **Path Compression** - Flattens tree structure for faster future queries
- **Union by Rank** - Keeps tree balanced

**Time Complexity:** O(E log E)

## 📁 Project Architecture

```
SmartDisasterResponse/
│
├── src/
│   ├── model/
│   │   ├── Location.java        # Location/vertex in the graph
│   │   ├── Road.java            # Road/edge with distance, status, risk
│   │   ├── SafeZone.java        # Safe evacuation zone with capacity
│   │   ├── Disaster.java        # Disaster event affecting roads
│   │   └── Graph.java           # Adjacency list graph representation
│   │
│   ├── algorithms/
│   │   ├── DijkstraAlgorithm.java   # Shortest/safest path algorithm
│   │   ├── BFS.java                 # Breadth-First Search
│   │   ├── DFS.java                 # Depth-First Search
│   │   ├── KruskalMST.java          # Minimum Spanning Tree
│   │   └── UnionFind.java           # Disjoint Set for MST
│   │
│   ├── service/
│   │   ├── DisasterService.java     # Disaster effects on roads
│   │   ├── RouteService.java        # Route calculation service
│   │   └── SafeZoneService.java     # Safe zone management
│   │
│   ├── ui/
│   │   ├── MainFrame.java           # Main application window
│   │   ├── RoutePanel.java          # Input controls & output display
│   │   └── GraphPanel.java          # Graph visualization
│   │
│   └── Main.java                    # Application entry point
│
├── tests/
│   └── AlgorithmTests.java          # Comprehensive test suite
│
└── README.md
```

## 🚀 How to Run

### Prerequisites
- Java 17 or newer

### Steps

1. **Navigate to the project directory:**
   ```bash
   cd SmartDisasterResponse
   ```

2. **Create output directory:**
   ```bash
   mkdir -p bin
   ```

3. **Compile all Java files:**
   ```bash
   javac -d bin src/model/*.java src/algorithms/*.java src/service/*.java src/ui/*.java src/Main.java
   ```

4. **Run the application:**
   ```bash
   java -cp bin Main
   ```

5. **Run tests:**
   ```bash
   javac -d bin src/model/*.java src/algorithms/*.java src/service/*.java tests/AlgorithmTests.java
   java -cp bin AlgorithmTests
   ```

### Quick Run (One-liner)
```bash
cd SmartDisasterResponse && mkdir -p bin && javac -d bin src/model/*.java src/algorithms/*.java src/service/*.java src/ui/*.java src/Main.java && java -cp bin Main
```

## 🖥 GUI Features

The application provides a Swing-based GUI with:

### Input Section
- **Current Location** dropdown - Select your starting position
- **Disaster Type** dropdown - Flood, Fire, or Earthquake
- **Destination** dropdown - Select target location
- **Nearest Safe Zone** checkbox - Auto-find closest available safe zone

### Action Buttons
- **Find Safest Route** - Calculate evacuation route using Dijkstra
- **Find Nearest Safe Zone** - Find closest available safe zone
- **Run BFS** - Breadth-First Search traversal
- **Run DFS** - Depth-First Search traversal
- **Generate MST** - Minimum Spanning Tree using Kruskal's
- **View Roads** - Display all road statuses
- **Update Road Status** - Manually change road conditions
- **Reset** - Clear all disaster effects

### Output Display
- Algorithm used
- Route with step-by-step directions
- Total distance and cost
- Risk level assessment
- Status warnings

### Graph Visualization
- Locations as colored nodes
- Roads as connecting lines
- Color-coded: Blue (normal), Green (safe zone), Orange (route), Red (blocked)
- Dashed lines for blocked roads

## 📝 Example Output

### Finding Safest Route
```
═══════════════════════════════════════════
  DIJKSTRA'S SHORTEST/SAFEST ROUTE
═══════════════════════════════════════════

Algorithm: Dijkstra's Algorithm
Disaster Type: Flood
Source: Residential Area
Destination: Relief Camp A

Status: ✅ SAFE ROUTE AVAILABLE

Recommended Route:
───────────────────────────────────────────
  Residential Area
    ↓
  Junction A
    ↓
  Junction B
    ↓
  Relief Camp A
───────────────────────────────────────────
Total Distance: 10.0 km
Total Cost: 15.0
Risk Level: LOW
```

### BFS Traversal
```
═══════════════════════════════════════════
  BFS (BREADTH-FIRST SEARCH)
═══════════════════════════════════════════

Algorithm: Breadth-First Search (BFS)
Starting Location: Residential Area

Level-wise Exploration:
  Level 0: Residential Area
  Level 1: Junction A, School
  Level 2: Market, Junction B, Hospital
  ...
```

### MST Generation
```
═══════════════════════════════════════════
  MINIMUM SPANNING TREE (KRUSKAL'S)
═══════════════════════════════════════════

Selected Edges:
  1. Junction A -- 2.5 km -- School
  2. School -- 2.0 km -- Hospital
  ...

Total MST Cost: 25.0 km
```

## 🧪 Test Results

The project includes a comprehensive test suite (`AlgorithmTests.java`) with 33 test cases:

- **Dijkstra Tests (15):** Normal route, blocked road, high-risk road, no route, same source/destination
- **BFS Tests (3):** Traversal, minimum roads
- **DFS Tests (6):** Traversal, connectivity, connected components
- **MST Tests (3):** Basic MST, connectivity verification
- **Safe Zone Tests (3):** Availability checking
- **Disaster Tests (3):** Flood, fire, earthquake effects

All tests pass successfully ✅

## 🔮 Future Improvements

1. **Real Map Integration** - Use actual geographical coordinates with mapping APIs
2. **A* Algorithm** - Add heuristic-based pathfinding for better performance
3. **Multi-disaster Support** - Handle multiple simultaneous disasters
4. **Real-time Updates** - WebSocket-based live road condition updates
5. **Population Data** - Consider population density in evacuation planning
6. **Vehicle Simulation** - Model traffic and vehicle capacity
7. **Historical Data** - Learn from past disaster patterns
8. **Mobile App** - Android/iOS companion app
9. **Database Integration** - Persistent storage for road and location data
10. **API Endpoints** - REST API for external system integration

## 📚 Algorithm Explanation for Viva

### Why Dijkstra's?
Dijkstra's algorithm is perfect for finding the **optimal evacuation route** because it:
- Considers both distance AND risk (weighted cost)
- Naturally avoids blocked roads (infinite cost)
- Guarantees the minimum-cost path
- Works efficiently with a Priority Queue

### Why BFS?
BFS is ideal for finding the **minimum number of roads** to traverse because:
- It explores level by level
- The first time it reaches a destination, it's guaranteed to be via the shortest path (in terms of edges)
- Useful for emergency responders to understand distance levels

### Why DFS?
DFS is useful for **exploring all connected areas** because:
- It goes deep into one branch before backtracking
- Can find all reachable locations from a starting point
- Checks if two locations are connected
- Identifies isolated areas after road blockages

### Why Kruskal's MST?
Kruskal's MST helps in **infrastructure planning** because:
- It finds the minimum-cost network connecting all locations
- Useful for planning emergency communication routes
- Demonstrates the use of Union-Find data structure
- Shows greedy algorithm approach

## 👥 Contributors

This project demonstrates the practical application of fundamental Data Structures and Algorithms concepts in solving real-world disaster management problems.

## 📄 License

This is an educational project for college coursework purposes.
