package game.engine;


import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;


public class EnemyPool {
    private static EnemyPool INSTANCE;                  // Singleton
    private final ScheduledExecutorService scheduler;
    
    private EnemyPool(int poolSize) {
        this.scheduler = Executors.newScheduledThreadPool(poolSize);
    }

    public static synchronized EnemyPool init(int poolSize) {
        if (INSTANCE == null) INSTANCE = new EnemyPool(poolSize);
        return INSTANCE;
    }
    public static EnemyPool instance() { return INSTANCE; }

    public ScheduledExecutorService getScheduler() {
        return scheduler;
    }
    
    public void shutdown() {
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
    }
}
