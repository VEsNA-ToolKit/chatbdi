package interpreter;

import cartago.*;
import jason.asSyntax.*;
import static jason.asSyntax.ASSyntax.*;

import java.io.FileWriter;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.ConnectException;

import org.json.JSONObject;
import org.json.JSONArray;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;

public class LLMWithEmbeddingsInterpreter extends Artifact implements Interpreter{

    private final String OLLAMA_URL = "http://localhost:11434/api/";
    private final HttpClient client = HttpClient.newHttpClient();
    private final String EMBEDDING_MODEL = "nomic-embed-text";
    private final String FROM_MODEL = "codegemma";
    private final String LOGIC_TO_NL_MODEL = "logic-to-nl";
    private final String NL_TO_LOGIC_MODEL = "nl-to-logic";
    private final String CLASSIFICATION_MODEL = "classify-performative"; ////////<------init_generation_models
    private final float TEMPERATURE = 0.2f;
    private final String DEBUG_LOG = "interpreter.log";

    // The map of embeddings with:
    // - a literal as key
    // - the corresponding embedding as value
    private Map<Literal, List<Double>> embeddings;

    // 1. initializes the embeddings of each agent beliefs;
    // 2. creates the two generation models.
    //! init cannot signal because it is called before the agent focus on the artifact

    //done
    void init( Object[] literals ) {
        if ( !check_ollama() ) {
            log( "The ollama server is not running! Please start it and try again." );
            defineObsProperty( "running", false );
            return;
        }
        log( "Initializing Ollama models" );
        init_embeddings( literals );
        init_generation_models();
        defineObsProperty( "running", true );
    }


    //done
    @OPERATION
    public void generate_property( String sentence, OpFeedbackParam<Literal> property ) {
        // Remove all the mentions from the string
        sentence = sentence.replaceAll("\\s*@\\S+", "");
        // Check some specific simple cases
        sentence = sentence.toLowerCase();
        if (sentence.contains("which") && sentence.contains("available") ){
            if (sentence.contains("agents"))
                property.set( createLiteral( "which_available_agents" ) );
            else if ( sentence.contains("plans") )
                property.set( createLiteral( "which_are_your_available_plans" ) );
            return;
        }
        if ( sentence.contains("describe") && sentence.contains("plan") ){
            property.set( createLiteral( "describe_plan" ) );
            return;
        }
        // Compute the embedding of the message
        List<Double> embedding = compute_embedding( sentence );
        // Find the literal with the closer embedding
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
        log( "Best fitting embedding is " + best_literal );
        // If the literal is some of these cases that are ground terms of the interpreter we can directly return
        // if ( best_literal.equals( ASSyntax.createLiteral( "which_available_agents" ) ) ||
        //         best_literal.equals( ASSyntax.createLiteral( "which_are_your_available_plans" ) ) ||
        //         best_literal.equals( ASSyntax.createLiteral( "describe_plan" ) )
        // ) {
        //     property.set( best_literal );
        //     return;
        // }
        if ( best_literal.isGround() ){
            property.set( best_literal );
            return;
        }
        // Modify the closest property with the values provided in the sentence
        Literal new_property = generate_literal( best_literal, sentence.toLowerCase() );
        log( "Generated property: " + new_property );
        property.set( new_property );
    }


    //done
    @OPERATION
    public void generate_sentence( String performative, String literal_str, OpFeedbackParam<String> sentence ) {
        // Generate a sentence starting from the literal
        sentence.set( generate_string( createLiteral(literal_str) ) );
    }
    
    //done
    // init_embeddings takes all the literals from the agents and computes for each literal the embedding
    private void init_embeddings( Object[] literals ){
        log( "Initializing embeddings;" );
        // Initialize embeddings hashmap
        embeddings = new HashMap<>();
        for ( Object o_literal : literals ) {
            try {
                Literal literal = parseLiteral( (String) o_literal );
                // Add the embedding only if it is not already present
                if ( embeddings.get( literal ) == null ){
                    List<Double> embedding = compute_embedding( literal.toString() );
                    embeddings.put( literal, embedding );
                }
            } catch ( Exception e ) {
            }
        }
    }


    //done ->uguale a init_embeddings ma anzichè inizializzarli, aggiorna con nuovi
    // This function updates the embeddings with new beliefs gained by agents
    @OPERATION
    public void update_embeddings( Object[] literals ) {
        log( "Updating embeddings" );
        for (Object o_literal : literals ){
            try {
                Literal literal = parseLiteral( (String) o_literal );
                if ( embeddings.get( literal ) == null ){
                    List<Double> embedding = compute_embedding( literal.toString() );
                    embeddings.put( literal, embedding );
                }
            } catch ( Exception e ) {
            }
        }
    }

    // This function creates the two models needed
    //done
    private void init_generation_models() {
        log( "Initializing generations models;");
        JSONObject nl_to_logic_model = get_nl_to_logic_model();
        JSONObject logic_to_nl_model = get_logic_to_nl_model();
        JSONObject classify_model = get_classify_model();

        create_model( nl_to_logic_model );
        create_model( logic_to_nl_model );
        create_model( classify_model );
    }

    // Given the modelfile as JSON it sends the request to the Ollama API
    //done
    private void create_model( JSONObject modelfile ){
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri( URI.create( OLLAMA_URL + "create" ) )
                    .header( "Content-Type", "application/json" )
                    .POST( HttpRequest.BodyPublishers.ofString( modelfile.toString() ) )
                    .build();

            HttpResponse<String> httpResponse = client.send(request, HttpResponse.BodyHandlers.ofString());
        } catch ( Exception e ) {
            failed( "Error calling OLLAMA API: " + e.getMessage() );
        }
    }


    //done
    private JSONObject get_nl_to_logic_model() {
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

            Logical property: which_available_agents
            Sentence: Which are the agents available in this project?
            Answer: which_available_agents
        """;

        JSONObject json = new JSONObject();
        json.put( "model", NL_TO_LOGIC_MODEL ); //json.put("chiave","valore")
        json.put( "from", FROM_MODEL );
        json.put( "system", system );
        JSONObject parameters = new JSONObject( );
        parameters.put( "temperature", TEMPERATURE );
        parameters.put( "penalize_newline", true );
        json.put( "parameters", parameters );
        json.put( "stream", false );

        return json;
    }




////////------------------------------------------------------------------------


//1
private JSONObject get_classify_model(){
    
    String system = """
      You are a logician who works with Prolog. You will receive a sentence.
        Your task is to classify this sentence based on its content.
        The sentence can be classified as six different types: tell, achieve, tellHow, askOne, askAll and askHow

        Sentence will be classified as "tell" if it contains knowledge or information that is communicated.

        Sentence will be classified as "achieve" if it contains the giving of an order or the request to achieve a goal.

        Sentence will be classified as "tellHow" if it contains an explanation of how to carry out a task or action.

        Sentence will be classified as "askOne" if it contains a request to provide a specific information or a specific knowledge
        contained within the set of information involved.

        Sentence will be classified as "askAll" if it contains a request to provide all information or all knowledge
        contained within the set of information involved.

        Sentence will be classified as "askHow" if it contains a request to provide an explanation of how to do a
        specific task.

        Your task, therefore, is to respond with one of the six types of sentence that you think fits better

        Here a few Examples:

        Sentence: I ordered sushi at 14:00.
        Explanation: I'm reporting that I ordered sushi, that is, information
        Answer: tell

        Sentence: It will rain tomorrow.
        Explanation: I'm saying it will rain tomorrow, that is, information
        Answer: tell

        Sentence: Bring me a pen.
        Explanation: I'm ordering you to bring me a pen.
        Answer: achieve

        Sentence: Could you bring me a pen?
        Explanation: I'm asking you to bring me a pen, that is, to achieve a goal
        Answer: achieve

        Sentence: To bring me a pen, you must go to my desk, pick it, come back to me, leave the pen to me
        Explanation: I'm explaining how to do the task to you of bringing me a pen. In other words, I'm telling you a procedure consisting of a sequence of actions.
        Answer: tellHow

        Sentence: Can you tell me a commitment for tomorrow?
        Explanation: I'm asking you to tell a single commitment present in the set of commitments you know.
        Answer: askOne

        Sentence: Tell me any type of vegetable present in the kitchen
        Explanation: I'm asking you to tell a single type of vegetable in the set of vegetables you know.
        Answer: askOne

        Sentence: Can you tell me all commitments for tomorrow?
        Explanation: I'm asking you to tell all commitment present in the set of commitments you know.
        Answer: askAll

        Sentence: Tell me all types of vegetable present in the kitchen
        Explanation: I'm asking you to tell all types of vegetable in the set of vegetables you know.
        Answer: askAll

        Sentence: Can you show me how to play this videogame?
        Explanation: I'm asking an explanation of which actions I need to perform to achieve the goal of playing videogame
        Answer: askHow

        Sentence: Can you tell me the procedure for cooking a nice pizza?
        Explanation: I'm asking an explanation of which actions I need to perform to achieve the goal of cooking pizza
        Answer: askHow

        """;
    

     JSONObject json = new JSONObject();
        json.put( "model", CLASSIFICATION_MODEL); 
        json.put( "from", FROM_MODEL );
        json.put( "system", system );
        JSONObject parameters = new JSONObject( );
        parameters.put( "temperature", TEMPERATURE );
        parameters.put( "penalize_newline", true );
        json.put( "parameters", parameters );
        json.put( "stream", false );

        return json;


}

    //clasify_perf

    @OPERATION 
    public void classify_performative( String sentence, OpFeedbackParam<String>performative_type ){
           
            sentence = sentence.replaceAll("\\s*@\\S+", "");
            sentence = sentence.toLowerCase();

            String body = send_ollama( "generate", CLASSIFICATION_MODEL, sentence );
            performative_type.set( ASSyntax.createLiteral(body).toString());

    }



////////------------------------------------------------------------------------
    //done
    private JSONObject get_logic_to_nl_model() {
        String system = """
            You will receive a logical property and you will generate a sentence.
            You will tell the content of the property to me as if we are speaking and the content is something about you, so you should include all the information in the sentence and be conversational.
            For example the logical property myname(alice) should be translated to "My name is Alice".
            Another example: hasColor(apple, red) should be translated to "The apple is red".
            Another example: meeting(tomorrow, room(102), [alice, bob, charles]) should be translated to "Tomorrow there is a meeting in room 102 with alice, bob and charles."
        """;

        JSONObject json = new JSONObject();
        json.put( "model", LOGIC_TO_NL_MODEL );
        json.put( "from", FROM_MODEL );
        json.put( "system", system );
        JSONObject parameters = new JSONObject( );
        parameters.put( "temperature", TEMPERATURE );
        parameters.put( "penalize_newline", true );
        json.put( "parameters", parameters );
        json.put( "stream", false );

        return json;
    }


    //done 
    private String send_ollama( String type, String model, String input ) {
        try {
            JSONObject json = new JSONObject();
            json.put( "model", model );
            if ( type.equals( "embed" ) )
                json.put( "input", input );
            else if ( type.equals( "generate" ) )
                json.put( "prompt", input );
            json.put( "stream", false );

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

    //done
    private boolean check_ollama( ) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri( URI.create( OLLAMA_URL.replaceAll( "/api/", "") ) )
                .header( "Content-Type", "application/json" )
                .GET()
                .build();

            HttpResponse<String> httpResponse = client.send(request, HttpResponse.BodyHandlers.ofString());
            // return status.getBoolean("status");
            return httpResponse.statusCode() == 200;
        }catch ( ConnectException e ){
            return false;
        } catch ( Exception e ) {
            failed( "Error calling OLLAMA API: " + e.getMessage() );
        }
        return false;
    }

    //done -> basta sapere che il metodo restituisce l'embedding (vettore numerico)
    private List<Double> compute_embedding( String literal ) {
        List<Double> embedding = new ArrayList<>();

        literal = literal.replaceAll( "_", " " );
        literal = literal.replaceAll( "\\(", " " );
        literal = literal.replaceAll( "\\)", " " );
        literal = literal.replaceAll( "my", "your" );

        String body = send_ollama( "embed", EMBEDDING_MODEL, literal );

        JSONObject embedding_json = new JSONObject( body );
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
        String new_property = generate_json.getString("response");
        return ASSyntax.createLiteral( new_property );

    }


    //done
    private String generate_string( Literal literal ) {
        String prompt = String.format( "Generate a sentence describing this logical property: %s.", literal.toString() );
        String body = send_ollama("generate", LOGIC_TO_NL_MODEL, prompt );
        assert body != null;
        JSONObject generate_json = new JSONObject( body );
        return generate_json.getString("response");
    }

    //done -> basta sapere che calcola la distanza tra due vettori due vettori
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

}
