package game.characters;

import game.map.Position;
import game.network.NetworkIdentifiable;
import java.io.Serializable;

/**
 * Example modifications for PlayerCharacter class to support networking.
 * Add these modifications to your existing PlayerCharacter class.
 * 
 * @author Artiom Bondar:332692730
 * @author Shahar Dahan:207336355
 */
public abstract class PlayerCharacterNetworked extends PlayerCharacter implements NetworkIdentifiable, Serializable {
    private static final long serialVersionUID = 1L;
    private int networkId = -1;
    
    public PlayerCharacterNetworked(String name) {
        super(name);
    }
    
    @Override
    public int getNetworkId() {
        return networkId;
    }
    
    @Override
    public void setNetworkId(int id) {
        this.networkId = id;
    }
    
    /**
     * Override moveToPosition to remove direct world interaction.
     * In multiplayer, movement is validated by the server.
     */
    @Override
    public boolean moveToPosition(Position newPos) {
        // In multiplayer, this is handled by the server
        // Client only requests movement
        return false;
    }
    
    /**
     * Override handleInteractions to remove direct world interaction.
     */
    @Override
    public void handleInteractions(Position pos) {
        // In multiplayer, this is handled by the server
    }
}



/**
 * Instructions for modifying your existing classes:
 * 
 * 1. In PlayerCharacter.java:
 *    - Add: implements NetworkIdentifiable, Serializable
 *    - Add: private static final long serialVersionUID = 1L;
 *    - Add: private int networkId = -1;
 *    - Add: getNetworkId() and setNetworkId() methods
 *    - Modify: moveToPosition() to return false (server handles movement)
 *    - Modify: handleInteractions() to be empty (server handles interactions)
 * 
 * 2. In Enemy.java:
 *    - Add: implements NetworkIdentifiable, Serializable
 *    - Add: private static final long serialVersionUID = 1L;
 *    - Add: private int networkId = -1;
 *    - Add: getNetworkId() and setNetworkId() methods
 *    - Modify: run() to check if running on server before executing
 * 
 * 3. In Position.java:
 *    - Add: implements Serializable
 *    - Add: private static final long serialVersionUID = 1L;
 * 
 * 4. In all GameItem subclasses:
 *    - Add: implements Serializable
 *    - Add: private static final long serialVersionUID = 1L;
 * 
 * 5. In Warrior.java, Mage.java, Archer.java:
 *    - Ensure they extend the modified PlayerCharacter
 *    - Add: private static final long serialVersionUID = 1L;
 * 
 * 6. In Dragon.java, Goblin.java, Orc.java:
 *    - Ensure they extend the modified Enemy
 *    - Add: private static final long serialVersionUID = 1L;
 */