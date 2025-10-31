Rodar comandos na pasta raiz do projeto

Compilar Server
`javac -d build .\classes\* .\server\*`

Executar Server
`java -cp build server.Server`

Compilar Cliente
`javac -d build Client.java`

Executar Cliente
`java -cp build Client`

.

`javac -d build .\classes\* .\server\* && java -cp build server.Server`

`javac -d build .\client\Client.java && java -cp build client.Client`

Compilar Jogo
`javac -d build .\client\Jogo.java && java -cp build client.Jogo`

Criar executavel Server
`javac --release 21 -d build -sourcepath . .\classes\*.java .\server\*.java && jar cfe build\input\Server.jar server.Server -C build . && jpackage --input build\input --name Servidor --main-jar Server.jar --main-class server.Server --type app-image --win-console --dest build\output`


Criar executavel Cliente
`javac --release 21 -d build -sourcepath . .\client\Client.java && jar cfe build\input\Client.jar client.Client -C build . && jpackage --input build\input --name Cliente --main-jar Client.jar --main-class client.Client --type app-image --win-console --dest build\output`


Criar executavel jogo
`javac --release 21 -d build -sourcepath . .\client\Jogo.java && jar cfe build\input\Jogo.jar client.Jogo -C build . && jpackage --input build\input --name Jogo --main-jar Jogo.jar --main-class client.Jogo --type app-image --win-console --dest build/output`

`javac --release 21 -d build -sourcepath . .\client\TelaMensagem.java && jar cfe build\input\TelaMensagem.jar client.TelaMensagem -C build . && jpackage --input build\input --name TelaMensagem --main-jar TelaMensagem.jar --main-class client.TelaMensagem --type app-image --win-console --dest build/output`