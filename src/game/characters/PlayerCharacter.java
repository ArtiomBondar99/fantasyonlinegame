//Artiom Bondar:332692730
//Shahar Dahan: 207336355
package game.characters;

import game.core.Inventory;
import game.gui.GameObserver;
import game.items.*;
import game.map.Position;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;
import game.network.NetworkIdentifiable;
import java.io.Serializable;

/**
 * Abstract class representing a player-controlled character.
 * It extends AbstractCharacter and adds name, inventory, and treasure point tracking.
 */
public abstract class PlayerCharacter extends AbstractCharacter implements NetworkIdentifiable, Serializable {
    private String name;
    private Inventory inventory;
    private int treasurePoints;
    private final List<GameObserver> observers = new CopyOnWriteArrayList<>();
    private static final long serialVersionUID = 1L;
    private int networkId = -1;
    
    /**
     * Constructs a new PlayerCharacter with a given name and random starting position on the board.
     */
    public PlayerCharacter(String name) {
        super(new Position(new Random().nextInt(10),new Random().nextInt(10)));
        this.name = name;
        this.inventory = new Inventory();
        this.treasurePoints = 0;
        this.setVisible(true);
    }


    	public String getClassSimpleName()
    	{
    		return getClass().getSimpleName();
    	}

    	@Override
    	public int getNetworkId() {
    	    return networkId;
    	}

    	@Override
    	public void setNetworkId(int id) {
    	    this.networkId = id;
    	}

    	public boolean moveToPosition(Position newPos) {
    	    // In multiplayer, just update position locally
    	    setPosition(newPos);
    	    notifyObservers();
    	    return true;
    	}



    /**
     * Handles all interactions between the player and entities located in the given position.
     * Interactions can be with Enemies, Potions, or Treasures.
     * If the player dies during combat, further interactions are skipped.
     */
    	public void handleInteractions(Position pos) {
    	    // In multiplayer, interactions are handled by server
    	    // This method can be empty or removed
    	}

    /**
     * Returns the name of the player.
     */
    public String getName() {return name;}


    public Inventory getInventory() {return inventory;}
    
    /**
     * Adds a GameItem to the player's inventory.
     * @param item the item to add
     * @return true (always succeeds for now)
     */
    public boolean addToInventory(GameItem item){
        inventory.addItem(item);
        return true;
    }

    /**
     * Uses the first regular Potion in the inventory (if any).
     * Removes it after use.
     * @return true if a potion was used, false otherwise
     */
    public boolean usePotion() {
        if(getHealth() == 100)
            return false;
        for (GameItem item : inventory.getItems()) {
            if (item instanceof Potion && !(item instanceof PowerPotion)) {
                ((Potion)item).interact(this);
                inventory.removeItem(item);
                return true;
            }
        }
        return false;
    }

    /**
     * Uses the first PowerPotion in the inventory (if any).
     * Removes it after use.
     * @return true if a power potion was used, false otherwise
     */
    public boolean usePowerPotion(){
        for (GameItem item : inventory.getItems()) {
            if (item instanceof PowerPotion) {
                ((PowerPotion)item).interact(this);
                inventory.removeItem(item);
                return true;
            }
        }
        return false;
    }

    /**
     * Adds treasure points to the player.
     * @param amount the number of points to add
     * @return true (always succeeds)
     */
    public boolean updateTreasurePoint(int amount){
        this.treasurePoints += amount;
        return true;
    }

    /**
     * Gets the total number of treasure points the player has earned.
     */
    public int getTreasurePoints(){return treasurePoints;}

    public String getDisplaySymbol() {
        return name.substring(0, 1).toUpperCase();
    }

    public void addObserver(GameObserver observer) {
        observers.add(observer);
    }
    
    public void removeObserver(GameObserver observer){observers.remove(observer);}

    
    public void notifyObservers() {
        for (GameObserver observer : observers) {
            observer.onPlayerMoved(getPosition());
        }
    }

    
    public void clearObservers() {
        observers.clear();
    }

    
    public int getPowerPotionCount() {
        int counter = 0;
        for (GameItem i :inventory.getItems())
        {
            if(i instanceof PowerPotion)
                counter++;
        }
        return counter;
    }

    
    public int getLifePotionCount() {
        int counter = 0;
        for (GameItem i :inventory.getItems())
        {
            if(i instanceof Potion && !(i instanceof PowerPotion))
                counter++;
        }
        return counter;
    }


    
    public String getImagePath() {
        return "/game/resources/images/" + this.getClass().getSimpleName() + ".png";
    }




}
