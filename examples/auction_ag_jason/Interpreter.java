import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Queue;

import jason.asSyntax.*;
import static jason.asSyntax.ASSyntax.*;

import jason.runtime.RuntimeServices;

import jason.architecture.AgArch;
import jason.asSemantics.*;
import jason.bb.*;
import jason.pl.*;

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
	}

	@Override
	public void checkMail() {
		super.checkMail();
		Queue<Message> mbox = getTS().getC().getMailBox();
		System.out.println( "Mailbox: " + mbox );
		for ( Message m : mbox ) {
			System.out.println( m );
		}
	}

	private void init_embeddings() {
		getTS().getLogger().log( Level.INFO, "Initializing embeddings" );
		embeddings = new HashMap<>();
		ag_literals = new HashMap<>();
		try {
			Collection<String> ag_names = getRuntimeServices().getAgentsName();
			for ( String ag_name : ag_names ) {
				Agent ag = getRuntimeServices().getAgentSnapshot( ag_name );
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

}
