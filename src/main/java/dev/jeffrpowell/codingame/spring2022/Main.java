package dev.jeffrpowell.codingame.spring2022;

import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Scanner;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Main {
    private static final Point2D MAX_PT = new Point2D.Double(17630, 9000);
    private static final Point2D MIN_PT = new Point2D.Double(0, 0);
    private static final Point2D MIN_IDLE_CENTER = new Point2D.Double(5166, 2637);
    private static final Point2D MIN_IDLE_BL_WING = new Point2D.Double(800, 5745);
    private static final Point2D MIN_IDLE_TR_WING = new Point2D.Double(5745, 800);
    private static final Point2D MAX_IDLE_CENTER = new Point2D.Double(12464, 6363);
    private static final Point2D MAX_IDLE_BL_WING = new Point2D.Double(11885, 8200);
    private static final Point2D MAX_IDLE_TR_WING = new Point2D.Double(16830, 3255);
    private static final int HERO_ATTACK_DISTANCE = 800;
    private static Point2D baseXY;
    private static Point2D oppositeBaseXY;
    private static Point2D idlePositionCenter;
    private static Point2D idlePositionBLWing;
    private static Point2D idlePositionTRWing;

    public static void main(String args[]) {
        Scanner in = new Scanner(System.in);
        int baseX = in.nextInt(); // The corner of the map representing your base
        int baseY = in.nextInt();
        if (baseX == 0) {
            baseXY = MIN_PT;
            oppositeBaseXY = MAX_PT;
            idlePositionCenter = MIN_IDLE_CENTER;
            idlePositionBLWing = MIN_IDLE_BL_WING;
            idlePositionTRWing = MIN_IDLE_TR_WING;
        } else {
            baseXY = MAX_PT;
            oppositeBaseXY = MIN_PT;
            idlePositionCenter = MAX_IDLE_CENTER;
            idlePositionBLWing = MAX_IDLE_BL_WING;
            idlePositionTRWing = MAX_IDLE_TR_WING;
        }
        int heroesPerPlayer = in.nextInt(); // Always 3
        int turn = 0;
        
        // game loop
        while (true) {
            TreeMap<Integer, Monster> monsters = new TreeMap<>();
            List<Point2D> heroes = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                int health = in.nextInt(); // Your base health
                int mana = in.nextInt(); // Ignore in the first league; Spend ten mana to cast a spell
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
                if (threatFor == 1 && !monsters.containsKey(id)) {
                    monsters.put(id, new Monster(id, x, y, health, vx, vy));
                }
                if (type == 1) {
                    heroes.add(new Point2D.Double(x, y));
                }
            }
            // In the first league: MOVE <x> <y> | WAIT; In later leagues: | SPELL <spellParams>;
            List<Monster> monstersByDistance = monsters.values().stream().collect(Collectors.toList());
            List<Point2D> chosenTargets = new ArrayList<>();
            Iterator<Monster> i = monstersByDistance.iterator();
            MonsterGrouping group = null;
            while (i.hasNext() && chosenTargets.size() < heroesPerPlayer) {
                Monster next = i.next();
                System.err.print("Monster " + next.getId() + " is a priority threat. ");
                if (group == null) {
                    group = new MonsterGrouping(next);
                } else {
                    boolean grouped = group.tryAddingMonster(next);
                    if (!grouped) {
                        System.err.println("It's far enough from the previous group. Setting target to " + group.getCenter());
                        chosenTargets.add(group.getCenter());
                        group = new MonsterGrouping(next);
                    }
                    else {
                        System.err.println("It's near enough to the previous group.");
                    }
                }
            }
            if (turn < 5 || chosenTargets.isEmpty()) {
                System.err.println("Defaulting to idle positions");
                chosenTargets.add(idlePositionCenter);
                chosenTargets.add(idlePositionTRWing);
                chosenTargets.add(idlePositionBLWing);
            }
            List<Point2D> targetsInHeroOrder = Stream.of(MIN_PT, MIN_PT, MIN_PT).collect(Collectors.toList());
            Set<Point2D> claimedHeroes = new HashSet<>();
            for (Point2D target : chosenTargets) {
                Point2D hero = heroes.stream()
                    .filter(h -> !claimedHeroes.contains(h))
                    .min(Comparator.comparing(h -> getEuclideanDistance(h, target))).get();
                claimedHeroes.add(hero);
                targetsInHeroOrder.set(heroes.indexOf(hero), target);
            }
            for (Point2D target : targetsInHeroOrder) {
                System.out.println("MOVE " + d2i(target.getX()) + " " + d2i(target.getY()));
            }
            turn++;
        }
    }

    private static class Monster implements Comparator<Monster> {
        static final int SPEED = 400;
        static final int ATTACK_DISTANCE = 300;
        int id;
        Point2D xy;
        int health;

        public Monster(int id, int x, int y, int health, int vx, int vy) {
            this.id = id;
            this.xy = applyVectorToPt(new Point2D.Double(vx, vy), new Point2D.Double(x, y));
            this.health = health;
        }

        @Override
        public int compare(Monster m1, Monster m2) {
            int dist = Double.compare(getEuclideanDistance(m1.xy, baseXY), getEuclideanDistance(m2.xy, baseXY));
            if (dist == 0) {
                return Integer.compare(m1.health, m2.health);
            }
            return dist;
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

    }

    private static class MonsterGrouping {
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

        public Point2D getCenter() {
            return center;
        }
    }

    private static double getEuclideanDistance(Point2D pt1, Point2D pt2) {
        return pt1.distance(pt2);
    }

    private static Point2D applyVectorToPt(Point2D vector, Point2D pt) {
        return new Point2D.Double(pt.getX() + vector.getX(), pt.getY() + vector.getY());
    }

    private static int d2i(Double d) {
        return d.intValue();
    }
}
