package interpreter;

import cartago.*;
import jason.asSyntax.*;

// This interface provides a template for the interpreter class
public interface Interpreter {

    // This function takes a sentence in natural language and generates a property as result
    void generate_property( String sentence, OpFeedbackParam<Literal> property );

    // This function takes a performative and a literal and generates a sentence to be showed on the chat
    void generate_sentence( String performative, String literal, OpFeedbackParam<String> sentence );

    // This function takes a sentence and classifies the performative for the KQML message
    default void classify_performative( String sentence, OpFeedbackParam<Literal> performative ) {
        performative.set( ASSyntax.createLiteral( "tell" ) );
    }

}