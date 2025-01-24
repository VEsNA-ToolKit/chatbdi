package interpreter;

import cartago.*;
import jason.asSyntax.*;

// This interface provides a template for the interpreter class
public interface Interpreter {

    // This function takes a sentence in natural language and generates a property as result
    void generate_property( String sentence, OpFeedbackParam<Literal> property );

    // This function takes a literals and generates a sentence to be showed on the chat
    void generate_sentence( String literal, OpFeedbackParam<String> sentence );

}