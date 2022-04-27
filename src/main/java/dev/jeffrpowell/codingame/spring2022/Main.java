package dev.jeffrpowell.codingame.spring2022;

import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.Scanner;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.ResourceBundle.Control;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Main {
    private static final int BASE_RANGE = 5000;

    public static void main(String args[]) {
        Scanner in = new Scanner(System.in);
        int baseX = in.nextInt(); // The corner of the map representing your base
        int baseY = in.nextInt();
        int heroesPerPlayer = in.nextInt(); // Always 3
        GameState state = new GameState();
        HeroCoordinator heroCoordinator = new HeroCoordinator(state, baseX);
        
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
                        state.heroes.put(id, new Hero(id, x, y, shieldLife > 0, getEuclideanDistance(new Point2D.Double(x, y), state.baseXY) <= BASE_RANGE, isControlled == 1, state));
                    }
                    else {
                        state.heroes.get(id).updateHero(x, y, shieldLife > 0, getEuclideanDistance(new Point2D.Double(x, y), state.baseXY) <= BASE_RANGE, isControlled == 1);
                    }
                }
                else if (type == 2) {
                    debug(heroToString(id) + " (" + x + " " + y + ") is visible");
                    double distanceToBase = getEuclideanDistance(new Point2D.Double(x, y), state.baseXY);
                    state.enemyHeroes.putIfAbsent(id, new Hero(id, x, y, shieldLife > 0, distanceToBase <= BASE_RANGE, isControlled == 1, state));
                    state.enemyHeroes.get(id).updateHero(x, y, shieldLife > 0, distanceToBase <= BASE_RANGE, isControlled == 1);
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
        List<Monster> helpfulMonsters;
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
            this.helpfulMonsters = new ArrayList<>();
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
        private static final Point2D MAX_PT = new Point2D.Double(17630, 9000);
        private static final Point2D MIN_PT = new Point2D.Double(0, 0);
        private static final Point2D MIN_IDLE_CENTER = new Point2D.Double(6000, 3500);
        private static final Point2D MIN_IDLE_CLOSE_WING = new Point2D.Double(7000, 800);
        private static final Point2D MIN_IDLE_FAR_WING = new Point2D.Double(3500, 6000);
        private static final Point2D MIN_ATTACK_FAR_WING = new Point2D.Double(1500, 4500);
        private static final Point2D MIN_ATTACK_CLOSE_WING = new Point2D.Double(4500, 1500);
        private static final Point2D EXPLORE_CENTER = new Point2D.Double(8700, 4500);
        private static final Point2D EXPLORE_TR_WING = new Point2D.Double(11000, 1600);
        private static final Point2D EXPLORE_BL_WING = new Point2D.Double(6000, 7300);
        private static final Point2D MAX_IDLE_CENTER = new Point2D.Double(MAX_PT.getX() - MIN_IDLE_CENTER.getX(), MAX_PT.getY() - MIN_IDLE_CENTER.getY());
        private static final Point2D MAX_IDLE_FAR_WING = new Point2D.Double(MAX_PT.getX() - MIN_IDLE_CLOSE_WING.getX(), MAX_PT.getY() - MIN_IDLE_CLOSE_WING.getY());
        private static final Point2D MAX_IDLE_CLOSE_WING = new Point2D.Double(MAX_PT.getX() - MIN_IDLE_FAR_WING.getX(), MAX_PT.getY() - MIN_IDLE_FAR_WING.getY());
        private static final Point2D MAX_ATTACK_FAR_WING = new Point2D.Double(MAX_PT.getX() - MIN_ATTACK_CLOSE_WING.getX(), MAX_PT.getY() - MIN_ATTACK_CLOSE_WING.getY());
        private static final Point2D MAX_ATTACK_CLOSE_WING = new Point2D.Double(MAX_PT.getX() - MIN_ATTACK_FAR_WING.getX(), MAX_PT.getY() - MIN_ATTACK_FAR_WING.getY());
    
        GameState state;
        Point2D idlePositionCenter;
        Point2D idlePositionFarWing;
        Point2D idlePositionCloseWing;
        Point2D explorePositionCenter;
        Point2D explorePositionFarWing;
        Point2D explorePositionCloseWing;
        Point2D attackFarWing;
        Point2D attackCloseWing;

        public HeroCoordinator(GameState state, int baseX) {
            this.state = state;
            identifyKeyLocations(baseX);
        }

        private void identifyKeyLocations(int baseX) {
            if (baseX == 0) {
                state.baseXY = MIN_PT;
                state.oppositeBaseXY = MAX_PT;
                idlePositionCenter = MIN_IDLE_CENTER;
                idlePositionFarWing = MIN_IDLE_FAR_WING;
                idlePositionCloseWing = MIN_IDLE_CLOSE_WING;
                explorePositionCenter = EXPLORE_CENTER;
                explorePositionFarWing = EXPLORE_BL_WING;
                explorePositionCloseWing = EXPLORE_TR_WING;
                attackFarWing = MAX_ATTACK_FAR_WING;
                attackCloseWing = MAX_ATTACK_CLOSE_WING;
            } else {
                state.baseXY = MAX_PT;
                state.oppositeBaseXY = MIN_PT;
                idlePositionCenter = MAX_IDLE_CENTER;
                idlePositionFarWing = MAX_IDLE_FAR_WING;
                idlePositionCloseWing = MAX_IDLE_CLOSE_WING;
                explorePositionCenter = EXPLORE_CENTER;
                explorePositionFarWing = EXPLORE_TR_WING;
                explorePositionCloseWing = EXPLORE_BL_WING;
                attackFarWing = MIN_ATTACK_FAR_WING;
                attackCloseWing = MIN_ATTACK_CLOSE_WING;
            }
        }

        public void executeMoves() {
            analyzeBoardAndUpdateState();
            int numAttackers = numberOfAttackers();
            Set<Hero> attackers = state.heroes.values().stream()
                .sorted(Comparator.comparing(h -> getEuclideanDistance(h.getXy(), state.oppositeBaseXY)))
                .limit(numAttackers)
                .collect(Collectors.toSet());
            Set<Hero> defenders = state.heroes.values().stream()
                .filter(h -> !attackers.contains(h))
                .collect(Collectors.toSet());
            handleDefenders(defenders);
            handleAttackers(attackers);
            state.heroes.values().stream().forEach(hero -> 
                System.out.println(hero.getTarget().printTarget())
            );
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
                debug("Monster " + next.getId() + " is a priority threat. ");
                if (group == null) {
                    group = new EntityGrouping(next, state);
                } else {
                    boolean grouped = group.tryAddingEntity(next);
                    if (!grouped) {
                        debug("It's far enough from the previous group.");
                        state.priorityMonstersToKill.add(group);
                        group = new EntityGrouping(next, state);
                    }
                    else {
                        debug("It's near enough to the previous group.");
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

        private int numberOfAttackers() {
            if (state.myMana < 40) {
                return 0;
            }
            switch (state.enemiesInMyTerritory.size()) {
                case 0:
                    return 2;
                default:
                    return 1;
            }
        }

        private void handleDefenders(Set<Hero> defenders) {
            Optional<Hero> controlledHero = defenders.stream().filter(Hero::isControlled).findAny();
            if (controlledHero.isPresent() && !controlledHero.get().isShielded()) {
                defenders.stream()
                    .filter(h -> h.getId() != controlledHero.get().getId())
                    .filter(h -> !h.isControlled())
                    .sorted(Comparator.comparing(h -> h.distanceToEntity(controlledHero.get())))
                    .limit(1)
                    .forEach(h -> h.saveControlledDefender(controlledHero.get()));
            }
            for (EntityGrouping target : state.priorityMonstersToKill) {
                defenders.stream()
                    .filter(Hero::canAcceptPriorityTarget)
                    .min(Comparator.comparing(h -> getEuclideanDistance(h.getXy(), target.getTarget())))
                    .ifPresent(h -> h.targetThisGroup(target));
            }
            identifyIdleExplorePts(defenders);
        }

        private void identifyIdleExplorePts(Set<Hero> defenders) {
            List<Point2D> idlePts;
            List<Point2D> explorePts;
            switch (defenders.size()) {
                case 1:
                    idlePts.add(idlePositionCenter);
                    explorePts.add(explorePositionCenter);
                    break;
                case 2:
                    idlePts.add(idlePositionCloseWing);
                    idlePts.add(idlePositionFarWing);
                    explorePts.add(explorePositionCloseWing);
                    explorePts.add(explorePositionFarWing);
                    break;
                case 3:
                    idlePts.add(idlePositionCloseWing);
                    idlePts.add(idlePositionFarWing);
                    idlePts.add(idlePositionCenter);
                    explorePts.add(explorePositionCloseWing);
                    explorePts.add(explorePositionFarWing);
                    explorePts.add(explorePositionCenter);
                    break;
            }
            defenders.stream().forEach(hero -> {
                if (!hero.hasTarget()) {
                    hero.findATargetForDefense();
                }
            });
        }

        private void handleAttackers(Set<Hero> attackers) {
            attackers.stream().forEach(h -> h.findATargetForAttack(attackFarWing, attackCloseWing));
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
        
        public double distanceToEntity(Entity e) {
            return getEuclideanDistance(xy, e.getXy());
        }
    }

    private static class Hero extends Entity{
        public static final int ATTACK_DISTANCE = 800;
        enum HeroState {EXPLORE, IDLE, SAVE_DEFENDER}
        Target target;
        boolean hasTarget;
        boolean controlled;
        HeroState heroState;

        public Hero(int id, int x, int y, boolean shielded, boolean insideBase, boolean controlled, GameState state) {
            super(id, new Point2D.Double(x, y), shielded, insideBase, state);
            this.target = null;
            this.hasTarget = false;
            this.controlled = controlled;
            this.heroState = HeroState.IDLE;
        }

        public void updateHero(int x, int y, boolean shielded, boolean insideBase, boolean controlled) {
            this.xy = new Point2D.Double(x, y);
            this.shielded = shielded;
            this.insideBase = insideBase;
            this.controlled = controlled;
        }
        
        public void saveControlledDefender(Hero controlledDefender) {
            if (distanceToEntity(controlledDefender) < ShieldSpell.RANGE) {
                debug(heroToString(id) + " is shielding " + heroToString(controlledDefender.getId()));
                this.target = new ShieldSpell(controlledDefender.getId());
                this.hasTarget = true;
            }
        }

        public void resetHero() {
            this.target = null;
            this.hasTarget = false;
        }

        public boolean hasTarget() {
            return hasTarget;
        }

        public boolean isControlled() {
            return controlled;
        }

        public boolean canAcceptPriorityTarget() {
            return state.myMana >= 10
                && !controlled
                && !hasTarget;
        }

        public void targetThisGroup(EntityGrouping group) {
            this.hasTarget = true;
            if (state.myMana >= 10 && entityGroupIsTooClose(group) && entityGroupIsUnshielded(group)) {
                Entity entityClosestToBase = group.getEntityClosestToBase();
                double distanceToFurthestEntity = distanceToEntity(entityClosestToBase);
                if (distanceToFurthestEntity <= WindSpell.RANGE) {
                    debug(heroToString(id) + " is too close to home; wind spell");
                    this.target = new WindSpell(state.oppositeBaseXY);
                    return;
                }
            }
            debug(heroToString(id) + " is tracking group containing " + group.getEntities().stream().map(Entity::getId).map(i -> i.toString()).collect(Collectors.joining(",")));
            this.target = group;
        }

        public void findATargetForDefense(Point2D idlePt, Point2D explorePt) {
            //GATHER WILD MANA SAFELY
            if (state.enemyInMyTerritory) {
                Optional<Monster> closestMonster = state.wanderingMonsters.stream()
                    .filter(m -> state.enemiesInMyTerritory.stream().anyMatch(enemy -> distanceToEntity(m) < (2 * Hero.ATTACK_DISTANCE)))
                    .min(Comparator.comparing(this::distanceToEntity));
                if (closestMonster.isPresent() && distanceToEntity(closestMonster.get()) < BASE_RANGE) {
                    target = new EntityGrouping(closestMonster.get(), state);
                    debug(heroToString(id) + " gathering mana from safe monster " + closestMonster.get().getId());
                    heroState = HeroState.IDLE;
                    return;
                }
            }
            //GATHER WILD MANA FREELY
            else {
                Optional<Monster> closestMonster = state.wanderingMonsters.stream().min(Comparator.comparing(this::distanceToEntity));
                if (closestMonster.isPresent() && distanceToEntity(closestMonster.get()) < BASE_RANGE) {
                    target = new EntityGrouping(closestMonster.get(), state);
                    debug(heroToString(id) + " gathering mana from monster " + closestMonster.get().getId());
                    heroState = HeroState.IDLE;
                    return;
                }
            }
            //EXPLORE
            if (heroState == HeroState.EXPLORE && getEuclideanDistance(xy, explorePt) > (2 * Hero.ATTACK_DISTANCE)) {
                debug(heroToString(id) + " not finding anything; exploring now");
                this.target = new IdlePt(explorePt);
            }
            else if (heroState == HeroState.EXPLORE && getEuclideanDistance(xy, explorePt) <= (2 * Hero.ATTACK_DISTANCE)){
                heroState = HeroState.IDLE;
            }
            debug(heroToString(id) + " not finding anything; idling now");
            this.target = new IdlePt(idlePt);
            if (heroState == HeroState.IDLE && getEuclideanDistance(xy, idlePt) <= Hero.ATTACK_DISTANCE){
                heroState = HeroState.EXPLORE;
            }
        }

        public void findATargetForAttack(Point2D sweepFar, Point2D sweepClose) {
            //MONSTER CAN KAMIKAZE
            Optional<Monster> kamikazeMonster = state.helpfulMonsters.stream()
                .filter(m -> !m.isShielded())
                .filter(m -> m.strikesLeft() >= m.turnsLeftToReachBase(state.oppositeBaseXY))
                .sorted(Comparator.comparing(m -> getEuclideanDistance(m.getXy(), state.oppositeBaseXY)))
                .findFirst();
            if (kamikazeMonster.isPresent()) {
                this.target = new ShieldSpell(kamikazeMonster.get().getId());
                heroState = HeroState.IDLE;
                return;
            }
            //SEND MONSTER ACROSS BOUNDARY
            Optional<Monster> closeMonster = Stream.of(state.helpfulMonsters, state.wanderingMonsters)
                .flatMap(List::stream)
                .filter(m -> {
                    double dist = getEuclideanDistance(m.getXy(), state.oppositeBaseXY);
                    return dist > BASE_RANGE && dist < BASE_RANGE + WindSpell.RANGE;
                })
                .filter(m -> m.strikesLeft() > 2)
                .filter(m -> distanceToEntity(m) < WindSpell.RANGE)
                .findAny();
            if (closeMonster.isPresent()) {
                this.target = new WindSpell(state.oppositeBaseXY);
                heroState = HeroState.IDLE;
                return;
            }
            //REDIRECT WANDERING MONSTER
            Optional<Monster> wanderer = state.wanderingMonsters.stream()
                .filter(m -> distanceToEntity(m) < ControlSpell.RANGE)
                .findAny();
            if (wanderer.isPresent()) {
                Point2D t = Stream.of(sweepFar, sweepClose)
                    .min(Comparator.comparing(p -> getEuclideanDistance(wanderer.get().getXy(), p)))
                    .get();
                this.target = new ControlSpell(wanderer.get().getId(), t);
                heroState = HeroState.IDLE;
                return;
            }
            //EXPLORE
            if (heroState == HeroState.EXPLORE && getEuclideanDistance(xy, sweepFar) > (2 * Hero.ATTACK_DISTANCE)) {
                debug(heroToString(id) + " not finding anything; sweeping far");
                this.target = new IdlePt(sweepFar);
            }
            else if (heroState == HeroState.EXPLORE && getEuclideanDistance(xy, sweepFar) <= (2 * Hero.ATTACK_DISTANCE)){
                heroState = HeroState.IDLE;
            }
            debug(heroToString(id) + " not finding anything; sweeping close");
            this.target = new IdlePt(sweepClose);
            if (heroState == HeroState.IDLE && getEuclideanDistance(xy, sweepClose) <= Hero.ATTACK_DISTANCE){
                heroState = HeroState.EXPLORE;
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
            debug(heroToString(id) + " unexpectedly lacking a target");
            return new IdlePt(Stream.of(state.baseXY, state.oppositeBaseXY)
                .max(Comparator.comparing(p -> getEuclideanDistance(xy, p)))
                .get());
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

        public int strikesLeft() {
            return health / 2;
        }

        public int turnsLeftToReachBase(Point2D targetBase) {
            //TODO: account for ATTACK_DISTANCE
            return d2i(Math.ceil(getEuclideanDistance(xy, targetBase) / SPEED)); 
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
                .allMatch(dist -> dist < Hero.ATTACK_DISTANCE);
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

    private static void debug(String message) {
        System.err.println(message);
    }

    private static String heroToString(int id) {
        return "Hero " + id;
    }
}
