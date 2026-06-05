package kurs;

import javax.swing.*;

public class Main {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("ТАНЧИКИ");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

            GamePanel gamePanel = new GamePanel();
            frame.setContentPane(gamePanel);
            frame.pack();
            frame.setResizable(false);
            frame.setSize(GamePanel.WIDTH, GamePanel.HEIGHT);
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
            gamePanel.requestFocusInWindow();

            // Подключаемся к серверу (сервер должен быть запущен заранее)
            gamePanel.connectToServer("localhost", 5000, "Player1");
        });
    }
}