package game.server;

import game.characters.*;
import game.items.*;
import game.map.*;
import game.network.GameMessage;
import game.network.MessageType;
import game.factory.EnemyFactory;
import game.core.GameEntity;
import game.decorators.*;
import game.logging.LogManager;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Server-side game world that manages all game state and logic.
 * Handles multiple players, enemies, items, and combat.
 * 
 * @author Artiom Bondar:332692730
 * @author Shahar Dahan:207336355
 */
public class ServerGameWorld {
    private final GameServer server;
    private final GameMap map;
    private final Map<Integer, PlayerCharacter> players = new ConcurrentHashMap<>();
    private final Map<Integer, Enemy> enemies = new ConcurrentHashMap<>();
    private final List<GameItem> items = new CopyOnWriteArrayList<>();
    private ServerCombatManager combatManager;
    private final AtomicInteger nextEnemyId = new AtomicInteger(1000);
    private final ScheduledExecutorService enemyScheduler = Executors.newScheduledThreadPool(10);
    private final Random random = new Random();
    private final Map<Integer, Boolean> playersUnderAttack = new ConcurrentHashMap<>();
    private static final int BOARD_SIZE = 15; // Larger for multiplayer
    private static final int MAX_ENEMIES = 20;
    private static final int ENEMY_SPAWN_DELAY = 5000; // 5 seconds
    
    /**
     * Creates a new server game world.
     */
    public ServerGameWorld(GameServer server) {
        this.server = server;
        this.map = new GameMap(BOARD_SIZE);
        this.combatManager = new ServerCombatManager(server, this);
    }
    
    /**
     * Initializes the game world with items and enemies.
     */
    public void initialize() {
        LogManager.log("Initializing server game world");
        
        // Place items on the map
        placeItems();
        
        // Spawn initial enemies
        spawnInitialEnemies();
        
        // Schedule enemy spawning
        enemyScheduler.scheduleAtFixedRate(this::spawnEnemyIfNeeded, 
            ENEMY_SPAWN_DELAY, ENEMY_SPAWN_DELAY, TimeUnit.MILLISECONDS);
    }
    
    /**
     * Places random items on the map.
     */
    private void placeItems() {
        int itemCount = BOARD_SIZE * BOARD_SIZE / 10; // 10% of tiles
        
        for (int i = 0; i < itemCount; i++) {
            Position pos = getRandomFreePosition();
            if (pos != null) {
                GameItem item = createRandomItem(pos);
                if (item != null) {
                    items.add(item);
                    map.addEntity(pos, item);
                }
            }
        }
    }
    
    
    private boolean isPlayerUnderAttack(PlayerCharacter player) {
        return playersUnderAttack.getOrDefault(player.getNetworkId(), false);
    }

    private void markPlayerUnderAttack(PlayerCharacter player, boolean underAttack) {
        playersUnderAttack.put(player.getNetworkId(), underAttack);
    }
    
    
    
    /**
     * Handles special decorator behaviors after enemy death.
     */
    private void handleEnemyDeathSpecial(Enemy enemy) {
        // Check for ExplodingEnemyDecorator
        if (enemy instanceof ExplodingEnemyDecorator explodingEnemy) {
            handleExplosion(explodingEnemy);
        }
        
        // Recursively check wrapped enemies
        Enemy current = enemy;
        while (current instanceof EnemyDecorator) {
            EnemyDecorator decorator = (EnemyDecorator) current;
            if (decorator instanceof ExplodingEnemyDecorator) {
                handleExplosion((ExplodingEnemyDecorator) decorator);
            }
            current = decorator.getWrapped();
        }
    }

    /**
     * Handles explosion damage to nearby players.
     */
    private void handleExplosion(ExplodingEnemyDecorator explodingEnemy) {
        Position explosionPos = explodingEnemy.getPosition();
        int explosionDamage = explodingEnemy.getExplosionDamage();
        int explosionRange = explodingEnemy.getExplosionRange();
        
        LogManager.log("Explosion at " + explosionPos + " with damage " + explosionDamage);
        
        // Damage all players within range
        for (PlayerCharacter player : players.values()) {
            int distance = GameMap.calcDistance(player.getPosition(), explosionPos);
            if (distance <= explosionRange) {
                player.receiveDamage(explosionDamage, explodingEnemy);
                
                // Send damage event only to affected player
                GameMessage damageMsg = new GameMessage(MessageType.DAMAGE_DEALT);
                damageMsg.setPosition(player.getPosition());
                damageMsg.setHealth(explosionDamage);
                damageMsg.setMessage("EXPLOSION");
                damageMsg.setTargetPlayerId(player.getNetworkId());
                
                ClientHandler handler = server.getClient(player.getNetworkId());
                if (handler != null) {
                    handler.sendMessage(damageMsg);
                }
                
                if (player.isDead()) {
                    handlePlayerDeath(player);
                }
            }
        }
    }

    /**
     * Checks and handles teleporting enemies.
     * Call this after any damage is dealt to an enemy.
     */
    private void checkAndHandleTeleport(Enemy enemy) {
        // Check if enemy has teleporting decorator
        Enemy current = enemy;
        TeleportingEnemyDecorator teleporter = null;
        
        // Check if enemy itself is a teleporting decorator
        if (current instanceof TeleportingEnemyDecorator) {
            teleporter = (TeleportingEnemyDecorator) current;
        } else {
            // Check wrapped enemies
            while (current instanceof EnemyDecorator) {
                EnemyDecorator decorator = (EnemyDecorator) current;
                if (decorator instanceof TeleportingEnemyDecorator) {
                    teleporter = (TeleportingEnemyDecorator) decorator;
                    break;
                }
                current = decorator.getWrapped();
            }
        }
        
        // Handle teleportation if needed
        if (teleporter != null && teleporter.shouldTeleport()) {
            Position oldPos = enemy.getPosition();
            Position newPos = getRandomFreePosition();
            
            if (newPos != null) {
                // Teleport the enemy
                map.removeEntity(oldPos, enemy);
                enemy.setPosition(newPos);
                map.addEntity(newPos, enemy);
                
                // Reset teleport flag
                teleporter.resetTeleportFlag();
                
                // Send update to clients
                GameMessage teleportMsg = new GameMessage(MessageType.ENEMY_UPDATE);
                teleportMsg.setPlayerId(enemy.getNetworkId());
                teleportMsg.setPosition(newPos);
                server.broadcastMessage(teleportMsg);
                
                // Log the teleportation
                LogManager.log("Enemy teleported from " + oldPos + " to " + newPos);
                
                // Reactivate the enemy after teleport
                enemy.setActive(true);
            }
        }
    }
    
    
    /**
     * Creates a random item at the given position.
     */
    private GameItem createRandomItem(Position pos) {
        int chance = random.nextInt(100);
        
        if (chance < 30) {
            return new Wall(pos);
        } else if (chance < 65) {
            return new Potion(pos);
        } else {
            return new PowerPotion(pos);
        }
    }
    
    /**
     * Spawns initial enemies.
     */
    private void spawnInitialEnemies() {
        int initialEnemies = Math.min(MAX_ENEMIES / 2, 10);
        
        for (int i = 0; i < initialEnemies; i++) {
            spawnEnemy();
        }
    }
    
    /**
     * Spawns a new enemy if below maximum.
     */
    private void spawnEnemyIfNeeded() {
        if (enemies.size() < MAX_ENEMIES) {
            spawnEnemy();
        }
    }
    
    /**
     * Spawns a single enemy.
     */
    
    private void spawnEnemy() {
        Position pos = getRandomFreePosition();
        if (pos != null) {
            Enemy enemy = EnemyFactory.createRandomEnemy(pos);
            
            int enemyId = nextEnemyId.getAndIncrement();
            enemy.setNetworkId(enemyId);
            
            enemies.put(enemyId, enemy);
            map.addEntity(pos, enemy);
            
            // Start enemy AI
            startEnemyAI(enemy);
            
            LogManager.log("Spawned " + enemy.getClass().getSimpleName() + " at " + pos);
        }
    }
    
    /**
     * Starts the AI for an enemy.
     */
    private void startEnemyAI(Enemy enemy) {
        enemyScheduler.scheduleAtFixedRate(() -> {
            if (!enemy.isDead() && enemies.containsKey(enemy.getNetworkId())) {
                updateEnemy(enemy);
            }
        }, 1000, 500 + random.nextInt(1000), TimeUnit.MILLISECONDS);
    }
    
    /**
     * Updates a single enemy's behavior.
     */
    private void updateEnemy(Enemy enemy) {
        if (enemy.isDead()) {
            handleEnemyDeath(enemy);
            return;
        }
        
        // Check if this enemy is in combat
        for (PlayerCharacter player : players.values()) {
            Enemy opponent = combatManager.getCombatOpponent(player.getNetworkId());
            if (opponent == enemy) {
                // This enemy is in combat, skip normal AI
                return;
            }
        }
        
        if (players.isEmpty()) return;
        
        // Find nearest player
        PlayerCharacter nearestPlayer = findNearestPlayer(enemy.getPosition());
        if (nearestPlayer == null) return;
        
        int distance = GameMap.calcDistance(enemy.getPosition(), nearestPlayer.getPosition());
        
        // Activate if player is nearby
        enemy.onPlayerMoved(nearestPlayer.getPosition());
        
        if (enemy.isActive()) {
            // Check if player is already in combat
            if (combatManager.isPlayerInCombat(nearestPlayer.getNetworkId())) {
                // Don't attack a player who's already fighting
                return;
            }
            
            if (distance <= 1) {
                // Initiate combat
                combatManager.startCombat(nearestPlayer, enemy);
            } else if (distance <= 5) {
                // Move towards player
                moveEnemyTowards(enemy, nearestPlayer.getPosition());
            }
        }
    }
    
    /**
     * Finds the nearest player to a position.
     */
    private PlayerCharacter findNearestPlayer(Position pos) {
        PlayerCharacter nearest = null;
        int minDistance = Integer.MAX_VALUE;
        
        for (PlayerCharacter player : players.values()) {
            int distance = GameMap.calcDistance(pos, player.getPosition());
            if (distance < minDistance) {
                minDistance = distance;
                nearest = player;
            }
        }
        
        return nearest;
    }
    
    /**
     * Moves an enemy towards a target position.
     */
    private void moveEnemyTowards(Enemy enemy, Position target) {
        // Use Enemy's static pathfinding method
        List<Position> path = Enemy.findPath(map, enemy.getPosition(), target);
        
        if (path != null && path.size() > 1) {
            Position nextPos = path.get(1);
            
            // Check if position is free
            List<GameEntity> entities = map.getEntitiesAt(nextPos);
            boolean blocked = entities.stream().anyMatch(e -> 
                e instanceof Wall || e instanceof PlayerCharacter || e instanceof Enemy);
                
            if (!blocked) {
                Position oldPos = enemy.getPosition();
                map.removeEntity(oldPos, enemy);
                enemy.setPosition(nextPos);
                map.addEntity(nextPos, enemy);
                
                // Send position update to clients
                GameMessage updateMsg = new GameMessage(MessageType.ENEMY_UPDATE);
                updateMsg.setPlayerId(enemy.getNetworkId()); // Using playerId field for enemy ID
                updateMsg.setPosition(nextPos);
                server.broadcastMessage(updateMsg);
            }
        }
    }
    
    /**
     * Initiates combat between an enemy and player.
     */
    private void initiateEnemyCombat(Enemy enemy, PlayerCharacter player) {
        // Check if another enemy is already attacking this player
        if (isPlayerUnderAttack(player)) {
            return; // Don't allow multiple enemies to attack same player
        }
        
        markPlayerUnderAttack(player, true);
        
        try {
            // Use ServerCombatSystem for single attack (enemy attacks player)
            ServerCombatSystem.CombatResult result = ServerCombatSystem.resolveAttack(enemy, player);
            
            if (result.wasEvaded) {
                // Send miss event
                GameMessage missMsg = new GameMessage(MessageType.DAMAGE_DEALT);
                missMsg.setPosition(player.getPosition());
                missMsg.setHealth(0);
                missMsg.setMessage("MISS");
                server.broadcastMessage(missMsg);
            } else if (result.damageDealt > 0) {
                // Send damage event
                GameMessage damageMsg = new GameMessage(MessageType.DAMAGE_DEALT);
                damageMsg.setPosition(player.getPosition());
                damageMsg.setHealth(result.damageDealt);
                damageMsg.setMessage("ENEMY");
                server.broadcastMessage(damageMsg);
            }
            
            LogManager.log("Enemy " + enemy.getClass().getSimpleName() + 
                " attacked " + player.getName() + " for " + result.damageDealt + " damage");
                
            if (player.isDead()) {
                handlePlayerDeath(player);
            }
        } finally {
            markPlayerUnderAttack(player, false);
        }
    }
    
    /**
     * Adds a new player to the game.
     */
    public synchronized void addPlayer(PlayerCharacter player) {
        Position pos = getRandomFreePosition();
        if (pos != null) {
            player.setPosition(pos);
            player.setVisible(true);
            
            players.put(player.getNetworkId(), player);
            map.addEntity(pos, player);
            
            LogManager.log("Player " + player.getName() + " joined at " + pos);
        }
    }
    
    /**
     * Removes a player from the game.
     */
    public synchronized PlayerCharacter removePlayer(int playerId) {
        PlayerCharacter player = players.remove(playerId);
        if (player != null) {
            map.removeEntity(player.getPosition(), player);
            LogManager.log("Player " + player.getName() + " removed from game");
        }
        return player;
    }
    
    /**
     * Validates and executes a player move.
     */
    public synchronized boolean validateAndMovePlayer(PlayerCharacter player, Position newPos) {
        // Check if player is in combat
        if (combatManager.isPlayerInCombat(player.getNetworkId())) {
            // Check if this is a flee attempt
            if (!combatManager.handleCombatMovement(player.getNetworkId(), newPos)) {
                return false; // Movement not allowed during combat
            }
        }
        
        // Check bounds
        if (!map.isValidPosition(newPos)) {
            return false;
        }
        
        // Check distance (only adjacent moves allowed)
        if (player.getPosition().distanceTo(newPos) > 1) {
            return false;
        }
        
        // Check for obstacles
        List<GameEntity> entities = map.getEntitiesAt(newPos);
        for (GameEntity entity : entities) {
            if (entity instanceof Wall) {
                return false;
            }
            if (entity instanceof Enemy) {
                // Don't move into enemy space, initiate combat instead
                return false;
            }
        }
        
        // Move is valid
        Position oldPos = player.getPosition();
        map.removeEntity(oldPos, player);
        player.setPosition(newPos);
        map.addEntity(newPos, player);
        
        // Handle item interactions
        handleItemInteractions(player, newPos);
        
        return true;
    }
    
    private void sendDamageEvent(Position pos, int damage, boolean isCrit) {
        GameMessage damageMsg = new GameMessage(MessageType.DAMAGE_DEALT);
        damageMsg.setPosition(pos);
        damageMsg.setHealth(damage); // Using health field to store damage amount
        damageMsg.setMessage(isCrit ? "CRIT" : "NORMAL");
        server.broadcastMessage(damageMsg);
    }
    
    private void sendDamageEventToPlayer(int playerId, Position pos, int damage, boolean isCrit) {
        GameMessage damageMsg = new GameMessage(MessageType.DAMAGE_DEALT);
        damageMsg.setPosition(pos);
        damageMsg.setHealth(damage);
        damageMsg.setMessage(isCrit ? "CRIT" : "NORMAL");
        damageMsg.setTargetPlayerId(playerId);
        
        ClientHandler handler = server.getClient(playerId);
        if (handler != null) {
            handler.sendMessage(damageMsg);
        }
    }
    
    /**
     * Handles player attacking an enemy.
     */
    private void handlePlayerAttack(PlayerCharacter player, Enemy enemy) {
        // Use the new ServerCombatSystem
        ServerCombatSystem.CombatRoundResult result = ServerCombatSystem.resolveCombatRound(
            player, enemy, player.getPosition(), enemy.getPosition()
        );
        
        // Send damage events for player's attack
        if (result.attackerResult != null) {
            if (result.attackerResult.wasEvaded) {
                // Send miss event
                GameMessage missMsg = new GameMessage(MessageType.DAMAGE_DEALT);
                missMsg.setPosition(enemy.getPosition());
                missMsg.setHealth(0);
                missMsg.setMessage("MISS");
                server.broadcastMessage(missMsg);
            } else if (result.attackerResult.damageDealt > 0) {
                // Send damage event
                GameMessage damageMsg = new GameMessage(MessageType.DAMAGE_DEALT);
                damageMsg.setPosition(enemy.getPosition());
                damageMsg.setHealth(result.attackerResult.damageDealt);
                damageMsg.setMessage(result.attackerResult.wasCritical ? "CRIT" : "NORMAL");
                server.broadcastMessage(damageMsg);
                checkAndHandleTeleport(enemy);
            }
        }
        
        // Send damage events for enemy's counter-attack
        if (result.defenderResult != null) {
            if (result.defenderResult.wasEvaded) {
                // Send miss event
                GameMessage missMsg = new GameMessage(MessageType.DAMAGE_DEALT);
                missMsg.setPosition(player.getPosition());
                missMsg.setHealth(0);
                missMsg.setMessage("MISS");
                server.broadcastMessage(missMsg);
            } else if (result.defenderResult.damageDealt > 0) {
                // Send damage event
                GameMessage damageMsg = new GameMessage(MessageType.DAMAGE_DEALT);
                damageMsg.setPosition(player.getPosition());
                damageMsg.setHealth(result.defenderResult.damageDealt);
                damageMsg.setMessage("ENEMY");
                server.broadcastMessage(damageMsg);
                checkAndHandleTeleport(enemy);
            }
        }
        
        // Handle deaths
        if (enemy.isDead()) {
            handleEnemyDeath(enemy);
        }
        
        if (player.isDead()) {
            handlePlayerDeath(player);
        }
    }
    
    /**
     * Handles item interactions at a position.
     */
    private void handleItemInteractions(PlayerCharacter player, Position pos) {
        List<GameEntity> entities = new ArrayList<>(map.getEntitiesAt(pos));
        
        for (GameEntity entity : entities) {
            if (entity instanceof Potion potion && !potion.getIsUsed()) {
                potion.interact(player);
                map.removeEntity(pos, potion);
                items.remove(potion);
                
                LogManager.log(player.getName() + " used a potion");
                
                // Send item collected message to player
                sendItemCollectedMessage(player.getNetworkId(), "Potion");
                
            } else if (entity instanceof PowerPotion powerPotion && !powerPotion.getIsUsed()) {
                powerPotion.interact(player);
                map.removeEntity(pos, powerPotion);
                items.remove(powerPotion);
                
                LogManager.log(player.getName() + " used a power potion");
                
                // Send item collected message to player
                sendItemCollectedMessage(player.getNetworkId(), "PowerPotion");
                
            } else if (entity instanceof Treasure treasure) {
                treasure.interact(player);
                map.removeEntity(pos, treasure);
                items.remove(treasure);
                
                LogManager.log(player.getName() + " collected treasure");
                
                // Send item collected message to player
                sendItemCollectedMessage(player.getNetworkId(), "Treasure");
            }
        }
    }
    
    private void sendItemCollectedMessage(int playerId, String itemType) {
        GameMessage itemMsg = new GameMessage(MessageType.ITEM_COLLECTED);
        itemMsg.setPlayerId(playerId);
        itemMsg.setMessage(itemType);
        
        ClientHandler handler = server.getClient(playerId);
        if (handler != null) {
            handler.sendMessage(itemMsg);
        }
    }
    
    /**
     * Handles enemy death.
     */
    public void handleEnemyDeath(Enemy enemy) {
        // Handle special death effects first
        handleEnemyDeathSpecial(enemy);
        
        // Then proceed with normal death handling
        enemies.remove(enemy.getNetworkId());
        map.removeEntity(enemy.getPosition(), enemy);
        
        Position pos = enemy.getPosition();
        Treasure treasure = new Treasure(pos, true, enemy.getLoot());
        items.add(treasure);
        map.addEntity(pos, treasure);
        
        LogManager.log("Enemy " + enemy.getClass().getSimpleName() + " defeated, dropped treasure worth " + enemy.getLoot());
        
        if (enemies.size() < MAX_ENEMIES) {
            spawnEnemy();
        }
    }
    
    /**
     * Handles player death.
     */
    public void handlePlayerDeath(PlayerCharacter player) {
        // Respawn at random location
        Position respawnPos = getRandomFreePosition();
        if (respawnPos != null) {
            map.removeEntity(player.getPosition(), player);
            player.setPosition(respawnPos);
            player.setHealth(100); // Full health
            map.addEntity(respawnPos, player);
            
            LogManager.log(player.getName() + " died and respawned at " + respawnPos);
        }
    }
    
    /**
     * Activates a player ability (decorator).
     */
    public boolean activatePlayerAbility(PlayerCharacter player, String abilityType) {
        try {
            // Get the base player without decorators
            PlayerCharacter basePlayer = player;
            while (basePlayer instanceof PlayerDecorator) {
                basePlayer = ((PlayerDecorator) basePlayer).getWrapped();
            }
            
            // Apply new decorator to base player
            PlayerCharacter decorated = switch (abilityType) {
                case "BOOST" -> new BoostedAttackDecorator(basePlayer, 10);
                case "SHIELD" -> new ShieldedPlayerDecorator(basePlayer);
                case "REGEN" -> new RegenerationDecorator(basePlayer);
                default -> null;
            };
            
            if (decorated != null) {
                // Preserve position and other state
                decorated.setPosition(player.getPosition());
                decorated.setHealth(player.getHealth());
                decorated.setPower(basePlayer.getPower()); // Use base power
                decorated.setNetworkId(player.getNetworkId());
                
                // Replace player in collections
                players.put(player.getNetworkId(), decorated);
                
                // Update map
                map.removeEntity(player.getPosition(), player);
                map.addEntity(decorated.getPosition(), decorated);
                
                // Schedule removal after duration
                enemyScheduler.schedule(() -> {
                    removePlayerDecorator(player.getNetworkId(), abilityType);
                }, 15, TimeUnit.SECONDS);
                
                // Send full state update to sync with clients
                GameMessage stateMsg = server.createFullStateMessage();
                server.broadcastMessage(stateMsg);
                
                LogManager.log(player.getName() + " activated " + abilityType);
                return true;
            }
        } catch (Exception e) {
            LogManager.log("Error activating ability: " + e.getMessage());
            e.printStackTrace();
        }
        
        return false;
    }
    
    /**
     * Removes a decorator from a player.
     */
    private void removePlayerDecorator(int playerId, String decoratorType) {
        PlayerCharacter player = players.get(playerId);
        if (player instanceof PlayerDecorator decorator) {
            PlayerCharacter basePlayer = decorator.getWrapped();
            
            // Preserve current state
            basePlayer.setPosition(player.getPosition());
            basePlayer.setHealth(player.getHealth());
            
            // Replace in collections
            players.put(playerId, basePlayer);
            
            // Update map
            map.removeEntity(player.getPosition(), player);
            map.addEntity(basePlayer.getPosition(), basePlayer);
            
            // Send update to clients
            GameMessage stateMsg = server.createFullStateMessage();
            server.broadcastMessage(stateMsg);
            
            LogManager.log("Removed " + decoratorType + " from player " + playerId);
        }
    }
    
    /**
     * Gets a random free position on the map.
     */
    private Position getRandomFreePosition() {
        for (int attempts = 0; attempts < 100; attempts++) {
            int row = random.nextInt(BOARD_SIZE);
            int col = random.nextInt(BOARD_SIZE);
            Position pos = new Position(row, col);
            
            if (map.getEntitiesAt(pos).isEmpty()) {
                return pos;
            }
        }
        return null;
    }
    
    /**
     * Checks if an enemy is visible to any player.
     */
    public boolean isEnemyVisibleToAnyPlayer(Enemy enemy) {
        for (PlayerCharacter player : players.values()) {
            if (GameMap.calcDistance(player.getPosition(), enemy.getPosition()) <= 2) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Checks if an item is visible to any player.
     */
    public boolean isItemVisibleToAnyPlayer(GameItem item) {
        for (PlayerCharacter player : players.values()) {
            if (GameMap.calcDistance(player.getPosition(), item.getPosition()) <= 2) {
                return true;
            }
        }
        return false;
    }
    
    // Getters
    public PlayerCharacter getPlayerById(int playerId) {
        return players.get(playerId);
    }
    
    public Collection<PlayerCharacter> getAllPlayers() {
        return players.values();
    }
    
    public Collection<Enemy> getAllEnemies() {
        return enemies.values();
    }
    
    public List<GameItem> getAllItems() {
        return new ArrayList<>(items);
    }
    
    public GameMap getMap() {
        return map;
    }
    
    /**
     * Shuts down the game world.
     */
    public void shutdown() {
        combatManager.shutdown();
        enemyScheduler.shutdownNow();
        players.clear();
        enemies.clear();
        items.clear();
    }

    public void handlePlayerCombat(PlayerCharacter player, Enemy enemy) {
        // Start combat through the combat manager
        combatManager.startCombat(player, enemy);
    }
}