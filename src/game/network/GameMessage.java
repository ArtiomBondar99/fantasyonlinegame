package game.network;

import game.map.Position;
import java.io.Serializable;
import java.util.List;


/**
 * Main message class for all network communication.
 * Uses serialization for easy transmission.
 */
public class GameMessage implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private MessageType type;
    private int playerId;
    private String playerName;
    private String message;
    private Position position;
    private int health;
    private int power;
    private int targetPlayerId = -1; // For damage messages, who should see it
    
    // For full state updates
    private List<PlayerState> playerStates;
    private List<EnemyState> enemyStates;
    private List<ItemState> itemStates;
    
    public GameMessage(MessageType type) {
        this.type = type;
    }
    
    // Getters and setters
    public MessageType getType() { return type; }
    public void setType(MessageType type) { this.type = type; }
    
    public int getPlayerId() { return playerId; }
    public void setPlayerId(int playerId) { this.playerId = playerId; }
    
    public String getPlayerName() { return playerName; }
    public void setPlayerName(String playerName) { this.playerName = playerName; }
    
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    
    public Position getPosition() { return position; }
    public void setPosition(Position position) { this.position = position; }
    
    public int getHealth() { return health; }
    public void setHealth(int health) { this.health = health; }
    
    public int getPower() { return power; }
    public void setPower(int power) { this.power = power; }
    
    public List<PlayerState> getPlayerStates() { return playerStates; }
    public void setPlayerStates(List<PlayerState> playerStates) { this.playerStates = playerStates; }
    
    public List<EnemyState> getEnemyStates() { return enemyStates; }
    public void setEnemyStates(List<EnemyState> enemyStates) { this.enemyStates = enemyStates; }
    
    public List<ItemState> getItemStates() { return itemStates; }
    public void setItemStates(List<ItemState> itemStates) { this.itemStates = itemStates; }
    
    public int getTargetPlayerId() { return targetPlayerId; }
    public void setTargetPlayerId(int targetPlayerId) { this.targetPlayerId = targetPlayerId; }
}




