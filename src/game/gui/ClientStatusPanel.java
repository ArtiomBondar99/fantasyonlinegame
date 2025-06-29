
package game.gui;

import java.awt.*;
import java.net.URL;
import javax.swing.*;
import game.network.PlayerState;

/**
 * Enhanced client-side status panel showing player information and inventory.
 * 
 * @author Artiom Bondar:332692730
 * @author Shahar Dahan:207336355
 */
class ClientStatusPanel extends JPanel {
    private final JLabel nameLabel;
    private final JLabel classLabel;
    private final JLabel powerLabel;
    private final JProgressBar healthBar;
    
    // Inventory labels
    private final JLabel lifePotionLabel;
    private final JLabel powerPotionLabel;
    private final JLabel treasureLabel;
    
    public ClientStatusPanel() {
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createTitledBorder("Player Status"));
        setPreferredSize(new Dimension(200, 250));
        
        // Top panel for inventory items
        JPanel inventoryPanel = new JPanel(new GridLayout(3, 1));
        
        // Life Potion row
        JPanel lifeRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
        lifeRow.add(new JLabel("Life Potion:       "));
        ImageIcon lifePotionIcon = loadImageIcon("/game/resources/images/Life_potion.png");
        if (lifePotionIcon != null) {
            lifeRow.add(new JLabel(lifePotionIcon));
        }
        lifePotionLabel = new JLabel("0");
        lifeRow.add(lifePotionLabel);
        inventoryPanel.add(lifeRow);
        
        // Power Potion row
        JPanel powerRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
        powerRow.add(new JLabel("Power Potion:      "));
        ImageIcon powerPotionIcon = loadImageIcon("/game/resources/images/Power_potion.png");
        if (powerPotionIcon != null) {
            powerRow.add(new JLabel(powerPotionIcon));
        }
        powerPotionLabel = new JLabel("0");
        powerRow.add(powerPotionLabel);
        inventoryPanel.add(powerRow);
        
        // Treasure row
        JPanel treasureRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
        treasureRow.add(new JLabel("Treasure Points: "));
        ImageIcon treasureIcon = loadImageIcon("/game/resources/images/Treasure.png");
        if (treasureIcon != null) {
            treasureRow.add(new JLabel(treasureIcon));
        }
        treasureLabel = new JLabel("0");
        treasureRow.add(treasureLabel);
        inventoryPanel.add(treasureRow);
        
        // Main status panel
        JPanel statusPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(2, 5, 2, 5);
        
        // Name
        gbc.gridx = 0; gbc.gridy = 0;
        statusPanel.add(new JLabel("Name:"), gbc);
        gbc.gridx = 1;
        nameLabel = new JLabel("---");
        statusPanel.add(nameLabel, gbc);
        
        // Class
        gbc.gridx = 0; gbc.gridy = 1;
        statusPanel.add(new JLabel("Class:"), gbc);
        gbc.gridx = 1;
        classLabel = new JLabel("---");
        statusPanel.add(classLabel, gbc);
        
        // Health bar (no label)
        gbc.gridx = 0; gbc.gridy = 2;
        gbc.gridwidth = 2;
        healthBar = new JProgressBar(0, 100);
        healthBar.setStringPainted(true);
        healthBar.setForeground(Color.GREEN);
        statusPanel.add(healthBar, gbc);
        
        // Power
        gbc.gridx = 0; gbc.gridy = 3;
        gbc.gridwidth = 1;
        statusPanel.add(new JLabel("Power:"), gbc);
        gbc.gridx = 1;
        powerLabel = new JLabel("---");
        statusPanel.add(powerLabel, gbc);
        
        // Add panels to main panel
        add(inventoryPanel, BorderLayout.NORTH);
        add(statusPanel, BorderLayout.CENTER);
    }
    
    private ImageIcon loadImageIcon(String path) {
        URL url = getClass().getResource(path);
        if (url != null) {
            ImageIcon icon = new ImageIcon(url);
            Image scaledImage = icon.getImage().getScaledInstance(32, 32, Image.SCALE_SMOOTH);
            return new ImageIcon(scaledImage);
        } else {
            System.err.println("Image not found: " + path);
            return null;
        }
    }
    
    /**
     * Updates the status display.
     */
    public void updateStatus(PlayerState player) {
        if (player != null) {
            nameLabel.setText(player.getName());
            classLabel.setText(player.getCharacterClass());
            powerLabel.setText(String.valueOf(player.getPower()));
            
            healthBar.setValue(player.getHealth());
            healthBar.setString(player.getHealth() + " / 100");
            
            // Update inventory counts
            lifePotionLabel.setText(String.valueOf(player.getLifePotionCount()));
            powerPotionLabel.setText(String.valueOf(player.getPowerPotionCount()));
            treasureLabel.setText(String.valueOf(player.getTreasurePoints()));
            
            // Color health bar based on health level
            if (player.getHealth() > 70) {
                healthBar.setForeground(Color.GREEN);
            } else if (player.getHealth() > 30) {
                healthBar.setForeground(Color.YELLOW);
            } else {
                healthBar.setForeground(Color.RED);
            }
        }
    }
}

