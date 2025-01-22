{ include ("interpreter/instrumentation.asl") }

literals( interpreter, [ which_available_agents ] ).

!init_interpreter.

+!init_interpreter
    :   true
    <-  makeArtifact(chat, "interpreter.ChatArtifact", [], ArtId);
        focus(ArtId);
        makeArtifact(interpreter, "interpreter.Interpreter", [], ArtId2);
        focus(ArtId2);
        !instrument_all.

+!kqml_received( Sender, tell, Msg, X )
    <-  .print("Received ", Msg, " from ", Sender );
        generate_sentence( Msg, Sentence );
        msg( Sender, Sentence ).

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

+user_msg( Recipients, Msg )
    :   true
    <-  .print( "Message to ", Recipients );
        generate_property( Msg, NewBelief );
        for ( .member(Recipient, Recipients ) ){
            .send( Recipient, tell, NewBelief );
        }.

+!enumerate_agents
    :   .all_names( Names ) & .my_name( Name )
    <-  .delete(Name, Names, NewNames);
        generate_sentence( agents_available_in_this_project( NewNames ), Sentence );
        msg( interpreter, Sentence ).