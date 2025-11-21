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
import static jason.asSyntax.ASSyntax.*;

import java.io.IOException;
import jason.asSyntax.parser.ParseException;

public class Ollama {
    private final String URL = "http://localhost:11434/api/";
    // Starting models
    protected final String EMB_MODEL = "all-minilm";
    protected final String GEN_MODEL = "qwen2.5-coder";
    // Specialized Model names
    private final String LOG2NL_MODEL = "logic-to-nl";
    private final String NL2LOG_MODEL = "nl-to-logic";
    private final String CLASS_MODEL = "classify-ilf";
    // Settings
    private final float TEMPERATURE = 0.0f;
    private final int SEED = 42;
    // Specialized prompts
    private final String NL2LOG_PROMPT = "src/agt/interpreter/modelfiles/nl2logPrompt.txt";
    private final String NL2LOG_MODELFILE = "src/agt/interpreter/modelfiles/nl2log.txt";
    private final String LOG2NL_MODELFILE = "src/agt/interpreter/modelfiles/log2nl.txt";
    private final String CLASS_MODELFILE = "src/agt/interpreter/modelfiles/classifier.txt";
	private final String[] SUPPORTED_ILF;

	private final HttpClient client = HttpClient.newHttpClient();

	public Ollama( String[] supportedIlfs ) throws ConnectException {
		SUPPORTED_ILF = new String[ supportedIlfs.length ];
		for ( int i = 0; i < supportedIlfs.length; i++ )
			SUPPORTED_ILF[i] = supportedIlfs[i];
		if ( !is_online() ) {
			throw new ConnectException( "The Ollama Server is offline or the address is not correct." );
		}
		System.out.println( "Initializing generation models" );
		create( GEN_MODEL, NL2LOG_MODEL, TEMPERATURE, NL2LOG_MODELFILE, SEED );
		create( GEN_MODEL, CLASS_MODEL, TEMPERATURE, LOG2NL_MODELFILE, SEED );
		create( GEN_MODEL, LOG2NL_MODEL, TEMPERATURE, CLASS_MODELFILE, SEED );
	}

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
		} catch( Exception e ) {
			e.printStackTrace();
		}
		return false;
	}

	public List<Double> embed( String str ) {
		JSONObject json = new JSONObject();
		json.put( "model", EMB_MODEL );
		json.put( "input", str );
		json.put( "stream", false );

		HttpRequest req = HttpRequest.newBuilder()
			.uri( URI.create( URL + "embed" ) )
			.header( "Content-Type", "application/json" )
			.POST( HttpRequest.BodyPublishers.ofString( json.toString() ) )
			.build();

		try {
			HttpResponse<String> res = client.send( req, HttpResponse.BodyHandlers.ofString() );
			JSONObject emb_json = new JSONObject( res.body() );
			JSONArray vec = emb_json.getJSONArray( "embeddings" ).getJSONArray(0);
			System.out.println( "Embedding generated: " + vec );
			List<Double> list = new ArrayList<>();
			for( int i = 0; i < vec.length(); i++ ) {
				list.add( vec.getDouble( i ) );
			}
			return list;
		} catch( Exception e ) {
			e.printStackTrace();
		}
		return null;
	}

	public List<Double> embed( Literal term ) {
        // Note: 'replace' replaces all the occurrences. 'replaceAll' takes a regex as first arg
        String functor = term.getFunctor().replace( "_", " " ).replace( "my", "your" ) + " ";
        String terms = "";
        if ( !term.hasTerm() )
            return embed( functor.repeat( 4 ) );
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
        String processedTerm = functor.repeat( 4 ) + terms.trim();
		return embed( processedTerm );
	}

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

	public Literal classify( String msg ) {
        JSONObject ilf = new JSONObject();
        ilf.put( "type", "string" );
        ilf.put( "enum", SUPPORTED_ILF );
        JSONObject properties = new JSONObject();
        properties.put( "Illocutionary Force", ilf );
        JSONObject json = new JSONObject();
        json.put( "type", "object" );
        json.put( "properties", properties );
        json.put( "required", new JSONArray().put( "Illocutionary Force" ) );
        JSONObject ans = new JSONObject( generate( CLASS_MODEL, msg, json ) );
        String ilfObjStr = ans.getString( "response" )
            .replaceAll( "```json\\s*", "" )
            .replaceAll( "```\\s*$", "" )
            .trim();
        JSONObject ilfObj = new JSONObject( ilfObjStr );
        return createLiteral( ilfObj.getString( "Illocutionary Force" ).trim() );
	}

	public Literal generate( String msg, Literal nearest, Literal ilf, List<Literal> examples ) throws IOException, ParseException {
        List<JSONObject> jsonExamples = new ArrayList<>();
        JSONObject nearestJson = Tools.termToJSON( nearest );
        for ( Literal example : examples )
            jsonExamples.add( Tools.termToJSON( example ) );
        JSONObject schema = Tools.genJSONSchema( jsonExamples );
        String prompt = Files.readString( Path.of( NL2LOG_PROMPT ) )
            .replace( "SENTENCE", msg )
            .replace( "NEAREST_JSON", nearestJson.toString() )
            .replace( "ILF", ilf.toString() )
            .replace( "EXAMPLES", jsonExamples.toString() );
        List<Set<Term>> varNames = Tools.getVarNames( examples );
        for ( int i = 0; i < varNames.size(); i++ )
            if ( !varNames.get( i ).isEmpty() )
                prompt += " - arg" + i + " should contain " + varNames.get( i ) +
                "; if this piece of information is in the sentence place it here, otherwise place underscore or null";
        
        JSONObject answer = new JSONObject( generate( GEN_MODEL, prompt, schema ) );
        JSONObject response = new JSONObject( answer.getString( "response" ) );
        return Tools.jsonToTerm( response );
	}

	private String generate( String model, String str, JSONObject format ) {
		JSONObject json = new JSONObject();
		json.put( "model", model );
		json.put( "prompt", str );
		json.put( "format", format );
		json.put( "stream", false );

		System.out.println( json.toString() );

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
		} catch ( Exception e ) {
			e.printStackTrace();
		}
	}

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
		} catch ( Exception e ) {
			e.printStackTrace();
		}
	}
}
