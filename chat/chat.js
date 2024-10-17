const express = require('express');
const path = require('path');
const app = express();
const PORT = 3000;

app.use(express.json());

app.use(express.static(__dirname))

app.post('/', (req, res) => {
    const jsonData = req.body;
    console.log('Dati ricevuti:', jsonData);

    res.json({ message: 'JSON ricevuto con successo' });
});

app.listen(PORT, () => {
    console.log(`Server in ascolto su http://localhost:${PORT}`);
});
