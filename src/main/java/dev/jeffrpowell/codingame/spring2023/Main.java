package dev.jeffrpowell.codingame.spring2023;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Scanner;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javafx.geometry.Point3D;

/**
 * Notes on the hexagonal coordinate system of this game and my implementation
 * 
 * The game uses pointy-corner-up orientation for the hexagon orientation.
 * Any time the game rotates around, it always starts in the hex to the right, followed by a counterclockwise rotation.
 * The game considers all the hexes with distance = 0 from the center, then distance = 1, then distance = 2, etc. Always following the rotation pattern mentioned above ^.
 * Each time the game decides to place a hex in the grid, it assigns the next monotonically increasing hex id to it, starting with id 0.
 * Once a hex is placed, the next hex to be placed MUST be the hex that is mirror-imaged over the center from the last hex that was placed.
 * After placing the mirror-image hex, the rotation and deciding logic proceeds from the originally placed hex. A couple examples:
 *    5     3            2    -1
 * 2     0     1      1    -1     0
 *    4     6           -1     3
 * The game feeds you the hex grid in ascending hex-id order. This means that you'll receive them in ascending distance-from-0 order too.
 * Furthermore, when you receive a hex, you receive its neighbor hexes in the same rotation pattern described above ^.
 * Given this, you can easily make an id-to-cube-coordinate mapping as you iteratively receive the hexes at the start.
 * See https://www.redblobgames.com/grids/hexagons/#coordinates-cube for the hexagonal cube-coordinate system that this implementation leverages.
 *     -r+s   +q-r   
 * -q+s     0     +q-s
 *     -q+r   +r-s   
 * 
 * STRATEGY & OPTIMIZATION TODOS
 * - Need to calculate min-span tree to create more of a highway network than a mass flood to reach all the resources
 * - When there are multiple paths to a target, pick the one that is closest to other resources
 * - Make sure I don't target a resource that is further away than I have number of ants (maybe even x2?)
 * - Take into account what my win-condition score is and leverage it to take shortcuts to closer crystals
 */
public class Main {
    public static void main(String args[]) {
        Scanner in = new Scanner(System.in);
        // while (in.hasNextLine()) {
        //     System.err.println(in.nextLine());
        // }
        int numberOfCells = in.nextInt(); // amount of hexagonal cells in this map
        HexIndices indices = generateHexGrid(in, numberOfCells);
        int numberOfBases = in.nextInt();
        List<Point3D> bases = new ArrayList<>();
        for (int i = 0; i < numberOfBases; i++) {
            int myBaseIndex = in.nextInt();
            bases.add(indices.idToHexMap.get(myBaseIndex).pt);
        }
        List<Point3D> enemyBases = new ArrayList<>();
        for (int i = 0; i < numberOfBases; i++) {
            int oppBaseIndex = in.nextInt();
            enemyBases.add(indices.idToHexMap.get(oppBaseIndex).pt);
        }
        Game g = new Game(numberOfCells, bases, enemyBases, indices);
        // game loop
        while (true) {
            System.out.println(g.gameLoop(in));
        }
    }

    private static class HexIndices{
        Map<Point3D, Hex> ptToHexMap;
        Map<Integer, Hex> idToHexMap;

        public HexIndices(Map<Point3D, Hex> ptToHexMap, Map<Integer, Hex> idToHexMap) {
            this.ptToHexMap = ptToHexMap;
            this.idToHexMap = idToHexMap;
        }

        public void putHex(Point3D pt, Hex h) {
            idToHexMap.put(h.id, h);
            ptToHexMap.put(pt, h);
        }

        public void putHex(int id, Hex h) {
            idToHexMap.put(id, h);
            ptToHexMap.put(h.pt, h);
        }

        public Hex getHex(Point3D pt) {
            return ptToHexMap.get(pt);
        }

        public Hex getHex(int id) {
            return idToHexMap.get(id);
        }
    }
    private static HexIndices generateHexGrid(Scanner in, int numberOfCells) {
        Map<Integer, HexBuilder> hexBuilderMap = new HashMap<>();
        for (int i = 0; i < numberOfCells; i++) {
            int type = in.nextInt(); // 0 for empty, 1 for eggs, 2 for crystal
            int initialResources = in.nextInt(); // the initial amount of eggs/crystals on this cell
            int neigh0 = in.nextInt(); // the index of the neighbouring cell for each direction
            int neigh1 = in.nextInt();
            int neigh2 = in.nextInt();
            int neigh3 = in.nextInt();
            int neigh4 = in.nextInt();
            int neigh5 = in.nextInt();
            HexBuilder builder = new HexBuilder(i, type, initialResources, neigh0, neigh1, neigh2, neigh3, neigh4, neigh5);
            if (i == 0) {
                builder.pt = new Point3D(0, 0, 0);
            }
            hexBuilderMap.put(i, builder);
        }
        for (int i = 0; i < numberOfCells; i++) {
            hexBuilderMap.get(i).setPts(hexBuilderMap);
        }
        if (hexBuilderMap.get(1).needsNeighborPtsStill()) {
            //edge case where hex 0 was not in the center and thus separated from hex 1
            //one more pump should do the trick
            for (int i = 0; i < numberOfCells; i++) {
                hexBuilderMap.get(i).setPts(hexBuilderMap);
            }
        }
        Map<Point3D, Hex> ptToHexMap = new HashMap<>();
        Map<Integer, Hex> idToHexMap = new HashMap<>();
        for (HexBuilder builder : hexBuilderMap.values()) {
            Hex h = new Hex(builder.id, builder.pt, builder.type, builder.initialResources, 0, 0);
            ptToHexMap.put(builder.pt, h);
            idToHexMap.put(builder.id, h);
            for (int i = 0; i < builder.neighbor.length; i++) {
                if (builder.neighbor[i] == -1) {
                    Hex emptyHex = new Hex(-1, builder.neighborPts.get(i), -1, 0, 0 ,0);
                    ptToHexMap.put(builder.neighborPts.get(i), emptyHex);    
                }
            }
        }
        return new HexIndices(ptToHexMap, idToHexMap);
    }

    private static class HexBuilder{
        int id;
        int type;
        int initialResources;
        int[] neighbor = new int[6];
        List<Point3D> neighborPts;
        Point3D pt;

        public HexBuilder(int id, int type, int initialResources, int... neighbors) {
            this.id = id;
            this.type = type;
            this.initialResources = initialResources;
            this.neighbor = neighbors;
            this.neighborPts = Stream.generate(()->(Point3D)null).limit(6).collect(Collectors.toList());
            this.pt = null;
        }

        public boolean needsNeighborPtsStill() {
            return neighborPts.stream().anyMatch(pt -> pt == null);
        }

        public void setPts(Map<Integer, HexBuilder> hexBuilderMap) {
            for (int i = 0; i < neighbor.length; i++) {
                if (hexBuilderMap.containsKey(neighbor[i]) && hexBuilderMap.get(neighbor[i]).pt != null) {
                    neighborPts.set(i, hexBuilderMap.get(neighbor[i]).pt);
                    if (pt == null) {
                        pt = getPtFromNeighborPt(hexBuilderMap.get(neighbor[i]).pt, i);
                    }
                }
            }
            if (pt == null) {
                return;
            }
            for (int i = 0; i < neighbor.length; i++) {
                if (neighborPts.get(i) == null) {
                    switch (i) {
                        case 0:
                            neighborPts.set(i, getPtFromNeighborPt(pt, 3));
                            break;
                        case 1:
                            neighborPts.set(i, getPtFromNeighborPt(pt, 4));
                            break;
                        case 2:
                            neighborPts.set(i, getPtFromNeighborPt(pt, 5));
                            break;
                        case 3:
                            neighborPts.set(i, getPtFromNeighborPt(pt, 0));
                            break;
                        case 4:
                            neighborPts.set(i, getPtFromNeighborPt(pt, 1));
                            break;
                        default:
                            neighborPts.set(i, getPtFromNeighborPt(pt, 2));
                            break;
                    }
                }
                if (neighbor[i] != -1) {
                    hexBuilderMap.get(neighbor[i]).pt = neighborPts.get(i);
                }
            }
        }

        private Point3D getPtFromNeighborPt(Point3D neighbor, int neighborRelativePositionIndex) {
            switch (neighborRelativePositionIndex) {
                case 0:
                    return neighbor.add(-1, 0, 1);
                case 1:
                    return neighbor.add(-1, 1, 0);
                case 2:
                    return neighbor.add(0, 1, -1);
                case 3:
                    return neighbor.add(1, 0, -1);
                case 4:
                    return neighbor.add(1, -1, 0);
                default:
                    return neighbor.add(0, -1, 1);
            }
        }
    }

    public static class Hex{
        int id;
        Point3D pt;
        int type;
        int resources;
        int myAnts;
        int enemyAnts;
        
        public Hex(int id, Point3D pt, int type, int resources, int myAnts, int enemyAnts) {
            this.id = id;
            this.pt = pt;
            this.type = type;
            this.resources = resources;
            this.myAnts = myAnts;
            this.enemyAnts = enemyAnts;
        }
        public int id() {
            return this.id;
        }
        public Point3D pt() {
            return this.pt;
        }
    }

    private static class Game{
        private final int numCells;
        private final List<Point3D> bases;
        private final List<Point3D> enemyBases;
        private final HexIndices indices;
        private List<Integer> eggSpots;
        private List<Integer> crystalSpots;
        private Map<Integer, Integer> crystalSpotsCoveredByEnemies;
        private boolean earlyGame;
        private Collection<Beacon> lastBeacons;
        private Map<Integer, Beacon> beacons;
        private int enemyAnts;
        private int myAnts;
        private int startingAnts;
        private boolean earlyFloodThresholdSet;
        private int earlyFloodDistanceThreshold;

        public Game(int numCells, List<Point3D> bases, List<Point3D> enemyBases, HexIndices indices) {
            this.numCells = numCells;
            this.bases = bases;
            this.enemyBases = enemyBases;
            this.indices = indices;
            this.eggSpots = indices.idToHexMap.values().stream().filter(h -> h.type == 1).map(Hex::id).collect(Collectors.toList());
            this.crystalSpots = indices.idToHexMap.values().stream().filter(h -> h.type == 2).map(Hex::id).collect(Collectors.toList());
            this.crystalSpotsCoveredByEnemies = new HashMap<>();
            this.beacons = new HashMap<>();
            this.earlyGame = true;
            this.lastBeacons = new ArrayList<>();
            this.startingAnts = 0;
            this.earlyFloodThresholdSet = false;
            this.earlyFloodDistanceThreshold = 2;
        }
        
        public String gameLoop(Scanner in) {
            clearLastTurnState();
            for (int i = 0; i < numCells; i++) {
                int resources = in.nextInt(); // the current amount of eggs/crystals on this cell
                int myAntsOnCell = in.nextInt(); // the amount of your ants on this cell
                int oppAntsOnCell = in.nextInt(); // the amount of opponent ants on this cell
                this.myAnts += myAntsOnCell;
                this.enemyAnts += oppAntsOnCell;
                Hex h = indices.getHex(i);
                indices.putHex(i, new Hex(h.id, h.pt, h.type, resources, myAntsOnCell, oppAntsOnCell));
                if (h.type == 1 && resources > 0) {
                    eggSpots.add(h.id);
                }
                else if (h.type == 2 && resources > 0) {
                    crystalSpots.add(h.id);
                    crystalSpotsCoveredByEnemies.put(h.id, oppAntsOnCell);
                }
            }
            if (startingAnts == 0) {
                startingAnts = myAnts;
            }
            decideBeacons();
            if (beacons.isEmpty()) {
                return "WAIT";
            }
            lastBeacons = beacons.values();
            return beacons.values().stream().map(b -> "BEACON "+b.id+" "+b.strength).collect(Collectors.joining(";"));
        }

        private void clearLastTurnState() {
            for (Integer id : eggSpots) {
                Hex h = indices.getHex(id);
                indices.putHex(id, new Hex(h.id, h.pt, h.type, 0, 0, 0));
            }
            for (Integer id : crystalSpots) {
                Hex h = indices.getHex(id);
                indices.putHex(id, new Hex(h.id, h.pt, h.type, 0, 0, 0));
            }
            eggSpots.clear();
            crystalSpots.clear();
            crystalSpotsCoveredByEnemies.clear();
            beacons.clear();
            myAnts = 0;
            enemyAnts = 0;
        }
        
        private static class Beacon{
            int id;
            int strength;
            public Beacon(int id, int strength) {
                this.id = id;
                this.strength = strength;
            }
        }

        private void addBeacon(Beacon b) {
            beacons.merge(b.id, b, (b1, b2) -> new Beacon(b1.id, 1));
        }

        private void decideBeacons() {
            if (needToShortCircuit()) {
                earlyGame = false;
            }
            List<Point3D> targetPts = earlyGame ? earlyFlood() : lateFlood();
            // findMinimumSpanningTree(targetPts, targetPts)
        }

        private List<Point3D> earlyFlood() {
            Map<Integer, List<ClosestBase>> pathToAllEggs = eggSpots.stream()
                .map(eggId -> getClosestBaseToTarget(bases, indices.getHex(eggId).pt))
                .collect(Collectors.groupingBy(cb -> cb.bestPath.history.size() - 1));
            Map<Integer, List<ClosestBase>> pathToAllCrystals = crystalSpots.stream()
                .map(crystalId -> getClosestBaseToTarget(bases, indices.getHex(crystalId).pt))
                .collect(Collectors.groupingBy(cb -> cb.bestPath.history.size() - 1));
            int closestEggDistance = pathToAllEggs.keySet().stream().min(Comparator.naturalOrder()).get();
            if (!earlyFloodThresholdSet) {
                earlyFloodDistanceThreshold = Math.max(2, closestEggDistance);
                earlyFloodThresholdSet = true;
            }
            if (closestEggDistance > earlyFloodDistanceThreshold || iHaveEnoughAnts()) {
                earlyGame = false;
                lateFlood();
            }
            else {
                for (int i = 1; i <= earlyFloodDistanceThreshold; i++) {
                    if (pathToAllEggs.containsKey(i)){
                        pathToAllEggs.get(i).forEach(ClosestBase::addBeacons);
                    }
                    if (pathToAllCrystals.containsKey(i)){
                        pathToAllCrystals.get(i).forEach(ClosestBase::addBeacons);
                    }
                }
            }
            return new ArrayList<>();
        }

        private List<Point3D> lateFlood() {
            if (!iHaveEnoughAnts() && crystalSpots.size() > 4) {
                eggSpots.stream()
                    .map(eggId -> getClosestBaseToTarget(bases, indices.getHex(eggId).pt))
                    .forEach(ClosestBase::addBeacons);
            }
            crystalSpots.stream()
                .map(crystalId -> getClosestBaseToTarget(bases, indices.getHex(crystalId).pt))
                .forEach(ClosestBase::addBeacons);
            return new ArrayList<>();
        }

        private boolean iHaveEnoughAnts() {
            return myAnts > 1.5 * enemyAnts;
        }

        private boolean needToShortCircuit() {
            return eggSpots.isEmpty()
                || crystalSpots.size() < 5;
        }

        private class ClosestBase{
            Point3D base;
            SearchNode bestPath;
            public ClosestBase(Point3D base, SearchNode bestPath) {
                this.base = base;
                this.bestPath = bestPath;
            }
            
            public void addBeacons() {
                for (Point3D pt : bestPath.history) {
                    addBeacon(new Beacon(indices.getHex(pt).id, 1));
                }
            }
        }
        private ClosestBase getClosestBaseToTarget(List<Point3D> baseList, Point3D target) {
            ClosestBase closest = new ClosestBase(null, new SearchNode(null, Double.MAX_VALUE, new ArrayList<>()));
            for (Point3D base : baseList) {
                SearchNode bestPath = getPathFromBaseToPt(base, target);
                if (bestPath != null && bestPath.distance < closest.bestPath.distance) {
                    closest = new ClosestBase(base, bestPath);
                }
            }
            return closest;
        }

        private class SearchNode {
            Point3D pt;
            double distance;
            List<Point3D> history;
            
            public SearchNode(Point3D pt, double distance, List<Point3D> history) {
                this.pt = pt;
                this.distance = distance;
                this.history = history;
                this.history.add(pt);
            }

            public double heuristic(Point3D target) {
                return distance + pt.distance(target);
            }
        }
        private SearchNode getPathFromBaseToPt(Point3D base, Point3D target) {
            PriorityQueue<SearchNode> q = new PriorityQueue<>(Comparator.comparingDouble(s -> s.heuristic(target)));
            Set<Point3D> visited = new HashSet<>();
            q.add(new SearchNode(base, 0, new ArrayList<>()));
            while (!q.isEmpty()) {
                SearchNode n = q.poll();
                if (n.pt.equals(target)) {
                    return n;
                }
                if (!visited.add(n.pt)) {
                    continue;
                }
                validNeighbors(n).stream()
                    .map(p -> new SearchNode(p, n.distance + 1, n.history.stream().collect(Collectors.toList())))
                    .forEach(q::add);
            }
            return null;
        }
        private List<Point3D> validNeighbors(SearchNode n) {
            return Stream.of(
                n.pt.add(1, 0, -1),
                n.pt.add(1, -1, 0),
                n.pt.add(0, -1, 1),
                n.pt.add(-1, 0, 1),
                n.pt.add(-1, 1, 0),
                n.pt.add(0, 1, -1)
            )
            .filter(indices.ptToHexMap::containsKey)
            .filter(pt -> !n.history.contains(pt))
            .map(indices::getHex)
            .filter(h -> h.type != -1)
            .map(Hex::pt)
            .collect(Collectors.toList());
        }
    
        /**
         * Find minimum spanning tree using Kruskal's Algorithm for Hexagonal Grids
         * 
         * TODO: Need to change this generated code
         * 1 - Instantiate a forest of size=1 trees of all points of interest (typically my base, eggs, and crystals)
         * 2 - Union all trees together that are distance=1 from each other (need to while-loop successive waves on this one)
         * 3 - Instantiate searchDistance = 2
         * 4 - Search for all trees that are searchDistance away from the tree containing my base
         * 5 - Calculate which hexes are in the intersecting region (regions is searchDistance large) between the home tree and all found trees
         * 6 - Establish a waypoint region (http://theory.stanford.edu/~amitp/GameProgramming/MapRepresentations.html#waypoints) for the points that are in the most intersecting regions
         * 6a - Ensure that you track which frontier trees are associated with each waypoint region
         * 6b - If a frontier tree hasn't been accounted for yet, go to the set of points in the next most amount of intersecting regions, etc., until all frontier trees are accounted for
         * 6c - http://theory.stanford.edu/~amitp/GameProgramming/Heuristics.html#multiple-goals and http://theory.stanford.edu/~amitp/GameProgramming/Heuristics.html#precomputed-exact-heuristic
         * 7 - Starting from the home tree, A* with a heuristic that hits the waypoint first, then goes to the target tree
         * 8 - Store the path to each frontier tree in the min span tree structure; connect all the trees identified in #4
         * 9 - While-loop 4-8, increase searchDistance each time, until there is only one tree; shuttle all identified hexes to beacon-creation
         * 
         * @param grid
         * @param pointsOfInterest
         * @return
         */
        public static List<Point3D> findMinimumSpanningTree(List<Point3D> grid, List<Point3D> pointsOfInterest) {
            List<Point3D> minimumSpanningTree = new ArrayList<>();
            List<Edge> allEdges = new ArrayList<>();
            UnionFind<Point3D> unionFind = new UnionFind<>();
            
            // Add all edges to the list
            for (int i = 0; i < grid.size(); i++) {
                for (int j = i + 1; j < grid.size(); j++) {
                    Point3D p1 = grid.get(i);
                    Point3D p2 = grid.get(j);
                    allEdges.add(new Edge(p1, p2));
                }
            }
            
            // Sort edges by weight (Manhattan distance)
            Collections.sort(allEdges);
            
            // Initialize Union-Find data structure
            for (Point3D point : grid) {
                unionFind.makeSet(point);
            }
            
            // Iterate through sorted edges and add to minimum spanning tree
            for (Edge edge : allEdges) {
                Point3D start = edge.start;
                Point3D end = edge.end;
                
                if (unionFind.find(start) != unionFind.find(end)) {
                    minimumSpanningTree.add(start);
                    minimumSpanningTree.add(end);
                    unionFind.union(start, end);
                }
                
                // Check if all points of interest are connected
                if (pointsOfInterestConnected(minimumSpanningTree, pointsOfInterest)) {
                    break;
                }
            }
            
            return minimumSpanningTree;
        }
        
        // Check if all points of interest are connected to the minimum spanning tree
        private static boolean pointsOfInterestConnected(List<Point3D> minimumSpanningTree, List<Point3D> pointsOfInterest) {
            Set<Point3D> set = new HashSet<>(minimumSpanningTree);
            for (Point3D point : pointsOfInterest) {
                if (!set.contains(point)) {
                    return false;
                }
            }
            return true;
        }
        
        // Helper class for representing an edge
        static class Edge implements Comparable<Edge> {
            Point3D start;
            Point3D end;
            double weight;
            
            public Edge(Point3D start, Point3D end) {
                this.start = start;
                this.end = end;
                this.weight = hexDistance(start, end);
            }
            
            @Override
            public int compareTo(Edge other) {
                return Double.compare(weight, other.weight);
            }
            
            private double hexDistance(Point3D start, Point3D end) {
                return (Math.abs(start.getX() - end.getX()) + Math.abs(start.getY() - end.getY()) + Math.abs(start.getZ() - end.getZ())) / 2.0;
            }
        }
        
        // Helper class for Union-Find data structure
        static class UnionFind<T> {
            private Map<T, T> parent;
            private Map<T, Integer> rank;
            
            public UnionFind() {
                parent = new HashMap<>();
                rank = new HashMap<>();
            }
            
            public void makeSet(T x) {
                parent.put(x, x);
                rank.put(x, 0);
            }
            
            public T find(T x) {
                if (parent.get(x) != x) {
                    parent.put(x, find(parent.get(x)));
                }
                return parent.get(x);
            }
            
            public void union(T x, T y) {
                T rootX = find(x);
                T rootY = find(y);
                
                if (rootX != rootY) {
                    if (rank.get(rootX) < rank.get(rootY)) {
                        parent.put(rootX, rootY);
                    } else if (rank.get(rootX) > rank.get(rootY)) {
                        parent.put(rootY, rootX);
                    } else {
                        parent.put(rootY, rootX);
                        rank.put(rootX, rank.get(rootX) + 1);
                    }
                }
            }
        }
    }
}
