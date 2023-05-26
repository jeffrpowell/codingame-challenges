package dev.jeffrpowell.codingame.spring2023;

import java.util.Scanner;

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
 */
public class Main {
    public static void main(String args[]) {
        Scanner in = new Scanner(System.in);
        int numberOfCells = in.nextInt(); // amount of hexagonal cells in this map
        for (int i = 0; i < numberOfCells; i++) {
            int type = in.nextInt(); // 0 for empty, 1 for eggs, 2 for crystal
            int initialResources = in.nextInt(); // the initial amount of eggs/crystals on this cell
            int neigh0 = in.nextInt(); // the index of the neighbouring cell for each direction
            int neigh1 = in.nextInt();
            int neigh2 = in.nextInt();
            int neigh3 = in.nextInt();
            int neigh4 = in.nextInt();
            int neigh5 = in.nextInt();
        }
        int numberOfBases = in.nextInt();
        for (int i = 0; i < numberOfBases; i++) {
            int myBaseIndex = in.nextInt();
        }
        for (int i = 0; i < numberOfBases; i++) {
            int oppBaseIndex = in.nextInt();
        }

        // game loop
        while (true) {
            for (int i = 0; i < numberOfCells; i++) {
                int resources = in.nextInt(); // the current amount of eggs/crystals on this cell
                int myAnts = in.nextInt(); // the amount of your ants on this cell
                int oppAnts = in.nextInt(); // the amount of opponent ants on this cell
            }

            // Write an action using System.out.println()
            // To debug: System.err.println("Debug messages...");


            // WAIT | LINE <sourceIdx> <targetIdx> <strength> | BEACON <cellIdx> <strength> | MESSAGE <text>
            System.out.println("WAIT");
        }
    }
}
