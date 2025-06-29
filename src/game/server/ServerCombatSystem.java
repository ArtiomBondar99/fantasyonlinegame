package game.server;

import game.combat.*;
import game.decorators.*;
import game.map.Position;

/**
 * Server-side combat resolution without GameWorld dependencies or popups.
 * Returns combat results that the server can use to send network messages.
 */
public class ServerCombatSystem {
    
    public static class CombatResult {
        public final int damageDealt;
        public final boolean wasCritical;
        public final boolean wasEvaded;
        
        public CombatResult(int damageDealt, boolean wasCritical, boolean wasEvaded) {
            this.damageDealt = damageDealt;
            this.wasCritical = wasCritical;
            this.wasEvaded = wasEvaded;
        }
    }
    
    /**
     * Resolves a single attack from attacker to defender.
     * Returns the combat result without any side effects.
     */
    public static CombatResult resolveAttack(Combatant attacker, Combatant defender) {
        // Get the base attacker type (unwrap decorators)
        Combatant baseAttacker = attacker;
        while (baseAttacker instanceof PlayerDecorator) {
            baseAttacker = ((PlayerDecorator) baseAttacker).getWrapped();
        }
        while (baseAttacker instanceof EnemyDecorator) {
            baseAttacker = ((EnemyDecorator) baseAttacker).getWrapped();
        }
        
        int healthBefore = defender.getHealth();
        boolean wasCritical = false;
        
        // Physical attack
        if (baseAttacker instanceof PhysicalAttacker) {
            PhysicalAttacker physAttacker = (PhysicalAttacker) baseAttacker;
            
            // Check for critical hit
            wasCritical = physAttacker.isCriticalHit();
            int damage = attacker.getPower(); // Use decorated power
            if (wasCritical) {
                damage *= 2;
                System.out.println("Critical hit!");
            }
            
            // Apply damage (defender's receiveDamage handles evasion/armor)
            defender.receiveDamage(damage, attacker);
        }
        // Magic attack
        else if (baseAttacker instanceof MagicAttacker) {
            MagicAttacker magicAttacker = (MagicAttacker) baseAttacker;
            
            // Calculate magic damage
            double baseDamage = attacker.getPower() * 1.5;
            
            // Check elemental advantages
            if (defender instanceof MagicAttacker) {
                MagicAttacker defenderMagic = (MagicAttacker) defender;
                if (magicAttacker.getElement().isStrongerThan(defenderMagic.getElement())) {
                    baseDamage *= 1.2;
                } else if (defenderMagic.getElement().isStrongerThan(magicAttacker.getElement())) {
                    baseDamage *= 0.8;
                }
            }
            
            defender.receiveDamage((int) baseDamage, attacker);
        }
        
        int healthAfter = defender.getHealth();
        int actualDamage = healthBefore - healthAfter;
        boolean wasEvaded = (actualDamage == 0 && healthBefore > 0);
        
        return new CombatResult(actualDamage, wasCritical, wasEvaded);
    }
    
    /**
     * Resolves full combat between two combatants.
     * Returns results for both attacker and defender attacks.
     */
    public static CombatRoundResult resolveCombatRound(Combatant attacker, Combatant defender, 
                                                       Position attackerPos, Position defenderPos) {
        CombatRoundResult result = new CombatRoundResult();
        
        // Check if attacker is in range
        boolean attackerInRange = checkRange(attacker, attackerPos, defenderPos);
        if (!attackerInRange) {
            return result; // No combat occurs
        }
        
        // Attacker attacks first
        result.attackerResult = resolveAttack(attacker, defender);
        
        // If defender is still alive and in range, they counter-attack
        if (!defender.isDead()) {
            boolean defenderInRange = checkRange(defender, defenderPos, attackerPos);
            if (defenderInRange) {
                result.defenderResult = resolveAttack(defender, attacker);
            }
        }
        
        return result;
    }
    
    /**
     * Checks if attacker is in range to attack target.
     */
    private static boolean checkRange(Combatant attacker, Position attackerPos, Position targetPos) {
        int distance = attackerPos.distanceTo(targetPos);
        
        // Get base type for range checking
        Combatant base = attacker;
        while (base instanceof PlayerDecorator) {
            base = ((PlayerDecorator) base).getWrapped();
        }
        while (base instanceof EnemyDecorator) {
            base = ((EnemyDecorator) base).getWrapped();
        }
        
        if (base instanceof RangedFighter) {
            return distance <= ((RangedFighter) base).getRange();
        } else if (base instanceof MeleeFighter) {
            return distance <= 1;
        }
        
        return false;
    }
    
    public static class CombatRoundResult {
        public CombatResult attackerResult;
        public CombatResult defenderResult;
    }
}