package env;

import com.formdev.flatlaf.FlatLightLaf;

import cartago.*;
import cartago.tools.GUIArtifact;
import jason.asSyntax.*;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextPane;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ChatArtifact extends GUIArtifact {

    ChatView view;

    @Override
    public void init() {
        view = new ChatView( this );
        view.setVisible(true);
    }

    @OPERATION
    public void msg(String sender, String message) {
        view.appendMessage(sender, message);
    }

    @INTERNAL_OPERATION
    private void notify_new_msg( String msg ){
        signal("user_msg", msg);
    }

    class ChatView extends JFrame {
        private ChatArtifact art;
        // private JTextArea chatArea;
        private JTextPane chatPane;
        private JTextField inputField;
        private JButton sendButton;

        public ChatView( ChatArtifact art ) {
            FlatLightLaf.setup();
            this.art = art;

            setTitle("SpeakAgent");
            setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            setSize(400, 500);
            setLayout(new BorderLayout());

            // chatArea = new JTextArea();
            // chatArea.setEditable(false);
            // chatArea.setLineWrap(true);
            // chatArea.setWrapStyleWord(true);
            chatPane = new JTextPane();
            chatPane.setContentType("text/html");
            chatPane.setEditable(false);
            JScrollPane scrollPane = new JScrollPane( chatPane );
            // JScrollPane scrollPane = new JScrollPane(chatArea);

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

        @INTERNAL_OPERATION
        private void sendMessage() {
            String message = inputField.getText();
            if ( !message.trim().isEmpty() ) {
                appendMessage( "interpreter", message );
                inputField.setText("");
            }
            try {
                art.beginExtSession();
                art.notify_new_msg( message );
            } finally {
                art.endExtSession();
            }
            log("Sent " + message);
        }

        public void appendMessage( String sender, String msg ) {
            String msg_with_hm = highlight_mentions( msg );
            String msg_class = sender.equals("interpreter") ? "sent" : "received";

            String currentContent = chatPane.getText();
            int bodyStart = currentContent.indexOf("<body>") + 6;
            int bodyEnd = currentContent.lastIndexOf("</body>");
            String bodyContent = currentContent.substring(bodyStart, bodyEnd);
            String headerContent = """
                    <html>
                    <head>
                    <style>
                    span {
                      background: #ee6c4d;
                      color: #fff;
                      padding: 5px 10px;
                      margin: 0 5px;
                    }
                    
                    .sent {
                        text-align: right;
                        background: #25a18e;
                        padding: 10px;
                        margin: 5px;
                    }

                    .received {
                        text-align: left;
                        background: #00a5cf;
                        padding: 10px;
                        margin: 5px;
                    }

                    .sender {
                        font-weight: bold;
                        font-size: 10px;
                    }
                    
                    .content {
                        font-weight: normal;
                    }

                    .chat {
                        font-family: Roboto, sans-serif;
                        font-size: 12px;
                        padding: 5px;
                    }
                    </style>
                    </head>
                    <body>
                    <div class="chat">
                    """;
            String sender_div = "<div class='sender'> " + sender + "</div>";
            String content_div = "<div class='content'>" + msg_with_hm + "</div>";
            String msg_div = "<div class='" + msg_class + "'>" + sender_div + content_div + "</div>";
            String updatedContent = bodyContent + msg_div;

            chatPane.setText(headerContent + updatedContent + "</div></body></html>");
        }

        private String highlight_mentions( String msg ) {
            Pattern pattern = Pattern.compile("@\\w+");
            Matcher matcher = pattern.matcher( msg );
            StringBuffer sb = new StringBuffer();

            while ( matcher.find() ){
                String mention = matcher.group();
                matcher.appendReplacement( sb, "<span>" + mention + "</span>");
            }
            matcher.appendTail( sb );
            return sb.toString();
        }

    }
}