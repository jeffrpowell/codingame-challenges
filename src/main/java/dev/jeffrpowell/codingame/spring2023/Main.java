package dev.jeffrpowell.codingame.spring2023;

import java.util.ArrayList;
import java.util.Collection;
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
            if (earlyGame) {
                earlyFlood();
            }
            else {
                lateFlood();
            }
        }

        private void earlyFlood() {
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
        }

        private void lateFlood() {
            if (!iHaveEnoughAnts() && crystalSpots.size() > 4) {
                eggSpots.stream()
                    .map(eggId -> getClosestBaseToTarget(bases, indices.getHex(eggId).pt))
                    .forEach(ClosestBase::addBeacons);
            }
            crystalSpots.stream()
                .map(crystalId -> getClosestBaseToTarget(bases, indices.getHex(crystalId).pt))
                .forEach(ClosestBase::addBeacons);
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

            public long numResourcesInPath() {
                return history.stream()
                    .map(indices::getHex)
                    .filter(h -> h.type > 0)
                    .count();
            }
        }
        private SearchNode getPathFromBaseToPt(Point3D base, Point3D target) {
            PriorityQueue<SearchNode> q = new PriorityQueue<>(Comparator.comparingDouble(s -> s.heuristic(target)));
            Set<Point3D> visited = new HashSet<>();
            SearchNode contender = null;
            double contenderMaxDistance = -1;
            long resourceHexesToBeat = -1;
            q.add(new SearchNode(base, 0, new ArrayList<>()));
            while (!q.isEmpty()) {
                SearchNode n = q.poll();
                if (n.pt.equals(target)) {
                    if (contenderMaxDistance == -1) {
                        contender = n;
                        contenderMaxDistance = n.distance + 1;
                        resourceHexesToBeat = n.numResourcesInPath();
                        return contender;
                    }
                    else if (n.distance > contenderMaxDistance) {
                        return contender;
                    }
                    else if (n.numResourcesInPath() > resourceHexesToBeat) {
                        contender = n;
                        resourceHexesToBeat = n.numResourcesInPath();
                    }
                    continue;
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
    }
}
