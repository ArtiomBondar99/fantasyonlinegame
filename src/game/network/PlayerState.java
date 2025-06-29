
package game.network;

import java.io.Serializable;
import game.map.Position;

/**
 * Enhanced PlayerState that includes inventory information.
 */
public class PlayerState implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private int playerId;
    private String name;
    private Position position;
    private int health;
    private int power;
    private String characterClass;
    private int lifePotionCount;
    private int powerPotionCount;
    private int treasurePoints;
    
    public PlayerState(int playerId, String name, Position position, int health, int power, 
                      String characterClass, int lifePotionCount, int powerPotionCount, int treasurePoints) {
        this.playerId = playerId;
        this.name = name;
        this.position = position;
        this.health = health;
        this.power = power;
        this.characterClass = characterClass;
        this.lifePotionCount = lifePotionCount;
        this.powerPotionCount = powerPotionCount;
        this.treasurePoints = treasurePoints;
    }
    
    // Keep old constructor for compatibility
    public PlayerState(int playerId, String name, Position position, int health, int power, String characterClass) {
        this(playerId, name, position, health, power, characterClass, 0, 0, 0);
    }
    
    // Getters
    public int getPlayerId() { return playerId; }
    public String getName() { return name; }
    public Position getPosition() { return position; }
    public int getHealth() { return health; }
    public int getPower() { return power; }
    public String getCharacterClass() { return characterClass; }
    public int getLifePotionCount() { return lifePotionCount; }
    public int getPowerPotionCount() { return powerPotionCount; }
    public int getTreasurePoints() { return treasurePoints; }
}
