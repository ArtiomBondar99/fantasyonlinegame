//Artiom Bondar:332692730
//Shahar Dahan: 207336355
package game.items;
import java.io.Serializable;

import game.map.Position;
/**
 * Represents a wall on the game board.
 * Walls block movement and cannot be interacted with.
 */
public class Wall extends GameItem implements Serializable{
	private static final long serialVersionUID = 1L;
    /**
     * Constructs a wall at the specified position.
     *
     * @param position The location of the wall on the board
     */
    public Wall(Position position) {
        super(position,true,"Wall",true, "W");
    }

}


