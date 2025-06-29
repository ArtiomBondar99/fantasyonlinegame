package game.server;

import game.network.*;
import game.map.Position;
import game.logging.LogManager;

import java.io.*;
import java.net.*;

/**
 * Handles communication with a single client.
 * Each client connection runs in its own thread.
 * 
 * @author Artiom Bondar:332692730
 * @author Shahar Dahan:207336355
 */
public class ClientHandler implements Runnable {
    private final int clientId;
    private final Socket socket;
    private final GameServer server;
    private ObjectOutputStream out;
    private ObjectInputStream in;
    private boolean connected = true;
    private String playerName;
    
    /**
     * Creates a new client handler.
     * 
     * @param clientId Unique ID for this client
     * @param socket The client's socket connection
     * @param server Reference to the game server
     */
    public ClientHandler(int clientId, Socket socket, GameServer server) {
        this.clientId = clientId;
        this.socket = socket;
        this.server = server;
    }
    
    @Override
    public void run() {
        try {
            // Set up streams
            out = new ObjectOutputStream(socket.getOutputStream());
            in = new ObjectInputStream(socket.getInputStream());
            
            // Send welcome message with client ID
            GameMessage welcome = new GameMessage(MessageType.WELCOME);
            welcome.setPlayerId(clientId);
            sendMessage(welcome);
            
            // Main message loop
            while (connected) {
                try {
                    GameMessage message = (GameMessage) in.readObject();
                    handleMessage(message);
                } catch (EOFException | SocketException e) {
                    // Client disconnected
                    break;
                } catch (ClassNotFoundException e) {
                    LogManager.log("Invalid message from client " + clientId + ": " + e.getMessage());
                }
            }
            
        } catch (IOException e) {
            LogManager.log("Error with client " + clientId + ": " + e.getMessage());
        } finally {
            disconnect();
        }
    }
    
    /**
     * Handles incoming messages from the client.
     */
    private void handleMessage(GameMessage message) {
        try {
            switch (message.getType()) {
                case JOIN_GAME:
                    playerName = message.getPlayerName();
                    String playerType = message.getMessage(); // Contains player class type
                    server.handlePlayerJoin(clientId, playerName, playerType);
                    break;
                    
                case MOVE_REQUEST:
                    Position newPos = message.getPosition();
                    server.handlePlayerMove(clientId, newPos);
                    break;
                    
                case USE_POTION:
                    String potionType = message.getMessage();
                    server.handleUsePotion(clientId, potionType);
                    break;
                    
                case ACTIVATE_ABILITY:
                    String abilityType = message.getMessage();
                    server.handleActivateAbility(clientId, abilityType);
                    break;
                    
                case CHAT_MESSAGE:
                    String chatText = message.getMessage();
                    server.handleChatMessage(clientId, chatText);
                    break;
                    
                case DISCONNECT:
                    connected = false;
                    break;
                    
                case ATTACK_REQUEST:
                    Position targetPos = message.getPosition();
                    server.handlePlayerAttackRequest(clientId, targetPos);
                    break;
                    
                default:
                    LogManager.log("Unknown message type from client " + clientId + ": " + message.getType());
            }
        } catch (Exception e) {
            LogManager.log("Error handling message from client " + clientId + ": " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Sends a message to this client.
     */
    public synchronized void sendMessage(GameMessage message) {
        if (connected && out != null) {
            try {
                out.writeObject(message);
                out.flush();
            } catch (IOException e) {
                LogManager.log("Failed to send message to client " + clientId + ": " + e.getMessage());
                disconnect();
            }
        }
    }
    
    /**
     * Disconnects this client.
     */
    public void disconnect() {
        if (connected) {
            connected = false;
            
            // Notify server
            server.handleClientDisconnect(clientId);
            
            // Close streams and socket
            try {
                if (in != null) in.close();
                if (out != null) out.close();
                if (socket != null && !socket.isClosed()) socket.close();
            } catch (IOException e) {
                // Ignore errors during cleanup
            }
            
            LogManager.log("Client " + clientId + " (" + playerName + ") disconnected");
        }
    }
    
    public int getClientId() {
        return clientId;
    }
    
    public String getPlayerName() {
        return playerName;
    }
    
    public boolean isConnected() {
        return connected;
    }
}