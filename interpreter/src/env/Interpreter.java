import cartago.*;
import jason.asSyntax.*;

import java.io.FileWriter;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.json.JSONObject;
import org.json.JSONArray;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;

public class Interpreter extends Artifact{

    private final String OLLAMA_URL = "http://localhost:11434/api/";
    private final HttpClient client = HttpClient.newHttpClient();
    private final String EMBEDDING_MODEL = "nomic-embed-text";
    private final String FROM_MODEL = "llama3.1";
    private final String LOGIC_TO_NL_MODEL = "logic-to-nl";
    private final String NL_TO_LOGIC_MODEL = "nl-to-logic";
    private final float TEMPERATURE = 0.2f;
    private final String DEBUG_LOG = "interpreter.log";

    private Map<Literal, List<Double>> embeddings;

    @OPERATION
    public void init_ollama( Object[] literals ) {
        init_embeddings(literals);
        init_generate_model();
    }

    private void init_embeddings( Object[] literals ){
        log( "Initializing embeddings" );
        embeddings = new HashMap<>();
        for ( Object o_literal : literals ) {
            String literal = ( String ) o_literal;
            List<Double> embedding = compute_embedding( literal );
            embeddings.put( ASSyntax.createLiteral( literal ), embedding );
        }
    }

    private void init_generate_model() {
        String system = """
            You are a logician who works with Prolog. You will receive a logical property and a sentence.
            Modify the logical property according to the sentence and answer with the modified logical property.
            If an information is not contained in the sentence, place an underscore in the place of the value or the variable.
            The underscore must be not be surrounded by quotes, it should be _ and not "_".
            Remember that words that starts with a capital letter are variables and words that starts with a lowercase letter are values.
            Examples:
            Logical property: hasColor(apple, red)
            Sentence: The apple is green.
            Answer: hasColor(apple, green)

            Logical property: order(pizza, "1/1/1999", 12)
            Sentence: I ordered a sushi at 14:00.
            Answer: order(sushi, _, 14)
        """;

        JSONObject json = new JSONObject();
        json.put( "model", NL_TO_LOGIC_MODEL);
        json.put( "from", FROM_MODEL );
        json.put( "system", system );
        JSONObject parameters = new JSONObject( );
        parameters.put( "temperature", TEMPERATURE );
        parameters.put( "penalize_newline", true );
        json.put( "parameters", parameters );
        json.put( "stream", false );

        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri( URI.create( OLLAMA_URL + "create" ) )
                .header( "Content-Type", "application/json" )
                .POST( HttpRequest.BodyPublishers.ofString( json.toString() ) )
                .build();

            HttpResponse<String> httpResponse = client.send(request, HttpResponse.BodyHandlers.ofString());
        } catch ( Exception e ) {
            failed( "Error calling OLLAMA API: " + e.getMessage() );
        }
    }

    @OPERATION
    public void generate_property( String sentence, OpFeedbackParam<Literal> property ) {
        List<Double> embedding = compute_embedding( sentence );
        Literal best_literal = null;
        double best_distance = Double.MAX_VALUE;
        for ( Literal literal : embeddings.keySet() ) {
            List<Double> literal_embedding = embeddings.get( literal );
            double distance = cosine_similarity( embedding, literal_embedding );
            if ( distance < best_distance ) {
                best_distance = distance;
                best_literal = literal;
            }
        }

        Literal new_property = generate_literal(best_literal, sentence);
        property.set( new_property );
    }

    private String send_ollama( String type, String model, String input ) {
        try {
            JSONObject json = new JSONObject();
            json.put( "model", model );
            if ( type.equals( "embed" ) )
                json.put( "input", input );
            else if ( type.equals( "generate" ) )
                json.put( "prompt", input );
            log( "JSON: " + json.toString() );

            HttpRequest request = HttpRequest.newBuilder()
                .uri( URI.create( OLLAMA_URL + type ) )
                .header( "Content-Type", "application/json" )
                .POST( HttpRequest.BodyPublishers.ofString( json.toString() ) )
                .build();

            HttpResponse<String> httpResponse = client.send(request, HttpResponse.BodyHandlers.ofString());

            return httpResponse.body();
        } catch ( Exception e ) {
            failed( "Error calling OLLAMA API: " + e.getMessage() );
        }
        return null;
    }

    private List<Double> compute_embedding( String literal ) {
        List<Double> embedding = new ArrayList<>();

        String body = send_ollama( "embed", EMBEDDING_MODEL, literal );

        JSONObject embedding_json = new JSONObject( body );
        log(body);
        JSONArray embedding_array = embedding_json.getJSONArray("embeddings").getJSONArray(0);
        for ( int i = 0; i < embedding_array.length(); i++ ) {
            embedding.add( embedding_array.getDouble(i) );
        }

        return embedding;
    }

    private Literal generate_literal( Literal literal, String sentence ) {

        String prompt = String.format("""
            Modify this logical property %s according to this sentence "%s". Answer only with the modified logical property in plain text. If an information is not contained in the sentence, place an underscore in the place of the value.
            """, literal, sentence);

        String body = send_ollama( "generate", NL_TO_LOGIC_MODEL, prompt );

        assert body != null;
        JSONObject generate_json = new JSONObject( body );
        log(generate_json.toString());
        String new_property = generate_json.getString("response");
        return ASSyntax.createLiteral( new_property );

    }

    private String generate_string( Literal literal ) {
        String prompt = String.format( "Generate a sentence describing this logical property: %s.", literal.toString() );
        String body = send_ollama("generate", LOGIC_TO_NL_MODEL, prompt );
        assert body != null;
        JSONObject generate_json = new JSONObject( body );
        return generate_json.getString("response");
    }

    private double cosine_similarity( List<Double> embedding1, List<Double> embedding2 ) {
        if (embedding1 == null || embedding2 == null)
            failed( "One of the embeddings is null" );
        assert embedding1 != null && embedding2 != null;
        if ( embedding1.size() != embedding2.size() )
            failed( "Embeddings have different sizes" );
        
        double dotProd = 0.0;
        double norm1 = 0.0;
        double norm2 = 0.0;

        for ( int i=0; i<embedding1.size(); i++ ) {
            dotProd += embedding1.get(i) * embedding2.get(i);
            norm1 += embedding1.get(i) * embedding1.get(i);
            norm2 += embedding2.get(i) * embedding2.get(i);
        }

        norm1 = Math.sqrt(norm1);
        norm2 = Math.sqrt(norm2);

        if ( norm1 == 0 || norm2 == 0 )
            failed("Embedding norm cannot be ZERO");

        return 1.0 - (dotProd / ( norm1 * norm2 ) );
    }

    private void write_log( String msg ) throws IOException {
        FileWriter writer = new FileWriter( DEBUG_LOG, true );
        writer.write( msg );
        writer.close();
    }

}
