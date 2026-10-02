# 🚨 Smart Disaster Response and Evacuation Management System

A Java-based disaster management and evacuation system designed to help emergency teams identify safe and efficient routes during disaster situations.

The system uses **graph-based algorithms** to model roads, locations, blocked paths, and evacuation routes, helping users make faster and more informed routing decisions during emergencies.

## ✨ Features

* 🗺️ **Interactive Disaster Map** – Visualize locations, roads, and emergency routes.
* 🚑 **Emergency Route Planning** – Find suitable routes between locations.
* 🚧 **Blocked Road Detection** – Identify and handle roads that are unavailable during a disaster.
* 🔄 **Route Comparison** – Compare available routes based on distance and accessibility.
* 📍 **Evacuation Management** – Support efficient movement of people toward safe locations.
* 📊 **Graph Algorithm Visualization** – Apply graph algorithms to solve routing and connectivity problems.

## 🧠 Algorithms Used

The project demonstrates several important graph algorithms:

### Dijkstra's Algorithm

Used to find the shortest path between locations based on road distance or travel cost.

### BFS – Breadth-First Search

Used to explore connected locations and support route traversal.

### DFS – Depth-First Search

Used for graph traversal and exploring connected areas.

### MST – Minimum Spanning Tree

Used to determine an efficient network connecting multiple locations with minimum total cost.

## 🛠️ Technologies Used

* **Java**
* **Java Swing / GUI**
* **Graph Data Structures**
* **Dijkstra's Algorithm**
* **BFS**
* **DFS**
* **Minimum Spanning Tree**
* **Object-Oriented Programming**

## 📂 Project Structure

```text
Smart-Disaster-Response/
│
├── src/
│   ├── Main.java
│   ├── RouteService.java
│   ├── Graph.java
│   └── ...
│
├── resources/
│   └── ...
│
├── README.md
└── ...
```

> The exact structure may vary depending on the project version.

## ⚙️ How to Run

### 1. Clone the repository

```bash
git clone https://github.com/YOUR_USERNAME/YOUR_REPOSITORY.git
```

### 2. Open the project

Open the project in **IntelliJ IDEA, Eclipse, or VS Code** with Java support.

### 3. Compile and run

Make sure **Java 17 or later** is installed.

```bash
javac Main.java
java Main
```

If you're using an IDE, simply run the `Main` class.

## 🔄 System Workflow

```text
User
  ↓
Select Source & Destination
  ↓
Load Disaster / Road Conditions
  ↓
Build Graph
  ↓
Apply Routing Algorithm
  ↓
Calculate Available Routes
  ↓
Check Blocked Roads
  ↓
Display Safe / Alternative Route
  ↓
Evacuation Decision
```

## 🎯 Objective

The main objective of this project is to demonstrate how **graph algorithms and Java-based software systems** can be applied to disaster management and emergency evacuation scenarios.

The system provides a simulation environment where routes can be analyzed under changing road and disaster conditions.

## 👥 Project Team

* **Pr**
