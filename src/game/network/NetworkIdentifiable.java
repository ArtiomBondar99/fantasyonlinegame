package game.network;

import java.util.Arrays;

import game.client.GameClient;
import game.server.GameServer;

/**
 * Interface for entities that can be identified over the network.
 * 
 * @author Artiom Bondar:332692730
 * @author Shahar Dahan:207336355
 */
public interface NetworkIdentifiable {
    /**
     * Gets the network ID for this entity.
     */
    int getNetworkId();
    
    /**
     * Sets the network ID for this entity.
     */
    void setNetworkId(int id);
}

// Add these methods to your existing PlayerCharacter class:
/*
public abstract class PlayerCharacter extends AbstractCharacter implements NetworkIdentifiable {
    private int networkId = -1;
    
    @Override
    public int getNetworkId() {
        return networkId;
    }
    
    @Override
    public void setNetworkId(int id) {
        this.networkId = id;
    }
    
    // ... rest of existing PlayerCharacter code ...
}
*/

// Add these methods to your existing Enemy class:
/*
public abstract class Enemy extends AbstractCharacter implements Runnable, GameObserver, NetworkIdentifiable {
    private int networkId = -1;
    
    @Override
    public int getNetworkId() {
        return networkId;
    }
    
    @Override
    public void setNetworkId(int id) {
        this.networkId = id;
    }
    
    // ... rest of existing Enemy code ...
}
*/

// Instructions for modifying existing classes:
/*
MODIFICATIONS NEEDED FOR MULTIPLAYER:

1. In PlayerCharacter class:
   - Add implements NetworkIdentifiable
   - Add private int networkId = -1;
   - Add getNetworkId() and setNetworkId() methods
   - Remove direct GameWorld.getInstance() calls
   - Remove movement logic (handled by server now)

2. In Enemy class:
   - Add implements NetworkIdentifiable  
   - Add private int networkId = -1;
   - Add getNetworkId() and setNetworkId() methods
   - Remove direct player interaction (server handles this)

3. In Position class:
   - Add implements Serializable

4. In GameItem classes:
   - Ensure they're serializable for network transmission

5. In Main class:
   - Add option to start as server or client
   - Remove single-player game logic

Example main method:
*/

/**
 * Example modified Main class for client-server architecture.
 */
class NetworkMain {
    public static void main(String[] args) {
        if (args.length > 0 && args[0].equals("server")) {
            // Start server
            GameServer.main(Arrays.copyOfRange(args, 1, args.length));
        } else {
            // Start client
            GameClient.main(args);
        }
    }
}

/*
STARTUP INSTRUCTIONS:

1. Start the server:
   java game.Main server [port]
   
2. Start clients (multiple instances):
   java game.Main
   
   Each client will show a connection dialog where players can:
   - Enter server address (localhost for same machine)
   - Choose character name and class
   - Connect to the game

3. Game Features:
   - Multiple players see each other in real-time
   - Chat system for communication
   - Shared enemies that all players can fight
   - No PvP (players can't attack each other)
   - Abilities and potions work as before
   - Server handles all game logic
   - Clients only display and send input

4. Testing on single machine:
   - Run one server instance
   - Run multiple client instances
   - Each client is a separate player
*/