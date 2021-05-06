package dev.jeffrpowell.codingame.spring2021;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Scanner;
import java.util.function.BiFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Main {
    public static void main(String[] args) {
        Map<Integer, Cell> cellMap = new HashMap<>();
        Map<Integer, Tree> treeMap = new HashMap<>();
        List<Tree> myTrees = new ArrayList<>();
        Scanner in = new Scanner(System.in);
        int numberOfCells = in.nextInt(); // 37
        for (int i = 0; i < numberOfCells; i++) {
            int index = in.nextInt(); // 0 is the center cell, the next cells spiral outwards
            int richness = in.nextInt(); // 0 if the cell is unusable, 1-3 for usable cells
            int neigh0 = in.nextInt(); // the index of the neighbouring cell for each direction
            int neigh1 = in.nextInt();
            int neigh2 = in.nextInt();
            int neigh3 = in.nextInt();
            int neigh4 = in.nextInt();
            int neigh5 = in.nextInt();
            cellMap.put(index, new Cell(index, richness));
        }
        Game game = new Game(cellMap);

        // game loop
        while (true) {
            treeMap.clear();
            myTrees.clear();
            int day = in.nextInt(); // the game lasts 24 days: 0-23
            int nutrients = in.nextInt(); // the base score you gain from the next COMPLETE action
            int sun = in.nextInt(); // your sun points
            int score = in.nextInt(); // your current score
            int oppSun = in.nextInt(); // opponent's sun points
            int oppScore = in.nextInt(); // opponent's score
            boolean oppIsWaiting = in.nextInt() != 0; // whether your opponent is asleep until the next day
            int numberOfTrees = in.nextInt(); // the current amount of trees
            for (int i = 0; i < numberOfTrees; i++) {
                int cellIndex = in.nextInt(); // location of this tree
                int size = in.nextInt(); // size of this tree: 0-3
                boolean isMine = in.nextInt() != 0; // 1 if this is your tree
                boolean isDormant = in.nextInt() != 0; // 1 if this tree is dormant
                Tree t = new Tree(cellMap.get(cellIndex), size, isMine, isDormant);
                treeMap.put(cellIndex, t);
                if (isMine) {
                    myTrees.add(t);
                }
            }
            int numberOfPossibleMoves = in.nextInt();
            if (in.hasNextLine()) {
                in.nextLine();
            }
            List<String> possibleMoves = new ArrayList<>();
            for (int i = 0; i < numberOfPossibleMoves; i++) {
                String possibleMove = in.nextLine();
                possibleMoves.add(possibleMove);
            }
            
            // Write an action using System.out.println()
            // To debug: System.err.println("Debug messages...");
            // GROW cellIdx | SEED sourceIdx targetIdx | COMPLETE cellIdx | WAIT <message>
            System.out.println(game.executeNewTurn(day, nutrients, sun, score, oppSun, oppScore, oppIsWaiting, treeMap, myTrees, possibleMoves));
            if (game.isGameOver()) {
                break;
            }
        }
    }
    
    static class Game { 
        private final Map<Integer, Cell> cellMap;
        private int day;
        private int nutrients;
        private int sun;
        private int score;
        private int oppSun;
        private int oppScore;
        private boolean oppIsWaiting;
        private Map<Integer, Tree> treeMap;
        private List<Tree> myTrees;
        private Map<Move, Integer> moveScores;
        private Move lastMove;

        public Game(Map<Integer, Cell> cellMap) {
            this.cellMap = cellMap;
        }

        public String executeNewTurn(
            int day,
            int nutrients,
            int sun,
            int score,
            int oppSun,
            int oppScore,
            boolean oppIsWaiting,
            Map<Integer, Tree> treeMap, 
            List<Tree> myTrees, 
            List<String> possibleMoves
        ) {
            this.day = day;
            this.nutrients = nutrients;
            this.sun = sun;
            this.score = score;
            this.oppSun = oppSun;
            this.oppScore = oppScore;
            this.oppIsWaiting = oppIsWaiting;
            this.treeMap = treeMap;
            this.myTrees = myTrees;
            this.moveScores = new HashMap<>();
            this.lastMove = possibleMoves.stream().map(Move::new).max(this::scoreMoves).get();
            return lastMove.toFlavorfulString();
        }
        
        public boolean isGameOver() {
            //Bronze
            return day >= 24 && lastMove.action == Move.Action.WAIT;
        }
        
        /**
         * Given a move, how likely are we to win with it?
         * @param move
         * @param otherMove
         * @return 
         */
        private int scoreMoves(Move move, Move otherMove) {
            return Integer.compare(
                moveScores.computeIfAbsent(move, this::scoreMove), 
                moveScores.computeIfAbsent(otherMove, this::scoreMove)
            );
        }
        
        private int scoreMove(Move move) {
            if (move.action == Move.Action.WAIT) {
                return 0;
            }
            int actionScore = move.action.ordinal();
            int cellIndex = move.action == Move.Action.SEED ? move.index2 : move.index;
            int richnessMultiplier = cellMap.get(cellIndex).getRichness() + 1;
            int treeSize = move.action == Move.Action.SEED ? 1 : treeMap.get(cellIndex).getSize() + 1;
            int moveScore = actionScore * richnessMultiplier * treeSize;
            //System.err.println(move + ": " + actionScore + " * " + richnessMultiplier + " * " + treeSize + " = " + moveScore);
            return moveScore;
        }
    }
    
    static class Move {
        private static final Pattern MOVE_PATTERN = Pattern.compile("(GROW|COMPLETE|WAIT|SEED) ?(\\d*)? ?(\\d*)?");
        enum Action {
            WAIT((i, j) -> "Soak in the sun, my Arbor Army!"),
            SEED((i, j) -> "#" + i + ", go sneeze on #" + j),
            GROW((i, j) -> "Let's give some water to #" + i),
            COMPLETE((i, j) -> "I release you to Mother Earth, #" + i);
            
            private final BiFunction<Integer, Integer, String> flavor;

            private Action(BiFunction<Integer, Integer, String> flavor) {
                this.flavor = flavor;
            }
            
            public String stringify(int index, int index2) {
                if (index == -1) { //WAIT
                    return name();
                }
                return name() + " " + getIndexString(index, index2);
            }
            
            public String stringifyWithFlavor(int index, int index2) {
                if (index == -1) { //WAIT
                    return name() + " " + flavor.apply(index, index2);
                }
                return name() + " " + getIndexString(index, index2) + flavor.apply(index, index2);
            }
            
            private String getIndexString(int index, int index2) {
                StringBuilder builder = new StringBuilder();
                if (index > -1) {
                    builder.append(index).append(" ");
                }
                if (index2 > -1) {
                    builder.append(index2).append(" ");
                }
                return builder.toString();
            }
        }
        
        private final Action action;
        private final int index;
        private final int index2;
        
        public Move(String moveString) {
            Matcher m = MOVE_PATTERN.matcher(moveString);
            m.find();
            this.action = Action.valueOf(m.group(1));
            this.index = this.action == Action.WAIT ? -1 : Integer.valueOf(m.group(2));
            this.index2 = this.action == Action.SEED ? Integer.valueOf(m.group(3)) : -1;
        }

        @Override
        public int hashCode() {
            int hash = 7;
            hash = 23 * hash + Objects.hashCode(this.action);
            hash = 23 * hash + this.index;
            hash = 23 * hash + this.index2;
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            final Move other = (Move) obj;
            if (this.index != other.index) {
                return false;
            }
            if (this.index2 != other.index2) {
                return false;
            }
            return this.action == other.action;
        }
        
        @Override
        public String toString() {
            return action.stringify(index, index2);
        }
        
        public String toFlavorfulString() {
            return action.stringifyWithFlavor(index, index2);
        }
    }
    
    static class Cell {
        private final int index;
        private final int richness;

        public Cell(int index, int richness) {
            this.index = index;
            this.richness = richness;
        }

        public int getIndex() {
            return index;
        }

        public int getRichness() {
            return richness;
        }
    }
    
    static class Tree {
        private final Cell cell;
        private final int size;
        private final boolean isMine;
        private final boolean isDormant;

        public Tree(Cell cell, int size, boolean isMine, boolean isDormant) {
            this.cell = cell;
            this.size = size;
            this.isMine = isMine;
            this.isDormant = isDormant;
        }

        public Cell getCell() {
            return cell;
        }
        
        public int getSize() {
            return size;
        }

        public boolean isIsMine() {
            return isMine;
        }

        public boolean isIsDormant() {
            return isDormant;
        }
    }
}
