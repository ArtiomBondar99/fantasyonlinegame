package game.client;

import game.network.*;
import game.map.Position;
import game.audio.SoundPlayer;
import game.gui.ClientGameFrame;
import game.gui.PopupPanel;
import javax.swing.*;
import java.awt.GridLayout;
import java.io.*;
import java.net.*;
import java.util.concurrent.*;

/**
 * Main client application that connects to the game server.
 * Handles all client-side logic and GUI updates.
 * 
 * @author Artiom Bondar:332692730
 * @author Shahar Dahan:207336355
 */
public class GameClient {
    private static final String DEFAULT_HOST = "localhost";
    private static final int DEFAULT_PORT = 62222;
    
    private Socket socket;
    private ObjectOutputStream out;
    private ObjectInputStream in;
    private boolean connected = false;
    
    private int playerId = -1;
    private String playerName;
    private String playerType;
    
    private ClientGameFrame gameFrame;
    private ClientGameState gameState;
    
    private final ExecutorService messageHandler = Executors.newSingleThreadExecutor();
    
    /**
     * Creates a new game client.
     */
    public GameClient() {
        this.gameState = new ClientGameState();
    }
    
    /**
     * Connects to the game server.
     * 
     * @param host Server hostname
     * @param port Server port
     * @return true if connection successful
     */
    public boolean connect(String host, int port) {
        try {
            socket = new Socket(host, port);
            out = new ObjectOutputStream(socket.getOutputStream());
            in = new ObjectInputStream(socket.getInputStream());
            connected = true;
            
            // Start message receiver thread
            messageHandler.execute(this::receiveMessages);
            
            System.out.println("Connected to server at " + host + ":" + port);
            return true;
            
        } catch (IOException e) {
            JOptionPane.showMessageDialog(null, 
                "Failed to connect to server: " + e.getMessage(),
                "Connection Error", 
                JOptionPane.ERROR_MESSAGE);
            return false;
        }
    }
    
    /**
     * Joins the game with the specified player details.
     */
    public void joinGame(String name, String type) {
        this.playerName = name;
        this.playerType = type;
        
        GameMessage joinMsg = new GameMessage(MessageType.JOIN_GAME);
        joinMsg.setPlayerName(name);
        joinMsg.setMessage(type); // Character class
        sendMessage(joinMsg);
    }
    
    /**
     * Main message receiving loop.
     */
    private void receiveMessages() {
        while (connected) {
            try {
                GameMessage message = (GameMessage) in.readObject();
                handleServerMessage(message);
            } catch (EOFException | SocketException e) {
                // Server disconnected
                handleDisconnect();
                break;
            } catch (Exception e) {
                System.err.println("Error receiving message: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }
    
    /**
     * Handles messages received from the server.
     */
    private void handleServerMessage(GameMessage message) {
        SwingUtilities.invokeLater(() -> {
            switch (message.getType()) {
                case WELCOME:
                    playerId = message.getPlayerId();
                    System.out.println("Assigned player ID: " + playerId);
                    
                    // Create and show game GUI
                    gameFrame = new ClientGameFrame(this, playerName);
                    gameFrame.setVisible(true);
                    break;
                    
                case FULL_STATE:
                    updateFullGameState(message);
                    break;
                    
                case PLAYER_MOVED:
                    handlePlayerMoved(message);
                    break;
                    
                case PLAYER_JOINED:
                    handlePlayerJoined(message);
                    break;
                    
                case PLAYER_LEFT:
                    handlePlayerLeft(message);
                    break;
                    
                case PLAYER_UPDATE:
                    handlePlayerUpdate(message);
                    break;
                    
                case CHAT_MESSAGE:
                    handleChatMessage(message);
                    break;
                    
                case ABILITY_ACTIVATED:
                    handleAbilityActivated(message);
                    break;
                    
                case ERROR:
                case MOVE_FAILED:
                    JOptionPane.showMessageDialog(gameFrame, 
                        message.getMessage(), 
                        "Error", 
                        JOptionPane.WARNING_MESSAGE);
                    break;
                    
                case SERVER_SHUTDOWN:
                    JOptionPane.showMessageDialog(gameFrame, 
                        "Server is shutting down", 
                        "Server Shutdown", 
                        JOptionPane.WARNING_MESSAGE);
                    disconnect();
                    break;
                    
                case DAMAGE_DEALT:
                    handleDamageDealt(message);
                    break;
                    
                case ENEMY_UPDATE:
                    handleEnemyUpdate(message);
                    break;
                    
                case COMBAT_UPDATE:
                    handleCombatUpdate(message);
                    break;
                    
                case ITEM_COLLECTED:
                    handleItemCollected(message);
                    break;
                    
                default:
                    System.out.println("Unhandled message type: " + message.getType());
            }
        });
    }
    
    private void handleCombatUpdate(GameMessage message) {
        String combatState = message.getMessage();
        
        if ("COMBAT_START".equals(combatState)) {
            if (gameFrame != null) {
                gameFrame.setCombatState(true);
            }
        } else if ("COMBAT_END".equals(combatState)) {
            if (gameFrame != null) {
                gameFrame.setCombatState(false);
            }
        }
    }
    
    private void handleEnemyUpdate(GameMessage message) {
        int enemyId = message.getPlayerId(); // Enemy ID stored in playerId field
        Position newPos = message.getPosition();
        
        // Update enemy position in game state
        gameState.updateEnemyPosition(enemyId, newPos);
        
        // Refresh display
        if (gameFrame != null) {
            gameFrame.updateGameState(gameState);
        }
    }
    
    private void handleDamageDealt(GameMessage message) {
        // Only show popup if this damage is for our player or from our player
        int targetPlayerId = message.getTargetPlayerId();
        boolean isMyDamage = (targetPlayerId == playerId) || (message.getPlayerId() == playerId);
        
        if (!isMyDamage) {
            return; // Don't show damage popups for other players
        }
        
        Position pos = message.getPosition();
        int damage = message.getHealth();
        String type = message.getMessage();
        
        SwingUtilities.invokeLater(() -> {
            switch (type) {
                case "CRIT":
                    PopupPanel.critDamagePopup(pos, damage, gameFrame);
                    SoundPlayer.playSound("critical-hit.wav");
                    break;
                case "MISS":
                    PopupPanel.missPopup(pos, gameFrame);
                    break;
                case "EXPLOSION":
                    if (gameFrame != null) {
                        gameFrame.addChatMessage("System", "Explosion damage: " + damage + "!", true);
                    }
                    PopupPanel.damagePopup(pos, damage, gameFrame);
                    break;
                case "SHIELD_BLOCK":
                    // For shield, use chat message instead of popup
                    gameFrame.addChatMessage("System", "Your shield blocked the incoming attack!", true);
                    SoundPlayer.playSound("shield.wav");
                    break;
                case "ENEMY_DEATH":
                    SoundPlayer.playSound("kill.wav");
                    break;
                default:
                    PopupPanel.damagePopup(pos, damage, gameFrame);
                    SoundPlayer.playSound("hit.wav");
                    break;
            }
        });
    }
    
    private void handleItemCollected(GameMessage message) {
        String itemType = message.getMessage();
        
        switch (itemType) {
            case "Treasure":
                SoundPlayer.playSound("treasure.wav");
                break;
            case "Potion":
            case "PowerPotion":
                SoundPlayer.playSound("life-spell.wav");
                break;
        }
    }
    
    /**
     * Updates the complete game state from server.
     */
    private void updateFullGameState(GameMessage message) {
        gameState.updateFromMessage(message);
        
        if (gameFrame != null) {
            gameFrame.updateGameState(gameState);
            
            // Update player status if this is our player
            PlayerState myPlayer = gameState.getPlayer(playerId);
            if (myPlayer != null) {
                gameFrame.updatePlayerStatus(myPlayer);
            }
        }
    }
    
    /**
     * Handles player movement updates.
     */
    private void handlePlayerMoved(GameMessage message) {
        gameState.updatePlayerPosition(message.getPlayerId(), message.getPosition());
        if (gameFrame != null) {
            gameFrame.updateGameState(gameState);
        }
    }
    
    /**
     * Handles new player joining.
     */
    private void handlePlayerJoined(GameMessage message) {
        // Full state update will follow, just show notification
        if (gameFrame != null) {
            gameFrame.addChatMessage("System", 
                message.getPlayerName() + " has joined the game", 
                true);
        }
    }
    
    /**
     * Handles player leaving.
     */
    private void handlePlayerLeft(GameMessage message) {
        gameState.removePlayer(message.getPlayerId());
        if (gameFrame != null) {
            gameFrame.updateGameState(gameState);
        }
    }
    
    /**
     * Handles player stat updates.
     */
    private void handlePlayerUpdate(GameMessage message) {
        // Check if this update includes full player states (with inventory)
        if (message.getPlayerStates() != null && !message.getPlayerStates().isEmpty()) {
            // Update with full state including inventory
            for (PlayerState state : message.getPlayerStates()) {
                gameState.updatePlayerFull(state);
                if (state.getPlayerId() == playerId) {
                    gameFrame.updatePlayerStatus(state);
                }
            }
        } else {
            // Old style update (just health/power)
            gameState.updatePlayerStats(message.getPlayerId(), 
                message.getHealth(), 
                message.getPower());
                
            if (gameFrame != null) {
                gameFrame.updateGameState(gameState);
                
                if (message.getPlayerId() == playerId) {
                    PlayerState myPlayer = gameState.getPlayer(playerId);
                    if (myPlayer != null) {
                        gameFrame.updatePlayerStatus(myPlayer);
                    }
                }
            }
        }
    }
    
    /**
     * Handles chat messages.
     */
    private void handleChatMessage(GameMessage message) {
        if (gameFrame != null && message.getMessage() != null) {
            // Extract chat data from the message
            String chatText = message.getMessage();
            // Message format: "sender:::message:::isSystem"
            String[] parts = chatText.split(":::");
            if (parts.length >= 2) {
                String sender = parts[0];
                String text = parts[1];
                boolean isSystem = parts.length > 2 && "true".equals(parts[2]);
                gameFrame.addChatMessage(sender, text, isSystem);
            }
        }
    }
    
    /**
     * Handles ability activation notifications.
     */
    private void handleAbilityActivated(GameMessage message) {
        if (gameFrame != null) {
            PlayerState player = gameState.getPlayer(message.getPlayerId());
            if (player != null) {
                String ability = message.getMessage();
                gameFrame.addChatMessage("System", 
                    player.getName() + " activated " + ability, 
                    true);
                
                // Play sound for abilities
                if (message.getPlayerId() == playerId) {
                    switch (ability) {
                        case "BOOST":
                        case "SHIELD":
                        case "REGEN":
                            SoundPlayer.playSound("magic-spell.wav");
                            break;
                    }
                }
            }
        }
    }
    
    /**
     * Sends a movement request to the server.
     */
    public void requestMove(Position newPos) {
        GameMessage moveMsg = new GameMessage(MessageType.MOVE_REQUEST);
        moveMsg.setPosition(newPos);
        sendMessage(moveMsg);
    }
    
    /**
     * Sends a potion use request to the server.
     */
    public void requestUsePotion(String potionType) {
        GameMessage potionMsg = new GameMessage(MessageType.USE_POTION);
        potionMsg.setMessage(potionType);
        sendMessage(potionMsg);
    }
    
    /**
     * Sends an ability activation request to the server.
     */
    public void requestActivateAbility(String abilityType) {
        GameMessage abilityMsg = new GameMessage(MessageType.ACTIVATE_ABILITY);
        abilityMsg.setMessage(abilityType);
        sendMessage(abilityMsg);
    }
    
    /**
     * Sends a chat message to the server.
     */
    public void sendChatMessage(String text) {
        GameMessage chatMsg = new GameMessage(MessageType.CHAT_MESSAGE);
        chatMsg.setMessage(text);
        sendMessage(chatMsg);
    }
    
    /**
     * Sends a message to the server.
     */
    public synchronized void sendMessage(GameMessage message) {
        if (connected && out != null) {
            try {
                out.writeObject(message);
                out.flush();
            } catch (IOException e) {
                System.err.println("Failed to send message: " + e.getMessage());
                handleDisconnect();
            }
        }
    }
    
    /**
     * Handles disconnection from server.
     */
    private void handleDisconnect() {
        if (connected) {
            connected = false;
            SwingUtilities.invokeLater(() -> {
                if (gameFrame != null) {
                    JOptionPane.showMessageDialog(gameFrame,
                        "Disconnected from server",
                        "Connection Lost",
                        JOptionPane.ERROR_MESSAGE);
                    gameFrame.dispose();
                }
            });
            disconnect();
        }
    }
    
    /**
     * Disconnects from the server.
     */
    public void disconnect() {
        if (connected) {
            connected = false;
            
            // Send disconnect message
            GameMessage disconnectMsg = new GameMessage(MessageType.DISCONNECT);
            sendMessage(disconnectMsg);
            
            // Close resources
            messageHandler.shutdown();
            try {
                if (in != null) in.close();
                if (out != null) out.close();
                if (socket != null) socket.close();
            } catch (IOException e) {
                // Ignore
            }
        }
    }
    
    public int getPlayerId() { return playerId; }
    public boolean isConnected() { return connected; }
    public ClientGameState getGameState() { return gameState; }
    
    /**
     * Shows the initial connection dialog.
     */
    private static ConnectionInfo showConnectionDialog() {
        JPanel panel = new JPanel(new GridLayout(5, 2, 5, 5));
        
        JTextField hostField = new JTextField(DEFAULT_HOST);
        JTextField portField = new JTextField(String.valueOf(DEFAULT_PORT));
        JTextField nameField = new JTextField("Player" + (int)(Math.random() * 1000));
        JComboBox<String> typeCombo = new JComboBox<>(new String[]{"Warrior", "Mage", "Archer"});
        
        panel.add(new JLabel("Server Host:"));
        panel.add(hostField);
        panel.add(new JLabel("Server Port:"));
        panel.add(portField);
        panel.add(new JLabel("Player Name:"));
        panel.add(nameField);
        panel.add(new JLabel("Character Type:"));
        panel.add(typeCombo);
        
        int result = JOptionPane.showConfirmDialog(null, panel, 
            "Connect to Game Server", JOptionPane.OK_CANCEL_OPTION);
            
        if (result == JOptionPane.OK_OPTION) {
            try {
                String host = hostField.getText().trim();
                int port = Integer.parseInt(portField.getText().trim());
                String name = nameField.getText().trim();
                String type = (String) typeCombo.getSelectedItem();
                
                if (name.isEmpty()) {
                    JOptionPane.showMessageDialog(null, "Please enter a player name");
                    return null;
                }
                
                return new ConnectionInfo(host, port, name, type);
            } catch (NumberFormatException e) {
                JOptionPane.showMessageDialog(null, "Invalid port number");
                return null;
            }
        }
        
        return null;
    }
    
    /**
     * Connection info holder class.
     */
    private static class ConnectionInfo {
        final String host;
        final int port;
        final String playerName;
        final String playerType;
        
        ConnectionInfo(String host, int port, String playerName, String playerType) {
            this.host = host;
            this.port = port;
            this.playerName = playerName;
            this.playerType = playerType;
        }
    }
    
    /**
     * Main method to start the client.
     */
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            ConnectionInfo info = showConnectionDialog();
            if (info != null) {
                GameClient client = new GameClient();
                
                if (client.connect(info.host, info.port)) {
                    client.joinGame(info.playerName, info.playerType);
                    
                    // Add shutdown hook
                    Runtime.getRuntime().addShutdownHook(new Thread(client::disconnect));
                } else {
                    System.exit(1);
                }
            } else {
                System.exit(0);
            }
        });
    }
}