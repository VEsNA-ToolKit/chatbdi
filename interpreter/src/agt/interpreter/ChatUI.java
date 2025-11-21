package chatbdi;

import java.util.List;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import java.nio.file.Files;
import java.nio.file.Path;
import java.io.IOException;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.JTextPane;
import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import jason.infra.local.RunLocalMAS;

import com.github.rjeschke.txtmark.Processor;

public class ChatUI {

    private JFrame chatView;
    private JTextPane chatPane;
    private JTextField inputField;
    private JButton sendButton;

    public ChatUI() {

        chatView = new JFrame();

        chatView.setTitle( "..::ChatBDI::.." );
        chatView.setDefaultCloseOperation( JFrame.EXIT_ON_CLOSE );
        chatView.setSize( 400, 500 );
        chatView.setLayout( new BorderLayout() );

        chatPane = new JTextPane();
        chatPane.setContentType( "text/html" );
        chatPane.setEditable( false );

        JScrollPane scrollPane = new JScrollPane( chatPane );

        inputField = new JTextField();
        sendButton = new JButton( "Send" );

        JPanel inputPanel = new JPanel( new BorderLayout() );
        inputPanel.add( inputField, BorderLayout.CENTER );
        inputPanel.add( sendButton, BorderLayout.EAST );

        chatView.add( scrollPane, BorderLayout.CENTER );
        chatView.add( inputPanel, BorderLayout.SOUTH );

        sendButton.addActionListener( new ActionListener() {
            @Override
            public void actionPerformed( ActionEvent e ) {
                String msg = inputField.getText();
                Interpreter ag = (Interpreter) RunLocalMAS.getRunner().getAg( "user" ).getTS().getAgArch();
                try {
                    List<String> receivers = showMsg( "user", msg );
                    ag.nl2kqml( receivers, msg );
                } catch ( IOException ioe ) {
                    ag.logSevere( "Cannot display message because the header.html file cannot be opened." );
                    return;
                } catch ( Exception ex ) {
                    ex.printStackTrace();
                }
            }
        });

        inputField.addActionListener( new ActionListener() {
            @Override
            public void actionPerformed( ActionEvent e ) {
                String msg = inputField.getText();
                Interpreter ag = (Interpreter) RunLocalMAS.getRunner().getAg( "alice" ).getTS().getAgArch();
                try {
                    List<String> receivers = showMsg( "alice", msg );
                    ag.nl2kqml( receivers, msg );
                } catch ( IOException ioe ) {
                    ag.logSevere( "Cannot display message because the header.html file cannot be opened." );
                    return;
                } catch ( Exception ex ) {
                    ex.printStackTrace();
                }
            }
        });

        chatView.setVisible( true );
    }

    private class Message {
        private String content;
        private List<String> receivers;

        private Message( String content, List<String> receivers ) {
            this.content = content;
            this.receivers = receivers;
        }

        private String getContent() {
            return this.content;
        }

        private List<String> getReceivers() {
            return this.receivers;
        }
    }

    protected List<String> showMsg( String sender, String content ) throws IOException {
        //! This can be done better, if:
        //! - an agent is called user;
        //! - I am writing on the chat;
        //! - the agent user sends a message
        //! -> I will lose what I was writing.
        if ( sender.equals( "user" ) )
            inputField.setText( "" );
        Message msg = getMessage( content );
        System.out.println( "[LOG] " + msg.getContent() );

        String currentChat = chatPane.getText();
        int bodyStart = currentChat.indexOf( "<body>" ) + 6;
        int bodyEnd = currentChat.indexOf( "</body>" );
        String body = currentChat.substring( bodyStart, bodyEnd );
        String header = Files.readString( Path.of( "src/agt/interpreter/header.html" ) );
        if ( !body.contains( "div" ) )
            body = "";
        String senderDiv = "<div class='sender'> " + sender + "</div>";
        String contentDiv = "<div class='content' " + Processor.process( msg.getContent() ) + "</div>";
        String msgClass = sender.equals( "user" ) ? "sent" : "received";
        String msgDiv = "<div class='" + msgClass + "'>";
        msgDiv += senderDiv + contentDiv + "</div>";
        msgDiv += "</div></body></html>";
        String updatedBody = body + msgDiv;

        chatPane.setText( header + updatedBody );
        chatView.revalidate();
        chatView.repaint();

        return msg.getReceivers();
    }

    private Message getMessage( String msg ) {
        Pattern pattern = Pattern.compile( "@\\w+" );
        Matcher matcher = pattern.matcher( msg );
        StringBuffer sb = new StringBuffer();
        List<String> mentions = new ArrayList<>();

        while ( matcher.find() ) {
            String mention = matcher.group();
            mentions.add( mention.substring(1) );
            matcher.appendReplacement( sb, "<span>" + mention + "</span>" );
        }
        matcher.appendTail( sb );
        System.out.println( "[LOG] " + sb );
        Message message = new Message( sb.toString(), mentions );
        return message;
    }


}