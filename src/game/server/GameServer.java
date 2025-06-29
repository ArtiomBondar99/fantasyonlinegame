package game.server;

import game.characters.*;
import game.decorators.PlayerDecorator;
import game.items.*;
import game.map.*;
import game.network.*;
import game.logging.LogManager;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * Main game server that manages all game state and client connections.
 * Handles game logic, player management, and state synchronization.
 * 
 * @author Artiom Bondar:332692730
 * @author Shahar Dahan:207336355
 */
public class GameServer {
    private static final int DEFAULT_PORT = 62222;
    private static final int MAX_PLAYERS = 10;
    
    private ServerSocket serverSocket;
    private final Map<Integer, ClientHandler> clients = new ConcurrentHashMap<>();
    private final ServerGameWorld gameWorld;
    private final ChatManager chatManager;
    private boolean running = true;
    private int nextClientId = 1;
    
    private final ExecutorService clientExecutor = Executors.newCachedThreadPool();
    private final ScheduledExecutorService gameUpdateExecutor = Executors.newScheduledThreadPool(2);
    
    /**
     * Creates a new game server instance.
     * 
     * @param port The port to listen on
     */
    public GameServer(int port) throws IOException {
        this.serverSocket = new ServerSocket(port);
        this.gameWorld = new ServerGameWorld(this);
        this.chatManager = new ChatManager();
        
        LogManager.log("Server started on port " + port);
        System.out.println("Game Server started on port " + port);
    }
    
    public ClientHandler getClient(int clientId) {
        return clients.get(clientId);
    }
    
    /**
     * Starts the server and begins accepting client connections.
     */
    public void start() {
        // Start game world updates
        gameWorld.initialize();
        
        // Schedule periodic state broadcasts
        gameUpdateExecutor.scheduleAtFixedRate(this::broadcastGameState, 0, 100, TimeUnit.MILLISECONDS);
        
        // Accept client connections
        while (running) {
            try {
                Socket clientSocket = serverSocket.accept();
                
                if (clients.size() >= MAX_PLAYERS) {
                    // Server full
                    PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true);
                    out.println("SERVER_FULL");
                    clientSocket.close();
                    continue;
                }
                
                int clientId = nextClientId++;
                ClientHandler handler = new ClientHandler(clientId, clientSocket, this);
                clients.put(clientId, handler);
                clientExecutor.execute(handler);
                
                LogManager.log("Client " + clientId + " connected from " + clientSocket.getInetAddress());
                
            } catch (IOException e) {
                if (running) {
                    e.printStackTrace();
                }
            }
        }
    }
    
    /**
     * Handles a new player joining the game.
     */
    public synchronized void handlePlayerJoin(int clientId, String playerName, String playerType) {
        try {
            // Create player character based on type
            PlayerCharacter player = switch (playerType) {
                case "Warrior" -> new Warrior(playerName);
                case "Mage" -> new Mage(playerName);
                case "Archer" -> new Archer(playerName);
                default -> new Warrior(playerName); // Default
            };
            
            // Set unique ID for network identification
            player.setNetworkId(clientId);
            
            // Add to game world
            gameWorld.addPlayer(player);
            
            // Notify all clients
            GameMessage joinMsg = new GameMessage(MessageType.PLAYER_JOINED);
            joinMsg.setPlayerId(clientId);
            joinMsg.setPlayerName(playerName);
            joinMsg.setPosition(player.getPosition());
            broadcastMessage(joinMsg);
            
            // Send initial state to new player
            ClientHandler handler = clients.get(clientId);
            if (handler != null) {
                handler.sendMessage(createFullStateMessage());
            }
            
            // Broadcast chat notification
            chatManager.broadcastSystemMessage(playerName + " has joined the game!", this);
            
        } catch (Exception e) {
            LogManager.log("Error handling player join: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Handles player movement request.
     */
    public synchronized void handlePlayerMove(int clientId, Position newPos) {
        PlayerCharacter player = gameWorld.getPlayerById(clientId);
        if (player != null && gameWorld.validateAndMovePlayer(player, newPos)) {
            // Movement successful, broadcast update
            GameMessage moveMsg = new GameMessage(MessageType.PLAYER_MOVED);
            moveMsg.setPlayerId(clientId);
            moveMsg.setPosition(newPos);
            broadcastMessage(moveMsg);
            
            // Send updated player state with inventory
            GameMessage updateMsg = new GameMessage(MessageType.PLAYER_UPDATE);
            updateMsg.setPlayerId(clientId);
            updateMsg.setHealth(player.getHealth());
            updateMsg.setPower(player.getPower());
            
            // Create full player state for inventory update
            PlayerState fullState = new PlayerState(
                clientId,
                player.getName(),
                player.getPosition(),
                player.getHealth(),
                player.getPower(),
                getBaseClassName(player),
                player.getLifePotionCount(),
                player.getPowerPotionCount(),
                player.getTreasurePoints()
            );
            List<PlayerState> states = new ArrayList<>();
            states.add(fullState);
            updateMsg.setPlayerStates(states);
            
            broadcastMessage(updateMsg);
        } else {
            // Movement failed, send error to client
            ClientHandler handler = clients.get(clientId);
            if (handler != null) {
                GameMessage errorMsg = new GameMessage(MessageType.MOVE_FAILED);
                errorMsg.setMessage("Invalid move");
                handler.sendMessage(errorMsg);
            }
        }
    }
    
    /**
     * Handles player using a potion.
     */
    public synchronized void handleUsePotion(int clientId, String potionType) {
        PlayerCharacter player = gameWorld.getPlayerById(clientId);
        if (player != null) {
            boolean used = false;
            if ("LIFE".equals(potionType)) {
                used = player.usePotion();
            } else if ("POWER".equals(potionType)) {
                used = player.usePowerPotion();
            }
            
            if (used) {
                // Broadcast player state update
                GameMessage updateMsg = new GameMessage(MessageType.PLAYER_UPDATE);
                updateMsg.setPlayerId(clientId);
                updateMsg.setHealth(player.getHealth());
                updateMsg.setPower(player.getPower());
                broadcastMessage(updateMsg);
            }
        }
    }
    
    /**
     * Handles player activating a decorator ability.
     */
    public synchronized void handleActivateAbility(int clientId, String abilityType) {
        PlayerCharacter player = gameWorld.getPlayerById(clientId);
        if (player != null) {
            boolean activated = gameWorld.activatePlayerAbility(player, abilityType);
            if (activated) {
                GameMessage abilityMsg = new GameMessage(MessageType.ABILITY_ACTIVATED);
                abilityMsg.setPlayerId(clientId);
                abilityMsg.setMessage(abilityType);
                broadcastMessage(abilityMsg);
            }
        }
    }
    
    /**
     * Handles chat messages from players.
     */
    public void handleChatMessage(int clientId, String message) {
        PlayerCharacter player = gameWorld.getPlayerById(clientId);
        if (player != null) {
            chatManager.handlePlayerMessage(player.getName(), message, this);
        }
    }
    
    /**
     * Handles client disconnection.
     */
    public synchronized void handleClientDisconnect(int clientId) {
        ClientHandler handler = clients.remove(clientId);
        if (handler != null) {
            PlayerCharacter player = gameWorld.removePlayer(clientId);
            if (player != null) {
                // Notify all clients
                GameMessage leaveMsg = new GameMessage(MessageType.PLAYER_LEFT);
                leaveMsg.setPlayerId(clientId);
                broadcastMessage(leaveMsg);
                
                chatManager.broadcastSystemMessage(player.getName() + " has left the game.", this);
            }
        }
        LogManager.log("Client " + clientId + " disconnected");
    }
    
    /**
     * Broadcasts a message to all connected clients.
     */
    public void broadcastMessage(GameMessage message) {
        for (ClientHandler handler : clients.values()) {
            handler.sendMessage(message);
        }
    }
    
    /**
     * Broadcasts a message to all clients except one.
     */
    public void broadcastMessageExcept(GameMessage message, int exceptClientId) {
        for (Map.Entry<Integer, ClientHandler> entry : clients.entrySet()) {
            if (entry.getKey() != exceptClientId) {
                entry.getValue().sendMessage(message);
            }
        }
    }
    
    /**
     * Periodically broadcasts the full game state to all clients.
     */
    private void broadcastGameState() {
        if (!clients.isEmpty()) {
            GameMessage stateMsg = createFullStateMessage();
            broadcastMessage(stateMsg);
        }
    }
    
    /**
     * Creates a message containing the full game state.
     */
    public GameMessage createFullStateMessage() {
        GameMessage msg = new GameMessage(MessageType.FULL_STATE);
        
        // Add all players with inventory info
        List<PlayerState> playerStates = new ArrayList<>();
        for (PlayerCharacter player : gameWorld.getAllPlayers()) {
            PlayerState state = new PlayerState(
                player.getNetworkId(),
                player.getName(),
                player.getPosition(),
                player.getHealth(),
                player.getPower(),
                getBaseClassName(player),
                player.getLifePotionCount(),
                player.getPowerPotionCount(),
                player.getTreasurePoints()
            );
            playerStates.add(state);
        }
        msg.setPlayerStates(playerStates);
        
        // Rest of the method remains the same...
        // Add visible enemies
        List<EnemyState> enemyStates = new ArrayList<>();
        for (Enemy enemy : gameWorld.getAllEnemies()) {
            if (gameWorld.isEnemyVisibleToAnyPlayer(enemy)) {
                EnemyState state = new EnemyState(
                    enemy.getNetworkId(),
                    enemy.getClass().getSimpleName(),
                    enemy.getPosition(),
                    enemy.getHealth(),
                    enemy.isVisible()
                );
                enemyStates.add(state);
            }
        }
        msg.setEnemyStates(enemyStates);
        
        // Add visible items
        List<ItemState> itemStates = new ArrayList<>();
        for (GameItem item : gameWorld.getAllItems()) {
            if (gameWorld.isItemVisibleToAnyPlayer(item)) {
                String itemType = item.getClass().getSimpleName();
                ItemState state = new ItemState(
                    itemType,
                    item.getPosition(),
                    item.isVisible()
                );
                itemStates.add(state);
            }
        }
        msg.setItemStates(itemStates);
        
        return msg;
    }
    
    public void sendDamageToPlayer(int playerId, GameMessage damageMsg) {
        ClientHandler handler = clients.get(playerId);
        if (handler != null) {
            handler.sendMessage(damageMsg);
        }
    }
    
    private String getBaseClassName(PlayerCharacter player) {
        PlayerCharacter base = player;
        while (base instanceof PlayerDecorator) {
            base = ((PlayerDecorator) base).getWrapped();
        }
        return base.getClass().getSimpleName();
    }
    
    /**
     * Shuts down the server gracefully.
     */
    public void shutdown() {
        running = false;
        
        // Notify all clients
        GameMessage shutdownMsg = new GameMessage(MessageType.SERVER_SHUTDOWN);
        broadcastMessage(shutdownMsg);
        
        // Close all client connections
        for (ClientHandler handler : clients.values()) {
            handler.disconnect();
        }
        
        // Shutdown executors
        clientExecutor.shutdown();
        gameUpdateExecutor.shutdown();
        gameWorld.shutdown();
        
        try {
            serverSocket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        
        LogManager.log("Server shutdown complete");
    }
    
    public ServerGameWorld getGameWorld() {
        return gameWorld;
    }
    
    /**
     * Main method to start the server.
     */
    public static void main(String[] args) {
        int port = DEFAULT_PORT;
        
        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                System.err.println("Invalid port number. Using default: " + DEFAULT_PORT);
            }
        }
        
        try {
            GameServer server = new GameServer(port);
            
            // Add shutdown hook
            Runtime.getRuntime().addShutdownHook(new Thread(server::shutdown));
            
            // Start server
            server.start();
            
        } catch (IOException e) {
            System.err.println("Failed to start server: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public synchronized void handlePlayerAttackRequest(int clientId, Position targetPos) {
        PlayerCharacter player = gameWorld.getPlayerById(clientId);
        if (player == null) return;
        
        // Find enemy at target position
        Enemy targetEnemy = null;
        for (Enemy enemy : gameWorld.getAllEnemies()) {
            if (enemy.getPosition().equals(targetPos)) {
                targetEnemy = enemy;
                break;
            }
        }
        
        if (targetEnemy != null) {
            // Use combat manager instead of direct attack
            gameWorld.handlePlayerCombat(player, targetEnemy);
        } else {
            // No enemy at target position
            ClientHandler handler = clients.get(clientId);
            if (handler != null) {
                GameMessage errorMsg = new GameMessage(MessageType.ERROR);
                errorMsg.setMessage("No target at that position");
                handler.sendMessage(errorMsg);
            }
        }
    }
}