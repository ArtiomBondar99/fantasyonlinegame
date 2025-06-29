package game.gui;


import game.client.ClientGameState;
import game.network.PlayerState;
import game.network.EnemyState;
import game.network.ItemState;
import game.map.Position;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.net.URL;

/**
 * Client-side map panel that displays the game world.
 * 
 * @author Artiom Bondar:332692730
 * @author Shahar Dahan:207336355
 */
class ClientMapPanel extends JPanel {
    private static final int BOARD_SIZE = 15;
    private static final int CELL_SIZE = 40;
    
    private final ClientGameFrame frame;
    private final JButton[][] cells;
    private ClientGameState gameState;
    
    public ClientMapPanel(ClientGameFrame frame) {
        this.frame = frame;
        this.cells = new JButton[BOARD_SIZE][BOARD_SIZE];
        
        setLayout(new GridLayout(BOARD_SIZE, BOARD_SIZE));
        setPreferredSize(new Dimension(BOARD_SIZE * CELL_SIZE, BOARD_SIZE * CELL_SIZE));
        
        initializeCells();
    }
    
    /**
     * Initializes all map cells.
     */
    private void initializeCells() {
        for (int row = 0; row < BOARD_SIZE; row++) {
            for (int col = 0; col < BOARD_SIZE; col++) {
                final int r = row;
                final int c = col;
                
                JButton cell = new JButton();
                cell.setPreferredSize(new Dimension(CELL_SIZE, CELL_SIZE));
                cell.setFont(new Font("Monospaced", Font.BOLD, 16));
                cell.setMargin(new Insets(0, 0, 0, 0));
                
                // Click handler
                cell.addActionListener(e -> frame.handleCellClick(r, c));
                
                // Add right-click handler for quick popup
                cell.addMouseListener(new MouseAdapter() {
                    @Override
                    public void mousePressed(MouseEvent e) {
                        if (SwingUtilities.isRightMouseButton(e)) {
                            Position clickedPos = new Position(r, c);
                            PlayerState myPlayer = gameState.getPlayer(frame.getClient().getPlayerId());
                            if (myPlayer != null) {
                                // Only show popup if within 2 tiles (visibility range)
                                if (myPlayer.getPosition().distanceTo(clickedPos) <= 2) {
                                    PopupPanel.quickPopup(gameState, clickedPos, frame);
                                }
                            }
                        }
                    }
                    
                    @Override
                    public void mouseEntered(MouseEvent e) {
                        if (gameState != null) {
                            showCellInfo(r, c);
                        }
                    }
                });
                
                cells[row][col] = cell;
                add(cell);
            }
        }
    }
    
    /**
     * Updates the map display based on game state.
     */
    public void updateGameState(ClientGameState state) {
        this.gameState = state;
        
        SwingUtilities.invokeLater(() -> {
            for (int row = 0; row < BOARD_SIZE; row++) {
                for (int col = 0; col < BOARD_SIZE; col++) {
                    updateCell(row, col);
                }
            }
        });
    }
    
    /**
     * Updates a single cell display.
     */
    private void updateCell(int row, int col) {
        JButton cell = cells[row][col];
        Position pos = new Position(row, col);
        
        // Clear cell
        cell.setText("");
        cell.setBackground(null);
        cell.setForeground(Color.BLACK);
        cell.setIcon(null);
        
        if (gameState == null) return;
        
        int myPlayerId = frame.getClient().getPlayerId();
        
        // Check for entities at this position
        String symbol = gameState.getDisplaySymbolAt(pos, myPlayerId);
        
        if (!symbol.isEmpty()) {
        		if (symbol.startsWith("m") || symbol.startsWith("o")) {
                ImageIcon playerIcon = loadImageIcon(getImagePath(symbol.substring(1)));
                if (playerIcon != null) cell.setIcon(playerIcon);
                if(symbol.startsWith("m"))
                		cell.setBackground(Color.GREEN);
                else
                		cell.setBackground(Color.CYAN);
                return;
            }
        	
        		ImageIcon icon = loadImageIcon(getImagePath(symbol));
            if (icon != null) cell.setIcon(icon);
            // Check if it's an enemy
            for (EnemyState enemy : gameState.getAllEnemies()) {
                if (enemy.getPosition().equals(pos)) {
                    // Enemy - red
                    cell.setBackground(Color.RED);
                    cell.setForeground(Color.WHITE);
                    return;
                }
            }
            
            // Must be an item
            if (symbol.equals("W")) {
                cell.setBackground(Color.GRAY);
            } else if (symbol.equals("P")) {
                cell.setBackground(Color.PINK);
            } else if (symbol.equals("T")) {
                cell.setBackground(Color.YELLOW);
            }
        }
    }
    
    public JButton getCellButton(int row, int col) {
        if (row >= 0 && row < BOARD_SIZE && col >= 0 && col < BOARD_SIZE) {
            return cells[row][col];
        }
        return null;
    }
    
    /**
     * Shows information about a cell on hover.
     */
    private void showCellInfo(int row, int col) {
        Position pos = new Position(row, col);
        StringBuilder info = new StringBuilder();
        
        // Check for players
        for (PlayerState player : gameState.getAllPlayers()) {
            if (player.getPosition().equals(pos)) {
                info.append(player.getName())
                    .append(" (")
                    .append(player.getCharacterClass())
                    .append(")\n");
                info.append("HP: ").append(player.getHealth()).append("\n");
                info.append("Power: ").append(player.getPower());
                cells[row][col].setToolTipText(info.toString());
                return;
            }
        }
        
        // Check for enemies
        for (EnemyState enemy : gameState.getAllEnemies()) {
            if (enemy.getPosition().equals(pos)) {
                info.append(enemy.getType()).append("\n");
                info.append("HP: ").append(enemy.getHealth());
                cells[row][col].setToolTipText(info.toString());
                return;
            }
        }
        
        // Check for items
        java.util.List<ItemState> items = gameState.getItemsAt(pos);
        if (!items.isEmpty()) {
            for (ItemState item : items) {
                info.append(item.getType()).append("\n");
            }
            cells[row][col].setToolTipText(info.toString());
            return;
        }
        
        cells[row][col].setToolTipText(null);
    }
    
    public static String getImagePath(String type) {
        String base = "/game/resources/images/";

        switch (type) {
            case "D":
                return base + "Dragon.png";
            case "G":
                return base + "Goblin.png";
            case "O":
                return base + "Orc.png";
            case "T":
                return base + "Treasure.png";
            case "W":
                return base + "Wall.png";
            case "L":
                return base + "Life_potion.png";
            case "P":
                return base + "Power_potion.png";
            default:
                return getActualPlayerImagePath(type);
        }
    }

    private static String getActualPlayerImagePath(String className) {
        String base = "/game/resources/images/";
        return base + className + ".png";
    }

    private ImageIcon loadImageIcon(String path) {
        URL url = getClass().getResource(path);
        if (url != null) {
            ImageIcon icon = new ImageIcon(url);
            Image scaledImage = icon.getImage().getScaledInstance(40, 40, Image.SCALE_SMOOTH);
            return new ImageIcon(scaledImage);
        } else {
            System.err.println("Image not found: " + path);
            return null;
        }
    }
    
    
}
