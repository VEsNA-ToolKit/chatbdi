{ include( "interpreter.asl" ) }

instrumentation( false ).
interpreter_class( "interpreter.SampleInterpreter").

+user_msg( Msg )
    :   .my_name( Me )
    <-  classify_performative( Msg, Performative );
        generate_property( Msg, Belief );
        .concat("Broadcast input: ", Msg, ", you can check the beliefs", NewMsg );
        .broadcast( Performative, Belief );
        .print( "Input: ", Msg, ", property: ", Belief, ", performative: ", Performative ).

+user_msg( Recipients, Msg )
    :   .my_name( Me )
    <-  classify_performative( Msg, Performative );
        generate_property( Msg, Belief );
        .concat("Sended input: ", Msg, " to: ", Recipients, ", you can check the beliefs", NewMsg );
        .send( Recipients, tell, Belief );
        .print( "Input: ", Msg, ", property: ", Belief, ", performative: ", Performative ).

+!kqml_received( Sender, Performative, Msg, _ )
    <-  msg( Sender, Msg ).