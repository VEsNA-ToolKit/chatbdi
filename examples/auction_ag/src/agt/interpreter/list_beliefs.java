package interpreter;

import java.util.List;
import java.util.ArrayList;

import jason.asSemantics.*;
import jason.asSyntax.*;

import static jason.asSyntax.ASSyntax.*;

public class list_beliefs extends DefaultInternalAction {
    
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
            if ( ! srcInfo.getSrcFile().startsWith( "file:" ) )
                continue;
            if ( belief.getAnnot( "source" ).getTerm(0).equals( interpreter_name ) )
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
        return un.unifies( list, args[0] );
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
