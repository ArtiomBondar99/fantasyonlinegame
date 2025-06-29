package game.characters;

import java.io.Serializable;

import game.map.Position;
import game.network.NetworkIdentifiable;

/**
 * Example modifications for Enemy class to support networking.
 * Add these modifications to your existing Enemy class.
 */
public abstract class EnemyNetworked extends Enemy implements NetworkIdentifiable, Serializable {
    private static final long serialVersionUID = 1L;
    private int networkId = -1;
    
    public EnemyNetworked(int loot, Position position) {
        super(loot, position);
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
     * Override takeAction() to prevent client-side actions.
     */
    @Override
    public void takeAction() {
        // Do nothing on client side
    }
}