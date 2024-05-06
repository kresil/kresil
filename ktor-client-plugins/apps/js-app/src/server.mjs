`use strict`

import express from 'express'

const app = express()
const port = 8080

app.get('/', (req, res) => {
    res.send('Hello, Ktor!')
})

// Start the server
app.listen(port, () => {
    console.log(`Server is listening at http://localhost:${port}`)
})
