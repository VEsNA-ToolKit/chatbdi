package interpreter;

import cartago.*;
import jason.asSyntax.*;

public class SampleInterpreter extends Artifact implements Interpreter {

    @OPERATION
    public void generate_property( String sentence, OpFeedbackParam<Literal> property ) {
        Literal string_prop = ASSyntax.createLiteral( "user_said", ASSyntax.createString( sentence ) );
        property.set( string_prop );
    }

    @OPERATION
    public void generate_sentence( String performative, String literal, OpFeedbackParam<String> sentence ) {
        sentence.set( performative + ": " + literal );
    }

    @OPERATION
    public void classify_performative( String sentence, OpFeedbackParam<Literal> performative ) {
        performative.set( ASSyntax.createLiteral( "tell" ) );
    }
}