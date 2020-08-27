# Create-application-on-ONOS

### Use idea of Ryu SimpleSwitch13 application and implement on ONOS: 

Ryu SimpleSwitch13 application

https://osrg.github.io/ryu-book/zh_tw/html/switching_hub.html.

ONOS Java API (1.15.0)

http://api.onosproject.org/1.15.0/apidocs/

ONOS ReactiveForwarding application

https://github.com/opennetworkinglab/onos/blob/onos-1.15/apps/fwd/src/main/java/org/onosproject/fwd/ReactiveForwarding.java


### 1. Setup ONOS profile:
```bash
#Add following lines into ~/.bashrc
export ONOS_ROOT= ~/onos
source $ONOS_ROOT/tools/dev/bash_profile
```
### 2. Install Maven
```bash
$ sudo apt install maven
```
### 3. Build ONOS
```bash
$ cd onos
$ op
```
### 4. Publish ONOS libraries only
```bash
$ onos-publish -l
```
### 5.Build the current version of the ONOS application archetypes
```bash
$ cd $ONOS_ROOT/tools/package/archetypes
$ mvn clean install
```
### 6.Indicate version of ONOS API
```bash
$ export ONOS_POM_VERSION=1.15.0
```
### 7. Create ONOS application 
```bash
$ onos-create-app
…
[INFO] …
Define value for property 'groupId': <yourID>
Define value for property 'artifactId': <yourAPP_Name>
Define value for property 'version' 1.0-SNAPSHOT: : <enter>
Define value for property 'package' nctu.winlab: : <yourID.yourAPP_Name>
Confirm properties configuration:
.......
.......
```

```bash
$ cd <artifactId>
$ gedit pom.xml

(Uncomment and modify the following two lines in 34,36 line) 
<onos.app.name>yourID.yourAPP_Name</onos.app.name>
<onos.app.origin>xxxLAB</onos.app.origin>
```
### 8. Write the Application
```bash
$ cd ~/onos/tools/package/archetypes/app/src/main/java/yi/app
  gedit AppComponent.java
```
### 9. Compile ONOS application
```bash
$ cd <artifactId>
$ mvn clean install -DskipTests
```
### 10. Run ONOS
```bash
$ cd ~/onos
$ ok clean
```
### 11. Install and activate ONOS application
```bash
$ cd ~/onos/tools/package/archetypes/app
$ onos-app localhost reinstall! target/<package>-<version>.oar
```
### 12. Deactivate ONOS forwarding application
```bash
$ cd onos
$ tools/test/bin/onos localhost

onos > app deactivate fwd
```

### 13. Use mininet to test
```bash
$sudo mn --controller=remote --topo=tree,depth=2
mininet > pingall
```
