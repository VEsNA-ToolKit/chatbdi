// !start_ws_server.

+!start_ws_server
    :   true
    <-  .print("Starting WebSocket connection");
        .my_name(Me);
        .concat(Me, "ws", WsArtName);
        makeArtifact(WsArtName, "Env.WebSocketArtifact", [8080], WsArtId);
        focus(WsArtId);
        .wait({+clientConnected(_)}).

+!start_websocket_server
   <- makeArtifact("websocket","WebSocketArtifact",[],ArtId);
      focus(ArtId);
      init(8080).

+!send_to_client(Address, Msg)
   <- send(Address, Msg).

+clientConnected(Address)
   <- .print("New client connected: ", Address);
      send(Address, "Welcome to the server!");
      +client(Address).

+messageReceived(Address, Message)
   <- .print("Received message from ", Address, ": ", Message);
      .concat("Message received: ", Message, Msg);
      !msg_to_kqml(Message).