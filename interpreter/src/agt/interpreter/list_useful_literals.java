package interpreter;

import jason.asSemantics.*;
import jason.asSyntax.*;
import jason.pl.PlanLibrary;

import java.util.regex.*;
import java.util.List;
import java.util.ArrayList;

public class list_useful_literals extends DefaultInternalAction {
    
    @Override
    public Object execute( TransitionSystem ts, Unifier un, Term[] args ) throws Exception {

        Agent agent = ts.getAg();

        ListTerm list = new ListTermImpl();

        for (Literal original_belief : agent.getBB()) {
            Literal belief = original_belief.copy();
            SourceInfo srcInfo = belief.getSrcInfo();
            if ( srcInfo != null ) {
                String srcInfoStr = srcInfo.toString();
                if ( srcInfoStr.startsWith( "file:" ) ) {
                    belief.clearAnnots();
                    list.add( belief );
                }
            }
        }

        PlanLibrary pl = agent.getPL();

        for ( Plan p : pl.getPlans() ) {
            String srcInfo = p.getSrcInfo().toString();
            if ( srcInfo.startsWith( "file:" ) ) {
                LogicalFormula context = p.getContext();
                if ( context != null ) {
                    String contextStr = context.toString();
                    List<String> groundTerms = new ArrayList<>();
        
                    String regex = "(?<!\\.)\\b[a-z]\\w*\\s*(\\([^()]*\\))?";

                    
                    Pattern pattern = Pattern.compile( regex );
                    Matcher matcher = pattern.matcher( contextStr );
                    
                    while ( matcher.find() ) {
                        groundTerms.add( matcher.group() );
                    }
                    for ( String term : groundTerms ) {
                        Literal lterm = Literal.parseLiteral( term );
                        list.add( lterm );
                    }
                }

                if ( p.getTrigger().getType() == Trigger.TEType.belief ) {
                    list.add( p.getTrigger() );
                }
            }
        }

        return un.unifies( list, args[0] );
    }
}
