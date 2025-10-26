Rodar comandos na pasta raiz do projeto

Compilar Server
`javac -d build .\classes\* .\server\*`

Executar Server
`java -cp build server.Server`

Compilar Cliente
`javac -d build Client.java`

Executar Cliente
`java -cp build Client`



`javac -d build .\classes\* .\server\* && java -cp build server.Server`
`javac -d build Client.java && java -cp build Client`
