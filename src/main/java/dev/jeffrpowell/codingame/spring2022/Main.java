package dev.jeffrpowell.codingame.spring2022;

import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.Scanner;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.stream.Collectors;

public class Main {
    private static final Point2D MAX_PT = new Point2D.Double(17630, 9000);
    private static final Point2D MIN_PT = new Point2D.Double(0, 0);
    private static final Point2D MIN_IDLE_CENTER = new Point2D.Double(10400, 6000);
    private static final Point2D MIN_IDLE_CLOSE_WING = new Point2D.Double(7000, 800);
    private static final Point2D MIN_IDLE_FAR_WING = new Point2D.Double(3500, 6000);
    private static final Point2D EXPLORE_CENTER = new Point2D.Double(8700, 4500);
    private static final Point2D EXPLORE_TR_WING = new Point2D.Double(11000, 1600);
    private static final Point2D EXPLORE_BL_WING = new Point2D.Double(6000, 7300);
    private static final Point2D MAX_IDLE_CENTER = new Point2D.Double(MAX_PT.getX() - MIN_IDLE_CENTER.getX(), MAX_PT.getY() - MIN_IDLE_CENTER.getY());
    private static final Point2D MAX_IDLE_FAR_WING = new Point2D.Double(MAX_PT.getX() - MIN_IDLE_CLOSE_WING.getX(), MAX_PT.getY() - MIN_IDLE_CLOSE_WING.getY());
    private static final Point2D MAX_IDLE_CLOSE_WING = new Point2D.Double(MAX_PT.getX() - MIN_IDLE_FAR_WING.getX(), MAX_PT.getY() - MIN_IDLE_FAR_WING.getY());
    private static final int BASE_RANGE = 5000;
    private static final int HERO_ATTACK_DISTANCE = 800;

    public static void main(String args[]) {
        Scanner in = new Scanner(System.in);
        GameState state = new GameState();
        HeroCoordinator heroCoordinator = new HeroCoordinator(state);
        int baseX = in.nextInt(); // The corner of the map representing your base
        int baseY = in.nextInt();
        Point2D idlePositionCenter;
        Point2D idlePositionFarWing;
        Point2D idlePositionCloseWing;
        Point2D explorePositionCenter;
        Point2D explorePositionFarWing;
        Point2D explorePositionCloseWing;
        if (baseX == 0) {
            state.baseXY = MIN_PT;
            state.oppositeBaseXY = MAX_PT;
            idlePositionCenter = MIN_IDLE_CENTER;
            idlePositionFarWing = MIN_IDLE_FAR_WING;
            idlePositionCloseWing = MIN_IDLE_CLOSE_WING;
            explorePositionCenter = EXPLORE_CENTER;
            explorePositionFarWing = EXPLORE_BL_WING;
            explorePositionCloseWing = EXPLORE_TR_WING;
        } else {
            state.baseXY = MAX_PT;
            state.oppositeBaseXY = MIN_PT;
            idlePositionCenter = MAX_IDLE_CENTER;
            idlePositionFarWing = MAX_IDLE_FAR_WING;
            idlePositionCloseWing = MAX_IDLE_CLOSE_WING;
            explorePositionCenter = EXPLORE_CENTER;
            explorePositionFarWing = EXPLORE_TR_WING;
            explorePositionCloseWing = EXPLORE_BL_WING;
        }
        int heroesPerPlayer = in.nextInt(); // Always 3
        
        // game loop
        while (true) {
            for (int i = 0; i < 2; i++) {
                int health = in.nextInt(); // Your base health
                int mana = in.nextInt(); // Ignore in the first league; Spend ten mana to cast a spell
                if (i == 0) {
                    state.myMana = mana;
                }
            }
            int entityCount = in.nextInt(); // Amount of heros and monsters you can see
            for (int i = 0; i < entityCount; i++) {
                int id = in.nextInt(); // Unique identifier
                int type = in.nextInt(); // 0=monster, 1=your hero, 2=opponent hero
                if (type == 0) {
                    System.err.println("Monster " + id + " is visible");
                }
                int x = in.nextInt(); // Position of this entity
                int y = in.nextInt();
                int shieldLife = in.nextInt(); // Ignore for this league; Count down until shield spell fades
                int isControlled = in.nextInt(); // Ignore for this league; Equals 1 when this entity is under a control spell
                int health = in.nextInt(); // Remaining health of this monster
                int vx = in.nextInt(); // Trajectory of this monster
                int vy = in.nextInt();
                int nearBase = in.nextInt(); // 0=monster with no target yet, 1=monster targeting a base
                int threatFor = in.nextInt(); // Given this monster's trajectory, is it a threat to 1=your base, 2=your opponent's base, 0=neither
                if (type == 0) {
                    if (threatFor == 1) {
                        state.threateningMonsters.add(new Monster(id, x, y, health, vx, vy, nearBase == 1, shieldLife > 0, state));
                    } else if (threatFor == 0){
                        state.wanderingMonsters.add(new Monster(id, x, y, health, vx, vy, false, shieldLife > 0, state));
                    } else if (threatFor == 2) {
                        state.helpfulMonsters.add(new Monster(id, x, y, health, vx, vy, nearBase == 1, shieldLife > 0, state));
                    }
                }
                else if (type == 1) {
                    if (state.turn == 0){
                        Point2D idlePt = idlePositionCenter;
                        Point2D explorePt = explorePositionCenter;
                        if (id == 1 || id == 4) {
                            idlePt = idlePositionCloseWing;
                            explorePt = explorePositionCloseWing;
                        } else if (id == 2 || id == 5) {
                            idlePt = idlePositionFarWing;
                            explorePt = explorePositionFarWing;
                        }
                        state.heroes.put(id, new Hero(id, x, y, idlePt, explorePt, shieldLife > 0, getEuclideanDistance(new Point2D.Double(x, y), state.baseXY) <= BASE_RANGE, state));
                    }
                    else {
                        state.heroes.get(id).updateHero(x, y, shieldLife > 0, getEuclideanDistance(new Point2D.Double(x, y), state.baseXY) <= BASE_RANGE);
                    }
                }
                else if (type == 2) {
                    System.err.println("Hero " + id + " (" + x + " " + y + ") is visible");
                    double distanceToBase = getEuclideanDistance(new Point2D.Double(x, y), state.baseXY);
                    state.enemyHeroes.putIfAbsent(id, new Hero(id, x, y, shieldLife > 0, distanceToBase <= BASE_RANGE, state));
                    state.enemyHeroes.get(id).updateHero(x, y, shieldLife > 0, distanceToBase <= BASE_RANGE);
                }
            }
            state.enemyHeroes.values().stream().filter(h -> getEuclideanDistance(h.getXy(), state.baseXY) <= getEuclideanDistance(h.getXy(), state.oppositeBaseXY)).forEach(state.enemiesInMyTerritory::add);
            state.enemyInMyTerritory = !state.enemiesInMyTerritory.isEmpty();
            heroCoordinator.executeMoves();
            state.endTurn();
        }
    }

    private static class GameState {
        int myMana;
        boolean enemyInMyTerritory;
        List<Hero> enemiesInMyTerritory;
        int turn;
        Point2D baseXY;
        Point2D oppositeBaseXY;
        SortedMap<Integer, Hero> heroes;
        SortedMap<Integer, Hero> enemyHeroes;
        PriorityQueue<Monster> threateningMonsters;
        List<Monster> wanderingMonsters;
        PriorityQueue<Monster> helpfulMonsters;
        List<EntityGrouping> priorityMonstersToKill;

        public GameState() {
            this.myMana = 0;
            this.enemyInMyTerritory = false;
            this.enemiesInMyTerritory = new ArrayList<>();
            this.turn = 0;
            this.baseXY = null;
            this.oppositeBaseXY = null;
            this.heroes = new TreeMap<>();
            this.enemyHeroes = new TreeMap<>();
            this.threateningMonsters = new PriorityQueue<>();
            this.wanderingMonsters = new ArrayList<>();
            this.helpfulMonsters = new PriorityQueue<>();
            this.priorityMonstersToKill = new ArrayList<>();
        }

        public void endTurn() {
            turn++;
            heroes.values().forEach(Hero::resetHero);
            threateningMonsters.clear();
            wanderingMonsters.clear();
            helpfulMonsters.clear();
            priorityMonstersToKill.clear();
            enemiesInMyTerritory.clear();
            enemyInMyTerritory = false;
        }
    }

    private static class HeroCoordinator {
        GameState state;

        public HeroCoordinator(GameState state) {
            this.state = state;
        }

        public void executeMoves() {
            analyzeBoardAndUpdateState();
            for (EntityGrouping target : state.priorityMonstersToKill) {
                state.heroes.values().stream()
                    .filter(h -> !h.hasTarget())
                    .min(Comparator.comparing(h -> getEuclideanDistance(h.getXy(), target.getTarget()))).get()
                    .targetThisGroup(target);
            }
            state.heroes.values().stream().forEach(hero -> {
                if (!hero.hasTarget()) {
                    hero.findATarget();
                }
                System.out.println(hero.getTarget().printTarget());
            });
        }

        private void analyzeBoardAndUpdateState() {
            List<Monster> monstersByDistance = state.threateningMonsters.stream().collect(Collectors.toList());
            Iterator<Monster> i = monstersByDistance.iterator();
            EntityGrouping group = null;
            while (i.hasNext() && state.priorityMonstersToKill.size() < state.heroes.size()) {
                Monster next = i.next();
                if (getEuclideanDistance(next.getXy(), state.baseXY) >= getEuclideanDistance(next.getXy(), state.oppositeBaseXY)) {
                    continue;
                }
                System.err.println("Monster " + next.getId() + " is a priority threat. ");
                if (group == null) {
                    group = new EntityGrouping(next, state);
                } else {
                    boolean grouped = group.tryAddingEntity(next);
                    if (!grouped) {
                        System.err.println("It's far enough from the previous group.");
                        state.priorityMonstersToKill.add(group);
                        group = new EntityGrouping(next, state);
                    }
                    else {
                        System.err.println("It's near enough to the previous group.");
                    }
                    state.enemyHeroes.values().stream().forEach(group::tryAddingEntity);
                }
            }
            if (group != null && state.priorityMonstersToKill.size() < state.heroes.size()) {
                state.priorityMonstersToKill.add(group);
            }
            if (state.priorityMonstersToKill.size() < state.heroes.size()) {
                state.enemiesInMyTerritory.stream()
                    .sorted(Comparator.comparing(enemy -> getEuclideanDistance(enemy.getXy(), state.baseXY)))
                    .limit((long)state.heroes.size() - state.priorityMonstersToKill.size())
                    .forEach(enemy -> state.priorityMonstersToKill.add(new EntityGrouping(enemy, state)));
            }
        }
    }

    private abstract static class Entity {
        protected int id;
        protected Point2D xy;
        protected boolean shielded;
        protected boolean insideBase;
        protected GameState state;

        public Entity(int id, Point2D xy, boolean shielded, boolean insideBase, GameState state) {
            this.id = id;
            this.xy = xy;
            this.shielded = shielded;
            this.insideBase = insideBase;
            this.state = state;
        }

        public int getId() {
            return id;
        }

        public Point2D getXy() {
            return xy;
        }

        public boolean isShielded() {
            return shielded;
        }

        public boolean isInsideBase() {
            return insideBase;
        }
        
    }

    private static class Hero extends Entity{
        enum State {EXPLORE, IDLE, ATTACKING}
        IdlePt idleTarget;
        IdlePt exploreTarget;
        Target target;
        boolean hasTarget;
        boolean couldUseBackup;
        State heroState;

        public Hero(int id, int x, int y, Point2D idleTargetPt, Point2D exploreTargetPt, boolean shielded, boolean insideBase, GameState state) {
            super(id, new Point2D.Double(x, y), shielded, insideBase, state);
            this.idleTarget = new IdlePt(idleTargetPt);
            this.exploreTarget = new IdlePt(exploreTargetPt);
            this.target = null;
            this.hasTarget = false;
            this.couldUseBackup = false;
            this.heroState = State.IDLE;
        }

        /**
         * Enemy hero constructor
         */
        public Hero(int id, int x, int y, boolean shielded, boolean insideBase, GameState state) {
            super(id, new Point2D.Double(x, y), shielded, insideBase, state);
            this.idleTarget = null;
            this.exploreTarget = null;
            this.target = null;
            this.hasTarget = false;
            this.couldUseBackup = false;
            this.heroState = State.IDLE;
        }

        public void updateHero(int x, int y, boolean shielded, boolean insideBase) {
            this.xy = new Point2D.Double(x, y);
            this.shielded = shielded;
            this.insideBase = insideBase;
        }

        public void resetHero() {
            this.target = null;
            this.hasTarget = false;
            this.couldUseBackup = false;
        }

        public boolean hasTarget() {
            return hasTarget;
        }

        public boolean couldUseBackup() {
            return couldUseBackup;
        }

        public void targetThisGroup(EntityGrouping group) {
            this.hasTarget = true;
            this.couldUseBackup = group.getEntities().size() > 2;
            if (state.myMana >= 10 && entityGroupIsTooClose(group) && entityGroupIsUnshielded(group)) {
                Entity entityClosestToBase = group.getEntityClosestToBase();
                double distanceToFurthestEntity = getEuclideanDistance(xy, entityClosestToBase.getXy());
                if (distanceToFurthestEntity <= WindSpell.RANGE) {
                    System.err.println("Hero " + id + " is too close to home; wind spell");
                    this.target = new WindSpell(state.oppositeBaseXY);
                    return;
                }
            }
            System.err.println("Hero " + id + " is tracking group containing " + group.getEntities().stream().map(Entity::getId).map(i -> i.toString()).collect(Collectors.joining(",")));
            this.target = group;
        }

        public void findATarget() {
            List<Hero> otherHeroes = state.heroes.values().stream().filter(h -> h.getId() != this.id).collect(Collectors.toList());
            //PROVIDE URGENT BACKUP
            Optional<Target> needsBackup = otherHeroes.stream()
                .filter(Hero::couldUseBackup)
                .map(Hero::getTarget)
                .min(Comparator.comparing(t -> getEuclideanDistance(t.getTarget(), xy)));
            if (needsBackup.isPresent()) {
                System.err.println("Hero " + id + " giving backup");
                target = needsBackup.get();
                heroState = State.IDLE;
                return;
            }
            //GATHER WILD MANA SAFELY
            if (state.enemyInMyTerritory) {
                Optional<Monster> closestMonster = state.wanderingMonsters.stream()
                    .filter(m -> state.enemiesInMyTerritory.stream().anyMatch(enemy -> getEuclideanDistance(m.getXy(), enemy.getXy()) < (2 * HERO_ATTACK_DISTANCE)))
                    .min(Comparator.comparing(m -> getEuclideanDistance(xy, m.getXy())));
                if (closestMonster.isPresent() && getEuclideanDistance(closestMonster.get().getXy(), xy) < BASE_RANGE) {
                    target = new EntityGrouping(closestMonster.get(), state);
                    System.err.println("Hero " + id + " gathering mana from safe monster " + closestMonster.get().getId());
                    heroState = State.IDLE;
                    return;
                }
            }
            //GATHER WILD MANA FREELY
            else {
                Optional<Monster> closestMonster = state.wanderingMonsters.stream().min(Comparator.comparing(m -> getEuclideanDistance(xy, m.getXy())));
                if (closestMonster.isPresent() && getEuclideanDistance(closestMonster.get().getXy(), xy) < BASE_RANGE) {
                    target = new EntityGrouping(closestMonster.get(), state);
                    System.err.println("Hero " + id + " gathering mana from monster " + closestMonster.get().getId());
                    heroState = State.IDLE;
                    return;
                }
            }
            //EXPLORE
            if (heroState == State.EXPLORE && getEuclideanDistance(xy, exploreTarget.getTarget()) > (2 * HERO_ATTACK_DISTANCE)) {
                System.err.println("Hero " + id + " not finding anything; exploring now");
                this.target = exploreTarget;
            }
            else if (heroState == State.EXPLORE && getEuclideanDistance(xy, exploreTarget.getTarget()) <= (2 * HERO_ATTACK_DISTANCE)){
                heroState = State.IDLE;
            }
            System.err.println("Hero " + id + " not finding anything; idling now");
            this.target = idleTarget;
            if (heroState == State.IDLE && getEuclideanDistance(xy, idleTarget.getTarget()) <= HERO_ATTACK_DISTANCE){
                heroState = State.EXPLORE;
            }
        }

        private static boolean entityGroupIsTooClose(EntityGrouping group) {
            if (group.getEntities().isEmpty()) {
                return false;
            }
            return group.getEntityFurthestFromBase().isInsideBase();
        }

        private static boolean entityGroupIsUnshielded(EntityGrouping group) {
            if (group.getEntities().isEmpty()) {
                return false;
            }
            return !group.getEntityClosestToBase().isShielded();
        }

        public Target getTarget() {
            if (target != null) {
                return target;
            }
            System.err.println("Hero " + id + " unexpectedly lacking a target, going to center");
            return new IdlePt(EXPLORE_CENTER);
        }
        
    }


    private static class Monster extends Entity implements Comparable<Monster>, Comparator<Monster>{
        static final int SPEED = 400;
        static final int ATTACK_DISTANCE = 300;
        int health;
        Point2D reverseDirection;

        public Monster(int id, int x, int y, int health, int vx, int vy, boolean insideBase, boolean shielded, GameState state) {
            super(id, new Point2D.Double(x, y), shielded, insideBase, state);
            this.health = health;
            this.reverseDirection = applyVectorToPt(new Point2D.Double(-vx, -vy), this.xy);
        }

        @Override
        public int compare(Monster m1, Monster m2) {
            int dist = Double.compare(getEuclideanDistance(m1.xy, state.baseXY), getEuclideanDistance(m2.xy, state.baseXY));
            if (dist == 0) {
                return Integer.compare(m1.health, m2.health);
            }
            return dist;
        }

        @Override
        public int compareTo(Monster o) {
            return compare(this, o);
        }

        public int getHealth() {
            return health;
        }

        public Point2D getReverseDirection() {
            return reverseDirection;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + id;
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            Monster other = (Monster) obj;
            return id == other.id;
        }
    }

    private static interface Target {
        public Point2D getTarget();
        public default String printTarget() {
            return "MOVE " + printPt(getTarget());
        }
    }
    
    private static class IdlePt implements Target {
        private Point2D pt;

        public IdlePt(Point2D pt) {
            this.pt = pt;
        }

        @Override
        public Point2D getTarget() {
            return pt;
        }
    }

    private static class WindSpell implements Target {
        public static int RANGE = 1280;
        public static int PUSH = 2200;
        private Point2D direction;

        public WindSpell(Point2D direction) {
            this.direction = direction;
        }

        @Override
        public Point2D getTarget() {
            return direction;
        }

        @Override
        public String printTarget() {
            return "SPELL WIND " + printPt(direction);
        }
    }

    private static class ControlSpell implements Target {
        public static int RANGE = 2200;
        private int entityId;
        private Point2D direction;

        public ControlSpell(int entityId, Point2D direction) {
            this.entityId = entityId;
            this.direction = direction;
        }

        @Override
        public Point2D getTarget() {
            return direction;
        }

        @Override
        public String printTarget() {
            return "SPELL CONTROL " + entityId + " " + printPt(direction);
        }
    }

    private static class ShieldSpell implements Target {
        public static int RANGE = 2200;
        public static int DURATION = 12;
        private int entityId;

        public ShieldSpell(int entityId) {
            this.entityId = entityId;
        }

        @Override
        public Point2D getTarget() {
            return null;
        }

        @Override
        public String printTarget() {
            return "SPELL SHIELD " + entityId;
        }
    }

    private static class EntityGrouping implements Target{
        List<Entity> entities;
        Point2D center;
        GameState state;

        public EntityGrouping(Entity entity, GameState state) {
            entities = new ArrayList<>();
            entities.add(entity);
            center = entity.getXy();
            this.state = state;
        }

        public boolean tryAddingEntity(Entity m) {
            entities.add(m);
            Point2D newCenter = new Point2D.Double(
                    entities.stream().map(Entity::getXy).map(Point2D::getX).reduce(0D, Double::sum) / entities.size(),
                    entities.stream().map(Entity::getXy).map(Point2D::getY).reduce(0D, Double::sum) / entities.size()
            );
            boolean valid = entities.stream()
                .map(Entity::getXy)
                .map(xy -> getEuclideanDistance(xy, newCenter))
                .allMatch(dist -> dist < HERO_ATTACK_DISTANCE);
            if (!valid) {
                entities.remove(entities.size() - 1);
                return false;
            } else {
                this.center.setLocation(newCenter);
                return true;
            }

        }

        public Entity getEntityClosestToBase() {
            return entities.stream().min(Comparator.comparing(m -> getEuclideanDistance(m.getXy(), state.baseXY))).get();
        }

        public Entity getEntityFurthestFromBase() {
            return entities.stream().max(Comparator.comparing(m -> getEuclideanDistance(m.getXy(), state.baseXY))).get();
        }

        public List<Entity> getEntities() {
            return entities;
        }
        
        @Override
        public Point2D getTarget(){
            return center;
        }
    }

    private static double getEuclideanDistance(Point2D pt1, Point2D pt2) {
        return pt1.distance(pt2);
    }

    private static Point2D applyVectorToPt(Point2D vector, Point2D pt) {
        return new Point2D.Double(pt.getX() + vector.getX(), pt.getY() + vector.getY());
    }

    private static String printPt(Point2D pt) {
        return d2i(pt.getX()) + " " + d2i(pt.getY());
    }

    private static int d2i(Double d) {
        return d.intValue();
    }
}
