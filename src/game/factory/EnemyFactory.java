package game.factory;

import game.builder.EnemyBuilder;
import game.characters.Enemy;
import game.characters.Goblin;
import game.characters.Orc;
import game.characters.Dragon;
import game.map.Position;
import game.decorators.ExplodingEnemyDecorator;
import game.decorators.TeleportingEnemyDecorator;
import game.decorators.VampireEnemyDecorator;
import java.util.*;

public class EnemyFactory {
    private static final Random random = new Random();
    private static final Map<String, Class<? extends Enemy>> enemyTypes = new HashMap<>();

    static {
        enemyTypes.put("Goblin", Goblin.class);
        enemyTypes.put("Orc", Orc.class);
        enemyTypes.put("Dragon", Dragon.class);
    }

    public static Enemy createRandomEnemy(Position position) {
        // Choose enemy type
        List<String> types = new ArrayList<>(enemyTypes.keySet());
        String selectedType = types.get(random.nextInt(types.size()));
        Class<? extends Enemy> enemyClass = enemyTypes.get(selectedType);
        
        // Use builder to create enemy with random stats
        Enemy baseEnemy = new EnemyBuilder()
                .setType(enemyClass)
                .setPosition(position)
                .setRandom()  // Random health, power, loot
                .build();
                
        return baseEnemy;
    }
    
    private static String chooseEnemyType() {
        // Simple random selection
        List<String> types = Arrays.asList("Goblin", "Orc", "Dragon");
        return types.get(random.nextInt(types.size()));
    }

    public static Enemy wrapWithRandomDecorator(Enemy enemy) {
        int type = random.nextInt(3);
        
        Enemy decorated = switch (type) {
            case 0 -> new ExplodingEnemyDecorator(enemy);
            case 1 -> new VampireEnemyDecorator(enemy);
            case 2 -> new TeleportingEnemyDecorator(enemy);
            default -> enemy;
        };
        
        // Preserve position and active state
        decorated.setPosition(enemy.getPosition());
        if (enemy.isActive()) {
            decorated.onPlayerMoved(enemy.getPosition()); // Preserve active state
        }
        
        return decorated;
    }

    public static Set<String> getSupportedEnemyTypes() {
        return enemyTypes.keySet();
    }
}
