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
import jason.asSyntax.parser.ParseException;

import com.github.rjeschke.txtmark.Processor;
import com.formdev.flatlaf.FlatLightLaf;

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

    public ChatUI( String myName ) {

        // Initialize FlatLaf Look and Feel
        try {
            FlatLightLaf.setup();
        } catch (Exception e) {
            System.err.println("Failed to initialize FlatLaf: " + e.getMessage());
        }

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
        ChatEntry entry = showMsg( myName, msg );

        // This worker will launch the generation of the KQML term from the sentence in background
        new SwingWorker<Integer, Void>() {
            @Override
            protected Integer doInBackground() {
                try {
                    String plainContent = entry.getContent().replaceAll("<[^>]*>", "");
                    return ag.handleUserMsg( entry.getReceivers(), plainContent );
                } catch( IOException ioe ) {
                    ag.logSevere("Cannot handle user message: " + ioe.getMessage());
                } catch( ParseException pe ) {

                } catch ( Exception e ) {
                    ag.logSevere( "Cannot translate or send the current message. Error: " + e.getStackTrace().toString() );
                    e.printStackTrace();
                }
                return -1;
            }

            // Manage when the translation is completed
            @Override
            public void done() {
                try {
                    int result = get();
                    entry.setSending(false);
                    if ( result == 0 )
                        entry.setWarning( true );
                    else if ( result == -1 )
                        entry.setError( true );
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
    protected ChatEntry showMsg( String sender, String content ) {
        if ( sender.equals( myName ) )
            inputField.setText( "" );

        ChatEntry entry = new ChatEntry( sender, content );
        if ( sender.equals( myName ) ) {
            entry.setSending(true);
            listModel.addElement( entry );
        } else {
            listModel.addElement( entry );
        }

        // scroll to bottom
        // messageList.ensureIndexIsVisible( listModel.size() - 1 );

        return entry;
    }

    /** Add or remove a "typing" indicator for a sender. Can be called from other classes (e.g., Interpreter). */
    public void setTyping(String sender, boolean typing) {
        SwingUtilities.invokeLater(() -> {
            int idx = findTypingIndex(sender);
            if ( typing ) {
                if ( idx == -1 ) {
                    ChatEntry e = new ChatEntry(sender, "<i>typing...</i>" );
                    e.setTyping(true);
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
            if ( e.isTyping() && e.getSender().equals(sender) )
                return i;
        }
        return -1;
    }

    private class ChatEntry {
        private String sender;
        private String content;
        private List<String> mentions;
        // Possible message states
        private boolean sending;
        private boolean typing;
        private boolean systemNotice;
        private boolean warning;
        private boolean error;

        ChatEntry( String sender, String msg ) {
            this.sender = sender;
            this.content = msg;
            this.sending = false;
            this.typing = false;
            this.warning = false;
            this.error = false;
            this.systemNotice = false;
            this.mentions = new ArrayList<>();

            if ( !content.contains( "@" ) )
                return;
            Pattern pattern = Pattern.compile( "@\\w+" );
            Matcher matcher = pattern.matcher( msg );
            StringBuffer sb = new StringBuffer();

            while ( matcher.find() ) {
                String mention = matcher.group();
                this.mentions.add( mention.substring(1) );
                // modern/elegant styled highlight for mentions
                String styled = "<span style='color:#0b66d0;padding:2px 6px;border-radius:8px;font-weight:600;'>" + mention + "</span>";
                matcher.appendReplacement( sb, styled );
            }
            matcher.appendTail( sb );
            this.content = sb.toString();
        }

        boolean isSending() { return sending; }
        boolean isTyping() { return typing; }
        boolean isSystemNotice() { return systemNotice; }
        boolean isWarning() { return warning; }
        boolean isError() { return error; }

        void setSending(boolean s) { this.sending = s; }
        void setTyping(boolean t) { this.typing = t; }
        void setSystemNotice(boolean s) { this.systemNotice = s; }
        void setWarning( boolean s ) { this.warning = s; }
        void setError( boolean s ) { this.error = s; }

        List<String> getReceivers() {return mentions; }
        String getSender() { return sender; }
        String getContent() { return content; }
    }

    /** Show a small system notice under the current message when a tagged agent does not exist. */
    public void showAgentNotFoundNotice(String agentName) {
        String content = "<i>agent @" + agentName + " does not exist</i>";
        ChatEntry note = new ChatEntry("", content );
        note.setSystemNotice(true);
        listModel.addElement(note);
        messageList.ensureIndexIsVisible(listModel.size() - 1);
    }

    /** Show the KQML translation in gray under the current message. */
    public void showKqmlTranslation( String sender, String ilf, String msg ) {
        String content = "<i>ilf: " + escapeHtml(ilf) + ", content: " + escapeHtml(msg) + "</i>";
        ChatEntry note = new ChatEntry(sender, content );
        note.setSystemNotice(true);
        SwingUtilities.invokeLater(() -> {
            listModel.addElement(note);
            messageList.ensureIndexIsVisible(listModel.size() - 1);
        });
    }

    private String escapeHtml(String s) {
        if ( s == null ) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    private class ChatCellRenderer implements ListCellRenderer<ChatEntry> {
        ChatCellRenderer() {
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends ChatEntry> list, ChatEntry value, int index, boolean isSelected, boolean cellHasFocus) {
            String sender = value.getSender();
            String contentHtml = Processor.process( value.getContent() );

            // Outer panel provides spacing between messages and from the edges
            JPanel panel = new JPanel(new BorderLayout());
            panel.setOpaque(false);
            panel.setBorder(new EmptyBorder(6, 8, 6, 8));

            // Determine bubble color (error/warning override)
            Color bubbleColor;
            Color textColor = Color.BLACK;
            if ( value.isError() ) {
                // error: reddish background with white text for contrast
                bubbleColor = new Color(234,138,148);
                textColor = Color.WHITE;
            } else if ( value.isWarning() ) {
                // warning: light yellow background
                bubbleColor = new Color(255, 245, 153);
                textColor = Color.BLACK;
            } else if ( value.isSystemNotice() ) {
                bubbleColor = new Color(250,250,250);
                textColor = Color.GRAY;
            } else if ( value.isTyping() ) {
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
                String html = "<html><div style='width:250px;color:gray;font-size:small;word-wrap:break-word;'>" + contentHtml + "</div></html>";
                noticeLabel.setText(html);
                // wrap label inside a panel to preserve padding similar to bubbles
                JPanel wrapper = new JPanel(new BorderLayout());
                wrapper.setOpaque(false);
                wrapper.setBorder(new EmptyBorder(8,12,8,12));
                wrapper.setMaximumSize(new java.awt.Dimension(270, Integer.MAX_VALUE));
                wrapper.add(noticeLabel, BorderLayout.CENTER);
                contentComp = wrapper;
            } else {
                // Bubble panel draws a rounded rectangle background for normal messages
                BubblePanel bubble = new BubblePanel(bubbleColor);
                bubble.setLayout(new BorderLayout());
                bubble.setBorder(new EmptyBorder(8,12,8,12));
                bubble.setMaximumSize(new java.awt.Dimension(270, Integer.MAX_VALUE));

                JLabel label = new JLabel();
                label.setOpaque(false);
                label.setFont(new Font("SansSerif", Font.PLAIN, 12));
                label.setForeground(textColor);

                String html = "<html><div style='width:230px;word-wrap:break-word;overflow-wrap:break-word;'>" +
                              "<b>" + sender + "</b><br/>" + contentHtml;
                if ( value.isSending() ) {
                    html += "<div style='font-size:small;color:gray'><i>sending...</i></div>";
                } else if ( value.isWarning() ) {
                    html += "<div style='font-size:small;color:orange'><i>One or more receivers not found.</i></div>";
                } else if ( value.isError() ) {
                    html += "<div style='font-size:small;color:red;'><i>All receivers not found.</i></div>";
                }
                html += "</div></html>";

                label.setText(html);
                bubble.add(label, BorderLayout.CENTER);
                contentComp = bubble;
            }

            // place the content component according to message type
            // Wrap the content in a side panel so we can append an icon to the right
            JPanel sidePanel = new JPanel(new BorderLayout());
            sidePanel.setOpaque(false);
            sidePanel.add(contentComp, BorderLayout.CENTER);

            if ( value.isError() ) {
                // small spacing between bubble and icon: simple '!' label with red glyph on white background
                JLabel iconLabel = new JLabel("!");
                iconLabel.setFont(new Font("SansSerif", Font.BOLD, 12));
                iconLabel.setForeground(Color.RED);
                iconLabel.setOpaque(true);
                iconLabel.setBackground(Color.WHITE);
                iconLabel.setHorizontalAlignment(SwingConstants.CENTER);
                iconLabel.setPreferredSize(new java.awt.Dimension(20, 20));
                // add a little left spacing; no red border
                iconLabel.setBorder(new EmptyBorder(0,8,0,0));
                sidePanel.add(iconLabel, BorderLayout.EAST);
            } else if ( value.isWarning() ) {
                // show a warning glyph (⚠) with orange glyph on white background
                JLabel warnLabel = new JLabel("\u26A0");
                warnLabel.setFont(new Font("SansSerif", Font.BOLD, 12));
                warnLabel.setForeground(new Color(255,140,0));
                warnLabel.setOpaque(true);
                warnLabel.setBackground(Color.WHITE);
                warnLabel.setHorizontalAlignment(SwingConstants.CENTER);
                warnLabel.setPreferredSize(new java.awt.Dimension(20, 20));
                warnLabel.setBorder(new EmptyBorder(0,8,0,0));
                sidePanel.add(warnLabel, BorderLayout.EAST);
            }

            if ( value.isSystemNotice() ) {
                if ( value.getSender() != null && value.getSender().equals( myName ) )
                    panel.add(sidePanel, BorderLayout.EAST);
                else
                    panel.add(sidePanel, BorderLayout.WEST);
            } else if ( value.isTyping() ) {
                panel.add(sidePanel, BorderLayout.WEST);
            } else if ( sender.equals( myName ) ) {
                panel.add(sidePanel, BorderLayout.EAST);
            } else {
                panel.add(sidePanel, BorderLayout.WEST);
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

}
