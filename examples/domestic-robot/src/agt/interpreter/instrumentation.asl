// This plan instruments all the agents that are not the chat_bdi Agent
// @atomic forces the agent to wait for all answers
@atomic
+!instrument_all
    <-  .all_names(Agents);
        .my_name(Me);
        .delete( Me, Agents, NewAgents);
        .length( NewAgents, N );
        for( .member(Agent, NewAgents) ){
            .print("I instrument ", Agent);
            !instrument(Agent);
        };
        while( .count( literals( _ ), X ) & X < N ){
            .wait(500);
        }.

// The instrument( Agent ) plan sends to Agent:
// - the name of the interpreter
// - the plan +!list_plans to list all plans
// - the plan +!provide_literals(Interpreter) that sends to the interpreter all beliefs, plans and literals
// - the triggering event +_ that makes Agent send an update message with new beliefs for each new own belief
// - a fallback plan for messages without specific management from Agent
// And sends an achieve for plan +!provide_literals for the initialization
+!instrument( Agent )
    :   .my_name( Me )
    <-  .send( Agent, tell, interpreter( Me ) );
        .send( Agent , tellHow, "+!list_plans : interpreter( Name ) <- interpreter.list_plans( Plans ); .concat( \"Make a summary of what you can do as a dotted list. The list of the plans you have available is \", Plans, PlanString ); .send( Name, tell, describe(PlanString))." );
        .send( Agent , tellHow, "+!provide_literals( Interpreter ) : .my_name( Me ) <- interpreter.list_plans( Plans ); .send( Interpreter, tell, plans( Plans ) ); interpreter.list_beliefs( Beliefs ); .send( Interpreter, tell, beliefs( Beliefs ) ); interpreter.list_useful_literals( Literals ); .send( Interpreter, tell, literals( Literals ) )." );
        .wait( 250 );
        .send( Agent , achieve, provide_literals( Me ) );
        .send( Agent , tellHow, "+_ : interpreter( Name ) <- interpreter.list_beliefs( Beliefs ); .send( Name, tell, beliefs( Beliefs) )." );
        .send( Agent , tellHow, "+!kqml_received( Agent, Performative, Msg, X ) : true <- .send( Agent, tell, error_message )." ).

// Update plans
+!kqml_received( Sender, tell, plans( Plans ), X )
    :   plans( _ )[ source( Sender ) ]
    <-  .print( "Got triggers from ", Sender );
        -+plans( Plans );
        update_embeddings( Plans ).

// Initialize plans
+!kqml_received( Sender, tell, plans( Triggers ), X )
    <-  .print( "Got triggers from ", Sender );
        -+plans( Triggers )[ source( Sender ) ].

// Update beliefs
+!kqml_received( Sender, tell, beliefs( Beliefs ), X )
    :   beliefs( _ )[ source( Sender ) ]
    <-  .print( "Got beliefs from ", Sender, ": ", Beliefs );
        -+beliefs( Beliefs )[ source( Sender ) ];
        update_embeddings( Beliefs ).

// Initialize beliefs
+!kqml_received( Sender, tell, beliefs( Beliefs ), X )
    <-  .print( "Got beliefs from ", Sender, ": ", Beliefs );
        -+beliefs( Beliefs )[ source( Sender ) ].
 
// Update literals
+!kqml_received( Sender, tell, literals( Literals ), X )
    :   literals( _ )[ source( Sender ) ]
    <-  .print( "Got Literals from ", Sender, ": ", Literals );
        -+literals( Literals )[ source( Sender ) ];
        update_embeddings( Literals ).

// Initialize literals
+!kqml_received( Sender, tell, literals( Literals ), X )
    <-  .print( "Got Literals from ", Sender, ": ", Literals );
        -+literals( Literals )[ source( Sender ) ].