package game.map;

import java.io.Serializable;

/**
 * Modifications needed for Position class.
 * Make your existing Position class implement Serializable.
 */
class PositionSerializable extends Position implements Serializable {
    private static final long serialVersionUID = 1L;
    
    public PositionSerializable(int row, int col) {
        super(row, col);
    }
}