package env;

import java.util.List;

import jason.asSemantics.*;
import jason.asSyntax.*;

public class custom_list_beliefs extends DefaultInternalAction {
    
    @Override
    public Object execute(TransitionSystem ts, Unifier un, Term[] args) throws Exception {
        Agent agent = ts.getAg();
        ListTerm list = new ListTermImpl();
        for (Literal belief : agent.getBB()) {
            SourceInfo srcInfo = belief.getSrcInfo();
            if ( srcInfo != null ) {
                String srcInfoStr = srcInfo.toString();
                if ( srcInfoStr.startsWith("file:") ) {
                    list.add(belief);
                }
            }
        }
        return un.unifies(list, args[0]);
    }
}
