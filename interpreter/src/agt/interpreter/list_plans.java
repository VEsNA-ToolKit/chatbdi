package interpreter;

import jason.asSemantics.*;
import jason.asSyntax.*;
import jason.pl.PlanLibrary;
import java.util.List;
import java.util.ArrayList;

public class list_plans extends DefaultInternalAction {

    @Override
    public Object execute( TransitionSystem ts, Unifier un, Term[] args ) throws Exception {
        Agent agent = ts.getAg();
        PlanLibrary pl = agent.getPL();

        ListTerm list = new ListTermImpl();

        for ( Plan p : pl.getPlans() ) {
            String srcInfo = p.getSrcInfo().toString();
            if ( srcInfo.startsWith( "file:" ) ) {
                list.add( new StringTermImpl( p.getTrigger().getTerm(1).toString() ) );
            }
        }

        return un.unifies( list, args[0] );
    }
}
