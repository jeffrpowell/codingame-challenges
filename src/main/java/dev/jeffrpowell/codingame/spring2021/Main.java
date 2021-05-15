package dev.jeffrpowell.codingame.spring2021;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Scanner;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.function.BiFunction;
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
        private final MoveManager moveManager;
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
        private boolean gameOver;

        public Game(Map<Integer, Cell> cellMap) {
            this.cellMap = cellMap;
            this.day = -1;
            this.moveManager = new MoveManager(this);
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
            Move nextMove = moveManager.nextMove(day, possibleMoves);
            if (day == 23 && nextMove.action == Action.WAIT) {
                gameOver = true;
            }
            return nextMove.toFlavorfulString();
        }
        
        public boolean isGameOver() {
            return gameOver;
        }
        
        public HexDirection getShadeDirection(int day) {
            return HexDirection.values()[day % 6];
        }
        
        public int numberOfSunPointsComingUp(Tree tree, int startDay, int numberOfDays) {
            int totalPoints = 0;
            int treeSize = tree.getSize();
            for (int i = 1; i <= numberOfDays; i++) {
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
            return totalPoints;
        }
        
        public int numberOfSpookyPointsComingUp(Tree tree, int startDay, int numberOfDays) {
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
            return totalSpookyPoints / 3;
        }
    }
    
    static class BudgetManager {
        
        public int planCompleteBudget(int day, int nutrients, int lastNutrientGrab, int sunPoints, int score, Map<Integer, Integer> myTreeSizes) {
            if (day < 18) {
                return planEarlyGame(day, nutrients, lastNutrientGrab, sunPoints, score, myTreeSizes);
            }
            else {
                return planLateGame(day, nutrients, sunPoints, score, myTreeSizes);
            }
        }
        
        private int planEarlyGame(int day, int nutrients, int lastNutrientGrab, int sunPoints, int score, Map<Integer, Integer> myTreeSizes) {
            int num3Trees = myTreeSizes.getOrDefault(3, 0);
            int costToGrowTo3 = 7 + num3Trees;
            if (nutrients == 19 && score == 0) {
                return 4;
            }
            if (sunPoints < 4 + costToGrowTo3 - 1) {
                //I can't replace a completed tree with a new size-3 tree; just grow this day
                return 0;
            }
            if (num3Trees < 3 || num3Trees == 3 && lastNutrientGrab - nutrients == 1) {
                return 0;
            }
            if (lastNutrientGrab - nutrients == 1 && num3Trees >= 4) {
                return 4;
            }
            Double completeBudgetExact = ((double) day + 1.0) / 24.0 * (double) sunPoints;
            return completeBudgetExact.intValue();
        }
        
        private int planLateGame(int day, int nutrients, int sunPoints, int score, Map<Integer, Integer> myTreeSizes) {
            int num3Trees = myTreeSizes.getOrDefault(3, 0);
            int costToGrowTo3 = 7 + num3Trees;
            if (nutrients <= num3Trees) {
                return walkDownCompleteBudget((num3Trees - nutrients + 1) * 4, sunPoints);
            }
            if (day > 21) {
                return sunPoints;
            }
            if (sunPoints < 4 + costToGrowTo3 - 1) {
                //I can't replace a completed tree with a new size-3 tree; just grow this day
                return 0;
            }
            return 4;
        }
        
        private static int walkDownCompleteBudget(int initialBudgetRequest, int sunPoints) {
            while(initialBudgetRequest > sunPoints) {
                initialBudgetRequest -= 4;
            }
            return initialBudgetRequest;
        }
    }
    
    static class MoveManager {
        enum State {PLAN_COMPLETE, COMPLETE, PLAN_GROW3, GROW3, PLAN_GROW2, GROW2, PLAN_GROW1, GROW1, PLAN_SEED, SEED, WAIT;}
        private final List<Move> moveBuffer;
        private final Game game;
        private final BudgetManager budgetManager;
        private int lastNutrientGrab;
        private State state;
        
        public MoveManager(Game game) {
            this.moveBuffer = new ArrayList<>();
            this.game = game;
            this.budgetManager = new BudgetManager();
            this.lastNutrientGrab = 21;
            this.state = State.PLAN_COMPLETE;
        }
        
        //Plan all moves for a day at once
        //Execute all completes first, then grow in descending order, then seed
        //If nutrients < 5, complete trees in ascending richness order
        //If day < 18 && num3Trees < 4, don't complete unless you can grow a 2-tree to replace it immediately afterward
        //While nutrients > 5, Complete the tree that will get the most shade in the next 2 turns
        
        private void planCompletes(List<Move> possibleCompletes, int budget) {
            if (budget == 0 || possibleCompletes.isEmpty()) {
                return;
            }
            int targetNum = budget / 4;
            if (game.day <= 21 && game.nutrients > game.myTrees.stream().filter(t -> t.getSize() == 3).count()) {
                targetNum = Math.min(targetNum, Long.valueOf(game.myTrees.stream().filter(t -> t.getSize() == 2).count()).intValue());
            }
            if (targetNum >= possibleCompletes.size()) {
                simulateCompletes(possibleCompletes);
                moveBuffer.addAll(possibleCompletes);
                return;
            }
            SortedMap<Integer, List<Move>> spookyPoints = possibleCompletes.stream()
                .collect(Collectors.toMap(
                    move -> game.numberOfSpookyPointsComingUp(game.treeMap.get(move.index), game.day, 2),
                    move -> {List<Move> singletonList = new ArrayList<>(); singletonList.add(move); return singletonList;},
                    (a, b) -> {a.addAll(b); return a;},
                    () -> new TreeMap<>(Comparator.<Integer>reverseOrder()))
                );
            List<Move> mostShadedTrees = new ArrayList<>();
            while(mostShadedTrees.size() < targetNum) {
                List<Move> nextBatchOfMoves = spookyPoints.remove(spookyPoints.firstKey());
                if (mostShadedTrees.size() + nextBatchOfMoves.size() > targetNum) {
                    //secondary sort: prefer completing more rich soil
                    mostShadedTrees.addAll(nextBatchOfMoves.stream()
                        .sorted(Comparator.comparing((Move move) -> game.cellMap.get(move.index).getRichness()).reversed())
                        .limit(targetNum - mostShadedTrees.size())
                        .collect(Collectors.toList()));
                }
                else {
                    mostShadedTrees.addAll(nextBatchOfMoves);
                }
            }
            simulateCompletes(mostShadedTrees);
            moveBuffer.addAll(mostShadedTrees);
        }

        private void simulateCompletes(List<Move> completeMoves) {
            for (Move move : completeMoves) {
                Tree choppedTree = game.treeMap.remove(move.index);
                game.myTrees.remove(choppedTree);
            }
        }
        
        private long getBaseCost(int treeSize) {
            switch (treeSize) {
                case 3:
                    return 7;
                case 2:
                    return 3;
                default:
                    return 1;
            }
        }
        
        private long getActualCost(long baseCost, long numUpgrades) {
            return baseCost * numUpgrades + numUpgrades - 1;
        }
        
        private void planGrowsForSize(List<Move> possibleGrows, int targetTreeSize) {
            if (game.sun == 0 || possibleGrows.isEmpty()) {
                return;
            }
            long baseCost = game.myTrees.stream().filter(t -> t.getSize() == targetTreeSize).count() + getBaseCost(targetTreeSize);
            long maxMoves = game.sun / baseCost;
            if (maxMoves > 1) {
                //since growing more than once will increase the cost by one on successive GROW commands, have to make this adjustment
                if (getActualCost(baseCost, maxMoves) > game.sun) {
                    maxMoves--;
                }
            }
            if (possibleGrows.size() <= maxMoves) {
                simulateGrows(possibleGrows);
                moveBuffer.addAll(possibleGrows);
                return;
            }
            SortedMap<Integer, List<Move>> spookyPoints = possibleGrows.stream()
                .collect(Collectors.toMap(
                    move -> game.numberOfSpookyPointsComingUp(game.treeMap.get(move.index).growTree(), game.day, 2),
                    move -> {List<Move> singletonList = new ArrayList<>(); singletonList.add(move); return singletonList;},
                    (a, b) -> {a.addAll(b); return a;},
                    () -> new TreeMap<>(Comparator.<Integer>naturalOrder()))
                );
            List<Move> leastShadedTrees = new ArrayList<>();
            while(leastShadedTrees.size() < maxMoves) {
                List<Move> nextBatchOfMoves = spookyPoints.remove(spookyPoints.firstKey());
                if (leastShadedTrees.size() + nextBatchOfMoves.size() > maxMoves) {
                    //secondary sort: prefer growing in more rich soil
                    leastShadedTrees.addAll(nextBatchOfMoves.stream()
                        .sorted(Comparator.comparing((Move move) -> game.cellMap.get(move.index).getRichness()).reversed())
                        .limit(maxMoves - leastShadedTrees.size())
                        .collect(Collectors.toList()));
                }
                else {
                    leastShadedTrees.addAll(nextBatchOfMoves);
                }
            }
            simulateGrows(leastShadedTrees);
            moveBuffer.addAll(leastShadedTrees);
        }
        
        private void simulateGrows(List<Move> growMoves) {
            for (Move move : growMoves) {
                Tree oldTree = game.treeMap.remove(move.index);
                Tree newTree = oldTree.growTree();
                game.treeMap.put(move.index, newTree);
                game.myTrees.set(game.myTrees.indexOf(oldTree), newTree);
            }
        }
        
        private void planSeed(List<Move> possibleSeeds) {
            if (shouldWeSkipSeed() || possibleSeeds.isEmpty()) {
                return;
            }
            moveBuffer.add(possibleSeeds.stream().sorted(Comparator.comparing(this::scoreSeedPlacement).reversed()).findFirst().get());
        }
        
        private boolean shouldWeSkipSeed() {
            return game.day <= 1 || game.myTrees.stream().anyMatch(t -> t.getSize() == 0);
        }
    
        private int scoreSeedPlacement(Move move) {
            boolean stillSearching = true;
            int distanceToNearestTree = 0;
            Set<Cell> cells = new HashSet<>();
            cells.add(game.cellMap.get(move.index2));
            while (stillSearching) {
                distanceToNearestTree++;
                cells.addAll(cells.stream()
                    .map(Cell::getNeighbors)
                    .map(Map::values)
                    .flatMap(Collection::stream)
                    .collect(Collectors.toSet()));
                stillSearching = cells.stream()
                    .filter(cell -> game.treeMap.containsKey(cell.index))
                    .map(cell -> game.treeMap.get(cell.index))
                    .noneMatch(Tree::isIsMine);
            }
            int bonus = 0;
            if (distanceToNearestTree == 2) {
                bonus += 3;
            }
            if (game.treeMap.get(move.index).getSize() == 3) {
                bonus++;
            }
            if (game.cellMap.get(move.index2).getRichness() == 3) {
                bonus++;
            }
            return bonus;
        }
        
        public Move nextMove(int day, List<String> possibleMoves) {
            switch (state) {
                /* ------
                    This switch is designed to be very slippery. If you don't have a move at your current state, you should eagerly move on to the next state to find the next best move
                ------
                */
                case PLAN_COMPLETE:
                    moveBuffer.clear();
                    int budget = budgetManager.planCompleteBudget(day, game.nutrients, lastNutrientGrab, game.sun, game.score, game.myTrees.stream().collect(Collectors.groupingBy(Tree::getSize, Collectors.reducing(0, t -> 1, Math::addExact))));
                    planCompletes(possibleMoves.stream().map(Move::new).filter(move -> move.getAction() == Action.COMPLETE).collect(Collectors.toList()), budget);
                    state = State.COMPLETE;
                case COMPLETE:
                    if (!moveBuffer.isEmpty()) {
                        Move nextMove = moveBuffer.remove(0);
                        lastNutrientGrab = game.nutrients;
                        if (moveBuffer.isEmpty()) {
                            state = State.PLAN_GROW3;
                        }
                        return nextMove;
                    }
                case PLAN_GROW3: 
                    moveBuffer.clear();
                    planGrowsForSize(
                        possibleMoves.stream()
                            .map(Move::new)
                            .filter(move -> move.getAction() == Action.GROW)
                            .filter(move -> game.treeMap.get(move.index).getSize() == 2)
                            .collect(Collectors.toList()),
                        3
                    );
                    state = State.GROW3;
                case GROW3:
                    if (!moveBuffer.isEmpty()) {
                        Move nextMove = moveBuffer.remove(0);
                        if (moveBuffer.isEmpty()) {
                            state = State.PLAN_GROW2;
                        }
                        return nextMove;
                    }
                case PLAN_GROW2:
                    moveBuffer.clear();
                    planGrowsForSize(
                        possibleMoves.stream()
                            .map(Move::new)
                            .filter(move -> move.getAction() == Action.GROW)
                            .filter(move -> game.treeMap.get(move.index).getSize() == 1)
                            .collect(Collectors.toList()),
                        2
                    );
                    state = State.GROW2;
                case GROW2:
                    if (!moveBuffer.isEmpty()) {
                        Move nextMove = moveBuffer.remove(0);
                        if (moveBuffer.isEmpty()) {
                            state = State.PLAN_GROW1;
                        }
                        return nextMove;
                    }
                case PLAN_GROW1:
                    moveBuffer.clear();
                    planGrowsForSize(
                        possibleMoves.stream()
                            .map(Move::new)
                            .filter(move -> move.getAction() == Action.GROW)
                            .filter(move -> game.treeMap.get(move.index).getSize() == 0)
                            .collect(Collectors.toList()),
                        1
                    );
                    state = State.GROW1;
                case GROW1:
                    if (!moveBuffer.isEmpty()) {
                        Move nextMove = moveBuffer.remove(0);
                        if (moveBuffer.isEmpty()) {
                            state = State.PLAN_SEED;
                        }
                        return nextMove;
                    }
                case PLAN_SEED:
                    moveBuffer.clear();
                    planSeed(possibleMoves.stream().map(Move::new).filter(move -> move.getAction() == Action.SEED).collect(Collectors.toList()));
                    state = State.WAIT;
                case SEED:
                    if (!moveBuffer.isEmpty()) {
                        Move nextMove = moveBuffer.remove(0);
                        state = State.WAIT;
                        return nextMove;
                    }
                case WAIT:
                default:
                    moveBuffer.clear();
                    state = State.PLAN_COMPLETE;
                    return new Move("WAIT");
            }
        }
    }
    
    static enum Action {
        WAIT((i, j) -> "Soak in the sun, my Arbor Army!"),
        SEED((i, j) -> "#" + i + ", go sneeze on #" + j),
        COMPLETE((i, j) -> "I release you to Mother Earth, #" + i),
        GROW((i, j) -> "Let's give some water to #" + i);

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

        public Action getAction() {
            return action;
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

        public Map<HexDirection, Cell> getNeighbors() {
            return neighbors;
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
        
        public Tree growTree() {
            return new Tree(cell, size + 1, isMine, isDormant);
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

        @Override
        public int hashCode() {
            int hash = 7;
            hash = 59 * hash + Objects.hashCode(this.cell);
            hash = 59 * hash + this.size;
            hash = 59 * hash + (this.isMine ? 1 : 0);
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
            final Tree other = (Tree) obj;
            if (this.size != other.size) {
                return false;
            }
            if (this.isMine != other.isMine) {
                return false;
            }
            return Objects.equals(this.cell, other.cell);
        }
    }
}
