Elvis logger -Branch:kandidat
=========================

From the beginning a copy of ElivsLogger.LoggToBus<br />
But due to stability problems overall and specifically akka version headaches, a new branch had to be made <br />.

Upgrading the old one was at the point too risky if something would stop working, soon a merge could be an option. <br />

To get the code running:<br />
[1] Make sure you've got the Scala Build Tool(sbt) on your dev machine.<br />
[2] Clone the repository<br />
[3] Change the connection setup in the following file:<br />
* src/main/resources/application.conf, buss.ip="active.mq.ip"<br />
