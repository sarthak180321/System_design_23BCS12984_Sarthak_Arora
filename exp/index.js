const express = require('express');
const ws = require('ws');
const app = express();
const client = require("pg").Client;

app.use(express.json());

const wss = new ws.Server({ noServer: true });

let myclient = null;

(async () => {
    myclient = new client({
        user: "neondb_owner",
        password: "",
        host: "ep-misty-morning-ad45oy2e-pooler.c-2.us-east-1.aws.neon.tech",
        database: "neondb",
        port: 5432,
        ssl: {
            require: true,
            rejectUnauthorized: false,
        }
    });

    await myclient.connect();
    console.log("DB connected");

    myclient.on("error", (err) => {
        console.error("Postgres client error:", err.message);
    });
})();



app.post('/shorten', async (req, res) => {
    try {
        const longurl = req.headers.url;
        const query_result = await myclient.query(
            "INSERT INTO url_list (url) VALUES ($1) RETURNING counter",
            [longurl]
        );
        res.send(query_result.rows[0].counter.toString());
    } catch (err) {
        res.status(500).send(err.message);
    }
});
app.get('/:counter', async (req, res) => {
    const counter = req.params.counter;

    const query_result = await myclient.query(
        "SELECT url FROM url_list WHERE counter = $1",
        [counter]
    );
    if (query_result.rows.length === 0) {
        return res.status(404).send("Not found");
    }
    res.redirect(query_result.rows[0].url);
});
app.listen(4000, () => {
    console.log("server is running on port 4000");
});
