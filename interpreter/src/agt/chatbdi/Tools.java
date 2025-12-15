package chatbdi;

import jason.asSyntax.*;
import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import java.util.HashSet;
import java.util.Map;
import java.util.HashMap;

import org.json.JSONObject;
import org.json.JSONArray;

import jason.asSyntax.parser.ParseException;
import static jason.asSyntax.ASSyntax.*;
import jason.NoValueException;

/**
 * This class provides a set of static methods to use inside the whole project
 * @author Andrea Gatti
 */
public class Tools {

    private static final JSONObject ATOM;
    private static final JSONObject VAR;
    private static final JSONObject STRING;
    private static final JSONObject NUMBER;
    private static final JSONObject LIST;
    private static final JSONObject NULL;

    static {
        ATOM = new JSONObject()
            .put("type", "string")
            .put("pattern", "^[a-z][A-Za-z0-9_]*$")
            .put("format", "prolog-atom");

        VAR = new JSONObject()
            .put("type", "string")
            .put("pattern", "^[A-Z][A-Za-z0-9_]*$")
            .put("format", "prolog-variable");

        STRING = new JSONObject().put("type", "string");
        NUMBER = new JSONObject().put("type", "number");
        LIST = new JSONObject().put("type", "array");
        NULL = new JSONObject().put("type", "null");
    }

    /**
     * Given a literal preprocess it for being embedded
     * @param lit the literal to preprocess
     * @return a string with the value to embed ( 4 times the head + terms )
     * It gives more weight to the head of the literal
     */
    public static String preprocess( Literal lit ) {
        // Note: 'replace' replaces all the occurrences. 'replaceAll' takes a regex as first arg

		// Get the functor
        String functor = lit.getFunctor().replace( "_", " " ).replace( "my", "your" ) + " ";
        String terms = "";

		// If the term does not have nested terms return it repeated 4 times (weighted)
        if ( !lit.hasTerm() )
            return functor.repeat( 4 );

		// Preprocess the term arguments
        for ( Term t : lit.getTerms() ) {
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
        return functor.repeat( 4 ) + terms.trim();
    }

    /**
     * Computes the cosine distance between two vectors
     * @param emb1 the first vector
     * @param emb2 the second vector
     * @return the distance as a double in [0.0, 2.0]
     * @throws IllegalArgumentException if one of the embedding is null or if they have different sizes
     * @see <a href="https://en.wikipedia.org/wiki/Cosine_similarity#Cosine_distance">Wikipedia</a>
     */
    public static double cosineDistance( List<Double> emb1, List<Double> emb2 ) {

        // Sanity checks
        if ( emb1 == null || emb2 == null )
            throw new IllegalArgumentException( "One of the embeddings is null" );
        assert emb1 != null && emb2 != null;

        if ( emb1.size() != emb2.size() )
            throw new IllegalArgumentException( "Embeddings have different sizes: " +  emb1.size() + " and " + emb2.size() );
        assert emb1.size() == emb2.size();
        
        // Compute the distance
        double dotProd = 0.0;
        double norm1 = 0.0;
        double norm2 = 0.0;

        for ( int i = 0; i < emb1.size(); i++ ) {
            dotProd += emb1.get( i ) * emb2.get( i );
            norm1 += emb1.get( i ) * emb1.get( i );
            norm2 += emb2.get( i ) * emb2.get( i );
        }
        norm1 = Math.sqrt( norm1 );
        norm2 = Math.sqrt( norm2 );

        if ( norm1 == 0 || norm2 == 0 )
            throw new IllegalArgumentException( "Embedding norm cannot be ZERO" );

        return 1.0 - ( dotProd / ( norm1 * norm2 ) );
    }

    /**
     * Given a formula it creates a list of subpredicates
     * @param formula the formula can be either a real formula or a simple predicate
     * @return the list of single predicates
     * For example:
     * <ul>
     * <li> a(b, c, d) -> [a, b, c, d] </li>
     * <li> a(b) :- c &amp; d(e) -> [a, b, c, d, e] </li>
     * </ul>
     * It is inductive.
     */
    public static List<Pred> formulaToList( LogicalFormula formula ) {
        List<Pred> preds = new ArrayList<>();
        if ( formula.isPred() ) {
            System.out.println( "[DEBUG] " + formula + " is a PRED!" );
            preds.add( (Pred) formula );
        }
        if ( formula.isNumeric() || formula.isString() )
            return preds;
        if ( formula.isVar() || formula.isUnnamedVar() )
            return preds;
        if ( formula.isList() ) {
            for ( Term t : (ListTerm) formula ) 
                preds.addAll( formulaToList( (LogicalFormula) t ) );
            return preds;
        }
        if ( formula.isInternalAction() ) {
            Structure s = (Structure) formula;
            System.out.println( "[DEBUG] Internal Action: " + s );
            if ( s.hasTerm() ) {
                for ( Term t : s.getTerms() ) {
                    System.out.println( "[DEBUG] IA preds before: " + preds );
                    if ( t.isNumeric() || t.isString() )
                        continue;
                    preds.addAll( formulaToList( (LogicalFormula) t ) );
                    System.out.println( "[DEBUG] IA | " + t );
                    System.out.println( "[DEBUG] IA preds after : " + preds );
                }
            }
        }
        if ( formula.isStructure() ) {
            Structure s = (Structure) formula;
            if ( s.hasTerm() ) {
                for ( Term t : s.getTerms() ) {
                    if ( t.isNumeric() || t.isString() )
                        continue;
                    preds.addAll( formulaToList( (LogicalFormula) t ) );
                }
                return preds;
            } else {
                preds.add( (Pred) s );
                return preds;
            }
        } 

        return preds;
    }

    /**
     * Converts a term to its JSON object
     * @param term the term to convert
     * @return a JSONObject with the conversion
     * Example: 
     * a(b) -> { "functor": "a", "arg0": "b"}
     */
    public static Map<String, Term> termToMap( Literal term ) {
        Map<String, Term> jsonMap = new HashMap<>();
        String functor = term.getFunctor();
        jsonMap.put( "functor", createLiteral( functor ) );
        for ( int i = 0; i < term.getArity(); i++ ) {
            jsonMap.put( "arg" + i, term.getTerm( i ) );
        }
        System.out.println( "[LOG] Map: " + jsonMap.toString() );
        return jsonMap;
    }

    // public static JSONObject termToJSON( Literal term ) throws NoValueException {
    //     JSONObject json = new JSONObject();
    //     String functor = term.getFunctor();
    //     json.put( "functor", functor );
    //     for ( int i = 0; i < term.getArity(); i++ ) {
    //         if ( term.getTerm(i).isNumeric() )
    //             json.put( "arg" + i, ( (NumberTerm) ( term.getTerm(i) ) ).solve() ); 
    //         else
    //             json.put( "arg" + i, term.getTerm(i).toString() ); 
    //     }
    //     return json;
    // }

    public static JSONObject mapToJson( Map<String, Term> map ) throws NoValueException {
        JSONObject json = new JSONObject();
        for (String key: map.keySet() ) {
            Term value = map.get( key );
            if ( value.isString() ) {
                json.put( key, value.toString() );
            } else if ( value.isNumeric() ) {
                json.put( key, ( ( NumberTerm ) value ).solve() );
            } else if ( value.isUnnamedVar() ) {
                json.put( key, "_" );
            } else {
                if ( value.toString().startsWith( "_" ) )
                    json.put( key, "_" );
                else
                    json.put( key, value.toString() );
            }
        }
        return json;
    }

    /**
     * Converts a JSON object into the corresponding term
     * @param json the JSONObject to convert
     * @return a Literal with the conversion
     * @throws ParseException if the provided JSON object produces syntax errors
     */
    public static Literal jsonToTerm( JSONObject json ) throws ParseException {
        // TODO: Consider that the arg can be also null or an Integer
        System.out.println( "[DEBUG] Json object considered: " + json );
        if ( json.length() == 1 )
            return createLiteral( json.getString( "functor" ) );
        String term = json.getString( "functor" ) + "(";
        for ( int i = 0; i < json.length() - 1; i++ ) {
            if ( json.isNull( "arg" + i ) )
                term += "_, ";
            Object value = json.get( "arg" + i );
            if ( value instanceof String ) {
                if ( json.getString( "arg" + i ).equals( "null" ) )
                    term += " _, ";
                else if ( json.getString( "arg" + i ).startsWith( "_" ) )
                    term += " _, ";
                else
                    term += json.getString( "arg" + i ) + ", ";
            } else if ( value instanceof Integer ) {
                term += value.toString() + ", ";
            } else {
                term += value.toString() + ", ";
            }
        }
        term = term.substring( 0, term.length() - 2) + ")";
        return parseLiteral( term );
    }

    /**
     * Given a list of JSONObject generates a schema with the most general set of Json types
     * @param examples the list of all the terms (all with same functor and arity by design)
     * @return a JSONObject containing a json schema
     * The schema is built iterating over all the arguments and creating a list of all the possible json types for the arg.
     * Null is used for underscore.
     */
    public static JSONObject genJSONSchema( List<Map<String, Term>> examples ) {
        JSONObject schema = new JSONObject();
        schema.put( "type", "object" );
        JSONObject properties = new JSONObject();
        properties.put( "functor", new JSONObject().put( "const", examples.get( 0 ).get( "functor" ) ) );

        System.out.println( "[DEBUG] Before examples: " + schema );
        System.out.println( "[DEBUG] Before examples properties: " + properties );
        for ( String key : examples.get( 0 ).keySet() ) {
            System.out.println( "[DEBUG] Considering " + key );
            // // Set<String> types = new HashSet<>();
            Set<JSONObject> types = new HashSet<>();
            Set<String> hints = new HashSet<>();
            if ( key.equals( "functor" ) )
                continue;
            for ( Map<String, Term> example : examples ) {
                Term term = example.get( key );
                if ( term.isString() )
                    types.add( STRING );
                else if ( term.isAtom() )
                    types.add( ATOM );
                else if ( term.isUnnamedVar() )
                    types.add( NULL );
                else if ( term.isVar() ) {
                    types.add( VAR );
                    hints.add( term.toString() );
                } else if ( term.isNumeric() )
                    types.add( NUMBER );
                else if ( term.isList() )
                    types.add( LIST );
                // // if ( term.isString() || term.isAtom() || term.isVar() ) {
                //     // types.add( "string" );
                //     // if ( term.isVar() )
                //         // hints.add( term.toString() );
                // // }
                // // else if ( term.isNumeric() )
                //     // types.add( "number" );
                // // else if ( term.isList() )
                //     // types.add( "list" );
                // // else if ( term.isUnnamedVar() )
                //     // types.add( "null" );
            }
            System.out.println( "[DEBUG] Extracted types: " + types );
            System.out.println( "[DEBUG] Extracted hints: " + hints );
            // // List<JSONObject> jsonTypes = new ArrayList<>();
            // // for ( String type : types )
            // //     jsonTypes.add( new JSONObject().put( "type", type ) );
            // // if ( !types.contains( "null" ) )
            // //     jsonTypes.add( new JSONObject().put( "type", "null" ) );
            JSONObject field = new JSONObject();
            if ( !hints.isEmpty() ) {
                field.put( "description", "This field contains: " + String.join( " or ", hints ) );
            }
            field.put( "anyOf", new JSONArray( types.toArray() ) );
            properties.put( key, field );
            System.out.println("[DEBUG] Properties: " + properties );
        }
        schema.put( "properties", properties );
        schema.put( "required", new JSONArray( examples.get( 0 ).keySet().toArray() ) );
        System.out.println( "[DEBUG] Generated Schema: " + schema.toString() );
        return schema;
    }

    // // /**
    // //  * Given the set of examples returns a list of all the found var names for each argument.
    // //  * This is useful for the LLM to help translation
    // //  * @param examples the list of all the literals
    // //  * @return a list of set of terms, one set for each argument
    // //  */
    // // public static List<Set<Term>> getVarNames( List<Literal> examples ) {
    // //     List<Set<Term>> varNames = new ArrayList<>();
    // //     for ( int i = 0; i < examples.get( 0 ).getArity(); i++ )
    // //         varNames.add( new HashSet<>() );
    // //     for ( Literal example : examples ) {
    // //         for ( int i = 0; i < example.getArity(); i++ )
    // //             if ( example.getTerm(i).isVar() )
    // //                 varNames.get( i ).add( example.getTerm( i ) );
    // //     }
    // //     return varNames;
    // // }
}