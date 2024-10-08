const express = require('express');
const path = require('path');
const app = express();
const PORT = 3000;

// Middleware per il parsing del JSON
app.use(express.json());

// Configura la directory dei file statici
// app.use(express.static(path.join(__dirname, 'public')));
app.use(express.static(__dirname))

// Gestisce le richieste POST all'endpoint '/'
app.post('/', (req, res) => {
    const jsonData = req.body;
    console.log('Dati ricevuti:', jsonData);

    res.json({ message: 'JSON ricevuto con successo' });
});

// Avvia il server
app.listen(PORT, () => {
    console.log(`Server in ascolto su http://localhost:${PORT}`);
});
