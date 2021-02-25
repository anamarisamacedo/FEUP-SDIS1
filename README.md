# sdis1920-t5-g03

Versão do Java: Java 8 Update 181

COMPILAR: 
Para compilar o projeto basta executar o ficheiro scriptBuild.bat

EXECUTAR:
Os ficheiros scriptPeer.bat, scriptPeer2.bat e scriptPeer3.bat são executáveis de três peers. 
O comando "start rmiregistry" encontra-se no executável scriptPeer.bat.
O ficheiro scriptTestApp.bat serve para executar o cliente/aplicação de teste.

FICHEIROS PARA TESTE:
Os ficheiros para teste encontram-se no diretório Files (Text2.txt e Test3.txt). 
Os diretórios P1, P2 e P3 dentro do diretório Files correspondem aos Peers 1,2 e 3 respetivamente. 
Cada Peer guarda no diretório Backup os ficheiros de que fez backup e no Restore os ficheiros de que fez restore. No original encontra-se a cópia original do ficheiro que mandou fazer backup.