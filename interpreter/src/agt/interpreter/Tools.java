package chatbdi;

import jason.asSyntax.*;
import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import java.util.HashSet;

import org.json.JSONObject;
import org.json.JSONArray;

import jason.asSyntax.parser.ParseException;
import static jason.asSyntax.ASSyntax.*;

/**
 * This class provides a set of static methods to use inside the whole project
 * @author Andrea Gatti
 */
public class Tools {

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
        if ( formula instanceof Pred ) {
            if ( formula.isNumeric() ) //! In the original version there was even isVar. Consider it.
                return preds;
            if ( formula.isList() ) {
                for ( Term t : (ListTerm) formula )
                    preds.addAll( formulaToList( (LogicalFormula) t ) );
                return preds;
            }
        } else if ( formula instanceof Pred ) {
            preds.add( (Pred) formula );
            return preds;
        }
        if ( formula instanceof Structure ) {
            Structure s = (Structure) formula;
            for ( Term t : s.getTerms() ) {
                if ( t.isNumeric() )
                    continue;
                preds.addAll( formulaToList( (LogicalFormula) t ) );
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
    public static JSONObject termToJSON( Literal term ) {
        JSONObject json = new JSONObject();
        String functor = term.getFunctor();
        json.put( "functor", functor );
        for ( int i = 0; i < term.getArity(); i++ )
            json.put( "arg" + i, term.getTerm( i ) );
        return json;
    }

    /**
     * Converts a JSON object into the corresponding term
     * @param json the JSONObject to convert
     * @return a Literal with the conversion
     * @throws ParseException if the provided JSON object produces syntax errors
     */
    public static Literal jsonToTerm( JSONObject json ) throws ParseException {
        if ( json.length() == 1 )
            return createLiteral( json.getString( "functor" ) );
        String term = json.getString( "functor" ) + "(";
        for ( int i = 0; i < json.length() - 1; i++ ) {
            if ( json.isNull( "arg" + i ) || json.getString( "arg" + i ).equals( "null" ) )
                term += " _, ";
            else
                term += json.get( "arg" + i ) + ", ";
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
    public static JSONObject genJSONSchema( List<JSONObject> examples ) {
        JSONObject schema = new JSONObject();
        schema.put( "type", "object" );
        JSONObject properties = new JSONObject();
        properties.put( "functor", new JSONObject().put( "const", examples.get( 0 ).getString( "functor" ) ) );

        for ( String key : examples.get( 0 ).keySet() ) {
            Set<String> types = new HashSet<>();
            if ( key.equals( "functor" ) )
                continue;
            for ( JSONObject example : examples ) {
                Term term = (Term) example.get( key );
                if ( term.isString() || term.isAtom() || term.isVar() )
                    types.add( "string" );
                else if ( term.isNumeric() )
                    types.add( "number" );
                else if ( term.isList() )
                    types.add( "list" );
                else if ( term.isUnnamedVar() )
                    types.add( "null" );
            }
            List<JSONObject> jsonTypes = new ArrayList<>();
            for ( String type : types )
                jsonTypes.add( new JSONObject().put( "type", type ) );
            if ( !types.contains( "null" ) )
                jsonTypes.add( new JSONObject().put( "type", "null" ) );
            properties.put( key, new JSONObject().put( "anyOf", jsonTypes.toArray() ) );
        }
        schema.put( "properties", properties );
        return schema;
    }

    /**
     * Given the set of examples returns a list of all the found var names for each argument.
     * This is useful for the LLM to help translation
     * @param examples the list of all the literals
     * @return a list of set of terms, one set for each argument
     */
    public static List<Set<Term>> getVarNames( List<Literal> examples ) {
        List<Set<Term>> varNames = new ArrayList<>();
        for ( int i = 0; i < examples.get( 0 ).getArity(); i++ )
            varNames.add( new HashSet<>() );
        for ( Literal example : examples ) {
            for ( int i = 0; i < example.getArity(); i++ )
                if ( example.getTerm(i).isVar() )
                    varNames.get( i ).add( example.getTerm( i ) );
        }
        return varNames;
    }
}