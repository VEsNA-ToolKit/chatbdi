package chatbdi;

import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.HashMap;
import java.util.Set;
import java.util.HashSet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Queue;
import java.nio.file.Files;
import java.nio.file.Path;

import jason.asSyntax.*;
import jason.asSemantics.*;
import jason.architecture.AgArch;
import static jason.asSyntax.ASSyntax.*;
import jason.asSemantics.Agent;
import jason.asSemantics.Message;
import jason.infra.local.RunLocalMAS;
import jason.runtime.RuntimeServices;
import jason.bb.*;
import jason.pl.*;

import jason.asSyntax.parser.ParseException;
import java.net.ConnectException;
import java.io.IOException;
import java.rmi.RemoteException;

import org.json.JSONArray;
import org.json.JSONObject;

public class Interpreter extends AgArch {

    // Supported Illocutionary forces for the classifier
    private final String[] SUPPORTED_ILF = { "tell", "askOne", "askAll" };
    // Logging
    private final String DEBUG_LOG = "interpreter.log";

    // Ollama manages the connection with the daemon
    private Ollama ollama;
    // ChatUI manages the GUI
    private ChatUI chatUI;
    // EmbeddingSpace manages the embedding space
    private EmbeddingSpace embSpace;
    // Tools contains a set of useful tools for standardized stuff
    // private Tools tools;

    @Override
    public void init() {
        try {
            ollama = new Ollama( SUPPORTED_ILF );
            logInfo( "Initializing Ollama models" );
            embSpace = new EmbeddingSpace( ollama );
            initEmbeddingSpace();
            logInfo( "Initializing the Embedding Space" );
            chatUI = new ChatUI();
        } catch ( ConnectException ce ) {
            logSevere( ce.getMessage() );
        } catch ( RemoteException re ) {
            logSevere( "REMOTE EXCEPTION! " + re.getMessage() );
        }
    }

    @Override
    public void checkMail() {
        super.checkMail();

        Queue<Message> mbox = getTS().getC().getMailBox();

        if ( mbox.isEmpty() )
            return;
        
        Message m = mbox.peek();
        String msg = kqml2nl( m );
        // TODO: function to vis the msg
        try {
            chatUI.showMsg( m.getSender(), msg );
        } catch ( IOException ioe ) {
            logSevere( ioe.getMessage() );
        }
    }

    // ! Questo è sbagliato! La nl2kqml non deve mandare, deve solo ritornare il termine
    protected void nl2kqml( List<String> receivers, String msg ) throws Exception {
        if ( msg.trim().isEmpty() )
            return;
        Literal ilf = ollama.classify( msg );
        Literal term = generateTerm( receivers, ilf, msg );
        if ( ilf.equalsAsStructure( createLiteral( "askHow" ) ) )
            term = new Trigger( Trigger.TEOperator.add, Trigger.TEType.achieve, term );
        Message m = new Message( ilf.toString(), this.getAgName(), null, term );
        logInfo( "Sending msg " + m );
        if ( receivers.isEmpty() ) {
            broadcast( m );
            return;
        }
        for ( String receiver : receivers ) {
            m.setReceiver( receiver );
            sendMsg( m );
        }
    }

    protected String kqml2nl( Message m ) {
        return "";
    }

    private Literal generateTerm( List<String> receivers, Literal ilf, String msg ) throws ParseException {
        msg = msg.replaceAll( "\\s*@\\S+", "" );
        String subSpace = "terms";
        if ( ilf.equals( "achieve" ) )
            subSpace = "plans";
        Literal nearest = embSpace.findNearest( receivers, subSpace, msg );
        try {
            System.out.println( "[LOG] " + nearest );
            List<Literal> examples = embSpace.getExamples( ilf, nearest );
            return ollama.generate( msg, nearest, ilf, examples );
        } catch( IOException ioe ) {
            throw new IllegalArgumentException( "Prompt loading caused a IO Exception: check the file path. Full error: " + ioe.getMessage() );
        } catch( ParseException pe ) {
            throw new ParseException( "The generated property was not a valid Jason term." );
        }
    }

    private void initEmbeddingSpace() throws RemoteException {
        logInfo( "Initializing content of the Embedding Space" );
        Collection<String> agNames = getRuntimeServices().getAgentsName();
        logInfo( "Agents: " + getRuntimeServices().getAgentsName().toString() );
        for ( String agName : agNames ) {
            logInfo( "Considering " + agName );
            Agent ag = RunLocalMAS.getRunner().getAg( agName ).getTS().getAg();
            BeliefBase bb = ag.getBB().clone();
            PlanLibrary pl = ag.getPL().clone();

            List<Literal> agDomain = new ArrayList<>();
            for ( Literal bel : bb ) {
                if ( bel.toString().contains( "kqml::" ) )
                    continue;
                if ( embSpace.containsTerm( bel ) )
                    continue;
                if ( bel.isRule() ) { 
                    Literal head = ( (Rule) bel ).getHead();
                    embSpace.addTerm( agName, head );
                    LogicalFormula body = ( (Rule) bel ).getBody();
                    List<Pred> preds = Tools.formulaToList( body );
                    for ( Pred p : preds )
                        embSpace.addTerm( agName, p );
                    continue; // necessary to do not call the addTerm this line + 2
                }
                embSpace.addTerm( agName, bel );
            }
            for ( Plan plan : pl ) {
                if ( plan.toString().contains( "@kqml" ) )
                    continue;
                Literal triggerLit = plan.getTrigger().getLiteral();
                if ( plan.getTrigger().isAchvGoal() && embSpace.containsPlan( triggerLit ) )
                    continue;
                if ( !plan.getTrigger().isAchvGoal() && embSpace.containsTerm( triggerLit ) )
                    continue;
                LogicalFormula context = plan.getContext();
                List<Pred> contextList = Tools.formulaToList( context );
                for ( Pred pred : contextList )
                    embSpace.addTerm( agName, pred );
                if ( plan.getTrigger().isAchvGoal() )
                    embSpace.addPlan( agName, triggerLit );
                else
                    embSpace.addTerm( agName, triggerLit );
            }
        }
    }

    protected void logInfo( String msg ) {
        getTS().getLogger().log( Level.INFO, msg );
    }

    protected void logSevere( String msg ) {
        getTS().getLogger().log( Level.SEVERE, msg );
    }

}