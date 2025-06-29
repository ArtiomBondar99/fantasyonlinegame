package game.gui;

import game.client.ClientGameState;
import game.map.Position;
import game.network.EnemyState;
import game.network.ItemState;
import game.network.PlayerState;
import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.geom.GeneralPath;

public class PopupPanel {
    static Integer enemyHealth = null;

    public static void showPopup(String title, String message) {
        JTextArea textArea = new JTextArea(message);
        textArea.setWrapStyleWord(true);
        textArea.setLineWrap(true);
        textArea.setEditable(false);
        textArea.setFocusable(false);
        textArea.setBackground(UIManager.getColor("Label.background"));
        textArea.setFont(UIManager.getFont("Label.font"));
        
        JScrollPane scrollPane = new JScrollPane(textArea);
        scrollPane.setPreferredSize(new Dimension(100, 100));
        JOptionPane.showMessageDialog(null, scrollPane, title, JOptionPane.INFORMATION_MESSAGE);
    }

    /**
     * Quick popup for showing entity information at a position.
     * Adapted for multiplayer - uses ClientGameState instead of GameWorld.
     */
    public static void quickPopup(ClientGameState gameState, Position pos, ClientGameFrame frame) {
        String message = "Nothing here.";
        Integer entityHealth = null;
        String entityType = null;
        
        // Check for players at this position
        for (PlayerState player : gameState.getAllPlayers()) {
            if (player.getPosition().equals(pos)) {
                entityType = "Player";
                entityHealth = player.getHealth();
                message = "Player: " + player.getName() + "\n" + 
                         player.getCharacterClass() + "\n" +
                         "HP: " + entityHealth + "/100";
                break;
            }
        }
        
        // Check for enemies at this position
        if (entityType == null) {
            for (EnemyState enemy : gameState.getAllEnemies()) {
                if (enemy.getPosition().equals(pos)) {
                    entityType = "Enemy";
                    entityHealth = enemy.getHealth();
                    message = "Enemy: " + enemy.getType() + "\n" +
                             "HP: " + entityHealth + "/50";
                    break;
                }
            }
        }
        
        // Check for items at this position
        if (entityType == null) {
            java.util.List<ItemState> items = gameState.getItemsAt(pos);
            if (!items.isEmpty()) {
                ItemState item = items.get(0);
                switch (item.getType()) {
                    case "PowerPotion":
                        message = "You found a power potion!";
                        break;
                    case "Potion":
                        message = "You found a Life potion!";
                        break;
                    case "Treasure":
                        message = "You found a treasure!";
                        break;
                    case "Wall":
                        message = "It is a wall";
                        break;
                }
            }
        }

        final String popupText = message;
        final Integer health = entityHealth;
        final boolean isEnemy = "Enemy".equals(entityType);

        SwingUtilities.invokeLater(() -> {
            JPopupMenu popupMenu = new JPopupMenu();

            // Custom panel for content
            JPanel content = new JPanel();
            content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
            content.setBackground(new Color(40, 40, 40));
            content.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

            // Text area
            JTextArea textArea = new JTextArea(popupText);
            textArea.setEditable(false);
            textArea.setOpaque(false);
            textArea.setForeground(Color.WHITE);
            textArea.setFont(new Font("Arial", Font.PLAIN, 14));
            content.add(textArea);

            // Only add health bar if there's a health value
            if (health != null) {
                JProgressBar healthBar = new JProgressBar(0, isEnemy ? 50 : 100);
                healthBar.setValue(health);
                healthBar.setStringPainted(false);
                healthBar.setPreferredSize(new Dimension(200, 15));
                healthBar.setBorderPainted(false);

                float ratio = isEnemy ? health / 50f : health / 100f;
                if (ratio <= 0.3f)
                    healthBar.setForeground(Color.RED);
                else if (ratio <= 0.7f)
                    healthBar.setForeground(new Color(176, 121, 54)); // brown/orange
                else
                    healthBar.setForeground(new Color(63, 119, 76)); // green

                content.add(Box.createVerticalStrut(5));
                content.add(healthBar);
            }

            popupMenu.setLayout(new BorderLayout());
            popupMenu.add(content, BorderLayout.CENTER);

            // Auto-hide on mouse exit
            Toolkit.getDefaultToolkit().addAWTEventListener(event -> {
                if (!(event instanceof MouseEvent)) return;
                MouseEvent me = (MouseEvent) event;
                if (popupMenu.isVisible()) {
                    Point screenPoint = me.getLocationOnScreen();
                    SwingUtilities.convertPointFromScreen(screenPoint, popupMenu);
                    if (!popupMenu.contains(screenPoint)) {
                        popupMenu.setVisible(false);
                    }
                }
            }, AWTEvent.MOUSE_MOTION_EVENT_MASK);

            // Show popup at mouse position
            PointerInfo pointerInfo = MouseInfo.getPointerInfo();
            Point mouseLocation = pointerInfo.getLocation();

            Component focusComponent = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
            if (focusComponent != null) {
                SwingUtilities.convertPointFromScreen(mouseLocation, focusComponent);
                popupMenu.show(focusComponent, mouseLocation.x, mouseLocation.y);
            }
        });
    }
    
    public static void damagePopup(Position pos, int damage, ClientGameFrame frame) {

        //  ❶  Always touch Swing from the EDT
        SwingUtilities.invokeLater(() -> {

            /* ----------------------------------------------------------
             * 1.  Resolve the map-tile’s *screen* location
             * ---------------------------------------------------------- */
        		ClientMapPanel mapPanel = frame.getMapPanel();
            JComponent tile = mapPanel.getCellButton(pos.getRow(), pos.getCol());
            if (tile == null) return;  // safety

            Point tileScreen = tile.getLocationOnScreen();

            /* ----------------------------------------------------------
             * 2.  Build a tiny red/white label inside a border-less window
             * ---------------------------------------------------------- */
            JWindow popup = new JWindow();        // lightweight, no focus-steal
            JLabel lbl   = new JLabel(String.valueOf(damage));
            lbl.setOpaque(true);
            lbl.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));
            lbl.setForeground(Color.WHITE);       // text
            lbl.setBackground(new Color(200, 0, 0)); // red (slightly darker than #FF0000)
            lbl.setFont(lbl.getFont().deriveFont(Font.BOLD, 14f));

            popup.getContentPane().add(lbl);
            popup.pack();

            /* ----------------------------------------------------------
             * 3.  Drop it *slightly above* the tile centre
             * ---------------------------------------------------------- */
            int x = tileScreen.x + 3*(tile.getWidth() - popup.getWidth()) / 4;
            int y = tileScreen.y - (tile.getHeight() - popup.getHeight()) / 6;     // slight gap
            popup.setLocation(x, y);
            popup.setAlwaysOnTop(true);   // stay above the game frame
            popup.setVisible(true);

            /* ----------------------------------------------------------
             * 4.  Auto-dispose after 600 ms
             * ---------------------------------------------------------- */
            new javax.swing.Timer(600, e -> {
                popup.setVisible(false);
                popup.dispose();
                ((Timer) e.getSource()).stop();
            }).start();
        });
    }
    
    
  

    public static void critDamagePopup(Position pos, int damage, ClientGameFrame frame) {

        SwingUtilities.invokeLater(() -> {

            /* ▸ 1.  Locate the tile component on screen
             * ---------------------------------------------------------- */
        		ClientMapPanel mapPanel = frame.getMapPanel();
            JComponent tile = mapPanel.getCellButton(pos.getRow(), pos.getCol());
            if (tile == null) return;                          // safety
            Point tileScreen = tile.getLocationOnScreen();

            /* ▸ 2.  Star-burst geometry (sharp, compact)
             * ---------------------------------------------------------- */
            int tileSide = Math.min(tile.getWidth(), tile.getHeight()); // e.g. 32 px
            int R_OUT    = (int) (tileSide * 0.30);   // outer spike tip
            int R_IN     = R_OUT / 2;                 // inner valley
            int POINTS   = 24;                        // # of spikes

            GeneralPath star = new GeneralPath();
            for (int i = 0; i < POINTS; i++) {
                double angle  = i * 2 * Math.PI / POINTS;
                double radius = (i & 1) == 0 ? R_OUT : R_IN;
                double x = R_OUT + radius * Math.cos(angle);
                double y = R_OUT + radius * Math.sin(angle);
                if (i == 0) star.moveTo(x, y); else star.lineTo(x, y);
            }
            star.closePath();

            /* ▸ 3.  Create a frameless, fully transparent JWindow */
            JWindow popup = new JWindow();
            popup.setBackground(new Color(0, 0, 0, 0)); // total alpha
            popup.setShape(star);

            /* ▸ 4.  Damage label */
            JLabel lbl = new JLabel(String.valueOf(damage), SwingConstants.CENTER);
            lbl.setOpaque(false);
            lbl.setForeground(Color.WHITE);
            lbl.setFont(lbl.getFont().deriveFont(Font.BOLD, 14f));

            /* ▸ 5.  Paint star behind the label */
            JComponent content = new JComponent() {
                @Override protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                                        RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setColor(new Color(255, 80, 40)); // fiery orange-red
                    g2.fill(star);
                    g2.dispose();
                }
            };
            content.setLayout(new BorderLayout());
            content.add(lbl, BorderLayout.CENTER);

            popup.setContentPane(content);
            popup.setSize(2 * R_OUT, 2 * R_OUT);              // star bounds

            /* ▸ 6.  Centre above the tile and show */
            int x = tileScreen.x + 3*(tile.getWidth() - popup.getWidth()) / 4;
            int y = tileScreen.y - (tile.getHeight() - popup.getHeight()) / 4;     // slight gap
            popup.setLocation(x, y);
            popup.setAlwaysOnTop(true);
            popup.setVisible(true);

            /* ▸ 7.  Auto-hide after 0.6 s */
            new javax.swing.Timer(600, e -> {
                popup.setVisible(false);
                popup.dispose();
                ((Timer) e.getSource()).stop();
            }).start();
        });
    }

    
    public static void missPopup(Position pos, ClientGameFrame frame) {
        SwingUtilities.invokeLater(() -> {
        		ClientMapPanel mapPanel = frame.getMapPanel();
            JComponent tile = mapPanel.getCellButton(pos.getRow(), pos.getCol());
            if (tile == null) return;

            Point tileScreen = tile.getLocationOnScreen();

            JWindow popup = new JWindow();
            JLabel lbl = new JLabel("MISS");
            lbl.setOpaque(true);
            lbl.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));
            lbl.setForeground(Color.GREEN);
            lbl.setBackground(new Color(50, 50, 50));
            lbl.setFont(lbl.getFont().deriveFont(Font.BOLD, 12f));

            popup.getContentPane().add(lbl);
            popup.pack();

            int x = tileScreen.x + 3*(tile.getWidth() - popup.getWidth()) / 4;
            int y = tileScreen.y - (tile.getHeight() - popup.getHeight()) / 4;     // slight gap
            popup.setLocation(x, y);
            popup.setAlwaysOnTop(true);
            popup.setVisible(true);

            new javax.swing.Timer(600, e -> {
                popup.setVisible(false);
                popup.dispose();
                ((Timer) e.getSource()).stop();
            }).start();
        });
    }
        
}
