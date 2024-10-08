document.getElementById('send-button').addEventListener('click', sendMessage);
document.getElementById('message-input').addEventListener('input', handleInput);
document.getElementById('message-input').addEventListener('keydown', handleInput);

// function (e) {
//     if (e.key === 'Enter') {
//         sendMessage();
//     }
// });

function handleInput(e) {

    if (e.key === 'Enter') {
        e.preventDefault();
        sendMessage();
        document.getElementById('message-input').innerHTML = '';
        return;
    }

    const messageInput = document.getElementById('message-input');
    var text = messageInput.textContent;

    if (text.endsWith(' '))
        text = text.replace(/ $/, '\u00A0');

    // Cerca parole che iniziano con @ e sostituiscile con uno span stilizzato
    const formattedText = processMentions(text);

    // Aggiorna il contenuto solo se cambia, per evitare la cancellazione della selezione
    if (text !== formattedText) {
        messageInput.innerHTML = formattedText;

        // Sposta il cursore alla fine del testo
        placeCaretAtEnd(messageInput);
    }
}

function placeCaretAtEnd(el) {
    el.focus();
    if (typeof window.getSelection != "undefined" && typeof document.createRange != "undefined") {
        const range = document.createRange();
        range.selectNodeContents(el);
        range.collapse(false);
        const sel = window.getSelection();
        sel.removeAllRanges();
        sel.addRange(range);
    }
}

function sendMessage() {
    const messageInput = document.getElementById('message-input');
    const message = messageInput.innerHTML.trim();

    if (message) {
        displayMessage(message, 'user');
        ws.send(message);
        messageInput.innerHTML = '';

        // Simulating a bot response
        // setTimeout(() => {
        //     displayMessage('Hello! How can I help you?', 'bot');
        // }, 1000);
    }
}

function displayMessage(message, sender) {
    const chatMessages = document.getElementById('chat-messages');
    const messageElement = document.createElement('div');
    messageElement.classList.add('chat-message', sender);

    const formattedMessage = processMentions(message);
    messageElement.innerHTML = formattedMessage;

    // messageElement.textContent = message;
    chatMessages.appendChild(messageElement);
    chatMessages.scrollTop = chatMessages.scrollHeight;
}

function processMentions(message) {
    const mentionRegex = /@(\w+)/g;
    return message.replace(mentionRegex, '<span class="mention">@$1</span>');
}

let ws;
let reconnectionInterval = 5000;
let maxRetries = 10;
let retries = 0;

function connect() {
    ws = new WebSocket(`ws://127.0.0.1:8080`);

    ws.onmessage = function(event) {
        const message = JSON.parse(event.data);
        displayMessage(message['msg'], "bot");
        console.log('Messaggio ricevuto:', message['msg']);
    };

    ws.onopen = function() {
        console.log('Connessione WebSocket aperta');
    };

    ws.onclose = function() {
        console.log('Connessione WebSocket chiusa');
        retryConnection();
    };
}

function retryConnection() {
    if (retries < maxRetries) {
        setTimeout(() => {
            retries++;
            console.log('Tentativo di riconnessione...');
            connect();
        }, reconnectionInterval);
    } else {
        console.log('Raggiunto il numero massimo di tentativi di connessione');
    }
}

connect();
