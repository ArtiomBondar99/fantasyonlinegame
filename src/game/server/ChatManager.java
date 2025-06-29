package game.server;

import game.network.*;
import game.logging.LogManager;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Manages the chat system for the game.
 * Handles player messages and system notifications.
 * 
 * @author Artiom Bondar:332692730
 * @author Shahar Dahan:207336355
 */
public class ChatManager {
    private static final int MAX_HISTORY = 100;
    private final Queue<ChatMessage> messageHistory = new ConcurrentLinkedQueue<>();
    
    /**
     * Handles a chat message from a player.
     */
    public void handlePlayerMessage(String playerName, String message, GameServer server) {
        // Validate message
        if (message == null || message.trim().isEmpty()) {
            return;
        }
        
        // Trim message to reasonable length
        message = message.trim();
        if (message.length() > 200) {
            message = message.substring(0, 200);
        }
        
        // Log the message
        LogManager.log("Chat - " + playerName + ": " + message);
        
        // Create chat message
        ChatMessage chatMsg = new ChatMessage(playerName, message, false);
        addToHistory(chatMsg);
        
        // Broadcast to all clients
        GameMessage gameMsg = new GameMessage(MessageType.CHAT_MESSAGE);
        gameMsg.setMessage(playerName + ":::" + message + ":::false");
        server.broadcastMessage(gameMsg);
    }
    
    /**
     * Broadcasts a system message to all players.
     */
    public void broadcastSystemMessage(String message, GameServer server) {
        LogManager.log("System: " + message);
        
        ChatMessage chatMsg = new ChatMessage("System", message, true);
        addToHistory(chatMsg);
        
        GameMessage gameMsg = new GameMessage(MessageType.CHAT_MESSAGE);
        gameMsg.setMessage("System:::" + message + ":::true");
        server.broadcastMessage(gameMsg);
    }
    
    /**
     * Adds a message to the history.
     */
    private void addToHistory(ChatMessage message) {
        messageHistory.offer(message);
        
        // Limit history size
        while (messageHistory.size() > MAX_HISTORY) {
            messageHistory.poll();
        }
    }
    
    /**
     * Gets recent message history for a new player.
     */
    public List<ChatMessage> getRecentHistory(int count) {
        List<ChatMessage> recent = new ArrayList<>();
        Iterator<ChatMessage> iter = messageHistory.iterator();
        
        // Get last 'count' messages
        while (iter.hasNext() && recent.size() < count) {
            recent.add(iter.next());
        }
        
        return recent;
    }
}