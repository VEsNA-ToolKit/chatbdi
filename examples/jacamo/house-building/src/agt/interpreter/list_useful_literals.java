package interpreter;

import jason.asSemantics.*;
import jason.asSyntax.*;
import jason.pl.PlanLibrary;

import java.util.regex.*;
import java.util.List;
import java.util.ArrayList;

import static jason.asSyntax.ASSyntax.*;

public class list_useful_literals extends DefaultInternalAction {

    @Override
    public Object execute( TransitionSystem ts, Unifier un, Term[] args ) throws Exception {

        Agent ag = ts.getAg();

        Unifier u = new Unifier();
        ag.believes( parseLiteral( "interpreter( Interpreter )" ), u );
        Term interpreter_name = u.get( "Interpreter" );

        ListTerm list = new ListTermImpl();

        for ( Literal original_belief : ag.getBB() ) {
            Literal belief = original_belief.copy();
            SourceInfo srcInfo = belief.getSrcInfo();
            if ( srcInfo == null )
                continue;
            if ( ! srcInfo.getSrcFile().startsWith("file:") )
                continue;
            if ( belief.getAnnot( "source" ).getTerm( 0 ).equals( interpreter_name ) )
                continue;
            if ( belief.isRule() ) {
                Rule r = ( Rule ) belief;
                list.add( r.headClone() );
                list.addAll( get_all_literals( ( Structure ) r.getBody() ) );
                continue;
            }
            belief.clearAnnots();
            list.add( belief );
        }

        PlanLibrary pl = ag.getPL();

        for ( Plan p : pl.getPlans() ) {
            SourceInfo srcInfo = p.getSrcInfo();
            if (! srcInfo.getSrcFile().startsWith("file:") )
                continue;
            if ( srcInfo.getSrcFile().contains( "interpreter.asl" ) )
                continue;
            LogicalFormula context = p.getContext();
            if ( context == null )
                continue;

            list.addAll( get_all_literals( ( Structure ) context ) );

            if ( p.getTrigger().getType() == Trigger.TEType.belief ) {
                list.add( p.getTrigger().getLiteral() );
            }
        }

        return un.unifies(list, args[0]);
    }

    private List<Literal> get_all_literals( Structure s ) {

        List<Literal> list = new ArrayList<>();

        if ( s.isPred() ) {
            list.add( s );
            return list;
        }

        for ( Term t : s.getTerms() ) {
            if ( t.isStructure() )
                list.addAll( get_all_literals( ( Structure ) t ) );
        }
        return list;
    }

}
