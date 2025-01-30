package interpreter;

import java.util.List;

import jason.asSemantics.*;
import jason.asSyntax.*;

public class list_beliefs extends DefaultInternalAction {
    
    @Override
    public Object execute( TransitionSystem ts, Unifier un, Term[] args ) throws Exception {

        Agent agent = ts.getAg();

        ListTerm list = new ListTermImpl();

        for ( Literal original_belief : agent.getBB() ) {
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
        return un.unifies( list, args[0] );
    }
}
