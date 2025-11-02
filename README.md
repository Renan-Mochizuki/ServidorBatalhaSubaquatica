Rodar comandos no CMD na pasta raiz do projeto

Compilar Server e executar
`javac -d build .\classes\* .\server\* && java -cp build server.Server`

Compilar Client e executar
`javac -d build .\client\Client.java && java -cp build client.Client`

Compilar Jogo e executar
`javac -d build .\client\Jogo.java && java -cp build client.Jogo`

Criar executaveis leves do Server e Jogo
Powershell: `.\build-small.ps1 -JdkPath $jdkRoot`

Testar comandos automatizados
Powershell: `powershell -ExecutionPolicy Bypass -File .\run_autoclients.ps1`

Criar executavel Server (outro método)
`javac --release 21 -d build -sourcepath . .\classes\*.java .\server\*.java && jar cfe build\input\Server.jar server.Server -C build . && jpackage --input build\input --name Servidor --main-jar Server.jar --main-class server.Server --type app-image --win-console --dest build\output`

Criar executavel Cliente (outro método)
`javac --release 21 -d build -sourcepath . .\client\Client.java && jar cfe build\input\Client.jar client.Client -C build . && jpackage --input build\input --name Cliente --main-jar Client.jar --main-class client.Client --type app-image --win-console --dest build\output`

Criar executavel Jogo (outro método)
`javac --release 21 -d build -sourcepath . .\client\Jogo.java && jar cfe build\input\Jogo.jar client.Jogo -C build . && jpackage --input build\input --name Jogo --main-jar Jogo.jar --main-class client.Jogo --type app-image --win-console --dest build/output`
