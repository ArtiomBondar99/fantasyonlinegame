package game.network;

/**
 * Defines message types for client-server communication.
 * 
 * @author Artiom Bondar:332692730
 * @author Shahar Dahan:207336355
 */
public enum MessageType {
    // Server to Client
    WELCOME,
    FULL_STATE,
    PLAYER_UPDATE,
    PLAYER_MOVED,
    PLAYER_JOINED,
    PLAYER_LEFT,
    ENEMY_UPDATE,
    ITEM_UPDATE,
    ITEM_COLLECTED, 
    COMBAT_UPDATE,
    CHAT_MESSAGE,
    ABILITY_ACTIVATED,
    SERVER_SHUTDOWN,
    ERROR,
    MOVE_FAILED,
    DAMAGE_DEALT,
    
    // Client to Server
    JOIN_GAME,
    MOVE_REQUEST,
    USE_POTION,
    ACTIVATE_ABILITY,
    ATTACK_REQUEST,
    DISCONNECT
}
