package game.server;

import game.characters.*;
import game.combat.*;
import game.decorators.PlayerDecorator;
import game.decorators.ShieldedPlayerDecorator;
import game.map.Position;
import game.network.*;
import game.logging.LogManager;
import java.util.*;
import java.util.concurrent.*;

/**
 * Manages active combat sessions between players and enemies.
 * Handles turn-based combat similar to single-player mode.
 */
public class ServerCombatManager {
    private final GameServer server;
    private final ServerGameWorld gameWorld;
    private final Map<Integer, CombatSession> activeCombats = new ConcurrentHashMap<>();
    private final ScheduledExecutorService combatExecutor = Executors.newScheduledThreadPool(5);
    
    public ServerCombatManager(GameServer server, ServerGameWorld gameWorld) {
        this.server = server;
        this.gameWorld = gameWorld;
    }
    
    /**
     * Represents an active combat session.
     */
    private class CombatSession {
        final int playerId;
        final int enemyId;
        final PlayerCharacter player;
        final Enemy enemy;
        boolean playerTurn;
        boolean active = true;
        ScheduledFuture<?> combatTask;
        
        CombatSession(PlayerCharacter player, Enemy enemy) {
            this.playerId = player.getNetworkId();
            this.enemyId = enemy.getNetworkId();
            this.player = player;
            this.enemy = enemy;
            this.playerTurn = true; // Player always starts
        }
    }
    
    /**
     * Initiates combat between a player and enemy.
     */
    public synchronized boolean startCombat(PlayerCharacter player, Enemy enemy) {
        int playerId = player.getNetworkId();
        
        // Check if player is already in combat
        if (activeCombats.containsKey(playerId)) {
            return false;
        }
        
        // Check range
        Position playerPos = player.getPosition();
        Position enemyPos = enemy.getPosition();
        int distance = playerPos.distanceTo(enemyPos);
        
        boolean inRange = false;
        if (player instanceof RangedFighter) {
            inRange = distance <= 2;
        } else {
            inRange = distance <= 1;
        }
        
        if (!inRange) {
            sendErrorToPlayer(playerId, "Target out of range!");
            return false;
        }
        
        // Create combat session
        CombatSession session = new CombatSession(player, enemy);
        activeCombats.put(playerId, session);
        
        // Mark enemy as in combat
        enemy.setActive(false); // Prevent normal AI movement
        
        // Notify client that combat started
        GameMessage combatStart = new GameMessage(MessageType.COMBAT_UPDATE);
        combatStart.setPlayerId(playerId);
        combatStart.setMessage("COMBAT_START");
        server.broadcastMessage(combatStart);
        
        LogManager.log("Combat started: " + player.getName() + " vs " + enemy.getClass().getSimpleName());
        
        // Start combat loop
        startCombatLoop(session);
        
        return true;
    }
    
    /**
     * Starts the turn-based combat loop.
     */
    private void startCombatLoop(CombatSession session) {
        session.combatTask = combatExecutor.scheduleWithFixedDelay(() -> {
            if (!session.active || session.player.isDead() || session.enemy.isDead()) {
                endCombat(session.playerId);
                return;
            }
            
            // Check if combatants are still in range
            int distance = session.player.getPosition().distanceTo(session.enemy.getPosition());
            int maxRange = getMaxCombatRange(session.player, session.enemy);
            
            if (distance > maxRange) {
                // Player fled from combat
                endCombat(session.playerId);
                return;
            }
            
            // Execute turn
            if (session.playerTurn) {
                executePlayerTurn(session);
            } else {
                executeEnemyTurn(session);
            }
            
            // Switch turns
            session.playerTurn = !session.playerTurn;
            
        }, 0, 1000, TimeUnit.MILLISECONDS); // 1 second between turns
    }
    
    /**
     * Executes the player's turn in combat.
     */
    private void executePlayerTurn(CombatSession session) {
        PlayerCharacter player = session.player;
        Enemy enemy = session.enemy;
        
        // Check for shield block before attack
        int enemyHealthBefore = enemy.getHealth();
        
        // Use ServerCombatSystem to resolve attack
        ServerCombatSystem.CombatResult result = ServerCombatSystem.resolveAttack(player, enemy);
        
        // Send damage event only to players involved
        if (result.wasEvaded) {
            sendMissEventToPlayer(session.playerId, enemy.getPosition());
        } else if (result.damageDealt > 0) {
            sendDamageEventToPlayer(session.playerId, enemy.getPosition(), result.damageDealt, result.wasCritical, false);
        }
        
        // Check if enemy died
        if (enemy.isDead()) {
            LogManager.log("Enemy defeated in combat");
            // Send death sound event to player
            sendEnemyDeathEvent(session.playerId, enemy.getPosition());
            gameWorld.handleEnemyDeath(enemy);
            endCombat(session.playerId);
        }
    }
    
    /**
     * Executes the enemy's turn in combat.
     */
    private void executeEnemyTurn(CombatSession session) {
        Enemy enemy = session.enemy;
        PlayerCharacter player = session.player;
        
        // Check if player has shield
        boolean shieldBlocked = false;
        if (player instanceof ShieldedPlayerDecorator shield) {
            if (shield.isShieldActive()) {
                shieldBlocked = true;
            }
        }
        
        // Use ServerCombatSystem to resolve attack
        ServerCombatSystem.CombatResult result = ServerCombatSystem.resolveAttack(enemy, player);
        
        // Send damage event only to the affected player
        if (shieldBlocked && !result.wasEvaded) {
            // Shield blocked the attack
            sendShieldBlockEvent(session.playerId, player.getPosition());
        } else if (result.wasEvaded) {
            sendMissEventToPlayer(session.playerId, player.getPosition());
        } else if (result.damageDealt > 0) {
            sendDamageEventToPlayer(session.playerId, player.getPosition(), result.damageDealt, false, true);
        }
        
        // Send player update
        GameMessage updateMsg = new GameMessage(MessageType.PLAYER_UPDATE);
        updateMsg.setPlayerId(player.getNetworkId());
        updateMsg.setHealth(player.getHealth());
        updateMsg.setPower(player.getPower());
        
        // Include full inventory state
        PlayerState fullState = new PlayerState(
            player.getNetworkId(),
            player.getName(),
            player.getPosition(),
            player.getHealth(),
            player.getPower(),
            getBaseClassName(player),
            player.getLifePotionCount(),
            player.getPowerPotionCount(),
            player.getTreasurePoints()
        );
        List<PlayerState> states = new ArrayList<>();
        states.add(fullState);
        updateMsg.setPlayerStates(states);
        
        server.broadcastMessage(updateMsg);
        
        // Check if player died
        if (player.isDead()) {
            LogManager.log("Player defeated in combat");
            gameWorld.handlePlayerDeath(player);
            endCombat(session.playerId);
        }
    }
    
    /**
     * Ends a combat session.
     */
    public synchronized void endCombat(int playerId) {
        CombatSession session = activeCombats.remove(playerId);
        if (session != null) {
            session.active = false;
            if (session.combatTask != null) {
                session.combatTask.cancel(false);
            }
            
            // Reactivate enemy AI if still alive
            if (!session.enemy.isDead()) {
                session.enemy.setActive(true);
            }
            
            // Notify client that combat ended
            GameMessage combatEnd = new GameMessage(MessageType.COMBAT_UPDATE);
            combatEnd.setPlayerId(playerId);
            combatEnd.setMessage("COMBAT_END");
            server.broadcastMessage(combatEnd);
            
            LogManager.log("Combat ended for player " + playerId);
        }
    }
    
    /**
     * Checks if a player is currently in combat.
     */
    public boolean isPlayerInCombat(int playerId) {
        return activeCombats.containsKey(playerId);
    }
    
    /**
     * Gets the enemy a player is fighting.
     */
    public Enemy getCombatOpponent(int playerId) {
        CombatSession session = activeCombats.get(playerId);
        return session != null ? session.enemy : null;
    }
    
    /**
     * Handles player movement during combat (fleeing).
     */
    public boolean handleCombatMovement(int playerId, Position newPos) {
        CombatSession session = activeCombats.get(playerId);
        if (session == null) {
            return true; // Not in combat, allow movement
        }
        
        // Check if movement would take player out of combat range
        int newDistance = newPos.distanceTo(session.enemy.getPosition());
        int maxRange = getMaxCombatRange(session.player, session.enemy);
        
        if (newDistance > maxRange) {
            // Player is fleeing
            endCombat(playerId);
            return true; // Allow movement
        }
        
        // Don't allow movement closer during combat
        return false;
    }
    
    /**
     * Gets the maximum combat range between two combatants.
     */
    private int getMaxCombatRange(Combatant c1, Combatant c2) {
        int range1 = (c1 instanceof RangedFighter) ? 2 : 1;
        int range2 = (c2 instanceof RangedFighter) ? 2 : 1;
        return Math.max(range1, range2);
    }
    
    /**
     * Sends a damage event to all clients.
     */
    private void sendDamageEvent(Position pos, int damage, boolean isCrit) {
        GameMessage damageMsg = new GameMessage(MessageType.DAMAGE_DEALT);
        damageMsg.setPosition(pos);
        damageMsg.setHealth(damage);
        damageMsg.setMessage(isCrit ? "CRIT" : "NORMAL");
        server.broadcastMessage(damageMsg);
    }
    
    /**
     * Sends a miss event to all clients.
     */
    private void sendMissEvent(Position pos) {
        GameMessage missMsg = new GameMessage(MessageType.DAMAGE_DEALT);
        missMsg.setPosition(pos);
        missMsg.setHealth(0);
        missMsg.setMessage("MISS");
        server.broadcastMessage(missMsg);
    }
    
    /**
     * Sends an error message to a specific player.
     */
    private void sendErrorToPlayer(int playerId, String message) {
        ClientHandler handler = server.getClient(playerId);
        if (handler != null) {
            GameMessage error = new GameMessage(MessageType.ERROR);
            error.setMessage(message);
            handler.sendMessage(error);
        }
    }
    
    private void sendDamageEventToPlayer(int playerId, Position pos, int damage, boolean isCrit, boolean fromEnemy) {
        GameMessage damageMsg = new GameMessage(MessageType.DAMAGE_DEALT);
        damageMsg.setPosition(pos);
        damageMsg.setHealth(damage);
        damageMsg.setMessage(isCrit ? "CRIT" : (fromEnemy ? "ENEMY" : "NORMAL"));
        damageMsg.setTargetPlayerId(playerId);
        damageMsg.setPlayerId(playerId); // Who caused or received the damage
        
        ClientHandler handler = server.getClient(playerId);
        if (handler != null) {
            handler.sendMessage(damageMsg);
        }
    }

    private void sendMissEventToPlayer(int playerId, Position pos) {
        GameMessage missMsg = new GameMessage(MessageType.DAMAGE_DEALT);
        missMsg.setPosition(pos);
        missMsg.setHealth(0);
        missMsg.setMessage("MISS");
        missMsg.setTargetPlayerId(playerId);
        
        ClientHandler handler = server.getClient(playerId);
        if (handler != null) {
            handler.sendMessage(missMsg);
        }
    }

    private void sendShieldBlockEvent(int playerId, Position pos) {
        GameMessage shieldMsg = new GameMessage(MessageType.DAMAGE_DEALT);
        shieldMsg.setPosition(pos);
        shieldMsg.setHealth(0);
        shieldMsg.setMessage("SHIELD_BLOCK");
        shieldMsg.setTargetPlayerId(playerId);
        
        ClientHandler handler = server.getClient(playerId);
        if (handler != null) {
            handler.sendMessage(shieldMsg);
        }
    }

    private void sendEnemyDeathEvent(int playerId, Position pos) {
        GameMessage deathMsg = new GameMessage(MessageType.DAMAGE_DEALT);
        deathMsg.setPosition(pos);
        deathMsg.setHealth(0);
        deathMsg.setMessage("ENEMY_DEATH");
        deathMsg.setTargetPlayerId(playerId);
        
        ClientHandler handler = server.getClient(playerId);
        if (handler != null) {
            handler.sendMessage(deathMsg);
        }
    }

    private String getBaseClassName(PlayerCharacter player) {
        PlayerCharacter base = player;
        while (base instanceof PlayerDecorator) {
            base = ((PlayerDecorator) base).getWrapped();
        }
        return base.getClass().getSimpleName();
    }
    
    /**
     * Shuts down the combat manager.
     */
    public void shutdown() {
        // End all active combats
        for (Integer playerId : new ArrayList<>(activeCombats.keySet())) {
            endCombat(playerId);
        }
        combatExecutor.shutdownNow();
    }
}