package game.client;

import game.network.*;
import game.map.Position;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Maintains the game state on the client side.
 * Updated based on messages from the server.
 * 
 * @author Artiom Bondar:332692730
 * @author Shahar Dahan:207336355
 */
public class ClientGameState {
    private final Map<Integer, PlayerState> players = new ConcurrentHashMap<>();
    private final Map<Integer, EnemyState> enemies = new ConcurrentHashMap<>();
    private final Map<Position, List<ItemState>> items = new ConcurrentHashMap<>();
    
    /**
     * Updates the entire game state from a server message.
     */
    public synchronized void updateFromMessage(GameMessage message) {
        // Update players
        if (message.getPlayerStates() != null) {
            players.clear();
            for (PlayerState player : message.getPlayerStates()) {
                players.put(player.getPlayerId(), player);
            }
        }
        
        // Update enemies
        if (message.getEnemyStates() != null) {
            enemies.clear();
            for (EnemyState enemy : message.getEnemyStates()) {
                enemies.put(enemy.getEnemyId(), enemy);
            }
        }
        
        // Update items
        if (message.getItemStates() != null) {
            items.clear();
            for (ItemState item : message.getItemStates()) {
                items.computeIfAbsent(item.getPosition(), k -> new ArrayList<>()).add(item);
            }
        }
    }
    
    public synchronized void updatePlayerFull(PlayerState playerState) {
        players.put(playerState.getPlayerId(), playerState);
    }
    
    /**
     * Updates a player's position.
     */
    public synchronized void updatePlayerPosition(int playerId, Position newPos) {
        PlayerState player = players.get(playerId);
        if (player != null) {
            // Create new state with updated position, preserving inventory
            PlayerState updated = new PlayerState(
                playerId,
                player.getName(),
                newPos,
                player.getHealth(),
                player.getPower(),
                player.getCharacterClass(),
                player.getLifePotionCount(),
                player.getPowerPotionCount(),
                player.getTreasurePoints()
            );
            players.put(playerId, updated);
        }
    }
    
    /**
     * Updates a player's stats.
     */
    public synchronized void updatePlayerStats(int playerId, int health, int power) {
        PlayerState player = players.get(playerId);
        if (player != null) {
            // Create new state with updated stats, preserving inventory
            PlayerState updated = new PlayerState(
                playerId,
                player.getName(),
                player.getPosition(),
                health,
                power,
                player.getCharacterClass(),
                player.getLifePotionCount(),
                player.getPowerPotionCount(),
                player.getTreasurePoints()
            );
            players.put(playerId, updated);
        }
    }
    
    /**
     * Removes a player from the game state.
     */
    public synchronized void removePlayer(int playerId) {
        players.remove(playerId);
    }
    
    /**
     * Gets a specific player's state.
     */
    public PlayerState getPlayer(int playerId) {
        return players.get(playerId);
    }
    
    /**
     * Gets all players.
     */
    public Collection<PlayerState> getAllPlayers() {
        return new ArrayList<>(players.values());
    }
    
    /**
     * Gets all enemies.
     */
    public Collection<EnemyState> getAllEnemies() {
        return new ArrayList<>(enemies.values());
    }
    
    /**
     * Gets items at a specific position.
     */
    public List<ItemState> getItemsAt(Position pos) {
        return items.getOrDefault(pos, new ArrayList<>());
    }
    
    /**
     * Gets all items.
     */
    public Map<Position, List<ItemState>> getAllItems() {
        return new HashMap<>(items);
    }
    
    /**
     * Checks if a position has any entities.
     */
    public boolean hasEntitiesAt(Position pos) {
        // Check for players
        for (PlayerState player : players.values()) {
            if (player.getPosition().equals(pos)) {
                return true;
            }
        }
        
        // Check for enemies
        for (EnemyState enemy : enemies.values()) {
            if (enemy.getPosition().equals(pos)) {
                return true;
            }
        }
        
        // Check for items
        return items.containsKey(pos) && !items.get(pos).isEmpty();
    }
    
    /**
     * Gets the display symbol for the top entity at a position.
     */
    public String getDisplaySymbolAt(Position pos, int myPlayerId) {
        // Check for my player first
    		PlayerState myPlayer = players.get(myPlayerId);
        if (myPlayer != null && myPlayer.getPosition().equals(pos)) {
            return 'm' + getBaseClass(myPlayer.getCharacterClass());
        }
        
        // Check for other players
        for (PlayerState player : players.values()) {
            if (player.getPlayerId() != myPlayerId && player.getPosition().equals(pos)) {
                return 'o' + getBaseClass(player.getCharacterClass());
            }
        }
        
        // Check for enemies
        for (EnemyState enemy : enemies.values()) {
            if (enemy.getPosition().equals(pos) ) { //&& enemy.isVisible()
                return getEnemySymbol(enemy.getType());
            }
        }
        
        // Check for items
        List<ItemState> itemsAtPos = items.get(pos);
        if (itemsAtPos != null && !itemsAtPos.isEmpty()) {
            return getItemSymbol(itemsAtPos.get(0).getType());
        }
        
        return "";
    }
    
    private String getBaseClass(String className) {
        // Handle decorated class names
        if (className.contains("Decorator")) {
            // For now, try to extract base class from name
            if (className.contains("Warrior")) return "Warrior";
            if (className.contains("Mage")) return "Mage";
            if (className.contains("Archer")) return "Archer";
        }
        return className;
    }
    
    /**
     * Gets the appropriate symbol for a player class.
     */
    private String getPlayerSymbol(String characterClass) {
        return switch (characterClass) {
            case "Warrior" -> "W";
            case "Mage" -> "M";
            case "Archer" -> "A";
            default -> "P";
        };
    }
    
    /**
     * Gets the appropriate symbol for an enemy type.
     */
    private String getEnemySymbol(String enemyType) {
        if (enemyType.contains("Dragon")) return "D";
        if (enemyType.contains("Goblin")) return "G";
        if (enemyType.contains("Orc")) return "O";
        return "E";
    }
    
    /**
     * Gets the appropriate symbol for an item type.
     */
    private String getItemSymbol(String itemType) {
        return switch (itemType) {
            case "Wall" -> "W";
            case "Potion" -> "L";
            case "PowerPotion" -> "P";
            case "Treasure" -> "T";
            default -> "?";
        };
    }
    
    public synchronized void updateEnemyPosition(int enemyId, Position newPos) {
        EnemyState enemy = enemies.get(enemyId);
        if (enemy != null) {
            // Create new state with updated position
            EnemyState updated = new EnemyState(
                enemyId,
                enemy.getType(),
                newPos,
                enemy.getHealth(),
                enemy.isVisible()
            );
            enemies.put(enemyId, updated);
        }
    }
}