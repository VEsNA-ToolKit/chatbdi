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

// ** INSTRUMENTATION ** //

// This plan instruments all the agents and waits for all the literals
// from the agents.
//! Can broadcast overwrite one of these two methods?
@atomic
+!instrument_all
    <-  .all_names( AllAgents );
        .my_name( Me );
        .delete( Me, AllAgents, Agents );
        .length( Agents, N );
        for( .member( Agent, Agents ) ) {
            .print( "I instrument ", Agent );
            !instrument( Agent );
        };
        while( .count( literals( _ ), RecvN ) & RecvN < N ){
            .wait( 500 );
        }.

+!instrument( Agent )
    <-  .my_name( Me );
        .send( Agent, tell, interpreter( Me ) );
        .plan_label( ListPlans, list_plans );
        .plan_label( ProvideLiterals, provide_literals );
        .send( Agent, tellHow, ListPlans );
        .send( Agent, tellHow, ProvideLiterals );
        .send( Agent, tellHow, "+_ : interpreter( Interpreter ) <- interpreter.list_beliefs( Beliefs ); .send( Name, tell, beliefs( Beliefs ) ).");
        .send( Agent, tellHow, "+!kqml_received( Agent, _, _, _ ) <- .send( Agent, tell, error_message )");
        .send( Agent, achieve, provide_literals ).

@list_plans
+!list_plans
    :   interpreter( Intepreter )
    <-  interpreter.list_plans( Plans );
        .concat( "These is what you can do, describe to me your functions using a dotted list. These are your plans: ", Plans, PromptString );
        .send( Name, tell, describe( PromptString ) ).

@provide_literals
+!provide_literals
    :   interpreter( Interpreter )
    <-  .my_name( Me );
        interpreter.list_plans( Plans );
        interpreter.list_beliefs( Beliefs );
        interpreter.list_useful_literals( Literals );
        .send( Interpreter, tell, Plans );
        .send( Interpreter, tell, Beliefs );
        .send( Interpreter, tell, Literals).

+!kqml_received( Sender, tell, beliefs( Beliefs ), _ )
    :   beliefs( _ )[ source( Sender ) ]
    <-  -+beliefs( Beliefs )[ source( Sender ) ];
        update_embeddings( Beliefs ).

+!kqml_received( Sender, tell, beliefs( Beliefs ), _ )
    <-  -+beliefs( Beliefs )[ source( Sender ) ].

+!kqml_received( Sender, tell, plans( Plans ), _ )
    :   plans( _ )[ source( Sender ) ]
    <-  -+plans( Plans )[ source( Sender ) ];
        update_embeddings( Plans ).

+!kqml_received( Sender, tell, plans( Plans ), _ )
    <-  -+plans( Plans )[ source( Sender ) ].

+!kqml_received( Sender, tell, literals( Literals ) )
    :   literals( _ )[ source( Sender ) ]
    <-  -+literals( Literals )[ source( Sender ) ];
        update_embeddings( Literals ).

+!kqml_received( Sender, tell, literals( Literals ) )
    <-  -+literals( Literals )[ source( Sender ) ].