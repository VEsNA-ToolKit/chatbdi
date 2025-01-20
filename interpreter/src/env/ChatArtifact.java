package env;

import cartago.OPERATION;
import cartago.tools.GUIArtifact;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class ChatArtifact extends GUIArtifact {

    ChatView view;

    @Override
    public void init() {
        view = new ChatView();
        view.setVisible(true);
    }

    @OPERATION
    public void msg(String message) {
        view.appendMessage("[AGENT] " + message);
    }

    class ChatView extends JFrame {
        private JTextArea chatArea;
        private JTextField inputField;
        private JButton sendButton;

        public ChatView() {
            setTitle("SpeakAgent");
            setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            setSize(400, 500);
            setLayout(new BorderLayout());

            chatArea = new JTextArea();
            chatArea.setEditable(false);
            chatArea.setLineWrap(true);
            chatArea.setWrapStyleWord(true);
            JScrollPane scrollPane = new JScrollPane(chatArea);

            inputField = new JTextField();

            sendButton = new JButton("Send");

            JPanel inputPanel = new JPanel(new BorderLayout());
            inputPanel.add(inputField, BorderLayout.CENTER);
            inputPanel.add(sendButton, BorderLayout.EAST);

            add(scrollPane, BorderLayout.CENTER);
            add(inputPanel, BorderLayout.SOUTH);

            sendButton.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    sendMessage();
                }
            });

            inputField.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    sendMessage();
                }
            });

            setVisible(true);
        }

        private void sendMessage() {
            String message = inputField.getText();
            if (!message.trim().isEmpty()) {
                appendMessage("[USER] " + message);
                inputField.setText("");
            }
        }

        public void appendMessage(String message) {
            chatArea.append(message + "\n");
        }

    }
}