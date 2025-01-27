!init_interpreter.

// Error if no instrumentation or interpreter_class provided
+!init_interpreter
    :   ( not instrumentation( _ ) | not interpreter_class( _ ) ) & .my_name( Me )
    <-  .print("You need these two beliefs:");
        .print(" - instrumentation( true | false ).");
        .print("   If true the interpreter will instrument the other agents;");
        .print(" - interpreter_class( \"MyInterpreterClass\" ).");
        .print("   The value is the name of the class to use as interpreter that should implement the Interpreter interface.");
        .kill_agent( Me ).


// Initialize the interpreter in case the instrumentation is activated.
+!init_interpreter
    :   instrumentation( true ) & interpreter_class( InterpreterClass )
    <-  makeArtifact( chat, "interpreter.ChatArtifact", [], ChatArtId );
        focus( ChatArtId );
        !instrument_all;
        ?literals( Literals );
        ?beliefs( Beliefs );
        ?plans( Plans );
        .concat( Literals, Beliefs, Plans, AllLiterals );
        makeArtifact( interpreter, InterpreterClass, [ AllLiterals ], IntArtId );
        focus( IntArtId ).

// Initialize the interpreter without instrumentation
+!init_interpreter
    :   instrumentation( false ) & interpreter_class( InterpreterClass )
    <-  makeArtifact( chat, "interpreter.ChatArtifact", [], ChatArtId );
        focus( ChatArtId );
        makeArtifact( interpreter, InterpreterClass, [], IntArtId );
        focus( IntArtId ). 

// Each message received from the interpreter agent
// - is sent to the interpreter artifact to generate a sentence;
// - is shown in the chat.
+!kqml_received( Sender, Performative, Msg, _ )
    <-  .print("Received ", Msg, " from ", Sender );
        generate_sentence( Performative, Msg, Sentence );
        msg( Sender, Sentence ).