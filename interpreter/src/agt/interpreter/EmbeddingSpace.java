package chatbdi;

import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Set;
import java.util.HashSet;

import jason.asSyntax.*;
import static jason.asSyntax.ASSyntax.*;

public class EmbeddingSpace {
    private Map<Literal, List<Double>> plans;
    private Map<Literal, List<Double>> terms;
    private Map<String, List<Literal>> agDomain;
    private Ollama ollama;

    public EmbeddingSpace( Ollama ollama ) {
        this.plans = new HashMap<>();
        this.terms = new HashMap<>();
        this.agDomain = new HashMap<>();
        this.ollama = ollama;
    }

    protected boolean containsTerm( Literal t ) {
        return terms.keySet().contains( t );
    }

    protected boolean containsPlan( Literal p ) {
        return plans.keySet().contains( p );
    }

    protected boolean contains( String subSpace, Literal l ) {
        if ( subSpace.equals( "terms" ) )
            return containsTerm( l );
        else if ( subSpace.equals( "plans" ) )
            return containsPlan( l );
        throw new IllegalArgumentException( "Space can be either terms or plans." );
    }

    protected boolean contains( Literal l ) {
        return containsTerm( l ) || containsPlan( l );
    }

    protected void addPlan( String agName, Literal p ) {
        // String planStr = tools.preprocess( p );
        List<Double> embedding = ollama.embed( p );
        plans.put( p, embedding );
        if ( !agDomain.containsKey( agName ) )
            agDomain.put( agName, new ArrayList<>() );
        agDomain.get( agName ).add( p );
    }

    protected void addTerm( String agName, Literal t ) {
        // String termStr = tools.preprocess( t );
        List<Double> embedding = ollama.embed( t );
        terms.put( t, embedding );
        if ( !agDomain.containsKey( agName ) )
            agDomain.put( agName, new ArrayList<>() );
        agDomain.get( agName ).add( t );
    }

    protected List<Literal> getPlans() {
        return new ArrayList<>( plans.keySet() );
    }

    protected List<Literal> getTerms() {
        return new ArrayList<>( terms.keySet() );
    }

    private List<Double> getPlanEmbedding( Literal plan ) {
        return plans.get( plan );
    }

    private List<Double> getTermEmbedding( Literal term ) {
        return terms.get( term );
    }

    private List<Literal> getSubSpace( String subSpace ) {
        if ( subSpace.equals( "terms" ) )
            return getTerms();
        if ( subSpace.equals( "plans" ) )
            return getPlans();
        throw new IllegalArgumentException( "Subspace can be either 'terms' or 'plans'.");
    }

    private List<Literal> getAgsSubSpace( List<String> agNames, String subSpace ) {
        Set<Literal> space;
        if ( subSpace.equals( "terms" ) )
            space = new HashSet<>( getTerms() );
        else if ( subSpace.equals( "plans" ) )
            space = new HashSet<>( getPlans() );
        else
            throw new IllegalArgumentException( "Subspace should be either 'terms' or 'plans'" );
        Set<Literal> agsDomain = new HashSet<>();

        // If there are no receivers I get the full space
        if ( agNames.isEmpty() )
            return getSubSpace( subSpace );

        for ( String ag : agNames )
            agsDomain.addAll( getAgDomain( ag ) );

        space.retainAll( agsDomain );
        return new ArrayList<Literal>( space );
    }

    private List<Double> getEmbedding( Literal l ) {
        if ( terms.keySet().contains( l ) )
            return getTermEmbedding( l );
        else if ( plans.keySet().contains( l ) )
            return getPlanEmbedding( l );
        return null;
    }

    private List<Double> getEmbedding( String subSpace, Literal l ) {
        if ( subSpace.equals( "terms" ) )
            return getTermEmbedding( l );
        if ( subSpace.equals( "plans" ) )
            return getPlanEmbedding( l );
        return null;
    }

    private List<Literal> getAgDomain( String ag ) {
        return agDomain.get( ag );
    }

    protected Literal findNearest( List<String> ags, String subSpace, String msg ) {
        System.out.println( "[LOG] " + getTerms() );
        System.out.println( "[LOG] " + ags + ", " + subSpace + ": " + msg );
        List<Double> emb = ollama.embed( msg );
        Literal nearestLiteral = null;
        double nearestDist = Double.MAX_VALUE;
        for ( Literal lit : getAgsSubSpace( ags, subSpace ) ) {
            List<Double> litEmb = getEmbedding( subSpace, lit );
            double dist = Tools.cosineDistance( emb, litEmb );
            if ( dist < nearestDist ) {
                nearestDist = dist;
                nearestLiteral = lit;
            }
        }
        return nearestLiteral;
    }

    protected List<Literal> getExamples( Literal ilf, Literal nearest ) {
        List<Literal> examples = new ArrayList<>();
        examples.add( nearest );
        String subSpace = "terms";
        if ( ilf.equalsAsStructure( createLiteral( "achieve" ) ) || ilf.equalsAsStructure( createLiteral( "askHow" ) ) )
            subSpace = "plans";
        for ( Literal lit : getSubSpace( subSpace ) )
            if ( nearest.getFunctor().equals( lit.getFunctor() ) && nearest.getArity() == lit.getArity() )
                examples.add( lit );
        return examples;
    }

}