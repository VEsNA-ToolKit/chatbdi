package interpreter;

import jason.asSemantics.*;
import jason.asSyntax.*;
import jason.pl.PlanLibrary;
import java.util.List;
import java.util.ArrayList;

import static jason.asSyntax.ASSyntax.*;

public class list_plans extends DefaultInternalAction {

    @Override
    public Object execute( TransitionSystem ts, Unifier un, Term[] args ) throws Exception {
        Agent ag = ts.getAg();

        Unifier u = new Unifier();
        ag.believes( parseLiteral( "interpreter( Interpreter )" ), u );
        Term interpreter_name = u.get( "Interpreter" );

        PlanLibrary pl = ag.getPL();

        ListTerm list = new ListTermImpl();

        for ( Plan p : pl.getPlans() ) {
            SourceInfo srcInfo = p.getSrcInfo();
            if ( srcInfo == null )
                continue;
            if ( ! srcInfo.getSrcFile().startsWith( "file:" ) )
                continue;
            if (p.getAnnot( "source " ) != null && p.getAnnot( "source" ).getTerm( 0 ).equals( interpreter_name ) )
                continue;
            list.add( p.getTrigger().getLiteral() );
        }

        return un.unifies( list, args[0] );
    }
}
