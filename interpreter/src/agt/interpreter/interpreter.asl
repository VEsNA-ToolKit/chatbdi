{ include ("interpreter/instrumentation.asl") }
{ include ("interpreter/server.asl") }

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
    :   true
    <-  .print("Sending message to LLaMA: ", Msg);
        classify_performative(Msg, Performative);
        .print("Performative classified: ", Performative);
        !build_kqml_msg(Msg, Performative).

+!build_kqml_msg(Msg, achieve)
    :   triggers(master, Triggers)
    <-  .print("Got an achieve message. Sending it to the Llama translator.");
        translate_achieve(Msg, Triggers, KqmlMsg);
        .send(master, achieve, KqmlMsg).

+!build_kqml_msg(Msg, ask)
    :   beliefs(master, Beliefs)
    <-  .print("Got an ask message. Sending it to the Llama translator.");
        translate_ask(Msg, Beliefs, KqmlMsg);
        .send(master, achieve, KqmlMsg).

+!build_kqml_msg(Msg, tell)
    :   literals(master, Literals)
    <-  .print("Got a tell message. Sending it to the Llama translator.");
        translate_tell(Msg, Literals, KqmlMsg);
        .send(master, tell, KqmlMsg).

// KQML TO NATURAL LANGUAGE
+!kqml_received(Sender, Performative, Msg, X)
    :   client(Address)
    <-  .print("[TELL] Received ", Msg, " from ", Sender);
        kqml2nl(Msg, Performative, NlMsg);
        send(Address, NlMsg).