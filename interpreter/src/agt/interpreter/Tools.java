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

public class Tools {

    public static String preprocess( Literal lit ) {
        // Note: 'replace' replaces all the occurrences. 'replaceAll' takes a regex as first arg
        String functor = lit.getFunctor().replace( "_", " " ).replace( "my", "your" ) + " ";
        String terms = "";
        if ( !lit.hasTerm() )
            return functor.repeat( 4 );
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
        return functor.repeat( 4 ) + terms.trim();
    }

    public static double cosineDistance( List<Double> emb1, List<Double> emb2 ) {
        if ( emb1 == null || emb2 == null )
            throw new IllegalArgumentException( "One of the embeddings is null" );
        assert emb1 != null && emb2 != null;
        if ( emb1.size() != emb2.size() )
            throw new IllegalArgumentException( "Embeddings have different sizes: " +  emb1.size() + " and " + emb2.size() );
        
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

    public static JSONObject termToJSON( Literal term ) {
        JSONObject json = new JSONObject();
        String functor = term.getFunctor();
        json.put( "functor", functor );
        for ( int i = 0; i < term.getArity(); i++ )
            json.put( "arg" + i, term.getTerm( i ) );
        return json;
    }

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