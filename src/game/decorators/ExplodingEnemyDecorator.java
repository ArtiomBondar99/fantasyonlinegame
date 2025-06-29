package game.decorators;

import game.characters.Enemy;


/**
 * Enemy decorator that causes area damage when the enemy dies.
 * Damages nearby players within 1 tile for 20% of the enemy's max health.
 */
public class ExplodingEnemyDecorator extends EnemyDecorator {
    private int maxHealth;
    private boolean hasExploded = false;

    public ExplodingEnemyDecorator(Enemy wrapped) {
        super(wrapped);
        this.maxHealth = wrapped.getHealth();
    }

    @Override
    public void setHealth(int health) {
        int previousHealth = getHealth();
        super.setHealth(health);
        
        // Check if enemy just died
        if (previousHealth > 0 && health <= 0 && !hasExploded) {
            explode();
            hasExploded = true;
        }
    }

    /**
     * Triggers explosion damage to nearby players.
     * In multiplayer, this just marks that explosion should happen.
     * The server will handle the actual damage calculation.
     */
    private void explode() {
        // In multiplayer, the server handles this
        // This is just a marker that explosion occurred
        System.out.println("[ExplodingEnemyDecorator] Enemy exploded!");
    }

    /**
     * For server-side use: calculates explosion damage
     */
    public int getExplosionDamage() {
        return (int)(maxHealth * 0.2); // 20% of max health
    }

    /**
     * For server-side use: gets explosion range
     */
    public int getExplosionRange() {
        return 1; // 1 tile radius
    }

    @Override
    public String getDisplaySymbol() {
        return wrapped.getDisplaySymbol();
    }

    @Override
    public boolean isDead() {
        boolean dead = super.isDead();
        if (dead && !hasExploded) {
            explode();
            hasExploded = true;
        }
        return dead;
    }
}