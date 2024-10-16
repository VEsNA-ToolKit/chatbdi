{ include ("interpreter/server.asl") }
{ include ("interpreter/instrumentation.asl") }

// This is the main agent.
!init_llama.

// interpreter waits for a client before creating the Llama artifact.
// Meanwhile it instruments the agents.
+!init_llama
    :   not client(Address)
    <-  .wait(500);
        !init_llama.

+!init_llama
    :   client(Address)
    <-  makeArtifact(llamaArtName, "env.LLaMAArtifact", [], LLamaArtId);
        focus(LLamaArtId);
        .print("LLaMA Artifact created.").

// NATURAL LANGUAGE TO KQML

// The agent takes the message and:
//      - classifies the performative;
//      - builds a kqml message that will be sent to the agent.
+!msg_to_kqml(Msg)
    :   .my_name(Me)
    <-  .print("Update MAS state");
        .broadcast(achieve, provide_plans(Me));
        .print("Sending message to LLaMA: ", Msg);
        classify_performative(Msg, Performative);
        .print("Performative classified: ", Performative);
        !build_kqml_msg(Msg, Performative).

+!build_kqml_msg(Msg, achieve)
    :   triggers(_, Triggers)
    <-  .print("Got an achieve message. Sending it to the Llama translator.");
        translate_achieve(Msg, Triggers, KqmlMsg);
        .broadcast(achieve, KqmlMsg).

+!build_kqml_msg(Msg, ask)
    :   beliefs(_, Beliefs)
    <-  .print("Got an ask message. Sending it to the Llama translator.");
        translate_ask(Msg, Beliefs, KqmlMsg);
        .broadcast(achieve, KqmlMsg).

+!build_kqml_msg(Msg, tell)
    :   literals(_, Literals)
    <-  .findall(AllLiterals, literals(_, AllLiterals), NewAllLiterals);
        .print(NewAllLiterals);
        .print("Got a tell message. Sending it to the Llama translator.");
        translate_tell(Msg, NewAllLiterals, KqmlMsg);
        .broadcast(tell, KqmlMsg).

// KQML TO NATURAL LANGUAGE
+!kqml_received(Sender, tell, error_message, X)
    :   client(Address)
    <-  send(Address, "I didn't get it. Can you repeat it?").

+!kqml_received(Sender, Performative, Msg, X)
    :   client(Address) & literals(Sender, Literals)
    <-  .print("[TELL] Received ", Msg, " from ", Sender);
        kqml2nl(Msg, Performative, Literals, NlMsg);
        .concat(Sender, ": ", SenderStr);
        .concat(SenderStr, NlMsg, SenderNlMsg);
        send(Address, SenderNlMsg).