package interpreter;

import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.JTextPane;

import org.json.JSONArray;
import org.json.JSONObject;

import com.formdev.flatlaf.FlatLightLaf;

import cartago.INTERNAL_OPERATION;
import cartago.OPERATION;
import cartago.tools.GUIArtifact;
import jason.asSyntax.ASSyntax;
import jason.asSyntax.ListTerm;
import jason.asSyntax.Literal;

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

    @INTERNAL_OPERATION
    private void notify_new_msg( List<Literal> recipients, String msg ) {
        try {
            ListTerm recipients_list = ASSyntax.parseList(recipients.toString());
            signal( "user_msg", recipients_list, msg );
        } catch ( Exception e ){
            e.printStackTrace();
        }
    }

    class ChatView extends JFrame {
        private ChatArtifact art;
        private JTextPane chatPane;
        private JTextField inputField;
        private JButton sendButton;

        public ChatView( ChatArtifact art ) {
            FlatLightLaf.setup();
            this.art = art;

            setTitle("..::ChatBDI::..");
            setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            setSize(400, 500);
            setLayout(new BorderLayout());

            chatPane = new JTextPane();
            chatPane.setContentType("text/html");
            chatPane.setEditable(false);
            JScrollPane scrollPane = new JScrollPane( chatPane );

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
                List<Literal> recipients = appendMessage( "user", message );
                inputField.setText("");
                try {
                    art.beginExtSession();
                    if ( recipients.size() > 0 )
                        art.notify_new_msg( recipients, message );
                    else
                        art.notify_new_msg( message );
                } finally {
                    art.endExtSession();
                }
                log("Sent " + message);
            }
        }

        public List<Literal> appendMessage( String sender, String msg ) {
            JSONObject mention_json = highlight_mentions( msg );
            String msg_with_hm = mention_json.getString( "display" );
            JSONArray recipients = mention_json.getJSONArray( "recipients" );
            List<Literal> recipients_l = new ArrayList<>();
            for ( Object recipient : recipients ) {
                recipients_l.add( ASSyntax.createLiteral( (String) recipient ) );
            }
            String msg_class = sender.equals("user") ? "sent" : "received";

            String currentContent = chatPane.getText();
            int bodyStart = currentContent.indexOf("<body>") + 6;
            int bodyEnd = currentContent.lastIndexOf("</body>");
            String bodyContent = currentContent.substring(bodyStart, bodyEnd);
            String headerContent = """
                    <html>
                    <head>
                        <style>

                            body {
                                font-family: Roboto, sans-serif;
                                font-size: 12px;
                            }
                            span {
                                background: #F4A261;
                                color: #fff;
                                padding: 5px 10px;
                                margin: 0 5px;
                            }
                        
                            .sent {
                                text-align: right;
                                background: #2c6e49;
                                padding: 10px;
                                margin: 5px;
                            }

                            .received {
                                text-align: left;
                                background: #05668d;
                                padding: 10px;
                                margin: 5px;
                            }

                            .sender {
                                font-weight: bold;
                                font-size: 10px;
                                color: white;
                            }
                        
                            .content {
                                font-weight: bold;
                                color: white;
                            }

                        </style>
                    </head>
                    <body>
                    """;
            if ( !bodyContent.contains( "div" ) ){
                bodyContent = "";
            }
            String sender_div = "<div class='sender'> " + sender + "</div>";
            String content_div = "<div class='content'>" + msg_with_hm + "</div>";
            String msg_div = "<div class='" + msg_class + "'>" + sender_div + content_div + "</div>";
            String updatedContent = bodyContent + msg_div;

            chatPane.setText(headerContent + updatedContent + "</div></body></html>");

            return recipients_l;
        }

        private JSONObject highlight_mentions( String msg ) {
            Pattern pattern = Pattern.compile("@\\w+");
            Matcher matcher = pattern.matcher( msg );
            StringBuffer sb = new StringBuffer();
            List<String> mentions = new ArrayList<>();

            while ( matcher.find() ){
                String mention = matcher.group();
                mentions.add(mention.substring(1));
                matcher.appendReplacement( sb, "<span>" + mention + "</span>");
            }
            matcher.appendTail( sb );
            JSONObject json = new JSONObject();
            json.put( "display", sb.toString() );
            json.put( "recipients", mentions );
            return json;
        }
    }
}