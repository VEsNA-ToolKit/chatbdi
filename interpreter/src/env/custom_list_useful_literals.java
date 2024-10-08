package env;

import jason.asSemantics.*;
import jason.asSyntax.*;
import jason.pl.PlanLibrary;

import java.util.regex.*;
import java.util.List;
import java.util.ArrayList;

public class custom_list_useful_literals extends DefaultInternalAction {
    
    // list.add(new StringTermImpl(p.getTrigger().toString()));

    @Override
    public Object execute(TransitionSystem ts, Unifier un, Term[] args) throws Exception {
        Agent agent = ts.getAg();
        ListTerm list = new ListTermImpl();
        for (Literal belief : agent.getBB()) {
            SourceInfo srcInfo = belief.getSrcInfo();
            if ( srcInfo != null ) {
                String srcInfoStr = srcInfo.toString();
                if ( srcInfoStr.startsWith("file:") ) {
                    list.add(new StringTermImpl(belief.toString()));
                }
            }
        }

        PlanLibrary pl = agent.getPL();
        for ( Plan p : pl.getPlans() ) {
            String srcInfo = p.getSrcInfo().toString();
            if ( srcInfo.startsWith("file:") ) {
                LogicalFormula context = p.getContext();
                if ( context != null ) {
                    String contextStr = context.toString();
                    System.out.println(contextStr);
                    List<String> groundTerms = new ArrayList<>();
        
                    String regex = "\\b([a-z][a-zA-Z0-9_]*)\\s*(\\(([^()]|\\([^()]*\\))*\\))?\\b";
                    
                    Pattern pattern = Pattern.compile(regex);
                    Matcher matcher = pattern.matcher(contextStr);
                    
                    while (matcher.find()) {
                        groundTerms.add(matcher.group());
                    }
                    for ( String term : groundTerms ) {
                        list.add( new StringTermImpl( term ) );
                    }
                }

                if ( p.getTrigger().getType() == Trigger.TEType.belief ) {
                    list.add(new StringTermImpl(p.getTrigger().toString()));
                }
            }
        }

        return un.unifies(list, args[0]);
    }
}
