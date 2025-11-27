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

import static chatbdi.Tools.*;

/**
 * Interpreter is an Agent Architecture that enables the user to interact with the agents in the mas
 * @author Andrea Gatti
 */
public class Interpreter extends AgArch {

    /** Supported Illocutionary forces for the classifier */
    private final String[] SUPPORTED_ILF = { "tell", "askOne", "askAll" };
    /** Logging file */
    private final String DEBUG_LOG = "interpreter.log";

    /** Ollama manages the connection with the daemon */
    private Ollama ollama;
    /** ChatUI manages the GUI */
    private ChatUI chatUI;
    /** EmbeddingSpace manages the embedding space */
    private EmbeddingSpace embSpace;

    /**
     * Initializes all what is needed for the interpreter:
     * <ul>
     * <li> the Ollama client </li>
     * <li> the embedding space </li>
     * <li> the chat UI </li>
     */
    @Override
    public void init() {
        try {
            ollama = new Ollama( SUPPORTED_ILF );
            logInfo( "Initializing Ollama models" );
            embSpace = new EmbeddingSpace( ollama );
            initEmbeddingSpace();
            logInfo( "Initializing the Embedding Space" );
            chatUI = new ChatUI( getAgName() );
        } catch ( ConnectException ce ) {
            logSevere( ce.getMessage() );
        } catch ( RemoteException re ) {
            logSevere( "REMOTE EXCEPTION! " + re.getMessage() );
        }
    }

    /**
     * Interpreter overwrites the checkMail method: 
     * every message received by the agent triggers a translation to Natural Language and displays it on the chat.
     */
    @Override
    public void checkMail() {
        super.checkMail();

        Queue<Message> mbox = getTS().getC().getMailBox();

        if ( mbox.isEmpty() )
            return;
        
        Message m = mbox.peek();
        String msg = kqml2nl( m );
        try {
            chatUI.showMsg( m.getSender(), msg );
        } catch ( IOException ioe ) {
            logSevere( ioe.getMessage() );
        }
    }

    /**
     * Translates and send a user message to the agents
     * @param receivers the list of receiver agents
     * @param msg the message written on the chat
     */
    protected void handleUserMsg( List<String> receivers, String msg ) {
        // Translates the message into a KQML Message
        Message m = nl2kqml( receivers, msg );
        if ( m == null )
            return;
        // Broadcast if no receivers are set
        if ( receivers.isEmpty() ) {
            broadcast( m );
            return;
        }
        // Send it to all receivers
        for ( String receiver : receivers ) {
            m.setReceiver( receiver );
            sendMsg( m );
        }
    }

    /**
     * Translates a user message into a KQML Message object
     * @param receivers the list of receiver agents
     * @param msg the message written on the chat
     * @return the KQML Message
     * @throws ParseException if the resulting translation is not syntactically correct
     * @throws Exception if it fails sending or broadcasting the message
     */
    protected Message nl2kqml( List<String> receivers, String msg ) throws Exception, ParseException {
        // If the message is empty return
        if ( msg.trim().isEmpty() )
            return null;
        // Classify the message
        Literal ilf = ollama.classify( msg );
        // Generate the final term
        Literal term = generateTerm( receivers, ilf, msg );
        // If the computed ilf is an askHow add the triggering +! part to the term
        if ( ilf.equalsAsStructure( createLiteral( "askHow" ) ) )
            term = new Trigger( Trigger.TEOperator.add, Trigger.TEType.achieve, term );

        return new Message( ilf.toString(), this.getAgName(), null, term );
    }

    protected String kqml2nl( Message m ) {
        try {
            return ollama.generate( m );
        } catch ( IOException ioe ) {
            logSevere( ioe.getMessage() );
        }
        return "Error showing the message";
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
                    List<Pred> preds = formulaToList( body );
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
                List<Pred> contextList = formulaToList( context );
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