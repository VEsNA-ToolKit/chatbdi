package interpreter;

import cartago.*;
import jason.asSyntax.*;

public interface Interpreter {

    void generate_property( String sentence, OpFeedbackParam<Literal> property );
    void generate_sentence( String literal, OpFeedbackParam<String> sentence );

}