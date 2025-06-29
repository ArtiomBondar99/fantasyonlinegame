package game.gui;

import game.client.*;
import game.network.*;
import game.map.Position;

import javax.swing.*;
import javax.swing.text.*;
import java.awt.*;
import java.awt.event.*;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Client-side game GUI with map display, status, controls, and chat.
 * 
 * @author Artiom Bondar:332692730
 * @author Shahar Dahan:207336355
 */
public class ClientGameFrame extends JFrame {
    private final GameClient client;
    private final String playerName;
    private boolean inCombat = false;
    
    // GUI Components
    private ClientMapPanel mapPanel;
    private ClientStatusPanel statusPanel;
    private JTextArea chatArea;
    private JTextField chatInput;
    private JPanel controlPanel;
    
    // Control buttons
    private JButton lifePotionButton;
    private JButton powerPotionButton;
    private JButton boostButton;
    private JButton shieldButton;
    private JButton regenButton;
    
    // Date formatter for chat timestamps
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss");
    
    /**
     * Creates the client game frame.
     */
    public ClientGameFrame(GameClient client, String playerName) {
        this.client = client;
        this.playerName = playerName;
        
        setTitle("Fantasy Game - " + playerName);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());
        
        initializeComponents();
        layoutComponents();
        setupKeyBindings();
        
        pack();
        setLocationRelativeTo(null);
        setResizable(false);
    }
    
    /**
     * Initializes all GUI components.
     */
    private void initializeComponents() {
        // Map panel
        mapPanel = new ClientMapPanel(this);
        mapPanel.setPreferredSize(new Dimension(640, 540));
        
        // Status panel
        statusPanel = new ClientStatusPanel();
        
        // Control panel with ability buttons
        controlPanel = createControlPanel();
        
        // Chat components
        chatArea = new JTextArea(10, 30);
        chatArea.setEditable(false);
        chatArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        chatArea.setLineWrap(true);
        chatArea.setWrapStyleWord(true);
        
        // Auto-scroll to bottom
        DefaultCaret caret = (DefaultCaret) chatArea.getCaret();
        caret.setUpdatePolicy(DefaultCaret.ALWAYS_UPDATE);
        
        chatInput = new JTextField();
        chatInput.addActionListener(e -> sendChatMessage());
    }
    
    public void setCombatState(boolean inCombat) {
        this.inCombat = inCombat;
        SwingUtilities.invokeLater(() -> {
            if (inCombat) {
                addChatMessage("System", "Combat started! Attack or flee by moving away.", true);
                // Optionally disable some UI elements during combat
            } else {
                addChatMessage("System", "Combat ended.", true);
                // Re-enable UI elements
            }
        });
    }
    
    /**
     * Creates the control panel with ability buttons.
     */
    private JPanel createControlPanel() {
        JPanel panel = new JPanel(new GridLayout(2, 3, 5, 5));
        panel.setBorder(BorderFactory.createTitledBorder("Actions"));
        
        // Potion buttons
        lifePotionButton = new JButton("Life Potion (L)");
        lifePotionButton.addActionListener(e -> client.requestUsePotion("LIFE"));
        
        powerPotionButton = new JButton("Power Potion (P)");
        powerPotionButton.addActionListener(e -> client.requestUsePotion("POWER"));
        
        // Ability buttons
        boostButton = new JButton("Boost Attack");
        boostButton.addActionListener(e -> {
            client.requestActivateAbility("BOOST");
            boostButton.setEnabled(false);
            // Re-enable after cooldown
            Timer timer = new Timer(180000, ev -> boostButton.setEnabled(true));
            timer.setRepeats(false);
            timer.start();
        });
        
        shieldButton = new JButton("Activate Shield");
        shieldButton.addActionListener(e -> {
            client.requestActivateAbility("SHIELD");
            shieldButton.setEnabled(false);
            // Re-enable after cooldown
            Timer timer = new Timer(180000, ev -> shieldButton.setEnabled(true));
            timer.setRepeats(false);
            timer.start();
        });
        
        regenButton = new JButton("Regeneration");
        regenButton.addActionListener(e -> {
            client.requestActivateAbility("REGEN");
            regenButton.setEnabled(false);
        });
        
        panel.add(lifePotionButton);
        panel.add(powerPotionButton);
        panel.add(new JLabel()); // Empty cell
        panel.add(boostButton);
        panel.add(shieldButton);
        panel.add(regenButton);
        
        return panel;
    }
    
    /**
     * Layouts all components in the frame.
     */
    private void layoutComponents() {
        // Main game panel (map + status)
        JPanel gamePanel = new JPanel(new BorderLayout());
        gamePanel.add(mapPanel, BorderLayout.CENTER);
        
        // Right panel (status + controls)
        JPanel rightPanel = new JPanel(new BorderLayout());
        rightPanel.add(statusPanel, BorderLayout.NORTH);
        rightPanel.add(controlPanel, BorderLayout.CENTER);
        gamePanel.add(rightPanel, BorderLayout.EAST);
        
        // Chat panel
        JPanel chatPanel = new JPanel(new BorderLayout());
        chatPanel.setBorder(BorderFactory.createTitledBorder("Chat"));
        chatPanel.add(new JScrollPane(chatArea), BorderLayout.CENTER);
        
        JPanel chatInputPanel = new JPanel(new BorderLayout());
        chatInputPanel.add(new JLabel("Message: "), BorderLayout.WEST);
        chatInputPanel.add(chatInput, BorderLayout.CENTER);
        JButton sendButton = new JButton("Send");
        sendButton.addActionListener(e -> sendChatMessage());
        chatInputPanel.add(sendButton, BorderLayout.EAST);
        chatPanel.add(chatInputPanel, BorderLayout.SOUTH);
        
        // Add to frame
        add(gamePanel, BorderLayout.CENTER);
        add(chatPanel, BorderLayout.SOUTH);
    }
    
    /**
     * Sets up keyboard shortcuts.
     */
    private void setupKeyBindings() {
        InputMap inputMap = getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap actionMap = getRootPane().getActionMap();
        
        // Movement keys
        inputMap.put(KeyStroke.getKeyStroke("UP"), "moveUp");
        inputMap.put(KeyStroke.getKeyStroke("DOWN"), "moveDown");
        inputMap.put(KeyStroke.getKeyStroke("LEFT"), "moveLeft");
        inputMap.put(KeyStroke.getKeyStroke("RIGHT"), "moveRight");
        
        // Potion keys
        inputMap.put(KeyStroke.getKeyStroke("L"), "lifePotion");
        inputMap.put(KeyStroke.getKeyStroke("P"), "powerPotion");
        
        // Chat focus
        inputMap.put(KeyStroke.getKeyStroke("ENTER"), "focusChat");
        inputMap.put(KeyStroke.getKeyStroke("ESCAPE"), "unfocusChat");
        
        // Action mappings
        actionMap.put("moveUp", new MoveAction(0, -1));
        actionMap.put("moveDown", new MoveAction(0, 1));
        actionMap.put("moveLeft", new MoveAction(-1, 0));
        actionMap.put("moveRight", new MoveAction(1, 0));
        
        actionMap.put("lifePotion", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                client.requestUsePotion("LIFE");
            }
        });
        
        actionMap.put("powerPotion", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                client.requestUsePotion("POWER");
            }
        });
        
        actionMap.put("focusChat", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                chatInput.requestFocus();
            }
        });
        
        actionMap.put("unfocusChat", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                mapPanel.requestFocus();
            }
        });
    }
    
    /**
     * Movement action for keyboard controls.
     */
    private class MoveAction extends AbstractAction {
        private final int dx, dy;
        
        MoveAction(int dx, int dy) {
            this.dx = dx;
            this.dy = dy;
        }
        
        @Override
        public void actionPerformed(ActionEvent e) {
            // Don't move if chat is focused
            if (chatInput.hasFocus()) return;
            
            PlayerState myPlayer = client.getGameState().getPlayer(client.getPlayerId());
            if (myPlayer != null) {
                Position currentPos = myPlayer.getPosition();
                Position newPos = new Position(
                    currentPos.getRow() + dy,
                    currentPos.getCol() + dx
                );
                client.requestMove(newPos);
            }
        }
    }
    
    /**
     * Sends the chat message.
     */
    private void sendChatMessage() {
        String message = chatInput.getText().trim();
        if (!message.isEmpty()) {
            client.sendChatMessage(message);
            chatInput.setText("");
        }
    }
    
    
    
    /**
     * Adds a chat message to the display.
     */
    public void addChatMessage(String sender, String message, boolean isSystem) {
        SwingUtilities.invokeLater(() -> {
            String timestamp = timeFormat.format(new Date());
            String formattedMessage;
            
            if (isSystem) {
                formattedMessage = String.format("[%s] ** %s **\n", timestamp, message);
            } else {
                formattedMessage = String.format("[%s] %s: %s\n", timestamp, sender, message);
            }
            
            chatArea.append(formattedMessage);
            
            // Limit chat history
            Document doc = chatArea.getDocument();
            if (doc.getLength() > 10000) {
                try {
                    doc.remove(0, 1000);
                } catch (BadLocationException e) {
                    // Ignore
                }
            }
        });
    }
    
    /**
     * Updates the game state display.
     */
    public void updateGameState(ClientGameState gameState) {
        mapPanel.updateGameState(gameState);
    }
    
    /**
     * Updates player status display.
     */
    public void updatePlayerStatus(PlayerState player) {
        statusPanel.updateStatus(player);
    }
    
    /**
     * Handles cell click on the map.
     */
    public void handleCellClick(int row, int col) {
        PlayerState myPlayer = client.getGameState().getPlayer(client.getPlayerId());
        if (myPlayer == null) return;
        
        Position clickedPos = new Position(row, col);
        Position playerPos = myPlayer.getPosition();
        
        // Don't process clicks on player's own position
        if (clickedPos.equals(playerPos)) {
            return;
        }
        
        // Check what's at the clicked position
        boolean hasEnemy = false;
        boolean hasWall = false;
        boolean hasOtherPlayer = false;
        
        // Check for enemies
        for (EnemyState enemy : client.getGameState().getAllEnemies()) {
            if (enemy.getPosition().equals(clickedPos)) {
                hasEnemy = true;
                break;
            }
        }
        
        // Check for other players
        for (PlayerState player : client.getGameState().getAllPlayers()) {
            if (player.getPlayerId() != client.getPlayerId() && 
                player.getPosition().equals(clickedPos)) {
                hasOtherPlayer = true;
                break;
            }
        }
        
        // Check for walls
        for (ItemState item : client.getGameState().getItemsAt(clickedPos)) {
            if ("Wall".equals(item.getType())) {
                hasWall = true;
                break;
            }
        }
        
        int distance = playerPos.distanceTo(clickedPos);
        
        // Handle based on what's at the position
        if (hasEnemy) {
            // Check if in attack range
            String myClass = myPlayer.getCharacterClass();
            int attackRange = (myClass.equals("Archer") || myClass.equals("Mage")) ? 2 : 1;
            
            if (distance <= attackRange) {
                // Send attack request
                GameMessage attackMsg = new GameMessage(MessageType.ATTACK_REQUEST);
                attackMsg.setPosition(clickedPos);
                client.sendMessage(attackMsg);
            } else {
                // Enemy out of range - show message
                addChatMessage("System", "Enemy is out of range!", true);
            }
        } else if (hasWall || hasOtherPlayer) {
            // Can't move to these positions
            addChatMessage("System", "Cannot move there!", true);
        } else if (distance == 1) {
            // Empty adjacent cell - request movement
            client.requestMove(clickedPos);
        } else {
            // Too far to move
            addChatMessage("System", "Can only move to adjacent cells!", true);
        }
    }
    
    public GameClient getClient() {
        return client;
    }

	public ClientMapPanel getMapPanel() {
		return mapPanel;
	}

	
}