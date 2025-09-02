import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.HashMap;
import java.util.HashSet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Queue;
import java.util.Iterator;
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

import com.formdev.flatlaf.FlatLightLaf;

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
	private final String[] SUPPORTED_ILF = { "tell", "askOne", "askAll", "askOne" };
	private Ollama ollama;

	private JFrame chatView;
	private JTextPane chatPane;
	private JTextField inputField;
	private JButton sendButton;

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
		chatView = new JFrame();

		FlatLightLaf.setup();
		// this.art = art;

		chatView.setTitle("..::ChatBDI::..");
		chatView.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		chatView.setSize(400, 500);
		chatView.setLayout(new BorderLayout());

		chatPane = new JTextPane();
		chatPane.setContentType("text/html");
		chatPane.setEditable(false);
		JScrollPane scrollPane = new JScrollPane( chatPane );

		inputField = new JTextField();

		sendButton = new JButton("Send");

		JPanel inputPanel = new JPanel(new BorderLayout());
		inputPanel.add(inputField, BorderLayout.CENTER);
		inputPanel.add(sendButton, BorderLayout.EAST);

		chatView.add(scrollPane, BorderLayout.CENTER);
		chatView.add(inputPanel, BorderLayout.SOUTH);

		sendButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				sendMessage();
			}
		});

		chatView.setVisible(true);
	}

	private void sendMessage() {
        String message = inputField.getText();
        if ( !message.trim().isEmpty() ) {
            List<Literal> recipients = visMsg( "user", message );
            inputField.setText("");
            generate_property( message );
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

	@Override
	public void checkMail() {
		super.checkMail();
		Queue<Message> mbox = getTS().getC().getMailBox();
		if ( mbox.isEmpty() )
			return;
		Message m = mbox.peek();
		String user_msg = generate_sentence( m );
		visMsg( m.getSender(), user_msg );
	}

	private String generate_sentence( Message m ) {
		String prompt = "You are " + m.getSender() +
			" and you are " + m.getIlForce() + " and content: " + m.getPropCont() +
			". You have to impersonate the sender and translate the logical term into a natural language sentence as you were telling it to me.";
		JSONObject ans = new JSONObject( ollama.generate( LOG2NL_MODEL, prompt ) );
		System.out.println( m.getReceiver() );
		return ans.getString( "response" );
	}

	private Literal generate_property( String sentence ) {
		// remove all the mentions inside sentence
		sentence = sentence.replaceAll( "\\s*@\\S+", "" );
		Literal performative = classify( sentence );
		System.out.println( performative );
		Literal nearest = find_nearest( sentence );
		Literal term = get_term( sentence, performative, nearest );
		return createLiteral( "p" );
	}

	private Literal get_term( String sentence, Literal performative, Literal nearest ) {
		List<Literal> examples = get_examples( nearest );
		System.out.println( examples );
		List<JSONObject> json_terms = new ArrayList<>();
		JSONObject nearest_json = term_to_json( nearest );
		json_terms.add( nearest_json );
		for ( Literal example : examples ) {
			json_terms.add( term_to_json( example ) );
		}
		JSONObject schema = gen_json_schema( json_terms );
		String prompt = "The input sentence is \"" + sentence + "\". The best fitting term in the domain is " +
			nearest_json + ". The extracted performative is " + performative + ". We can find also other examples with the same functor: " +
			json_terms + ". Translate it.";
		Map<String, Literal> hints = gen_hints( examples );
		for ( String hint : hints.keySet() ) {
			prompt += " - " + hint + " should contain the " + hints.get( hint ) + "; if the content is provided, it MUST be here, otherwise place an underscore or null";
		}
		String ans = ollama.generate( GEN_MODEL, prompt, schema.toString() );
		System.out.println( ans );
		return createLiteral( "p" );
	}

	private JSONObject term_to_json( Literal term ) {
		JSONObject json = new JSONObject();
		String functor = term.getFunctor();
		json.put( "functor", functor );
		for ( int i = 0; i<term.getArity(); i++ ) {
			json.put( "arg" + i, term.getTerm(i) );
		}
		System.out.println( json );
		return json;
	}

	private JSONObject gen_json_schema( List<JSONObject> examples ) {
		JSONObject json = new JSONObject();
		json.put( "type", "object" );
		JSONObject properties = new JSONObject();
		properties.put( "functor", new JSONObject().put( "type", "string" ) );
		Iterator<String> keys = examples.get( 0 ).keys();
		while( keys.hasNext() ) {
			String key = keys.next();
			Set<String> curr_type = new HashSet<>();
			System.out.println( key );
			if ( key.equals( "functor" ) )
				continue;
			for ( JSONObject example : examples ) {
				Term v = (Term) example.get( key );
				System.out.println( v );
				if ( v.isString() || v.isAtom() || v.isVar() )
					curr_type.add( "string" );
				else if ( v.isNumeric() )
					curr_type.add( "number" );
				else if ( v.isList() )
					curr_type.add( "array" );
				else if ( v.isUnnamedVar() )
					curr_type.add( "null" );
			}
			if ( curr_type.size() == 1 ) {
				properties.put( key, new JSONObject().put( "type", curr_type.iterator().next() ) );
			} else {
				properties.put( key, new JSONObject().put( "anyOf", curr_type.toArray() ) );
			}
		}
		json.put( "properties", properties );
		return json;
	}

	private Map<String, Literal> gen_hints( List<Literal> examples ) {
		Map<String, Literal> hints = new HashMap<>();
		for (int i = 0; i<examples.get(0).getArity(); i++ ){
			for (Literal l : examples ) {
				if ( l.getTerm( i ).isVar() && !hints.containsKey( "arg" + i ) )
					hints.put( "arg" + i, (Literal) l.getTerm( i ) );
			}
		}
		return hints;
	}

	private Literal classify( String sentence ) {
		JSONObject performative = new JSONObject();
		performative.put( "type", "enum" );
		performative.put( "enum", SUPPORTED_ILF );
		JSONObject properties = new JSONObject();
		properties.put( "performative", performative );
		JSONObject json = new JSONObject();
		json.put( "type", "object" );
		json.put( "properties", properties );
		json.put( "required", new JSONArray().put( "performative" ) );
		System.out.println( json.toString() );
		JSONObject ans = new JSONObject( ollama.generate( CLASS_MODEL, sentence, json.toString() ) );
		String perf_obj_str = ans.getString( "response" )
			.replaceAll( "```json\\s*", "" )
			.replaceAll( "```\\s*$", "" )
			.trim();
		JSONObject perf_obj = new JSONObject( perf_obj_str );
		return createLiteral( perf_obj.getString( "performative" ) );
	}

	private Literal find_nearest( String sentence ) {
		List<Double> emb = ollama.embed( EMB_MODEL, sentence );
		Literal best_lit = null;
		double best_dist = Double.MAX_VALUE;
		for ( Literal lit : embeddings.keySet() ) {
			List<Double> lit_emb = embeddings.get( lit );
			double dist = cosine_similarity( emb, lit_emb );
			if ( dist < best_dist ) {
				best_dist = dist;
				best_lit = lit;
			}
		}
		System.out.println( "Best fitting embedding is " + best_lit );
		return best_lit;
	}

	private List<Literal> get_examples( Literal nearest ) {
		List<Literal> examples = new ArrayList<>();
		for ( Literal lit : embeddings.keySet() ) {
			if ( nearest.getFunctor().equals( lit.getFunctor() ) && nearest.getArity() == lit.getArity() ) {
				examples.add( lit );
			}
		}
		return examples;
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
		ollama.create( GEN_MODEL, NL2LOG_MODEL, TEMPERATURE, "java/modelfiles/nl2log.txt" );
		ollama.create( GEN_MODEL, CLASS_MODEL, TEMPERATURE, "java/modelfiles/classifier.txt" );
		ollama.create( GEN_MODEL, LOG2NL_MODEL, TEMPERATURE, "java/modelfiles/log2nl.txt" );
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

	private double cosine_similarity( List<Double> emb1, List<Double> emb2 ) {
        if (emb1 == null || emb2 == null)
            throw new IllegalArgumentException( "One of the embeddings is null" );
        assert emb1 != null && emb2 != null;
        if ( emb1.size() != emb2.size() )
            throw new IllegalArgumentException( "Embeddings have different sizes" );

        double dotProd = 0.0;
        double norm1 = 0.0;
        double norm2 = 0.0;

        for ( int i=0; i<emb1.size(); i++ ) {
            dotProd += emb1.get(i) * emb2.get(i);
            norm1 += emb1.get(i) * emb1.get(i);
            norm2 += emb2.get(i) * emb2.get(i);
        }

        norm1 = Math.sqrt(norm1);
        norm2 = Math.sqrt(norm2);

        if ( norm1 == 0 || norm2 == 0 )
            throw new IllegalArgumentException( "Embedding norm cannot be ZERO");

        return 1.0 - (dotProd / ( norm1 * norm2 ) );
    }

}
