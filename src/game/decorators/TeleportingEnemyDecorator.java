package game.decorators;

import game.characters.Enemy;
import game.combat.Combatant;

/**
 * Enemy decorator that teleports the enemy to a random position on the map
 * if its health drops below 30% after being attacked.
 * In multiplayer, actual teleportation is handled by the server.
 */
public class TeleportingEnemyDecorator extends EnemyDecorator {
    private boolean hasTeleported = false;
    private int startingHealth;
    private boolean shouldTeleport = false;
    
    public TeleportingEnemyDecorator(Enemy enemy) {
        super(enemy);
        this.startingHealth = wrapped.getHealth();
    }
    
    public boolean hasTeleported() {
        return hasTeleported;
    }

    public void setHasTeleported(boolean teleported) {
        this.hasTeleported = teleported;
    }
    
    /**
     * Check if this enemy should teleport (for server to query)
     */
    public boolean shouldTeleport() {
        return shouldTeleport;
    }
    
    /**
     * Reset the teleport flag after server handles it
     */
    public void resetTeleportFlag() {
        shouldTeleport = false;
    }
    
    @Override
    public void receiveDamage(int amount, Combatant source) {
        wrapped.receiveDamage(amount, source);
        
        // Check if we should teleport
        if (!hasTeleported && wrapped.getHealth() > 0 && 
            wrapped.getHealth() < startingHealth * 0.3) {
            // Mark that we should teleport - server will handle the actual teleportation
            shouldTeleport = true;
            hasTeleported = true;
            wrapped.setActive(false);
            System.out.println("[TeleportingEnemyDecorator] Enemy marked for teleportation");
        }
    }

    @Override
    public String getDisplaySymbol() {
        return wrapped.getDisplaySymbol();
    }
}