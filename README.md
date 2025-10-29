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


Criar executavel Server
`javac --release 21 -d build -sourcepath . .\classes\*.java .\server\*.java`

`jar cfe build\input\Server.jar server.Server -C build .`

`jpackage --input build\input --name Servidor --main-jar Server.jar --main-class server.Server --type app-image --win-console --dest build`

.

`javac --release 21 -d build -sourcepath . .\classes\*.java .\server\*.java && jar cfe build\input\Server.jar server.Server -C build . && jpackage --input build\input --name Servidor --main-jar Server.jar --main-class server.Server --type app-image --win-console --dest build`


Criar executavel Cliente
`javac --release 21 -d build -sourcepath . .\client\Client.java`

`jar cfe build\input\Client.jar client.Client -C build .`

`jpackage --input build\input --name Cliente --main-jar Client.jar --main-class Client --type app-image --win-console --dest build`

.

`javac --release 21 -d build -sourcepath . .\client\Client.java && jar cfe build\input\Client.jar client.Client -C build . && jpackage --input build\input --name Cliente --main-jar Client.jar --main-class Client --type app-image --win-console --dest build`

