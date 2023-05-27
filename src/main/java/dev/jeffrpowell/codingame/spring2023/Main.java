package dev.jeffrpowell.codingame.spring2023;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javafx.geometry.Point3D;

/**
 * Notes on the hexagonal coordinate system of this game and my implementation
 * 
 * The game server generates the map by placing cell-0 in the center and then generating the rest of the map following a half-spiral pattern.
 * The game uses pointy-corner-up orientation for the hexagon orientation.
 * Any time the game rotates around, it always starts in the hex to the right, followed by a counterclockwise rotation.
 * The game places all the hexes with distance = 1 first, then distance = 2, etc. Always following the rotation pattern mentioned above ^.
 * Each time the game decides to place a hex in the grid, it assigns the next monotonically increasing hex id to it.
 * Once a hex is placed, the next hex to be placed MUST be the hex that is mirror-imaged over the center from the last hex that was placed.
 * After placing the mirror-image hex, the rotation and deciding logic proceeds from the originally placed hex. A couple examples:
 *    5     3            3    -1
 * 2     0     1      2     0     1
 *    4     6           -1     4
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

    private static record HexIndices(Map<Point3D, Hex> ptToHexMap, Map<Integer, Hex> idToHexMap){}
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

        public void setPts(Map<Integer, HexBuilder> hexBuilderMap) {
            for (int i = 0; i < neighbor.length; i++) {
                if (hexBuilderMap.containsKey(neighbor[i]) && hexBuilderMap.get(neighbor[i]).pt != null) {
                    neighborPts.set(i, hexBuilderMap.get(neighbor[i]).pt);
                    if (pt == null) {
                        pt = getPtFromNeighborPt(hexBuilderMap.get(neighbor[i]).pt, i);
                    }
                }
            }
            for (int i = 0; i < neighbor.length; i++) {
                if (neighborPts.get(i) == null) {
                    switch (i) {
                        case 0 -> neighborPts.set(i, getPtFromNeighborPt(pt, 3));
                        case 1 -> neighborPts.set(i, getPtFromNeighborPt(pt, 4));
                        case 2 -> neighborPts.set(i, getPtFromNeighborPt(pt, 5));
                        case 3 -> neighborPts.set(i, getPtFromNeighborPt(pt, 0));
                        case 4 -> neighborPts.set(i, getPtFromNeighborPt(pt, 1));
                        default -> neighborPts.set(i, getPtFromNeighborPt(pt, 2));
                    }
                }
                if (neighbor[i] != -1) {
                    hexBuilderMap.get(neighbor[i]).pt = neighborPts.get(i);
                }
            }
        }

        private Point3D getPtFromNeighborPt(Point3D neighbor, int neighborRelativePositionIndex) {
            return switch (neighborRelativePositionIndex) {
                case 0 -> neighbor.add(-1, 0, 1);
                case 1 -> neighbor.add(-1, 1, 0);
                case 2 -> neighbor.add(0, 1, -1);
                case 3 -> neighbor.add(1, 0, -1);
                case 4 -> neighbor.add(1, -1, 0);
                default -> neighbor.add(0, -1, 1);
            };
        }
    }

    private static record Hex(int id, Point3D pt, int type, int resources){}

    private static class Game{
        private final int numCells;
        private final List<Point3D> bases;
        private final List<Point3D> enemyBases;
        private final HexIndices indices;

        public Game(int numCells, List<Point3D> bases, List<Point3D> enemyBases, HexIndices indices) {
            this.numCells = numCells;
            this.bases = bases;
            this.enemyBases = enemyBases;
            this.indices = indices;
        }
        
        public String gameLoop(Scanner in) {
            for (int i = 0; i < numCells; i++) {
                int resources = in.nextInt(); // the current amount of eggs/crystals on this cell
                int myAnts = in.nextInt(); // the amount of your ants on this cell
                int oppAnts = in.nextInt(); // the amount of opponent ants on this cell
            }

            // Write an action using System.out.println()
            // To debug: System.err.println("Debug messages...");


            // WAIT | LINE <sourceIdx> <targetIdx> <strength> | BEACON <cellIdx> <strength> | MESSAGE <text>
            return "WAIT";
        }
    }
}
