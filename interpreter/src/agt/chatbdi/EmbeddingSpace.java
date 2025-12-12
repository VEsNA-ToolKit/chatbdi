package chatbdi;

import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Set;
import java.util.HashSet;

import jason.asSyntax.*;
import static jason.asSyntax.ASSyntax.*;

import jason.bb.BeliefBase;
import jason.pl.PlanLibrary;

import static chatbdi.Tools.*;

/**
 * This class implements the embedding space and all the necessary methods.
 * The embedding space is subdivided into two subspaces: one for the plan heads and one for all the other terms.
 * The embedding space is also organized in domains, one for each agent.
 * @author Andrea Gatti
 */
public class EmbeddingSpace {

    /**
     * The plan subspace
     */
    private Map<Literal, List<Double>> plans;
    /**
     * The term subspace
     */
    private Map<Literal, List<Double>> terms;
    /**
     * The agent domain
     */
    private Map<String, List<Literal>> agDomain;
    private Map<String, BeliefBase> agBBs;
    private Map<String, PlanLibrary> agPLs;
    /**
     * The ollama instance to use
     */
    private Ollama ollama;

    public void print() {
        String es = "";
        es += "..:: EMBEDDING SPACE ::..\n";
        es += " --- Plans ---\n";
        for ( Literal plan : plans.keySet() ) 
            es += " + " + plan + "\n";
        es += " --- Terms ---\n";
        for ( Literal term : terms.keySet() )
            es += " + " + term + "\n";
        es += "\n --- Agent Domains ---\n";
        for ( String ag: agDomain.keySet() ) {
            es += " + Agent: " + ag + "\n";
            for ( Literal lit : agDomain.get(ag) ) 
                es += "     - " + lit + "\n";
        }
        System.out.println( es );
    }

    /** Build an embedding space with the ollama object to use
     * @param ollama the Ollama object to use for embeddings
     */
    public EmbeddingSpace( Ollama ollama ) {
        this.plans = new HashMap<>();
        this.terms = new HashMap<>();
        this.agDomain = new HashMap<>();
        this.ollama = ollama;
        this.agBBs = new HashMap<>();
        this.agPLs = new HashMap<>();
    }

    protected void update( String agName, BeliefBase bb, PlanLibrary pl ) {
        if ( !agBBs.containsKey( agName ) )
            agBBs.put( agName, bb );
        if ( !agPLs.containsKey( agName ) )
            agPLs.put( agName, pl );
        // TODO: After first time the update should add and remove bbs and pls with a difference.

        for ( Literal bel : bb ) {
            if ( bel.toString().contains( "kqml::" ) )
                continue;
            if ( containsTerm( bel ) )
                continue;
            if ( bel.isRule() ) { 
                Literal head = ( (Rule) bel ).getHead();
                addTerm( agName, head );
                LogicalFormula body = ( (Rule) bel ).getBody();
                List<Pred> preds = formulaToList( body );
                for ( Pred p : preds )
                    addTerm( agName, p );
                continue; // necessary to do not call the addTerm this line + 2
            }
            addTerm( agName, bel );
        }
        for ( Plan plan : pl ) {
            if ( plan.toString().contains( "@kqml" ) )
                continue;
            Literal triggerLit = plan.getTrigger().getLiteral();
            LogicalFormula context = plan.getContext();
            if ( context != null ) {
                System.out.println( "[DEBUG] Plan " + triggerLit + " Context " + context );
                List<Pred> contextList = formulaToList( context );
                for ( Pred pred : contextList ) {
                    System.out.println( "[DEBUG] Plan " + triggerLit + " Context: " + pred );
                    addTerm( agName, pred );
                }
            }
            if ( plan.getTrigger().isAchvGoal() && containsPlan( triggerLit ) )
                continue;
            if ( !plan.getTrigger().isAchvGoal() && containsTerm( triggerLit ) )
                continue;
            if ( plan.getTrigger().isAchvGoal() )
                addPlan( agName, triggerLit );
            else
                addTerm( agName, triggerLit );
        }
    }

    /**
     * Check if the embedding term subspace contains a Literal t
     * @param t the literal to check
     * @return true if contained, false otherwise
     */
    protected boolean containsTerm( Literal t ) {
        return terms.keySet().contains( t );
    }

    /**
     * Check if the embedding plan subspace contains a Literal p
     * @param p the literal to check
     * @return true if contained, false otherwise
     */
    protected boolean containsPlan( Literal p ) {
        return plans.keySet().contains( p );
    }

    /**
     * Check if the embedding space contains a Literal inside one subspace (useful for scripting)
     * @param subSpace the subspace to check (terms|plans)
     * @param l the Literal to check
     * @return true if contained, false otherwise
     * @throws IllegalArgumentException if subSpace is not terms or plans
     */
    protected boolean contains( String subSpace, Literal l ) {
        if ( subSpace.equals( "terms" ) )
            return containsTerm( l );
        else if ( subSpace.equals( "plans" ) )
            return containsPlan( l );
        throw new IllegalArgumentException( "Space can be either terms or plans." );
    }

    /**
     * Check if the Literal is contained inside terms or plans
     * @param l the Literal to check
     * @return true if contained, false otherwise
     */
    protected boolean contains( Literal l ) {
        return containsTerm( l ) || containsPlan( l );
    }

    /**
     * Checks if the Literal is contained in the agent domain
     * @param agName the agent to consider
     * @param l the literal to check
     * @return true if contined, false otherwise
     */
    protected boolean isInAgDomain( String agName, Literal l ) {
        return this.agDomain.get( agName ).contains( l );
    }

    /**
     * Add a plan to the Embedding Space
     * @param agName the agent name for the domain
     * @param p the plan head to add
     */
    protected void addPlan( String agName, Literal p ) {
        // if the embedding is already computed
        if ( containsPlan( p ) ) {
            // if the plan is already in the agent domain exit
            if ( isInAgDomain( agName, p ) )
                return;
            // otherwise add it to the agent domain and exit
            if ( !agDomain.containsKey( agName ) )
                agDomain.put( agName, new ArrayList<>() );
            agDomain.get( agName).add( p );
            return;
        }
        // create the embedding vector
        List<Double> embedding = ollama.embed( p );
        // Add it to the plan subspace
        plans.put( p, embedding );
        // Add it to the agent domain
        if ( !agDomain.containsKey( agName ) )
            agDomain.put( agName, new ArrayList<>() );
        agDomain.get( agName ).add( p );
    }

    /**
     * Add a term to the Embedding Space
     * @param agName the agent name for the domain
     * @param t the Literal to add
     */
    protected void addTerm( String agName, Literal t ) {
        // if the embedding is already computed
        if ( containsTerm( t ) ) {
            // if the term is already in the agent domain exit
            if ( isInAgDomain( agName, t ) )
                return;
            // otherwise add it to the agent domain and exit
            if ( !agDomain.containsKey( agName ) )
                agDomain.put( agName, new ArrayList<>() );
            agDomain.get( agName).add( t );
            return;
        }

        // create the embedding vector
        List<Double> embedding = ollama.embed( t );
        // Add it to the terms subspace
        terms.put( t, embedding );
        // Add it to the agent domain
        if ( !agDomain.containsKey( agName ) )
            agDomain.put( agName, new ArrayList<>() );
        agDomain.get( agName ).add( t );
    }

    /**
     * Get the list of all the plans
     * @return a list of Literals
     */
    protected List<Literal> getPlans() {
        return new ArrayList<>( plans.keySet() );
    }

    /**
     * Get the list of all the terms
     * @return a list of Literals
     */
    protected List<Literal> getTerms() {
        return new ArrayList<>( terms.keySet() );
    }

    /**
     * Get the embedding vector of a specific plan
     * @param plan the plan head
     * @return the vector as a list of Double
     */
    private List<Double> getPlanEmbedding( Literal plan ) {
        return plans.get( plan );
    }

    /**
     * Get the embedding vector of a specific term
     * @param term the term head
     * @return the vector as a list of Double
     */
    private List<Double> getTermEmbedding( Literal term ) {
        return terms.get( term );
    }

    /**
     * Get the embedding vector of a literal
     * @param l the literal to get
     * @return the embedding vector as List of Double
     */
    private List<Double> getEmbedding( Literal l ) {
        if ( terms.keySet().contains( l ) )
            return getTermEmbedding( l );
        else if ( plans.keySet().contains( l ) )
            return getPlanEmbedding( l );
        return null;
    }

    /**
     * Get the embedding vector of a literal inside a subspace (useful for scripting)
     * @param subSpace the subspace to consider, either 'terms' or 'plans'
     * @param l the Literal to get
     * @return the embedding vector as a List of Double
     */
    private List<Double> getEmbedding( String subSpace, Literal l ) {
        if ( subSpace.equals( "terms" ) )
            return getTermEmbedding( l );
        if ( subSpace.equals( "plans" ) )
            return getPlanEmbedding( l );
        return null;
    }

    /**
     * Get the list of elements of the given subspace
     * @param subSpace the subspace to consider
     * @return a list of Literal
     * @throws IllegalArgumentException if the subspace is not 'terms' or 'plans'
     */
    private List<Literal> getSubSpace( String subSpace ) {
        if ( subSpace.equals( "terms" ) )
            return getTerms();
        if ( subSpace.equals( "plans" ) )
            return getPlans();
        throw new IllegalArgumentException( "Subspace can be either 'terms' or 'plans'.");
    }

    /**
     * Get the list of elements in the ags domains
     * @param agNames the list of agents domains to consider
     * @param subSpace the subspace to consider
     * @return the list of all the elements in the intersection of subspace and domain (without duplicates!)
     * @throws IllegalArgumentException if subspace is not 'terms' or 'plans'
     */
    private List<Literal> getAgsSubSpace( List<String> agNames, String subSpace ) {
        // The intersection is a set to prevent having duplicates
        Set<Literal> space = new HashSet<Literal>( getSubSpace( subSpace ) );

        // If there are no receivers return the full subspace
        if ( agNames.isEmpty() )
            return new ArrayList<Literal>( space );

        // Get all the literals inside the agents domains
        Set<Literal> agsDomain = new HashSet<>();
        for ( String ag : agNames )
            agsDomain.addAll( getAgDomain( ag ) );

        // Intersect the two sets
        space.retainAll( agsDomain );
        return new ArrayList<Literal>( space );
    }


    /**
     * Get the agent domain
     * @param ag the agent name
     * @return a list of literal
     */
    private List<Literal> getAgDomain( String ag ) {
        return agDomain.get( ag );
    }

    /**
     * Given a list of agent names, a subspace and a message, finds the nearest literal inside the space
     * @param ags the list of agent names
     * @param subSpace the subspace to consider, either 'terms' or 'plans'
     * @param msg the message to consider
     * @return the nearest literal found
     */
    protected Literal findNearest( List<String> ags, String subSpace, String msg ) {
        // Embed the message
        List<Double> emb = ollama.embed( msg );

        Literal nearestLiteral = null;
        double nearestDist = Double.MAX_VALUE;
        // Consider all the literals inside the intersection of agents' domains and subspace
        for ( Literal lit : getAgsSubSpace( ags, subSpace ) ) {
            List<Double> litEmb = getEmbedding( subSpace, lit );
            // Compute the distance and consider it
            double dist = cosineDistance( emb, litEmb );
            if ( dist < nearestDist ) {
                nearestDist = dist;
                nearestLiteral = lit;
            }
        }
        return nearestLiteral;
    }

    /**
     * Get a list of examples (terms or plans with same head and arity) from the space
     * @param ilf the illocutionary force of the message
     * @param nearest the found nearest literal in the space
     * @return a list of all the literals with same functor and arity
     */
    protected List<Literal> getExamples( Literal ilf, Literal nearest ) {
        List<Literal> examples = new ArrayList<>();
        examples.add( nearest );

        // achieve and askHow are in the plans subspace, all the others in the terms
        String subSpace = "terms";
        if ( ilf.equalsAsStructure( createLiteral( "achieve" ) ) || ilf.equalsAsStructure( createLiteral( "askHow" ) ) )
            subSpace = "plans";

        // check if same
        for ( Literal lit : getSubSpace( subSpace ) )
            if ( nearest.getFunctor().equals( lit.getFunctor() ) && nearest.getArity() == lit.getArity() )
                examples.add( lit );

        return examples;
    }

}