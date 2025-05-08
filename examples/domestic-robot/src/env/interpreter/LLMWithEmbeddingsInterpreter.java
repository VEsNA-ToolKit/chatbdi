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

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.HashSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.regex.*;

import java.util.stream.Collectors;

public class LLMWithEmbeddingsInterpreter extends Artifact implements Interpreter{

    private final String OLLAMA_URL = "http://localhost:11434/api/";
    private final HttpClient client = HttpClient.newHttpClient();
    private final String EMBEDDING_MODEL = "nomic-embed-text";
    private final String FROM_MODEL = "codegemma";
    private final String LOGIC_TO_NL_MODEL = "logic-to-nl";
    private final String NL_TO_LOGIC_MODEL = "nl-to-logic";
    private final String CLASSIFICATION_MODEL = "classify-performative";
    private final float TEMPERATURE = 0.2f;
    private final String DEBUG_LOG = "interpreter.log";
    private final float THRESHOLD = 1.0f;

    // The map of embeddings with:
    // - a literal as key
    // - the corresponding embedding as value
    private Map<Literal, List<Double>> embeddings;
    // 1. initializes the embeddings of each agent beliefs;
    // 2. creates the two generation models.
    //! init cannot signal because it is called before the agent focus on the artifact

    private Map<String, List<Literal>> ag_literals;

    void init( Object[] agentsList,  Object[] literalsList, Object[] beliefsList,  Object[] plansList ) {

        if ( !check_ollama() ) {
            log( "The ollama server is not running! Please start it and try again." );
            defineObsProperty( "running", false );
            return;
        }

        log( "Initializing Ollama models" );
        Literal[] allLiterals = buildEmbeddingsList(literalsList, beliefsList, plansList);
        ag_literals = build_personal_literals_dict(agentsList, literalsList, beliefsList, plansList);
      
        init_embeddings( allLiterals );
        init_generation_models();
        defineObsProperty( "running", true );  
        
    }


    //this method takes all literals, beliefs, plans contained in nested lists and returns a plain list with all literals
    public static Literal[] buildEmbeddingsList(Object[] literalsList, Object[] beliefsList,  Object[] plansList ) {

        // List<Object> list_embeddings = new LinkedList<>();
        Set<Literal> embeddings_set = new HashSet<>();
        embeddings_set.addAll( obj_matrix_to_literal_set( literalsList ) );
        embeddings_set.addAll( obj_matrix_to_literal_set( beliefsList ) );
        embeddings_set.addAll( obj_matrix_to_literal_set( plansList ) );

        return embeddings_set.toArray( new Literal[0] );

    }

    private static Set<Literal> obj_matrix_to_literal_set( Object[] list ) {
        return Arrays.stream( list ).flatMap( array -> Arrays.stream( ( Object[] ) array )
                .filter( String.class::isInstance )
                .map( String.class::cast )
                .map( str -> str.replaceFirst( "^[-+!]+", "" ) )
                .map( Literal::parseLiteral ) )
            .collect( Collectors.toSet() );
    }

    private static List<Literal> obj_list_to_literal_list( Object[] list ) {
        return Arrays.stream( list ).filter( String.class::isInstance )
            .map( String.class::cast )
            .map( str -> str.replaceFirst( "^[-+!]+", "" ) )
            .map( Literal::parseLiteral )
            .collect( Collectors.toList() );
    }

    private static Set<Literal> obj_list_to_literal_set( Object[] list ) {
        return Arrays.stream( list ).filter( String.class::isInstance )
            .map( String.class::cast )
            .map( str -> str.replaceFirst( "^[-+!]+", "" ) )
            .map( Literal::parseLiteral )
            .collect( Collectors.toSet() );
    }

    //method where ag_literals dictionary is initialized
    public Map<String, List<Literal>> build_personal_literals_dict(Object[] agentsList,  Object[] literalsList, Object[] beliefsList,  Object[] plansList ){
        
        HashMap<String, List<Literal>> ag_literals = new HashMap<>();
        List<String> ag_names = Arrays.stream( agentsList ).map( obj -> ( String ) obj ).collect( Collectors.toList() );
        ag_names.remove( "user" );

        for( int i = 0; i < ag_names.size() ; i++ ) {
            String ag_name = ag_names.get( i );
            Set<Literal> literals = new HashSet<>();
            literals.addAll( obj_list_to_literal_set( ( Object[] ) literalsList[ i ] ) );
            literals.addAll( obj_list_to_literal_set( ( Object[] ) beliefsList[ i ] ) );
            literals.addAll( obj_list_to_literal_set( ( Object[] ) plansList[ i ] ) );
            ag_literals.put( ag_name, new ArrayList<>( literals ) );
        }

        return ag_literals;

    }

    @OPERATION
    public void generate_property( String sentence, OpFeedbackParam<Literal> property ) {

        String recipient = null;
        if (sentence.startsWith("@")) {
            recipient = sentence.split(" ")[0].substring(1); // remove '@'
            System.out.println("Recipient " + recipient);
        }

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


        //if there is no recipient or recipient isn't the name of an agent, compute the best fitting embedding on all literals
        if ( recipient == null || ! ag_literals.containsKey( recipient ) ) {
            for ( Literal literal : embeddings.keySet() ) {
                List<Double> literal_embedding = embeddings.get( literal );
                double distance = cosine_similarity( embedding, literal_embedding );
                if ( distance < best_distance ) {
                    System.out.println( literal );
                    best_distance = distance;
                    best_literal = literal;
                }
            }
        } else {
            for ( Literal literal : ag_literals.get(recipient) ) {
                List<Double> literal_embedding = embeddings.get( literal );
                double distance = cosine_similarity( embedding, literal_embedding );
                if ( distance < best_distance ) {
                    best_distance = distance;
                    best_literal = literal;
                }
            }

        }

        if ( best_distance > THRESHOLD ) {
            try {
                property.set( parseLiteral( "error( \"Sentence is out of context!\" )") );
                return;
            } catch ( Exception e ) {
                e.printStackTrace();
            }
        }
        // If the literal is some of these cases that are ground and without terms (only functor) we can directly return
        if ( ! best_literal.hasTerm() ){
            property.set( best_literal );
            return;
        }
        System.out.println( "Best embedding found: " + best_literal );
        // Modify the closest property with the values provided in the sentence
        Literal new_property = generate_literal( best_literal, sentence.toLowerCase() );
        log( "Generated property: " + new_property );
        property.set( new_property );
    }


    @OPERATION
    public void generate_sentence( String performative, String literal_str, OpFeedbackParam<String> sentence ) {
        // Generate a sentence starting from the literal
        try {
            sentence.set( generate_string( parseLiteral(literal_str), performative ) );
        } catch( Exception e ) {
            failed( "Error parsing " + literal_str + " to Literal: " + e.getMessage() );
        }
    }
    
    // init_embeddings takes all the literals from the agents and computes for each literal the embedding
    private void init_embeddings(  Literal[] literals ) {
       log( "Initializing embeddings;" );

        // Initialize embeddings hashmap
        embeddings = new HashMap<>();
        for ( Literal literal : literals ) {
            try {
                // String str =((String) o_literal).replace("+","").replace("!","").replace("-","");
                
                // Add the embedding only if it is not already present
                if ( embeddings.get( literal ) == null ){
                    List<Double> embedding = compute_embedding( literal.toString() );
                    embeddings.put( literal, embedding );
                }
            } catch ( Exception e ) {
               //System.out.println("ERRROR:"+e.getMessage()+"FOR LITERAL:"+o_literal);
            }
        }
    }



    //done ->uguale a init_embeddings ma anzichè inizializzarli, aggiorna con nuovi
    // This function updates the embeddings with new beliefs gained by agents
    @OPERATION
    public void update_embeddings( String ag, Object o_literal ) throws Exception {
        log( "Updating embeddings for " + ag );
        if ( ! ag_literals.keySet().contains( ag ) ) {
            failed( "The agent " + ag + " is not initialized for embeddings! Cannot update them." );
            return;
        }
        Literal literal = parseLiteral( ( String ) o_literal );
        if ( !ag_literals.get( ag ).contains( literal ) )
            ag_literals.get( ag ).add( literal );
        if ( embeddings.get( literal ) == null ) {
            List<Double> embedding = compute_embedding( literal.toString() );
            embeddings.put( literal, embedding ); 
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
        The sentence can be classified as seven different types: tell, achieve, tellHow, askOne, askAll, askHow and unclassified

        Sentence will be classified as "tell" if it contains knowledge or information that is communicated.

        Sentence will be classified as "achieve" if it contains the giving of an order or the request to achieve a goal.

        Sentence will be classified as "tellHow" if it contains an explanation of how to carry out a task or action.

        Sentence will be classified as "askOne" if it contains a request to provide a specific information or a specific knowledge
        contained within the set of information involved.

        Sentence will be classified as "askAll" if it contains a request to provide all information or all knowledge
        contained within the set of information involved.

        Sentence will be classified as "askHow" if it contains a request to provide an explanation of how to do a
        specific task.

        If a sentence is a sequence of random letters, it will be classified as "unclassified"

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


        Sentence: Aslkdj
        Explanation: This sentence has no meaning
        Answer: unclassified

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
    public void classify_performative( String sentence, OpFeedbackParam<Literal>performative_type ){
           
            sentence = sentence.replaceAll("\\s*@\\S+", "");
            sentence = sentence.toLowerCase();

            String body = send_ollama( "generate", CLASSIFICATION_MODEL, sentence );

            //controllo tipo risposta

            if (body.contains("tell"))
                body = "tell";
            else if (body.contains("achieve"))
                body = "achieve";
            else if (body.contains("tellHow"))
                body = "tellHow";
            else if (body.contains("askHow"))
                body = "askHow";
            else if (body.contains("askOne"))
                body = "askOne";
            else if (body.contains("askAll"))
                body = "askAll";
            else if(body.contains("unclassified"))
                body = "unclassified";


            try {
                performative_type.set( parseLiteral( body ) );
            } catch ( Exception e ) {
                failed( "Error parsing " + body + " to literal: " + e.getMessage() );
            }

    }



////////------------------------------------------------------------------------
    //done
    private JSONObject get_logic_to_nl_model() {
        String system = """
            You will receive a logical property and a property type in the following format: logical property, property type.
            Your task consists in generate a sentence based on the logical property and its type.

            There are five types of property type:

            tell: this type indicates that knowledge must be communicated.
            askHow: this type indicates a request on how to do something, in other words you are requesting a sequence of instructions to complete a job.
            askOne: this type indicates a request to provide specific knowledge in the set of knowledge considered.
            askAll: this type indicates a request to provide all the knowledge in the set of knowledge considered.
            achieve: this type indicates an order about to achieve a goal or complete a task.


            You will tell the content of the property to me as if we are speaking and the content is something about you.
            
            Here's a few examples:
            hasColor(apple, red), tell should be translated to "The apple is red".

            meeting(tomorrow, room(102), [alice, bob, charles]), tell should be translated to "Tomorrow there is a meeting in room 102 with alice, bob and charles."
            meeting(tomorrow, room(102), [alice, bob, charles]), achieve should be translated to "Tomorrow you have to go to a meeting in room 102 with Alice, Bob and Charles."

            message(letter, grandma), achieve should be translated to "Could you text your grandma a letter?"
            message(letter, grandma), askOne should be translated to "Have you written a letter to your grandmother?"

            cooking(pizza, oven), askHow should be translated to "Can you explain to me how to cook pizza with the oven?"

            revolution(france), askOne should be translated to "Tell me a revolution that happened in France"
            revolution(france), askAll should be translated to "Tell all the revolutions that happened in France"
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
        try {
            return parseLiteral( new_property );
        } catch( Exception e ) {
            failed( "Error parsing literal " + e.getMessage() );
        }
        return null;

    }


    //done
    private String generate_string( Literal literal, String performative ) {
        String prompt = String.format( "Generate a sentence describing this logical property: %s.", literal.toString() );
        prompt = prompt +", "+ performative;
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
}
