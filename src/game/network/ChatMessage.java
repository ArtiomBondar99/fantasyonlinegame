package game.network;

import java.io.Serializable;

/**
 * Chat message with sender information.
 */
public class ChatMessage implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private String sender;
    private String message;
    private long timestamp;
    private boolean isSystemMessage;
    
    public ChatMessage(String sender, String message, boolean isSystemMessage) {
        this.sender = sender;
        this.message = message;
        this.timestamp = System.currentTimeMillis();
        this.isSystemMessage = isSystemMessage;
    }
    
    // Getters
    public String getSender() { return sender; }
    public String getMessage() { return message; }
    public long getTimestamp() { return timestamp; }
    public boolean isSystemMessage() { return isSystemMessage; }
}