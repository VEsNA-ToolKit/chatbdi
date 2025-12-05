package chatbdi;

import java.util.List;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.concurrent.ExecutionException;
import java.io.IOException;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.JList;
import javax.swing.DefaultListModel;
import javax.swing.ListCellRenderer;
import javax.swing.JLabel;
import javax.swing.SwingWorker;
import javax.swing.SwingUtilities;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import jason.infra.local.RunLocalMAS;

import com.github.rjeschke.txtmark.Processor;

/**
 * The ChatUI class manages the io visible to the user, using a JList for messages.
 */
public class ChatUI {

    /** The main window frame */
    private JFrame chatView;
    private DefaultListModel<ChatEntry> listModel;
    /** Chat: a list of messages */
    private JList<ChatEntry> messageList;
    /** The input field for messages */
    private JTextField inputField;
    /** The button right to the input field */
    private JButton sendButton;
    /** The interpreter agent name */
    private String myName;

    // index of the last outgoing "sending" message, -1 if none
    // TODO: I think this can be removed with some online computation or tagging
    private int lastSendingIndex = -1;
    // keep a reference to the last sending ChatEntry so we can clear it even if indices shift
    private ChatEntry lastSendingEntry = null;

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

        listModel = new DefaultListModel<>();
        messageList = new JList<>( listModel );
        messageList.setCellRenderer( new ChatCellRenderer() );
        messageList.setFixedCellWidth(350);
        messageList.setVisibleRowCount(10);

        JScrollPane scrollPane = new JScrollPane( messageList );

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
     * - calls the handleUserMsg function of the agent.
     */
    private void handleSendMsg( ) {
        // Get the text from the input field
        String msg = inputField.getText();
        // Get the agent casted to Interpreter
        Interpreter ag = (Interpreter) RunLocalMAS.getRunner().getAg( myName ).getTS().getAgArch();
        
        // show the message on the chat formatted with receivers highlighet (and take them)
        List<String> receivers = showMsg( myName, msg );

        // This worker will launch the generation of the KQML term from the sentence in background
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() {
                try {
                    ag.handleUserMsg( receivers, msg );
                } catch( IOException ioe ) {
                    ag.logSevere("Cannot handle user message: " + ioe.getMessage());
                    return null;
                } catch ( Exception e ) {
                    ag.logSevere( "Cannot translate or send the current message. Error: " + e.toString() );
                }
                return null;
            }

            // Manage when the translation is completed
            @Override
            public void done() {
                try {
                    get();
                    if ( lastSendingEntry != null ) {
                        int idx = listModel.indexOf(lastSendingEntry);
                        if ( idx != -1 ) {
                            ChatEntry sent = listModel.get(idx);
                            sent.setSending(false);
                            listModel.set(idx, sent);
                            messageList.ensureIndexIsVisible(idx);
                        } else if ( lastSendingIndex >= 0 && lastSendingIndex < listModel.size() ) {
                            // fallback to index if object not found
                            ChatEntry sent = listModel.get( lastSendingIndex );
                            sent.setSending(false);
                            listModel.set( lastSendingIndex, sent );
                            messageList.ensureIndexIsVisible( lastSendingIndex );
                        }
                        lastSendingEntry = null;
                        lastSendingIndex = -1;
                    }
                    chatView.revalidate();
                    chatView.repaint();
                } catch ( InterruptedException | ExecutionException e ) {
                    ag.logSevere( e.toString() );
                }
            }
        }.execute();

    }

    /** Display a message in the list and return detected receivers. 
     * @param sender the sender of the message
     * @param content the content of the message
    */
    protected List<String> showMsg( String sender, String content ) {
        if ( sender.equals( myName ) )
            inputField.setText( "" );

        Message msg = getMessage( content );

        boolean isOutgoing = sender.equals( myName );
        ChatEntry entry = new ChatEntry( sender, msg.getContent(), isOutgoing );
        if ( isOutgoing ) {
            entry.setSending(true);
            listModel.addElement( entry );
            lastSendingEntry = entry;
            lastSendingIndex = listModel.size() - 1;
        } else {
            listModel.addElement( entry );
        }

        // scroll to bottom
        messageList.ensureIndexIsVisible( listModel.size() - 1 );

        return msg.getReceivers();
    }

    /** Add or remove a "typing" indicator for a sender. Can be called from other classes (e.g., Interpreter). */
    public void setTyping(String sender, boolean typing) {
        SwingUtilities.invokeLater(() -> {
            int idx = findTypingIndex(sender);
            if ( typing ) {
                if ( idx == -1 ) {
                    ChatEntry e = new ChatEntry(sender, "<i>typing...</i>", false);
                    e.setTypingEntry(true);
                    listModel.addElement(e);
                    messageList.ensureIndexIsVisible(listModel.size()-1);
                }
            } else {
                if ( idx != -1 ) {
                    listModel.remove(idx);
                }
            }
        });
    }

    private int findTypingIndex(String sender) {
        for ( int i=0; i<listModel.size(); i++ ) {
            ChatEntry e = listModel.get(i);
            if ( e.isTypingEntry() && e.getSender().equals(sender) )
                return i;
        }
        return -1;
    }

    /** Simple data holder for parsed message and receivers */
    private class Message {
        private String content;
        private List<String> receivers;

        private Message( String content, List<String> receivers ) {
            this.content = content;
            this.receivers = receivers;
        }

        private String getContent() { return this.content; }
        private List<String> getReceivers() { return this.receivers; }
    }

    private class ChatEntry {
        private String sender;
        private String content;
        private boolean sending;
        private boolean typingEntry;
        private boolean systemNotice;

        ChatEntry( String sender, String content, boolean outgoing ) {
            this.sender = sender;
            this.content = content;
            this.sending = false;
            this.typingEntry = false;
            this.systemNotice = false;
        }

        String getSender() { return sender; }
        String getContent() { return content; }
        boolean isSending() { return sending; }
        void setSending(boolean s) { this.sending = s; }
        boolean isTypingEntry() { return typingEntry; }
        void setTypingEntry(boolean t) { this.typingEntry = t; }
        boolean isSystemNotice() { return systemNotice; }
        void setSystemNotice(boolean s) { this.systemNotice = s; }
    }

    /** Show a small system notice under the current message when a tagged agent does not exist. */
    public void showAgentNotFoundNotice(String agentName) {
        SwingUtilities.invokeLater(() -> {
            String content = "<i>agent @" + agentName + " does not exist</i>";
            ChatEntry note = new ChatEntry("", content, false);
            note.setSystemNotice(true);
            listModel.addElement(note);
            messageList.ensureIndexIsVisible(listModel.size() - 1);
        });
    }

    private class ChatCellRenderer implements ListCellRenderer<ChatEntry> {
        ChatCellRenderer() {
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends ChatEntry> list, ChatEntry value, int index, boolean isSelected, boolean cellHasFocus) {
            String sender = value.getSender();
            String contentHtml = Processor.process( value.getContent() );
            boolean sending = value.isSending();

            // Outer panel provides spacing between messages and from the edges
            JPanel panel = new JPanel(new BorderLayout());
            panel.setOpaque(false);
            panel.setBorder(new EmptyBorder(6, 8, 6, 8));

            // Determine bubble color
            Color bubbleColor;
            Color textColor = Color.BLACK;
            if ( value.isSystemNotice() ) {
                bubbleColor = new Color(250,250,250);
                textColor = Color.GRAY;
            } else if ( value.isTypingEntry() ) {
                bubbleColor = new Color(250,250,250);
                textColor = Color.DARK_GRAY;
            } else if ( sender.equals( myName ) ) {
                bubbleColor = new Color(220,248,198);
            } else {
                bubbleColor = new Color(173,216,230); // light blue for incoming
            }

            Component contentComp;
            if ( value.isSystemNotice() ) {
                // system notice: no bubble background, simple small gray text aligned right
                JLabel noticeLabel = new JLabel();
                noticeLabel.setOpaque(false);
                noticeLabel.setFont(new Font("SansSerif", Font.PLAIN, 11));
                noticeLabel.setForeground(Color.GRAY);
                String html = "<html><div style='max-width:300px;color:gray;font-size:small;'>" + contentHtml + "</div></html>";
                noticeLabel.setText(html);
                // wrap label inside a panel to preserve padding similar to bubbles
                JPanel wrapper = new JPanel(new BorderLayout());
                wrapper.setOpaque(false);
                wrapper.setBorder(new EmptyBorder(8,12,8,12));
                wrapper.add(noticeLabel, BorderLayout.CENTER);
                contentComp = wrapper;
            } else {
                // Bubble panel draws a rounded rectangle background for normal messages
                BubblePanel bubble = new BubblePanel(bubbleColor);
                bubble.setLayout(new BorderLayout());
                bubble.setBorder(new EmptyBorder(8,12,8,12));

                JLabel label = new JLabel();
                label.setOpaque(false);
                label.setFont(new Font("SansSerif", Font.PLAIN, 12));
                label.setForeground(textColor);

                String html = "<html><div style='max-width:300px;'>" +
                              "<b>" + sender + "</b><br/>" + contentHtml;
                if ( sending ) {
                    html += "<div style='font-size:small;color:gray'><i>sending...</i></div>";
                }
                html += "</div></html>";

                label.setText(html);
                bubble.add(label, BorderLayout.CENTER);
                contentComp = bubble;
            }

            // place the content component according to message type
            if ( value.isSystemNotice() ) {
                panel.add(contentComp, BorderLayout.EAST);
            } else if ( value.isTypingEntry() ) {
                panel.add(contentComp, BorderLayout.WEST);
            } else if ( sender.equals( myName ) ) {
                panel.add(contentComp, BorderLayout.EAST);
            } else {
                panel.add(contentComp, BorderLayout.WEST);
            }

            return panel;
        }

        // Panel that draws a rounded rectangle as background for the message bubble
        private class BubblePanel extends JPanel {
            private final Color bg;
            private final int arc = 16;

            BubblePanel(Color bg) {
                this.bg = bg;
                setOpaque(false);
            }

            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(bg);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), arc, arc);
                g2.dispose();
                super.paintComponent(g);
            }
        }
    }

    /** Extract mentions and replace them with a span (keeps HTML-compatible content). */
    private Message getMessage( String msg ) {
        Pattern pattern = Pattern.compile( "@\\w+" );
        Matcher matcher = pattern.matcher( msg );
        StringBuffer sb = new StringBuffer();
        List<String> mentions = new ArrayList<>();

        while ( matcher.find() ) {
            String mention = matcher.group();
            mentions.add( mention.substring(1) );
            // modern/elegant styled highlight for mentions
            String styled = "<span style='color:#0b66d0;padding:2px 6px;border-radius:8px;font-weight:600;'>" + mention + "</span>";
            matcher.appendReplacement( sb, styled );
        }
        matcher.appendTail( sb );
        return new Message( sb.toString(), mentions );
    }

}
