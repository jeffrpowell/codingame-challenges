package dev.jeffrpowell.codingame.spring2021;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Scanner;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Main {
    public static void main(String[] args) {
        Map<Integer, Cell> cellMap = new HashMap<>();
        Map<Integer, Tree> treeMap = new HashMap<>();
        List<Tree> myTrees = new ArrayList<>();
        List<Tree> theirTrees = new ArrayList<>();
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
            cellMap.put(index, new Cell(index, richness, neigh0, neigh1, neigh2, neigh3, neigh4, neigh5));
        }
        cellMap.put(-1, new Cell(-1, -1, -1, -1, -1, -1, -1, -1)); //Edge cells have -1 neighbors
        cellMap.forEach((i, cell) -> cell.populateNeighbors(cellMap));
        Game game = new Game(cellMap);

        // game loop
        while (true) {
            treeMap.clear();
            myTrees.clear();
            theirTrees.clear();
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
                else {
                    theirTrees.add(t);
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
            System.out.println(game.executeNewTurn(day, nutrients, sun, score, oppSun, oppScore, oppIsWaiting, treeMap, myTrees, theirTrees, possibleMoves));
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
        private HexDirection shadowDirection;
        private Map<Integer, Tree> treeMap;
        private List<Tree> myTrees;
        private List<Tree> theirTrees;
        private Map<Move, Integer> moveScores;
        private Move lastMove;

        public Game(Map<Integer, Cell> cellMap) {
            this.cellMap = cellMap;
            this.day = -1;
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
            List<Tree> theirTrees, 
            List<String> possibleMoves
        ) {
            this.day = day;
            this.nutrients = nutrients;
            this.sun = sun;
            this.score = score;
            this.oppSun = oppSun;
            this.oppScore = oppScore;
            this.oppIsWaiting = oppIsWaiting;
            this.shadowDirection = getShadeDirection(day);
            this.treeMap = treeMap;
            this.myTrees = myTrees;
            this.theirTrees = theirTrees;
            this.moveScores = new HashMap<>();
            this.lastMove = possibleMoves.stream().map(Move::new).max(this::scoreMoves).get();
            return lastMove.toFlavorfulString();
        }
        
        public boolean isGameOver() {
            //Bronze
            return day >= 24 && lastMove.action == Action.WAIT;
        }
        
        public HexDirection getShadeDirection(int day) {
            return HexDirection.values()[day % 6];
        }
        
        public int numberOfSunPointsInARevolution(Tree tree, int startDay) {
            int totalPoints = 0;
            int treeSize = tree.getSize();
            for (int i = 1; i <= 3; i++) {
                final int loopVar = i;
                Set<Cell> shadedCellsThisDay = treeMap.values().stream()
                    .map(t -> t.whichCellsAreShaded(getShadeDirection(startDay + loopVar)))
                    .flatMap(Set::stream)
                    .filter(shadeSource -> shadeSource.getTreeSize() >= tree.getSize())
                    .map(ShadeSource::getCell)
                    .collect(Collectors.toSet());
                if (!shadedCellsThisDay.contains(tree.getCell())) {
                    totalPoints += treeSize;
                }
            }
            //TWEAK: possible sun points in the next 3 turns are worth more than possible sun points in the next 4-6 turns
            for (int i = 4; i <= 6; i++) {
                final int loopVar = i;
                Set<Cell> shadedCellsThisDay = treeMap.values().stream()
                    .map(t -> t.whichCellsAreShaded(getShadeDirection(startDay + loopVar)))
                    .flatMap(Set::stream)
                    .filter(shadeSource -> shadeSource.getTreeSize() >= tree.getSize())
                    .map(ShadeSource::getCell)
                    .collect(Collectors.toSet());
                if (!shadedCellsThisDay.contains(tree.getCell())) {
                    totalPoints += treeSize / 2;
                }
            }
            return totalPoints;
        }
        
        public int numberOfSpookyPointsInARevolution(Tree tree, int startDay) {
            int totalSpookyPoints = 0;
            int treeSize = tree.getSize();
            for (int i = 1; i <= 3; i++) {
                final int loopVar = i;
                Set<Cell> shadedCellsThisDay = treeMap.values().stream()
                    .map(t -> t.whichCellsAreShaded(getShadeDirection(startDay + loopVar)))
                    .flatMap(Set::stream)
                    .filter(shadeSource -> shadeSource.getTreeSize() >= tree.getSize())
                    .map(ShadeSource::getCell)
                    .collect(Collectors.toSet());
                if (shadedCellsThisDay.contains(tree.getCell())) {
                    totalSpookyPoints += treeSize;
                }
            }
            //TWEAK: possible sun points in the next 3 turns are worth more than possible sun points in the next 4-6 turns
            for (int i = 4; i <= 6; i++) {
                final int loopVar = i;
                Set<Cell> shadedCellsThisDay = treeMap.values().stream()
                    .map(t -> t.whichCellsAreShaded(getShadeDirection(startDay + loopVar)))
                    .flatMap(Set::stream)
                    .filter(shadeSource -> shadeSource.getTreeSize() >= tree.getSize())
                    .map(ShadeSource::getCell)
                    .collect(Collectors.toSet());
                if (shadedCellsThisDay.contains(tree.getCell())) {
                    totalSpookyPoints += treeSize / 2;
                }
            }
            return totalSpookyPoints;
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
        
        /*
         * Scan the current game state. Assume that the state doesn't change for 6 turns. 
         * The next 3 methods consider the potential sun-point impact of the decision.
         */
        
        public int howManySunPointsCanThisEarn() {
            Tree modifiedTree = getModifiedTree();
            Tree originalTree = null;
            if (modifiedTree != null && getTargetCell() != null) {
                originalTree = game.treeMap.get(getTargetCell().getIndex());
                game.treeMap.put(getTargetCell().index, modifiedTree);
            }
            int points = game.treeMap.values().stream()
                .filter(Tree::isIsMine)
                .map(t -> game.numberOfSunPointsInARevolution(t, game.day))
                .reduce(0, Math::addExact);
            if (modifiedTree != null && getTargetCell() != null) {
                game.treeMap.put(getTargetCell().index, originalTree);
            }
            return points;
        }

        public int howManyAdditionalSunPointsWillThisRobFromOpponent() {
            Tree modifiedTree = getModifiedTree();
            Tree originalTree = null;
            if (modifiedTree != null && getTargetCell() != null) {
                originalTree = game.treeMap.get(getTargetCell().getIndex());
                game.treeMap.put(getTargetCell().index, modifiedTree);
            }
            int points =  game.treeMap.values().stream()
                .filter(t -> !t.isIsMine())
                .map(t -> game.numberOfSpookyPointsInARevolution(t, game.day))
                .reduce(0, Math::addExact);
            if (modifiedTree != null && getTargetCell() != null) {
                game.treeMap.put(getTargetCell().index, originalTree);
            }
            return points;
        }

        public int howManyAdditionalSunPointsWillThisRobFromMe() {
            Tree modifiedTree = getModifiedTree();
            Tree originalTree = null;
            if (modifiedTree != null && getTargetCell() != null) {
                originalTree = game.treeMap.get(getTargetCell().getIndex());
                game.treeMap.put(getTargetCell().index, modifiedTree);
            }
            int points =  game.treeMap.values().stream()
                .filter(Tree::isIsMine)
                .map(t -> game.numberOfSpookyPointsInARevolution(t, game.day))
                .reduce(0, Math::addExact);
            if (modifiedTree != null && getTargetCell() != null) {
                game.treeMap.put(getTargetCell().index, originalTree);
            }
            return points;
        }
        
        public abstract Cell getTargetCell();
        public abstract Cell getSourceCell();
        public abstract Tree getModifiedTree();
        public abstract int getTreeSizeScore();
        public abstract int getCost();
        public abstract Predicate<Integer> getDaysThatShouldBeFavored();
        public abstract Predicate<Integer> getDaysThatShouldBeAvoided();
        
        public int getFinalScore() {
            int bias = getDaysThatShouldBeFavored().test(game.day) ? 5 : 1;
            bias = getDaysThatShouldBeAvoided().test(game.day) ? 0 : bias;
            return (getActionScore() * getRichnessScore() * getTreeSizeScore() * bias) + howManySunPointsCanThisEarn() - getCost() - howManyAdditionalSunPointsWillThisRobFromMe() + howManyAdditionalSunPointsWillThisRobFromOpponent();
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
        public Tree getModifiedTree() {
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
        public Predicate<Integer> getDaysThatShouldBeFavored() {
            return day -> false;
        }

        @Override
        public Predicate<Integer> getDaysThatShouldBeAvoided() {
            return day -> false;
        }
    }
    
    static class SeedScoreBuilder extends ScoreBuilder {

        private static final int VERY_HIGH_COST = Integer.MAX_VALUE - 1000;
        
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
        public Tree getModifiedTree() {
            return null;
        }

        @Override
        public int getTreeSizeScore() {
            return 1;
        }

        @Override
        public int getCost() {
            return game.myTrees.stream().anyMatch(t -> t.getSize() == 0) ? VERY_HIGH_COST : 0;
        }

        @Override
        public Predicate<Integer> getDaysThatShouldBeFavored() {
            return day -> false;
        }

        @Override
        public Predicate<Integer> getDaysThatShouldBeAvoided() {
            return day -> day > 18 || day == 0;
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
        public Tree getModifiedTree() {
            Tree original = game.treeMap.get(move.index);
            return new Tree(getTargetCell(), original.getSize() + 1, true, true);
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
        public Predicate<Integer> getDaysThatShouldBeFavored() {
            return day -> day == 0;
        }

        @Override
        public Predicate<Integer> getDaysThatShouldBeAvoided() {
            return day -> false;
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
        public Tree getModifiedTree() {
            return new Tree(getTargetCell(), 0, true, true);
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
        public Predicate<Integer> getDaysThatShouldBeFavored() {
            return day -> day > 21;
        }

        @Override
        public Predicate<Integer> getDaysThatShouldBeAvoided() {
            return day -> day < 13;
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
                //System.err.println(toString() + ": " + builder);
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
    
    static enum HexDirection {
        E,NE,NW,W,SW,SE;
    }
    
    static class Cell {
        private final int index;
        private final int richness;
        private final List<Integer> neighborIndices;
        private final Map<HexDirection, Cell> neighbors;

        public Cell(int index, int richness, int neigh0, int neigh1, int neigh2, int neigh3, int neigh4, int neigh5) {
            this.index = index;
            this.richness = richness;
            this.neighborIndices = Stream.of(neigh0, neigh1, neigh2, neigh3, neigh4, neigh5).collect(Collectors.toList());
            this.neighbors = new HashMap<>();
        }

        public int getIndex() {
            return index;
        }

        public int getRichness() {
            return richness;
        }
        
        public void populateNeighbors(Map<Integer, Cell> cellMap) {
            HexDirection[] orderedDirections = HexDirection.values();
            for (int i = 0; i < 6; i++) {
                neighbors.put(orderedDirections[i], cellMap.get(neighborIndices.get(i)));
            }
        }
    }
    
    static class ShadeSource {
        private final Cell cell;
        private final int treeSize;

        public ShadeSource(Cell cell, int treeSize) {
            this.cell = cell;
            this.treeSize = treeSize;
        }

        public Cell getCell() {
            return cell;
        }

        public int getTreeSize() {
            return treeSize;
        }

        @Override
        public int hashCode() {
            int hash = 3;
            hash = 19 * hash + Objects.hashCode(this.cell);
            hash = 19 * hash + this.treeSize;
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
            final ShadeSource other = (ShadeSource) obj;
            if (this.treeSize != other.treeSize) {
                return false;
            }
            return Objects.equals(this.cell, other.cell);
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
        
        public Set<ShadeSource> whichCellsAreShaded(HexDirection shadeDirection) {
            Cell currentCell = cell;
            Set<ShadeSource> shadedCells = new HashSet<>();
            for (int i = 0; i < size; i++) {
                currentCell = currentCell.neighbors.get(shadeDirection);
                shadedCells.add(new ShadeSource(currentCell, size));
            }
            return shadedCells;
        }
    }
}
