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
            return day >= 24 && lastMove.action == Action.WAIT;
        }
        
        /**
         * Given a move, how likely are we to win with it?
         * @param move
         * @param otherMove
         * @return 
         */
        private int scoreMoves(Move move, Move otherMove) {
            return Integer.compare(
                moveScores.computeIfAbsent(move, m -> m.scoreMove(this)), 
                moveScores.computeIfAbsent(otherMove, m -> m.scoreMove(this))
            );
        }
    }
    
    static abstract class ScoreBuilder {
        protected final Game game;
        protected final Move move;

        public ScoreBuilder(Game game, Move move) {
            this.game = game;
            this.move = move;
        }
        
        public int getActionScore() {
            return move.action.ordinal();
        }
        
        public int getRichnessScore() {
            return getTargetCell().getRichness() + 1;
        }
        
        public abstract Cell getTargetCell();
        public abstract Cell getSourceCell();
        public abstract int getTreeSizeScore();
        public abstract int getCost();
        /**
         * Scan the current game state. Assume that the state doesn't change for 6 turns. How many sun points will this tree get?
         */
        public abstract int howManySunPointsCanThisEarn();
        /**
         * Scan the current game state. Assume that the state doesn't change for 6 turns. How many opponent sun points will this tree make spooky?
         */
        public abstract int howManyAdditionalSunPointsWillThisRobFromOpponent();
        /**
         * Scan the current game state. Assume that the state doesn't change for 6 turns. How many of our sun points will this tree make spooky?
         */
        public abstract int howManyAdditionalSunPointsWillThisRobFromMe();
        
        public int getFinalScore() {
            return (getActionScore() * getRichnessScore() * getTreeSizeScore()) + howManySunPointsCanThisEarn() - getCost() - howManyAdditionalSunPointsWillThisRobFromMe() + howManyAdditionalSunPointsWillThisRobFromOpponent();
        }
        
        @Override
        public String toString() {
            return "("+getActionScore()+" * "+getRichnessScore()+" * "+getTreeSizeScore()+") + "+howManySunPointsCanThisEarn()+" - "+getCost()+" - "+howManyAdditionalSunPointsWillThisRobFromMe()+" + "+howManyAdditionalSunPointsWillThisRobFromOpponent()+" = "+getFinalScore();
        }
    }
    
    static class WaitScoreBuilder extends ScoreBuilder {
        
        public WaitScoreBuilder(Game game, Move move) {
            super(game, move);
        }
        
        @Override
        public int getRichnessScore() {
            return 1;
        }
        
        @Override
        public Cell getTargetCell() {
            return null;
        }

        @Override
        public Cell getSourceCell() {
            return null;
        }

        @Override
        public int getTreeSizeScore() {
            return 1;
        }

        @Override
        public int getCost() {
            return 0;
        }

        @Override
        public int howManySunPointsCanThisEarn() {
            return game.myTrees.stream().map(Tree::getSize).reduce(0, Math::addExact);
        }

        @Override
        public int howManyAdditionalSunPointsWillThisRobFromOpponent() {
            return 0;
        }

        @Override
        public int howManyAdditionalSunPointsWillThisRobFromMe() {
            return 0;
        }
    }
    
    static class SeedScoreBuilder extends ScoreBuilder {
        
        public SeedScoreBuilder(Game game, Move move) {
            super(game, move);
        }

        @Override
        public Cell getTargetCell() {
            return game.cellMap.get(move.index2);
        }

        @Override
        public Cell getSourceCell() {
            return game.cellMap.get(move.index);
        }

        @Override
        public int getTreeSizeScore() {
            return 1;
        }

        @Override
        public int getCost() {
            return Long.valueOf(game.myTrees.stream().filter(t -> t.size == 0).count()).intValue();
        }

        @Override
        public int howManySunPointsCanThisEarn() {
            return 0;
        }

        @Override
        public int howManyAdditionalSunPointsWillThisRobFromOpponent() {
            return 0;
        }

        @Override
        public int howManyAdditionalSunPointsWillThisRobFromMe() {
            return 0;
        }
        
    }
    
    static class GrowScoreBuilder extends ScoreBuilder {
        
        public GrowScoreBuilder(Game game, Move move) {
            super(game, move);
        }

        @Override
        public Cell getTargetCell() {
            return game.cellMap.get(move.index);
        }

        @Override
        public Cell getSourceCell() {
            return game.cellMap.get(move.index);
        }

        @Override
        public int getTreeSizeScore() {
            return game.treeMap.get(move.index).getSize() + 1;
        }

        @Override
        public int getCost() {
            int targetTreeSize = getTreeSizeScore();
            int numOfTargetTrees = Long.valueOf(game.myTrees.stream().filter(t -> t.size == targetTreeSize).count()).intValue();
            switch (targetTreeSize) {
                case 1:
                    return 1 + numOfTargetTrees;
                case 2:
                    return 3 + numOfTargetTrees;
                case 3:
                default:
                    return 7 + numOfTargetTrees;
            }
        }

        @Override
        public int howManySunPointsCanThisEarn() {
            //TODO consider shadows
            return game.myTrees.stream().filter(t -> t.getCell().getIndex() == move.index).findAny().get().getSize() * 6;
        }

        @Override
        public int howManyAdditionalSunPointsWillThisRobFromOpponent() {
            return 0;
        }

        @Override
        public int howManyAdditionalSunPointsWillThisRobFromMe() {
            return 0;
        }
    }
    
    static class CompleteScoreBuilder extends ScoreBuilder {
        
        public CompleteScoreBuilder(Game game, Move move) {
            super(game, move);
        }

        @Override
        public Cell getTargetCell() {
            return game.cellMap.get(move.index);
        }

        @Override
        public Cell getSourceCell() {
            return game.cellMap.get(move.index);
        }

        @Override
        public int getTreeSizeScore() {
            return 4;
        }

        @Override
        public int getCost() {
            return 4;
        }

        @Override
        public int howManySunPointsCanThisEarn() {
            return 0;
        }

        @Override
        public int howManyAdditionalSunPointsWillThisRobFromOpponent() {
            return 0;
        }

        @Override
        public int howManyAdditionalSunPointsWillThisRobFromMe() {
            return -1 * game.myTrees.stream().filter(t -> t.getCell().getIndex() != move.index).map(Tree::getSize).reduce(0, Math::addExact, Math::addExact);
        }
    }
    
    static class ScoreBuilderFactory {
        public static ScoreBuilder createScoreBuilder(Action action, Game game, Move move) {
            switch (action) {
                case SEED:
                    return createSeedScoreBuilder(game, move);
                case GROW:
                    return createGrowScoreBuilder(game, move);
                case COMPLETE:
                    return createCompleteScoreBuilder(game, move);
                case WAIT:
                default:
                    return createWaitScoreBuilder(game, move);
                
            }
        }
        private static ScoreBuilder createWaitScoreBuilder(Game game, Move move) {
            return new WaitScoreBuilder(game, move);
        }
        private static ScoreBuilder createSeedScoreBuilder(Game game, Move move) {
            return new SeedScoreBuilder(game, move);
        }
        private static ScoreBuilder createGrowScoreBuilder(Game game, Move move) {
            return new GrowScoreBuilder(game, move);
        }
        private static ScoreBuilder createCompleteScoreBuilder(Game game, Move move) {
            return new CompleteScoreBuilder(game, move);
        }
    }
    
    static enum Action {
        WAIT((i, j) -> "Soak in the sun, my Arbor Army!"),
        SEED((i, j) -> "#" + i + ", go sneeze on #" + j),
        GROW((i, j) -> "Let's give some water to #" + i),
        COMPLETE((i, j) -> "I release you to Mother Earth, #" + i);

        private final BiFunction<Integer, Integer, String> flavorFn;

        private Action(BiFunction<Integer, Integer, String> flavorFn) {
            this.flavorFn = flavorFn;
        }

        public String stringify(int index, int index2) {
            if (index == -1) { //WAIT
                return name();
            }
            return name() + " " + getIndexString(index, index2);
        }

        public String stringifyWithFlavor(int index, int index2) {
            if (index == -1) { //WAIT
                return name() + " " + flavorFn.apply(index, index2);
            }
            return name() + " " + getIndexString(index, index2) + flavorFn.apply(index, index2);
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
    
    static class Move {
        private static final Pattern MOVE_PATTERN = Pattern.compile("(GROW|COMPLETE|WAIT|SEED) ?(\\d*)? ?(\\d*)?");
        private final Action action;
        private final int index;
        private final int index2;
        private int score;
        private boolean scoreCached;
        
        public Move(String moveString) {
            Matcher m = MOVE_PATTERN.matcher(moveString);
            m.find();
            this.action = Action.valueOf(m.group(1));
            this.index = this.action == Action.WAIT ? -1 : Integer.valueOf(m.group(2));
            this.index2 = this.action == Action.SEED ? Integer.valueOf(m.group(3)) : -1;
            this.scoreCached = false;
        }

        @Override
        public int hashCode() {
            int hash = 7;
            hash = 23 * hash + Objects.hashCode(this.action);
            hash = 23 * hash + this.index;
            hash = 23 * hash + this.index2;
            return hash;
        }
        
        public int scoreMove(Game game) {
            if (scoreCached) {
                return score;
            }
            else {
                ScoreBuilder builder = ScoreBuilderFactory.createScoreBuilder(action, game, this);
                score = builder.getFinalScore();
                System.err.println(toString() + ": " + builder);
                scoreCached = true;
            }
            return score;
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
