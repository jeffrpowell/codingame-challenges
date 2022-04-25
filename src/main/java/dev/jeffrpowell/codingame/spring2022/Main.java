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
import java.util.stream.Stream;

public class Main {
    private static final Point2D MAX_PT = new Point2D.Double(17630, 9000);
    private static final Point2D MIN_PT = new Point2D.Double(0, 0);
    private static final Point2D MIN_IDLE_CENTER = new Point2D.Double(6000, 3500);
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
    private static Point2D baseXY;
    private static Point2D oppositeBaseXY;

    public static void main(String args[]) {
        Scanner in = new Scanner(System.in);
        int baseX = in.nextInt(); // The corner of the map representing your base
        int baseY = in.nextInt();
        Point2D idlePositionCenter;
        Point2D idlePositionFarWing;
        Point2D idlePositionCloseWing;
        Point2D explorePositionCenter;
        Point2D explorePositionFarWing;
        Point2D explorePositionCloseWing;
        if (baseX == 0) {
            baseXY = MIN_PT;
            oppositeBaseXY = MAX_PT;
            idlePositionCenter = MIN_IDLE_CENTER;
            idlePositionFarWing = MIN_IDLE_FAR_WING;
            idlePositionCloseWing = MIN_IDLE_CLOSE_WING;
            explorePositionCenter = EXPLORE_CENTER;
            explorePositionFarWing = EXPLORE_BL_WING;
            explorePositionCloseWing = EXPLORE_TR_WING;
        } else {
            baseXY = MAX_PT;
            oppositeBaseXY = MIN_PT;
            idlePositionCenter = MAX_IDLE_CENTER;
            idlePositionFarWing = MAX_IDLE_FAR_WING;
            idlePositionCloseWing = MAX_IDLE_CLOSE_WING;
            explorePositionCenter = EXPLORE_CENTER;
            explorePositionFarWing = EXPLORE_TR_WING;
            explorePositionCloseWing = EXPLORE_BL_WING;
        }
        int heroesPerPlayer = in.nextInt(); // Always 3
        int turn = 0;
        int myMana = 0;
        SortedMap<Integer, Hero> heroes = new TreeMap<>();
        
        // game loop
        while (true) {
            PriorityQueue<Monster> monsters = new PriorityQueue<>();
            List<Monster> nonThreateningMonsters = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                int health = in.nextInt(); // Your base health
                int mana = in.nextInt(); // Ignore in the first league; Spend ten mana to cast a spell
                if (i == 0) {
                    myMana = mana;
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
                        monsters.add(new Monster(id, x, y, health, vx, vy, nearBase == 1, shieldLife > 0));
                    } else if (threatFor == 0){
                        nonThreateningMonsters.add(new Monster(id, x, y, health, vx, vy, false, shieldLife > 0));
                    }
                }
                else if (type == 1) {
                    if (turn == 0){
                        Point2D idlePt = idlePositionCenter;
                        Point2D explorePt = explorePositionCenter;
                        if (id == 1 || id == 4) {
                            idlePt = idlePositionCloseWing;
                            explorePt = explorePositionCloseWing;
                        } else if (id == 2 || id == 5) {
                            idlePt = idlePositionFarWing;
                            explorePt = explorePositionFarWing;
                        }
                        heroes.put(id, new Hero(id, x, y, idlePt, explorePt));
                    }
                    else {
                        heroes.get(id).updateHero(x, y);
                    }
                }
            }
            // In the first league: MOVE <x> <y> | WAIT; In later leagues: | SPELL <spellParams>;
            List<Monster> monstersByDistance = monsters.stream().collect(Collectors.toList());
            List<MonsterGrouping> chosenTargets = new ArrayList<>();
            Iterator<Monster> i = monstersByDistance.iterator();
            MonsterGrouping group = null;
            while (i.hasNext() && chosenTargets.size() < heroesPerPlayer) {
                Monster next = i.next();
                System.err.println("Monster " + next.getId() + " is a priority threat. ");
                if (group == null) {
                    group = new MonsterGrouping(next);
                } else {
                    boolean grouped = group.tryAddingMonster(next);
                    if (!grouped) {
                        System.err.println("It's far enough from the previous group.");
                        chosenTargets.add(group);
                        group = new MonsterGrouping(next);
                    }
                    else {
                        System.err.println("It's near enough to the previous group.");
                    }
                }
            }
            if (group != null && chosenTargets.size() < heroesPerPlayer) {
                chosenTargets.add(group);
            }
            for (MonsterGrouping target : chosenTargets) {
                heroes.values().stream()
                    .filter(h -> !h.hasTarget())
                    .min(Comparator.comparing(h -> getEuclideanDistance(h.getXy(), target.getTarget()))).get()
                    .targetThisGroup(target, myMana);
            }
            List<Hero> heroList = heroes.values().stream().collect(Collectors.toList());
            for (int ih = 0; ih < heroesPerPlayer; ih++) {
                Hero hero = heroList.get(ih);
                if (!hero.hasTarget()) {
                    hero.findATarget(heroList, turn, nonThreateningMonsters);
                }
                System.out.println(hero.getTarget().printTarget());
                hero.resetHero();
            }
            turn++;
        }
    }

    private static class Hero {
        static enum State {EXPLORE, IDLE}
        int id;
        Point2D xy;
        IdlePt idleTarget;
        IdlePt exploreTarget;
        Target target;
        boolean hasTarget;
        boolean couldUseBackup;
        State state;

        public Hero(int id, int x, int y, Point2D idleTargetPt, Point2D exploreTargetPt) {
            this.id = id;
            this.xy = new Point2D.Double(x, y);
            this.idleTarget = new IdlePt(idleTargetPt);
            this.exploreTarget = new IdlePt(exploreTargetPt);
            this.target = null;
            this.hasTarget = false;
            this.couldUseBackup = false;
            state = State.IDLE;
        }

        public void updateHero(int x, int y) {
            this.xy = new Point2D.Double(x, y);
        }

        public void resetHero() {
            this.target = null;
            this.hasTarget = false;
            this.couldUseBackup = false;
        }

        public int getId() {
            return id;
        }

        public Point2D getXy() {
            return xy;
        }

        public boolean hasTarget() {
            return hasTarget;
        }

        public boolean couldUseBackup() {
            return couldUseBackup;
        }

        public void targetThisGroup(MonsterGrouping group, int myMana) {
            this.hasTarget = true;
            this.couldUseBackup = group.getMonsters().size() > 2;
            if (myMana >= 10 && monsterGroupIsTooClose(group) && monsterGroupIsUnshielded(group)) {
                Monster monsterClosestToBase = group.getMonsterClosestToBase();
                double distanceToFurthestMonster = getEuclideanDistance(xy, monsterClosestToBase.getXy());
                if (distanceToFurthestMonster <= 1280) {
                    System.err.println("Hero " + id + " is too close to home; wind spell");
                    this.target = new WindSpell(oppositeBaseXY);
                    return;
                }
                // else if (distanceToFurthestMonster > 1280 && distanceToFurthestMonster <= 2200) {
                //     System.err.println("Monsters are too close to home; trying to redirect and buy time");
                //     this.target = new ControlSpell(monsterClosestToBase.getId(), monsterClosestToBase.getReverseDirection());
                //     return;
                // }
            }
            System.err.println("Hero " + id + " is tracking group containing " + group.getMonsters().stream().map(Monster::getId).map(i -> i.toString()).collect(Collectors.joining(",")));
            this.target = group;
        }

        public void findATarget(List<Hero> allHeroes, int turn, List<Monster> nonThreateningMonsters) {
            List<Hero> otherHeroes = allHeroes.stream().filter(h -> h.getId() != this.id).collect(Collectors.toList());
            //PROVIDE URGENT BACKUP
            Optional<Target> needsBackup = otherHeroes.stream()
                .filter(Hero::couldUseBackup)
                .map(Hero::getTarget)
                .min(Comparator.comparing(t -> getEuclideanDistance(t.getTarget(), xy)));
            if (needsBackup.isPresent()) {
                System.err.println("Hero " + id + " giving backup");
                target = needsBackup.get();
                state = State.IDLE;
                return;
            }
            //GATHER WILD MANA
            Optional<Monster> closestMonster = nonThreateningMonsters.stream().min(Comparator.comparing(m -> getEuclideanDistance(xy, m.getXy())));
            if (closestMonster.isPresent() && getEuclideanDistance(closestMonster.get().getXy(), xy) < BASE_RANGE) {
                target = new MonsterGrouping(closestMonster.get());
                System.err.println("Hero " + id + " gathering mana from monster " + closestMonster.get().getId());
                state = State.IDLE;
                return;
            }
            //EXPLORE
            if (state == State.EXPLORE && getEuclideanDistance(xy, exploreTarget.getTarget()) > 800) {
                System.err.println("Hero " + id + " not finding anything; exploring now");
                this.target = exploreTarget;
            }
            else if (state == State.EXPLORE && getEuclideanDistance(xy, exploreTarget.getTarget()) <= 800){
                state = State.IDLE;
            }
            System.err.println("Hero " + id + " not finding anything; idling now");
            this.target = idleTarget;
        }

        private static boolean monsterGroupIsTooClose(MonsterGrouping group) {
            if (group.getMonsters().isEmpty()) {
                return false;
            }
            return group.getMonsterFurthestFromBase().isInsideBase();
        }

        private static boolean monsterGroupIsUnshielded(MonsterGrouping group) {
            if (group.getMonsters().isEmpty()) {
                return false;
            }
            return !group.getMonsterClosestToBase().isShielded();
        }

        public Target getTarget() {
            if (target != null) {
                return target;
            }
            System.err.println("Hero " + id + " unexpectedly lacking a target, going to center");
            return new IdlePt(EXPLORE_CENTER);
        }
        
    }

    private static class Monster implements Comparable<Monster>, Comparator<Monster> {
        static final int SPEED = 400;
        static final int ATTACK_DISTANCE = 300;
        int id;
        Point2D xy;
        int health;
        Point2D reverseDirection;
        boolean insideBase;
        boolean shielded;

        public Monster(int id, int x, int y, int health, int vx, int vy, boolean insideBase, boolean shielded) {
            this.id = id;
            this.xy = new Point2D.Double(x, y);
            this.health = health;
            this.reverseDirection = applyVectorToPt(new Point2D.Double(-vx, -vy), this.xy);
            this.insideBase = insideBase;
            this.shielded = shielded;
        }

        @Override
        public int compare(Monster m1, Monster m2) {
            int dist = Double.compare(getEuclideanDistance(m1.xy, baseXY), getEuclideanDistance(m2.xy, baseXY));
            if (dist == 0) {
                return Integer.compare(m1.health, m2.health);
            }
            return dist;
        }

        @Override
        public int compareTo(Monster o) {
            return compare(this, o);
        }

        public int getId() {
            return id;
        }

        public Point2D getXy() {
            return xy;
        }

        public int getHealth() {
            return health;
        }

        public Point2D getReverseDirection() {
            return reverseDirection;
        }

        public boolean isInsideBase() {
            return insideBase;
        }

        public boolean isShielded() {
            return shielded;
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
        };
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

    private static class MonsterGrouping implements Target{
        List<Monster> monsters;
        Point2D center;

        public MonsterGrouping(Monster m) {
            monsters = new ArrayList<>();
            monsters.add(m);
            center = m.getXy();
        }

        public boolean tryAddingMonster(Monster m) {
            monsters.add(m);
            Point2D newCenter = new Point2D.Double(
                    monsters.stream().map(Monster::getXy).map(Point2D::getX).reduce(0D, Double::sum) / monsters.size(),
                    monsters.stream().map(Monster::getXy).map(Point2D::getY).reduce(0D, Double::sum) / monsters.size()
            );
            boolean valid = monsters.stream()
                .map(Monster::getXy)
                .map(xy -> getEuclideanDistance(xy, newCenter))
                .allMatch(dist -> dist < HERO_ATTACK_DISTANCE);
            if (!valid) {
                monsters.remove(monsters.size() - 1);
                return false;
            } else {
                this.center.setLocation(newCenter);
                return true;
            }

        }

        public Monster getMonsterClosestToBase() {
            return monsters.stream().min(Comparator.comparing(m -> getEuclideanDistance(m.getXy(), baseXY))).get();
        }

        public Monster getMonsterFurthestFromBase() {
            return monsters.stream().max(Comparator.comparing(m -> getEuclideanDistance(m.getXy(), baseXY))).get();
        }

        public List<Monster> getMonsters() {
            return monsters;
        }

        public Point2D getCenter() {
            return center;
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
