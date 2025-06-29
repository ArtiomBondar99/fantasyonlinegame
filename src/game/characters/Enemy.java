//Artiom Bondar:332692730
//Shahar Dahan: 207336355
package game.characters;



import game.map.GameMap;
import game.map.Position;
import game.network.NetworkIdentifiable;
import java.util.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;


public abstract class Enemy extends AbstractCharacter implements NetworkIdentifiable {
    private int loot;
    private final AtomicBoolean active = new AtomicBoolean(false);
    private int networkId = -1;
    
    public Enemy(int loot, Position position) {
        super(position);
        this.loot = loot;
        // Health is set separately via setInitialRandomHealth or builder
    }
    
    @Override
    public int getNetworkId() {
        return networkId;
    }

    @Override
    public void setNetworkId(int id) {
        this.networkId = id;
    }

    

    // Used when we want randomized health on creation
    public void setInitialRandomHealth() {
        setHealth(new Random().nextInt(50) + 1);
    }

    public int getLoot() {
        return loot;
    }

    
    public boolean setLoot(int loot) {
        if (loot >= 0) {
            this.loot = loot;
            return true;
        }
        return false;
    }

    
    public boolean isActive() {
        return active.get();
    }
    
    public void setActive(boolean active) {
        this.active.set(active);
    }

    // Determines whether the player is near enough to activate the enemy
    public void onPlayerMoved(Position pos) {
        active.set(getPosition().distanceTo(pos) <= 2);
    }

    public void takeAction() {
		
	}
	
	// Handles combat logic between this enemy and the player
	public void fightPlayer(PlayerCharacter player) {
	    
	}


    // Moves the enemy one step closer to the player
    public void moveToPlayer(PlayerCharacter player) {
//        GameWorld world = GameWorld.getInstance();
//        Position start = getPosition();
//        Position goal = player.getPosition();
//
//        List<Position> path = findPath(world.getMap(), start, goal);
//        if (path != null && path.size() > 1) {
//            Position nextStep = path.get(1);
//            synchronized (world.getMap()) {
//                world.getMap().moveEntity(world, this, start, nextStep);
//            }
//        }
    }

    // Pathfinding algorithm: A* search (simplified)
    public static List<Position> findPath(GameMap map, Position start, Position goal) {
        Set<Position> closedSet = new HashSet<>();
        Map<Position, Position> cameFrom = new HashMap<>();
        Map<Position, Integer> gScore = new HashMap<>();
        Map<Position, Integer> fScore = new HashMap<>();

        PriorityQueue<Position> openSet = new PriorityQueue<>(Comparator.comparingInt(fScore::get));

        gScore.put(start, 0);
        fScore.put(start, start.distanceTo(goal));
        openSet.add(start);

        while (!openSet.isEmpty()) {
            Position current = openSet.poll();

            if (current.equals(goal)) {
                return reconstructPath(cameFrom, current);
            }

            closedSet.add(current);

            for (Position neighbor : getNeighbors(current, map)) {
                if (closedSet.contains(neighbor)) continue;

                int tentativeG = gScore.getOrDefault(current, Integer.MAX_VALUE) + 1;

                if (tentativeG < gScore.getOrDefault(neighbor, Integer.MAX_VALUE)) {
                    cameFrom.put(neighbor, current);
                    gScore.put(neighbor, tentativeG);
                    fScore.put(neighbor, tentativeG + neighbor.distanceTo(goal));
                    if (!openSet.contains(neighbor)) {
                        openSet.add(neighbor);
                    }
                }
            }
        }

        return null;
    }

    // Reconstructs the path from start to goal
    private static List<Position> reconstructPath(Map<Position, Position> cameFrom, Position current) {
        List<Position> path = new LinkedList<>();
        path.add(current);
        while (cameFrom.containsKey(current)) {
            current = cameFrom.get(current);
            path.add(0, current);
        }
        return path;
    }

    // Returns all 4 valid neighboring positions (up/down/left/right)
    private static List<Position> getNeighbors(Position pos, GameMap map) {
        List<Position> neighbors = new ArrayList<>();
        int[][] directions = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

        for (int[] d : directions) {
            Position next = new Position(pos.getRow() + d[0], pos.getCol() + d[1]);
            if (map.isValidPosition(next) && !map.isWall(next)) {
                neighbors.add(next);
            }
        }

        return neighbors;
    }

    public String getOriginalName() {
        return getClass().getSimpleName();
    }

}
