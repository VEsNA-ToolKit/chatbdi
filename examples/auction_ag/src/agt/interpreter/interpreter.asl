{ include ("interpreter/instrumentation.asl") }

// This are the literals from interpreter that handles some specific actions
literals( interpreter, [ which_available_agents, which_are_your_available_plans, describe_plan ] ).

!init_interpreter.

// Init the interpreter
// - create the chat artifact
// - instrument all the agents and wait for the answers
// - init the Interpreter implemented with LLM and embeddings
//  with all the literals as parameters to generate the embeddings
+!init_interpreter
    :   true
    <-  makeArtifact(chat, "interpreter.ChatArtifact", [], ArtId);
        focus(ArtId);
        !instrument_all;
        ?literals( Literals );
        ?beliefs( Beliefs );
        ?plans( Plans );
        .concat( Literals, Beliefs, Plans, AllLiterals );
        makeArtifact(interpreter, "interpreter.LLMWithEmbeddingsInterpreter", [AllLiterals], ArtId2);
        focus(ArtId2).

// Each message received is translated in a sentence and sent on the chat
+!kqml_received( Sender, tell, Msg, X )
    <-  .print("Received ", Msg, " from ", Sender );
        generate_sentence( Msg, Sentence );
        msg( Sender, Sentence ).

// If the user has asked one agent to describe a plan now the interpreter is waiting for the plan name
+user_msg( Msg )
    :   plan_description_choice( Agent )
    <-  if( not .substring( "+!", Msg ) ){
            .concat( "+!", Msg, PlanName );
            .send( Agent, askHow, PlanName, Plan );
        } else {
            .send( Agent, askHow, Msg, Plan );
        }
        .concat( "Describe this plan that you can perform: ", Plan, Prompt );
        generate_sentence( Prompt, Sentence);
        msg( Agent, Sentence );
        -plan_description_choice( Agent ).

// The user sent a message without recipients -> broadcast
// It manages a request from the user of which are the available agents
+user_msg( Msg )
    :   true
    <-  .print( "Broadcast msg" );
        generate_property( Msg, NewBelief );
        if ( NewBelief == which_available_agents ){
            .print( "I enumerate the agents for the user" );
            !enumerate_agents;
        } else {
            .broadcast( tell, NewBelief );
        }.

// The user sent a message with recipients -> send to all recipients
// It manages the request from the user of available plans and describe a plan
+user_msg( Recipients, Msg )
    :   true
    <-  .print( "Message to ", Recipients );
        generate_property( Msg, NewBelief );
        for ( .member(Recipient, Recipients ) ){
            if ( NewBelief == which_are_your_available_plans ) {
                .send( Recipient, achieve, list_plans );
            } else {
                if ( NewBelief == describe_plan ) {
                    +plan_description_choice( Recipient );
                    ?plans( Plans )[ source( Recipient ) ];
                    .concat( "Please write on the chat the plan you want: ", Plans, PlanChoice );
                    msg( interpreter, PlanChoice );
                } else {
                    .send( Recipient, tell, NewBelief );
                }
            };
        }.

// Enumerates the name of the agents.
+!enumerate_agents
    :   .all_names( Names ) & .my_name( Name )
    <-  .delete(Name, Names, NewNames);
        .concat( "Enumerate the agents in this multi-agent system in a dotted list. The agents are: ", NewNames, Description);
        generate_sentence( describe( Description ), Sentence );
        msg( interpreter, Sentence ).