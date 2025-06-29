package game.network;

import java.io.Serializable;

import game.map.Position;

/**
 * Represents an enemy's state for network transmission.
 */
public class EnemyState implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private int enemyId;
    private String type;
    private Position position;
    private int health;
    private boolean visible;
    
    public EnemyState(int enemyId, String type, Position position, int health, boolean visible) {
        this.enemyId = enemyId;
        this.type = type;
        this.position = position;
        this.health = health;
        this.visible = visible;
    }
    
    // Getters
    public int getEnemyId() { return enemyId; }
    public String getType() { return type; }
    public Position getPosition() { return position; }
    public int getHealth() { return health; }
    public boolean isVisible() { return visible; }
}