package game.network;

import java.io.Serializable;

import game.map.Position;

/**
 * Represents an item's state for network transmission.
 */
public class ItemState implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private String type;
    private Position position;
    private boolean visible;
    
    public ItemState(String type, Position position, boolean visible) {
        this.type = type;
        this.position = position;
        this.visible = visible;
    }
    
    // Getters
    public String getType() { return type; }
    public Position getPosition() { return position; }
    public boolean isVisible() { return visible; }
}