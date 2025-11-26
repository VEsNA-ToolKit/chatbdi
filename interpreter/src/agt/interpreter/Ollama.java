package chatbdi;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.ConnectException;
import java.net.URI;
import java.io.IOException;
import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import java.util.HashSet;

import java.nio.file.Files;
import java.nio.file.Path;

import org.json.JSONObject;
import org.json.JSONArray;

import jason.asSyntax.*;
import jason.asSemantics.Message;
import static jason.asSyntax.ASSyntax.*;

import jason.asSyntax.parser.ParseException;

/**
 * The Ollama class provides an API to call the Ollama models
 * @author Andrea Gatti
 */
public class Ollama {
	/**
	 * The url at which the Ollama server listens
	 */
    private final String URL = "http://localhost:11434/api/";
	/**
	 * The embedding model to use
	 */
    protected final String EMB_MODEL = "all-minilm";
	/**
	 * The generation model to use
	 */
    protected final String GEN_MODEL = "qwen2.5-coder";
	/**
	 * The name that will be assigned to the model that translates KQML to NL
	 */
    private final String LOG2NL_MODEL = "logic-to-nl";
	/**
	 * The name that will be assigned to the model that translates NL to KQML
	 */
    private final String NL2LOG_MODEL = "nl-to-logic";
	/**
	 * The name that will be assigned to the model that classifies the Illocutionary Force
	 */
    private final String CLASS_MODEL = "classify-ilf";

	/**
	 * The temperature for the generation models
	 */
    private final float TEMPERATURE = 0.0f;
	/**
	 * The seed for the generation models (to be reproducible)
	 */
    private final int SEED = 42;

    private final String NL2LOG_PROMPT = "src/agt/interpreter/modelfiles/nl2logPrompt.txt";
    private final String LOG2NL_PROMPT = "src/agt/interpreter/modelfiles/log2nlPrompt.txt";
    private final String NL2LOG_MODELFILE = "src/agt/interpreter/modelfiles/nl2log.txt";
    private final String LOG2NL_MODELFILE = "src/agt/interpreter/modelfiles/log2nl.txt";
    private final String CLASS_MODELFILE = "src/agt/interpreter/modelfiles/classifier.txt";

	/**
	 * List of the supported Illocutionary Forces
	 */
	private final String[] SUPPORTED_ILF;
	/**
	 * The HTTP client for the requests
	 */
	private final HttpClient client = HttpClient.newHttpClient();

	/**
	 * Creates a new Ollama object
	 * @param supportedIlfs the list of supported Illocuctionary Forces
	 * @throws ConnectException if the ollama server is not available
	 */
	public Ollama( String[] supportedIlfs ) throws ConnectException {
		// Store the supported Illocutionary Forces
		SUPPORTED_ILF = new String[ supportedIlfs.length ];
		for ( int i = 0; i < supportedIlfs.length; i++ )
			SUPPORTED_ILF[i] = supportedIlfs[i];

		// Check if the Ollama server is online at the given address
		if ( !is_online() ) {
			throw new ConnectException( "The Ollama Server is offline or the address is not correct." );
		}
		// Initialize the generation models with prompts
		System.out.println( "Initializing generation models" );
		create( GEN_MODEL, NL2LOG_MODEL, TEMPERATURE, NL2LOG_MODELFILE, SEED );
		create( GEN_MODEL, LOG2NL_MODEL, TEMPERATURE, LOG2NL_MODELFILE, SEED );
		create( GEN_MODEL, CLASS_MODEL, TEMPERATURE, CLASS_MODELFILE, SEED );
	}

	/**
	 * Checks if Ollama is online
	 * @return true if available and ready (status code: 200), false otherwise
	 * @throws ConnectExcept if the server is not reachable
	 * @throws IOException if the message cannot be send
	 * @throws InterruptedException if the connection is interrupted
	 */
	private boolean is_online() {
		try {
		HttpRequest req = HttpRequest.newBuilder()
			.uri( URI.create( URL.replaceAll( "/api/", "" ) ) )
			.header( "Content-Type", "application/json" )
			.GET()
			.build();

			HttpResponse<String> res = client.send( req, HttpResponse.BodyHandlers.ofString() );
			return res.statusCode() == 200;
		} catch( ConnectException e ) {
			return false;
		} catch( IOException e ) {
			e.printStackTrace();
		} catch( InterruptedException e ) {
			e.printStackTrace();
		}
		return false;
	}

	/**
	 * Generates the embedding of a string
	 * @param str the string to embed
	 * @return a list of double (the embedding vector). The size depends on the model used.
	 */
	public List<Double> embed( String str ) {
		// Build the JSON object
		JSONObject json = new JSONObject();
		json.put( "model", EMB_MODEL );
		json.put( "input", str );
		json.put( "stream", false );

		// Build the request
		HttpRequest req = HttpRequest.newBuilder()
			.uri( URI.create( URL + "embed" ) )
			.header( "Content-Type", "application/json" )
			.POST( HttpRequest.BodyPublishers.ofString( json.toString() ) )
			.build();

		try {
			// Send the message
			HttpResponse<String> res = client.send( req, HttpResponse.BodyHandlers.ofString() );
			// Load the content inside a JSONObject
			JSONObject emb_json = new JSONObject( res.body() );
			// Get the embedding array
			JSONArray vec = emb_json.getJSONArray( "embeddings" ).getJSONArray(0);
			// Copy the JSONArray into a Java List
			List<Double> list = new ArrayList<>();
			for( int i = 0; i < vec.length(); i++ ) {
				list.add( vec.getDouble( i ) );
			}
			return list;
		} catch ( IOException e ) {
			e.printStackTrace();
		} catch ( InterruptedException e ) {
			e.printStackTrace();
		}
		return null;
	}

	/**
	 * Computes the embedding of a Literal term
	 * @param term the Literal to embed
	 * @return a list of double (the embedding vector). The size depends on the model used.
	 */
	public List<Double> embed( Literal term ) {
        // Note: 'replace' replaces all the occurrences. 'replaceAll' takes a regex as first arg

		// Get the functor
        String functor = term.getFunctor().replace( "_", " " ).replace( "my", "your" ) + " ";
        String terms = "";
		// If the term does not have nested terms return it repeated 4 times (weighted)
        if ( !term.hasTerm() )
            return embed( functor.repeat( 4 ) );
		// Preprocess the term arguments
        for ( Term t : term.getTerms() ) {
            String tStr = t.toString();
            tStr = tStr.replace( "_", " ");
            tStr = tStr.replace( "(", " ( " );
            tStr = tStr.replace( ")", " ) " );
            tStr = tStr.replace( ",", " , " );
            tStr = tStr.replace( "my", "your" );
            tStr = tStr.replaceAll( "([=<>!]+)", " $1 " );
            tStr = tStr.replaceAll( "\\s+", " " );
            tStr = tStr.trim();
            terms += tStr + " ";
        }
		// repeat the functor 4 times and append the arguments
        String processedTerm = functor.repeat( 4 ) + terms.trim();
		// return the emnbedding
		return embed( processedTerm );
	}

	/**
	 * This function calls the GENERATE API for the model with input str
	 * @param model the model to use
	 * @param str the input message
	 * @return the body of the answer
	 */
	private String generate( String model, String str ) {
		JSONObject json = new JSONObject();
		json.put( "model", model );
		json.put( "prompt", str );
		json.put( "stream", false );

		HttpRequest req = HttpRequest.newBuilder()
			.uri( URI.create( URL + "generate" ) )
			.header( "Content-Type", "application/json" )
			.POST( HttpRequest.BodyPublishers.ofString( json.toString() ) )
			.build();

		try {
			HttpResponse<String> res = client.send( req, HttpResponse.BodyHandlers.ofString() );
			return res.body();
		} catch( Exception e ) {
			e.printStackTrace();
		}
		return null;
	}

	/**
	 * This function classifies a message Illocutionary Force
	 * @param msg the input message
	 * @return the Literal correspondent to the Illocutionary Force
	 */
	public Literal classify( String msg ) {
		// Build a JSON Schema with the available Illocutionary Forces
        JSONObject ilf = new JSONObject();
        ilf.put( "type", "string" );
        ilf.put( "enum", SUPPORTED_ILF );
        JSONObject properties = new JSONObject();
        properties.put( "Illocutionary Force", ilf );
        JSONObject json = new JSONObject();
        json.put( "type", "object" );
        json.put( "properties", properties );
        json.put( "required", new JSONArray().put( "Illocutionary Force" ) );

		// Generate the answer and load it inside a JSON Object
        JSONObject ans = new JSONObject( generate( CLASS_MODEL, msg, json ) );
		// Postprocess
        String ilfObjStr = ans.getString( "response" )
            .replaceAll( "```json\\s*", "" )
            .replaceAll( "```\\s*$", "" )
            .trim();
        JSONObject ilfObj = new JSONObject( ilfObjStr );
		// Return the literal
        return createLiteral( ilfObj.getString( "Illocutionary Force" ).trim() );
	}

	/**
	 * This function translates a user message to a Literal
	 * To get better results the terms are translated in Json, for example:
	 * p(a, b, 1) -> { "functor": p, "arg0": "a", "arg1": "b", "arg2": 1 }
	 * @param msg the input message
	 * @param nearest the nearest term in the embedding space
	 * @param ilf the classified Illocutionary Force
	 * @param examples a list of all the terms with same functor and arity of the nearest
	 * @throws IOException if fails reading the NL2LOG_PROMPT file
	 * @throws ParseException if the provided answer is not a valid Jason term
	 */
	public Literal generate( String msg, Literal nearest, Literal ilf, List<Literal> examples ) throws IOException, ParseException {

        List<JSONObject> jsonExamples = new ArrayList<>();
		// Translate the term in JSON
        JSONObject nearestJson = Tools.termToJSON( nearest );
		// Translate all the examples
        for ( Literal example : examples )
            jsonExamples.add( Tools.termToJSON( example ) );
		// Generate a schema with types provided in the examples for each arg
        JSONObject schema = Tools.genJSONSchema( jsonExamples );
		// Read the prompt and replace needed placeholders
        String prompt = Files.readString( Path.of( NL2LOG_PROMPT ) )
            .replace( "SENTENCE", msg )
            .replace( "NEAREST_JSON", nearestJson.toString() )
            .replace( "ILF", ilf.toString() )
            .replace( "EXAMPLES", jsonExamples.toString() );
		// List variable names: they may have meaningful names
        List<Set<Term>> varNames = Tools.getVarNames( examples );
        for ( int i = 0; i < varNames.size(); i++ )
            if ( !varNames.get( i ).isEmpty() )
                prompt += " - arg" + i + " should contain " + varNames.get( i ) +
                "; if this piece of information is in the sentence place it here, otherwise place underscore or null";
        
		// Generate the new term
        JSONObject answer = new JSONObject( generate( GEN_MODEL, prompt, schema ) );
        JSONObject response = new JSONObject( answer.getString( "response" ) );
        return Tools.jsonToTerm( response );
	}

	/**
	 * This function translates a Jason message in Natural Language
	 * @param msg the KQML message
	 * @return the natural language translation
	 * @throws IOException if fails to open LOG2NL_PROMPT
	 */
	public String generate( Message msg ) throws IOException {
		String prompt = Files.readString( Path.of( LOG2NL_PROMPT ) )
			.replace( "SENDER", msg.getSender() )
			.replace( "ILFORCE", msg.getIlForce() )
			.replace( "CONTENT", msg.getPropCont().toString() );
		JSONObject answer = new JSONObject( generate(LOG2NL_MODEL, prompt) );
		return answer.getString( "response" ).replaceAll( "(?s)<think>.*?</think>", "" );
	}

	/**
	 * This function translates a message using model and following format
	 * @param model the model to use
	 * @param str the input message
	 * @param format the Json Schema for the output
	 * @throws IOException if the client fails to send the message
	 */
	private String generate( String model, String str, JSONObject format ) {
		JSONObject json = new JSONObject();
		json.put( "model", model );
		json.put( "prompt", str );
		json.put( "format", format );
		json.put( "stream", false );

		HttpRequest req = HttpRequest.newBuilder()
			.uri( URI.create( URL + "generate" ) )
			.header( "Content-Type", "application/json" )
			.POST( HttpRequest.BodyPublishers.ofString( json.toString() ) )
			.build();

		try {
			HttpResponse<String> res = client.send( req, HttpResponse.BodyHandlers.ofString() );
			return res.body();
		} catch( IOException e ) {
			e.printStackTrace();
		} catch ( InterruptedException e ) {
			e.printStackTrace();
		}
		return null;
	}

	/**
	 * Creates a prompted model
	 * @param from the starting model
	 * @param model the final model name
	 * @param t the temperature
	 * @param sys_file the path to a system file
	 */
	public void create( String from, String model, float t, String sys_file ) {
		JSONObject params = new JSONObject();
		params.put( "temperature", t );
		JSONObject json = new JSONObject();
		json.put( "from", from );
		json.put( "model", model );
		json.put( "stream", false );
		json.put( "parameters", params );
		try {
			json.put( "system", Files.readString( Path.of(sys_file ) ) );
			HttpRequest request = HttpRequest.newBuilder()
				.uri( URI.create( URL + "create" ) )
				.header( "Content-Type", "application/json" )
				.POST( HttpRequest.BodyPublishers.ofString( json.toString() ) )
				.build();

			HttpResponse<String> httpResponse = client.send(request, HttpResponse.BodyHandlers.ofString());
		} catch ( IOException e ) {
			e.printStackTrace();
		} catch ( InterruptedException e ) {
			e.printStackTrace();
		}
	}

	/**
	 * Creates a prompted model
	 * @param from the starting model
	 * @param model the final model name
	 * @param t the temperature
	 * @param sys_file the path to a system file
	 * @param seed the seed for generation
	 */
	public void create( String from, String model, float t, String sys_file, int seed ) {
		JSONObject params = new JSONObject();
		params.put( "temperature", t );
		params.put( "seed", seed );
		JSONObject json = new JSONObject();
		json.put( "from", from );
		json.put( "model", model );
		json.put( "stream", false );
		json.put( "parameters", params );
		try {
			json.put( "system", Files.readString( Path.of(sys_file ) ) );
			HttpRequest request = HttpRequest.newBuilder()
				.uri( URI.create( URL + "create" ) )
				.header( "Content-Type", "application/json" )
				.POST( HttpRequest.BodyPublishers.ofString( json.toString() ) )
				.build();

			HttpResponse<String> httpResponse = client.send(request, HttpResponse.BodyHandlers.ofString());
		} catch ( IOException e ) {
			e.printStackTrace();
		} catch ( InterruptedException e ) {
			e.printStackTrace();
		}
	}
}
