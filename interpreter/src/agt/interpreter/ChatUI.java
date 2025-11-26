package chatbdi;

import java.util.List;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.concurrent.ExecutionException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.io.IOException;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.JTextPane;
import javax.swing.SwingWorker;
import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import jason.infra.local.RunLocalMAS;

import com.github.rjeschke.txtmark.Processor;

/**
 * The ChatUI class manages the io visible to the user, the chat window and the call of the necessary methods to translate
 * @author Andrea Gatti
 */
public class ChatUI {

    /**
     * The main window frame
     */
    private JFrame chatView;
    /**
     * The chat frame
     */
    private JTextPane chatPane;
    /**
     * The input field
     */
    private JTextField inputField;
    /**
     * The button right to the input field
     */
    private JButton sendButton;
    /**
     * The interpreter agent name
     */
    private String myName;

    /**
     * Initialize the Chat UI
     * @param myName the name of the interpreter agent
     */
    public ChatUI( String myName ) {

        // init the interpreter agent name
        this.myName = myName;

        // Create the frame window
        chatView = new JFrame();

        // Set appearance of the window
        chatView.setTitle( "..::ChatBDI::.." );
        chatView.setDefaultCloseOperation( JFrame.EXIT_ON_CLOSE );
        chatView.setSize( 400, 500 );
        chatView.setLayout( new BorderLayout() );

        // Create a text pane to show the conversation (not editable)
        chatPane = new JTextPane();
        chatPane.setContentType( "text/html" );
        chatPane.setEditable( false );

        // Makes the conversation scrollable
        JScrollPane scrollPane = new JScrollPane( chatPane );

        // Create the input field for sending messages
        JPanel inputPanel = new JPanel( new BorderLayout() );
        inputField = new JTextField();
        sendButton = new JButton( "Send" );
        inputPanel.add( inputField, BorderLayout.CENTER );
        inputPanel.add( sendButton, BorderLayout.EAST );

        // Add the scrollable chat and the input field to the main window
        chatView.add( scrollPane, BorderLayout.CENTER );
        chatView.add( inputPanel, BorderLayout.SOUTH );

        // Send the message when the send button is pressed
        sendButton.addActionListener( new ActionListener() {
            @Override
            public void actionPerformed( ActionEvent e ) {
                handleSendMsg();
            }
        });

        // Send the message when the Enter key is pressed from the keyboard
        inputField.addActionListener( new ActionListener() {
            @Override
            public void actionPerformed( ActionEvent e ) {
                handleSendMsg();
            }
        });

        // show the chat
        chatView.setVisible( true );
    }

    /**
     * Function called when a message is sent from the user
     * - takes the message inside the inputField;
     * - takes the receivers;
     * - shows the message on the chat;
     * - calls the nl2kqml function of the agent.
     */
    private void handleSendMsg( ) {
        // Get the text from the input field
        String msg = inputField.getText();
        // Get the agent casted to Interpreter
        Interpreter ag = (Interpreter) RunLocalMAS.getRunner().getAg( myName ).getTS().getAgArch();
        List<String> receivers;
        try {
            // show the message on the chat formatted with receivers highlighet (and take them)
            receivers = showMsg( myName, msg );
            // translate and send message to the receivers
            // ag.nl2kqml( receivers, msg );
        } catch ( IOException ioe ) {
            ag.logSevere("Cannot display message because the header.html file cannot be opened.");
            return;
        }
        // } catch ( Exception e ) {
        //     ag.logSevere( "Cannot translate or send the current message. Error: " + e.getStackTrace() );
        // }

        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() {
                try {
                ag.nl2kqml( receivers, msg );
                } catch( IOException ioe ) {
                    ag.logSevere("Cannot display message because the header.html file cannot be opened.");
                    return null;
                } catch ( Exception e ) {
                    ag.logSevere( "Cannot translate or send the current message. Error: " + e.getStackTrace() );
                }
                return null;
            }

            @Override
            public void done() {
                try {
                    get();
                } catch ( InterruptedException e ) {
                    ag.logSevere( e.getStackTrace().toString() );
                } catch ( ExecutionException e ) {
                    ag.logSevere( e.getStackTrace().toString() );
                } catch ( Exception e ) {
                    ag.logSevere( e.getStackTrace().toString() );
                }
            }
        }.execute();

    }

    /**
     * Class Message is a simple object to store:
     * - receivers of a message (if any);
     * - content of the message.
     */
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

    /**
     * The function showMsg takes a message with a sender and a content; the content is html highlighted and receivers are returned.
     * @param sender The sender of the message (it is the name of one of the agents)
     * @param content The content of the message itself
     * @throws IOException If the file src/agt/interpreter/header.html cannot be read
     * @return a list of receivers (empty if broadcast)
     */
    protected List<String> showMsg( String sender, String content ) throws IOException {

        // empty the chat input
        if ( sender.equals( myName ) )
            inputField.setText( "" );

        // Build a Message from the content of the message
        Message msg = getMessage( content );

        // Get the current content of the chat
        String currentChat = chatPane.getText();
        // Get the HTML body
        int bodyStart = currentChat.indexOf( "<body>" ) + 6;
        int bodyEnd = currentChat.indexOf( "</body>" );
        String body = currentChat.substring( bodyStart, bodyEnd );
        // Load the HTML header from file
        String header = Files.readString( Path.of( "src/agt/interpreter/header.html" ) );
        // If the body does not contain messages -> empty it
        if ( !body.contains( "div" ) )
            body = "";
        // Create the new message div
        String senderDiv = "<div class='sender'> " + sender + "</div>";
        // Processor.process makes the string compatible with HTML
        String contentDiv = "<div class='content' " + Processor.process( msg.getContent() ) + "</div>";
        String msgClass = sender.equals( myName ) ? "sent" : "received";
        String msgDiv = "<div class='" + msgClass + "'>";
        msgDiv += senderDiv + contentDiv + "</div>";
        msgDiv += "</div></body></html>";
        // Add the new message to the body
        String updatedBody = body + msgDiv;
        
        // Set the new content to the chat and repaint it
        chatPane.setText( header + updatedBody );
        chatView.revalidate();
        chatView.repaint();

        return msg.getReceivers();
    }

    /**
     * This method takes a string and returns a Message object
     * @param msg The input message written by the user
     * @return the Message object with receivers and content
     */
    private Message getMessage( String msg ) {
        // Pattern and matcher for mentions
        Pattern pattern = Pattern.compile( "@\\w+" );
        Matcher matcher = pattern.matcher( msg );
        StringBuffer sb = new StringBuffer();
        List<String> mentions = new ArrayList<>();

        // Find all the mentions
        while ( matcher.find() ) {
            String mention = matcher.group();
            mentions.add( mention.substring(1) );
            matcher.appendReplacement( sb, "<span>" + mention + "</span>" );
        }
        matcher.appendTail( sb );
        // return the new Message
        return new Message( sb.toString(), mentions );
    }


}
