package dev.jeffrpowell.codingame.spring2023;

import java.util.ArrayList;
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
 */
public class Main {
    public static void main(String args[]) {
        Scanner in = new Scanner(System.in);
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
            Hex h = new Hex(builder.id, builder.pt, builder.type, builder.initialResources);
            ptToHexMap.put(builder.pt, h);
            idToHexMap.put(builder.id, h);
            for (int i = 0; i < builder.neighbor.length; i++) {
                if (builder.neighbor[i] == -1) {
                    Hex emptyHex = new Hex(-1, builder.neighborPts.get(i), -1, 0);
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
        public Hex(int id, Point3D pt, int type, int resources) {
            this.id = id;
            this.pt = pt;
            this.type = type;
            this.resources = resources;
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
        private List<Integer> crystalSpots;
        private boolean earlyGame;

        public Game(int numCells, List<Point3D> bases, List<Point3D> enemyBases, HexIndices indices) {
            this.numCells = numCells;
            this.bases = bases;
            this.enemyBases = enemyBases;
            this.indices = indices;
            this.crystalSpots = indices.idToHexMap.values().stream().filter(h -> h.type == 2).map(Hex::id).collect(Collectors.toList());
            this.earlyGame = true;
        }
        
        public String gameLoop(Scanner in) {
            for (Integer id : crystalSpots) {
                Hex h = indices.getHex(id);
                indices.putHex(id, new Hex(h.id, h.pt, h.type, 0));
            }
            crystalSpots.clear();
            for (int i = 0; i < numCells; i++) {
                int resources = in.nextInt(); // the current amount of eggs/crystals on this cell
                if (resources > 0) {
                    Hex h = indices.getHex(i);
                    if (h.resources != resources) {
                        indices.putHex(i, new Hex(h.id, h.pt, h.type, resources));
                        crystalSpots.add(h.id);
                    }
                }
                int myAnts = in.nextInt(); // the amount of your ants on this cell
                int oppAnts = in.nextInt(); // the amount of opponent ants on this cell
            }
            List<Line> lines = decideLines();
            if (lines.isEmpty()) {
                return "WAIT";
            }
            return lines.stream().map(b -> "LINE "+b.baseId+" "+b.targetId+" "+b.strength).collect(Collectors.joining(";"));
        }
        
        private static class Line{
            int baseId;
            int targetId;
            int strength;
            public Line(int baseId, int targetId, int strength) {
                this.baseId = baseId;
                this.targetId = targetId;
                this.strength = strength;
            }
            
        }

        private static class ContentionScore{
            ClosestBase closestBase;
            int score;
            public ContentionScore(ClosestBase closestBase, int score) {
                this.closestBase = closestBase;
                this.score = score;
            }
            
        }
        // WAIT | LINE <sourceIdx> <targetIdx> <strength> | BEACON <cellIdx> <strength> | MESSAGE <text>
        private List<Line> decideLines() {
            if (earlyGame) {
                return earlyGameLines();
            }
            return lateGameLines();
        }

        private List<Line> earlyGameLines() {
            Map<Integer, ContentionScore> contentionScores = new HashMap<>();
            for (Integer id : crystalSpots) {
                ClosestBase closestOpponent = calcDistanceFromBasesToPt(enemyBases, indices.getHex(id).pt);
                ClosestBase closestBase = calcDistanceFromBasesToPt(bases, indices.getHex(id).pt);
                int score = Math.abs(closestOpponent.distance - closestBase.distance);
                contentionScores.put(id, new ContentionScore(closestBase, score));
            }
            Map.Entry<Integer, ContentionScore> bestPick = contentionScores.entrySet().stream()
                .min(Comparator.comparing(entry -> entry.getValue().score)).get();
            if (bestPick.getValue().score > 4) {
                earlyGame = false;
                return lateGameLines();
            }
            return Collections.singletonList(new Line(
                indices.getHex(bestPick.getValue().closestBase.base).id,
                bestPick.getKey(),
                1
            ));
        }

        private List<Line> lateGameLines() {
            List<Line> lines = new ArrayList<>();
            ClosestBase closestOpponentResource = new ClosestBase(null, Integer.MAX_VALUE);
            int closestOpponentResourceId = -1;
            for (Integer id : crystalSpots) {
                ClosestBase closestOpponent = calcDistanceFromBasesToPt(enemyBases, indices.getHex(id).pt);
                ClosestBase closestBase = calcDistanceFromBasesToPt(bases, indices.getHex(id).pt);
                if (closestBase.distance <= closestOpponent.distance) {
                    lines.add(new Line(indices.getHex(closestBase.base).id, id, 1));
                }
                else if (closestBase.distance < closestOpponentResource.distance) {
                    closestOpponentResource = closestBase;
                    closestOpponentResourceId = id;
                }
            }
            if (lines.isEmpty()) {
                //Strange end-game situation where we need to extend into enemy territory
                lines.add(new Line(indices.getHex(closestOpponentResource.base).id, closestOpponentResourceId, 1));
            }
            return lines;
        }

        private static class ClosestBase{
            Point3D base;
            int distance;
            public ClosestBase(Point3D base, int distance) {
                this.base = base;
                this.distance = distance;
            }
            
        }
        private ClosestBase calcDistanceFromBasesToPt(List<Point3D> baseList, Point3D target) {
            ClosestBase closest = new ClosestBase(null, Integer.MAX_VALUE);
            for (Point3D base : baseList) {
                int distance = calcDistanceFromBaseToPt(base, target);
                if (distance < closest.distance) {
                    closest = new ClosestBase(base, distance);
                }
            }
            return closest;
        }

        private static class SearchNode {
            Point3D pt;
            int distance;
            
            public SearchNode(Point3D pt, int distance) {
                this.pt = pt;
                this.distance = distance;
            }

            public double heuristic(Point3D target) {
                return distance + pt.distance(target);
            }
        }
        private int calcDistanceFromBaseToPt(Point3D base, Point3D target) {
            PriorityQueue<SearchNode> q = new PriorityQueue<>(Comparator.comparingDouble(s -> s.heuristic(target)));
            Set<Point3D> visited = new HashSet<>();
            q.add(new SearchNode(base, 0));
            while (!q.isEmpty()) {
                SearchNode n = q.poll();
                if (n.pt.equals(target)) {
                    return n.distance;
                }
                if (!visited.add(n.pt)) {
                    continue;
                }
                validNeighbors(n.pt).stream()
                    .map(p -> new SearchNode(p, n.distance + 1))
                    .forEach(q::add);
            }
            return -1;
        }
        private List<Point3D> validNeighbors(Point3D pt) {
            return Stream.of(
                pt.add(1, 0, -1),
                pt.add(1, -1, 0),
                pt.add(0, -1, 1),
                pt.add(-1, 0, 1),
                pt.add(-1, 1, 0),
                pt.add(0, 1, -1)
            )
            .filter(indices.ptToHexMap::containsKey)
            .map(indices::getHex)
            .filter(h -> h.type != -1)
            .map(Hex::pt)
            .collect(Collectors.toList());
        }
    }
}
