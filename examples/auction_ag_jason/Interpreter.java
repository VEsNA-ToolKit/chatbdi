import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Queue;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.JTextPane;

import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import jason.asSyntax.*;
import static jason.asSyntax.ASSyntax.*;
import jason.infra.local.RunLocalMAS;

import jason.runtime.RuntimeServices;

import jason.architecture.AgArch;
import jason.asSemantics.*;
import jason.bb.*;
import jason.pl.*;

import org.json.JSONArray;
import org.json.JSONObject;

public class Interpreter extends AgArch {

	// GLOBAL VARIABLES
	private final String OLLAMA_URL = "http://localhost:11434/api/";
	private final String EMB_MODEL = "all-minilm";
	private final String GEN_MODEL = "qwen2.5-coder";
	private final String LOG2NL_MODEL = "logic-to-nl";
	private final String NL2LOG_MODEL = "nl-to-logic";
	private final String CLASS_MODEL = "classify-performative";
	private final float TEMPERATURE = 0.2f;
	private final String DEBUG_LOG = "interpreter.log";
	private Ollama ollama;

	private Map<Literal, List<Double>> embeddings;
	private Map<String, List<Literal>> ag_literals;

	// Init method: we get the beliefs and plans from the other agents.
	@Override
	public void init() throws Exception {

		// Check if the Ollama server is up and running
		ollama = new Ollama( OLLAMA_URL );

		// Initialize embeddings and generation models
		getTS().getLogger().log( Level.INFO, "Initializing Ollama models" );
		init_embeddings();
		init_generation_models();
		ChatView chatView = new ChatView();
	}

	@Override
	public void checkMail() {
		super.checkMail();
		Queue<Message> mbox = getTS().getC().getMailBox();
		System.out.println( "Mailbox: " + mbox );
		for ( Message m : mbox ) {
			System.out.println( m );
			String user_msg = generate_sentence( m );
		}
	}

	private String generate_sentence( Message m ) {
		String ans = ollama.generate( LOG2NL_MODEL, m.getPropCont().toString() );
		return ans;
	}

	private void init_embeddings() {
		getTS().getLogger().log( Level.INFO, "Initializing embeddings" );
		embeddings = new HashMap<>();
		ag_literals = new HashMap<>();
		try {
			Collection<String> ag_names = getRuntimeServices().getAgentsName();
			for ( String ag_name : ag_names ) {
				Agent ag = RunLocalMAS.getRunner().getAg( ag_name ).getTS().getAg();
				BeliefBase bb = ag.getBB().clone();
				PlanLibrary pl = ag.getPL().clone();

				List<Literal> curr_ag_literals = new ArrayList<>();
				for ( Literal bel : bb ) {
					if ( bel.toString().contains( "kqml::" ) )
						continue;
					if ( embeddings.get( bel ) != null )
						continue;
					curr_ag_literals.add( bel );
					String bel_str = preprocess( bel );
					List<Double> embedding = ollama.embed( EMB_MODEL, bel_str );
					embeddings.put( bel, embedding );
				}
				for ( Plan p : pl ) {
					if ( p.toString().contains( "@kqml" ) )
						continue;
					if ( embeddings.get( p.getTrigger() ) != null )
						continue;
					String trigger = preprocess( p.getTrigger().getLiteral() );
					List<Double> embedding = ollama.embed( EMB_MODEL, trigger );
					embeddings.put( p.getTrigger(), embedding );
					curr_ag_literals.add( p.getTrigger() );
				}

				ag_literals.put( ag_name, curr_ag_literals );

			}
		} catch (Exception e) {
			getTS().getLogger().log(Level.SEVERE, "Error initializing embeddings: " + e.getMessage());
		}
	}

	private void init_generation_models() {
		getTS().getLogger().log( Level.INFO, "Initializing generation models" );
		ollama.create( GEN_MODEL, NL2LOG_MODEL, TEMPERATURE, "modelfiles/nl2log.txt" );
		ollama.create( GEN_MODEL, CLASS_MODEL, TEMPERATURE, "modelfiles/classifier.txt" );
		ollama.create( GEN_MODEL, LOG2NL_MODEL, TEMPERATURE, "modelfiles/log2nl.txt" );
	}

	private String preprocess( Literal bel ) {
		// Get the functor and preprocess it;
		// We will use it to give more importance to the functor computing embeddings
		String functor = bel.getFunctor().replace( "_", " " ).replace( "my", "your" ) + " ";

		String terms = "";
		// if the literal has no terms, return the functor repeated 4 times
		if ( !bel.hasTerm() )
			return functor.repeat( 4 );

		// if the literal has terms, preprocess them
		for ( Term t : bel.getTerms() ) {
			String t_str = t.toString();
			t_str = t_str.replace( "_", " ");
			t_str = t_str.replace( "(", " ( " );
			t_str = t_str.replace( ")", " ) " );
			t_str = t_str.replace( ",", " , " );
			t_str = t_str.replace( "my", "your" );
			t_str = t_str.replaceAll( "([=<>!]+)", " $1 " );
			t_str = t_str.replaceAll( "\\s+", " " );
			t_str = t_str.trim();
			terms += t_str + " ";
		}
		// return the functor repeated 4 times followed by the preprocessed terms
		// this way, we give more importance to the functor with respect to the terms
		return functor.repeat( 4 ) + terms.trim();
	}

	class ChatView extends JFrame {
		// private ChatArtifact art;
		private JTextPane chatPane;
		private JTextField inputField;
		private JButton sendButton;

		public ChatView( ) {
			// FlatLightLaf.setup();
			// this.art = art;

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

			// inputField.addActionListener(new ActionListener() {
			// @Override
			// 	public void actionPerformed(ActionEvent e) {
			// 		sendMessage();
			// 	}
			// });

			setVisible(true);
		}

		private void sendMessage() {
            String message = inputField.getText();
            if ( !message.trim().isEmpty() ) {
                List<Literal> recipients = visMsg( "user", message );
                inputField.setText("");
                // try {
                //     art.beginExtSession();
                //     if ( recipients.size() > 0 )
                //         art.notify_new_msg( recipients, message );
                //     else
                //         art.notify_new_msg( message );
                // } finally {
                //     art.endExtSession();
                // }
                // log("Sent " + message);
            }
        }

		public List<Literal> visMsg( String sender, String msg ) {
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
