package game;

import game.server.GameServer;
import game.client.GameClient;
import javax.swing.*;
import java.awt.*;
import java.io.FileWriter;
import java.io.IOException;

/**
 * Main launcher for the multiplayer fantasy game.
 * Allows choosing between server and client modes.
 * 
 * @author Artiom Bondar:332692730
 * @author Shahar Dahan:207336355
 */
public class MultiplayerLauncher {
    
    /**
     * Main entry point for the game.
     */
    public static void main(String[] args) {
        // Clear log file
        clearLogFile();
        
        // Check command line arguments
        if (args.length > 0) {
            if (args[0].equalsIgnoreCase("server")) {
                // Start server with optional port
                String[] serverArgs = new String[args.length - 1];
                System.arraycopy(args, 1, serverArgs, 0, serverArgs.length);
                GameServer.main(serverArgs);
                return;
            } else if (args[0].equalsIgnoreCase("client")) {
                // Start client directly
                GameClient.main(new String[0]);
                return;
            }
        }
        
        // Show launcher GUI
        SwingUtilities.invokeLater(() -> showLauncherDialog());
    }
    
    /**
     * Shows the launcher dialog for choosing server or client mode.
     */
    private static void showLauncherDialog() {
        JFrame frame = new JFrame("Fantasy Game Launcher");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLayout(new BorderLayout());
        
        // Title
        JLabel titleLabel = new JLabel("Multiplayer Fantasy Game", SwingConstants.CENTER);
        titleLabel.setFont(new Font("Arial", Font.BOLD, 24));
        titleLabel.setBorder(BorderFactory.createEmptyBorder(20, 0, 20, 0));
        frame.add(titleLabel, BorderLayout.NORTH);
        
        // Buttons panel
        JPanel buttonPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(10, 20, 10, 20);
        
        // Server button
        JButton serverButton = new JButton("Start Server");
        serverButton.setFont(new Font("Arial", Font.PLAIN, 18));
        serverButton.setPreferredSize(new Dimension(200, 60));
        serverButton.addActionListener(e -> {
            frame.dispose();
            startServer();
        });
        
        gbc.gridx = 0;
        gbc.gridy = 0;
        buttonPanel.add(serverButton, gbc);
        
        // Client button
        JButton clientButton = new JButton("Join Game");
        clientButton.setFont(new Font("Arial", Font.PLAIN, 18));
        clientButton.setPreferredSize(new Dimension(200, 60));
        clientButton.addActionListener(e -> {
            frame.dispose();
            startClient();
        });
        
        gbc.gridy = 1;
        buttonPanel.add(clientButton, gbc);
        
        // Instructions
        JTextArea instructions = new JTextArea(
            "Server Mode:\n" +
            "  - Hosts the game for other players to join\n" +
            "  - Must be started before clients can connect\n" +
            "  - Shows server console with game logs\n\n" +
            "Client Mode:\n" +
            "  - Connect to an existing game server\n" +
            "  - Play as a character in the game world\n" +
            "  - Chat with other players"
        );
        instructions.setEditable(false);
        instructions.setFont(new Font("Arial", Font.PLAIN, 14));
        instructions.setBackground(frame.getBackground());
        instructions.setBorder(BorderFactory.createEmptyBorder(20, 40, 20, 40));
        
        frame.add(buttonPanel, BorderLayout.CENTER);
        frame.add(instructions, BorderLayout.SOUTH);
        
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }
    
    /**
     * Starts the game server.
     */
    private static void startServer() {
        // Show server configuration dialog
        JPanel panel = new JPanel(new GridLayout(2, 2, 5, 5));
        
        JTextField portField = new JTextField("62222");
        
        panel.add(new JLabel("Server Port:"));
        panel.add(portField);
        
        int result = JOptionPane.showConfirmDialog(null, panel, 
            "Server Configuration", JOptionPane.OK_CANCEL_OPTION);
            
        if (result == JOptionPane.OK_OPTION) {
            try {
                int port = Integer.parseInt(portField.getText().trim());
                
                // Show server console
                ServerConsole console = new ServerConsole();
                console.setVisible(true);
                
                // Start server in new thread
                new Thread(() -> {
                    try {
                        console.appendMessage("Starting server on port " + port + "...");
                        GameServer server = new GameServer(port);
                        console.appendMessage("Server started successfully!");
                        console.appendMessage("Players can now connect to this server.");
                        console.setServer(server);
                        server.start();
                    } catch (Exception e) {
                        console.appendMessage("ERROR: " + e.getMessage());
                        e.printStackTrace();
                    }
                }).start();
                
            } catch (NumberFormatException e) {
                JOptionPane.showMessageDialog(null, 
                    "Invalid port number", 
                    "Error", 
                    JOptionPane.ERROR_MESSAGE);
                startServer(); // Try again
            }
        } else {
            // Return to launcher
            showLauncherDialog();
        }
    }
    
    /**
     * Starts the game client.
     */
    private static void startClient() {
        GameClient.main(new String[0]);
    }
    
    /**
     * Clears the log file.
     */
    private static void clearLogFile() {
        try (FileWriter writer = new FileWriter("game_log.txt", false)) {
            // Empty file
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

/**
 * Simple console window for the server.
 */
class ServerConsole extends JFrame {
    private final JTextArea consoleArea;
    private GameServer server;
    
    public ServerConsole() {
        setTitle("Game Server Console");
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        
        // Console area
        consoleArea = new JTextArea(20, 60);
        consoleArea.setEditable(false);
        consoleArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        consoleArea.setBackground(Color.BLACK);
        consoleArea.setForeground(Color.GREEN);
        
        JScrollPane scrollPane = new JScrollPane(consoleArea);
        add(scrollPane, BorderLayout.CENTER);
        
        // Control panel
        JPanel controlPanel = new JPanel();
        
        JButton stopButton = new JButton("Stop Server");
        stopButton.addActionListener(e -> stopServer());
        controlPanel.add(stopButton);
        
        add(controlPanel, BorderLayout.SOUTH);
        
        // Window close handler
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent e) {
                stopServer();
            }
        });
        
        pack();
        setLocationRelativeTo(null);
    }
    
    public void setServer(GameServer server) {
        this.server = server;
    }
    
    public void appendMessage(String message) {
        SwingUtilities.invokeLater(() -> {
            consoleArea.append("[" + new java.util.Date() + "] " + message + "\n");
            consoleArea.setCaretPosition(consoleArea.getDocument().getLength());
        });
    }
    
    private void stopServer() {
        int confirm = JOptionPane.showConfirmDialog(this,
            "Are you sure you want to stop the server?",
            "Confirm Stop",
            JOptionPane.YES_NO_OPTION);
            
        if (confirm == JOptionPane.YES_OPTION) {
            if (server != null) {
                appendMessage("Shutting down server...");
                server.shutdown();
            }
            dispose();
            System.exit(0);
        }
    }
}